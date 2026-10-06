package com.azuriom.azlink.common.executor.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * One claimed operation from poll. Wire field for type is {@code type} (site canonical).
 * Payload shape matches {@code MinecraftOperationDispatcher} on the website.
 */
public class ShopOperation {

    @SerializedName("operation_id")
    private String operationId;

    @SerializedName("claim_token")
    private String claimToken;

    /** Site wire field name. */
    @SerializedName("type")
    private String type;

    @SerializedName("payload_hash")
    private String payloadHash;

    private JsonObject payload;

    @SerializedName("attempt_no")
    private Integer attemptNo;

    @SerializedName("lease_expires_at")
    private String leaseExpiresAt;

    public ShopOperation() {
    }

    public ShopOperation(String operationId, String claimToken, String type,
                         String payloadHash, JsonObject payload) {
        this.operationId = operationId;
        this.claimToken = claimToken;
        this.type = type;
        this.payloadHash = payloadHash;
        this.payload = payload;
    }

    public String getOperationId() {
        return this.operationId;
    }

    public String getClaimToken() {
        return this.claimToken;
    }

    public String getOperationTypeRaw() {
        return this.type;
    }

    public OperationType getOperationType() {
        return OperationType.fromWire(this.type);
    }

    public String getPayloadHash() {
        return this.payloadHash;
    }

    public JsonObject getPayload() {
        return this.payload;
    }

    public Integer getAttemptNo() {
        return this.attemptNo;
    }

    public String getLeaseExpiresAt() {
        return this.leaseExpiresAt;
    }

    public UUID getPlayerUuid() {
        if (this.payload == null) {
            return null;
        }
        if (this.payload.has("user") && this.payload.get("user").isJsonObject()) {
            JsonObject user = this.payload.getAsJsonObject("user");
            if (user.has("uuid") && !user.get("uuid").isJsonNull()) {
                try {
                    return UUID.fromString(user.get("uuid").getAsString());
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return parseUuid(payloadString("player_uuid"));
    }

    public String getEntitlementExpiresAt() {
        JsonObject entitlement = entitlementObject();
        if (entitlement == null) {
            return null;
        }
        return jsonString(entitlement, "expires_at");
    }

    public String getEntitlementId() {
        JsonObject entitlement = entitlementObject();
        if (entitlement != null && entitlement.has("id") && !entitlement.get("id").isJsonNull()) {
            JsonElement id = entitlement.get("id");
            try {
                return id.isJsonPrimitive() ? id.getAsString() : null;
            } catch (RuntimeException e) {
                return null;
            }
        }
        return payloadString("entitlement_id");
    }

    public Long getEntitlementVersion() {
        JsonObject entitlement = entitlementObject();
        if (entitlement != null && entitlement.has("version") && !entitlement.get("version").isJsonNull()) {
            try {
                return entitlement.get("version").getAsLong();
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (this.payload == null || !this.payload.has("entitlement_version")
                || this.payload.get("entitlement_version").isJsonNull()) {
            return null;
        }
        try {
            return this.payload.get("entitlement_version").getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String getSemanticKey() {
        JsonObject entitlement = entitlementObject();
        if (entitlement != null) {
            String key = jsonString(entitlement, "key");
            if (key != null) {
                return key;
            }
        }
        String kitKey = payloadString("kit_key");
        if (kitKey != null) {
            return kitKey;
        }
        return payloadString("semantic_key");
    }

    public String getRedemptionId() {
        if (this.payload == null || !this.payload.has("redemption_id")
                || this.payload.get("redemption_id").isJsonNull()) {
            return null;
        }
        try {
            JsonElement el = this.payload.get("redemption_id");
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                return Long.toString(el.getAsLong());
            }
            return el.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Site payload uses {@code capabilities} (capability key → value).
     */
    public JsonObject getCapabilities() {
        if (this.payload == null || !this.payload.has("capabilities")
                || !this.payload.get("capabilities").isJsonObject()) {
            return null;
        }
        return this.payload.getAsJsonObject("capabilities");
    }

    private JsonObject entitlementObject() {
        if (this.payload == null || !this.payload.has("entitlement")
                || !this.payload.get("entitlement").isJsonObject()) {
            return null;
        }
        return this.payload.getAsJsonObject("entitlement");
    }

    private String payloadString(String key) {
        if (this.payload == null) {
            return null;
        }
        return jsonString(this.payload, key);
    }

    private static String jsonString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
