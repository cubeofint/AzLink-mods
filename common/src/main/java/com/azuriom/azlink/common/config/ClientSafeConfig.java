package com.azuriom.azlink.common.config;

/**
 * Client-side config. Intentionally has NO site URL / Azuriom-Link-Token / credentials.
 * Client AzLink is a rendering worker only.
 */
public final class ClientSafeConfig {

    private boolean itemIconRenderingEnabled = true;
    private int preferredIconSize = 64;

    public boolean isItemIconRenderingEnabled() {
        return this.itemIconRenderingEnabled;
    }

    public void setItemIconRenderingEnabled(boolean itemIconRenderingEnabled) {
        this.itemIconRenderingEnabled = itemIconRenderingEnabled;
    }

    public int getPreferredIconSize() {
        return this.preferredIconSize;
    }

    public void setPreferredIconSize(int preferredIconSize) {
        this.preferredIconSize = preferredIconSize;
    }

    @Override
    public String toString() {
        return "ClientSafeConfig{itemIconRenderingEnabled=" + this.itemIconRenderingEnabled
                + ", preferredIconSize=" + this.preferredIconSize + '}';
    }
}
