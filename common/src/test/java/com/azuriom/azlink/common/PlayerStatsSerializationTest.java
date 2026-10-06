package com.azuriom.azlink.common;

import com.azuriom.azlink.common.data.PlayerData;
import com.azuriom.azlink.common.data.PlayerStats;
import com.azuriom.azlink.common.gson.InstantAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStatsSerializationTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    @Test
    void serializesStatsWithSnakeCaseKeys() {
        PlayerStats stats = new PlayerStats(3, 72000, 1200, 50, 1, 15, 1000, 400, 500000);
        PlayerData data = new PlayerData("Steve", UUID.fromString("00000000-0000-0000-0000-000000000001"), stats);

        JsonObject json = GSON.toJsonTree(data).getAsJsonObject();
        JsonObject statsJson = json.getAsJsonObject("stats");

        assertEquals("Steve", json.get("name").getAsString());
        assertTrue(json.has("uuid"));
        assertTrue(json.get("online").getAsBoolean());
        assertEquals(3, statsJson.get("deaths").getAsLong());
        assertEquals(72000, statsJson.get("play_time").getAsLong());
        assertEquals(1200, statsJson.get("time_since_death").getAsLong());
        assertEquals(50, statsJson.get("mob_kills").getAsLong());
        assertEquals(1, statsJson.get("player_kills").getAsLong());
        assertEquals(15, statsJson.get("xp_level").getAsInt());
        assertEquals(1000, statsJson.get("blocks_mined").getAsLong());
        assertEquals(400, statsJson.get("blocks_placed").getAsLong());
        assertEquals(500000, statsJson.get("walk_cm").getAsLong());
    }
}
