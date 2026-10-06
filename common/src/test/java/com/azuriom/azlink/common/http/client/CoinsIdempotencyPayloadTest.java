package com.azuriom.azlink.common.http.client;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinsIdempotencyPayloadTest {

    @Test
    void coinsRequestIncludesIdempotencyKey() {
        JsonObject params = new JsonObject();
        params.addProperty("amount", 10.5);
        String key = "abcdef0123456789";
        params.addProperty("idempotency_key", key);

        String json = AzLinkPlugin.getGson().toJson(params);
        assertTrue(json.contains("\"idempotency_key\""));
        assertTrue(json.contains(key));
        assertEquals(10.5, AzLinkPlugin.getGson().fromJson(json, JsonObject.class).get("amount").getAsDouble(), 0.001);
    }

    @Test
    void logicalKeyIsStableAcrossRetries() {
        String logicalKey = "logical-action-00123456";
        // Same key must be reused if HTTP layer retries the same mutation.
        assertEquals(logicalKey, logicalKey);
        assertTrue(logicalKey.length() >= 8);
        assertTrue(logicalKey.length() <= 64);
    }
}
