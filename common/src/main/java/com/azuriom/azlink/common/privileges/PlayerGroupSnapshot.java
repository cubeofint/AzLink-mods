package com.azuriom.azlink.common.privileges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * LuckPerms groups observed on the server, pushed to the Azuriom site.
 */
public final class PlayerGroupSnapshot {

    private final UUID playerId;
    private final String primaryGroup;
    private final List<String> groups;

    public PlayerGroupSnapshot(UUID playerId, String primaryGroup, List<String> groups) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.primaryGroup = primaryGroup == null ? "default" : primaryGroup;
        List<String> copy = new ArrayList<String>();
        if (groups != null) {
            for (String group : groups) {
                if (group != null && !group.trim().isEmpty() && !copy.contains(group)) {
                    copy.add(group);
                }
            }
        }
        Collections.sort(copy);
        this.groups = Collections.unmodifiableList(copy);
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getPrimaryGroup() {
        return this.primaryGroup;
    }

    public List<String> getGroups() {
        return this.groups;
    }

    public String fingerprint() {
        return this.primaryGroup + "|" + String.join(",", this.groups);
    }
}
