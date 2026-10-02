package com.nearbyshare.legacy;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;

final class FileSender {
    private static final long MAX_FILE_BYTES = 2147483647L;
    private static final int MAX_FILES = 20;
    private static final int MAX_TEXT_BYTES = 262144;

    interface Listener {
        void onStatus(String transferId, String message);
        void onAccepted(String transferId, ArrayList<IncomingOffer.Item> items);
        void onProgress(String transferId, int itemIndex, long transferred, long totalBytes);
        void onItemComplete(String transferId, int itemIndex);
        void onComplete(String transferId, String message);
        void onFailure(String transferId, String message);
    }

    private FileSender() {
    }

    static void send(final Context context, final Peer peer, final ArrayList<Uri> uris,
                     final String senderName, final Listener listener) {
        final ArrayList<Uri> selected = new ArrayList<Uri>(uris);
        Thread thread = new Thread(new Runnable() {
            public void run() {
                sendOnWorker(context, peer, selected, senderName, listener);
            }
        }, "nearby-file-send");
        thread.start();
    }

    static void sendText(final Peer peer, final String text, final String senderName,
                         final Listener listener) {
        final String transferId = createTransferId();
        Thread thread = new Thread(new Runnable() {
            public void run() {
                sendTextOnWorker(peer, text, senderName, transferId, listener);
            }
        }, "nearby-text-send");
        thread.start();
    }

    private static void sendOnWorker(Context context, Peer peer, ArrayList<Uri> uris,
                                     String senderName, Listener listener) {
        ArrayList<Payload> payloads = new ArrayList<Payload>();
        String transferId = createTransferId();
        try {
            if (uris.size() == 0 || uris.size() > MAX_FILES) {
                throw new Exception("Có thể gửi từ 1 đến 20 file mỗi lần.");
            }
            long totalBytes = 0L;
            for (Uri uri : uris) {
                Payload payload = describePayload(context, uri);
                payloads.add(payload);
                if (payload.size < 0 || payload.size > MAX_FILE_BYTES) {
                    throw new Exception("Mỗi file phải nhỏ hơn 2 GiB.");
                }
                if (totalBytes > MAX_FILE_BYTES - payload.size) {
                    throw new Exception("Tổng dung lượng mỗi lần gửi phải nhỏ hơn 2 GiB.");
                }
                totalBytes += payload.size;
            }

            notifyStatus(listener, transferId, "Đang chờ " + peer.name + " xác nhận…");
            if (!submitOffer(peer, senderName, transferId, payloads)) {
                notifyFailure(listener, transferId,
                        "Thiết bị bên kia đã từ chối hoặc chưa xác nhận.");
                return;
            }
            notifyAccepted(listener, transferId, createItems(payloads));
            for (int i = 0; i < payloads.size(); i++) {
                Payload payload = payloads.get(i);
                notifyStatus(listener, transferId, "Đang gửi file " + (i + 1) +
                        "/" + payloads.size() + ": " + payload.name);
                uploadFile(context, peer, transferId, i, payload, listener);
            }
            notifyComplete(listener, transferId, "Đã gửi " + payloads.size() + " file.");
        } catch (Exception e) {
            notifyFailure(listener, transferId, e.getMessage() == null ?
                    "Không gửi được file." : e.getMessage());
        } finally {
            for (Payload payload : payloads) {
                if (payload.temporaryFile != null) {
                    payload.temporaryFile.delete();
                }
            }
        }
    }

    private static void sendTextOnWorker(Peer peer, String text, String senderName,
                                         String transferId, Listener listener) {
        try {
            if (text == null || text.length() == 0) {
                throw new Exception("Văn bản đang trống.");
            }
            byte[] bytes = text.getBytes("UTF-8");
            if (bytes.length < 1 || bytes.length > MAX_TEXT_BYTES) {
                throw new Exception("Văn bản phải nhỏ hơn hoặc bằng 256 KB.");
            }
            notifyStatus(listener, transferId, "Đang chờ " + peer.name + " xác nhận…");
            if (!submitTextOffer(peer, senderName, transferId, bytes.length)) {
                notifyFailure(listener, transferId,
                        "Thiết bị bên kia đã từ chối hoặc chưa xác nhận.");
                return;
            }
            ArrayList<IncomingOffer.Item> items =
                    new ArrayList<IncomingOffer.Item>(1);
            items.add(new IncomingOffer.Item("Tin nhắn văn bản", bytes.length));
            notifyAccepted(listener, transferId, items);
            notifyStatus(listener, transferId, "Đang gửi tin nhắn văn bản…");
            uploadText(peer, transferId, bytes, listener);
            notifyComplete(listener, transferId, "Đã gửi văn bản.");
        } catch (Exception e) {
            notifyFailure(listener, transferId, e.getMessage() == null ?
                    "Không gửi được văn bản." : e.getMessage());
        }
    }

