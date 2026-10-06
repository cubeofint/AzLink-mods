package com.azuriom.azlink.common.executor.client;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.executor.ExecutorConfig;
import com.azuriom.azlink.common.executor.model.AckRequest;
import com.azuriom.azlink.common.executor.model.AckResponse;
import com.azuriom.azlink.common.executor.model.PollRequest;
import com.azuriom.azlink.common.executor.model.PollResponse;
import com.azuriom.azlink.common.http.client.HttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Poll/ACK HTTP client for {@code /api/shop/azlink/v1/operations/*}.
 * Auth: {@code Azuriom-Link-Token} only — never sends server id as identity.
 */
public class HttpOperationsClient implements OperationsApiClient {

    public static final String POLL_ENDPOINT = "/shop/azlink/v1/operations/poll";
    public static final String ACK_ENDPOINT = "/shop/azlink/v1/operations/ack";

    private final AzLinkPlugin plugin;
    private final ExecutorConfig executorConfig;

    public HttpOperationsClient(AzLinkPlugin plugin, ExecutorConfig executorConfig) {
        this.plugin = plugin;
        this.executorConfig = executorConfig;
    }

    @Override
    public CompletableFuture<PollResult> poll(PollRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doPoll(request);
            } catch (Exception e) {
                return PollResult.failure(e);
            }
        }, this.plugin.getScheduler().asyncExecutor());
    }

    @Override
    public CompletableFuture<AckResult> ack(AckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doAck(request);
            } catch (Exception e) {
                return AckResult.failure(e);
            }
        }, this.plugin.getScheduler().asyncExecutor());
    }

    private PollResult doPoll(PollRequest request) throws IOException {
        String body = AzLinkPlugin.getGson().toJson(request);
        HttpURLConnection conn = open(HttpClient.RequestMethod.POST, POLL_ENDPOINT, body);
        int status = conn.getResponseCode();
        String responseBody = readBody(conn, status);
        if (status >= 200 && status < 300) {
            PollResponse parsed = AzLinkPlugin.getGson().fromJson(responseBody, PollResponse.class);
            if (parsed == null) {
                parsed = new PollResponse();
            }
            return PollResult.success(status, parsed);
        }
        return PollResult.httpError(status, responseBody);
    }

    private AckResult doAck(AckRequest request) throws IOException {
        String body = AzLinkPlugin.getGson().toJson(request);
        HttpURLConnection conn = open(HttpClient.RequestMethod.POST, ACK_ENDPOINT, body);
        int status = conn.getResponseCode();
        String responseBody = readBody(conn, status);
        if (status >= 200 && status < 300) {
            AckResponse parsed = responseBody == null || responseBody.trim().isEmpty()
                    ? new AckResponse()
                    : AzLinkPlugin.getGson().fromJson(responseBody, AckResponse.class);
            return AckResult.success(status, parsed);
        }
        return AckResult.httpError(status, responseBody);
    }

    private HttpURLConnection open(HttpClient.RequestMethod method, String endpoint, String body)
            throws IOException {
        String baseUrl = this.plugin.getConfig().getSiteUrl();
        String version = this.plugin.getPlatform().getPluginVersion();
        String token = this.plugin.getConfig().getSiteKey();
        URL url = URI.create(baseUrl + "/api" + endpoint).toURL();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        int timeout = this.executorConfig.getRequestTimeoutMs();
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestMethod(method.name());
        conn.addRequestProperty("Accept", "application/json");
        conn.addRequestProperty("Azuriom-Link-Token", token);
        conn.addRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.addRequestProperty("User-Agent", "AzLink SemanticExecutor/" + this.executorConfig.getExecutorVersion()
                + " java v" + version);

        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
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
