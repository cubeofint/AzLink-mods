package com.azuriom.azlink.common.prefixes;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.http.client.HttpClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * Stable string-based facade for other mods (e.g. cointcore) to report chat prefixes
 * and pull the site prefix-select queue without a compile dependency on AzLink internals.
 * All methods return JSON strings.
 *
 * <p>cointcore reflects these <em>exact</em> names (also mirrored on
 * {@link com.azuriom.azlink.common.coins.CoinOperationsBridge}):
 * {@code postJson}, {@code getJson}, {@code reportPrefixes}, {@code fetchPrefixOperations},
 * {@code ackPrefix}. Paths are {@code /azlink/prefixes/...}; HttpClient prepends
 * {@code siteUrl + /api}.
 *
 * <p>Ack {@code status} is {@code applied} or {@code rejected}.
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
     * Generic POST. {@code path} is {@code /azlink/...} (HttpClient prepends {@code /api}).
     */
    public static CompletableFuture<String> postJson(String path, String json) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        JsonElement body = parseBody(json);
        return current.getHttpClient()
                .request(HttpClient.RequestMethod.POST, path, body, JsonObject.class)
                .thenApply(Object::toString);
    }

    /**
     * Generic GET. {@code path} may include a query string, e.g. {@code /azlink/prefixes/operations?limit=50}.
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
     * Push the current in-game prefix snapshot.
     * JSON {@code {"players":[{uuid,name,available:[{group,prefix_raw,category}],selected,locked_staff}]}}.
     */
    public static CompletableFuture<String> reportPrefixes(String json) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        JsonObject body = AzLinkPlugin.getGson().fromJson(json, JsonObject.class);
        return current.getHttpClient().reportPrefixes(body).thenApply(Object::toString);
    }

    /** JSON {@code {"operations":[{op_id,type,uuid,group}...]}}. */
    public static CompletableFuture<String> fetchPrefixOperations(int limit) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient().fetchPrefixOperations(limit).thenApply(Object::toString);
    }

    /**
     * Ack one {@code prefix_select} as {@code applied} or {@code rejected}; safe to repeat.
     * {@code selected} is the group now active in-game ({@code null} = Авто).
     */
    public static CompletableFuture<String> ackPrefix(String opId, String status, String reason, String selected) {
        AzLinkPlugin current = plugin;
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AzLink not configured"));
        }
        return current.getHttpClient().ackPrefix(opId, status, reason, selected).thenApply(Object::toString);
    }

    private static JsonElement parseBody(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return AzLinkPlugin.getGson().fromJson(json, JsonElement.class);
    }
}
