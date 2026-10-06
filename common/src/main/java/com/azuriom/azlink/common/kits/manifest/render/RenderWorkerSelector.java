package com.azuriom.azlink.common.kits.manifest.render;

import java.util.UUID;

/**
 * Selects which connected admin client may act as render worker.
 * Does not auto-pick random players.
 */
public interface RenderWorkerSelector {

    /**
     * @param preferredPlayer preferred online player UUID (may be null)
     * @return selected worker UUID, or empty if none suitable
     */
    java.util.Optional<UUID> select(UUID preferredPlayer);
}
