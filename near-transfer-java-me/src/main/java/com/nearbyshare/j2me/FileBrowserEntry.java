package com.nearbyshare.j2me;

final class FileBrowserEntry {
    final String name;
    final String path;
    final boolean directory;
    boolean selected;

    FileBrowserEntry(String name, String path, boolean directory) {
        this.name = name;
        this.path = path;
        this.directory = directory;
    }
}

final class FileBrowserListing {
    static final int READY = 0;
    static final int UNSUPPORTED = 1;
    static final int ERROR = 2;

    final FileBrowserEntry[] entries;
    final int state;
    final String message;
    final boolean truncated;

    FileBrowserListing(FileBrowserEntry[] entries, int state,
                       String message, boolean truncated) {
        this.entries = entries;
        this.state = state;
        this.message = message;
        this.truncated = truncated;
    }

    static FileBrowserListing unsupported() {
        return new FileBrowserListing(new FileBrowserEntry[0], UNSUPPORTED,
                "Máy này không hỗ trợ JSR-75 để duyệt bộ nhớ.",
                false);
    }

    static FileBrowserListing error() {
        return new FileBrowserListing(new FileBrowserEntry[0], ERROR,
                "Không đọc được thư mục. Kiểm tra quyền truy cập bộ nhớ.",
                false);
    }
}