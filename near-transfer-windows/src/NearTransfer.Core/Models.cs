using System.Net;

namespace NearTransfer.Core;

public sealed record Peer(string Name, IPAddress Address, int Port, DateTimeOffset LastSeen)
{
    public string AddressText => Address.ToString();
}

public sealed record TransferFile(string Name, long Size);

public sealed record TransferOffer(
    string TransferId,
    string SenderName,
    IPAddress SenderAddress,
    IReadOnlyList<TransferFile> Files,
    long TotalBytes,
    bool IsText = false);

public sealed record TransferProgress(
    string TransferId,
    int FileIndex,
    string FileName,
    long TransferredBytes,
    long TotalBytes);

public sealed record ReceivedFile(string TransferId, string FileName, string Path, int FileNumber, int FileCount);

public sealed record ReceivedText(string TransferId, string SenderName, string Text, long ByteLength);

public sealed class TransferRejectedException(string message) : Exception(message);