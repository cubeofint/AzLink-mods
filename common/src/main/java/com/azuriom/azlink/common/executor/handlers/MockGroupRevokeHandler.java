package com.azuriom.azlink.common.executor.handlers;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Mock GROUP_REVOKE — version-aware, no real permission removal.
 */
public class MockGroupRevokeHandler implements OperationHandler {

    @Override
    public ExecutionResult apply(ShopOperation operation, LoggerAdapter logger) {
        logger.info("[SemanticExecutor][MOCK] GROUP_REVOKE desired state"
                + " operation_id=" + operation.getOperationId()
                + " player=" + operation.getPlayerUuid()
                + " entitlement_key=" + operation.getEntitlementKey()
                + " version=" + operation.getEntitlementVersion());
        return ExecutionResult.of(OperationResultCode.SUCCEEDED, operation.getEntitlementVersion(),
                "mock group revoke applied");
    }
}
