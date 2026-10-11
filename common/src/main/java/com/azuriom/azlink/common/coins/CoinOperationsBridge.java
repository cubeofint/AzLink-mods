package com.azuriom.azlink.common.coins;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.http.client.HttpClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * Stable string-based facade for other mods (e.g. cointcore) to pull the site coin-operation queue
 * without a compile dependency on AzLink internals. All methods return JSON strings.
 *
 * <p>cointcore prefix sync reflects {@code postJson}/{@code getJson} and optional
 * {@code reportPrefixes}/{@code reportPrefixCatalog}/{@code fetchPrefixOperations}/{@code ackPrefix} on this class
 * (same names as {@code PrefixBridge}). Paths are {@code /azlink/...}; HttpClient prepends
 * {@code /api}. Ack status for prefixes is {@code applied}|{@code rejected}.
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

    /**
     * Ack with the in-game wallet after handling the op. cointcore calls this reflectively and
     * falls back to {@link #ack(String, String, String)} on older AzLink builds.
     */
    public static CompletableFuture<String> ackWithBalance(String operationId, String status, String error,
                                                           long balanceAfter) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient()
                .ackCoinOperation(operationId, status, error, balanceAfter)
                .thenApply(Object::toString);
    }

    /**
     * Send a batch of server-wallet movements (JSON {@code {"movements":[...]}}). Idempotent on the site
     * by (server, movement id). Returns the site JSON ({@code accepted_up_to}, {@code stored}, ...).
     */
    public static CompletableFuture<String> postMovements(String json) {
        return postJson("/azlink/coins/movements", json);
    }

    /**
     * Generic POST used by cointcore prefix sync (and anything else that only has this bridge).
     * {@code path} is {@code /azlink/...}; HttpClient prepends {@code siteUrl + /api}.
     */
    public static CompletableFuture<String> postJson(String path, String json) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        JsonElement body = (json == null || json.isBlank())
                ? null
                : AzLinkPlugin.getGson().fromJson(json, JsonElement.class);
        return current.getHttpClient()
                .request(HttpClient.RequestMethod.POST, path, body, JsonObject.class)
                .thenApply(Object::toString);
    }

    /**
     * Generic GET. {@code path} may include a query, e.g. {@code /azlink/prefixes/operations?limit=50}.
     */
    public static CompletableFuture<String> getJson(String path) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient()
                .request(HttpClient.RequestMethod.GET, path, null, JsonObject.class)
                .thenApply(Object::toString);
    }

    /**
     * Game → site prefix snapshot. Same contract as {@code PrefixBridge.reportPrefixes}.
     */
    public static CompletableFuture<String> reportPrefixes(String json) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        JsonObject body = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        return current.getHttpClient().reportPrefixes(body).thenApply(Object::toString);
    }

    /** Game → site prefix catalog. Same contract as {@code PrefixBridge.reportPrefixCatalog}. */
    public static CompletableFuture<String> reportPrefixCatalog(String json) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        JsonObject body = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        return current.getHttpClient().reportPrefixCatalog(body).thenApply(Object::toString);
    }

    /** Site → game pending {@code prefix_select} ops for this server. */
    public static CompletableFuture<String> fetchPrefixOperations(int limit) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient().fetchPrefixOperations(limit).thenApply(Object::toString);
    }

    /**
     * Ack a prefix op. {@code status} is {@code applied} or {@code rejected}. {@code selected} may be {@code null}.
     */
    public static CompletableFuture<String> ackPrefix(String opId, String status, String reason, String selected) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient().ackPrefix(opId, status, reason, selected).thenApply(Object::toString);
    }
}
