package com.azuriom.azlink.common.data;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class ServerData {

    private final PlatformData platform;
    private final String version;

    private final List<PlayerData> players;
    private final int maxPlayers;

    /**
     * All known players on this world (online and offline), with stats when available.
     * {@link #players} stays online-only so Azuriom "wait until online" still works.
     */
    @SerializedName("known_players")
    private final List<PlayerData> knownPlayers;

    private final SystemData system;
    private final WorldData worlds;

    private final boolean full;

    public ServerData(PlatformData platform, String version, List<PlayerData> players, int maxPlayers,
                      SystemData system, WorldData worlds, boolean full) {
        this(platform, version, players, maxPlayers, players, system, worlds, full);
    }

    public ServerData(PlatformData platform, String version, List<PlayerData> players, int maxPlayers,
                      List<PlayerData> knownPlayers, SystemData system, WorldData worlds, boolean full) {
        this.platform = platform;
        this.version = version;
        this.players = players;
        this.maxPlayers = maxPlayers;
        this.knownPlayers = knownPlayers != null ? knownPlayers : Collections.emptyList();
        this.system = system;
        this.worlds = worlds;
        this.full = full;
    }

    public PlatformData getPlatform() {
        return this.platform;
    }

    public String getVersion() {
        return this.version;
    }

    public List<PlayerData> getPlayers() {
        return this.players;
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public List<PlayerData> getKnownPlayers() {
        return this.knownPlayers;
    }

    public SystemData getSystem() {
        return this.system;
    }

    public WorldData getWorlds() {
        return this.worlds;
    }

    public boolean isFull() {
        return this.full;
    }
}
