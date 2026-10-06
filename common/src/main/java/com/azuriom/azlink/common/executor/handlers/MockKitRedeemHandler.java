package com.azuriom.azlink.common.executor.handlers;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Mock KIT_REDEEM — ledger guarantees at most one physical apply per operation_id.
 */
public class MockKitRedeemHandler implements OperationHandler {

    @Override
    public ExecutionResult apply(ShopOperation operation, LoggerAdapter logger) {
        logger.info("[SemanticExecutor][MOCK] KIT_REDEEM desired state"
                + " operation_id=" + operation.getOperationId()
                + " player=" + operation.getPlayerUuid()
                + " redemption_id=" + operation.getRedemptionId()
                + " semantic_key=" + operation.getSemanticKey()
                + " kit_key=" + operation.getSemanticKey());
        return ExecutionResult.of(OperationResultCode.SUCCEEDED, null, "mock kit redeem applied");
    }
}
