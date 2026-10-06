package com.azuriom.azlink.common.kits.manifest.client;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.http.client.HttpClient;
import com.azuriom.azlink.common.kits.manifest.icon.IconContentAddressing;
import com.azuriom.azlink.common.kits.manifest.model.IconUploadResult;
import com.azuriom.azlink.common.kits.manifest.model.KitEnsureRequest;
import com.azuriom.azlink.common.kits.manifest.model.KitLifecycleResponse;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadRequest;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadResponse;
import com.azuriom.azlink.common.utils.VersionInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * HTTP client for kit manifests / item-icons. Auth: {@code Azuriom-Link-Token} only.
 */
public final class HttpKitManifestClient implements KitManifestApiClient {

    public static final String MANIFESTS_ENDPOINT = "/shop/azlink/v1/kits/manifests";
    public static final String ICONS_ENDPOINT = "/shop/azlink/v1/kits/item-icons";
    public static final String KITS_ENDPOINT = "/shop/azlink/v1/kits";

    private final AzLinkPlugin plugin;

    public HttpKitManifestClient(AzLinkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ApiResult<IconUploadResult>> uploadIcon(byte[] pngBytes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                IconContentAddressing.validatePng(pngBytes);
                return doUploadIcon(pngBytes);
            } catch (Exception e) {
                return ApiResult.failure(e);
            }
        }, this.plugin.getScheduler().asyncExecutor());
    }

    @Override
    public CompletableFuture<ApiResult<KitManifestUploadResponse>> uploadManifest(KitManifestUploadRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doUploadManifest(request);
            } catch (Exception e) {
                return ApiResult.failure(e);
            }
        }, this.plugin.getScheduler().asyncExecutor());
    }

    @Override
    public CompletableFuture<ApiResult<KitLifecycleResponse>> ensureKit(KitEnsureRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doEnsureKit(request);
            } catch (Exception e) {
                return ApiResult.failure(e);
            }
        }, this.plugin.getScheduler().asyncExecutor());
    }

    @Override
    public CompletableFuture<ApiResult<KitLifecycleResponse>> retireKit(String deliveryKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doRetireKit(deliveryKey);
            } catch (Exception e) {
                return ApiResult.failure(e);
            }
        }, this.plugin.getScheduler().asyncExecutor());
    }

    private ApiResult<IconUploadResult> doUploadIcon(byte[] pngBytes) throws IOException {
        String boundary = "----AzLinkIcon" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection conn = open(HttpClient.RequestMethod.POST, ICONS_ENDPOINT, null);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = conn.getOutputStream()) {
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"icon\"; filename=\"icon.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(pngBytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        String body = readBody(conn, status);
        this.plugin.getLogger().info("[KitManifest] POST " + ICONS_ENDPOINT + " HTTP " + status);
        if (status >= 200 && status < 300) {
            IconUploadResult parsed = AzLinkPlugin.getGson().fromJson(body, IconUploadResult.class);
            return ApiResult.ok(status, parsed);
        }
        logHttpError(ICONS_ENDPOINT, status, body);
        return ApiResult.httpError(status, body);
    }

    private ApiResult<KitManifestUploadResponse> doUploadManifest(KitManifestUploadRequest request) throws IOException {
        String json = AzLinkPlugin.getGson().toJson(request);
        HttpURLConnection conn = open(HttpClient.RequestMethod.POST, MANIFESTS_ENDPOINT, json);
        int status = conn.getResponseCode();
        String body = readBody(conn, status);
        this.plugin.getLogger().info("[KitManifest] POST " + MANIFESTS_ENDPOINT
                + " delivery_key=" + request.getDeliveryKey()
                + " version=" + request.getManifestVersion()
                + " HTTP " + status);
        if (status >= 200 && status < 300) {
            KitManifestUploadResponse parsed = AzLinkPlugin.getGson().fromJson(body, KitManifestUploadResponse.class);
            return ApiResult.ok(status, parsed);
        }
        logHttpError(MANIFESTS_ENDPOINT, status, body);
        KitManifestUploadResponse err = null;
        try {
            err = AzLinkPlugin.getGson().fromJson(body, KitManifestUploadResponse.class);
        } catch (RuntimeException ignored) {
            // keep null
        }
        ApiResult<KitManifestUploadResponse> result = ApiResult.httpError(status, body);
        if (err != null && err.getError() != null) {
            return ApiResult.httpError(status, body);
        }
        return result;
    }

    private ApiResult<KitLifecycleResponse> doEnsureKit(KitEnsureRequest request) throws IOException {
        String json = AzLinkPlugin.getGson().toJson(request);
        HttpURLConnection conn = open(HttpClient.RequestMethod.POST, KITS_ENDPOINT, json);
        int status = conn.getResponseCode();
        String body = readBody(conn, status);
        this.plugin.getLogger().info("[KitManifest] POST " + KITS_ENDPOINT
                + " delivery_key=" + request.getDeliveryKey()
                + " HTTP " + status);
        if (status >= 200 && status < 300) {
            KitLifecycleResponse parsed = AzLinkPlugin.getGson().fromJson(body, KitLifecycleResponse.class);
            return ApiResult.ok(status, parsed);
        }
        logHttpError(KITS_ENDPOINT, status, body);
        return ApiResult.httpError(status, body);
    }

    private ApiResult<KitLifecycleResponse> doRetireKit(String deliveryKey) throws IOException {
        String endpoint = KITS_ENDPOINT + "/" + deliveryKey;
        HttpURLConnection conn = open(HttpClient.RequestMethod.DELETE, endpoint, null);
        int status = conn.getResponseCode();
        String body = readBody(conn, status);
        this.plugin.getLogger().info("[KitManifest] DELETE " + endpoint + " HTTP " + status);
        if (status >= 200 && status < 300) {
            KitLifecycleResponse parsed = AzLinkPlugin.getGson().fromJson(body, KitLifecycleResponse.class);
            return ApiResult.ok(status, parsed);
        }
        logHttpError(endpoint, status, body);
        return ApiResult.httpError(status, body);
    }

    private void logHttpError(String endpoint, int status, String body) {
        if (status == 401 || status == 403) {
            this.plugin.getLogger().warn("[KitManifest] auth failure " + endpoint + " HTTP " + status);
        } else if (status == 422) {
            this.plugin.getLogger().warn("[KitManifest] validation error " + endpoint + ": " + body);
        } else {
            this.plugin.getLogger().warn("[KitManifest] " + endpoint + " HTTP " + status
                    + (body == null || body.isEmpty() ? "" : " " + body));
        }
    }

    private HttpURLConnection open(HttpClient.RequestMethod method, String endpoint, String jsonBody)
            throws IOException {
        String baseUrl = this.plugin.getConfig().getSiteUrl();
        String token = this.plugin.getConfig().getSiteKey();
        URL url = URI.create(baseUrl + "/api" + endpoint).toURL();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod(method.name());
        conn.addRequestProperty("Accept", "application/json");
        conn.addRequestProperty("Azuriom-Link-Token", token);
        conn.addRequestProperty("User-Agent", "AzLink KitManifest/" + VersionInfo.VERSION);

        if (jsonBody != null) {
            conn.setDoOutput(true);
            conn.addRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    private static String readBody(HttpURLConnection conn, int status) throws IOException {
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
