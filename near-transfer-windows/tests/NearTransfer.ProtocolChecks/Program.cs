using NearTransfer.Core;
using System.Net;
using System.Net.Sockets;
using System.Text;

await RunProtocolChecksAsync();
Console.WriteLine("All Near Transfer protocol checks passed.");

static async Task RunProtocolChecksAsync()
{
    Check(TransferProtocol.IsTransferId(TransferProtocol.CreateTransferId()), "transfer ID format");
    Check(!TransferProtocol.IsTransferId("ABC"), "reject malformed transfer IDs");
    Check(
        TransferProtocol.DecodeHeaderValue(TransferProtocol.EncodeHeaderValue("Phone + ảnh.txt")) ==
        "Phone + ảnh.txt",
        "form-url-encoded headers round-trip");
    Check(
        FileNameRules.SanitizeFileName(@"..\..\CON.txt") == "_CON.txt",
        "sanitize path traversal and Windows device names");
    Check(
        TransferProtocol.TryParsePeerReply(
            "NWS1|PEER|45321|Android+Phone",
            IPAddress.Parse("192.168.1.25"),
            out var peer) &&
        peer is { Name: "Android Phone", Port: 45321 },
        "parse Android discovery replies");

    await CheckAndroidToDesktopReceiveAsync();
    await CheckAndroidToDesktopTextReceiveAsync();
    await CheckDeclinedOfferCannotUploadAsync();
    await CheckDesktopToAndroidSendAsync();
    await CheckDesktopToAndroidTextSendAsync();
}

