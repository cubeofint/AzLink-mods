package com.azuriom.azlink.common.kits.manifest.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Dedicated server → admin client render request. No website credentials.
 */
public final class ItemIconRenderRequest {

    private final UUID requestId;
    private final int protocolVersion;
    private final int iconSize;
    private final List<RenderItemPayload> items;

    public ItemIconRenderRequest(UUID requestId, int protocolVersion, int iconSize,
                                 List<RenderItemPayload> items) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.protocolVersion = protocolVersion;
        this.iconSize = iconSize;
        this.items = Collections.unmodifiableList(new ArrayList<RenderItemPayload>(
                items == null ? Collections.<RenderItemPayload>emptyList() : items));
    }

    public UUID getRequestId() {
        return this.requestId;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public int getIconSize() {
        return this.iconSize;
    }

    public List<RenderItemPayload> getItems() {
        return this.items;
    }
}
