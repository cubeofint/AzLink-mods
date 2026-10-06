package com.azuriom.azlink.common.data;

import java.util.UUID;

public class PlayerData {

    private final String name;
    private final UUID uuid;
    private final PlayerStats stats;
    private final boolean online;

    public PlayerData(String name, UUID uuid) {
        this(name, uuid, null, true);
    }

    public PlayerData(String name, UUID uuid, PlayerStats stats) {
        this(name, uuid, stats, true);
    }

    public PlayerData(String name, UUID uuid, PlayerStats stats, boolean online) {
        this.name = name;
        this.uuid = uuid;
        this.stats = stats;
        this.online = online;
    }

    public String getName() {
        return this.name;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public PlayerStats getStats() {
        return this.stats;
    }

    public boolean isOnline() {
        return this.online;
    }
}
