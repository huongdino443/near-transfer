package com.nearbyshare.j2me;

final class PeerDevice {
    final String name;
    final String address;
    final int port;

    PeerDevice(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    String endpoint() {
        return address + ":" + port;
    }

    boolean matches(String otherAddress, int otherPort) {
        return port == otherPort && address.equals(otherAddress);
    }
}
