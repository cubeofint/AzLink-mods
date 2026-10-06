package com.azuriom.azlink.common.privileges;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Body of {@code POST /api/shop/azlink/v1/luckperms/state}.
 */
public final class LuckPermsStateRequest {

    @SerializedName("protocol_version")
    private final int protocolVersion;

    @SerializedName("player_uuid")
    private final String playerUuid;

    @SerializedName("primary_group")
    private final String primaryGroup;

    @SerializedName("groups")
    private final List<String> groups;

    @SerializedName("source")
    private final String source;

    public LuckPermsStateRequest(PlayerGroupSnapshot snapshot) {
        this.protocolVersion = 1;
        this.playerUuid = snapshot.getPlayerId().toString();
        this.primaryGroup = snapshot.getPrimaryGroup();
        this.groups = snapshot.getGroups();
        this.source = "luckperms";
    }
}
