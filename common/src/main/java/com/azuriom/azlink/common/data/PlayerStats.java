package com.azuriom.azlink.common.data;

import com.google.gson.annotations.SerializedName;

/**
 * Vanilla Minecraft player statistics exported in {@code players[].stats} of POST /api/azlink.
 * <ul>
 *   <li>{@code play_time}, {@code time_since_death} — ticks (20 ticks = 1 second)</li>
 *   <li>{@code walk_cm} — centimetres walked ({@code WALK_ONE_CM})</li>
 * </ul>
 */
public class PlayerStats {

    private final long deaths;

    @SerializedName("play_time")
    private final long playTime;

    @SerializedName("time_since_death")
    private final long timeSinceDeath;

    @SerializedName("mob_kills")
    private final long mobKills;

    @SerializedName("player_kills")
    private final long playerKills;

    @SerializedName("xp_level")
    private final int xpLevel;

    @SerializedName("blocks_mined")
    private final long blocksMined;

    @SerializedName("blocks_placed")
    private final long blocksPlaced;

    @SerializedName("walk_cm")
    private final long walkCm;

    public PlayerStats(long deaths, long playTime, long timeSinceDeath, long mobKills, long playerKills,
                       int xpLevel, long blocksMined, long blocksPlaced, long walkCm) {
        this.deaths = deaths;
        this.playTime = playTime;
        this.timeSinceDeath = timeSinceDeath;
        this.mobKills = mobKills;
        this.playerKills = playerKills;
        this.xpLevel = xpLevel;
        this.blocksMined = blocksMined;
        this.blocksPlaced = blocksPlaced;
        this.walkCm = walkCm;
    }

    public long getDeaths() {
        return this.deaths;
    }

    public long getPlayTime() {
        return this.playTime;
    }

    public long getTimeSinceDeath() {
        return this.timeSinceDeath;
    }

    public long getMobKills() {
        return this.mobKills;
    }

    public long getPlayerKills() {
        return this.playerKills;
    }

    public int getXpLevel() {
        return this.xpLevel;
    }

    public long getBlocksMined() {
        return this.blocksMined;
    }

    public long getBlocksPlaced() {
        return this.blocksPlaced;
    }

    public long getWalkCm() {
        return this.walkCm;
    }
}
