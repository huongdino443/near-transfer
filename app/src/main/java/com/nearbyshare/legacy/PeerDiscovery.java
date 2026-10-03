package com.nearbyshare.legacy;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

final class PeerDiscovery {
    static final int DISCOVERY_PORT = 45322;
    private static final long BROADCAST_INTERVAL_MS = 2500L;
    private static final long PEER_TTL_MS = 10000L;
    private static final long UNICAST_SWEEP_INTERVAL_MS = 10000L;
    private static final long UNICAST_BATCH_INTERVAL_MS = 75L;
    private static final int UNICAST_BATCH_SIZE = 8;
    private static final int MAX_UNICAST_PROBES = 512;
    private static final int PASSIVE_LISTEN_TIMEOUT_MS = 5000;

    interface Listener {
        void onPeersChanged(ArrayList<Peer> peers);
    }

    private final Context context;
    private final Listener listener;
    private volatile boolean running;
    private volatile boolean searching;
    private volatile DatagramSocket socket;
    private Thread discoveryThread;

    PeerDiscovery(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        searching = true;
        discoveryThread = new Thread(new Runnable() {
            public void run() {
                runDiscovery();
            }
        }, "nearby-peer-discovery");
        discoveryThread.start();
    }

    synchronized void pauseSearching() {
        searching = false;
    }

