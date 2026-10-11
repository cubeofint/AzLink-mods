package com.azuriom.azlink.common.prefixes;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * Stable string-based facade for other mods (e.g. cointcore) to report chat prefixes
 * and pull the site prefix-select queue without a compile dependency on AzLink internals.
 * All methods return JSON strings. Mirrors {@link com.azuriom.azlink.common.coins.CoinOperationsBridge}.
 *
 * <p>Contract (keep exact — cointcore implements the game side in parallel):
 * <ul>
 *   <li>{@code POST /api/azlink/prefixes/report}
 *       body {@code {players:[{uuid,name,available:[{group,prefix_raw,category}],selected,locked_staff}]}}</li>
 *   <li>{@code GET  /api/azlink/prefixes/operations} →
 *       {@code {operations:[{op_id,type:"prefix_select",uuid,group}]}}</li>
 *   <li>{@code POST /api/azlink/prefixes/ack}
 *       body {@code {op_id,status:"applied"|"rejected",reason,selected}}</li>
 * </ul>
 */
public final class PrefixBridge {
    private static volatile AzLinkPlugin plugin;

    private PrefixBridge() {
    }

    public static void install(AzLinkPlugin instance) {
        plugin = instance;
    }

    /** {@code true} when AzLink is linked to the site and can serve requests. */
    public static boolean isAvailable() {
        AzLinkPlugin current = plugin;
        return current != null && current.getConfig() != null && current.getConfig().isValid();
    }

    /**
     * Push the current in-game prefix snapshot for online (or known) players.
     * JSON {@code {"players":[{uuid,name,available:[{group,prefix_raw,category}],selected,locked_staff}]}}.
     */
    public static CompletableFuture<String> report(String json) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        JsonObject body = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        return current.getHttpClient().reportPrefixes(body).thenApply(Object::toString);
    }

    /** JSON {@code {"operations":[{op_id,type,uuid,group}...]}}. */
    public static CompletableFuture<String> fetchPending(int limit) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient().fetchPrefixOperations(limit).thenApply(Object::toString);
    }

    /**
     * Ack one {@code prefix_select} operation as {@code applied} or {@code rejected}; safe to repeat.
     * {@code selected} is the group now active in-game ({@code null} = Авто).
     */
    public static CompletableFuture<String> ack(String opId, String status, String reason, String selected) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient()
                .ackPrefixOperation(opId, status, reason, selected)
                .thenApply(Object::toString);
    }
}
