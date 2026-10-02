package com.nearbyshare.legacy;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Locale;

final class IncomingOffer {
    final String transferId;
    final String senderName;
    final String senderAddress;
    final boolean textTransfer;
    final ArrayList<Item> files;
    final long totalBytes;

    IncomingOffer(String transferId, String senderName, String senderAddress,
                  boolean textTransfer, ArrayList<Item> files, long totalBytes) {
        this.transferId = transferId;
        this.senderName = senderName;
        this.senderAddress = senderAddress;
        this.textTransfer = textTransfer;
        this.files = files;
        this.totalBytes = totalBytes;
    }

    String summary() {
        StringBuilder summary = new StringBuilder();
        summary.append(senderName).append(" (").append(senderAddress).append(")")
                .append("\n");
        if (textTransfer) {
            summary.append("Một tin nhắn văn bản");
        } else {
            summary.append(files.size()).append(" file muốn gửi");
        }
        summary.append(" · ").append(formatSize(totalBytes)).append("\n\n");
        for (int i = 0; i < files.size(); i++) {
            Item file = files.get(i);
            summary.append(i + 1).append(". ").append(file.name)
                    .append(" — ").append(formatSize(file.size));
            if (i + 1 < files.size()) {
                summary.append('\n');
            }
        }
        return summary.toString();
    }

    static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB",
                    bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.2f GB",
                bytes / (1024.0 * 1024.0 * 1024.0));
    }

    static final class Item {
        final String name;
        final long size;
        final Uri previewUri;

        Item(String name, long size) {
            this(name, size, null);
        }

        Item(String name, long size, Uri previewUri) {
            this.name = name;
            this.size = size;
            this.previewUri = previewUri;
        }
    }
}