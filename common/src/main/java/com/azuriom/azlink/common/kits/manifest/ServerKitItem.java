package com.azuriom.azlink.common.kits.manifest;

import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;

import java.util.Arrays;
import java.util.Objects;

/**
 * Dedicated-server authoritative item: metadata snapshot + opaque ItemStack bytes for client render.
 * Client PNG never becomes authoritative metadata.
 */
public final class ServerKitItem {

    private final ItemStackSnapshot snapshot;
    private final byte[] itemStackBytes;

    public ServerKitItem(ItemStackSnapshot snapshot, byte[] itemStackBytes) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.itemStackBytes = itemStackBytes == null
                ? new byte[0]
                : Arrays.copyOf(itemStackBytes, itemStackBytes.length);
    }

    public ItemStackSnapshot getSnapshot() {
        return this.snapshot;
    }

    public byte[] getItemStackBytes() {
        return Arrays.copyOf(this.itemStackBytes, this.itemStackBytes.length);
    }
}
