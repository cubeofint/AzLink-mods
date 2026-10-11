package com.azuriom.azlink.common.prefixes;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixBridgePayloadTest {

    @Test
    void reportPayloadMatchesContract() {
        JsonObject availableItem = new JsonObject();
        availableItem.addProperty("group", "vip");
        availableItem.addProperty("prefix_raw", "<gold>[VIP]</gold>");
        availableItem.addProperty("category", "donate");

        JsonArray available = new JsonArray();
        available.add(availableItem);

        JsonObject player = new JsonObject();
        player.addProperty("uuid", "f3fc162d-d344-32fa-8c9a-0b987b0791cf");
        player.addProperty("name", "Nick");
        player.add("available", available);
        player.addProperty("selected", "vip");
        player.addProperty("locked_staff", false);

        JsonArray players = new JsonArray();
        players.add(player);
        JsonObject body = new JsonObject();
        body.add("players", players);

        String json = AzLinkPlugin.getGson().toJson(body);
        JsonObject parsed = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        JsonObject roundTrip = parsed.getAsJsonArray("players").get(0).getAsJsonObject();

        assertEquals("f3fc162d-d344-32fa-8c9a-0b987b0791cf", roundTrip.get("uuid").getAsString());
        assertEquals("vip", roundTrip.get("selected").getAsString());
        assertEquals("donate", roundTrip.getAsJsonArray("available").get(0).getAsJsonObject()
                .get("category").getAsString());
        assertEquals(json, AzLinkPlugin.getGson().toJson(parsed));
    }

    @Test
    void ackPayloadUsesAppliedRejectedAndNullableSelected() {
        JsonObject params = new JsonObject();
        params.addProperty("op_id", "9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b");
        params.addProperty("status", "applied");
        params.add("reason", JsonNull.INSTANCE);
        params.addProperty("selected", "vip");

        JsonObject parsed = AzLinkPlugin.getGson().fromJson(params.toString(), JsonObject.class);
        assertEquals("applied", parsed.get("status").getAsString());
        assertEquals("vip", parsed.get("selected").getAsString());

        JsonObject auto = new JsonObject();
        auto.addProperty("op_id", "9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b");
        auto.addProperty("status", "rejected");
        auto.addProperty("reason", "unknown_group");
        auto.add("selected", JsonNull.INSTANCE);

        String raw = auto.toString();
        assertTrue(raw.contains("\"selected\":null") || raw.contains("\"selected\": null"));
        JsonObject autoParsed = AzLinkPlugin.getGson().fromJson(raw, JsonObject.class);
        assertEquals("rejected", autoParsed.get("status").getAsString());
        assertTrue(autoParsed.get("selected").isJsonNull());
    }

    @Test
    void operationsPayloadIsPrefixSelect() {
        JsonObject op = new JsonObject();
        op.addProperty("op_id", "9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b");
        op.addProperty("type", "prefix_select");
        op.addProperty("uuid", "f3fc162d-d344-32fa-8c9a-0b987b0791cf");
        op.add("group", JsonNull.INSTANCE);

        JsonArray operations = new JsonArray();
        operations.add(op);
        JsonObject body = new JsonObject();
        body.add("operations", operations);

        JsonObject parsed = AzLinkPlugin.getGson().fromJson(body.toString(), JsonObject.class);
        JsonObject row = parsed.getAsJsonArray("operations").get(0).getAsJsonObject();
        assertEquals("prefix_select", row.get("type").getAsString());
        assertTrue(row.get("group").isJsonNull());
    }
}