    private static boolean submitOffer(Peer peer, String senderName,
                                       String transferId, ArrayList<Payload> payloads)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + peer.address + ":" + peer.port + "/offer");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(125000);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("X-Transfer-Id", transferId);
            connection.setRequestProperty("X-Transfer-Type", "files");
            connection.setRequestProperty("X-Sender-Name",
                    ShareFiles.encodeHeaderValue(senderName));
            connection.setRequestProperty("X-File-Count",
                    String.valueOf(payloads.size()));
            for (int i = 0; i < payloads.size(); i++) {
                Payload payload = payloads.get(i);
                connection.setRequestProperty("X-File-" + i + "-Name",
                        ShareFiles.encodeHeaderValue(payload.name));
                connection.setRequestProperty("X-File-" + i + "-Size",
                        String.valueOf(payload.size));
            }
            connection.setFixedLengthStreamingMode(0);
            OutputStream output = connection.getOutputStream();
            output.close();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true;
            }
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                return false;
            }
            throw new Exception("Máy nhận trả về HTTP " + responseCode + ".");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean submitTextOffer(Peer peer, String senderName,
                                           String transferId, int textSize)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + peer.address + ":" + peer.port + "/offer");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(125000);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("X-Transfer-Id", transferId);
            connection.setRequestProperty("X-Transfer-Type", "text");
            connection.setRequestProperty("X-Sender-Name",
                    ShareFiles.encodeHeaderValue(senderName));
            connection.setRequestProperty("X-Text-Size", String.valueOf(textSize));
            connection.setFixedLengthStreamingMode(0);
            OutputStream output = connection.getOutputStream();
            output.close();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true;
            }
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                return false;
            }
            throw new Exception("Máy nhận trả về HTTP " + responseCode + ".");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void uploadFile(Context context, Peer peer, String transferId,
                                   int index, Payload payload, Listener listener)
            throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            URL url = new URL("http://" + peer.address + ":" + peer.port + "/upload");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(60000);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("X-Transfer-Id", transferId);
            connection.setRequestProperty("X-File-Index", String.valueOf(index));
            connection.setRequestProperty("X-File-Name",
                    ShareFiles.encodeHeaderValue(payload.name));
            connection.setFixedLengthStreamingMode((int) payload.size);

            input = payload.file == null ?
                    context.getContentResolver().openInputStream(payload.uri) :
                    new FileInputStream(payload.file);
            if (input == null) {
                throw new Exception("Không mở được file: " + payload.name);
            }
            OutputStream output = new BufferedOutputStream(connection.getOutputStream());
            BufferedInputStream bufferedInput = new BufferedInputStream(input);
            byte[] buffer = new byte[8192];
            long sent = 0L;
            long lastUpdate = 0L;
            int count;
            while ((count = bufferedInput.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                sent += count;
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 150L || sent == payload.size) {
                    notifyProgress(listener, transferId, index, sent, payload.size);
                    lastUpdate = now;
                }
            }
            if (payload.size == 0L) {
                notifyProgress(listener, transferId, index, 0L, 0L);
            }
            output.flush();
            output.close();
            input.close();
            input = null;
            if (sent != payload.size) {
                throw new Exception("Kích thước file thay đổi khi gửi: " + payload.name);
            }
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_CREATED) {
                throw new Exception("Không gửi được " + payload.name +
                        " (HTTP " + responseCode + ").");
            }
            notifyItemComplete(listener, transferId, index);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void uploadText(Peer peer, String transferId, byte[] bytes,
                                   Listener listener) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + peer.address + ":" + peer.port +
                    "/upload-text");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(60000);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("X-Transfer-Id", transferId);
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream output = new BufferedOutputStream(connection.getOutputStream());
            int offset = 0;
            long lastUpdate = 0L;
            while (offset < bytes.length) {
                int count = Math.min(8192, bytes.length - offset);
                output.write(bytes, offset, count);
                offset += count;
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 150L || offset == bytes.length) {
                    notifyProgress(listener, transferId, 0, offset, bytes.length);
                    lastUpdate = now;
                }
            }
            output.flush();
            output.close();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_CREATED) {
                throw new Exception("Không gửi được văn bản (HTTP " +
                        responseCode + ").");
            }
            notifyItemComplete(listener, transferId, 0);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String createTransferId() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder id = new StringBuilder(32);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned < 16) {
                id.append('0');
            }
            id.append(Integer.toHexString(unsigned));
        }
        return id.toString();
    }

    static String displayName(Context context, Uri uri) {
        if (uri == null) {
            return "file";
        }
        String name = uri.getLastPathSegment();
        if ("file".equals(uri.getScheme())) {
            name = new File(uri.getPath()).getName();
        } else {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri,
                        new String[] {"_display_name"}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex("_display_name");
                    if (column >= 0) {
                        name = cursor.getString(column);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return ShareFiles.safeFileName(name);
    }

    private static Payload describePayload(Context context, Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        long size = -1L;
        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            name = file.getName();
            size = file.length();
            return new Payload(name, size, file, null);
        }

        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[] {"_display_name", "_size"},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex("_display_name");
                int sizeColumn = cursor.getColumnIndex("_size");
                if (nameColumn >= 0) {
                    name = cursor.getString(nameColumn);
                }
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    size = cursor.getLong(sizeColumn);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (name == null || name.length() == 0) {
            name = "shared-file";
        }
        name = ShareFiles.safeFileName(name);
        if (size >= 0) {
            return new Payload(name, size, null, null, uri);
        }

        File temporary = File.createTempFile("nearby-send-", ".tmp",
                context.getCacheDir());
        InputStream input = null;
        FileOutputStream output = null;
        boolean complete = false;
        long copied = 0L;
        try {
            input = resolver.openInputStream(uri);
            if (input == null) {
                throw new Exception("Không mở được file đã chọn.");
            }
            output = new FileOutputStream(temporary);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > MAX_FILE_BYTES) {
                    throw new Exception("File phải nhỏ hơn 2 GiB.");
                }
                output.write(buffer, 0, count);
            }
            output.flush();
            complete = true;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
            if (!complete) {
                temporary.delete();
            }
        }
        return new Payload(name, copied, temporary, temporary, uri);
    }

    private static ArrayList<IncomingOffer.Item> createItems(ArrayList<Payload> payloads) {
        ArrayList<IncomingOffer.Item> items =
                new ArrayList<IncomingOffer.Item>(payloads.size());
        for (Payload payload : payloads) {
            Uri previewUri = payload.uri;
            if (previewUri == null && payload.file != null) {
                previewUri = Uri.fromFile(payload.file);
            }
            items.add(new IncomingOffer.Item(payload.name, payload.size, previewUri));
        }
        return items;
    }

    private static void notifyStatus(Listener listener, String transferId,
                                     String message) {
        if (listener != null) {
            listener.onStatus(transferId, message);
        }
    }

    private static void notifyAccepted(Listener listener, String transferId,
                                       ArrayList<IncomingOffer.Item> items) {
        if (listener != null) {
            listener.onAccepted(transferId, items);
        }
    }

    private static void notifyProgress(Listener listener, String transferId,
                                       int index, long transferred, long totalBytes) {
        if (listener != null) {
            listener.onProgress(transferId, index, transferred, totalBytes);
        }
    }

    private static void notifyItemComplete(Listener listener, String transferId,
                                           int index) {
        if (listener != null) {
            listener.onItemComplete(transferId, index);
        }
    }

    private static void notifyComplete(Listener listener, String transferId,
                                       String message) {
        if (listener != null) {
            listener.onComplete(transferId, message);
        }
    }

    private static void notifyFailure(Listener listener, String transferId,
                                      String message) {
        if (listener != null) {
            listener.onFailure(transferId, message);
        }
    }

    private static final class Payload {
        final String name;
        final long size;
        final File file;
        final File temporaryFile;
        final Uri uri;

        Payload(String name, long size, File file, File temporaryFile) {
            this.name = name;
            this.size = size;
            this.file = file;
            this.temporaryFile = temporaryFile;
            this.uri = null;
        }

        Payload(String name, long size, File file, File temporaryFile, Uri uri) {
            this.name = name;
            this.size = size;
            this.file = file;
            this.temporaryFile = temporaryFile;
            this.uri = uri;
        }
    }
}