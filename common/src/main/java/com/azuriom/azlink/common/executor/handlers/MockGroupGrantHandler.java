package com.azuriom.azlink.common.executor.handlers;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Mock GROUP_GRANT — no LuckPerms mutation. Version ledger is updated by the executor.
 */
public class MockGroupGrantHandler implements OperationHandler {

    @Override
    public ExecutionResult apply(ShopOperation operation, LoggerAdapter logger) {
        logger.info("[SemanticExecutor][MOCK] GROUP_GRANT desired state"
                + " operation_id=" + operation.getOperationId()
                + " player=" + operation.getPlayerUuid()
                + " group=" + operation.getGroup()
                + " entitlement_key=" + operation.getEntitlementKey()
                + " version=" + operation.getEntitlementVersion()
                + " expires_at=" + operation.getAbsoluteExpiresAt());
        return ExecutionResult.of(OperationResultCode.SUCCEEDED, operation.getEntitlementVersion(),
                "mock group grant applied");
    }
}
