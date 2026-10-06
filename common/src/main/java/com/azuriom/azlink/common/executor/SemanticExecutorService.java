package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.executor.client.HttpOperationsClient;
import com.azuriom.azlink.common.privileges.PrivilegeBackend;
import com.azuriom.azlink.common.executor.client.OperationsApiClient;
import com.azuriom.azlink.common.executor.ledger.EntitlementVersionLedger;
import com.azuriom.azlink.common.executor.ledger.JsonOperationLedger;
import com.azuriom.azlink.common.scheduler.CancellableTask;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Boots Semantic Executor: durable ledgers, poll client, scheduled poll loop.
 */
public class SemanticExecutorService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final AzLinkPlugin plugin;
    private ExecutorConfig config;
    private SemanticExecutor executor;
    private SemanticPollTask pollTask;
    private CancellableTask scheduled;
    private Path configFile;
    private PrivilegeBackend privilegeBackend;
    private boolean luckPermsAcceptAllCapabilities;

    public SemanticExecutorService(AzLinkPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Install LuckPerms before {@link #start()}. Empty capability lists then accept every site capability.
     */
    public void useLuckPerms(PrivilegeBackend backend) {
        this.privilegeBackend = backend;
        this.luckPermsAcceptAllCapabilities = backend != null;
    }

    public void start() {
        Path dataDir = this.plugin.getPlatform().getDataDirectory();
        this.configFile = dataDir.resolve("executor.json");

        try {
            this.config = loadOrCreateConfig();
            if (this.luckPermsAcceptAllCapabilities && this.config.getSupportedCapabilities().isEmpty()) {
                this.config.setSupportedCapabilities(Collections.singletonList("*"));
            }
            JsonOperationLedger operationLedger = JsonOperationLedger.openDefault(dataDir);
            EntitlementVersionLedger entitlementLedger = EntitlementVersionLedger.openDefault(dataDir);
            this.executor = new SemanticExecutor(this.config, operationLedger, entitlementLedger,
                    this.plugin.getLogger(), this.privilegeBackend);
            OperationsApiClient client = new HttpOperationsClient(this.plugin, this.config);
            this.pollTask = new SemanticPollTask(this.plugin, this.executor, client);

            if (!this.config.isEnabled()) {
                this.plugin.getLogger().info("[SemanticExecutor] disabled via executor.json");
                return;
            }

            long intervalSec = this.config.getPollIntervalSeconds();
            long initialDelayMs = 5_000L + (long) (Math.random() * 5_000L);
            this.scheduled = this.plugin.getScheduler().scheduleAsyncRepeating(
                    this.pollTask, initialDelayMs, intervalSec * 1000L, TimeUnit.MILLISECONDS);

            this.plugin.getLogger().info("[SemanticExecutor] started executor "
                    + (this.privilegeBackend == null ? "mock-privileges " : "luckperms ")
                    + this.config.getExecutorVersion()
                    + " protocol=" + this.config.getProtocolVersion()
                    + " poll=" + intervalSec + "s"
                    + " max_batch=" + this.config.getMaxBatch()
                    + (this.plugin.isConfigured() ? "" : " (waiting for site link)"));
        } catch (IOException e) {
            this.plugin.getLogger().error("[SemanticExecutor] failed to start", e);
        }
    }

    public void stop() {
        if (this.scheduled != null) {
            this.scheduled.cancel();
            this.scheduled = null;
        }
    }

    public SemanticExecutor getExecutor() {
        return this.executor;
    }

    public SemanticPollTask getPollTask() {
        return this.pollTask;
    }

    public ExecutorConfig getConfig() {
        return this.config;
    }

    private ExecutorConfig loadOrCreateConfig() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(this.configFile)) {
            ExecutorConfig loaded = GSON.fromJson(reader, ExecutorConfig.class);
            return loaded != null ? loaded : ExecutorConfig.defaults();
        } catch (NoSuchFileException e) {
            ExecutorConfig defaults = ExecutorConfig.defaults();
            saveConfig(defaults);
            return defaults;
        }
    }

    private void saveConfig(ExecutorConfig config) throws IOException {
        Path parent = this.configFile.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(this.configFile)) {
            GSON.toJson(config, writer);
        }
    }
}
