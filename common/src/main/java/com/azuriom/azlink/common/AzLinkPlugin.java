package com.azuriom.azlink.common;

import com.azuriom.azlink.common.command.AzLinkCommand;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.config.PluginConfig;
import com.azuriom.azlink.common.executor.SemanticExecutorService;
import com.azuriom.azlink.common.kits.manifest.KitManifestProvider;
import com.azuriom.azlink.common.kits.manifest.KitManifestSyncCoordinator;
import com.azuriom.azlink.common.kits.manifest.KitManifestSyncService;
import com.azuriom.azlink.common.kits.manifest.KitManifestVersionStore;
import com.azuriom.azlink.common.kits.manifest.render.ClientCapabilityRegistry;
import com.azuriom.azlink.common.kits.manifest.render.IconRenderCache;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderCoordinator;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderRequest;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderTransport;
import com.azuriom.azlink.common.kits.manifest.render.PreferredPlayerRenderWorkerSelector;
import com.azuriom.azlink.common.privileges.PrivilegeBackend;
import com.azuriom.azlink.common.data.PlatformData;
import com.azuriom.azlink.common.data.PlayerData;
import com.azuriom.azlink.common.data.ServerData;
import com.azuriom.azlink.common.data.SystemData;
import com.azuriom.azlink.common.data.WorldData;
import com.azuriom.azlink.common.gson.InstantAdapter;
import com.azuriom.azlink.common.http.client.HttpClient;
import com.azuriom.azlink.common.http.server.HttpServer;
import com.azuriom.azlink.common.http.server.NettyHttpServer;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;
import com.azuriom.azlink.common.tasks.FetcherTask;
import com.azuriom.azlink.common.users.UserManager;
import com.azuriom.azlink.common.utils.SystemUtils;
import com.azuriom.azlink.common.utils.UpdateChecker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AzLinkPlugin {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();
    private static final Gson GSON_PRETTY_PRINT = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    private final HttpClient httpClient = new HttpClient(this);
    private final UserManager userManager = new UserManager(this);

    private final AzLinkCommand command = new AzLinkCommand(this);

    private final FetcherTask fetcherTask = new FetcherTask(this);

    private final AzLinkPlatform platform;
    private final SemanticExecutorService semanticExecutorService = new SemanticExecutorService(this);
    private final KitManifestSyncService kitManifestSyncService = new KitManifestSyncService(this);
    private final KitManifestVersionStore kitManifestVersionStore = new KitManifestVersionStore(this);
    private final ClientCapabilityRegistry clientCapabilityRegistry = new ClientCapabilityRegistry();
    private final IconRenderCache iconRenderCache = new IconRenderCache();
    private volatile ItemIconRenderTransport itemIconRenderTransport = NOOP_TRANSPORT;
    private ItemIconRenderCoordinator itemIconRenderCoordinator;
    private KitManifestSyncCoordinator kitManifestSyncCoordinator;

    private PluginConfig config = new PluginConfig(null, null);
    private HttpServer httpServer;
    private Path configFile;

    private boolean logCpuError = true;

    private static final ItemIconRenderTransport NOOP_TRANSPORT = new ItemIconRenderTransport() {
        @Override
        public void sendRequest(java.util.UUID playerId, ItemIconRenderRequest request) {
            // networking not registered yet
        }

        @Override
        public void sendCapabilities(com.azuriom.azlink.common.kits.manifest.render.ClientCapabilities capabilities) {
            // client-only
        }
    };

    public AzLinkPlugin(AzLinkPlatform platform) {
        this.platform = platform;
        this.itemIconRenderCoordinator = new ItemIconRenderCoordinator(
                this.clientCapabilityRegistry,
                new PreferredPlayerRenderWorkerSelector(
                        this.clientCapabilityRegistry,
                        uuid -> getPlatform().getOnlinePlayers().anyMatch(p -> p.getUuid().equals(uuid))),
                this.iconRenderCache,
                new ItemIconRenderTransport() {
                    @Override
                    public void sendRequest(java.util.UUID playerId, ItemIconRenderRequest request) {
                        AzLinkPlugin.this.itemIconRenderTransport.sendRequest(playerId, request);
                    }

                    @Override
                    public void sendCapabilities(
                            com.azuriom.azlink.common.kits.manifest.render.ClientCapabilities capabilities) {
                        AzLinkPlugin.this.itemIconRenderTransport.sendCapabilities(capabilities);
                    }
                },
                platform.getLoggerAdapter()
        );
        this.kitManifestSyncCoordinator = new KitManifestSyncCoordinator(this, this.itemIconRenderCoordinator);
    }

    public void init() {
        if (!this.platform.isDedicatedServerSiteLinkAllowed()) {
            getLogger().warn("AzLink site features disabled: not a dedicated server environment.");
            return;
        }

        this.configFile = this.platform.getDataDirectory().resolve("config.json");

        try (BufferedReader reader = Files.newBufferedReader(this.configFile)) {
            this.config = GSON.fromJson(reader, PluginConfig.class);
        } catch (NoSuchFileException e) {
            // ignore, not setup yet
        } catch (IOException e) {
            getLogger().error("Error while loading configuration", e);
            return;
        }

        this.httpServer = createHttpServer();

        // Add a random start delay to prevent important load on shared web hosts
        // caused by many servers sending request at the same time
        LocalDateTime start = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(1)
                .plusSeconds(1 + (long) (Math.random() * 30));
        long startDelay = Duration.between(LocalDateTime.now(), start).toMillis();
        long repeatDelay = TimeUnit.MINUTES.toMillis(1);

        getScheduler().scheduleAsyncRepeating(this.fetcherTask, startDelay, repeatDelay, TimeUnit.MILLISECONDS);

        // Load ledgers + schedule poll even before site link; poll no-ops until configured.
        this.semanticExecutorService.start();

        if (!this.config.isValid()) {
            getLogger().warn("Invalid configuration, please use '/azlink' to setup the plugin.");
            return;
        }

        if (this.config.hasInstantCommands() && this.httpServer != null) {
            this.httpServer.start();
        }

        if (this.config.hasUpdatesCheck()) {
            UpdateChecker updateChecker = new UpdateChecker(this);

            getScheduler().executeAsync(updateChecker::checkUpdates);
        }

        this.httpClient.verifyStatus()
                .thenRun(() -> getLogger().info("Successfully connected to " + this.config.getSiteUrl()))
                .exceptionally(ex -> {
                    getLogger().warn("Unable to verify the website connection: " + ex.getMessage());

                    return null;
                });
    }

    public void restartHttpServer() {
        if (this.httpServer != null) {
            this.httpServer.stop();
        }

        this.httpServer = createHttpServer();

        this.httpServer.start();
    }

    public void shutdown() {
        getLogger().info("Shutting down Semantic Executor");
        this.semanticExecutorService.stop();

        getLogger().info("Shutting down scheduler");

        try {
            getScheduler().shutdown();
        } catch (Exception e) {
            getLogger().warn("Error while shutting down scheduler", e);
        }

        if (this.httpServer != null) {
            getLogger().info("Stopping HTTP server");
            this.httpServer.stop();
        }
    }

    public void saveConfig() throws IOException {
        if (!Files.isDirectory(this.platform.getDataDirectory())) {
            Files.createDirectories(this.platform.getDataDirectory());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(this.configFile)) {
            GSON_PRETTY_PRINT.toJson(this.config, writer);
        }
    }

    public AzLinkCommand getCommand() {
        return this.command;
    }

    public ServerData getServerData(boolean fullData) {
        List<PlayerData> players = this.platform.getOnlinePlayers()
                .map(CommandSender::toData)
                .collect(Collectors.toList());
        List<PlayerData> knownPlayers = this.platform.getKnownPlayers();
        int max = this.platform.getMaxPlayers();

        double cpuUsage = getCpuUsage();

        String version = this.platform.getPluginVersion();
        SystemData system = fullData ? new SystemData(SystemUtils.getMemoryUsage(), cpuUsage) : null;
        WorldData world = fullData ? this.platform.getWorldData().orElse(null) : null;
        PlatformData platformData = this.platform.getPlatformData();

        return new ServerData(platformData, version, players, max, knownPlayers, system, world, fullData);
    }

    public CompletableFuture<Void> fetch() {
        return this.fetcherTask.fetch();
    }

    public LoggerAdapter getLogger() {
        return this.platform.getLoggerAdapter();
    }

    public SchedulerAdapter getScheduler() {
        return this.platform.getSchedulerAdapter();
    }

    public PluginConfig getConfig() {
        return this.config;
    }

    public AzLinkPlatform getPlatform() {
        return this.platform;
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    public UserManager getUserManager() {
        return this.userManager;
    }

    public SemanticExecutorService getSemanticExecutorService() {
        return this.semanticExecutorService;
    }

    /**
     * Call before {@link #init()} when LuckPerms is installed.
     */
    public void installLuckPerms(PrivilegeBackend backend) {
        this.semanticExecutorService.useLuckPerms(backend);
    }

    /**
     * Public Kit Manifest Sync API for platform modules.
     * NeoForge hooks FTB Essentials {@code KitManager} create/replace → {@link KitManifestSyncCoordinator#publish}.
     * Site HTTP is dedicated-server only.
     */
    public KitManifestProvider getKitManifestProvider() {
        return this.kitManifestSyncService;
    }

    public KitManifestSyncCoordinator getKitManifestSyncCoordinator() {
        return this.kitManifestSyncCoordinator;
    }

    public KitManifestVersionStore getKitManifestVersionStore() {
        return this.kitManifestVersionStore;
    }

    public ItemIconRenderCoordinator getItemIconRenderCoordinator() {
        return this.itemIconRenderCoordinator;
    }

    public void setItemIconRenderTransport(ItemIconRenderTransport transport) {
        this.itemIconRenderTransport = transport == null ? NOOP_TRANSPORT : transport;
    }

    public boolean isDedicatedServerSiteLinkAllowed() {
        return this.platform.isDedicatedServerSiteLinkAllowed();
    }

    protected HttpServer createHttpServer() {
        return new NettyHttpServer(this);
    }

    private double getCpuUsage() {
        try {
            return SystemUtils.getCpuUsage();
        } catch (Throwable t) {
            if (this.logCpuError) {
                this.logCpuError = false;

                getLogger().warn("Error while retrieving CPU usage", t);
            }
        }
        return -1;
    }

    public boolean isConfigured() {
        return this.config != null && this.config.isValid();
    }

    public static Gson getGson() {
        return GSON;
    }

    public static Gson getGsonPrettyPrint() {
        return GSON_PRETTY_PRINT;
    }
}
