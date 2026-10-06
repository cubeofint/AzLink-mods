package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.executor.model.OperationType;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.UUID;

final class OperationFixtures {

    static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private OperationFixtures() {
    }

    static ExecutorConfig configWithFly() {
        ExecutorConfig config = ExecutorConfig.defaults();
        config.setSupportedCapabilities(Arrays.asList("fly"));
        return config;
    }

    static ShopOperation privilegeReconcile(String operationId, String claimToken, String hash,
                                            long entitlementVersion) {
        return new ShopOperation(operationId, claimToken, OperationType.PRIVILEGE_RECONCILE.toWire(),
                hash, basePrivilegePayload(entitlementVersion, true));
    }

    static ShopOperation privilegeRevoke(String operationId, String claimToken, String hash,
                                         long entitlementVersion) {
        return new ShopOperation(operationId, claimToken, OperationType.PRIVILEGE_REVOKE.toWire(),
                hash, basePrivilegePayload(entitlementVersion, false));
    }

    static ShopOperation kitRedeem(String operationId, String claimToken, String hash, long redemptionId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", 1);
        payload.addProperty("type", OperationType.KIT_REDEEM.toWire());
        payload.addProperty("redemption_id", redemptionId);
        payload.addProperty("purchase_id", 1);
        payload.addProperty("player_uuid", PLAYER.toString());
        payload.addProperty("kit_key", "starter_kit");
        return new ShopOperation(operationId, claimToken, OperationType.KIT_REDEEM.toWire(), hash, payload);
    }

    static JsonObject basePrivilegePayload(long entitlementVersion, boolean withCapabilities) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", 1);
        payload.addProperty("type", withCapabilities
                ? OperationType.PRIVILEGE_RECONCILE.toWire()
                : OperationType.PRIVILEGE_REVOKE.toWire());
        JsonObject user = new JsonObject();
        user.addProperty("uuid", PLAYER.toString());
        payload.add("user", user);
        JsonObject entitlement = new JsonObject();
        entitlement.addProperty("id", 1);
        entitlement.addProperty("version", entitlementVersion);
        entitlement.addProperty("purchase_id", 10);
        entitlement.addProperty("key", "vip");
        payload.add("entitlement", entitlement);
        if (withCapabilities) {
            JsonObject caps = new JsonObject();
            caps.addProperty("fly", true);
            payload.add("capabilities", caps);
        }
        return payload;
    }
}
