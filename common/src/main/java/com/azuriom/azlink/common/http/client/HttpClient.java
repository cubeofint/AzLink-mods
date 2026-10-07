package com.azuriom.azlink.common.http.client;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.data.ServerData;
import com.azuriom.azlink.common.data.UserInfo;
import com.azuriom.azlink.common.data.WebsiteResponse;
import com.azuriom.azlink.common.users.EditMoneyResult;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class HttpClient {

    private static final int CONNECT_TIMEOUT = 5000; // 5 seconds
    private static final int READ_TIMEOUT = 5000; // 5 seconds

    private final AzLinkPlugin plugin;

    public HttpClient(AzLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> verifyStatus() {
        return request(RequestMethod.GET, "/azlink", null);
    }

    /**
     * Edit the user's game currency (coins). Site donate money ({@code /money/...}) is blocked with 403.
     *
     * @param idempotencyKey stable key for this logical mutation (must not change across HTTP retries)
     */
    public CompletableFuture<EditMoneyResult> editCoins(UserInfo user, String action, double amount,
                                                        String idempotencyKey) {
        String endpoint = "/azlink/user/" + user.getId() + "/coins/" + action;
        JsonObject params = new JsonObject();
        params.addProperty("amount", amount);
        params.addProperty("idempotency_key", idempotencyKey);

        return request(RequestMethod.POST, endpoint, params, EditMoneyResult.class);
    }

    /**
     * @deprecated Use {@link #editCoins(UserInfo, String, double, String)} — {@code /money/} is rejected by the site.
     */
    @Deprecated
    public CompletableFuture<EditMoneyResult> editMoney(UserInfo user, String action, double amount) {
        return editCoins(user, action, amount, java.util.UUID.randomUUID().toString());
    }

    /**
     * Pending site&lt;-&gt;server coin operations queued for this server (JSON body of the site response).
     */
    public CompletableFuture<com.google.gson.JsonObject> fetchCoinOperations(int limit) {
        return request(RequestMethod.GET, "/azlink/coins/operations?limit=" + limit, null, com.google.gson.JsonObject.class);
    }

    /**
     * Acknowledge a queued operation. {@code status} is {@code applied} or {@code failed}. Idempotent on the site.
     */
    public CompletableFuture<com.google.gson.JsonObject> ackCoinOperation(String operationId, String status, String error) {
        JsonObject params = new JsonObject();
        params.addProperty("status", status);
        if (error != null) {
            params.addProperty("error", error);
        }
        return request(RequestMethod.POST, "/azlink/coins/operations/" + operationId + "/ack", params, com.google.gson.JsonObject.class);
    }

    public CompletableFuture<WebsiteResponse> postData(ServerData data) {
        return request(RequestMethod.POST, "/azlink", data, WebsiteResponse.class);
    }

    public CompletableFuture<Void> request(RequestMethod method, String endpoint, Object params) {
        return request(method, endpoint, params, Void.class);
    }

    public <T> CompletableFuture<T> request(RequestMethod method, String endpoint, Object params, Class<T> clazz) {
        String body = AzLinkPlugin.getGson().toJson(params);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return rawRequest(method, endpoint, body, clazz);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, this.plugin.getScheduler().asyncExecutor());
    }

    private <T> T rawRequest(RequestMethod method, String endpoint, String body, Class<T> clazz)
            throws IOException {
        HttpURLConnection conn = prepareConnection(method, endpoint);

        if (method != RequestMethod.GET && body != null && !body.isEmpty()) {
            conn.setDoOutput(true);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = conn.getResponseCode();

        if (status >= 400) {
            String info = status == 401 || status == 403
                    ? ". Try to do again the link command given on the admin panel." : "";

            throw new IOException("Unexpected HTTP error " + status + info);
        }

        if (status >= 300) {
            String dest = conn.getHeaderField("Location");

            throw new IOException("Unexpected redirect status - " + status + ": " + dest);
        }

        if (clazz == null || clazz == Void.class) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            T response = AzLinkPlugin.getGson().fromJson(reader, clazz);

            if (response == null) {
                throw new IllegalStateException("Empty JSON response from API.");
            }

            return response;
        }
    }

    private HttpURLConnection prepareConnection(RequestMethod method, String endpoint) throws IOException {
        String baseUrl = this.plugin.getConfig().getSiteUrl();
        String version = this.plugin.getPlatform().getPluginVersion();
        String token = this.plugin.getConfig().getSiteKey();
        URL url = URI.create(baseUrl + "/api" + endpoint).toURL();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false); // POST requests are redirected as GET requests
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod(method.name());
        conn.addRequestProperty("Accept", "application/json");
        conn.addRequestProperty("Azuriom-Link-Token", token);
        conn.addRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.addRequestProperty("User-Agent", "AzLink java v" + version);

        return conn;
    }

    public enum RequestMethod {
        GET, POST, PATCH, PUT, DELETE
    }
}
