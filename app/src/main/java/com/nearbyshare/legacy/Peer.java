package com.nearbyshare.legacy;

public final class Peer {
    public final String name;
    public final String address;
    public final int port;

    public Peer(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    public String endpoint() {
        return address + ":" + port;
    }
}