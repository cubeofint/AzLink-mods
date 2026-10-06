package com.azuriom.azlink.common.kits.manifest.render;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Uses preferred player only when online + capability-compatible. Never random fallback.
 */
public final class PreferredPlayerRenderWorkerSelector implements RenderWorkerSelector {

    private final ClientCapabilityRegistry capabilities;
    private final Predicate<UUID> isOnline;

    public PreferredPlayerRenderWorkerSelector(ClientCapabilityRegistry capabilities,
                                               Predicate<UUID> isOnline) {
        this.capabilities = capabilities;
        this.isOnline = isOnline;
    }

    @Override
    public Optional<UUID> select(UUID preferredPlayer) {
        if (preferredPlayer == null) {
            return Optional.empty();
        }
        if (!this.isOnline.test(preferredPlayer)) {
            return Optional.empty();
        }
        if (!this.capabilities.isCompatibleRenderer(preferredPlayer)) {
            return Optional.empty();
        }
        return Optional.of(preferredPlayer);
    }
}
