package com.azuriom.azlink.common.executor.ledger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot of LuckPerms nodes last written for one entitlement on one player.
 * Revoke matches this group, meta entries, permission nodes and expiry exactly.
 */
public final class GrantedNodes {

    private final String entitlementKey;
    private final long version;
    private final String group;
    private final List<String> meta;
    private final List<String> permissions;
    private final Instant expiresAt;
    private final Instant updatedAt;

    public GrantedNodes(String entitlementKey, long version, String group,
                        List<String> meta, List<String> permissions,
                        Instant expiresAt, Instant updatedAt) {
        this.entitlementKey = entitlementKey;
        this.version = version;
        this.group = group;
        this.meta = copyList(meta);
        this.permissions = copyList(permissions);
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }

    public static GrantedNodes versionOnly(String entitlementKey, long version,
                                           String group, Instant expiresAt) {
        return new GrantedNodes(entitlementKey, version, group,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                expiresAt, Instant.now());
    }

    public String getEntitlementKey() {
        return this.entitlementKey;
    }

    public long getVersion() {
        return this.version;
    }

    public String getGroup() {
        return this.group;
    }

    public List<String> getMeta() {
        return this.meta;
    }

    public List<String> getPermissions() {
        return this.permissions;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
