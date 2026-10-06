package com.azuriom.azlink.common.executor.handlers;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Mock PRIVILEGE_RECONCILE — no LuckPerms/ServerUtilities mutation.
 */
public class MockPrivilegeReconcileHandler implements OperationHandler {

    @Override
    public ExecutionResult apply(ShopOperation operation, LoggerAdapter logger) {
        logger.info("[SemanticExecutor][MOCK] PRIVILEGE_RECONCILE desired state"
                + " operation_id=" + operation.getOperationId()
                + " player=" + operation.getPlayerUuid()
                + " entitlement_id=" + operation.getEntitlementId()
                + " entitlement_version=" + operation.getEntitlementVersion()
                + " semantic_key=" + operation.getSemanticKey()
                + " capability_keys=" + (operation.getCapabilities() == null
                ? "[]" : operation.getCapabilities().keySet().toString()));
        return ExecutionResult.of(OperationResultCode.SUCCEEDED, operation.getEntitlementVersion(),
                "mock privilege reconcile applied");
    }
}
