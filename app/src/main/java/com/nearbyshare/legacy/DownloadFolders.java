package com.nearbyshare.legacy;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

final class DownloadFolders {
    static final String RECEIVED_DIRECTORY_NAME = "Near Transfer";
    private static final String PREFERENCES = "near_transfer_settings";
    private static final String RECEIVED_DIRECTORY_KEY = "received_directory";

    private DownloadFolders() {
    }

    static File defaultReceivedDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), RECEIVED_DIRECTORY_NAME);
    }

    static File externalStorageRoot() {
        return Environment.getExternalStorageDirectory();
    }

    static File receivedDirectory(Context context) {
        if (context == null) {
            return defaultReceivedDirectory();
        }
        String path = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(RECEIVED_DIRECTORY_KEY, null);
        if (path == null || path.trim().length() == 0) {
            return defaultReceivedDirectory();
        }
        return new File(path);
    }

    static String displayPath(Context context) {
        File directory = receivedDirectory(context);
        try {
            String root = externalStorageRoot().getCanonicalPath();
            String path = directory.getCanonicalPath();
            if (path.startsWith(root + File.separator)) {
                return path.substring(root.length() + 1);
            }
            return "Chọn thư mục";
        } catch (IOException e) {
            return "Chọn thư mục";
        }
    }

    static File ensureReceivedDirectory(Context context) throws IOException {
        File directory = receivedDirectory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Không thể tạo thư mục nhận: " +
                    directory.getAbsolutePath());
        }
        if (!directory.isDirectory() || !directory.canWrite()) {
            throw new IOException("Không thể ghi vào thư mục nhận: " +
                    directory.getAbsolutePath());
        }
        return directory;
    }

    static boolean setReceivedDirectory(Context context, File directory) {
        if (context == null || directory == null ||
                !isBelowExternalStorageRoot(directory)) {
            return false;
        }
        try {
            File canonicalDirectory = directory.getCanonicalFile();
            if (!canonicalDirectory.isDirectory() &&
                    canonicalDirectory.equals(defaultReceivedDirectory()
                            .getCanonicalFile())) {
                canonicalDirectory.mkdirs();
            }
            if (!canonicalDirectory.isDirectory() || !canonicalDirectory.canWrite()) {
                return false;
            }
            return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(RECEIVED_DIRECTORY_KEY,
                            canonicalDirectory.getCanonicalPath())
                    .commit();
        } catch (IOException e) {
            return false;
        }
    }

    static boolean contains(Context context, File file) {
        if (file == null) {
            return false;
        }
        try {
            String directory = receivedDirectory(context).getCanonicalPath() +
                    File.separator;
            return file.getCanonicalPath().startsWith(directory);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isInsideExternalStorage(File directory) {
        if (directory == null) {
            return false;
        }
        try {
            String root = externalStorageRoot().getCanonicalPath();
            String path = directory.getCanonicalPath();
            return path.equals(root) || path.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isBelowExternalStorageRoot(File directory) {
        if (directory == null) {
            return false;
        }
        try {
            String root = externalStorageRoot().getCanonicalPath();
            String path = directory.getCanonicalPath();
            return path.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}