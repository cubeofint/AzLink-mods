package com.azuriom.azlink.common.kits.manifest.render;

import java.util.Objects;
import java.util.UUID;

/**
 * Capabilities advertised by client AzLink to the dedicated server. No website credentials.
 */
public final class ClientCapabilities {

    private final int protocolVersion;
    private final boolean supportsItemIconRendering;
    private final String rendererVersion;
    private final int supportedIconSize;
    private final String supportedImageFormat;

    public ClientCapabilities(int protocolVersion, boolean supportsItemIconRendering,
                              String rendererVersion, int supportedIconSize, String supportedImageFormat) {
        this.protocolVersion = protocolVersion;
        this.supportsItemIconRendering = supportsItemIconRendering;
        this.rendererVersion = rendererVersion == null ? "" : rendererVersion;
        this.supportedIconSize = supportedIconSize;
        this.supportedImageFormat = supportedImageFormat == null ? "" : supportedImageFormat;
    }

    public static ClientCapabilities v1(String rendererVersion) {
        return new ClientCapabilities(
                ItemIconRenderProtocol.VERSION,
                true,
                rendererVersion,
                ItemIconRenderProtocol.ICON_SIZE,
                ItemIconRenderProtocol.IMAGE_FORMAT
        );
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public boolean supportsItemIconRendering() {
        return this.supportsItemIconRendering;
    }

    public String getRendererVersion() {
        return this.rendererVersion;
    }

    public int getSupportedIconSize() {
        return this.supportedIconSize;
    }

    public String getSupportedImageFormat() {
        return this.supportedImageFormat;
    }

    public boolean isCompatibleWithServer() {
        return this.protocolVersion == ItemIconRenderProtocol.VERSION
                && this.supportsItemIconRendering
                && this.supportedIconSize == ItemIconRenderProtocol.ICON_SIZE
                && ItemIconRenderProtocol.IMAGE_FORMAT.equalsIgnoreCase(this.supportedImageFormat);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientCapabilities)) {
            return false;
        }
        ClientCapabilities that = (ClientCapabilities) o;
        return this.protocolVersion == that.protocolVersion
                && this.supportsItemIconRendering == that.supportsItemIconRendering
                && this.supportedIconSize == that.supportedIconSize
                && Objects.equals(this.rendererVersion, that.rendererVersion)
                && Objects.equals(this.supportedImageFormat, that.supportedImageFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.protocolVersion, this.supportsItemIconRendering,
                this.rendererVersion, this.supportedIconSize, this.supportedImageFormat);
    }
}
