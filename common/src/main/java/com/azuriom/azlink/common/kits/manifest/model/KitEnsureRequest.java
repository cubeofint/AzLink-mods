package com.azuriom.azlink.common.kits.manifest.model;

import com.google.gson.annotations.SerializedName;

/**
 * Wire body for {@code POST /api/shop/azlink/v1/kits}.
 */
public final class KitEnsureRequest {

    @SerializedName("delivery_key")
    private final String deliveryKey;

    private final String name;

    @SerializedName("cooldown_seconds")
    private final Integer cooldownSeconds;

    public KitEnsureRequest(String deliveryKey, String name, Integer cooldownSeconds) {
        this.deliveryKey = deliveryKey;
        this.name = name;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String getDeliveryKey() {
        return this.deliveryKey;
    }

    public String getName() {
        return this.name;
    }

    public Integer getCooldownSeconds() {
        return this.cooldownSeconds;
    }
}
