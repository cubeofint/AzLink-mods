package com.azuriom.azlink.common.executor.handlers;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Mock KIT_CREDIT — ledger guarantees at most one physical apply per operation_id.
 * Kit credits are paid items and never expire.
 */
public class MockKitCreditHandler implements OperationHandler {

    @Override
    public ExecutionResult apply(ShopOperation operation, LoggerAdapter logger) {
        logger.info("[SemanticExecutor][MOCK] KIT_CREDIT desired state"
                + " operation_id=" + operation.getOperationId()
                + " player=" + operation.getPlayerUuid()
                + " kit=" + operation.getKit()
                + " qty=" + operation.getQty());
        return ExecutionResult.of(OperationResultCode.SUCCEEDED, null, "mock kit credit applied");
    }
}
