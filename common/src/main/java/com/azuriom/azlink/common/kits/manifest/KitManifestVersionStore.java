package com.azuriom.azlink.common.kits.manifest;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persists last successfully allocated manifest_version per delivery_key.
 * Next publish uses {@code last + 1} (minimum 1).
 *
 * <p>File path is resolved lazily so construction is safe during {@link AzLinkPlugin} field init
 * (platform is assigned only in the plugin constructor body).</p>
 */
public final class KitManifestVersionStore {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {
    }.getType();

    private final AzLinkPlugin plugin;
    private final Map<String, Integer> lastVersions = new LinkedHashMap<String, Integer>();
    private boolean loaded;

    public KitManifestVersionStore(AzLinkPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized int allocateNext(String deliveryKey) {
        loadIfNeeded();
        int last = this.lastVersions.containsKey(deliveryKey)
                ? this.lastVersions.get(deliveryKey).intValue()
                : 0;
        int next = last + 1;
        if (next < 1) {
            next = 1;
        }
        this.lastVersions.put(deliveryKey, Integer.valueOf(next));
        saveQuietly();
        return next;
    }

    public synchronized void remember(String deliveryKey, int version) {
        if (deliveryKey == null || deliveryKey.isEmpty() || version < 1) {
            return;
        }
        loadIfNeeded();
        Integer existing = this.lastVersions.get(deliveryKey);
        if (existing == null || version > existing.intValue()) {
            this.lastVersions.put(deliveryKey, Integer.valueOf(version));
            saveQuietly();
        }
    }

    private Path file() {
        return this.plugin.getPlatform().getDataDirectory().resolve("kit-manifest-versions.json");
    }

    private void loadIfNeeded() {
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Integer> parsed = AzLinkPlugin.getGson().fromJson(reader, MAP_TYPE);
            if (parsed != null) {
                this.lastVersions.clear();
                this.lastVersions.putAll(parsed);
            }
        } catch (IOException | RuntimeException e) {
            this.plugin.getLogger().warn("[KitManifest] failed to load version store: " + e.getMessage());
        }
    }

    private void saveQuietly() {
        try {
            Path file = file();
            Path dir = file.getParent();
            if (dir != null && !Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                AzLinkPlugin.getGson().toJson(this.lastVersions, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            this.plugin.getLogger().warn("[KitManifest] failed to save version store: " + e.getMessage());
        }
    }
}
