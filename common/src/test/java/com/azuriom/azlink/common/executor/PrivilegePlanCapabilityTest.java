package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.executor.model.OperationType;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.privileges.PrivilegePlan;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivilegePlanCapabilityTest {

    @Test
    void numericCapabilitiesBecomeMetaNodes() {
        JsonObject payload = OperationFixtures.basePrivilegePayload(2, true);
        JsonObject caps = payload.getAsJsonObject("capabilities");
        caps.addProperty("cointcore.bonus_claim_chunks", 25);
        caps.addProperty("cointcore.bonus_forceload_chunks", "4.0000");
        caps.addProperty("claim.flag.pvp", "ftbchunks.flag.pvp");
        payload.getAsJsonObject("entitlement").addProperty("expires_at", "2099-01-01T00:00:00+00:00");
        ShopOperation op = new ShopOperation("op-1", "claim-1", OperationType.PRIVILEGE_RECONCILE.toWire(), "h", payload);

        PrivilegePlan plan = PrivilegePlan.reconcile(op);

        assertNull(plan.getError());
        assertTrue(plan.getPermissions().contains(PrivilegePlan.CAPABILITY_PREFIX + "fly"));
        assertTrue(plan.getPermissions().contains("meta:cointcore.bonus_claim_chunks=25"));
        assertTrue(plan.getPermissions().contains("meta:cointcore.bonus_forceload_chunks=4"));
        assertTrue(plan.getPermissions().contains("ftbchunks.flag.pvp"));
    }

    @Test
    void expiredEntitlementIsClearOnly() {
        JsonObject payload = OperationFixtures.basePrivilegePayload(2, true);
        payload.getAsJsonObject("entitlement").addProperty("expires_at", "2000-01-01T00:00:00+00:00");
        ShopOperation op = new ShopOperation("op-exp", "claim-1", OperationType.PRIVILEGE_RECONCILE.toWire(), "h", payload);

        PrivilegePlan plan = PrivilegePlan.reconcile(op);

        assertNull(plan.getError());
        assertTrue(plan.isExpired());
        assertTrue(plan.isClearOnly());
        assertTrue(plan.getPermissions().isEmpty());
    }

    @Test
    void futureExpiryKeptOnPlan() {
        JsonObject payload = OperationFixtures.basePrivilegePayload(2, true);
        payload.getAsJsonObject("entitlement").addProperty("expires_at", "2099-01-01T00:00:00+00:00");
        ShopOperation op = new ShopOperation("op-live", "claim-1", OperationType.PRIVILEGE_RECONCILE.toWire(), "h", payload);

        PrivilegePlan plan = PrivilegePlan.reconcile(op);

        assertNull(plan.getError());
        assertFalse(plan.isExpired());
        assertFalse(plan.isClearOnly());
        assertEquals("2099-01-01T00:00:00Z", plan.getExpiresAt().toString());
        assertEquals("vip", plan.getGroupName());
    }
}
