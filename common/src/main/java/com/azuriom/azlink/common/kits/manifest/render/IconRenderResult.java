package com.azuriom.azlink.common.kits.manifest.render;

import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    private final String displayName;
    private final List<String> tooltip;

    private IconRenderResult(String renderKey, Status status, byte[] pngBytes,
                             String clientSha256, String errorCode) {
        this(renderKey, status, pngBytes, clientSha256, errorCode, null);
    }

    private IconRenderResult(String renderKey, Status status, byte[] pngBytes,
                             String clientSha256, String errorCode, String displayName) {
        this(renderKey, status, pngBytes, clientSha256, errorCode, displayName, Collections.<String>emptyList());
    }

    private IconRenderResult(String renderKey, Status status, byte[] pngBytes,
                             String clientSha256, String errorCode, String displayName, List<String> tooltip) {
        this.displayName = normalizeDisplayName(displayName);
        this.tooltip = normalizeTooltip(tooltip);
        this.renderKey = Objects.requireNonNull(renderKey, "renderKey");
        this.status = Objects.requireNonNull(status, "status");
        this.pngBytes = pngBytes == null ? null : Arrays.copyOf(pngBytes, pngBytes.length);
        this.clientSha256 = clientSha256;
        this.errorCode = errorCode;
    }

    public static IconRenderResult rendered(String renderKey, byte[] pngBytes, String clientSha256) {
        return new IconRenderResult(renderKey, Status.RENDERED, pngBytes, clientSha256, null);
    }

    public static IconRenderResult rendered(String renderKey, byte[] pngBytes, String clientSha256,
                                             String displayName) {
        return new IconRenderResult(renderKey, Status.RENDERED, pngBytes, clientSha256, null, displayName);
    }

    public static IconRenderResult rendered(String renderKey, byte[] pngBytes, String clientSha256,
                                             String displayName, List<String> tooltip) {
        return new IconRenderResult(renderKey, Status.RENDERED, pngBytes, clientSha256, null, displayName, tooltip);
    }

    /** Cosmetic text only: item identity, count and NBT always come from the server. */
    public String getDisplayName() {
        return this.displayName;
    }

    public List<String> getTooltip() {
        return this.tooltip;
    }

    private static List<String> normalizeTooltip(List<String> lines) {
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tooltip = new ArrayList<String>();
        int bytes = Integer.BYTES;
        for (String line : lines) {
            String cleaned = line.replaceAll("[\\p{Cntrl}\\p{Cf}]", "");
            int end = Math.min(cleaned.length(), KitManifestLimits.MAX_TOOLTIP_LINE_LENGTH);
            if (end > 0 && Character.isHighSurrogate(cleaned.charAt(end - 1))) {
                end--;
            }
            String bounded = cleaned.substring(0, end);
            bytes += Integer.BYTES + bounded.getBytes(StandardCharsets.UTF_8).length;
            if (tooltip.size() == KitManifestLimits.MAX_TOOLTIP_LINES || bytes > ItemIconRenderProtocol.MAX_TOOLTIP_WIRE_BYTES) {
                break;
            }
            tooltip.add(bounded);
        }
        return Collections.unmodifiableList(tooltip);
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) return null;
        String name = value.replaceAll("\u00a7[0-9A-FK-ORa-fk-or]", "")
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        int end = Math.min(name.length(), KitManifestLimits.MAX_DISPLAY_NAME);
        if (end > 0 && Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return end == 0 ? null : name.substring(0, end);
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
