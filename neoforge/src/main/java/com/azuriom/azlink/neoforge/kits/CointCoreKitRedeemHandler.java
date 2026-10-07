package com.azuriom.azlink.neoforge.kits;

import com.azuriom.azlink.common.executor.handlers.OperationHandler;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.kits.manifest.KitDeliveryKeys;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import dev.ftb.mods.ftbessentials.kit.Kit;
import dev.ftb.mods.ftbessentials.kit.KitManager;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Real KIT_REDEEM: adds one cointcore kit credit for the FTB Essentials kit whose delivery key
 * matches {@code kit_key}. Credits are stored by UUID, so offline players get them too and claim
 * with {@code /kit <name>}. Exactly-once is guaranteed by the executor's operation ledger.
 */
public final class CointCoreKitRedeemHandler implements OperationHandler {
    private static final String CREDIT_SERVICE = "com.mawlee.cointcore.kit.KitCreditService";

    private static final int NEW = 0;
    private static final int STARTED = 1;
    private static final int ABANDONED = 2;
    private static final long QUEUE_TIMEOUT_SECONDS = 15;
    private static final long RUN_TIMEOUT_SECONDS = 120;

    private final Supplier<MinecraftServer> server;

    public CointCoreKitRedeemHandler(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    public static boolean isAvailable() {
        try {
            Class.forName(CREDIT_SERVICE);
            Class.forName("dev.ftb.mods.ftbessentials.kit.KitManager");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public ExecutionResult apply(ShopOperation operation, LoggerAdapter logger) {
        UUID playerId = operation.getPlayerUuid();
        String key = operation.getSemanticKey();
        MinecraftServer mc = this.server.get();
        if (playerId == null || key == null) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing player or kit_key");
        }
        if (mc == null) {
            return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, "server not ready");
        }

        // NEW -> STARTED (main thread runs the credit) or NEW -> ABANDONED (we gave up waiting).
        // Whoever wins the CAS decides: an abandoned task never touches credits, so a timeout
        // before the task started is a clean retryable failure, not an uncertain one.
        AtomicInteger state = new AtomicInteger(NEW);
        CompletableFuture<ExecutionResult> future = new CompletableFuture<>();
        mc.execute(() -> {
            if (!state.compareAndSet(NEW, STARTED)) {
                logger.warn("[SemanticExecutor] KIT_REDEEM skipped (abandoned after timeout) operation_id="
                        + operation.getOperationId());
                return;
            }
            try {
                Optional<String> kitName = findKit(key);
                if (kitName.isEmpty()) {
                    // Nothing changed: safe to retry after the kit is (re)created.
                    future.complete(ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, "unknown kit " + key));
                    return;
                }
                Method add = Class.forName(CREDIT_SERVICE)
                        .getMethod("addCredits", MinecraftServer.class, UUID.class, String.class, int.class);
                Object total = add.invoke(null, mc, playerId, kitName.get(), 1);
                logger.info("[SemanticExecutor] KIT_REDEEM credited kit=" + kitName.get() + " player=" + playerId
                        + " operation_id=" + operation.getOperationId() + " credits=" + total);
                future.complete(ExecutionResult.of(OperationResultCode.SUCCEEDED, null, "kit credit added"));
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                future.complete(ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, "cointcore kit credits unavailable"));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try {
            return future.get(QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            if (state.compareAndSet(NEW, ABANDONED)) {
                // Main thread never picked the task up (e.g. server still starting): nothing written.
                return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, "server thread busy, kit not credited");
            }
            // Task is running on the main thread: wait for its real outcome.
            try {
                return future.get(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e2) {
                throw new IllegalStateException("kit credit result unknown: " + e2, e2);
            }
        } catch (Exception e) {
            // Unknown whether the credit was written: never report retryable.
            throw new IllegalStateException("kit credit result unknown: " + e, e);
        }
    }

    private static Optional<String> findKit(String key) {
        for (Kit kit : KitManager.getInstance().allKits()) {
            Optional<String> normalized = KitDeliveryKeys.normalize(kit.getKitName());
            if (normalized.isPresent() && normalized.get().equals(key)) {
                return Optional.of(kit.getKitName());
            }
        }
        return Optional.empty();
    }
}
