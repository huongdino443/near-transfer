using System.Diagnostics;
using System.Net;
using System.Net.Http;
using System.Text;

namespace NearTransfer.Core;

public sealed class LanTransferClient : IDisposable
{
    private static readonly TimeSpan OfferTimeout = TimeSpan.FromSeconds(125);
    private readonly HttpClient _httpClient;

    public LanTransferClient()
    {
        var handler = new HttpClientHandler
        {
            AllowAutoRedirect = false,
            UseProxy = false
        };
        _httpClient = new HttpClient(handler)
        {
            Timeout = Timeout.InfiniteTimeSpan
        };
    }

    public async Task SendFilesAsync(
        Peer peer,
        IReadOnlyList<string> paths,
        string senderName,
        Action<TransferProgress>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var files = DescribeFiles(paths);
        var transferId = TransferProtocol.CreateTransferId();
        await SubmitOfferAsync(peer, senderName, transferId, files, cancellationToken);

        for (var index = 0; index < files.Count; index++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var file = files[index];
            await UploadFileAsync(peer, transferId, index, file, progress, cancellationToken);
        }
    }

    public async Task SendTextAsync(
        Peer peer,
        string text,
        string senderName,
        Action<TransferProgress>? progress = null,
        CancellationToken cancellationToken = default)
    {
        if (text is null)
        {
            throw new ArgumentNullException(nameof(text));
        }
        var bytes = Encoding.UTF8.GetBytes(text);
        if (bytes.Length is < 1 or > TransferProtocol.MaximumTextBytes)
        {
            throw new ArgumentException("Văn bản phải từ 1 byte đến 256 KB.", nameof(text));
        }

        var transferId = TransferProtocol.CreateTransferId();
        await SubmitTextOfferAsync(peer, senderName, transferId, bytes.Length, cancellationToken);
        await UploadTextAsync(peer, transferId, bytes, progress, cancellationToken);
    }

    public void Dispose() => _httpClient.Dispose();

