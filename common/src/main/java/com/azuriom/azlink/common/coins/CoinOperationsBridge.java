package com.azuriom.azlink.common.coins;

import com.azuriom.azlink.common.AzLinkPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * Stable string-based facade for other mods (e.g. cointcore) to pull the site coin-operation queue
 * without a compile dependency on AzLink internals. All methods return JSON strings.
 */
public final class CoinOperationsBridge {
    private static volatile AzLinkPlugin plugin;

    private CoinOperationsBridge() {
    }

    public static void install(AzLinkPlugin instance) {
        plugin = instance;
    }

    /** {@code true} when AzLink is linked to the site and can serve requests. */
    public static boolean isAvailable() {
        AzLinkPlugin current = plugin;
        return current != null && current.getConfig() != null && current.getConfig().isValid();
    }

    /** JSON {@code {"operations":[{id,user_id,game_id,name,amount,direction}...]}}. */
    public static CompletableFuture<String> fetchPending(int limit) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient().fetchCoinOperations(limit).thenApply(Object::toString);
    }

    /** Ack one operation as {@code applied} or {@code failed}; safe to repeat. */
    public static CompletableFuture<String> ack(String operationId, String status, String error) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient().ackCoinOperation(operationId, status, error).thenApply(Object::toString);
    }
}
