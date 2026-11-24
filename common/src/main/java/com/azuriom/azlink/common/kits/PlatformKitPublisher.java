package com.azuriom.azlink.common.kits;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.executor.handlers.OperationHandler;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.kits.manifest.KitDeliveryKeys;
import com.azuriom.azlink.common.kits.manifest.KitManifestSyncResult;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** The same publication/operation protocol for Forge 1.20.1 and Forge 1.7.10. */
public final class PlatformKitPublisher {
    private final AzLinkPlugin plugin;
    private final PlatformKitCatalog catalog;

    public PlatformKitPublisher(AzLinkPlugin plugin, PlatformKitCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    public List<String> names() {
        try { return this.catalog.names(); }
        catch (IllegalStateException unavailable) { return Collections.emptyList(); }
    }

    /** Called on the server thread: the snapshot is complete before any asynchronous work starts. */
    public CompletableFuture<KitManifestSyncResult> publish(String name, UUID renderer) {
        try {
            if (!this.plugin.isConfigured() || !this.plugin.isDedicatedServerSiteLinkAllowed()) {
                throw new IllegalStateException("Configure AzLink on a dedicated server first");
            }
            PlatformKitCatalog.Definition kit = this.catalog.read(name);
            String key = KitDeliveryKeys.normalize(kit.name).orElseThrow(() -> new IllegalArgumentException("Invalid kit delivery key"));
            if (kit.items.isEmpty()) throw new IllegalArgumentException("Kit is empty");
            int version = this.plugin.getKitManifestVersionStore().allocateNext(key);
            this.plugin.getLogger().info("[KitManifest] mirror+publish scheduled delivery_key=" + key
                    + " version=" + version + " items=" + kit.items.size());
            CompletableFuture<KitManifestSyncResult> result = this.plugin.getKitManifestSyncCoordinator()
                    .publish(key, version, kit.items, renderer, kit.name, kit.cooldownSeconds, true);
            result.whenComplete((outcome, error) -> {
                if (error != null || outcome == null || !outcome.isSuccess()) {
                    this.plugin.getLogger().warn("[KitManifest] publish failed delivery_key=" + key
                            + (error == null ? " (see upload/renderer diagnostics)" : ": " + error.getMessage()));
                }
            });
            return result;
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(KitManifestSyncResult.fail(error));
        }
    }

    public void saved(String name) {
        if (this.plugin.isConfigured() && this.plugin.isDedicatedServerSiteLinkAllowed()) {
            UUID renderer = this.plugin.getPlatform().getOnlinePlayers().filter(p -> p.hasPermission("azlink.admin"))
                    .map(p -> p.getUuid()).findFirst().orElse(null);
            publish(name, renderer);
        }
    }

    public void removed(String name) {
        if (!this.plugin.isConfigured() || !this.plugin.isDedicatedServerSiteLinkAllowed()) return;
        KitDeliveryKeys.normalize(name).ifPresent(key -> this.plugin.getKitManifestSyncCoordinator().retireOnSite(key)
                .whenComplete((result, error) -> {
                    if (error != null || result == null || !result.isSuccess())
                        this.plugin.getLogger().warn("[KitManifest] retire failed delivery_key=" + key);
                }));
    }

    public OperationHandler creditHandler(boolean singleRedeem) {
        return (operation, logger) -> {
            UUID player = operation.getPlayerUuid();
            String key = singleRedeem ? operation.getSemanticKey() : operation.getKit();
            Integer quantity = singleRedeem ? Integer.valueOf(1) : operation.getQty();
            if (player == null || key == null || quantity == null || quantity < 1)
                return ExecutionResult.of(OperationResultCode.FAILED, "Invalid kit credit payload");
            AtomicInteger state = new AtomicInteger(0);
            CompletableFuture<ExecutionResult> completion = new CompletableFuture<ExecutionResult>();
            this.plugin.getScheduler().executeSync(() -> {
                if (!state.compareAndSet(0, 1)) return;
                try {
                    int balance = this.catalog.credit(player, key, quantity, operation.getOperationId());
                    logger.info("[SemanticExecutor] credited kit=" + key + " qty=" + quantity + " player=" + player
                            + " operation_id=" + operation.getOperationId() + " persisted_result=" + balance);
                    completion.complete(ExecutionResult.of(OperationResultCode.SUCCEEDED, null, "kit credit saved"));
                } catch (UnsupportedOperationException unavailable) {
                    completion.complete(ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, unavailable.getMessage()));
                } catch (IllegalArgumentException invalid) {
                    completion.complete(ExecutionResult.of(OperationResultCode.FAILED, invalid.getMessage()));
                } catch (Throwable unknown) {
                    completion.completeExceptionally(unknown);
                }
            });
            try {
                return completion.get(15, TimeUnit.SECONDS);
            } catch (TimeoutException queued) {
                if (state.compareAndSet(0, 2))
                    return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, "Server thread did not start the credit");
                // A started mutation is never reported as safe to repeat without its real outcome.
                try { return completion.get(120, TimeUnit.SECONDS); }
                catch (Exception unknown) {
                    if (unknown instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new IllegalStateException("Kit credit result unknown", unknown);
                }
            } catch (Exception unknown) {
                if (unknown instanceof InterruptedException) Thread.currentThread().interrupt();
                if (state.compareAndSet(0, 2))
                    return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, "Credit was not started");
                throw new IllegalStateException("Kit credit result unknown", unknown);
            }
        };
    }
}