    synchronized void stop() {
        running = false;
        searching = false;
        DatagramSocket current = socket;
        socket = null;
        if (current != null) {
            current.close();
        }
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
        }
    }

    private void runDiscovery() {
        DatagramSocket localSocket = null;
        HashMap<String, SeenPeer> peers = new HashMap<String, SeenPeer>();
        try {
            localSocket = new DatagramSocket(null);
            localSocket.setReuseAddress(true);
            localSocket.bind(new InetSocketAddress(DISCOVERY_PORT));
            localSocket.setBroadcast(true);
            localSocket.setSoTimeout(350);
            socket = localSocket;

            byte[] receiveBuffer = new byte[1024];
            ArrayList<InetAddress> probeTargets = new ArrayList<InetAddress>();
            int probeIndex = 0;
            long nextBroadcast = 0L;
            long nextUnicastSweep = 0L;
            long nextProbeBatch = 0L;
            boolean probingSubnet = false;
            while (running) {
                if (!searching && probingSubnet) {
                    probingSubnet = false;
                    probeTargets.clear();
                    probeIndex = 0;
                }
                long now = System.currentTimeMillis();
                if (searching && now >= nextBroadcast) {
                    sendDiscovery(localSocket);
                    nextBroadcast = now + BROADCAST_INTERVAL_MS;
                }

                if (searching && !probingSubnet && now >= nextUnicastSweep) {
                    probeTargets = getUnicastProbeTargets();
                    if (probeTargets.size() > 0) {
                        probingSubnet = true;
                        probeIndex = 0;
                        nextProbeBatch = now;
                    } else {
                        nextUnicastSweep = now + UNICAST_SWEEP_INTERVAL_MS;
                    }
                }
                if (searching && probingSubnet && now >= nextProbeBatch) {
                    int sent = 0;
                    while (running && searching &&
                            probeIndex < probeTargets.size() &&
                            sent < UNICAST_BATCH_SIZE) {
                        sendDiscovery(localSocket, probeTargets.get(probeIndex++));
                        sent++;
                    }
                    now = System.currentTimeMillis();
                    if (probeIndex >= probeTargets.size()) {
                        probingSubnet = false;
                        nextUnicastSweep = now + UNICAST_SWEEP_INTERVAL_MS;
                    } else {
                        nextProbeBatch = now + UNICAST_BATCH_INTERVAL_MS;
                    }
                }

                if (probingSubnet) {
                    long untilBatch = Math.max(1L,
                            Math.min(100L, nextProbeBatch - System.currentTimeMillis()));
                    localSocket.setSoTimeout((int) untilBatch);
                } else {
                    localSocket.setSoTimeout(searching ? 350 :
                            PASSIVE_LISTEN_TIMEOUT_MS);
                }
                DatagramPacket packet = new DatagramPacket(receiveBuffer,
                        receiveBuffer.length);
                try {
                    localSocket.receive(packet);
                    handlePacket(localSocket, packet, peers);
                } catch (SocketTimeoutException ignored) {
                }

                if (searching &&
                        removeExpired(peers, System.currentTimeMillis())) {
                    publish(peers);
                }
            }
        } catch (Exception e) {
            if (running && listener != null) {
                listener.onPeersChanged(new ArrayList<Peer>());
            }
        } finally {
            socket = null;
            if (localSocket != null) {
                localSocket.close();
            }
        }
    }

    private void sendDiscovery(DatagramSocket localSocket) {
        ArrayList<LocalNetwork> networks = getLocalNetworks();
        for (LocalNetwork network : networks) {
            sendDiscovery(localSocket, network.broadcastAddress);
        }
    }

    private void sendDiscovery(DatagramSocket localSocket, InetAddress target) {
        try {
            String message = "NWS1|DISCOVER|" + LanShareServer.HTTP_PORT + "|" +
                    ShareFiles.encodeHeaderValue(safeDeviceName());
            byte[] bytes = message.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                    target, DISCOVERY_PORT);
            localSocket.send(packet);
        } catch (Exception ignored) {
        }
    }

    private void handlePacket(DatagramSocket localSocket, DatagramPacket packet,
                              HashMap<String, SeenPeer> peers) {
        try {
            String message = new String(packet.getData(), packet.getOffset(),
                    packet.getLength(), "UTF-8");
            String[] parts = message.split("\\|", 4);
            if (parts.length != 4 || !"NWS1".equals(parts[0])) {
                return;
            }
            if ("DISCOVER".equals(parts[1])) {
                int port = Integer.parseInt(parts[2]);
                if (port < 1 || port > 65535) {
                    return;
                }
                String response = "NWS1|PEER|" + LanShareServer.HTTP_PORT + "|" +
                        ShareFiles.encodeHeaderValue(safeDeviceName());
                byte[] bytes = response.getBytes("UTF-8");
                DatagramPacket reply = new DatagramPacket(bytes, bytes.length,
                        packet.getAddress(), packet.getPort());
                localSocket.send(reply);
                return;
            }
            if (!searching) {
                return;
            }
            if (!"PEER".equals(parts[1])) {
                return;
            }

            int port = Integer.parseInt(parts[2]);
            if (port < 1 || port > 65535) {
                return;
            }
            String address = packet.getAddress().getHostAddress();
            if (isLocalWifiAddress(packet.getAddress())) {
                return;
            }
            String name = ShareFiles.decodeHeaderValue(parts[3]);
            String key = address + ":" + port;
            SeenPeer current = peers.get(key);
            String safeName = name == null || name.length() == 0 ?
                    "Thiết bị Android" : name;
            if (current == null || !current.peer.name.equals(safeName)) {
                peers.put(key, new SeenPeer(new Peer(safeName, address, port),
                        System.currentTimeMillis()));
                publish(peers);
            } else {
                current.lastSeen = System.currentTimeMillis();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean removeExpired(HashMap<String, SeenPeer> peers, long now) {
        boolean changed = false;
        Iterator<Map.Entry<String, SeenPeer>> iterator =
                peers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SeenPeer> entry = iterator.next();
            if (now - entry.getValue().lastSeen > PEER_TTL_MS) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private void publish(HashMap<String, SeenPeer> peers) {
        if (listener == null) {
            return;
        }
        ArrayList<Peer> snapshot = new ArrayList<Peer>();
        for (SeenPeer seenPeer : peers.values()) {
            snapshot.add(seenPeer.peer);
        }
        listener.onPeersChanged(snapshot);
    }

    private ArrayList<LocalNetwork> getLocalNetworks() {
        ArrayList<LocalNetwork> networks = new ArrayList<LocalNetwork>();
        HashSet<String> seen = new HashSet<String>();
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (isCellularInterface(networkInterface.getName())) {
                        continue;
                    }
                    try {
                        if (!networkInterface.isUp() ||
                                networkInterface.isLoopback() ||
                                networkInterface.isPointToPoint()) {
                            continue;
                        }
                        for (InterfaceAddress interfaceAddress :
                                networkInterface.getInterfaceAddresses()) {
                            InetAddress address = interfaceAddress.getAddress();
                            InetAddress broadcast =
                                    interfaceAddress.getBroadcast();
                            short prefixLength =
                                    interfaceAddress.getNetworkPrefixLength();
                            if (!(address instanceof Inet4Address) ||
                                    !(broadcast instanceof Inet4Address) ||
                                    address.isAnyLocalAddress() ||
                                    address.isLoopbackAddress() ||
                                    prefixLength < 1 || prefixLength > 30) {
                                continue;
                            }
                            addLocalNetwork(networks, seen, address, broadcast,
                                    prefixLength);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Some older Android builds expose the connected Wi-Fi details through
        // WifiManager but omit them from NetworkInterface enumeration.
        if (networks.size() == 0) {
            addWifiManagerNetwork(networks, seen);
        }
        return networks;
    }

    private void addWifiManagerNetwork(ArrayList<LocalNetwork> networks,
                                       HashSet<String> seen) {
        try {
            WifiManager wifi = (WifiManager)
                    context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || !wifi.isWifiEnabled()) {
                return;
            }
            WifiInfo wifiInfo = wifi.getConnectionInfo();
            if (wifiInfo == null || wifiInfo.getIpAddress() == 0) {
                return;
            }
            DhcpInfo dhcp = wifi.getDhcpInfo();
            if (dhcp == null || dhcp.ipAddress == 0 || dhcp.netmask == 0 ||
                    dhcp.ipAddress != wifiInfo.getIpAddress()) {
                return;
            }
            long addressValue = Integer.reverseBytes(dhcp.ipAddress) & 0xffffffffL;
            long netmask = Integer.reverseBytes(dhcp.netmask) & 0xffffffffL;
            int prefixLength = getPrefixLength(netmask);
            if (prefixLength < 1 || prefixLength > 30) {
                return;
            }
            long broadcastValue = (addressValue & netmask) |
                    (~netmask & 0xffffffffL);
            addLocalNetwork(networks, seen, addressFromLong(addressValue),
                    addressFromLong(broadcastValue), (short) prefixLength);
        } catch (Exception ignored) {
        }
    }

    private int getPrefixLength(long netmask) {
        int prefixLength = 0;
        boolean foundZero = false;
        for (int bit = 31; bit >= 0; bit--) {
            boolean set = (netmask & (1L << bit)) != 0L;
            if (set) {
                if (foundZero) {
                    return -1;
                }
                prefixLength++;
            } else {
                foundZero = true;
            }
        }
        return prefixLength;
    }

    private void addLocalNetwork(ArrayList<LocalNetwork> networks,
                                 HashSet<String> seen,
                                 InetAddress address,
                                 InetAddress broadcast,
                                 short prefixLength) {
        String key = address.getHostAddress() + "/" + prefixLength;
        if (seen.add(key)) {
            networks.add(new LocalNetwork(address, broadcast, prefixLength));
        }
    }

    private boolean isCellularInterface(String name) {
        if (name == null) {
            return false;
        }
        String value = name.toLowerCase(Locale.US);
        return value.contains("rmnet") ||
                value.contains("ccmni") ||
                value.contains("ccemni") ||
                value.contains("ccinet") ||
                value.startsWith("pdp") ||
                value.startsWith("wwan") ||
                value.startsWith("ppp");
    }

    private ArrayList<InetAddress> getUnicastProbeTargets() {
        ArrayList<InetAddress> targets = new ArrayList<InetAddress>();
        HashSet<String> seen = new HashSet<String>();
        try {
            for (LocalNetwork localNetwork : getLocalNetworks()) {
                long localAddress = ipv4ToLong(localNetwork.address);
                long netmask = (0xffffffffL <<
                        (32 - localNetwork.prefixLength)) & 0xffffffffL;
                long network = localAddress & netmask;
                long broadcast = network | (~netmask & 0xffffffffL);
                long firstAddress = network + 1L;
                long lastAddress = broadcast - 1L;

                if (lastAddress - firstAddress + 1L > MAX_UNICAST_PROBES) {
                    network = localAddress & 0xffffff00L;
                    firstAddress = network + 1L;
                    lastAddress = network + 254L;
                }

                for (long address = firstAddress;
                     address <= lastAddress &&
                             targets.size() < MAX_UNICAST_PROBES;
                     address++) {
                    if (address == localAddress) {
                        continue;
                    }
                    InetAddress target = addressFromLong(address);
                    if (seen.add(target.getHostAddress())) {
                        targets.add(target);
                    }
                }
                if (targets.size() >= MAX_UNICAST_PROBES) {
                    break;
                }
            }
        } catch (Exception ignored) {
            targets.clear();
        }
        return targets;
    }

    private long ipv4ToLong(InetAddress address) {
        byte[] bytes = address.getAddress();
        return ((bytes[0] & 0xffL) << 24) |
                ((bytes[1] & 0xffL) << 16) |
                ((bytes[2] & 0xffL) << 8) |
                (bytes[3] & 0xffL);
    }

    private InetAddress addressFromLong(long address) throws Exception {
        byte[] bytes = new byte[] {
                (byte) (address >>> 24),
                (byte) (address >>> 16),
                (byte) (address >>> 8),
                (byte) address
        };
        return InetAddress.getByAddress(bytes);
    }

    private boolean isLocalWifiAddress(InetAddress address) {
        for (LocalNetwork network : getLocalNetworks()) {
            if (network.address.equals(address)) {
                return true;
            }
        }
        return false;
    }

    private String safeDeviceName() {
        String name = Build.MODEL == null ? "Android" : Build.MODEL.trim();
        if (name.length() == 0) {
            name = "Android";
        }
        name = name.replace('|', ' ');
        return name.length() > 40 ? name.substring(0, 40) : name;
    }

    private static final class LocalNetwork {
        final InetAddress address;
        final InetAddress broadcastAddress;
        final int prefixLength;

        LocalNetwork(InetAddress address, InetAddress broadcastAddress,
                     int prefixLength) {
            this.address = address;
            this.broadcastAddress = broadcastAddress;
            this.prefixLength = prefixLength;
        }
    }

    private static final class SeenPeer {
        final Peer peer;
        long lastSeen;

        SeenPeer(Peer peer, long lastSeen) {
            this.peer = peer;
            this.lastSeen = lastSeen;
        }
    }
}