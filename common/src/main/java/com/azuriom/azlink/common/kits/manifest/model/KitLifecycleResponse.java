package com.azuriom.azlink.common.kits.manifest.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response of kit ensure / retire lifecycle calls.
 */
public final class KitLifecycleResponse {

    @SerializedName("kit_id")
    private Long kitId;

    @SerializedName("delivery_key")
    private String deliveryKey;

    private Boolean created;

    private Boolean reenabled;

    private String outcome;

    private String error;

    public Long getKitId() {
        return this.kitId;
    }

    public String getDeliveryKey() {
        return this.deliveryKey;
    }

    public Boolean getCreated() {
        return this.created;
    }

    public Boolean getReenabled() {
        return this.reenabled;
    }

    public String getOutcome() {
        return this.outcome;
    }

    public String getError() {
        return this.error;
    }
}
