package com.azuriom.azlink.common.kits.manifest.model;

import com.google.gson.annotations.SerializedName;

/**
 * Icon reference in a manifest item. Wire: {@code type=none|content_hash}.
 */
public final class ItemIconRef {

    private final String type;
    private final String reference;

    public ItemIconRef(String type, String reference) {
        this.type = type;
        this.reference = reference;
    }

    public static ItemIconRef none() {
        return new ItemIconRef("none", null);
    }

    public static ItemIconRef contentHash(String sha256Hex) {
        return new ItemIconRef("content_hash", sha256Hex == null ? null : sha256Hex.toLowerCase());
    }

    public String getType() {
        return this.type;
    }

    public String getReference() {
        return this.reference;
    }
}
