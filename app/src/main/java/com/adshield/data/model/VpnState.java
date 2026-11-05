package com.adshield.data.model;

public enum VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING;

    public boolean isProtected() {
        return this == CONNECTED;
    }
}
