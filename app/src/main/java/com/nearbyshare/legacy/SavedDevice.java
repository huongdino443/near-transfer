package com.nearbyshare.legacy;

final class SavedDevice {
    final String name;
    final String address;
    final int port;

    SavedDevice(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    String key() {
        return address + ":" + port;
    }

    Peer toPeer() {
        return new Peer(name, address, port);
    }
}