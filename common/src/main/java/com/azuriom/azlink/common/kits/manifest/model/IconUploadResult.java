package com.azuriom.azlink.common.kits.manifest.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response of {@code POST .../kits/item-icons}.
 */
public final class IconUploadResult {

    private String hash;
    private String url;
    private Boolean created;

    public IconUploadResult() {
    }

    public IconUploadResult(String hash, String url, boolean created) {
        this.hash = hash;
        this.url = url;
        this.created = created;
    }

    public String getHash() {
        return this.hash;
    }

    public String getUrl() {
        return this.url;
    }

    public boolean isCreated() {
        return this.created != null && this.created;
    }
}