    private async Task SubmitOfferAsync(
        Peer peer,
        string senderName,
        string transferId,
        IReadOnlyList<SendFile> files,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, CreateUri(peer, "/offer"));
        request.Headers.ConnectionClose = true;
        request.Headers.ExpectContinue = false;
        AddHeader(request, "X-Transfer-Id", transferId);
        AddHeader(request, "X-Transfer-Type", "files");
        AddHeader(request, "X-Sender-Name", TransferProtocol.EncodeHeaderValue(senderName));
        AddHeader(request, "X-File-Count", files.Count.ToString());
        for (var index = 0; index < files.Count; index++)
        {
            AddHeader(request, $"X-File-{index}-Name", TransferProtocol.EncodeHeaderValue(files[index].Name));
            AddHeader(request, $"X-File-{index}-Size", files[index].Size.ToString());
        }
        request.Content = new ByteArrayContent(Array.Empty<byte>());
        request.Content.Headers.ContentLength = 0;

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(OfferTimeout);
        using var response = await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            timeout.Token);

        if (response.StatusCode == HttpStatusCode.OK)
        {
            return;
        }
        if (response.StatusCode == HttpStatusCode.Forbidden)
        {
            throw new TransferRejectedException("Thiết bị bên kia đã từ chối hoặc chưa xác nhận.");
        }

        throw new HttpRequestException(
            $"Thiết bị bên kia trả về HTTP {(int)response.StatusCode}.");
    }

    private async Task UploadFileAsync(
        Peer peer,
        string transferId,
        int index,
        SendFile file,
        Action<TransferProgress>? progress,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, CreateUri(peer, "/upload"));
        request.Headers.ConnectionClose = true;
        request.Headers.ExpectContinue = false;
        AddHeader(request, "X-Transfer-Id", transferId);
        AddHeader(request, "X-File-Index", index.ToString());
        AddHeader(request, "X-File-Name", TransferProtocol.EncodeHeaderValue(file.Name));
        request.Content = new ProgressFileContent(file, transferId, index, progress);
        request.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
        request.Content.Headers.ContentLength = file.Size;

        using var response = await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
        if (response.StatusCode != HttpStatusCode.Created)
        {
            throw new HttpRequestException(
                $"Không gửi được {file.Name} (HTTP {(int)response.StatusCode}).");
        }
    }

    private async Task SubmitTextOfferAsync(
        Peer peer,
        string senderName,
        string transferId,
        int textSize,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, CreateUri(peer, "/offer"));
        request.Headers.ConnectionClose = true;
        request.Headers.ExpectContinue = false;
        AddHeader(request, "X-Transfer-Id", transferId);
        AddHeader(request, "X-Transfer-Type", "text");
        AddHeader(request, "X-Sender-Name", TransferProtocol.EncodeHeaderValue(senderName));
        AddHeader(request, "X-Text-Size", textSize.ToString());
        request.Content = new ByteArrayContent(Array.Empty<byte>());
        request.Content.Headers.ContentLength = 0;

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(OfferTimeout);
        using var response = await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            timeout.Token);
        if (response.StatusCode == HttpStatusCode.OK)
        {
            return;
        }
        if (response.StatusCode == HttpStatusCode.Forbidden)
        {
            throw new TransferRejectedException("Thiết bị bên kia đã từ chối hoặc chưa xác nhận.");
        }

        throw new HttpRequestException(
            $"Thiết bị bên kia trả về HTTP {(int)response.StatusCode}.");
    }

    private async Task UploadTextAsync(
        Peer peer,
        string transferId,
        byte[] bytes,
        Action<TransferProgress>? progress,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, CreateUri(peer, "/upload-text"));
        request.Headers.ConnectionClose = true;
        request.Headers.ExpectContinue = false;
        AddHeader(request, "X-Transfer-Id", transferId);
        request.Content = new ProgressByteArrayContent(bytes, transferId, progress);
        request.Content.Headers.ContentType =
            new System.Net.Http.Headers.MediaTypeHeaderValue("text/plain") { CharSet = "utf-8" };

        using var response = await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
        if (response.StatusCode != HttpStatusCode.Created)
        {
            throw new HttpRequestException(
                $"Không gửi được văn bản (HTTP {(int)response.StatusCode}).");
        }
    }

    private static List<SendFile> DescribeFiles(IReadOnlyList<string> paths)
    {
        if (paths.Count is < 1 or > TransferProtocol.MaximumFiles)
        {
            throw new ArgumentException("Có thể gửi từ 1 đến 20 file mỗi lần.", nameof(paths));
        }

        var result = new List<SendFile>(paths.Count);
        long totalBytes = 0;
        foreach (var path in paths)
        {
            var fullPath = Path.GetFullPath(path);
            var info = new FileInfo(fullPath);
            if (!info.Exists)
            {
                throw new FileNotFoundException("Không tìm thấy file đã chọn.", fullPath);
            }

            if (info.Length < 0 || info.Length > TransferProtocol.MaximumFileBytes ||
                totalBytes > TransferProtocol.MaximumBatchBytes - info.Length)
            {
                throw new ArgumentException("Tổng dung lượng mỗi lần gửi phải nhỏ hơn 2 GiB.", nameof(paths));
            }

            totalBytes += info.Length;
            result.Add(new SendFile(
                fullPath,
                FileNameRules.SanitizeFileName(info.Name),
                info.Length));
        }

        return result;
    }

    private static Uri CreateUri(Peer peer, string path) =>
        new UriBuilder("http", peer.Address.ToString(), peer.Port, path).Uri;

    private static void AddHeader(HttpRequestMessage request, string name, string value)
    {
        if (!request.Headers.TryAddWithoutValidation(name, value))
        {
            throw new InvalidOperationException($"Could not add {name} header.");
        }
    }

    private sealed record SendFile(string Path, string Name, long Size);

    private sealed class ProgressByteArrayContent(
        byte[] bytes,
        string transferId,
        Action<TransferProgress>? progress) : HttpContent
    {
        protected override bool TryComputeLength(out long length)
        {
            length = bytes.LongLength;
            return true;
        }

#if NET48
        protected override Task SerializeToStreamAsync(Stream stream, System.Net.TransportContext context) =>
            SerializeContentAsync(stream, CancellationToken.None);
#else
        protected override Task SerializeToStreamAsync(Stream stream, System.Net.TransportContext? context) =>
            SerializeContentAsync(stream, CancellationToken.None);

        protected override Task SerializeToStreamAsync(
            Stream stream,
            System.Net.TransportContext? context,
            CancellationToken cancellationToken) =>
            SerializeContentAsync(stream, cancellationToken);
#endif

        private async Task SerializeContentAsync(Stream stream, CancellationToken cancellationToken)
        {
            const int chunkSize = 8192;
            var transferred = 0;
            while (transferred < bytes.Length)
            {
                var count = Math.Min(chunkSize, bytes.Length - transferred);
                await stream.WriteAsync(bytes, transferred, count, cancellationToken);
                transferred += count;
                progress?.Invoke(new TransferProgress(
                    transferId,
                    0,
                    "Tin nhắn văn bản",
                    transferred,
                    bytes.Length));
            }
        }
    }

    private sealed class ProgressFileContent(
        SendFile file,
        string transferId,
        int fileIndex,
        Action<TransferProgress>? progress) : HttpContent
    {
        protected override bool TryComputeLength(out long length)
        {
            length = file.Size;
            return true;
        }

#if NET48
        protected override Task SerializeToStreamAsync(Stream stream, System.Net.TransportContext context) =>
            SerializeContentAsync(stream, CancellationToken.None);
#else
        protected override Task SerializeToStreamAsync(Stream stream, System.Net.TransportContext? context) =>
            SerializeContentAsync(stream, CancellationToken.None);

        protected override Task SerializeToStreamAsync(
            Stream stream,
            System.Net.TransportContext? context,
            CancellationToken cancellationToken) =>
            SerializeContentAsync(stream, cancellationToken);
#endif

        private async Task SerializeContentAsync(Stream stream, CancellationToken cancellationToken)
        {
            using var input = new FileStream(
                file.Path,
                FileMode.Open,
                FileAccess.Read,
                FileShare.Read,
                128 * 1024,
                FileOptions.Asynchronous | FileOptions.SequentialScan);
            if (input.Length != file.Size)
            {
                throw new IOException($"Kích thước file thay đổi khi gửi: {file.Name}");
            }

            var buffer = new byte[128 * 1024];
            var transferred = 0L;
            var lastReport = Stopwatch.GetTimestamp();
            int count;
            while ((count = await input.ReadAsync(
                       buffer,
                       0,
                       buffer.Length,
                       cancellationToken)) > 0)
            {
                await stream.WriteAsync(buffer, 0, count, cancellationToken);
                transferred += count;
                var now = Stopwatch.GetTimestamp();
                if (now - lastReport >= Stopwatch.Frequency / 6 || transferred == file.Size)
                {
                    progress?.Invoke(new TransferProgress(
                        transferId,
                        fileIndex,
                        file.Name,
                        transferred,
                        file.Size));
                    lastReport = now;
                }
            }

            if (file.Size == 0)
            {
                progress?.Invoke(new TransferProgress(transferId, fileIndex, file.Name, 0, 0));
            }
            if (transferred != file.Size)
            {
                throw new IOException($"Kích thước file thay đổi khi gửi: {file.Name}");
            }
        }
    }
}