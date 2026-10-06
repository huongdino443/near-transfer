using System.Net;
using System.Security.Cryptography;
using System.Text;

namespace NearTransfer.Core;

public static class TransferProtocol
{
    public const int DiscoveryPort = 45322;
    public const int HttpPort = 45321;
    public const int MaximumFiles = 20;
    public const int MaximumTextBytes = 256 * 1024;
    public const long MaximumFileBytes = int.MaxValue;
    public const long MaximumBatchBytes = int.MaxValue;

    public static string CreateTransferId()
    {
        var randomBytes = new byte[16];
        using (var generator = RandomNumberGenerator.Create())
        {
            generator.GetBytes(randomBytes);
        }

        return BitConverter.ToString(randomBytes).Replace("-", string.Empty).ToLowerInvariant();
    }

    public static string EncodeHeaderValue(string value) =>
        WebUtility.UrlEncode(value);

    public static string DecodeHeaderValue(string value) =>
        WebUtility.UrlDecode(value);

    public static bool IsTransferId(string? value)
    {
        if (value is null || value.Length != 32)
        {
            return false;
        }

        foreach (var character in value)
        {
            if (!(character is >= '0' and <= '9') &&
                !(character is >= 'a' and <= 'f'))
            {
                return false;
            }
        }

        return true;
    }

    public static string BuildDiscoveryRequest(string deviceName) =>
        $"NWS1|DISCOVER|{HttpPort}|{EncodeHeaderValue(deviceName)}";

    public static string BuildPeerReply(string deviceName) =>
        $"NWS1|PEER|{HttpPort}|{EncodeHeaderValue(deviceName)}";

    public static bool TryParsePeerReply(string message, IPAddress address, out Peer? peer)
    {
        peer = null;
        var parts = message.Split('|');
        if (parts.Length != 4 ||
            !string.Equals(parts[0], "NWS1", StringComparison.Ordinal) ||
            !string.Equals(parts[1], "PEER", StringComparison.Ordinal) ||
            !int.TryParse(parts[2], out var port) ||
            port is < 1 or > 65535)
        {
            return false;
        }

        try
        {
            var name = FileNameRules.SanitizeFileName(DecodeHeaderValue(parts[3]));
            peer = new Peer(name, NormalizeAddress(address), port, DateTimeOffset.UtcNow);
            return true;
        }
        catch (ArgumentException)
        {
            return false;
        }
    }

    public static IPAddress NormalizeAddress(IPAddress address) =>
        address.IsIPv4MappedToIPv6 ? address.MapToIPv4() : address;
}