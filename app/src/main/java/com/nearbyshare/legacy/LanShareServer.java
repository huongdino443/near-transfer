package com.nearbyshare.legacy;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class LanShareServer {
    static final int HTTP_PORT = 45321;
    private static final long MAX_FILE_BYTES = 2147483647L;
    private static final long MAX_TRANSFER_BYTES = 2147483647L;
    private static final int MAX_TEXT_BYTES = 262144;
    private static final int MAX_FILES = 20;
    private static final int MAX_HEADER_BYTES = 32768;
    private static final long APPROVED_TRANSFER_TTL_MS = 300000L;
    private static final int MAX_APPROVED_TRANSFERS = 4;

    interface Listener {
        void onListening();
        void onTransferOffered(IncomingOffer offer, DecisionCallback decision);
        void onTransferProgress(String transferId, int itemIndex,
                               long transferred, long totalBytes);
        void onTransferFailed(String transferId, String message);
        void onFileSaved(String transferId, String name, File file,
                         int fileNumber, int fileCount);
        void onTextReceived(String transferId, String senderName, String text);
        void onError(String message);
    }

    interface DecisionCallback {
        void decide(boolean accepted);
    }

    private final Listener listener;
    private final Semaphore activeTransfers = new Semaphore(2);
    private final HashMap<String, AcceptedTransfer> approvedTransfers =
            new HashMap<String, AcceptedTransfer>();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;

    LanShareServer(Listener listener) {
        this.listener = listener;
    }

    synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        acceptThread = new Thread(new Runnable() {
            public void run() {
                acceptLoop();
            }
        }, "nearby-http-accept");
        acceptThread.start();
    }

    synchronized void stop() {
        running = false;
        synchronized (approvedTransfers) {
            approvedTransfers.clear();
        }
        ServerSocket current = serverSocket;
        serverSocket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
    }

    private void acceptLoop() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(HTTP_PORT));
            serverSocket = socket;
            if (listener != null) {
                listener.onListening();
            }

            while (running) {
                final Socket client = socket.accept();
                if (!activeTransfers.tryAcquire()) {
                    try {
                        writeResponse(client.getOutputStream(), 503, "Busy",
                                "This device is handling other transfers.");
                    } catch (IOException ignored) {
                    }
                    closeQuietly(client);
                    continue;
                }
                Thread worker = new Thread(new Runnable() {
                    public void run() {
                        try {
                            handleClient(client);
                        } finally {
                            closeQuietly(client);
                            activeTransfers.release();
                        }
                    }
                }, "nearby-http-client");
                worker.start();
            }
        } catch (IOException e) {
            if (running && listener != null) {
                listener.onError("Không mở được cổng nhận file: " + safeMessage(e));
            }
        } finally {
            serverSocket = null;
            closeQuietly(socket);
        }
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(60000);
            InputStream input = new BufferedInputStream(client.getInputStream());
            OutputStream output = new BufferedOutputStream(client.getOutputStream());
            String requestLine = readLine(input, MAX_HEADER_BYTES);
            if (requestLine == null) {
                return;
            }

            String[] request = requestLine.split(" ");
            if (request.length < 2) {
                writeResponse(output, 400, "Bad Request", "Invalid request.");
                return;
            }
            String method = request[0].toUpperCase(Locale.US);
            String path = request[1];
            String headerLine;
            int headerBytes = requestLine.length();
            java.util.HashMap<String, String> headers =
                    new java.util.HashMap<String, String>();
            while ((headerLine = readLine(input, MAX_HEADER_BYTES)) != null &&
                    headerLine.length() > 0) {
                headerBytes += headerLine.length() + 2;
                if (headerBytes > MAX_HEADER_BYTES) {
                    writeResponse(output, 431, "Request Header Fields Too Large",
                            "Headers are too large.");
                    return;
                }
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    String key = headerLine.substring(0, colon).trim()
                            .toLowerCase(Locale.US);
                    String value = headerLine.substring(colon + 1).trim();
                    headers.put(key, value);
                }
            }

            if ("POST".equals(method) && "/offer".equals(path)) {
                handleOffer(client, output, headers);
                return;
            }
            if ("POST".equals(method) && "/upload".equals(path)) {
                handleUpload(input, output, headers,
                        client.getInetAddress().getHostAddress());
                return;
            }
            if ("POST".equals(method) && "/upload-text".equals(path)) {
                handleTextUpload(input, output, headers,
                        client.getInetAddress().getHostAddress());
                return;
            }
            writeResponse(output, 404, "Not Found", "Unknown endpoint.");
        } catch (Exception e) {
            try {
                writeResponse(client.getOutputStream(), 500, "Transfer Failed",
                        safeMessage(e));
            } catch (IOException ignored) {
            }
            if (listener != null && running) {
                listener.onError("Không nhận được file: " + safeMessage(e));
            }
        }
    }

    private void handleOffer(Socket client, OutputStream output,
                             HashMap<String, String> headers) throws Exception {
        String bodyLength = headers.get("content-length");
        if (bodyLength != null && !"0".equals(bodyLength)) {
            writeResponse(output, 400, "Bad Request", "Offers must not include a body.");
            return;
        }

        IncomingOffer offer;
        try {
            offer = parseOffer(headers, client.getInetAddress().getHostAddress());
        } catch (Exception e) {
            writeResponse(output, 400, "Bad Request", safeMessage(e));
            return;
        }
        PendingDecision decision = new PendingDecision();
        if (listener == null) {
            decision.decide(false);
        } else {
            listener.onTransferOffered(offer, decision);
        }

        if (!decision.await(this)) {
            writeResponse(output, 403, "Declined",
                    "The receiver declined or did not confirm the transfer.");
            return;
        }
        if (!rememberApprovedTransfer(offer)) {
            writeResponse(output, 503, "Busy",
                    "Too many approved transfers are waiting.");
            if (listener != null) {
                listener.onTransferFailed(offer.transferId,
                        "Máy nhận đang bận. Hãy thử gửi lại sau.");
            }
            return;
        }
        writeResponse(output, 200, "OK", "Transfer accepted.");
    }

    private IncomingOffer parseOffer(HashMap<String, String> headers,
                                    String senderAddress) throws Exception {
        String transferId = headers.get("x-transfer-id");
        if (!isTransferId(transferId)) {
            throw new IOException("Mã yêu cầu gửi file không hợp lệ.");
        }
        String type = headers.get("x-transfer-type");
        String encodedSenderName = headers.get("x-sender-name");
        String senderName = encodedSenderName == null ? "Thiết bị" :
                ShareFiles.safeFileName(ShareFiles.decodeHeaderValue(encodedSenderName));
        if ("text".equals(type)) {
            long textSize;
            try {
                textSize = Long.parseLong(headers.get("x-text-size"));
            } catch (Exception e) {
                throw new IOException("Kích thước văn bản không hợp lệ.");
            }
            if (textSize < 1L || textSize > MAX_TEXT_BYTES) {
                throw new IOException("Văn bản phải từ 1 byte đến 256 KB.");
            }
            ArrayList<IncomingOffer.Item> textItem =
                    new ArrayList<IncomingOffer.Item>(1);
            textItem.add(new IncomingOffer.Item("Tin nhắn văn bản", textSize));
            return new IncomingOffer(transferId, senderName, senderAddress,
                    true, textItem, textSize);
        }
        if (!"files".equals(type)) {
            throw new IOException("Loại nội dung gửi không hợp lệ.");
        }
        int count;
        try {
            count = Integer.parseInt(headers.get("x-file-count"));
        } catch (Exception e) {
            throw new IOException("Danh sách file gửi không hợp lệ.");
        }
        if (count < 1 || count > MAX_FILES) {
            throw new IOException("Có thể gửi từ 1 đến 20 file mỗi lần.");
        }

        ArrayList<IncomingOffer.Item> files =
                new ArrayList<IncomingOffer.Item>(count);
        long totalBytes = 0L;
        for (int i = 0; i < count; i++) {
            String encodedName = headers.get("x-file-" + i + "-name");
            String sizeValue = headers.get("x-file-" + i + "-size");
            if (encodedName == null || sizeValue == null) {
                throw new IOException("Thiếu thông tin file trong yêu cầu gửi.");
            }
            String name = ShareFiles.safeFileName(
                    ShareFiles.decodeHeaderValue(encodedName));
            long size;
            try {
                size = Long.parseLong(sizeValue);
            } catch (NumberFormatException e) {
                throw new IOException("Kích thước file không hợp lệ.");
            }
            if (size < 0 || size > MAX_FILE_BYTES ||
                    totalBytes > MAX_TRANSFER_BYTES - size) {
                throw new IOException("Tổng dung lượng phải nhỏ hơn 2 GiB.");
            }
            totalBytes += size;
            files.add(new IncomingOffer.Item(name, size));
        }
        return new IncomingOffer(transferId, senderName, senderAddress,
                false, files, totalBytes);
    }

    private void handleUpload(InputStream input, OutputStream output,
                              HashMap<String, String> headers,
                              String senderAddress) throws Exception {
        String transferId = headers.get("x-transfer-id");
        String indexValue = headers.get("x-file-index");
        String encodedName = headers.get("x-file-name");
        if (!isTransferId(transferId) || indexValue == null || encodedName == null) {
            writeResponse(output, 400, "Bad Request", "Upload metadata is incomplete.");
            return;
        }
        int index;
        long contentLength;
        try {
            index = Integer.parseInt(indexValue);
            contentLength = Long.parseLong(headers.get("content-length"));
        } catch (Exception e) {
            writeResponse(output, 411, "Length Required",
                    "A valid file index and Content-Length are required.");
            return;
        }
        if (contentLength < 0 || contentLength > MAX_FILE_BYTES) {
            writeResponse(output, 413, "Payload Too Large",
                    "Files must be smaller than 2 GiB.");
            return;
        }
        String fileName = ShareFiles.safeFileName(
                ShareFiles.decodeHeaderValue(encodedName));
        UploadReservation reservation = reserveUpload(
                transferId, index, fileName, contentLength, senderAddress);
        if (reservation == null) {
            writeResponse(output, 403, "Forbidden",
                    "The receiver has not approved this file.");
            return;
        }

        try {
            File destination = receiveFile(input, contentLength, fileName,
                    transferId, index);
            completeUpload(reservation, true);
            writeResponse(output, 201, "Created", "Saved " + destination.getName());
            if (listener != null) {
                listener.onFileSaved(transferId, destination.getName(), destination,
                        index + 1, reservation.transfer.offer.files.size());
            }
        } catch (Exception e) {
            completeUpload(reservation, false);
            if (listener != null) {
                listener.onTransferFailed(transferId, safeMessage(e));
            }
            throw e;
        }
    }

    private void handleTextUpload(InputStream input, OutputStream output,
                                  HashMap<String, String> headers,
                                  String senderAddress) throws Exception {
        String transferId = headers.get("x-transfer-id");
        long contentLength;
        try {
            contentLength = Long.parseLong(headers.get("content-length"));
        } catch (Exception e) {
            writeResponse(output, 411, "Length Required",
                    "A valid Content-Length header is required.");
            return;
        }
        if (!isTransferId(transferId) || contentLength < 1L ||
                contentLength > MAX_TEXT_BYTES) {
            writeResponse(output, 413, "Payload Too Large",
                    "Text must be from 1 byte to 256 KB.");
            return;
        }
        UploadReservation reservation = reserveTextUpload(
                transferId, contentLength, senderAddress);
        if (reservation == null) {
            writeResponse(output, 403, "Forbidden",
                    "The receiver has not approved this message.");
            return;
        }

        ByteArrayOutputStream message = new ByteArrayOutputStream((int) contentLength);
        try {
            byte[] buffer = new byte[4096];
            long remaining = contentLength;
            long transferred = 0L;
            long lastUpdate = 0L;
            while (remaining > 0L) {
                int count = input.read(buffer, 0,
                        (int) Math.min((long) buffer.length, remaining));
                if (count < 0) {
                    throw new IOException("Kết nối bị ngắt khi nhận văn bản.");
                }
                if (count == 0) {
                    continue;
                }
                message.write(buffer, 0, count);
                remaining -= count;
                transferred += count;
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 150L || remaining == 0L) {
                    notifyProgress(transferId, 0, transferred, contentLength);
                    lastUpdate = now;
                }
            }
            String text = new String(message.toByteArray(), "UTF-8");
            completeUpload(reservation, true);
            writeResponse(output, 201, "Created", "Received text message.");
            if (listener != null) {
                listener.onTextReceived(transferId,
                        reservation.transfer.offer.senderName, text);
            }
        } catch (Exception e) {
            completeUpload(reservation, false);
            if (listener != null) {
                listener.onTransferFailed(transferId, safeMessage(e));
            }
            throw e;
        }
    }

    private boolean rememberApprovedTransfer(IncomingOffer offer) {
        synchronized (approvedTransfers) {
            removeExpiredTransfers();
            if (approvedTransfers.size() >= MAX_APPROVED_TRANSFERS ||
                    approvedTransfers.containsKey(offer.transferId)) {
                return false;
            }
            approvedTransfers.put(offer.transferId, new AcceptedTransfer(offer));
            return true;
        }
    }

    private UploadReservation reserveUpload(String transferId, int index,
                                           String fileName, long size,
                                           String senderAddress) {
        synchronized (approvedTransfers) {
            removeExpiredTransfers();
            AcceptedTransfer transfer = approvedTransfers.get(transferId);
            if (transfer == null ||
                    transfer.offer.textTransfer ||
                    !transfer.offer.senderAddress.equals(senderAddress) ||
                    index < 0 || index >= transfer.offer.files.size()) {
                return null;
            }
            IncomingOffer.Item expected = transfer.offer.files.get(index);
            if (transfer.inProgress[index] || transfer.complete[index] ||
                    expected.size != size || !expected.name.equals(fileName)) {
                return null;
            }
            transfer.inProgress[index] = true;
            return new UploadReservation(transferId, transfer, index);
        }
    }

    private UploadReservation reserveTextUpload(String transferId, long size,
                                                String senderAddress) {
        synchronized (approvedTransfers) {
            removeExpiredTransfers();
            AcceptedTransfer transfer = approvedTransfers.get(transferId);
            if (transfer == null || !transfer.offer.textTransfer ||
                    !transfer.offer.senderAddress.equals(senderAddress) ||
                    transfer.offer.totalBytes != size || transfer.inProgress[0] ||
                    transfer.complete[0]) {
                return null;
            }
            transfer.inProgress[0] = true;
            return new UploadReservation(transferId, transfer, 0);
        }
    }

    private void completeUpload(UploadReservation reservation, boolean succeeded) {
        synchronized (approvedTransfers) {
            reservation.transfer.inProgress[reservation.index] = false;
            if (succeeded) {
                reservation.transfer.complete[reservation.index] = true;
                if (reservation.transfer.allComplete()) {
                    approvedTransfers.remove(reservation.transferId);
                }
            }
        }
    }

    private void removeExpiredTransfers() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, AcceptedTransfer>> iterator =
                approvedTransfers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AcceptedTransfer> entry = iterator.next();
            if (now - entry.getValue().createdAt > APPROVED_TRANSFER_TTL_MS) {
                iterator.remove();
            }
        }
    }

    private boolean isTransferId(String transferId) {
        if (transferId == null || transferId.length() != 32) {
            return false;
        }
        for (int i = 0; i < transferId.length(); i++) {
            char c = transferId.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private File receiveFile(InputStream input, long contentLength, String fileName,
                             String transferId, int fileIndex)
            throws IOException {
        File directory = DownloadFolders.ensureReceivedDirectory();

        File destination = ShareFiles.uniqueFile(directory, fileName);
        FileOutputStream fileOutput = null;
        boolean complete = false;
        try {
            fileOutput = new FileOutputStream(destination);
            byte[] buffer = new byte[8192];
            long remaining = contentLength;
            long transferred = 0L;
            long lastUpdate = 0L;
            while (remaining > 0) {
                int wanted = (int) Math.min((long) buffer.length, remaining);
                int count = input.read(buffer, 0, wanted);
                if (count < 0) {
                    throw new IOException("Kết nối bị ngắt giữa chừng.");
                }
                if (count == 0) {
                    continue;
                }
                fileOutput.write(buffer, 0, count);
                remaining -= count;
                transferred += count;
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 150L || remaining == 0L) {
                    notifyProgress(transferId, fileIndex, transferred, contentLength);
                    lastUpdate = now;
                }
            }
            fileOutput.flush();
            complete = true;
            return destination;
        } finally {
            if (fileOutput != null) {
                try {
                    fileOutput.close();
                } catch (IOException ignored) {
                }
            }
            if (!complete && destination.exists()) {
                destination.delete();
            }
        }
    }

    private static String readLine(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                return new String(bytes, 0, length, "UTF-8");
            }
            line.write(current);
            previous = current;
            if (line.size() > maxBytes) {
                throw new IOException("Request header is too large.");
            }
        }
        return line.size() == 0 ? null : line.toString("UTF-8");
    }

    private static void writeResponse(OutputStream output, int code, String reason,
                                      String message) throws IOException {
        byte[] body = message.getBytes("UTF-8");
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Connection: close\r\n\r\n";
        output.write(headers.getBytes("UTF-8"));
        output.write(body);
        output.flush();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "Lỗi mạng." : message;
    }

    private void notifyProgress(String transferId, int itemIndex,
                                long transferred, long totalBytes) {
        if (listener != null) {
            listener.onTransferProgress(transferId, itemIndex, transferred, totalBytes);
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class PendingDecision implements DecisionCallback {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile boolean accepted;
        private boolean decided;

        public synchronized void decide(boolean accepted) {
            if (decided) {
                return;
            }
            this.accepted = accepted;
            decided = true;
            latch.countDown();
        }

        boolean await(LanShareServer server) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 120000L;
            while (server.running) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return false;
                }
                if (latch.await(Math.min(1000L, remaining), TimeUnit.MILLISECONDS)) {
                    return accepted;
                }
            }
            return false;
        }
    }

    private static final class AcceptedTransfer {
        final IncomingOffer offer;
        final boolean[] inProgress;
        final boolean[] complete;
        final long createdAt;

        AcceptedTransfer(IncomingOffer offer) {
            this.offer = offer;
            this.inProgress = new boolean[offer.files.size()];
            this.complete = new boolean[offer.files.size()];
            this.createdAt = System.currentTimeMillis();
        }

        boolean allComplete() {
            for (boolean value : complete) {
                if (!value) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class UploadReservation {
        final String transferId;
        final AcceptedTransfer transfer;
        final int index;

        UploadReservation(String transferId, AcceptedTransfer transfer, int index) {
            this.transferId = transferId;
            this.transfer = transfer;
            this.index = index;
        }
    }
}