package com.azuriom.azlink.common.coins;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinOperationsPayloadTest {

    @Test
    void ackPayloadOmitsBalanceAfterWhenNull() {
        JsonObject params = new JsonObject();
        params.addProperty("status", "applied");
        params.addProperty("error", "insufficient_server_balance");
        String json = AzLinkPlugin.getGson().toJson(params);
        JsonObject parsed = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        assertFalse(parsed.has("balance_after"));
        assertEquals("applied", parsed.get("status").getAsString());
    }

    @Test
    void ackPayloadIncludesBalanceAfterWhenPresent() {
        JsonObject params = new JsonObject();
        params.addProperty("status", "applied");
        params.addProperty("balance_after", 950L);
        String json = AzLinkPlugin.getGson().toJson(params);
        JsonObject parsed = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        assertEquals(950L, parsed.get("balance_after").getAsLong());
    }

    @Test
    void postMovementsJsonKeepsDeltasAndSiteOpId() {
        JsonObject delta = new JsonObject();
        delta.addProperty("uuid", "f3fc162d-d344-32fa-8c9a-0b987b0791cf");
        delta.addProperty("delta", -40);
        delta.addProperty("balance_after", 910);

        JsonArray deltas = new JsonArray();
        deltas.add(delta);

        JsonObject movement = new JsonObject();
        movement.addProperty("id", 812);
        movement.addProperty("timestamp", 1791400000000L);
        movement.addProperty("type", "trader_buy");
        movement.addProperty("amount", 40);
        movement.addProperty("from_id", "f3fc162d-d344-32fa-8c9a-0b987b0791cf");
        movement.addProperty("from_name", "Nick");
        movement.addProperty("to_name", "trader");
        movement.addProperty("note", "offer=diamond");
        movement.add("deltas", deltas);
        movement.addProperty("site_op_id", "9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b");

        JsonArray movements = new JsonArray();
        movements.add(movement);
        JsonObject body = new JsonObject();
        body.add("movements", movements);

        String json = AzLinkPlugin.getGson().toJson(body);
        JsonObject parsed = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        JsonObject roundTrip = parsed.getAsJsonArray("movements").get(0).getAsJsonObject();

        assertTrue(roundTrip.has("deltas"));
        assertEquals("9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b", roundTrip.get("site_op_id").getAsString());
        assertEquals(-40, roundTrip.getAsJsonArray("deltas").get(0).getAsJsonObject().get("delta").getAsInt());
        assertEquals(910, roundTrip.getAsJsonArray("deltas").get(0).getAsJsonObject().get("balance_after").getAsInt());

        JsonObject fromString = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        assertEquals(json, AzLinkPlugin.getGson().toJson(fromString));
    }
}
