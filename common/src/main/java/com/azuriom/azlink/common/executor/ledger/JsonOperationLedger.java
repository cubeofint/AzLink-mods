package com.azuriom.azlink.common.executor.ledger;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.gson.InstantAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Atomic JSON file ledger under AzLink data directory.
 * Chosen over SQLite: no native/JDBC deps, fits Java 8 common shade, survives restart via fsync-style rename.
 */
public class JsonOperationLedger implements OperationLedger {

    private static final Type LIST_TYPE = new TypeToken<List<LedgerEntry>>() {
    }.getType();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    private final Path file;
    private final Map<String, LedgerEntry> entries = new LinkedHashMap<String, LedgerEntry>();

    public JsonOperationLedger(Path file) throws IOException {
        this.file = file;
        load();
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(this.file)) {
            List<LedgerEntry> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded == null) {
                return;
            }
            for (LedgerEntry entry : loaded) {
                if (entry != null && entry.getOperationId() != null) {
                    this.entries.put(entry.getOperationId(), entry);
                }
            }
        }
    }

    @Override
    public synchronized Optional<LedgerEntry> find(String operationId) {
        return Optional.ofNullable(this.entries.get(operationId));
    }

    @Override
    public synchronized void put(LedgerEntry entry) throws IOException {
        this.entries.put(entry.getOperationId(), entry);
        flush();
    }

    @Override
    public synchronized Collection<LedgerEntry> all() {
        return Collections.unmodifiableCollection(new ArrayList<LedgerEntry>(this.entries.values()));
    }

    @Override
    public synchronized void flush() throws IOException {
        Path parent = this.file.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
        Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp)) {
            GSON.toJson(new ArrayList<LedgerEntry>(this.entries.values()), LIST_TYPE, writer);
        }
        try {
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path getFile() {
        return this.file;
    }

    /** Reload from disk — used by restart-persistence tests. */
    public synchronized void reload() throws IOException {
        this.entries.clear();
        load();
    }

    public static JsonOperationLedger openDefault(Path dataDirectory) throws IOException {
        return new JsonOperationLedger(dataDirectory.resolve("semantic-operations-ledger.json"));
    }
}
