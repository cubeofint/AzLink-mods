package com.azuriom.azlink.common.kits.manifest.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response of manifest upload (pending_review / idempotent_replay / errors).
 */
public final class KitManifestUploadResponse {

    private String outcome;
    private String status;

    @SerializedName("manifest_id")
    private Long manifestId;

    @SerializedName("kit_id")
    private Long kitId;

    @SerializedName("delivery_key")
    private String deliveryKey;

    @SerializedName("manifest_version")
    private Integer manifestVersion;

    @SerializedName("manifest_hash")
    private String manifestHash;

    private String error;

    public String getOutcome() {
        return this.outcome;
    }

    public String getStatus() {
        return this.status;
    }

    public Long getManifestId() {
        return this.manifestId;
    }

    public Long getKitId() {
        return this.kitId;
    }

    public String getDeliveryKey() {
        return this.deliveryKey;
    }

    public Integer getManifestVersion() {
        return this.manifestVersion;
    }

    public String getManifestHash() {
        return this.manifestHash;
    }

    public String getError() {
        return this.error;
    }

    public boolean isPendingReview() {
        return "pending_review".equals(this.outcome);
    }

    public boolean isApplied() {
        return "applied".equals(this.outcome);
    }

    public boolean isIdempotentReplay() {
        return "idempotent_replay".equals(this.outcome);
    }
}
