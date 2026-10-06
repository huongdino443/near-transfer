package com.nearbyshare.j2me;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.file.FileConnection;

/** Streams NWS1 offers, file payloads, and UTF-8 messages over TCP. */
final class LanTransferClient extends Thread {
    private final NearTransferMidlet host;
    private final PeerDevice peer;
    private final String deviceName;
    private final String[] fileNames;
    private final String[] filePaths;
    private final String text;
    private final boolean textTransfer;
    private volatile boolean running = true;
    private volatile StreamConnection activeConnection;
    private long completedBytes;
    private long totalBytes;

    LanTransferClient(NearTransferMidlet host, PeerDevice peer,
                      String deviceName, String[] fileNames,
                      String[] filePaths, String text) {
        this.host = host;
        this.peer = peer;
        this.deviceName = deviceName;
        this.fileNames = fileNames;
        this.filePaths = filePaths;
        this.text = text;
        textTransfer = text != null;
    }

    public void run() {
        try {
            if (textTransfer) {
                sendText();
            } else {
                sendFiles();
            }
            if (running) {
                host.onOutgoingCompleted();
            }
        } catch (Exception exception) {
            if (running) {
                String message = exception.getMessage();
                host.onOutgoingFailed(message == null || message.length() == 0 ?
                        "Không gửi được nội dung." : message);
            }
        } finally {
            closeActiveConnection();
        }
    }

    void cancel() {
        running = false;
        interrupt();
        closeActiveConnection();
    }

    private void sendText() throws IOException {
        byte[] bytes;
        try {
            bytes = text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IOException("UTF-8 is unavailable.");
        }
        if (bytes.length < 1 ||
                bytes.length > LanHttpProtocol.MAXIMUM_TEXT_BYTES) {
            throw new IOException("Tin nhắn phải từ 1 byte đến 256 KB.");
        }
        totalBytes = bytes.length;
        host.onOutgoingProgress(0, "Tin nhắn văn bản", 0,
                bytes.length, 0, totalBytes);
        String transferId = LanHttpProtocol.newTransferId();
        sendOffer(transferId, true, null, null, bytes.length);
        checkRunning();
        SocketConnection socket = openConnection();
        InputStream response;
        OutputStream output;
        try {
            response = socket.openInputStream();
            output = socket.openOutputStream();
            writeRequestHeaders(output, "POST /upload-text HTTP/1.1",
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "X-Transfer-Id: " + transferId + "\r\n");
            int offset = 0;
            long lastProgress = System.currentTimeMillis();
            while (offset < bytes.length) {
                checkRunning();
                int count = Math.min(8192, bytes.length - offset);
                output.write(bytes, offset, count);
                offset += count;
                long now = System.currentTimeMillis();
                if (now - lastProgress >= 150 || offset == bytes.length) {
                    host.onOutgoingProgress(0, "Tin nhắn văn bản", offset,
                            bytes.length, offset, totalBytes);
                    lastProgress = now;
                }
            }
            output.flush();
            int status = LanHttpProtocol.readResponseStatus(response);
            if (status != 201) {
                throw responseError(status, "Không gửi được tin nhắn.");
            }
        } finally {
            closeActiveConnection();
        }
    }

    private void sendFiles() throws IOException {
        if (filePaths == null || filePaths.length == 0 ||
                filePaths.length > LanHttpProtocol.MAXIMUM_FILES) {
            throw new IOException("Có thể gửi từ 1 đến 20 tệp mỗi lần.");
        }
        SendFile[] files = new SendFile[filePaths.length];
        long total = 0;
        int i;
        for (i = 0; i < filePaths.length; i++) {
            checkRunning();
            FileConnection file = Jsr75FileStorage.openForRead(filePaths[i]);
            try {
                long size = file.fileSize();
                if (size < 0 ||
                        size > LanHttpProtocol.MAXIMUM_TRANSFER_BYTES ||
                        total > LanHttpProtocol.MAXIMUM_TRANSFER_BYTES - size) {
                    throw new IOException(
                            "Tổng dung lượng gửi vượt giới hạn 2 GiB.");
                }
                String name = LanHttpProtocol.sanitizeFileName(fileNames[i]);
                files[i] = new SendFile(name, filePaths[i], size);
                total += size;
            } finally {
                file.close();
            }
        }
        totalBytes = total;
        host.onOutgoingProgress(0, files[0].name, 0, files[0].size,
                0, totalBytes);
        String transferId = LanHttpProtocol.newTransferId();
        sendOffer(transferId, false, files, null, 0);
        for (i = 0; i < files.length; i++) {
            checkRunning();
            sendFile(transferId, i, files[i]);
            completedBytes += files[i].size;
        }
    }

