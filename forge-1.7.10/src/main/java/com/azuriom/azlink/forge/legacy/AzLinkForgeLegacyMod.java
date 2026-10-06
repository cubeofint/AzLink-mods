package com.azuriom.azlink.forge.legacy;

import com.azuriom.azlink.common.AzLinkPlatform;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.data.PlayerData;
import com.azuriom.azlink.common.data.WorldData;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.platform.PlatformInfo;
import com.azuriom.azlink.common.platform.PlatformType;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;
import com.azuriom.azlink.common.tasks.TpsTask;
import com.azuriom.azlink.forge.legacy.command.AzLinkLegacyCommand;
import com.azuriom.azlink.forge.legacy.command.ForgeLegacyCommandSender;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Mod(modid = AzLinkForgeLegacyMod.MODID, name = "AzLink", version = Tags.VERSION, acceptableRemoteVersions = "*")
public final class AzLinkForgeLegacyMod implements AzLinkPlatform {

    public static final String MODID = "azlink";

    private final LoggerAdapter logger = new FmlLoggerAdapter();
    private final TpsTask tpsTask = new TpsTask();
    private final ForgeLegacyScheduler scheduler = new ForgeLegacyScheduler();
    private final AzLinkPlugin plugin = new AzLinkPlugin(this);

    private Path dataDirectory;
    private MinecraftServer server;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configDir = new File(event.getModConfigurationDirectory(), MODID);
        if (!configDir.exists() && !configDir.mkdirs()) {
            this.logger.warn("Unable to create config directory: " + configDir.getAbsolutePath());
        }
        this.dataDirectory = configDir.toPath();
        FMLCommonHandler.instance().bus().register(this);
        FMLCommonHandler.instance().bus().register(this.scheduler);
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        this.server = event.getServer();
        event.registerServerCommand(new AzLinkLegacyCommand(this.plugin));
        this.plugin.init();
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        this.plugin.shutdown();
        this.server = null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            this.tpsTask.run();
        }
    }

    @Override
    public AzLinkPlugin getPlugin() {
        return this.plugin;
    }

    @Override
    public LoggerAdapter getLoggerAdapter() {
        return this.logger;
    }

    @Override
    public SchedulerAdapter getSchedulerAdapter() {
        return this.scheduler;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.FORGE;
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return new PlatformInfo("Forge", "1.7.10");
    }

    @Override
    public String getPluginVersion() {
        return Tags.VERSION;
    }

    @Override
    public Path getDataDirectory() {
        return this.dataDirectory;
    }

    @Override
    public Optional<WorldData> getWorldData() {
        if (this.server == null) {
            return Optional.empty();
        }
        // Chunk/entity counts are optional telemetry — keep simple for 1.7.10.
        return Optional.of(new WorldData(this.tpsTask.getTps(), 0, 0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stream<CommandSender> getOnlinePlayers() {
        if (this.server == null) {
            return Stream.empty();
        }
        List<EntityPlayerMP> players = this.server.getConfigurationManager().playerEntityList;
        List<CommandSender> result = new ArrayList<CommandSender>(players.size());
        for (EntityPlayerMP player : players) {
            result.add(new ForgeLegacyCommandSender(player));
        }
        return result.stream();
    }

    @Override
    public List<PlayerData> getKnownPlayers() {
        // Offline stats collector not ported to 1.7.10 yet — online only.
        List<PlayerData> known = new ArrayList<PlayerData>();
        getOnlinePlayers().forEach(player -> known.add(player.toData()));
        return known;
    }

    @Override
    public int getMaxPlayers() {
        return this.server != null ? this.server.getMaxPlayers() : 0;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        if (this.server == null) {
            return;
        }
        this.server.getCommandManager().executeCommand(this.server, command);
    }
}
