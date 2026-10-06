package com.nearbyshare.j2me;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;

/** Receives the shared NWS1 HTTP transfer protocol on TCP port 45321. */
final class LanTransferServer extends Thread {
    private static final long OFFER_TIMEOUT = 120000L;
    private static final long APPROVAL_LIFETIME = 300000L;
    private static final int MAX_APPROVED = 4;

    private final NearTransferMidlet host;
    private final Vector approved = new Vector();
    private volatile boolean running = true;
    private volatile ServerSocketConnection listener;

    LanTransferServer(NearTransferMidlet host) {
        this.host = host;
    }

    public void run() {
        try {
            listener = (ServerSocketConnection) Connector.open(
                    "socket://:" + LanHttpProtocol.HTTP_PORT);
            host.onServerStarted(listener.getLocalAddress(),
                    listener.getLocalPort());
            while (running) {
                StreamConnection connection = listener.acceptAndOpen();
                Thread worker = new Thread(new RequestWorker(connection));
                worker.start();
            }
        } catch (SecurityException exception) {
            if (running) {
                host.onNetworkError(
                        "Thiết bị chưa cấp quyền mở cổng nhận TCP.");
            }
        } catch (IOException exception) {
            if (running) {
                host.onNetworkError(
                        "Không thể mở cổng nhận 45321 trên điện thoại.");
            }
        } finally {
            ServerSocketConnection current = listener;
            listener = null;
            if (current != null) {
                try {
                    current.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    void stopServer() {
        running = false;
        ServerSocketConnection current = listener;
        listener = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        interrupt();
    }

    synchronized void forgetTransfer(String transferId) {
        int i;
        for (i = approved.size() - 1; i >= 0; i--) {
            ApprovedTransfer transfer =
                    (ApprovedTransfer) approved.elementAt(i);
            if (transfer.offer.transferId.equals(transferId)) {
                approved.removeElementAt(i);
            }
        }
    }

    private final class RequestWorker implements Runnable {
        private final StreamConnection connection;

        RequestWorker(StreamConnection connection) {
            this.connection = connection;
        }

        public void run() {
            try {
                handle(connection);
            } catch (IOException exception) {
                host.onNetworkError("Kết nối nhận bị gián đoạn.");
            } finally {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void handle(StreamConnection connection) throws IOException {
        InputStream input = connection.openInputStream();
        OutputStream output = connection.openOutputStream();
        SocketConnection socket = (SocketConnection) connection;
        String senderAddress = LanHttpProtocol.parseIpv4Address(
                socket.getAddress());
        if (senderAddress == null) {
            LanHttpProtocol.writeResponse(output, 400, "Bad Request",
                    "IPv4 is required.");
            return;
        }

        HttpRequest request;
        try {
            request = readRequest(input);
        } catch (IOException exception) {
            LanHttpProtocol.writeResponse(output, 400, "Bad Request",
                    "Invalid HTTP request.");
            return;
        }
        if (!"POST".equals(request.method)) {
            LanHttpProtocol.writeResponse(output, 405, "Method Not Allowed",
                    "POST is required.");
            return;
        }
        if ("/offer".equals(request.path)) {
            handleOffer(request, senderAddress, output);
        } else if ("/upload".equals(request.path)) {
            handleFileUpload(request, senderAddress, input, output);
        } else if ("/upload-text".equals(request.path)) {
            handleTextUpload(request, senderAddress, input, output);
        } else {
            LanHttpProtocol.writeResponse(output, 404, "Not Found",
                    "Unknown endpoint.");
        }
    }

    private void handleOffer(HttpRequest request, String senderAddress,
                             OutputStream output) throws IOException {
        String contentLength = header(request, "content-length");
        if (header(request, "transfer-encoding") != null ||
                !"0".equals(contentLength)) {
            LanHttpProtocol.writeResponse(output, 400, "Bad Request",
                    "Offers must not include a body.");
            return;
        }

        IncomingOffer offer;
        try {
            offer = parseOffer(request.headers, senderAddress);
        } catch (IOException exception) {
            LanHttpProtocol.writeResponse(output, 400, "Bad Request",
                    exception.getMessage());
            return;
        }
        expireApprovedTransfers();
        if (!host.offerIncomingTransfer(offer)) {
            LanHttpProtocol.writeResponse(output, 503, "Busy",
                    "This device is handling another offer.");
            return;
        }
        boolean accepted;
        try {
            accepted = offer.awaitDecision(OFFER_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            accepted = false;
        }
        if (!accepted) {
            host.onIncomingOfferExpired(offer);
            LanHttpProtocol.writeResponse(output, 403, "Declined",
                    "The receiver declined or did not confirm the transfer.");
            return;
        }
        synchronized (this) {
            expireApprovedTransfers();
            if (approved.size() >= MAX_APPROVED) {
                LanHttpProtocol.writeResponse(output, 503, "Busy",
                        "Too many transfers are waiting.");
                return;
            }
            approved.addElement(new ApprovedTransfer(offer));
        }
        LanHttpProtocol.writeResponse(output, 200, "OK",
                "Transfer accepted.");
    }

    private void handleFileUpload(HttpRequest request, String senderAddress,
                                  InputStream input, OutputStream output)
            throws IOException {
        String transferId = header(request, "x-transfer-id");
        String fileIndexText = header(request, "x-file-index");
        String encodedName = header(request, "x-file-name");
        long contentLength;
        try {
            contentLength = parseContentLength(request);
        } catch (IOException exception) {
            LanHttpProtocol.writeResponse(output, 400, "Bad Request",
                    exception.getMessage());
            return;
        }
        int index;
        try {
            index = Integer.parseInt(fileIndexText);
        } catch (Exception exception) {
            index = -1;
        }
        String fileName;
        try {
            fileName = LanHttpProtocol.sanitizeFileName(
                    LanHttpProtocol.decodeHeaderValue(encodedName));
        } catch (Exception exception) {
            fileName = null;
        }
        ApprovedTransfer transfer = findApproved(
                transferId, senderAddress, false);
        if (transfer == null || fileName == null ||
                !transfer.reserve(index, fileName, contentLength)) {
            LanHttpProtocol.writeResponse(output, 403, "Forbidden",
                    "The receiver has not approved this file.");
            return;
        }

        Jsr75FileStorage.SavedFile destination = null;
        boolean saved = false;
        try {
            destination = Jsr75FileStorage.createUnique(fileName);
            final ApprovedTransfer progressTransfer = transfer;
            LanHttpProtocol.copyExactly(input, destination.output,
                    contentLength, new TransferProgressListener() {
                        public void onProgress(int itemIndex,
                                               String itemName,
                                               long transferred,
                                               long total) {
                            host.onTransferProgress(progressTransfer.offer,
                                    itemIndex, transferred, total);
                        }
                    }, index, fileName);
            destination.output.flush();
            destination.finish();
            saved = true;
        } catch (IOException exception) {
            transfer.release(index);
            forgetTransfer(transfer.offer.transferId);
            host.onIncomingTransferFailed(transfer.offer,
                    exception.getMessage());
            try {
                LanHttpProtocol.writeResponse(output, 500, "Receive Failed",
                        "Could not save the file.");
            } catch (IOException ignored) {
            }
            return;
        } catch (SecurityException exception) {
            transfer.release(index);
            forgetTransfer(transfer.offer.transferId);
            host.onIncomingTransferFailed(transfer.offer,
                    Jsr75FileStorage.writePermissionMessage());
            try {
                LanHttpProtocol.writeResponse(output, 500, "Receive Failed",
                        "The phone denied file write access.");
            } catch (IOException ignored) {
            }
            return;
        } finally {
            if (destination != null && !saved) {
                destination.delete();
            }
            if (!saved) {
                transfer.release(index);
            }
        }
        transfer.complete(index);
        host.onFileReceived(transfer.offer, index, destination.name);
        if (transfer.isComplete()) {
            forgetTransfer(transfer.offer.transferId);
            host.onIncomingFilesCompleted(transfer.offer);
        }
        LanHttpProtocol.writeResponse(output, 201, "Created",
                "Saved " + destination.name);
    }

    private void handleTextUpload(HttpRequest request, String senderAddress,
                                  InputStream input, OutputStream output)
            throws IOException {
        String transferId = header(request, "x-transfer-id");
        long contentLength;
        try {
            contentLength = parseContentLength(request);
        } catch (IOException exception) {
            LanHttpProtocol.writeResponse(output, 400, "Bad Request",
                    exception.getMessage());
            return;
        }
        ApprovedTransfer transfer = findApproved(
                transferId, senderAddress, true);
        if (transfer == null || contentLength < 1 ||
                contentLength > LanHttpProtocol.MAXIMUM_TEXT_BYTES ||
                !transfer.reserve(0, "Tin nhắn văn bản", contentLength)) {
            LanHttpProtocol.writeResponse(output, 403, "Forbidden",
                    "The receiver has not approved this message.");
            return;
        }
        byte[] bytes = new byte[(int) contentLength];
        String text;
        try {
            readExactly(input, bytes);
            try {
                text = new String(bytes, "UTF-8");
            } catch (UnsupportedEncodingException exception) {
                throw new IOException("UTF-8 is unavailable.");
            }
        } catch (IOException exception) {
            transfer.release(0);
            forgetTransfer(transfer.offer.transferId);
            host.onIncomingTransferFailed(transfer.offer,
                    exception.getMessage());
            try {
                LanHttpProtocol.writeResponse(output, 400, "Receive Failed",
                        "Could not receive the text.");
            } catch (IOException ignored) {
            }
            return;
        }
        transfer.complete(0);
        host.onTextReceived(transfer.offer, text);
        forgetTransfer(transfer.offer.transferId);
        LanHttpProtocol.writeResponse(output, 201, "Created",
                "Received text message.");
    }

    private ApprovedTransfer findApproved(String transferId,
                                          String senderAddress,
                                          boolean text) {
        if (!LanHttpProtocol.isTransferId(transferId)) {
            return null;
        }
        synchronized (this) {
            expireApprovedTransfers();
            int i;
            for (i = 0; i < approved.size(); i++) {
                ApprovedTransfer transfer =
                        (ApprovedTransfer) approved.elementAt(i);
                if (transfer.offer.transferId.equals(transferId) &&
                        transfer.offer.senderAddress.equals(senderAddress) &&
                        transfer.offer.text == text) {
                    return transfer;
                }
            }
        }
        return null;
    }

    private synchronized void expireApprovedTransfers() {
        long cutoff = System.currentTimeMillis() - APPROVAL_LIFETIME;
        int i = approved.size() - 1;
        while (i >= 0) {
            ApprovedTransfer transfer =
                    (ApprovedTransfer) approved.elementAt(i);
            if (transfer.createdAt < cutoff) {
                approved.removeElementAt(i);
                host.onIncomingOfferExpired(transfer.offer);
            }
            i--;
        }
    }

    private static IncomingOffer parseOffer(Hashtable headers,
                                            String senderAddress)
            throws IOException {
        String transferId = getHeader(headers, "x-transfer-id");
        if (!LanHttpProtocol.isTransferId(transferId)) {
            throw new IOException("Transfer ID is invalid.");
        }
        String senderName;
        try {
            senderName = LanHttpProtocol.sanitizeFileName(
                    LanHttpProtocol.decodeHeaderValue(
                            getHeader(headers, "x-sender-name")));
        } catch (Exception exception) {
            senderName = "Thiết bị";
        }
        String type = getHeader(headers, "x-transfer-type");
        if ("text".equals(type)) {
            long size = parseNonnegativeLong(
                    getHeader(headers, "x-text-size"));
            if (size < 1 || size > LanHttpProtocol.MAXIMUM_TEXT_BYTES ||
                    hasHeaderPrefix(headers, "x-file-")) {
                throw new IOException("Invalid text offer.");
            }
            IncomingOffer.Item[] items = new IncomingOffer.Item[] {
                    new IncomingOffer.Item("Tin nhắn văn bản", size)
            };
            return new IncomingOffer(transferId, senderName, senderAddress,
                    true, items, size);
        }
        if (!"files".equals(type) ||
                getHeader(headers, "x-text-size") != null) {
            throw new IOException("Transfer type must be files or text.");
        }
        int count;
        try {
            count = Integer.parseInt(getHeader(headers, "x-file-count"));
        } catch (Exception exception) {
            throw new IOException("File count is invalid.");
        }
        if (count < 1 || count > LanHttpProtocol.MAXIMUM_FILES) {
            throw new IOException("A batch must contain 1 to 20 files.");
        }
        IncomingOffer.Item[] items = new IncomingOffer.Item[count];
        long total = 0;
        int i;
        for (i = 0; i < count; i++) {
            String name;
            try {
                name = LanHttpProtocol.sanitizeFileName(
                        LanHttpProtocol.decodeHeaderValue(
                                getHeader(headers, "x-file-" + i + "-name")));
            } catch (Exception exception) {
                throw new IOException("File name is invalid.");
            }
            long size = parseNonnegativeLong(
                    getHeader(headers, "x-file-" + i + "-size"));
            if (size < 0 ||
                    size > LanHttpProtocol.MAXIMUM_TRANSFER_BYTES ||
                    total > LanHttpProtocol.MAXIMUM_TRANSFER_BYTES - size) {
                throw new IOException("File size is invalid.");
            }
            total += size;
            items[i] = new IncomingOffer.Item(name, size);
        }
        return new IncomingOffer(transferId, senderName, senderAddress,
                false, items, total);
    }

    private static long parseNonnegativeLong(String value)
            throws IOException {
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0 ? -1 : parsed;
        } catch (Exception exception) {
            throw new IOException("Content size is invalid.");
        }
    }

    private static long parseContentLength(HttpRequest request)
            throws IOException {
        if (getHeader(request.headers, "transfer-encoding") != null) {
            throw new IOException("Chunked uploads are not supported.");
        }
        try {
            long length = Long.parseLong(header(request, "content-length"));
            if (length < 0) {
                throw new IOException("Invalid Content-Length.");
            }
            return length;
        } catch (NumberFormatException exception) {
            throw new IOException("A valid Content-Length is required.");
        }
    }

    private static HttpRequest readRequest(InputStream input)
            throws IOException {
        String requestLine = LanHttpProtocol.readLine(input, 4096);
        if (requestLine == null) {
            throw new IOException("Request line is missing.");
        }
        int firstSpace = requestLine.indexOf(' ');
        int secondSpace = firstSpace < 0 ? -1 :
                requestLine.indexOf(' ', firstSpace + 1);
        if (firstSpace <= 0 || secondSpace <= firstSpace + 1) {
            throw new IOException("Request line is invalid.");
        }
        String version = requestLine.substring(secondSpace + 1);
        if (!version.startsWith("HTTP/")) {
            throw new IOException("HTTP version is invalid.");
        }
        String method = requestLine.substring(0, firstSpace);
        String path = requestLine.substring(firstSpace + 1, secondSpace);
        Hashtable headers = new Hashtable();
        int totalBytes = requestLine.length() + 2;
        while (true) {
            String line = LanHttpProtocol.readLine(input,
                    LanHttpProtocol.MAXIMUM_HEADER_BYTES);
            if (line == null) {
                throw new IOException("Request headers ended unexpectedly.");
            }
            if (line.length() == 0) {
                break;
            }
            totalBytes += line.length() + 2;
            if (totalBytes > LanHttpProtocol.MAXIMUM_HEADER_BYTES) {
                throw new IOException("Request headers are too large.");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IOException("HTTP header is malformed.");
            }
            String key = asciiLowercase(line.substring(0, colon).trim());
            String value = line.substring(colon + 1).trim();
            headers.put(key, value);
        }
        return new HttpRequest(method, path, headers);
    }

    private static String header(HttpRequest request, String name) {
        return getHeader(request.headers, name);
    }

    private static String getHeader(Hashtable headers, String name) {
        return (String) headers.get(asciiLowercase(name));
    }

    private static String asciiLowercase(String value) {
        StringBuffer result = new StringBuffer(value.length());
        int i;
        for (i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + ('a' - 'A'));
            }
            result.append(c);
        }
        return result.toString();
    }

    private static boolean hasHeaderPrefix(Hashtable headers, String prefix) {
        java.util.Enumeration names = headers.keys();
        while (names.hasMoreElements()) {
            String key = (String) names.nextElement();
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void readExactly(InputStream input, byte[] buffer)
            throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = input.read(buffer, offset, buffer.length - offset);
            if (count < 0) {
                throw new IOException("Kết nối bị ngắt khi nhận văn bản.");
            }
            offset += count;
        }
    }

    private static final class HttpRequest {
        final String method;
        final String path;
        final Hashtable headers;

        HttpRequest(String method, String path, Hashtable headers) {
            this.method = method;
            this.path = path;
            this.headers = headers;
        }
    }

    private static final class ApprovedTransfer {
        final IncomingOffer offer;
        final long createdAt;
        final boolean[] inProgress;
        final boolean[] completed;

        ApprovedTransfer(IncomingOffer offer) {
            this.offer = offer;
            createdAt = System.currentTimeMillis();
            inProgress = new boolean[offer.items.length];
            completed = new boolean[offer.items.length];
        }

        synchronized boolean reserve(int index, String name, long size) {
            if (offer.text && index == 0 && "Tin nhắn văn bản".equals(name) &&
                    offer.items[0].size == size && !inProgress[0] &&
                    !completed[0]) {
                inProgress[0] = true;
                return true;
            }
            if (offer.text || index < 0 || index >= offer.items.length) {
                return false;
            }
            IncomingOffer.Item expected = offer.items[index];
            if (inProgress[index] || completed[index] ||
                    expected.size != size || !expected.name.equals(name)) {
                return false;
            }
            inProgress[index] = true;
            return true;
        }

        synchronized void release(int index) {
            if (index >= 0 && index < inProgress.length) {
                inProgress[index] = false;
            }
        }

        synchronized void complete(int index) {
            if (index >= 0 && index < completed.length) {
                inProgress[index] = false;
                completed[index] = true;
            }
        }

        synchronized boolean isComplete() {
            int i;
            for (i = 0; i < completed.length; i++) {
                if (!completed[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
