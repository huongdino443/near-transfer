package com.nearbyshare.j2me;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

final class LanHttpProtocol {
    static final int HTTP_PORT = 45321;
    static final int DISCOVERY_PORT = 45322;
    static final int MAXIMUM_FILES = 20;
    static final int MAXIMUM_TEXT_BYTES = 256 * 1024;
    static final long MAXIMUM_TRANSFER_BYTES = 2147483647L;
    static final int MAXIMUM_HEADER_BYTES = 32 * 1024;

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private LanHttpProtocol() {
    }

    static String newTransferId() {
        StringBuffer id = new StringBuffer(32);
        long seed = System.currentTimeMillis() ^
                ((long) Thread.currentThread().hashCode() << 21);
        java.util.Random random = new java.util.Random(seed);
        char[] lowerHex = "0123456789abcdef".toCharArray();
        int i;
        for (i = 0; i < 32; i++) {
            id.append(lowerHex[random.nextInt(16)]);
        }
        return id.toString();
    }

    static boolean isTransferId(String value) {
        if (value == null || value.length() != 32) {
            return false;
        }
        int i;
        for (i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    static String encodeHeaderValue(String value) throws IOException {
        byte[] bytes;
        try {
            bytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IOException("UTF-8 is unavailable.");
        }
        StringBuffer encoded = new StringBuffer(bytes.length);
        int i;
        for (i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') ||
                    (b >= '0' && b <= '9') || b == '-' || b == '_' ||
                    b == '.' || b == '*' || b == '!' || b == '\'' ||
                    b == '(' || b == ')') {
                encoded.append((char) b);
            } else if (b == ' ') {
                encoded.append('+');
            } else {
                encoded.append('%');
                encoded.append(HEX[b >> 4]);
                encoded.append(HEX[b & 15]);
            }
        }
        return encoded.toString();
    }

    static String decodeHeaderValue(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '+') {
                bytes.write(' ');
                i++;
            } else if (c == '%' && i + 2 < value.length()) {
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IOException("Invalid encoded header value.");
                }
                bytes.write((high << 4) | low);
                i += 3;
            } else if (c <= 0x7f) {
                bytes.write((byte) c);
                i++;
            } else {
                throw new IOException("Invalid encoded header value.");
            }
        }
        try {
            return new String(bytes.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IOException("UTF-8 is unavailable.");
        }
    }

    static String sanitizeFileName(String requestedName) {
        if (requestedName == null || requestedName.trim().length() == 0) {
            return "received-file";
        }
        String normalized = requestedName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String leaf = slash >= 0 ? normalized.substring(slash + 1) :
                normalized;
        StringBuffer result = new StringBuffer();
        int i;
        for (i = 0; i < leaf.length(); i++) {
            char c = leaf.charAt(i);
            if (c < 32 || c == 127 || c == ':' || c == '*' || c == '?' ||
                    c == '"' || c == '<' || c == '>' || c == '|') {
                continue;
            }
            result.append(c);
        }
        String safe = result.toString().trim();
        while (safe.endsWith(".")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        if (safe.length() == 0 || safe.equals(".") || safe.equals("..")) {
            safe = "received-file";
        }
        if (safe.length() > 120) {
            safe = safe.substring(0, 120);
        }
        String upper = safe.toUpperCase();
        int dot = upper.indexOf('.');
        String stem = dot >= 0 ? upper.substring(0, dot) : upper;
        if (stem.equals("CON") || stem.equals("PRN") || stem.equals("AUX") ||
                stem.equals("NUL") || isWindowsNumberedName(stem, "COM") ||
                isWindowsNumberedName(stem, "LPT")) {
            safe = "_" + safe;
        }
        return safe;
    }

    private static boolean isWindowsNumberedName(String name, String prefix) {
        if (!name.startsWith(prefix) || name.length() != 4) {
            return false;
        }
        char number = name.charAt(3);
        return number >= '1' && number <= '9';
    }

    static String readLine(InputStream input, int maximumBytes)
            throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        boolean previousWasCarriageReturn = false;
        while (line.size() <= maximumBytes) {
            int value = input.read();
            if (value < 0) {
                return line.size() == 0 ? null :
                        throwIncompleteLine();
            }
            if (previousWasCarriageReturn && value == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                return new String(bytes, 0, length, "UTF-8");
            }
            line.write(value);
            previousWasCarriageReturn = value == '\r';
        }
        throw new IOException("HTTP headers are too large.");
    }

    private static String throwIncompleteLine() throws IOException {
        throw new IOException("HTTP request ended unexpectedly.");
    }

    static void writeAscii(OutputStream output, String value)
            throws IOException {
        byte[] bytes;
        try {
            bytes = value.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException exception) {
            throw new IOException("HTTP header encoding is unavailable.");
        }
        output.write(bytes);
    }

    static void writeResponse(OutputStream output, int status, String reason,
                              String message) throws IOException {
        byte[] body;
        try {
            body = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException exception) {
            body = new byte[0];
        }
        writeAscii(output, "HTTP/1.1 " + status + " " + reason + "\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Connection: close\r\n\r\n");
        output.write(body);
        output.flush();
    }

    static int readResponseStatus(InputStream input) throws IOException {
        String line = readLine(input, 4096);
        if (line == null) {
            throw new IOException("The receiving device closed the connection.");
        }
        int firstSpace = line.indexOf(' ');
        if (firstSpace < 0 || firstSpace + 4 > line.length()) {
            throw new IOException("Invalid HTTP response.");
        }
        try {
            return Integer.parseInt(line.substring(firstSpace + 1,
                    firstSpace + 4));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid HTTP response.");
        }
    }

    static int parsePort(String value, int fallback) {
        if (value == null || value.length() == 0) {
            return fallback;
        }
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port <= 65535 ? port : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    static String parseIpv4Address(String value) {
        if (value == null) {
            return null;
        }
        String address = value.trim();
        int scheme = address.indexOf("://");
        if (scheme >= 0) {
            address = address.substring(scheme + 3);
        }
        int slash = address.indexOf('/');
        if (slash >= 0) {
            address = address.substring(slash + 1);
        }
        int colon = address.lastIndexOf(':');
        if (colon >= 0) {
            address = address.substring(0, colon);
        }
        if (!isIpv4(address)) {
            return null;
        }
        return address;
    }

    static boolean isIpv4(String address) {
        if (address == null) {
            return false;
        }
        int parts = 0;
        int start = 0;
        int i;
        for (i = 0; i <= address.length(); i++) {
            if (i == address.length() || address.charAt(i) == '.') {
                if (parts >= 4 || i == start || i - start > 3) {
                    return false;
                }
                int value = 0;
                int j;
                for (j = start; j < i; j++) {
                    char c = address.charAt(j);
                    if (c < '0' || c > '9') {
                        return false;
                    }
                    value = value * 10 + c - '0';
                }
                if (value > 255) {
                    return false;
                }
                parts++;
                start = i + 1;
            }
        }
        return parts == 4;
    }

    static void copyExactly(InputStream input, OutputStream output, long size,
                            TransferProgressListener progress, int itemIndex,
                            String itemName) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = size;
        long transferred = 0;
        long lastReport = System.currentTimeMillis();
        while (remaining > 0) {
            int wanted = (int) Math.min((long) buffer.length, remaining);
            int count = input.read(buffer, 0, wanted);
            if (count < 0) {
                throw new IOException("Kết nối bị ngắt giữa chừng.");
            }
            output.write(buffer, 0, count);
            remaining -= count;
            transferred += count;
            long now = System.currentTimeMillis();
            if (now - lastReport >= 150 || remaining == 0) {
                if (progress != null) {
                    progress.onProgress(itemIndex, itemName, transferred, size);
                }
                lastReport = now;
            }
        }
        if (size == 0 && progress != null) {
            progress.onProgress(itemIndex, itemName, 0, 0);
        }
    }
}

interface TransferProgressListener {
    void onProgress(int itemIndex, String itemName, long transferred,
                    long total);
}
