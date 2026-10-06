using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;

namespace NearTransfer.Core;

#if !NET48
public sealed class LanTransferServer : IAsyncDisposable
#else
public sealed class LanTransferServer
#endif
{
    private const int MaximumHeaderBytes = 32 * 1024;
    private static readonly TimeSpan OfferTimeout = TimeSpan.FromMinutes(2);
    private static readonly TimeSpan ApprovedTransferLifetime = TimeSpan.FromMinutes(5);

    private readonly Func<string> _destinationDirectory;
    private readonly int _port;
    private readonly SemaphoreSlim _activeClients = new(2, 2);
    private readonly ConcurrentDictionary<string, ApprovedTransfer> _approved = new(StringComparer.Ordinal);
    private readonly object _approvedLock = new();
    private TcpListener? _listener;
    private CancellationTokenSource? _stop;
    private Task? _acceptTask;

    public LanTransferServer(Func<string> destinationDirectory, int port = TransferProtocol.HttpPort)
    {
        _destinationDirectory = destinationDirectory;
        _port = port;
    }

    public event Func<TransferOffer, Task<bool>>? OfferReceived;
    public event Action<TransferProgress>? ProgressChanged;
    public event Action<ReceivedFile>? FileReceived;
    public event Action<ReceivedText>? TextReceived;
    public event Action<string>? Error;

    public int LocalPort => (_listener?.LocalEndpoint as IPEndPoint)?.Port ?? _port;
    public bool IsRunning => _stop is not null;

    public void Start()
    {
        if (_stop is not null)
        {
            return;
        }

        var listener = new TcpListener(IPAddress.Any, _port);
        listener.Server.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        listener.Start();
        _listener = listener;
        _stop = new CancellationTokenSource();
        _acceptTask = AcceptLoopAsync(listener, _stop.Token);
    }

#if NET48
    public async Task DisposeAsync()
#else
    public async ValueTask DisposeAsync()
#endif
    {
        var stop = _stop;
        if (stop is null)
        {
            return;
        }

        _stop = null;
        stop.Cancel();
        _listener?.Stop();
        _listener = null;
        _approved.Clear();
        try
        {
            if (_acceptTask is not null)
            {
                await _acceptTask;
            }
        }
        catch (OperationCanceledException)
        {
        }
        catch (ObjectDisposedException)
        {
        }
        finally
        {
            stop.Dispose();
            _acceptTask = null;
        }
    }

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            TcpClient client;
            try
            {
                client = await listener.AcceptTcpClientAsync();
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }
            catch (SocketException exception)
            {
                if (!cancellationToken.IsCancellationRequested)
                {
                    Error?.Invoke($"Không mở được kết nối nhận: {exception.Message}");
                }
                continue;
            }

            client.NoDelay = true;
            if (!_activeClients.Wait(0))
            {
                _ = ReplyBusyAndCloseAsync(client);
                continue;
            }

            _ = ProcessClientAndReleaseAsync(client, cancellationToken);
        }
    }

    private async Task ProcessClientAndReleaseAsync(TcpClient client, CancellationToken cancellationToken)
    {
        using (client)
        {
            try
            {
                await HandleClientAsync(client, cancellationToken);
            }
            catch (Exception exception) when (
                exception is IOException or SocketException or OperationCanceledException or
                    ObjectDisposedException)
            {
                if (!cancellationToken.IsCancellationRequested)
                {
                    Error?.Invoke($"Kết nối nhận bị gián đoạn: {exception.Message}");
                }
            }
            catch (Exception exception)
            {
                Error?.Invoke($"Không nhận được file: {exception.Message}");
                try
                {
                    await WriteResponseAsync(
                        client.GetStream(),
                        500,
                        "Transfer Failed",
                        "Transfer failed.");
                }
                catch (Exception writeException) when (
                    writeException is IOException or SocketException or ObjectDisposedException)
                {
                }
            }
            finally
            {
                _activeClients.Release();
            }
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken cancellationToken)
    {
        var stream = client.GetStream();
        HttpRequest? request;
        try
        {
            request = await ReadRequestAsync(stream, cancellationToken);
        }
        catch (HttpProtocolException exception)
        {
            await WriteResponseAsync(stream, exception.StatusCode, exception.Reason, exception.Message);
            return;
        }

        if (request is null)
        {
            return;
        }

        if (!string.Equals(request.Method, "POST", StringComparison.Ordinal))
        {
            await WriteResponseAsync(stream, 405, "Method Not Allowed", "POST is required.");
            return;
        }

        var senderAddress = TransferProtocol.NormalizeAddress(
            ((IPEndPoint)client.Client.RemoteEndPoint!).Address);
        switch (request.Path)
        {
            case "/offer":
                await HandleOfferAsync(stream, request, senderAddress, cancellationToken);
                break;
            case "/upload":
                await HandleUploadAsync(stream, request, senderAddress, cancellationToken);
                break;
            case "/upload-text":
                await HandleTextUploadAsync(stream, request, senderAddress, cancellationToken);
                break;
            default:
                await WriteResponseAsync(stream, 404, "Not Found", "Unknown endpoint.");
                break;
        }
    }

    private async Task HandleOfferAsync(
        NetworkStream stream,
        HttpRequest request,
        IPAddress senderAddress,
        CancellationToken cancellationToken)
    {
        if (request.Headers.ContainsKey("transfer-encoding") ||
            !TryGetContentLength(request, out var offerBodyLength) ||
            offerBodyLength != 0)
        {
            await WriteResponseAsync(stream, 400, "Bad Request", "Offers must not include a body.");
            return;
        }

        TransferOffer offer;
        try
        {
            offer = ParseOffer(request.Headers, senderAddress);
        }
        catch (HttpProtocolException exception)
        {
            await WriteResponseAsync(stream, exception.StatusCode, exception.Reason, exception.Message);
            return;
        }

        var handler = OfferReceived;
        if (handler is null)
        {
            await WriteResponseAsync(stream, 403, "Declined", "No one is available to approve this transfer.");
            return;
        }

        bool accepted;
        try
        {
            accepted = await WaitForOfferApprovalAsync(handler(offer), OfferTimeout, cancellationToken);
        }
        catch (TimeoutException)
        {
            accepted = false;
        }

        if (!accepted)
        {
            await WriteResponseAsync(stream, 403, "Declined", "The receiver declined or timed out.");
            return;
        }

        lock (_approvedLock)
        {
            ExpireApprovedTransfers();
            if (_approved.Count >= 4 || !_approved.TryAdd(offer.TransferId, new ApprovedTransfer(offer)))
            {
                accepted = false;
            }
        }

        if (!accepted)
        {
            await WriteResponseAsync(stream, 503, "Busy", "Too many approved transfers are waiting.");
            return;
        }

        await WriteResponseAsync(stream, 200, "OK", "Transfer accepted.");
    }

    private async Task HandleUploadAsync(
        NetworkStream stream,
        HttpRequest request,
        IPAddress senderAddress,
        CancellationToken cancellationToken)
    {
        var transferId = GetHeader(request.Headers, "x-transfer-id");
        var indexValue = GetHeader(request.Headers, "x-file-index");
        var encodedName = GetHeader(request.Headers, "x-file-name");
        if (!TransferProtocol.IsTransferId(transferId) ||
            !int.TryParse(indexValue, out var fileIndex) ||
            encodedName is null)
        {
            await WriteResponseAsync(stream, 400, "Bad Request", "Upload metadata is incomplete.");
            return;
        }

        if (request.Headers.ContainsKey("transfer-encoding") ||
            !TryGetContentLength(request, out var contentLength))
        {
            await WriteResponseAsync(stream, 411, "Length Required", "A valid Content-Length is required.");
            return;
        }
        if (contentLength < 0 || contentLength > TransferProtocol.MaximumFileBytes)
        {
            await WriteResponseAsync(stream, 413, "Payload Too Large", "Files must be smaller than 2 GiB.");
            return;
        }

        string fileName;
        try
        {
            fileName = FileNameRules.SanitizeFileName(TransferProtocol.DecodeHeaderValue(encodedName));
        }
        catch (ArgumentException)
        {
            await WriteResponseAsync(stream, 400, "Bad Request", "File name is invalid.");
            return;
        }

        if (!TryReserveUpload(transferId!, fileIndex, fileName, contentLength, senderAddress, out var transfer))
        {
            await WriteResponseAsync(stream, 403, "Forbidden", "The receiver has not approved this file.");
            return;
        }

        string? destinationPath = null;
        try
        {
            using (var destination = FileNameRules.CreateUniqueFile(
                             _destinationDirectory(),
                             fileName,
                             out destinationPath))
            {
                await CopyExactlyAsync(
                    stream,
                    destination,
                    contentLength,
                    transferId!,
                    fileIndex,
                    fileName,
                    cancellationToken);
                await destination.FlushAsync(cancellationToken);
            }

            CompleteUpload(transferId!, transfer!, fileIndex, succeeded: true);
            var savedPath = destinationPath
                ?? throw new IOException("Could not determine the saved file path.");
            await WriteResponseAsync(stream, 201, "Created", $"Saved {Path.GetFileName(savedPath)}");
            FileReceived?.Invoke(new ReceivedFile(
                transferId!,
                Path.GetFileName(savedPath),
                savedPath,
                fileIndex + 1,
                transfer!.Offer.Files.Count));
        }
        catch
        {
            CompleteUpload(transferId!, transfer!, fileIndex, succeeded: false);
            if (destinationPath is not null)
            {
                try
                {
                    File.Delete(destinationPath);
                }
                catch (IOException)
                {
                }
                catch (UnauthorizedAccessException)
                {
                }
            }
            throw;
        }
    }

    private async Task HandleTextUploadAsync(
        NetworkStream stream,
        HttpRequest request,
        IPAddress senderAddress,
        CancellationToken cancellationToken)
    {
        var transferId = GetHeader(request.Headers, "x-transfer-id");
        if (!TransferProtocol.IsTransferId(transferId))
        {
            await WriteResponseAsync(stream, 400, "Bad Request", "Transfer ID is invalid.");
            return;
        }

        if (request.Headers.ContainsKey("transfer-encoding") ||
            !TryGetContentLength(request, out var contentLength))
        {
            await WriteResponseAsync(stream, 411, "Length Required", "A valid Content-Length is required.");
            return;
        }
        if (contentLength is < 1 or > TransferProtocol.MaximumTextBytes)
        {
            await WriteResponseAsync(stream, 413, "Payload Too Large", "Text must be from 1 byte to 256 KB.");
            return;
        }

        if (!TryReserveTextUpload(transferId!, contentLength, senderAddress, out var transfer))
        {
            await WriteResponseAsync(stream, 403, "Forbidden", "The receiver has not approved this message.");
            return;
        }

        try
        {
            var bytes = new byte[(int)contentLength];
            var offset = 0;
            var lastReport = Stopwatch.GetTimestamp();
            while (offset < bytes.Length)
            {
                var count = await stream.ReadAsync(bytes, offset, bytes.Length - offset, cancellationToken);
                if (count == 0)
                {
                    throw new EndOfStreamException("Kết nối bị ngắt khi nhận văn bản.");
                }

                offset += count;
                var now = Stopwatch.GetTimestamp();
                if (now - lastReport >= Stopwatch.Frequency / 6 || offset == bytes.Length)
                {
                    ProgressChanged?.Invoke(new TransferProgress(
                        transferId!,
                        0,
                        "Tin nhắn văn bản",
                        offset,
                        contentLength));
                    lastReport = now;
                }
            }

            var text = Encoding.UTF8.GetString(bytes);
            await WriteResponseAsync(stream, 201, "Created", "Received text message.");
            CompleteUpload(transferId!, transfer!, 0, succeeded: true);
            TextReceived?.Invoke(new ReceivedText(
                transferId!,
                transfer!.Offer.SenderName,
                text,
                contentLength));
        }
        catch
        {
            CompleteUpload(transferId!, transfer!, 0, succeeded: false);
            throw;
        }
    }

    private static TransferOffer ParseOffer(
        IReadOnlyDictionary<string, string> headers,
        IPAddress senderAddress)
    {
        var transferId = GetHeader(headers, "x-transfer-id");
        if (!TransferProtocol.IsTransferId(transferId))
        {
            throw new HttpProtocolException(400, "Bad Request", "Transfer ID is invalid.");
        }

        var type = GetHeader(headers, "x-transfer-type");
        var encodedSender = GetHeader(headers, "x-sender-name");
        var senderName = encodedSender is null
            ? "Thiết bị"
            : FileNameRules.SanitizeFileName(TransferProtocol.DecodeHeaderValue(encodedSender));
        if (!string.Equals(type, "files", StringComparison.Ordinal))
        {
            if (string.Equals(type, "text", StringComparison.Ordinal))
            {
                if (headers.Keys.Any(key => key.StartsWith("x-file-", StringComparison.OrdinalIgnoreCase)))
                {
                    throw new HttpProtocolException(400, "Bad Request", "A transfer cannot combine text and files.");
                }

                if (!long.TryParse(GetHeader(headers, "x-text-size"), out var textSize))
                {
                    throw new HttpProtocolException(400, "Bad Request", "Text size is invalid.");
                }
                if (textSize is < 1 or > TransferProtocol.MaximumTextBytes)
                {
                    throw new HttpProtocolException(413, "Payload Too Large", "Text must be from 1 byte to 256 KB.");
                }

                return new TransferOffer(
                    transferId!,
                    senderName,
                    senderAddress,
                    [new TransferFile("Tin nhắn văn bản", textSize)],
                    textSize,
                    IsText: true);
            }

            throw new HttpProtocolException(400, "Bad Request", "Transfer type must be files or text.");
        }
        if (headers.ContainsKey("x-text-size"))
        {
            throw new HttpProtocolException(400, "Bad Request", "A transfer cannot combine text and files.");
        }

        if (!int.TryParse(GetHeader(headers, "x-file-count"), out var count) ||
            count is < 1 or > TransferProtocol.MaximumFiles)
        {
            throw new HttpProtocolException(400, "Bad Request", "A batch must contain 1 to 20 files.");
        }

        var files = new List<TransferFile>(count);
        long totalBytes = 0;
        for (var index = 0; index < count; index++)
        {
            var encodedName = GetHeader(headers, $"x-file-{index}-name");
            var sizeValue = GetHeader(headers, $"x-file-{index}-size");
            if (encodedName is null || !long.TryParse(sizeValue, out var size))
            {
                throw new HttpProtocolException(400, "Bad Request", "File metadata is incomplete.");
            }

            var name = FileNameRules.SanitizeFileName(TransferProtocol.DecodeHeaderValue(encodedName));
            if (size < 0 || size > TransferProtocol.MaximumFileBytes ||
                totalBytes > TransferProtocol.MaximumBatchBytes - size)
            {
                throw new HttpProtocolException(413, "Payload Too Large", "The batch must be smaller than 2 GiB.");
            }

            totalBytes += size;
            files.Add(new TransferFile(name, size));
        }

        return new TransferOffer(transferId!, senderName, senderAddress, files, totalBytes);
    }

    private bool TryReserveTextUpload(
        string transferId,
        long size,
        IPAddress senderAddress,
        out ApprovedTransfer? transfer)
    {
        lock (_approvedLock)
        {
            ExpireApprovedTransfers();
            if (!_approved.TryGetValue(transferId, out transfer) ||
                !transfer.Offer.IsText ||
                !transfer.Offer.SenderAddress.Equals(senderAddress) ||
                transfer.Offer.TotalBytes != size ||
                transfer.Offer.Files.Count != 1 ||
                transfer.Offer.Files[0].Size != size ||
                transfer.InProgress[0] ||
                transfer.Completed[0])
            {
                transfer = null;
                return false;
            }

            transfer.InProgress[0] = true;
            return true;
        }
    }

    private bool TryReserveUpload(
        string transferId,
        int index,
        string fileName,
        long size,
        IPAddress senderAddress,
        out ApprovedTransfer? transfer)
    {
        lock (_approvedLock)
        {
            ExpireApprovedTransfers();
            if (!_approved.TryGetValue(transferId, out transfer) ||
                transfer.Offer.IsText ||
                !transfer.Offer.SenderAddress.Equals(senderAddress) ||
                index < 0 || index >= transfer.Offer.Files.Count)
            {
                transfer = null;
                return false;
            }

            var expected = transfer.Offer.Files[index];
            if (transfer.InProgress[index] || transfer.Completed[index] ||
                expected.Size != size || !string.Equals(expected.Name, fileName, StringComparison.Ordinal))
            {
                transfer = null;
                return false;
            }

            transfer.InProgress[index] = true;
            return true;
        }
    }

    private void CompleteUpload(string transferId, ApprovedTransfer transfer, int index, bool succeeded)
    {
        lock (_approvedLock)
        {
            transfer.InProgress[index] = false;
            if (succeeded)
            {
                transfer.Completed[index] = true;
                if (transfer.Completed.All(completed => completed))
                {
                    _approved.TryRemove(transferId, out _);
                }
            }
        }
    }

    private void ExpireApprovedTransfers()
    {
        var cutoff = DateTimeOffset.UtcNow - ApprovedTransferLifetime;
        foreach (var entry in _approved)
        {
            var id = entry.Key;
            var transfer = entry.Value;
            if (transfer.CreatedAt < cutoff)
            {
                _approved.TryRemove(id, out _);
            }
        }
    }

    private async Task CopyExactlyAsync(
        Stream input,
        Stream output,
        long length,
        string transferId,
        int fileIndex,
        string fileName,
        CancellationToken cancellationToken)
    {
        var buffer = new byte[128 * 1024];
        var remaining = length;
        var transferred = 0L;
        var lastReport = Stopwatch.GetTimestamp();
        if (length == 0)
        {
            ProgressChanged?.Invoke(new TransferProgress(transferId, fileIndex, fileName, 0, 0));
            return;
        }

        while (remaining > 0)
        {
            var wanted = (int)Math.Min(buffer.Length, remaining);
            var count = await input.ReadAsync(buffer, 0, wanted, cancellationToken);
            if (count == 0)
            {
                throw new EndOfStreamException("Kết nối bị ngắt giữa chừng.");
            }

            await output.WriteAsync(buffer, 0, count, cancellationToken);
            remaining -= count;
            transferred += count;
            var now = Stopwatch.GetTimestamp();
            if (now - lastReport >= Stopwatch.Frequency / 6 || remaining == 0)
            {
                ProgressChanged?.Invoke(
                    new TransferProgress(transferId, fileIndex, fileName, transferred, length));
                lastReport = now;
            }
        }
    }

    private static bool TryGetContentLength(HttpRequest request, out long length)
    {
        var raw = GetHeader(request.Headers, "content-length");
        return long.TryParse(raw, out length) && length >= 0;
    }

    private static string? GetHeader(IReadOnlyDictionary<string, string> headers, string name) =>
        headers.TryGetValue(name, out var value) ? value : null;

    private static async Task<HttpRequest?> ReadRequestAsync(Stream stream, CancellationToken cancellationToken)
    {
        var requestLine = await ReadLineAsync(stream, MaximumHeaderBytes, cancellationToken);
        if (requestLine is null)
        {
            return null;
        }

        var parts = requestLine.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length != 3 || !parts[2].StartsWith("HTTP/", StringComparison.Ordinal))
        {
            throw new HttpProtocolException(400, "Bad Request", "Invalid request line.");
        }

        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var headerBytes = Encoding.ASCII.GetByteCount(requestLine) + 2;
        while (true)
        {
            var line = await ReadLineAsync(stream, MaximumHeaderBytes, cancellationToken);
            if (line is null)
            {
                throw new HttpProtocolException(400, "Bad Request", "Request headers ended unexpectedly.");
            }
            if (line.Length == 0)
            {
                break;
            }

            headerBytes += Encoding.ASCII.GetByteCount(line) + 2;
            if (headerBytes > MaximumHeaderBytes)
            {
                throw new HttpProtocolException(431, "Request Header Fields Too Large", "Headers are too large.");
            }

            var colon = line.IndexOf(':');
            if (colon <= 0)
            {
                throw new HttpProtocolException(400, "Bad Request", "Malformed request header.");
            }
            headers[line.Substring(0, colon).Trim()] = line.Substring(colon + 1).Trim();
        }

        return new HttpRequest(parts[0], parts[1], headers);
    }

    private static async Task<string?> ReadLineAsync(
        Stream stream,
        int maximumBytes,
        CancellationToken cancellationToken)
    {
        using var line = new MemoryStream();
        var oneByte = new byte[1];
        var previousWasCarriageReturn = false;
        while (line.Length <= maximumBytes)
        {
            var count = await stream.ReadAsync(oneByte, 0, oneByte.Length, cancellationToken);
            if (count == 0)
            {
                return line.Length == 0 ? null : throw new HttpProtocolException(400, "Bad Request", "Incomplete request line.");
            }

            var current = oneByte[0];
            if (previousWasCarriageReturn && current == (byte)'\n')
            {
                var bytes = line.ToArray();
                var length = bytes.Length > 0 && bytes[bytes.Length - 1] == (byte)'\r'
                    ? bytes.Length - 1
                    : bytes.Length;
                return Encoding.UTF8.GetString(bytes, 0, length);
            }

            line.WriteByte(current);
            previousWasCarriageReturn = current == (byte)'\r';
        }

        throw new HttpProtocolException(431, "Request Header Fields Too Large", "Headers are too large.");
    }

    private static async Task WriteResponseAsync(Stream stream, int status, string reason, string message)
    {
        var body = Encoding.UTF8.GetBytes(message);
        var header = Encoding.ASCII.GetBytes(
            $"HTTP/1.1 {status} {reason}\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            $"Content-Length: {body.Length}\r\n" +
            "Connection: close\r\n\r\n");
        await stream.WriteAsync(header, 0, header.Length);
        await stream.WriteAsync(body, 0, body.Length);
        await stream.FlushAsync();
    }

    private static async Task<T> WaitForOfferApprovalAsync<T>(
        Task<T> task,
        TimeSpan timeout,
        CancellationToken cancellationToken)
    {
        var delay = Task.Delay(timeout, cancellationToken);
        if (await Task.WhenAny(task, delay) != task)
        {
            cancellationToken.ThrowIfCancellationRequested();
            throw new TimeoutException();
        }

        return await task;
    }

    private async Task ReplyBusyAndCloseAsync(TcpClient client)
    {
        using (client)
        {
            try
            {
                await WriteResponseAsync(
                    client.GetStream(),
                    503,
                    "Busy",
                    "This device is handling other transfers.");
            }
            catch (Exception exception) when (
                exception is IOException or SocketException or ObjectDisposedException)
            {
            }
        }
    }

    private sealed record HttpRequest(
        string Method,
        string Path,
        IReadOnlyDictionary<string, string> Headers);

    private sealed record ApprovedTransfer(TransferOffer Offer)
    {
        public DateTimeOffset CreatedAt { get; } = DateTimeOffset.UtcNow;
        public bool[] InProgress { get; } = new bool[Offer.Files.Count];
        public bool[] Completed { get; } = new bool[Offer.Files.Count];
    }

    private sealed class HttpProtocolException(int statusCode, string reason, string message)
        : Exception(message)
    {
        public int StatusCode { get; } = statusCode;
        public string Reason { get; } = reason;
    }
}