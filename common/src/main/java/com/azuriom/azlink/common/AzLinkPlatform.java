package com.azuriom.azlink.common;

import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.data.PlatformData;
import com.azuriom.azlink.common.data.PlayerData;
import com.azuriom.azlink.common.data.WorldData;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.platform.PlatformInfo;
import com.azuriom.azlink.common.platform.PlatformType;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface AzLinkPlatform {

    AzLinkPlugin getPlugin();

    LoggerAdapter getLoggerAdapter();

    SchedulerAdapter getSchedulerAdapter();

    PlatformType getPlatformType();

    PlatformInfo getPlatformInfo();

    String getPluginVersion();

    Path getDataDirectory();

    Stream<CommandSender> getOnlinePlayers();

    /**
     * All known players on this world (online + offline). Defaults to online players only.
     */
    default List<PlayerData> getKnownPlayers() {
        return getOnlinePlayers()
                .map(CommandSender::toData)
                .collect(Collectors.toList());
    }

    int getMaxPlayers();

    default Optional<WorldData> getWorldData() {
        return Optional.empty();
    }

    void dispatchConsoleCommand(String command);

    /**
     * Website linking / site HTTP (Azuriom-Link-Token) is allowed only on dedicated servers.
     * Client and integrated/singleplayer must return {@code false}.
     */
    default boolean isDedicatedServerSiteLinkAllowed() {
        return true;
    }

    default PlatformData getPlatformData() {
        return new PlatformData(getPlatformType(), getPlatformInfo());
    }

    default void saveResource(Path target, String name) throws IOException {
        if (Files.exists(target)) {
            return;
        }

        if (!Files.isDirectory(target.getParent())) {
            Files.createDirectory(target.getParent());
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            Files.copy(in, target);
        }
    }

    default void prepareDataAsync() {
    }
}
