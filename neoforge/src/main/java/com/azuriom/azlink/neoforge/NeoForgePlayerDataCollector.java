package com.azuriom.azlink.neoforge;

import com.azuriom.azlink.common.data.PlayerData;
import com.azuriom.azlink.common.data.PlayerStats;
import com.azuriom.azlink.neoforge.command.NeoForgePlayer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link PlayerData} for every player that has joined this world (online + offline).
 */
public final class NeoForgePlayerDataCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger("azlink");
    private static volatile boolean errorLogged;

    private NeoForgePlayerDataCollector() {
    }

    public static List<PlayerData> collectAll(MinecraftServer server) {
        Map<UUID, PlayerData> byUuid = new HashMap<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData online = new NeoForgePlayer(player).toData();
            byUuid.put(online.getUuid(), new PlayerData(
                    online.getName(),
                    online.getUuid(),
                    online.getStats(),
                    true
            ));
        }

        Path statsDir = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        GameProfileCache profileCache = server.getProfileCache();

        Set<UUID> discovered = new HashSet<>(byUuid.keySet());
        discovered.addAll(listUuidsFromDir(statsDir, ".json"));
        discovered.addAll(listUuidsFromDir(playerDataDir, ".dat"));

        for (UUID uuid : discovered) {
            if (byUuid.containsKey(uuid)) {
                continue;
            }

            String name = resolveName(profileCache, uuid);
            PlayerStats stats = readOfflineStats(statsDir.resolve(uuid + ".json"), playerDataDir.resolve(uuid + ".dat"));
            byUuid.put(uuid, new PlayerData(name, uuid, stats, false));
        }

        return new ArrayList<>(byUuid.values());
    }

    public static PlayerStats collectLiveStats(ServerPlayer player) {
        try {
            ServerStatsCounter stats = player.getStats();

            long deaths = stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
            long playTime = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            long timeSinceDeath = stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
            long mobKills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            long playerKills = stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
            long walkCm = stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM));
            int xpLevel = player.experienceLevel;

            long blocksMined = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                blocksMined += stats.getValue(Stats.BLOCK_MINED.get(block));
            }

            long blocksPlaced = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof BlockItem) {
                    blocksPlaced += stats.getValue(Stats.ITEM_USED.get(item));
                }
            }

            return new PlayerStats(
                    deaths, playTime, timeSinceDeath, mobKills, playerKills,
                    xpLevel, blocksMined, blocksPlaced, walkCm
            );
        } catch (Throwable t) {
            logOnce("Unable to collect live player stats for AzLink export", t);
            return null;
        }
    }

    private static PlayerStats readOfflineStats(Path statsFile, Path playerDataFile) {
        try {
            long deaths = 0;
            long playTime = 0;
            long timeSinceDeath = 0;
            long mobKills = 0;
            long playerKills = 0;
            long walkCm = 0;
            long blocksMined = 0;
            long blocksPlaced = 0;
            int xpLevel = 0;

            if (Files.isRegularFile(statsFile)) {
                try (BufferedReader reader = Files.newBufferedReader(statsFile, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject stats = root.has("stats") && root.get("stats").isJsonObject()
                            ? root.getAsJsonObject("stats")
                            : root;

                    JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                    if (custom != null) {
                        deaths = getLong(custom, "minecraft:deaths");
                        playTime = getLong(custom, "minecraft:play_time");
                        timeSinceDeath = getLong(custom, "minecraft:time_since_death");
                        mobKills = getLong(custom, "minecraft:mob_kills");
                        playerKills = getLong(custom, "minecraft:player_kills");
                        walkCm = getLong(custom, "minecraft:walk_one_cm");
                    }

                    JsonObject mined = stats.getAsJsonObject("minecraft:mined");
                    if (mined != null) {
                        for (Map.Entry<String, JsonElement> entry : mined.entrySet()) {
                            blocksMined += entry.getValue().getAsLong();
                        }
                    }

                    JsonObject used = stats.getAsJsonObject("minecraft:used");
                    if (used != null) {
                        for (Map.Entry<String, JsonElement> entry : used.entrySet()) {
                            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                            if (id == null) {
                                continue;
                            }
                            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
                            if (item.isPresent() && item.get() instanceof BlockItem) {
                                blocksPlaced += entry.getValue().getAsLong();
                            }
                        }
                    }
                }
            }

            if (Files.isRegularFile(playerDataFile)) {
                CompoundTag tag = NbtIo.readCompressed(playerDataFile, NbtAccounter.unlimitedHeap());
                if (tag != null && tag.contains("XpLevel")) {
                    xpLevel = tag.getInt("XpLevel");
                }
            }

            return new PlayerStats(
                    deaths, playTime, timeSinceDeath, mobKills, playerKills,
                    xpLevel, blocksMined, blocksPlaced, walkCm
            );
        } catch (Throwable t) {
            logOnce("Unable to read offline player stats for AzLink export", t);
            return null;
        }
    }

    private static Set<UUID> listUuidsFromDir(Path dir, String extension) {
        Set<UUID> uuids = new HashSet<>();
        if (!Files.isDirectory(dir)) {
            return uuids;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + extension)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (!fileName.endsWith(extension)) {
                    continue;
                }
                String raw = fileName.substring(0, fileName.length() - extension.length());
                try {
                    uuids.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                    // skip non-uuid files
                }
            }
        } catch (Exception e) {
            logOnce("Unable to list player files in " + dir, e);
        }

        return uuids;
    }

    private static String resolveName(GameProfileCache cache, UUID uuid) {
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(uuid);
            if (profile.isPresent() && profile.get().getName() != null && !profile.get().getName().isEmpty()) {
                return profile.get().getName();
            }
        }
        return uuid.toString().toLowerCase(Locale.ROOT);
    }

    private static long getLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsLong() : 0L;
    }

    private static void logOnce(String message, Throwable t) {
        if (!errorLogged) {
            errorLogged = true;
            LOGGER.warn(message, t);
        }
    }
}
