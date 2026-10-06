package com.nearbyshare.j2me;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/** Read-only directory listing using the optional JSR-75 FileConnection API. */
final class Jsr75FileBrowser {
    private static final int MAX_ENTRIES = 200;

    private Jsr75FileBrowser() {
    }

    static FileBrowserListing list(String directory, boolean mediaOnly)
            throws IOException {
        return list(directory, mediaOnly, false);
    }

    static FileBrowserListing list(String directory, boolean mediaOnly,
                                   boolean directoriesOnly)
            throws IOException {
        if (directory == null) {
            return listRoots();
        }
        return listDirectory(directory, mediaOnly, directoriesOnly);
    }

    private static FileBrowserListing listRoots() {
        Vector entries = new Vector();
        Enumeration roots = FileSystemRegistry.listRoots();
        if (roots != null) {
            while (roots.hasMoreElements() &&
                    entries.size() < MAX_ENTRIES) {
                String root = (String) roots.nextElement();
                if (root != null && root.length() > 0) {
                    entries.addElement(new FileBrowserEntry(root, root, true));
                }
            }
        }
        return makeListing(entries, roots != null &&
                roots.hasMoreElements());
    }

    private static FileBrowserListing listDirectory(String path,
                                                   boolean mediaOnly,
                                                   boolean directoriesOnly)
            throws IOException {
        Vector entries = new Vector();
        FileConnection directory = (FileConnection) Connector.open(
                toFileUrl(path), Connector.READ);
        boolean truncated = false;
        try {
            Enumeration names = directory.list("*", false);
            while (names.hasMoreElements()) {
                String rawName = (String) names.nextElement();
                boolean isDirectory = rawName.endsWith("/");
                if (directoriesOnly && !isDirectory) {
                    continue;
                }
                if (!isDirectory && mediaOnly && !isMediaFile(rawName)) {
                    continue;
                }
                if (entries.size() >= MAX_ENTRIES) {
                    truncated = true;
                    break;
                }
                String displayName = rawName;
                if (isDirectory && displayName.length() > 1) {
                    displayName = displayName.substring(
                            0, displayName.length() - 1);
                }
                entries.addElement(new FileBrowserEntry(displayName,
                        joinPath(path, rawName), isDirectory));
            }
        } finally {
            try {
                directory.close();
            } catch (IOException ignored) {
            }
        }
        return makeListing(entries, truncated);
    }

    private static FileBrowserListing makeListing(Vector entries,
                                                  boolean truncated) {
        FileBrowserEntry[] result = new FileBrowserEntry[entries.size()];
        int i;
        for (i = 0; i < result.length; i++) {
            result[i] = (FileBrowserEntry) entries.elementAt(i);
        }
        return new FileBrowserListing(result, FileBrowserListing.READY,
                truncated ? "Đang hiển thị tối đa 200 mục trong thư mục." : "",
                truncated);
    }

    private static String joinPath(String directory, String name) {
        StringBuffer result = new StringBuffer(directory);
        if (result.length() > 0 &&
                result.charAt(result.length() - 1) != '/' &&
                result.charAt(result.length() - 1) != '\\') {
            result.append('/');
        }
        result.append(name);
        return result.toString();
    }

    private static boolean isMediaFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".bmp") || lower.endsWith(".webp") ||
                lower.endsWith(".3gp") || lower.endsWith(".3g2") ||
                lower.endsWith(".mp4") || lower.endsWith(".m4v") ||
                lower.endsWith(".avi") || lower.endsWith(".mov") ||
                lower.endsWith(".mpg") || lower.endsWith(".mpeg") ||
                lower.endsWith(".wmv");
    }

    static String toFileUrl(String path) throws IOException {
        String normalized = path.replace('\\', '/');
        byte[] bytes;
        try {
            bytes = normalized.getBytes("UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IOException("UTF-8 encoding is unavailable.");
        }

        StringBuffer url = new StringBuffer("file:///");
        final char[] hex = "0123456789ABCDEF".toCharArray();
        int i;
        for (i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            if (isPathCharacter(value)) {
                url.append((char) value);
            } else {
                url.append('%');
                url.append(hex[value >> 4]);
                url.append(hex[value & 15]);
            }
        }
        return url.toString();
    }

    private static boolean isPathCharacter(int value) {
        return (value >= 'a' && value <= 'z') ||
                (value >= 'A' && value <= 'Z') ||
                (value >= '0' && value <= '9') ||
                value == '-' || value == '.' || value == '_' ||
                value == '~' || value == '/' || value == ':' ||
                value == '@' || value == '!' || value == '$' ||
                value == '&' || value == '\'' || value == '(' ||
                value == ')' || value == '*' || value == '+' ||
                value == ',' || value == ';' || value == '=';
    }
}