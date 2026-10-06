package com.azuriom.azlink.common.kits.manifest.render;

import java.util.Arrays;
import java.util.Objects;

/**
 * One item in a render request. ItemStack bytes are opaque Minecraft network encoding.
 * MUST NOT contain site URL/token.
 */
public final class RenderItemPayload {

    private final String renderKey;
    private final int slot;
    private final byte[] itemStackBytes;

    public RenderItemPayload(String renderKey, int slot, byte[] itemStackBytes) {
        this.renderKey = Objects.requireNonNull(renderKey, "renderKey");
        this.slot = slot;
        this.itemStackBytes = itemStackBytes == null ? new byte[0] : Arrays.copyOf(itemStackBytes, itemStackBytes.length);
    }

    public String getRenderKey() {
        return this.renderKey;
    }

    public int getSlot() {
        return this.slot;
    }

    public byte[] getItemStackBytes() {
        return Arrays.copyOf(this.itemStackBytes, this.itemStackBytes.length);
    }

    public int getSerializedSize() {
        return this.itemStackBytes.length;
    }
}