    private void sendOffer(String transferId, boolean textOffer,
                           SendFile[] files, byte[] ignored, long textSize)
            throws IOException {
        SocketConnection socket = openConnection();
        try {
            InputStream response = socket.openInputStream();
            OutputStream output = socket.openOutputStream();
            StringBuffer headers = new StringBuffer();
            headers.append("POST /offer HTTP/1.1\r\n");
            headers.append("Host: ").append(peer.address).append(':')
                    .append(peer.port).append("\r\n");
            headers.append("Content-Length: 0\r\n");
            headers.append("X-Transfer-Id: ").append(transferId).append("\r\n");
            headers.append("X-Transfer-Type: ")
                    .append(textOffer ? "text" : "files").append("\r\n");
            headers.append("X-Sender-Name: ")
                    .append(LanHttpProtocol.encodeHeaderValue(deviceName))
                    .append("\r\n");
            if (textOffer) {
                headers.append("X-Text-Size: ").append(textSize).append("\r\n");
            } else {
                headers.append("X-File-Count: ").append(files.length)
                        .append("\r\n");
                int i;
                for (i = 0; i < files.length; i++) {
                    headers.append("X-File-").append(i).append("-Name: ")
                            .append(LanHttpProtocol.encodeHeaderValue(
                                    files[i].name)).append("\r\n");
                    headers.append("X-File-").append(i).append("-Size: ")
                            .append(files[i].size).append("\r\n");
                }
            }
            headers.append("Connection: close\r\n\r\n");
            LanHttpProtocol.writeAscii(output, headers.toString());
            output.flush();
            int status = LanHttpProtocol.readResponseStatus(response);
            if (status == 403) {
                throw new IOException("Thiết bị bên kia đã từ chối yêu cầu.");
            }
            if (status != 200) {
                throw responseError(status,
                        "Thiết bị bên kia không chấp nhận yêu cầu.");
            }
        } finally {
            closeActiveConnection();
        }
    }

    private void sendFile(String transferId, int index, SendFile file)
            throws IOException {
        SocketConnection socket = openConnection();
        FileConnection source = null;
        InputStream input = null;
        try {
            source = Jsr75FileStorage.openForRead(file.path);
            if (source.fileSize() != file.size) {
                throw new IOException("Kích thước tệp đã thay đổi: " +
                        file.name);
            }
            input = source.openInputStream();
            InputStream response = socket.openInputStream();
            OutputStream output = socket.openOutputStream();
            StringBuffer headers = new StringBuffer();
            headers.append("POST /upload HTTP/1.1\r\n");
            headers.append("Host: ").append(peer.address).append(':')
                    .append(peer.port).append("\r\n");
            headers.append("Content-Type: application/octet-stream\r\n");
            headers.append("Content-Length: ").append(file.size).append("\r\n");
            headers.append("X-Transfer-Id: ").append(transferId).append("\r\n");
            headers.append("X-File-Index: ").append(index).append("\r\n");
            headers.append("X-File-Name: ")
                    .append(LanHttpProtocol.encodeHeaderValue(file.name))
                    .append("\r\nConnection: close\r\n\r\n");
            LanHttpProtocol.writeAscii(output, headers.toString());
            long sent = 0;
            long lastProgress = System.currentTimeMillis();
            byte[] buffer = new byte[8192];
            while (sent < file.size) {
                checkRunning();
                int wanted = (int) Math.min((long) buffer.length,
                        file.size - sent);
                int count = input.read(buffer, 0, wanted);
                if (count < 0) {
                    throw new IOException("Tệp bị rút ngắn khi đang gửi.");
                }
                output.write(buffer, 0, count);
                sent += count;
                long now = System.currentTimeMillis();
                if (now - lastProgress >= 150 || sent == file.size) {
                    host.onOutgoingProgress(index, file.name, sent, file.size,
                            completedBytes + sent, totalBytes);
                    lastProgress = now;
                }
            }
            if (input.read() != -1) {
                throw new IOException("Kích thước tệp đã thay đổi: " +
                        file.name);
            }
            output.flush();
            int status = LanHttpProtocol.readResponseStatus(response);
            if (status != 201) {
                throw responseError(status,
                        "Không gửi được tệp " + file.name + ".");
            }
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (source != null) {
                try {
                    source.close();
                } catch (IOException ignored) {
                }
            }
            closeActiveConnection();
        }
    }

    private SocketConnection openConnection() throws IOException {
        checkRunning();
        StreamConnection connection = (StreamConnection) Connector.open(
                "socket://" + peer.address + ":" + peer.port,
                Connector.READ_WRITE);
        activeConnection = connection;
        SocketConnection socket = (SocketConnection) connection;
        String local = LanHttpProtocol.parseIpv4Address(
                socket.getLocalAddress());
        if (local != null) {
            host.onLocalAddress(local);
        }
        return socket;
    }

    private void writeRequestHeaders(OutputStream output, String requestLine,
                                     String headers) throws IOException {
        LanHttpProtocol.writeAscii(output, requestLine + "\r\n" +
                "Host: " + peer.address + ":" + peer.port + "\r\n" +
                headers + "Connection: close\r\n\r\n");
    }

    private IOException responseError(int status, String fallback) {
        if (status == 403) {
            return new IOException(
                    "Thiết bị bên kia đã từ chối hoặc chưa xác nhận.");
        }
        if (status == 404) {
            return new IOException(
                    "Thiết bị không hỗ trợ giao thức truyền Near Transfer.");
        }
        return new IOException(fallback + " (HTTP " + status + ").");
    }

    private void checkRunning() throws IOException {
        if (!running) {
            throw new IOException("Đã hủy truyền.");
        }
    }

    private void closeActiveConnection() {
        StreamConnection connection = activeConnection;
        activeConnection = null;
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class SendFile {
        final String name;
        final String path;
        final long size;

        SendFile(String name, String path, long size) {
            this.name = name;
            this.path = path;
            this.size = size;
        }
    }
}