static async Task CheckAndroidToDesktopReceiveAsync()
{
    var directory = Path.Combine(Path.GetTempPath(), $"near-transfer-receive-{Guid.NewGuid():N}");
    Directory.CreateDirectory(directory);
    var body = Encoding.UTF8.GetBytes("Android to Windows file payload");
    var transferId = TransferProtocol.CreateTransferId();
    TransferOffer? capturedOffer = null;

    try
    {
        await using var server = new LanTransferServer(() => directory, port: 0);
        server.OfferReceived += offer =>
        {
            capturedOffer = offer;
            return Task.FromResult(true);
        };
        server.Start();

        var offerRequest = new StringBuilder()
            .AppendLine("POST /offer HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine("Content-Length: 0")
            .AppendLine($"X-Transfer-Id: {transferId}")
            .AppendLine("X-Transfer-Type: files")
            .AppendLine($"X-Sender-Name: {TransferProtocol.EncodeHeaderValue("Android + Phone")}")
            .AppendLine("X-File-Count: 1")
            .AppendLine($"X-File-0-Name: {TransferProtocol.EncodeHeaderValue("ảnh + note.txt")}")
            .AppendLine($"X-File-0-Size: {body.Length}")
            .AppendLine()
            .ToString();

        var offerResponse = await PostRawAsync(server.LocalPort, offerRequest, []);
        Check(offerResponse.StatusCode == 200, "approve Android file offer");
        Check(capturedOffer is
            {
                SenderName: "Android + Phone",
                Files.Count: 1,
                TotalBytes: 31
            }, "decode Android offer details");
        Check(capturedOffer!.Files[0].Name == "ảnh + note.txt", "decode Android file name");

        var uploadRequest = new StringBuilder()
            .AppendLine("POST /upload HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine("Content-Type: application/octet-stream")
            .AppendLine($"Content-Length: {body.Length}")
            .AppendLine($"X-Transfer-Id: {transferId}")
            .AppendLine("X-File-Index: 0")
            .AppendLine($"X-File-Name: {TransferProtocol.EncodeHeaderValue("ảnh + note.txt")}")
            .AppendLine()
            .ToString();
        var uploadResponse = await PostRawAsync(server.LocalPort, uploadRequest, body);
        Check(uploadResponse.StatusCode == 201, "accept Android file upload");

        var receivedPath = Path.Combine(directory, "ảnh + note.txt");
        Check(File.Exists(receivedPath), "create received file");
        Check((await File.ReadAllBytesAsync(receivedPath)).SequenceEqual(body), "preserve received bytes");

        using var duplicate = FileNameRules.CreateUniqueFile(directory, "ảnh + note.txt", out var duplicatePath);
        Check(Path.GetFileName(duplicatePath) == "ảnh + note (1).txt", "avoid overwriting an existing file");
    }
    finally
    {
        Directory.Delete(directory, recursive: true);
    }
}

static async Task CheckAndroidToDesktopTextReceiveAsync()
{
    var directory = Path.Combine(Path.GetTempPath(), $"near-transfer-text-receive-{Guid.NewGuid():N}");
    Directory.CreateDirectory(directory);
    var message = "Xin chào 🐱\r\nTin nhắn từ Android.";
    var body = Encoding.UTF8.GetBytes(message);
    var transferId = TransferProtocol.CreateTransferId();
    TransferOffer? capturedOffer = null;
    var receivedText = new TaskCompletionSource<ReceivedText>(
        TaskCreationOptions.RunContinuationsAsynchronously);

    try
    {
        await using var server = new LanTransferServer(() => directory, port: 0);
        server.OfferReceived += offer =>
        {
            capturedOffer = offer;
            return Task.FromResult(true);
        };
        server.TextReceived += text => receivedText.TrySetResult(text);
        server.Start();

        var offerRequest = new StringBuilder()
            .AppendLine("POST /offer HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine("Content-Length: 0")
            .AppendLine($"X-Transfer-Id: {transferId}")
            .AppendLine("X-Transfer-Type: text")
            .AppendLine($"X-Sender-Name: {TransferProtocol.EncodeHeaderValue("Android + Phone")}")
            .AppendLine($"X-Text-Size: {body.Length}")
            .AppendLine()
            .ToString();

        var offerResponse = await PostRawAsync(server.LocalPort, offerRequest, []);
        Check(offerResponse.StatusCode == 200, "approve Android text offer");
        Check(capturedOffer is
            {
                SenderName: "Android + Phone",
                IsText: true,
                Files.Count: 1
            }, "parse Android text offer as one text item");
        Check(capturedOffer!.Files[0].Name == "Tin nhắn văn bản", "label offered text item");
        Check(capturedOffer.TotalBytes == body.Length, "preserve UTF-8 byte size in text offer");

        var mismatchedUpload = new StringBuilder()
            .AppendLine("POST /upload-text HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine($"Content-Length: {body.Length - 1}")
            .AppendLine($"X-Transfer-Id: {transferId}")
            .AppendLine()
            .ToString();
        var mismatchedResponse = await PostRawAsync(
            server.LocalPort,
            mismatchedUpload,
            body[..^1]);
        Check(mismatchedResponse.StatusCode == 403, "reject text upload with mismatched byte size");

        var uploadRequest = new StringBuilder()
            .AppendLine("POST /upload-text HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine("Content-Type: text/plain; charset=utf-8")
            .AppendLine($"Content-Length: {body.Length}")
            .AppendLine($"X-Transfer-Id: {transferId}")
            .AppendLine()
            .ToString();
        var uploadResponse = await PostRawAsync(server.LocalPort, uploadRequest, body);
        Check(uploadResponse.StatusCode == 201, "accept Android text upload");

        var received = await receivedText.Task.WaitAsync(TimeSpan.FromSeconds(3));
        Check(received.SenderName == "Android + Phone", "retain text sender name");
        Check(received.Text == message, "decode received UTF-8 text exactly");
        Check(received.ByteLength == body.Length, "report received UTF-8 byte length");
        Check(!Directory.EnumerateFiles(directory).Any(), "do not store received text as a file");

        var mixedRequest = new StringBuilder()
            .AppendLine("POST /offer HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine("Content-Length: 0")
            .AppendLine($"X-Transfer-Id: {TransferProtocol.CreateTransferId()}")
            .AppendLine("X-Transfer-Type: text")
            .AppendLine($"X-Sender-Name: {TransferProtocol.EncodeHeaderValue("Android")}")
            .AppendLine("X-Text-Size: 1")
            .AppendLine("X-File-Count: 1")
            .AppendLine()
            .ToString();
        var mixedResponse = await PostRawAsync(server.LocalPort, mixedRequest, []);
        Check(mixedResponse.StatusCode == 400, "reject a text offer that also declares files");
    }
    finally
    {
        Directory.Delete(directory, recursive: true);
    }
}

static async Task CheckDeclinedOfferCannotUploadAsync()
{
    var directory = Path.Combine(Path.GetTempPath(), $"near-transfer-decline-{Guid.NewGuid():N}");
    Directory.CreateDirectory(directory);
    var transferId = TransferProtocol.CreateTransferId();
    var body = Encoding.UTF8.GetBytes("must not be written");

    try
    {
        await using var server = new LanTransferServer(() => directory, port: 0);
        server.OfferReceived += _ => Task.FromResult(false);
        server.Start();

        var offerRequest = new StringBuilder()
            .AppendLine("POST /offer HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine("Content-Length: 0")
            .AppendLine($"X-Transfer-Id: {transferId}")
            .AppendLine("X-Transfer-Type: files")
            .AppendLine($"X-Sender-Name: {TransferProtocol.EncodeHeaderValue("Android")}")
            .AppendLine("X-File-Count: 1")
            .AppendLine($"X-File-0-Name: {TransferProtocol.EncodeHeaderValue("blocked.txt")}")
            .AppendLine($"X-File-0-Size: {body.Length}")
            .AppendLine()
            .ToString();

        var offerResponse = await PostRawAsync(server.LocalPort, offerRequest, []);
        Check(offerResponse.StatusCode == 403, "decline transfer offer");

        var uploadRequest = new StringBuilder()
            .AppendLine("POST /upload HTTP/1.1")
            .AppendLine("Host: 127.0.0.1")
            .AppendLine("Connection: close")
            .AppendLine($"Content-Length: {body.Length}")
            .AppendLine($"X-Transfer-Id: {transferId}")
            .AppendLine("X-File-Index: 0")
            .AppendLine($"X-File-Name: {TransferProtocol.EncodeHeaderValue("blocked.txt")}")
            .AppendLine()
            .ToString();
        var uploadResponse = await PostRawAsync(server.LocalPort, uploadRequest, body);
        Check(uploadResponse.StatusCode == 403, "reject upload after declined offer");
        Check(!Directory.EnumerateFiles(directory).Any(), "write no file after decline");
    }
    finally
    {
        Directory.Delete(directory, recursive: true);
    }
}

static async Task CheckDesktopToAndroidSendAsync()
{
    var directory = Path.Combine(Path.GetTempPath(), $"near-transfer-send-{Guid.NewGuid():N}");
    Directory.CreateDirectory(directory);
    var sourcePath = Path.Combine(directory, "out + windows.txt");
    var source = Encoding.UTF8.GetBytes("Windows to Android file payload");
    await File.WriteAllBytesAsync(sourcePath, source);

    using var listener = new TcpListener(IPAddress.Loopback, 0);
    listener.Start();
    var port = ((IPEndPoint)listener.LocalEndpoint).Port;
    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
    var receiverTask = ReceiveFromDesktopAsync(listener, source, timeout.Token);

    try
    {
        using var sender = new LanTransferClient();
        var peer = new Peer("Android test", IPAddress.Loopback, port, DateTimeOffset.UtcNow);
        var reports = new List<TransferProgress>();
        await sender.SendFilesAsync(
            peer,
            [sourcePath],
            "Windows PC",
            reports.Add,
            timeout.Token);

        var received = await receiverTask.WaitAsync(timeout.Token);
        Check(received.Headers.GetValueOrDefault("x-transfer-type") == "files", "send Android-compatible offer");
        Check(received.Headers.GetValueOrDefault("x-sender-name") == "Windows+PC", "encode sender name");
        Check(received.UploadHeaders.GetValueOrDefault("x-file-name") == "out+%2B+windows.txt", "encode uploaded file name");
        Check(received.Bytes.SequenceEqual(source), "stream file bytes to Android");
        Check(reports.Count > 0 && reports[^1].TransferredBytes == source.Length, "report outbound progress");
    }
    finally
    {
        listener.Stop();
        Directory.Delete(directory, recursive: true);
    }
}

static async Task CheckDesktopToAndroidTextSendAsync()
{
    const string message = "Văn bản từ Windows 🐱\r\nDòng thứ hai.";
    var source = Encoding.UTF8.GetBytes(message);
    using var listener = new TcpListener(IPAddress.Loopback, 0);
    listener.Start();
    var port = ((IPEndPoint)listener.LocalEndpoint).Port;
    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
    var receiverTask = ReceiveTextFromDesktopAsync(listener, source, timeout.Token);

    using var sender = new LanTransferClient();
    var peer = new Peer("Android test", IPAddress.Loopback, port, DateTimeOffset.UtcNow);
    var reports = new List<TransferProgress>();
    await sender.SendTextAsync(
        peer,
        message,
        "Windows PC",
        reports.Add,
        timeout.Token);

    var received = await receiverTask.WaitAsync(timeout.Token);
    Check(received.OfferHeaders.GetValueOrDefault("x-transfer-type") == "text",
        "send Android-compatible text offer");
    Check(received.OfferHeaders.GetValueOrDefault("x-text-size") == source.Length.ToString(),
        "declare exact UTF-8 text size");
    Check(received.OfferHeaders.GetValueOrDefault("x-sender-name") == "Windows+PC",
        "encode text sender name");
    Check(received.UploadHeaders.GetValueOrDefault("x-transfer-id") ==
          received.OfferHeaders.GetValueOrDefault("x-transfer-id"),
        "use the approved ID for text upload");
    Check(received.UploadHeaders.GetValueOrDefault("content-length") == source.Length.ToString(),
        "announce exact UTF-8 upload length");
    Check(received.Bytes.SequenceEqual(source), "send UTF-8 text bytes to Android");
    Check(reports.Count > 0 && reports[^1].TransferredBytes == source.Length,
        "report outbound text progress");
}

static async Task<FakeAndroidTextReceive> ReceiveTextFromDesktopAsync(
    TcpListener listener,
    byte[] expectedBytes,
    CancellationToken cancellationToken)
{
    using var offerClient = await listener.AcceptTcpClientAsync(cancellationToken);
    var offerStream = offerClient.GetStream();
    var offer = await ReadRequestAsync(offerStream, cancellationToken);
    Check(offer.Path == "/offer", "Android peer receives text offer endpoint");
    Check(offer.ContentLength == 0, "text offer body is empty");
    await WriteResponseAsync(offerStream, 200, "OK", cancellationToken);

    using var uploadClient = await listener.AcceptTcpClientAsync(cancellationToken);
    var uploadStream = uploadClient.GetStream();
    var upload = await ReadRequestAsync(uploadStream, cancellationToken);
    Check(upload.Path == "/upload-text", "Android peer receives text upload endpoint");
    Check(upload.ContentLength == expectedBytes.Length, "announce exact text body length");
    var bytes = await ReadExactlyAsync(uploadStream, upload.ContentLength, cancellationToken);
    await WriteResponseAsync(uploadStream, 201, "Created", cancellationToken);
    return new FakeAndroidTextReceive(offer.Headers, upload.Headers, bytes);
}

static async Task<FakeAndroidReceive> ReceiveFromDesktopAsync(
    TcpListener listener,
    byte[] expectedBytes,
    CancellationToken cancellationToken)
{
    using var offerClient = await listener.AcceptTcpClientAsync(cancellationToken);
    var offerStream = offerClient.GetStream();
    var offer = await ReadRequestAsync(offerStream, cancellationToken);
    Check(offer.Path == "/offer", "Android peer receives offer endpoint");
    Check(offer.ContentLength == 0, "offer body is empty");
    await WriteResponseAsync(offerStream, 200, "OK", cancellationToken);

    using var uploadClient = await listener.AcceptTcpClientAsync(cancellationToken);
    var uploadStream = uploadClient.GetStream();
    var upload = await ReadRequestAsync(uploadStream, cancellationToken);
    Check(upload.Path == "/upload", "Android peer receives file endpoint");
    Check(upload.ContentLength == expectedBytes.Length, "announce exact upload length");
    var bytes = await ReadExactlyAsync(uploadStream, upload.ContentLength, cancellationToken);
    await WriteResponseAsync(uploadStream, 201, "Created", cancellationToken);
    return new FakeAndroidReceive(offer.Headers, upload.Headers, bytes);
}

static async Task<RawResponse> PostRawAsync(int port, string headers, byte[] body)
{
    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
    using var client = new TcpClient();
    await client.ConnectAsync(IPAddress.Loopback, port, timeout.Token);
    var stream = client.GetStream();
    var normalizedHeaders = headers.Replace("\r\n", "\n").Replace("\n", "\r\n");
    await stream.WriteAsync(Encoding.UTF8.GetBytes(normalizedHeaders), timeout.Token);
    await stream.WriteAsync(body, timeout.Token);
    return await ReadResponseAsync(stream, timeout.Token);
}

static async Task<RawRequest> ReadRequestAsync(Stream stream, CancellationToken cancellationToken)
{
    var requestLine = await ReadLineAsync(stream, cancellationToken);
    var parts = requestLine.Split(' ', StringSplitOptions.RemoveEmptyEntries);
    var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    while (true)
    {
        var line = await ReadLineAsync(stream, cancellationToken);
        if (line.Length == 0)
        {
            break;
        }
        var colon = line.IndexOf(':');
        headers[line[..colon].Trim()] = line[(colon + 1)..].Trim();
    }
    var length = long.Parse(headers.GetValueOrDefault("content-length") ?? "0");
    return new RawRequest(parts[1], headers, length);
}

static async Task<RawResponse> ReadResponseAsync(Stream stream, CancellationToken cancellationToken)
{
    var statusLine = await ReadLineAsync(stream, cancellationToken);
    var parts = statusLine.Split(' ', 3, StringSplitOptions.RemoveEmptyEntries);
    var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    while (true)
    {
        var line = await ReadLineAsync(stream, cancellationToken);
        if (line.Length == 0)
        {
            break;
        }
        var colon = line.IndexOf(':');
        headers[line[..colon].Trim()] = line[(colon + 1)..].Trim();
    }
    var length = long.Parse(headers.GetValueOrDefault("content-length") ?? "0");
    _ = await ReadExactlyAsync(stream, length, cancellationToken);
    return new RawResponse(int.Parse(parts[1]));
}

static async Task WriteResponseAsync(
    Stream stream,
    int status,
    string reason,
    CancellationToken cancellationToken)
{
    var body = Encoding.UTF8.GetBytes("ok");
    var header = Encoding.ASCII.GetBytes(
        $"HTTP/1.1 {status} {reason}\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n");
    await stream.WriteAsync(header, cancellationToken);
    await stream.WriteAsync(body, cancellationToken);
    await stream.FlushAsync(cancellationToken);
}

static async Task<byte[]> ReadExactlyAsync(Stream stream, long length, CancellationToken cancellationToken)
{
    if (length > int.MaxValue)
    {
        throw new InvalidOperationException("Test payload is unexpectedly large.");
    }

    var bytes = new byte[(int)length];
    var offset = 0;
    while (offset < bytes.Length)
    {
        var count = await stream.ReadAsync(bytes.AsMemory(offset), cancellationToken);
        if (count == 0)
        {
            throw new EndOfStreamException();
        }
        offset += count;
    }
    return bytes;
}

static async Task<string> ReadLineAsync(Stream stream, CancellationToken cancellationToken)
{
    using var line = new MemoryStream();
    var oneByte = new byte[1];
    var previousWasCarriageReturn = false;
    while (true)
    {
        var count = await stream.ReadAsync(oneByte.AsMemory(), cancellationToken);
        if (count == 0)
        {
            throw new EndOfStreamException();
        }

        if (previousWasCarriageReturn && oneByte[0] == (byte)'\n')
        {
            var bytes = line.ToArray();
            return Encoding.UTF8.GetString(bytes, 0, bytes.Length - 1);
        }
        line.WriteByte(oneByte[0]);
        previousWasCarriageReturn = oneByte[0] == (byte)'\r';
    }
}

static void Check(bool condition, string name)
{
    if (!condition)
    {
        throw new InvalidOperationException($"Protocol check failed: {name}");
    }
}

sealed record RawRequest(string Path, Dictionary<string, string> Headers, long ContentLength);
sealed record RawResponse(int StatusCode);
sealed record FakeAndroidReceive(
    Dictionary<string, string> Headers,
    Dictionary<string, string> UploadHeaders,
    byte[] Bytes);
sealed record FakeAndroidTextReceive(
    Dictionary<string, string> OfferHeaders,
    Dictionary<string, string> UploadHeaders,
    byte[] Bytes);