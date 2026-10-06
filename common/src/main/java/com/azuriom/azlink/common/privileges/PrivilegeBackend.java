package com.azuriom.azlink.common.privileges;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Applies shop privilege operations to a permission provider (LuckPerms on NeoForge).
 */
public interface PrivilegeBackend {

    ExecutionResult reconcile(ShopOperation operation, LoggerAdapter logger);

    ExecutionResult revoke(ShopOperation operation, LoggerAdapter logger);
}
