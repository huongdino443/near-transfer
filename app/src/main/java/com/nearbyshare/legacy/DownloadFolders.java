package com.nearbyshare.legacy;

import android.os.Environment;

import java.io.File;
import java.io.IOException;

final class DownloadFolders {
    static final String RECEIVED_DIRECTORY_NAME = "Near Transfer";

    private DownloadFolders() {
    }

    static File receivedDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), RECEIVED_DIRECTORY_NAME);
    }

    static File ensureReceivedDirectory() throws IOException {
        File directory = receivedDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Không tạo được thư mục Download/Near Transfer/.");
        }
        return directory;
    }

    static boolean contains(File file) {
        if (file == null) {
            return false;
        }
        try {
            String directory = receivedDirectory().getCanonicalPath() +
                    File.separator;
            return file.getCanonicalPath().startsWith(directory);
        } catch (IOException e) {
            return false;
        }
    }
}