package com.nearbyshare.legacy;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
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

    interface Listener {
        void onPeersChanged(ArrayList<Peer> peers);
    }

    private final Context context;
    private final Listener listener;
    private volatile boolean running;
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
        discoveryThread = new Thread(new Runnable() {
            public void run() {
                runDiscovery();
            }
        }, "nearby-peer-discovery");
        discoveryThread.start();
    }

    synchronized void stop() {
        running = false;
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
            ArrayList<InetAddress> probeTargets = getUnicastProbeTargets();
            int probeIndex = 0;
            long nextBroadcast = 0L;
            long nextUnicastSweep = 0L;
            long nextProbeBatch = 0L;
            boolean probingSubnet = false;
            while (running) {
                long now = System.currentTimeMillis();
                if (now >= nextBroadcast) {
                    sendDiscovery(localSocket);
                    nextBroadcast = now + BROADCAST_INTERVAL_MS;
                }

                if (!probingSubnet && probeTargets.size() > 0 &&
                        now >= nextUnicastSweep) {
                    probingSubnet = true;
                    probeIndex = 0;
                    nextProbeBatch = now;
                }
                if (probingSubnet && now >= nextProbeBatch) {
                    int sent = 0;
                    while (running && probeIndex < probeTargets.size() &&
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
                    localSocket.setSoTimeout(350);
                }
                DatagramPacket packet = new DatagramPacket(receiveBuffer,
                        receiveBuffer.length);
                try {
                    localSocket.receive(packet);
                    handlePacket(localSocket, packet, peers);
                } catch (SocketTimeoutException ignored) {
                }

                if (removeExpired(peers, System.currentTimeMillis())) {
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
        try {
            sendDiscovery(localSocket, getBroadcastAddress());
        } catch (Exception ignored) {
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
            if (!"PEER".equals(parts[1])) {
                return;
            }

            int port = Integer.parseInt(parts[2]);
            if (port < 1 || port > 65535) {
                return;
            }
            String address = packet.getAddress().getHostAddress();
            String localAddress = getLocalWifiAddress();
            if (address.equals(localAddress)) {
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

    private InetAddress getBroadcastAddress() throws Exception {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            DhcpInfo dhcp = wifi.getDhcpInfo();
            if (dhcp != null && dhcp.ipAddress != 0 && dhcp.netmask != 0) {
                int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
                byte[] address = new byte[] {
                        (byte) (broadcast & 0xff),
                        (byte) ((broadcast >> 8) & 0xff),
                        (byte) ((broadcast >> 16) & 0xff),
                        (byte) ((broadcast >> 24) & 0xff)
                };
                return InetAddress.getByAddress(address);
            }
        }
        return InetAddress.getByName("255.255.255.255");
    }

    private ArrayList<InetAddress> getUnicastProbeTargets() {
        ArrayList<InetAddress> targets = new ArrayList<InetAddress>();
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcp = wifi == null ? null : wifi.getDhcpInfo();
            if (dhcp == null || dhcp.ipAddress == 0 || dhcp.netmask == 0) {
                return targets;
            }

            long localAddress = Integer.reverseBytes(dhcp.ipAddress) & 0xffffffffL;
            long netmask = Integer.reverseBytes(dhcp.netmask) & 0xffffffffL;
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
                 address <= lastAddress && targets.size() < MAX_UNICAST_PROBES;
                 address++) {
                if (address == localAddress) {
                    continue;
                }
                byte[] bytes = new byte[] {
                        (byte) (address >>> 24),
                        (byte) (address >>> 16),
                        (byte) (address >>> 8),
                        (byte) address
                };
                targets.add(InetAddress.getByAddress(bytes));
            }
        } catch (Exception ignored) {
            targets.clear();
        }
        return targets;
    }

    private String getLocalWifiAddress() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                int ip = wifi.getConnectionInfo().getIpAddress();
            return String.format(Locale.US, "%d.%d.%d.%d",
                        ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff,
                        (ip >> 24) & 0xff);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String safeDeviceName() {
        String name = Build.MODEL == null ? "Android" : Build.MODEL.trim();
        if (name.length() == 0) {
            name = "Android";
        }
        name = name.replace('|', ' ');
        return name.length() > 40 ? name.substring(0, 40) : name;
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