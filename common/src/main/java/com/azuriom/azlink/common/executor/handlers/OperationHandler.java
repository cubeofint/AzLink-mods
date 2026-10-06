package com.azuriom.azlink.common.executor.handlers;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;

/**
 * Semantic operation handler. Mock stage validates + logs desired state only.
 */
public interface OperationHandler {

    ExecutionResult apply(ShopOperation operation, LoggerAdapter logger);
}
