package com.nearbyshare.j2me;

final class FileBrowserWorker extends Thread {
    private final NearTransferCanvas canvas;
    private final int requestId;
    private final String directory;
    private final boolean mediaOnly;
    private final boolean directoriesOnly;

    FileBrowserWorker(NearTransferCanvas canvas, int requestId,
                      String directory, boolean mediaOnly,
                      boolean directoriesOnly) {
        this.canvas = canvas;
        this.requestId = requestId;
        this.directory = directory;
        this.mediaOnly = mediaOnly;
        this.directoriesOnly = directoriesOnly;
    }

    public void run() {
        FileBrowserListing listing;
        try {
            listing = Jsr75FileBrowser.list(directory, mediaOnly,
                    directoriesOnly);
        } catch (NoClassDefFoundError error) {
            listing = FileBrowserListing.unsupported();
        } catch (Exception exception) {
            listing = FileBrowserListing.error();
        }
        canvas.acceptFileBrowserListing(requestId, listing);
    }
}