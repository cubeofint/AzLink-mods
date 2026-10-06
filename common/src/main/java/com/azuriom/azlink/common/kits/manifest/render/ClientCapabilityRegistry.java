package com.azuriom.azlink.common.kits.manifest.render;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks client AzLink capabilities per connected player. No credentials stored.
 */
public final class ClientCapabilityRegistry {

    private final Map<UUID, ClientCapabilities> byPlayer = new ConcurrentHashMap<UUID, ClientCapabilities>();

    public void register(UUID playerId, ClientCapabilities capabilities) {
        if (playerId == null || capabilities == null) {
            return;
        }
        this.byPlayer.put(playerId, capabilities);
    }

    public void unregister(UUID playerId) {
        if (playerId != null) {
            this.byPlayer.remove(playerId);
        }
    }

    public Optional<ClientCapabilities> get(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.byPlayer.get(playerId));
    }

    public boolean isCompatibleRenderer(UUID playerId) {
        return get(playerId).map(ClientCapabilities::isCompatibleWithServer).orElse(false);
    }

    public int size() {
        return this.byPlayer.size();
    }
}
