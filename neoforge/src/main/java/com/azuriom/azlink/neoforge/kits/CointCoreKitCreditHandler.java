package com.azuriom.azlink.neoforge.kits;

import com.azuriom.azlink.common.executor.handlers.OperationHandler;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Real KIT_CREDIT: adds paid kit credits in cointcore by UUID (offline-safe).
 * Credits never expire. Requires {@code KitCreditService.addCredits(UUID, String, int)}.
 */
public final class CointCoreKitCreditHandler implements OperationHandler {
    private static final String CREDIT_SERVICE = "com.mawlee.cointcore.kit.KitCreditService";

    public static boolean isAvailable() {
        try {
            Class.forName(CREDIT_SERVICE).getMethod("addCredits", UUID.class, String.class, int.class);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            return false;
        }
    }

    @Override
    public ExecutionResult apply(ShopOperation operation, LoggerAdapter logger) {
        UUID playerId = operation.getPlayerUuid();
        String kit = operation.getKit();
        Integer qty = operation.getQty();
        if (playerId == null || kit == null || kit.trim().isEmpty()) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing player or kit");
        }
        if (qty == null || qty.intValue() < 1) {
            return ExecutionResult.of(OperationResultCode.FAILED, "invalid qty");
        }

        try {
            Method add = Class.forName(CREDIT_SERVICE)
                    .getMethod("addCredits", UUID.class, String.class, int.class);
            Object total = add.invoke(null, playerId, kit.trim(), qty.intValue());
            logger.info("[SemanticExecutor] KIT_CREDIT credited kit=" + kit.trim()
                    + " qty=" + qty
                    + " player=" + playerId
                    + " operation_id=" + operation.getOperationId()
                    + " credits=" + total);
            return ExecutionResult.of(OperationResultCode.SUCCEEDED, null, "kit credit added");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED,
                    "cointcore KitCreditService.addCredits(UUID, String, int) unavailable");
        } catch (Throwable t) {
            throw new IllegalStateException("kit credit result unknown: " + t, t);
        }
    }
}
