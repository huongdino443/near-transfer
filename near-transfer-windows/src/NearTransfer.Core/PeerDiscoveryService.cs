using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;

namespace NearTransfer.Core;

#if !NET48
public sealed class PeerDiscoveryService : IAsyncDisposable
#else
public sealed class PeerDiscoveryService
#endif
{
    private static readonly TimeSpan PeerTimeout = TimeSpan.FromSeconds(10);
    private readonly string _deviceName;
    private readonly ConcurrentDictionary<string, Peer> _peers = new(StringComparer.Ordinal);
    private readonly HashSet<IPAddress> _localAddresses = [];
    private readonly SemaphoreSlim _scanGate = new(1, 1);
    private UdpClient? _client;
    private CancellationTokenSource? _stop;
    private Task? _receiveTask;
    private Task? _scanTask;

    public PeerDiscoveryService(string deviceName)
    {
        _deviceName = FileNameRules.SanitizeFileName(deviceName);
    }

    public event Action<IReadOnlyList<Peer>>? PeersChanged;
    public event Action<string>? Error;

    public IReadOnlyList<IPAddress> GetLocalIpv4Addresses() =>
        GetLocalAddresses().Distinct().OrderBy(address => address.ToString(), StringComparer.Ordinal).ToArray();

    public void Start()
    {
        if (_stop is not null)
        {
            return;
        }

        var client = new UdpClient(AddressFamily.InterNetwork);
        client.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        client.EnableBroadcast = true;
        client.Client.Bind(new IPEndPoint(IPAddress.Any, TransferProtocol.DiscoveryPort));
        _localAddresses.Clear();
        foreach (var address in GetLocalAddresses())
        {
            _localAddresses.Add(address);
        }

        _client = client;
        _stop = new CancellationTokenSource();
        _receiveTask = ReceiveLoopAsync(client, _stop.Token);
        _scanTask = ScanLoopAsync(client, _stop.Token);
    }

    public async Task RefreshAsync(CancellationToken cancellationToken = default)
    {
        var client = _client;
        if (client is null)
        {
            throw new InvalidOperationException("Peer discovery has not started.");
        }

        var request = Encoding.UTF8.GetBytes(TransferProtocol.BuildDiscoveryRequest(_deviceName));
        await _scanGate.WaitAsync(cancellationToken);
        try
        {
            foreach (var broadcast in GetBroadcastAddresses())
            {
                await SendDatagramAsync(
                    client,
                    request,
                    new IPEndPoint(broadcast, TransferProtocol.DiscoveryPort),
                    cancellationToken);
            }

            await SweepLocalSubnetsAsync(client, request, cancellationToken);
            ExpirePeers();
        }
        finally
        {
            _scanGate.Release();
        }
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
        _client?.Dispose();
        _client = null;
        var tasks = new[] { _receiveTask, _scanTask }.Where(task => task is not null).Cast<Task>();
        try
        {
            await Task.WhenAll(tasks);
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
            _receiveTask = null;
            _scanTask = null;
        }
    }

    private async Task ReceiveLoopAsync(UdpClient client, CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            UdpReceiveResult packet;
            try
            {
                packet = await client.ReceiveAsync();
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
                    Error?.Invoke($"Không nhận được gói tìm thiết bị: {exception.Message}");
                }
                continue;
            }

            var address = TransferProtocol.NormalizeAddress(packet.RemoteEndPoint.Address);
            if (_localAddresses.Contains(address))
            {
                continue;
            }

            var message = Encoding.UTF8.GetString(packet.Buffer);
            if (message.StartsWith("NWS1|DISCOVER|", StringComparison.Ordinal))
            {
                try
                {
                    var reply = Encoding.UTF8.GetBytes(TransferProtocol.BuildPeerReply(_deviceName));
                    await SendDatagramAsync(
                        client,
                        reply,
                        new IPEndPoint(address, packet.RemoteEndPoint.Port),
                        cancellationToken);
                }
                catch (Exception exception) when (
                    exception is SocketException or OperationCanceledException or ObjectDisposedException)
                {
                    if (!cancellationToken.IsCancellationRequested)
                    {
                        Error?.Invoke($"Không trả lời được thiết bị {address}: {exception.Message}");
                    }
                }
                continue;
            }

            if (TransferProtocol.TryParsePeerReply(message, address, out var peer) && peer is not null)
            {
                _peers[PeerKey(peer)] = peer;
                PublishPeers();
            }
        }
    }

    private async Task ScanLoopAsync(UdpClient client, CancellationToken cancellationToken)
    {
        var request = Encoding.UTF8.GetBytes(TransferProtocol.BuildDiscoveryRequest(_deviceName));
        var lastSweep = Stopwatch.GetTimestamp() - Stopwatch.Frequency * 20;

        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                await _scanGate.WaitAsync(cancellationToken);
                try
                {
                    foreach (var broadcast in GetBroadcastAddresses())
                    {
                        await SendDatagramAsync(
                            client,
                            request,
                            new IPEndPoint(broadcast, TransferProtocol.DiscoveryPort),
                            cancellationToken);
                    }

                    var now = Stopwatch.GetTimestamp();
                    if (now - lastSweep >= Stopwatch.Frequency * 10)
                    {
                        await SweepLocalSubnetsAsync(client, request, cancellationToken);
                        lastSweep = Stopwatch.GetTimestamp();
                    }
                }
                finally
                {
                    _scanGate.Release();
                }

                ExpirePeers();
                await Task.Delay(TimeSpan.FromSeconds(3), cancellationToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (SocketException exception)
            {
                Error?.Invoke($"Không thể tìm thiết bị trong mạng nội bộ: {exception.Message}");
                await Task.Delay(TimeSpan.FromSeconds(3), cancellationToken);
            }
        }
    }

    private async Task SweepLocalSubnetsAsync(
        UdpClient client,
        byte[] request,
        CancellationToken cancellationToken)
    {
        var targets = GetSubnetTargets().Take(512).ToArray();
        for (var index = 0; index < targets.Length; index++)
        {
            await SendDatagramAsync(
                client,
                request,
                new IPEndPoint(targets[index], TransferProtocol.DiscoveryPort),
                cancellationToken);
            if ((index + 1) % 16 == 0)
            {
                await Task.Delay(35, cancellationToken);
            }
        }
    }

    private static async Task SendDatagramAsync(
        UdpClient client,
        byte[] payload,
        IPEndPoint endpoint,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        await client.SendAsync(payload, payload.Length, endpoint);
        cancellationToken.ThrowIfCancellationRequested();
    }

    private void ExpirePeers()
    {
        var cutoff = DateTimeOffset.UtcNow - PeerTimeout;
        var changed = false;
        foreach (var entry in _peers)
        {
            var key = entry.Key;
            var peer = entry.Value;
            if (peer.LastSeen < cutoff && _peers.TryRemove(key, out _))
            {
                changed = true;
            }
        }
        if (changed)
        {
            PublishPeers();
        }
    }

    private void PublishPeers()
    {
        var current = _peers.Values
            .OrderBy(peer => peer.Name, StringComparer.CurrentCultureIgnoreCase)
            .ThenBy(peer => peer.AddressText, StringComparer.Ordinal)
            .ToArray();
        PeersChanged?.Invoke(current);
    }

    private IEnumerable<IPAddress> GetBroadcastAddresses()
    {
        var seen = new HashSet<IPAddress>();
        foreach (var network in GetNetworks())
        {
            if (seen.Add(network.Broadcast))
            {
                yield return network.Broadcast;
            }
        }
    }

    private IEnumerable<IPAddress> GetSubnetTargets()
    {
        var seen = new HashSet<IPAddress>();
        foreach (var network in GetNetworks().Where(network => network.PrefixLength is >= 23 and <= 30))
        {
            var hostCount = (1u << (32 - network.PrefixLength)) - 2;
            for (uint offset = 1; offset <= hostCount; offset++)
            {
                var address = FromUInt32(network.Network | offset);
                if (!_localAddresses.Contains(address) && seen.Add(address))
                {
                    yield return address;
                }
            }
        }
    }

    private IEnumerable<NetworkBlock> GetNetworks()
    {
        foreach (var networkInterface in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ShouldIgnoreInterface(networkInterface))
            {
                continue;
            }

            IPInterfaceProperties properties;
            try
            {
                properties = networkInterface.GetIPProperties();
            }
            catch (NetworkInformationException)
            {
                continue;
            }

            foreach (var unicast in properties.UnicastAddresses)
            {
                var address = unicast.Address;
                var prefix = unicast.PrefixLength;
                if (address.AddressFamily != AddressFamily.InterNetwork || prefix is < 1 or > 30)
                {
                    continue;
                }

                var addressValue = ToUInt32(address);
                var mask = uint.MaxValue << (32 - prefix);
                var network = addressValue & mask;
                var broadcast = network | ~mask;
                yield return new NetworkBlock(network, prefix, FromUInt32(broadcast));
            }
        }
    }

    private IEnumerable<IPAddress> GetLocalAddresses()
    {
        foreach (var networkInterface in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ShouldIgnoreInterface(networkInterface))
            {
                continue;
            }

            IPInterfaceProperties properties;
            try
            {
                properties = networkInterface.GetIPProperties();
            }
            catch (NetworkInformationException)
            {
                continue;
            }

            foreach (var unicast in properties.UnicastAddresses)
            {
                if (unicast.Address.AddressFamily == AddressFamily.InterNetwork)
                {
                    yield return unicast.Address;
                }
            }
        }
    }

    private static uint ToUInt32(IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        return ((uint)bytes[0] << 24) |
               ((uint)bytes[1] << 16) |
               ((uint)bytes[2] << 8) |
               bytes[3];
    }

    private static IPAddress FromUInt32(uint value) =>
        new(new[]
        {
            (byte)(value >> 24),
            (byte)(value >> 16),
            (byte)(value >> 8),
            (byte)value
        });

    private static bool ShouldIgnoreInterface(NetworkInterface networkInterface)
    {
        if (networkInterface.OperationalStatus != OperationalStatus.Up ||
            networkInterface.NetworkInterfaceType is NetworkInterfaceType.Loopback or
                NetworkInterfaceType.Tunnel or NetworkInterfaceType.Ppp)
        {
            return true;
        }

        var typeName = networkInterface.NetworkInterfaceType.ToString();
        return typeName.StartsWith("WWAN", StringComparison.OrdinalIgnoreCase) ||
               string.Equals(typeName, "Wman", StringComparison.OrdinalIgnoreCase);
    }

    private static string PeerKey(Peer peer) => $"{peer.Address}:{peer.Port}";

    private sealed record NetworkBlock(uint Network, int PrefixLength, IPAddress Broadcast);
}