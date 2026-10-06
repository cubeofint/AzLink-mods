package com.azuriom.azlink.common.kits.manifest.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin client → dedicated server render response. No website credentials / upload targets.
 */
public final class ItemIconRenderResponse {

    private final UUID requestId;
    private final int protocolVersion;
    private final String rendererVersion;
    private final List<IconRenderResult> icons;

    public ItemIconRenderResponse(UUID requestId, int protocolVersion, String rendererVersion,
                                  List<IconRenderResult> icons) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.protocolVersion = protocolVersion;
        this.rendererVersion = rendererVersion == null ? "" : rendererVersion;
        this.icons = Collections.unmodifiableList(new ArrayList<IconRenderResult>(
                icons == null ? Collections.<IconRenderResult>emptyList() : icons));
    }

    public UUID getRequestId() {
        return this.requestId;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public String getRendererVersion() {
        return this.rendererVersion;
    }

    public List<IconRenderResult> getIcons() {
        return this.icons;
    }
}
