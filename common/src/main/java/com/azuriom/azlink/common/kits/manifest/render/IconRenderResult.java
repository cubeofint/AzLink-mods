package com.azuriom.azlink.common.kits.manifest.render;

import java.util.Arrays;
import java.util.Objects;

public final class IconRenderResult {

    public enum Status {
        RENDERED,
        UNSUPPORTED,
        FAILED
    }

    private final String renderKey;
    private final Status status;
    private final byte[] pngBytes;
    private final String clientSha256;
    private final String errorCode;

    private IconRenderResult(String renderKey, Status status, byte[] pngBytes,
                             String clientSha256, String errorCode) {
        this.renderKey = Objects.requireNonNull(renderKey, "renderKey");
        this.status = Objects.requireNonNull(status, "status");
        this.pngBytes = pngBytes == null ? null : Arrays.copyOf(pngBytes, pngBytes.length);
        this.clientSha256 = clientSha256;
        this.errorCode = errorCode;
    }

    public static IconRenderResult rendered(String renderKey, byte[] pngBytes, String clientSha256) {
        return new IconRenderResult(renderKey, Status.RENDERED, pngBytes, clientSha256, null);
    }

    public static IconRenderResult unsupported(String renderKey, String errorCode) {
        return new IconRenderResult(renderKey, Status.UNSUPPORTED, null, null, errorCode);
    }

    public static IconRenderResult failed(String renderKey, String errorCode) {
        return new IconRenderResult(renderKey, Status.FAILED, null, null, errorCode);
    }

    public String getRenderKey() {
        return this.renderKey;
    }

    public Status getStatus() {
        return this.status;
    }

    public byte[] getPngBytes() {
        return this.pngBytes == null ? null : Arrays.copyOf(this.pngBytes, this.pngBytes.length);
    }

    public String getClientSha256() {
        return this.clientSha256;
    }

    public String getErrorCode() {
        return this.errorCode;
    }
}
