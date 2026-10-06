package com.azuriom.azlink.common.executor.ledger;

import com.azuriom.azlink.common.gson.InstantAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Last successfully applied entitlement version per player UUID (local server identity).
 */
public class EntitlementVersionLedger {

    private static final Type LIST_TYPE = new TypeToken<List<VersionEntry>>() {
    }.getType();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    private final Path file;
    private final Map<String, VersionEntry> byPlayer = new LinkedHashMap<String, VersionEntry>();

    public EntitlementVersionLedger(Path file) throws IOException {
        this.file = file;
        load();
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(this.file)) {
            List<VersionEntry> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded == null) {
                return;
            }
            for (VersionEntry entry : loaded) {
                if (entry != null && entry.playerUuid != null) {
                    this.byPlayer.put(entry.playerUuid, entry);
                }
            }
        }
    }

    public synchronized Optional<Long> getAppliedVersion(UUID playerUuid) {
        VersionEntry entry = this.byPlayer.get(playerUuid.toString());
        return entry == null ? Optional.<Long>empty() : Optional.of(entry.version);
    }

    public synchronized void putAppliedVersion(UUID playerUuid, long version) throws IOException {
        VersionEntry entry = new VersionEntry();
        entry.playerUuid = playerUuid.toString();
        entry.version = version;
        entry.updatedAt = Instant.now();
        this.byPlayer.put(entry.playerUuid, entry);
        flush();
    }

    public synchronized void flush() throws IOException {
        Path parent = this.file.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
        Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp)) {
            GSON.toJson(new ArrayList<VersionEntry>(this.byPlayer.values()), LIST_TYPE, writer);
        }
        try {
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized void reload() throws IOException {
        this.byPlayer.clear();
        load();
    }

    public static EntitlementVersionLedger openDefault(Path dataDirectory) throws IOException {
        return new EntitlementVersionLedger(dataDirectory.resolve("semantic-entitlement-versions.json"));
    }

    private static final class VersionEntry {
        @SerializedName("player_uuid")
        String playerUuid;
        long version;
        @SerializedName("updated_at")
        Instant updatedAt;
    }
}
