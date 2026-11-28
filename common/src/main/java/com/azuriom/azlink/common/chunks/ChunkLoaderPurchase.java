package com.azuriom.azlink.common.chunks;

import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An absolute expiry and durable receipt, shared by both game backends. */
public final class ChunkLoaderPurchase {
    public final UUID player;
    public final String name;
    public final int quantity;
    public final long expiresAt;
    public final String receipt;

    private ChunkLoaderPurchase(UUID player, String name, int quantity, long expiresAt, String receipt) {
        this.player = player; this.name = name; this.quantity = quantity;
        this.expiresAt = expiresAt; this.receipt = receipt;
    }

    public static ChunkLoaderPurchase parse(ShopOperation operation) {
        try {
            JsonObject payload = operation.getPayload();
            UUID player = UUID.fromString(string(payload, "player_uuid"));
            String name = string(payload, "player_name");
            String receipt = string(payload, "receipt");
            if (!name.matches("[A-Za-z0-9_.]{1,16}")
                    || !UUID.fromString(receipt).toString().equals(receipt)
                    || !receipt.equals(operation.getOperationId())
                    || !player.equals(operation.getPlayerUuid())
                    || !("chunk-loaders." + receipt).equals(operation.getSemanticKey()))
                throw new IllegalArgumentException();
            JsonElement count = payload.get("quantity");
            if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            int quantity = new BigDecimal(count.getAsString()).intValueExact();
            long expiry = Instant.parse(string(payload, "expires_at")).getEpochSecond();
            if (quantity < 1 || quantity > 999 || expiry <= 0) throw new IllegalArgumentException();
            // Expired replays are resolved by the durable game receipt before applying a new allowance.
            return new ChunkLoaderPurchase(player, name, quantity, expiry, receipt);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid chunk-loader purchase payload", invalid);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException();
        return value.getAsString();
    }
}
