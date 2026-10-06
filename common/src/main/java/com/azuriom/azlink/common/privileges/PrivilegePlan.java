package com.azuriom.azlink.common.privileges;

import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Desired LuckPerms state for one shop entitlement.
 * The entitlement key becomes a group. Each true capability becomes a permission node.
 * Nodes are tagged with {@link #ENTITLEMENT_CONTEXT} so revoke only removes this purchase.
 */
public final class PrivilegePlan {

    public static final String ENTITLEMENT_CONTEXT = "azlink-entitlement";
    public static final String CAPABILITY_PREFIX = "azlink.capability.";

    private final String entitlementId;
    private final String groupName;
    private final Instant expiresAt;
    private final boolean expired;
    private final List<String> permissions;
    private final boolean clearOnly;
    private final String error;

    private PrivilegePlan(String entitlementId, String groupName, Instant expiresAt, boolean expired,
                          List<String> permissions, boolean clearOnly, String error) {
        this.entitlementId = entitlementId;
        this.groupName = groupName;
        this.expiresAt = expiresAt;
        this.expired = expired;
        this.permissions = permissions;
        this.clearOnly = clearOnly;
        this.error = error;
    }

    public static PrivilegePlan reconcile(ShopOperation operation) {
        String entitlementId = operation.getEntitlementId();
        String groupName = groupName(operation.getSemanticKey());
        if (groupName == null) {
            return invalid("invalid entitlement key for luckperms group");
        }

        Instant expiresAt = null;
        boolean expired = false;
        String rawExpiry = operation.getEntitlementExpiresAt();
        if (rawExpiry != null && !rawExpiry.trim().isEmpty()) {
            try {
                expiresAt = Instant.parse(rawExpiry.trim());
            } catch (DateTimeParseException e) {
                return invalid("invalid entitlement.expires_at");
            }
            expired = !expiresAt.isAfter(Instant.now());
        }

        List<String> permissions = new ArrayList<String>();
        if (!expired) {
            String capabilityError = readCapabilities(operation.getCapabilities(), permissions);
            if (capabilityError != null) {
                return invalid(capabilityError);
            }
        }
        return new PrivilegePlan(entitlementId, groupName, expiresAt, expired, permissions, expired, null);
    }

    public static PrivilegePlan revoke(ShopOperation operation) {
        String entitlementId = operation.getEntitlementId();
        if (entitlementId == null || entitlementId.trim().isEmpty()) {
            return invalid("missing entitlement.id");
        }
        return new PrivilegePlan(entitlementId.trim(), groupName(operation.getSemanticKey()),
                null, false, Collections.<String>emptyList(), true, null);
    }

    public String getEntitlementId() {
        return this.entitlementId;
    }

    public String getGroupName() {
        return this.groupName;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isExpired() {
        return this.expired;
    }

    public List<String> getPermissions() {
        return this.permissions;
    }

    public String getError() {
        return this.error;
    }

    public boolean isClearOnly() {
        return this.clearOnly;
    }

    /**
     * LuckPerms group names: lowercase, 1–36 chars, {@code [a-z0-9._-]}.
     */
    public static String groupName(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 36) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!ok) {
                return null;
            }
        }
        return normalized;
    }

    private static PrivilegePlan invalid(String error) {
        return new PrivilegePlan(null, null, null, false, Collections.<String>emptyList(), false, error);
    }

    private static String readCapabilities(JsonObject capabilities, List<String> permissions) {
        if (capabilities == null) {
            return "missing capabilities";
        }
        for (Map.Entry<String, JsonElement> entry : capabilities.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                return "empty capability key";
            }
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (!value.isJsonPrimitive()) {
                return "unsupported capability value: " + key;
            }
            if (value.getAsJsonPrimitive().isBoolean()) {
                if (value.getAsBoolean()) {
                    permissions.add(CAPABILITY_PREFIX + key);
                }
                continue;
            }
            if (value.getAsJsonPrimitive().isString()) {
                String raw = value.getAsString().trim();
                if (raw.isEmpty() || raw.length() > 200 || raw.indexOf(' ') >= 0) {
                    return "invalid permission node for capability: " + key;
                }
                permissions.add(raw);
                continue;
            }
            return "unsupported capability value: " + key;
        }
        return null;
    }
}
