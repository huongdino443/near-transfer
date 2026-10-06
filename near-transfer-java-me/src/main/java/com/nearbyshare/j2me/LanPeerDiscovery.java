package com.nearbyshare.j2me;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.UDPDatagramConnection;

/** NWS1 discovery with separate passive-listener and active-scan UDP sockets. */
final class LanPeerDiscovery extends Thread {
    private static final long SCAN_INTERVAL = 3000L;
    private static final long SUBNET_SWEEP_INTERVAL = 10000L;
    private static final long PEER_TIMEOUT = 60000L;
    private static final long MANUAL_SCAN_RESPONSE_WINDOW = 3000L;
    private static final long ADDRESS_PROBE_RETRY = 15000L;
    private static final int MAX_PEERS = 8;
    private static final int MAX_DATAGRAM_SIZE = 1024;
    // Other clients reply to the source port of an active discovery request.
    private static final int SCAN_RESPONSE_PORT = 45323;

    private final NearTransferMidlet host;
    private final String discoveryName;
    private final Vector peers = new Vector();
    private final Object scanConnectionLock = new Object();
    private volatile boolean running = true;
    private volatile UDPDatagramConnection connection;
    private volatile UDPDatagramConnection scanConnection;
    private volatile String localAddress;
    private long lastSendError;
    private long lastAddressProbe;
    private long manualScanStartedAt;
    private long manualScanPruneAfter;
    private boolean addressProbeRunning;
    private boolean scanCycleRequested;
    private boolean manualScanRequested;

    LanPeerDiscovery(NearTransferMidlet host, String deviceName) {
        this.host = host;
        discoveryName = withIdentity(deviceName,
                LanHttpProtocol.newTransferId().substring(0, 8));
    }

    public void run() {
        try {
            connection = (UDPDatagramConnection) Connector.open(
                    "datagram://:" + LanHttpProtocol.DISCOVERY_PORT);
            updateLocalAddress(LanHttpProtocol.parseIpv4Address(
                    connection.getLocalAddress()));
            host.onLocalAddress(localAddress);

            Thread receiver = new Thread(new Runnable() {
                public void run() {
                    receiveLoop();
                }
            });
            receiver.start();

            Thread scanner = new Thread(new Runnable() {
                public void run() {
                    scanLoop();
                }
            }, "near-transfer-active-discovery");
            scanner.start();

            while (running) {
                synchronized (this) {
                    if (!scanCycleRequested && running) {
                        try {
                            wait(SCAN_INTERVAL);
                        } catch (InterruptedException ignored) {
                            if (!running) {
                                break;
                            }
                        }
                    }
                    scanCycleRequested = false;
                }
                if (running) {
                    closeScanConnection();
                    expirePeers();
                }
            }
        } catch (SecurityException exception) {
            host.onNetworkError("Thiết bị chưa cấp quyền UDP để tìm máy quanh đây.");
        } catch (Exception exception) {
            if (running) {
                host.onNetworkError("Không mở được tìm kiếm thiết bị qua Wi-Fi.");
            }
        } finally {
            UDPDatagramConnection current = connection;
            connection = null;
            if (current != null) {
                try {
                    current.close();
                } catch (IOException ignored) {
                }
            }
            closeScanConnection();
        }
    }

    String getLocalAddress() {
        return localAddress;
    }

    void scanNow() {
        synchronized (this) {
            if (!running) {
                return;
            }
            manualScanRequested = true;
            scanCycleRequested = true;
            notifyAll();
        }
    }

    void updateLocalAddress(String address) {
        if (isUsableLocalAddress(address)) {
            localAddress = address;
        }
    }

    void stopDiscovery() {
        running = false;
        interrupt();
        UDPDatagramConnection current = connection;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        closeScanConnection();
    }

