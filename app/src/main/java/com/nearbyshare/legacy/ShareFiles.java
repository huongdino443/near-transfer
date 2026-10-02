package com.nearbyshare.legacy;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

final class ShareFiles {
    private ShareFiles() {
    }

    static String encodeHeaderValue(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    static String decodeHeaderValue(String value) throws Exception {
        return URLDecoder.decode(value, "UTF-8");
    }

    static String safeFileName(String name) {
        if (name == null) {
            return "received-file";
        }
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c >= 32 && c != 127 && c != ':' && c != '*' && c != '?' &&
                    c != '"' && c != '<' && c != '>' && c != '|') {
                result.append(c);
            }
        }
        String safe = result.toString().trim();
        if (safe.length() == 0 || ".".equals(safe) || "..".equals(safe)) {
            return "received-file";
        }
        if (safe.length() > 120) {
            safe = safe.substring(0, 120);
        }
        return safe;
    }

    static File uniqueFile(File directory, String requestedName) throws IOException {
        String safeName = safeFileName(requestedName);
        int dot = safeName.lastIndexOf('.');
        String base = dot > 0 ? safeName.substring(0, dot) : safeName;
        String extension = dot > 0 ? safeName.substring(dot) : "";
        for (int suffix = 0; suffix < 10000; suffix++) {
            String candidateName = suffix == 0 ? safeName :
                    base + " (" + suffix + ")" + extension;
            File candidate = new File(directory, candidateName);
            if (candidate.createNewFile()) {
                return candidate;
            }
        }
        File fallback = new File(directory, String.format(Locale.US, "%d-%s",
                Long.valueOf(System.currentTimeMillis()), safeName));
        if (fallback.createNewFile()) {
            return fallback;
        }
        throw new IOException("Không tạo được file đích.");
    }
}