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
 * Last successfully applied entitlement version per player, plus the exact
 * LuckPerms nodes written for each entitlement key (group, meta, expiry).
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
                    if (entry.grants == null) {
                        entry.grants = new ArrayList<GrantRecord>();
                    }
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
        VersionEntry entry = playerEntry(playerUuid);
        entry.version = version;
        entry.updatedAt = Instant.now();
        flush();
    }

    public synchronized Optional<GrantedNodes> getGrant(UUID playerUuid, String entitlementKey) {
        if (playerUuid == null || isBlank(entitlementKey)) {
            return Optional.empty();
        }
        VersionEntry entry = this.byPlayer.get(playerUuid.toString());
        if (entry == null || entry.grants == null) {
            return Optional.empty();
        }
        GrantRecord record = findGrant(entry, entitlementKey);
        return record == null ? Optional.<GrantedNodes>empty() : Optional.of(toGranted(record));
    }

    public synchronized Optional<Long> getGrantVersion(UUID playerUuid, String entitlementKey) {
        Optional<GrantedNodes> grant = getGrant(playerUuid, entitlementKey);
        return grant.isPresent() ? Optional.of(grant.get().getVersion()) : Optional.<Long>empty();
    }

    public synchronized void putGrant(UUID playerUuid, GrantedNodes grant) throws IOException {
        if (playerUuid == null || grant == null || isBlank(grant.getEntitlementKey())) {
            throw new IllegalArgumentException("grant requires player and entitlement_key");
        }
        VersionEntry entry = playerEntry(playerUuid);
        if (entry.grants == null) {
            entry.grants = new ArrayList<GrantRecord>();
        }
        GrantRecord record = findGrant(entry, grant.getEntitlementKey());
        if (record == null) {
            record = new GrantRecord();
            record.entitlementKey = grant.getEntitlementKey();
            entry.grants.add(record);
        }
        record.version = grant.getVersion();
        record.group = grant.getGroup();
        record.meta = new ArrayList<String>(grant.getMeta());
        record.permissions = new ArrayList<String>(grant.getPermissions());
        record.expiresAt = grant.getExpiresAt();
        record.updatedAt = grant.getUpdatedAt() != null ? grant.getUpdatedAt() : Instant.now();
        if (grant.getVersion() > entry.version) {
            entry.version = grant.getVersion();
        }
        entry.updatedAt = Instant.now();
        flush();
    }

    public synchronized void removeGrant(UUID playerUuid, String entitlementKey) throws IOException {
        if (playerUuid == null || isBlank(entitlementKey)) {
            return;
        }
        VersionEntry entry = this.byPlayer.get(playerUuid.toString());
        if (entry == null || entry.grants == null) {
            return;
        }
        GrantRecord record = findGrant(entry, entitlementKey);
        if (record == null) {
            return;
        }
        entry.grants.remove(record);
        entry.updatedAt = Instant.now();
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

    private VersionEntry playerEntry(UUID playerUuid) {
        String key = playerUuid.toString();
        VersionEntry entry = this.byPlayer.get(key);
        if (entry == null) {
            entry = new VersionEntry();
            entry.playerUuid = key;
            entry.grants = new ArrayList<GrantRecord>();
            this.byPlayer.put(key, entry);
        }
        if (entry.grants == null) {
            entry.grants = new ArrayList<GrantRecord>();
        }
        return entry;
    }

    private static GrantRecord findGrant(VersionEntry entry, String entitlementKey) {
        for (GrantRecord record : entry.grants) {
            if (record != null && entitlementKey.equals(record.entitlementKey)) {
                return record;
            }
        }
        return null;
    }

    private static GrantedNodes toGranted(GrantRecord record) {
        return new GrantedNodes(
                record.entitlementKey,
                record.version,
                record.group,
                record.meta,
                record.permissions,
                record.expiresAt,
                record.updatedAt
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class VersionEntry {
        @SerializedName("player_uuid")
        String playerUuid;
        long version;
        @SerializedName("updated_at")
        Instant updatedAt;
        List<GrantRecord> grants;
    }

    private static final class GrantRecord {
        @SerializedName("entitlement_key")
        String entitlementKey;
        long version;
        String group;
        List<String> meta;
        List<String> permissions;
        @SerializedName("expires_at")
        Instant expiresAt;
        @SerializedName("updated_at")
        Instant updatedAt;
    }
}