    private void scanLoop() {
        long lastSubnetSweep = 0L;
        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        while (running) {
            UDPDatagramConnection current = null;
            boolean manualScan;
            synchronized (this) {
                manualScan = manualScanRequested;
                manualScanRequested = false;
            }
            boolean manualSweepCompleted = !manualScan;
            boolean retryDelay = false;
            try {
                current = (UDPDatagramConnection) Connector.open(
                        "datagram://:" + SCAN_RESPONSE_PORT);
                if (!setScanConnection(current)) {
                    closeDatagram(current);
                    return;
                }

                long now = System.currentTimeMillis();
                if (manualScan) {
                    synchronized (this) {
                        manualScanStartedAt = now;
                        manualScanPruneAfter = 0L;
                    }
                }
                sendDiscovery(current);

                boolean shouldSweep = isPrivateAddress(localAddress) &&
                        (manualScan || lastSubnetSweep == 0L ||
                         now - lastSubnetSweep >= SUBNET_SWEEP_INTERVAL);
                if (shouldSweep) {
                    sendSubnetDiscovery(current);
                    if (isCurrentScanConnection(current)) {
                        lastSubnetSweep = System.currentTimeMillis();
                        manualSweepCompleted = true;
                        if (manualScan) {
                            synchronized (this) {
                                manualScanPruneAfter = lastSubnetSweep +
                                        MANUAL_SCAN_RESPONSE_WINDOW;
                            }
                        }
                    }
                } else {
                    manualSweepCompleted = true;
                }

                if (hasManualScanRequest()) {
                    closeScanConnection();
                }
                while (isCurrentScanConnection(current)) {
                    Datagram datagram = current.newDatagram(buffer,
                            buffer.length);
                    current.receive(datagram);
                    handlePacket(current, datagram);
                }
            } catch (SecurityException exception) {
                if (running) {
                    reportSendError();
                    retryDelay = true;
                }
            } catch (IOException exception) {
                if (running && (current == null ||
                        isCurrentScanConnection(current))) {
                    reportSendError();
                    retryDelay = true;
                }
            } catch (Exception exception) {
                if (running && (current == null ||
                        isCurrentScanConnection(current))) {
                    host.onNetworkError(
                            "Không đọc được phản hồi tìm thiết bị.");
                    retryDelay = true;
                }
            } finally {
                clearScanConnection(current);
                closeDatagram(current);
            }

            if (manualScan && !manualSweepCompleted && running) {
                synchronized (this) {
                    manualScanRequested = true;
                    scanCycleRequested = true;
                    notifyAll();
                }
            }
            if (retryDelay && running) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    if (!running) {
                        return;
                    }
                }
            }
        }
    }

    private boolean setScanConnection(UDPDatagramConnection current) {
        synchronized (scanConnectionLock) {
            if (!running) {
                return false;
            }
            scanConnection = current;
            return true;
        }
    }

    private boolean isCurrentScanConnection(UDPDatagramConnection current) {
        return running && current != null && current == scanConnection;
    }

    private boolean hasManualScanRequest() {
        synchronized (this) {
            return manualScanRequested;
        }
    }

    private void clearScanConnection(UDPDatagramConnection current) {
        if (current == null) {
            return;
        }
        synchronized (scanConnectionLock) {
            if (scanConnection == current) {
                scanConnection = null;
            }
        }
    }

    private void closeScanConnection() {
        UDPDatagramConnection current;
        synchronized (scanConnectionLock) {
            current = scanConnection;
            scanConnection = null;
        }
        closeDatagram(current);
    }

    private static void closeDatagram(UDPDatagramConnection current) {
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sendDiscovery(UDPDatagramConnection current) {
        if (!isCurrentScanConnection(current)) {
            return;
        }
        try {
            byte[] payload = ("NWS1|DISCOVER|" +
                    LanHttpProtocol.HTTP_PORT + "|" +
                    LanHttpProtocol.encodeHeaderValue(discoveryName))
                    .getBytes("UTF-8");
            if (isPrivateAddress(localAddress)) {
                String broadcast = make24Broadcast(localAddress);
                if (broadcast != null) {
                    sendDiscoveryTo(current, payload, broadcast);
                }
            }
            sendDiscoveryToKnownPeers(current, payload);
        } catch (Exception exception) {
            reportSendError();
        }
    }

    private void sendDiscoveryToKnownPeers(
            UDPDatagramConnection current, byte[] payload) {
        PeerDevice[] snapshot;
        synchronized (this) {
            snapshot = new PeerDevice[peers.size()];
            int i;
            for (i = 0; i < snapshot.length; i++) {
                snapshot[i] = ((SeenPeer) peers.elementAt(i)).peer;
            }
        }
        int i;
        for (i = 0; i < snapshot.length &&
                isCurrentScanConnection(current); i++) {
            String address = snapshot[i].address;
            if (isPrivateAddress(address) && !address.equals(localAddress)) {
                sendDiscoveryTo(current, payload, address);
            }
        }
    }

    private void sendDiscoveryTo(UDPDatagramConnection current, byte[] payload,
                                 String address) {
        if (!isCurrentScanConnection(current)) {
            return;
        }
        try {
            Datagram datagram = current.newDatagram(payload, payload.length,
                    "datagram://" + address + ":" +
                            LanHttpProtocol.DISCOVERY_PORT);
            current.send(datagram);
        } catch (Exception exception) {
            if (isCurrentScanConnection(current)) {
                reportSendError();
            }
        }
    }

    private void sendSubnetDiscovery(UDPDatagramConnection current) {
        String address = localAddress;
        if (!isCurrentScanConnection(current) ||
                !isPrivateAddress(address)) {
            return;
        }
        int lastDot = address.lastIndexOf('.');
        if (lastDot < 0) {
            return;
        }

        byte[] payload;
        try {
            payload = ("NWS1|DISCOVER|" +
                    LanHttpProtocol.HTTP_PORT + "|" +
                    LanHttpProtocol.encodeHeaderValue(discoveryName))
                    .getBytes("UTF-8");
        } catch (Exception exception) {
            reportSendError();
            return;
        }

        String prefix = address.substring(0, lastDot + 1);
        int sent = 0;
        int hostPart;
        for (hostPart = 1; hostPart < 255 &&
                isCurrentScanConnection(current); hostPart++) {
            String target = prefix + hostPart;
            if (target.equals(address)) {
                continue;
            }
            sendDiscoveryTo(current, payload, target);
            sent++;
            if (sent % 16 == 0 && hostPart < 254) {
                try {
                    Thread.sleep(80L);
                } catch (InterruptedException ignored) {
                    if (!running) {
                        return;
                    }
                }
            }
        }
    }

    private void reportSendError() {
        long now = System.currentTimeMillis();
        if (running && now - lastSendError >= 15000L) {
            lastSendError = now;
            host.onNetworkError("Không gửi được yêu cầu tìm thiết bị.");
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        while (running) {
            UDPDatagramConnection current = connection;
            if (current == null) {
                return;
            }
            try {
                Datagram datagram = current.newDatagram(buffer,
                        buffer.length);
                current.receive(datagram);
                handlePacket(current, datagram);
            } catch (IOException exception) {
                if (running) {
                    host.onNetworkError("Mất kết nối dò tìm thiết bị.");
                }
                return;
            } catch (Exception exception) {
                if (running) {
                    host.onNetworkError("Không đọc được phản hồi tìm thiết bị.");
                }
            }
        }
    }

    private void handlePacket(UDPDatagramConnection current,
                              Datagram datagram) throws IOException {
        String message;
        try {
            message = new String(datagram.getData(), datagram.getOffset(),
                    datagram.getLength(), "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IOException("UTF-8 is unavailable.");
        }
        String[] parts = splitMessage(message);
        if (parts == null || !"NWS1".equals(parts[0])) {
            return;
        }
        String address = LanHttpProtocol.parseIpv4Address(
                datagram.getAddress());
        if (address == null || address.equals(localAddress)) {
            return;
        }

        if ("DISCOVER".equals(parts[1])) {
            String requesterName;
            try {
                requesterName = LanHttpProtocol.decodeHeaderValue(parts[3]);
            } catch (IOException exception) {
                return;
            }
            if (discoveryName.equals(requesterName)) {
                return;
            }
            int replyPort = LanHttpProtocol.parsePort(parts[2], -1);
            if (replyPort < 1) {
                return;
            }
            PeerDevice peer = new PeerDevice(displayName(requesterName),
                    address, replyPort);
            addPeer(peer);
            probeLocalAddress(peer);
            byte[] response = ("NWS1|PEER|" + LanHttpProtocol.HTTP_PORT +
                    "|" + LanHttpProtocol.encodeHeaderValue(discoveryName))
                    .getBytes("UTF-8");
            Datagram reply = current.newDatagram(response, response.length);
            reply.setAddress(datagram);
            current.send(reply);
            return;
        }

        if (!"PEER".equals(parts[1])) {
            return;
        }
        int port = LanHttpProtocol.parsePort(parts[2], -1);
        if (port < 1) {
            return;
        }
        String name;
        try {
            name = LanHttpProtocol.decodeHeaderValue(parts[3]);
        } catch (IOException exception) {
            return;
        }
        if (discoveryName.equals(name)) {
            return;
        }
        PeerDevice peer = new PeerDevice(displayName(name), address, port);
        addPeer(peer);
        probeLocalAddress(peer);
    }

    private static String displayName(String name) {
        name = withoutIdentity(name).trim();
        return name.length() == 0 ? "Thiết bị" : name;
    }

    private void probeLocalAddress(final PeerDevice peer) {
        if (peer == null || !isPrivateAddress(peer.address) ||
                isUsableLocalAddress(localAddress)) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (!running || addressProbeRunning ||
                    now - lastAddressProbe < ADDRESS_PROBE_RETRY) {
                return;
            }
            addressProbeRunning = true;
            lastAddressProbe = now;
        }

        Thread probe = new Thread(new Runnable() {
            public void run() {
                StreamConnection connection = null;
                InputStream input = null;
                OutputStream output = null;
                try {
                    connection = (StreamConnection) Connector.open(
                            "socket://" + peer.address + ":" + peer.port,
                            Connector.READ_WRITE, true);
                    SocketConnection socket =
                            (SocketConnection) connection;
                    String address = LanHttpProtocol.parseIpv4Address(
                            socket.getLocalAddress());
                    if (isUsableLocalAddress(address)) {
                        localAddress = address;
                        host.onLocalAddress(address);
                    }

                    input = connection.openInputStream();
                    output = connection.openOutputStream();
                    LanHttpProtocol.writeAscii(output,
                            "HEAD / HTTP/1.1\r\nHost: " + peer.address + ":" +
                            peer.port + "\r\nConnection: close\r\n\r\n");
                    output.flush();
                    LanHttpProtocol.readResponseStatus(input);
                } catch (Exception ignored) {
                } finally {
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException ignored) {
                        }
                    }
                    if (output != null) {
                        try {
                            output.close();
                        } catch (IOException ignored) {
                        }
                    }
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (IOException ignored) {
                        }
                    }
                    synchronized (LanPeerDiscovery.this) {
                        addressProbeRunning = false;
                    }
                }
            }
        }, "near-transfer-address-probe");
        probe.start();
    }

    private static boolean isUsableLocalAddress(String address) {
        return LanHttpProtocol.isIpv4(address) &&
                !"0.0.0.0".equals(address) &&
                !address.startsWith("127.");
    }

    private synchronized void addPeer(PeerDevice peer) {
        int i;
        for (i = 0; i < peers.size(); i++) {
            SeenPeer existing = (SeenPeer) peers.elementAt(i);
            if (existing.peer.matches(peer.address, peer.port)) {
                existing.peer = peer;
                existing.lastSeen = System.currentTimeMillis();
                publishPeers();
                return;
            }
        }
        if (peers.size() >= MAX_PEERS) {
            peers.removeElementAt(0);
        }
        peers.addElement(new SeenPeer(peer));
        publishPeers();
    }

    private synchronized void expirePeers() {
        long now = System.currentTimeMillis();
        long cutoff = now - PEER_TIMEOUT;
        if (manualScanStartedAt > 0L && manualScanPruneAfter > 0L &&
                now >= manualScanPruneAfter) {
            cutoff = Math.max(cutoff, manualScanStartedAt);
            manualScanStartedAt = 0L;
            manualScanPruneAfter = 0L;
        }
        boolean changed = false;
        int i = peers.size() - 1;
        while (i >= 0) {
            SeenPeer peer = (SeenPeer) peers.elementAt(i);
            if (peer.lastSeen < cutoff) {
                peers.removeElementAt(i);
                changed = true;
            }
            i--;
        }
        if (changed) {
            publishPeers();
        }
    }

    private void publishPeers() {
        PeerDevice[] snapshot = new PeerDevice[peers.size()];
        int i;
        for (i = 0; i < snapshot.length; i++) {
            snapshot[i] = ((SeenPeer) peers.elementAt(i)).peer;
        }
        host.onPeersChanged(snapshot);
    }

    private static String[] splitMessage(String message) {
        String[] parts = new String[4];
        int start = 0;
        int count = 0;
        int i;
        for (i = 0; i < message.length() && count < 3; i++) {
            if (message.charAt(i) == '|') {
                parts[count++] = message.substring(start, i);
                start = i + 1;
            }
        }
        if (count != 3 || start > message.length()) {
            return null;
        }
        parts[3] = message.substring(start);
        return parts;
    }

    private static String make24Broadcast(String address) {
        if (!LanHttpProtocol.isIpv4(address)) {
            return null;
        }
        int lastDot = address.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        return address.substring(0, lastDot + 1) + "255";
    }

    private static boolean isPrivateAddress(String address) {
        return address != null && address.startsWith("192.168.");
    }

    private static String withIdentity(String name, String token) {
        StringBuffer result = new StringBuffer(name);
        result.append('\u2063');
        int i;
        for (i = 0; i < token.length(); i++) {
            int nibble = Character.digit(token.charAt(i), 16);
            int bit;
            for (bit = 3; bit >= 0; bit--) {
                result.append((nibble & (1 << bit)) == 0 ?
                        '\u200b' : '\u200c');
            }
        }
        result.append('\u2063');
        return result.toString();
    }

    private static String withoutIdentity(String name) {
        int end = name.length() - 1;
        int start = name.lastIndexOf('\u2063', end - 1);
        if (end <= 0 || name.charAt(end) != '\u2063' || start < 0 ||
                end - start != 33) {
            return name;
        }
        int i;
        for (i = start + 1; i < end; i++) {
            char bit = name.charAt(i);
            if (bit != '\u200b' && bit != '\u200c') {
                return name;
            }
        }
        return name.substring(0, start);
    }

    private static final class SeenPeer {
        PeerDevice peer;
        long lastSeen;

        SeenPeer(PeerDevice peer) {
            this.peer = peer;
            lastSeen = System.currentTimeMillis();
        }
    }
}
