package com.azuriom.azlink.common.privileges;

import com.azuriom.azlink.common.AzLinkPlugin;
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
import java.util.stream.Collectors;

/**
 * Pushes observed LuckPerms groups to the site.
 * Auth is {@code Azuriom-Link-Token}. The body never carries the token.
 */
public final class HttpLuckPermsStateClient {

    public static final String ENDPOINT = "/shop/azlink/v1/luckperms/state";

    private final AzLinkPlugin plugin;

    public HttpLuckPermsStateClient(AzLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public int post(PlayerGroupSnapshot snapshot) throws IOException {
        String body = AzLinkPlugin.getGson().toJson(new LuckPermsStateRequest(snapshot));
        String baseUrl = this.plugin.getConfig().getSiteUrl();
        String version = this.plugin.getPlatform().getPluginVersion();
        String token = this.plugin.getConfig().getSiteKey();
        URL url = URI.create(baseUrl + "/api" + ENDPOINT).toURL();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod(HttpClient.RequestMethod.POST.name());
        conn.addRequestProperty("Accept", "application/json");
        conn.addRequestProperty("Azuriom-Link-Token", token);
        conn.addRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.addRequestProperty("User-Agent", "AzLink java v" + version);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        discard(conn, status);
        return status;
    }

    private static void discard(HttpURLConnection conn, int status) throws IOException {
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
