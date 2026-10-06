package com.nearbyshare.j2me;

final class IncomingOffer {
    static final class Item {
        final String name;
        final long size;

        Item(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    final String transferId;
    final String senderName;
    final String senderAddress;
    final boolean text;
    final Item[] items;
    final long totalBytes;

    private boolean decided;
    private boolean accepted;

    IncomingOffer(String transferId, String senderName, String senderAddress,
                  boolean text, Item[] items, long totalBytes) {
        this.transferId = transferId;
        this.senderName = senderName;
        this.senderAddress = senderAddress;
        this.text = text;
        this.items = items;
        this.totalBytes = totalBytes;
    }

    synchronized void decide(boolean accept) {
        if (decided) {
            return;
        }
        accepted = accept;
        decided = true;
        notifyAll();
    }

    synchronized boolean awaitDecision(long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!decided) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            wait(remaining);
        }
        return decided && accepted;
    }
}
