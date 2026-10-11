package com.azuriom.azlink.common.privileges;

import com.azuriom.azlink.common.executor.ledger.EntitlementVersionLedger;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Applies shop privilege operations to a permission provider (LuckPerms on NeoForge).
 */
public interface PrivilegeBackend {

    ExecutionResult reconcile(ShopOperation operation, LoggerAdapter logger);

    ExecutionResult revoke(ShopOperation operation, LoggerAdapter logger);

    ExecutionResult grantGroup(ShopOperation operation, LoggerAdapter logger);

    ExecutionResult revokeGroup(ShopOperation operation, LoggerAdapter logger);

    void bindLedger(EntitlementVersionLedger ledger);
}
