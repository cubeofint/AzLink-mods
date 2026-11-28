package com.azuriom.azlink.common.chunks;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.executor.handlers.OperationHandler;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlatformChunkLoaders {
    private PlatformChunkLoaders() {}

    public static OperationHandler handler(AzLinkPlugin plugin, ChunkLoaderBackend backend) {
        return (operation, logger) -> {
            final ChunkLoaderPurchase purchase;
            try { purchase = ChunkLoaderPurchase.parse(operation); }
            catch (IllegalArgumentException invalid) {
                return ExecutionResult.of(OperationResultCode.FAILED, invalid.getMessage());
            }
            AtomicInteger state = new AtomicInteger(0);
            CompletableFuture<ExecutionResult> completion = new CompletableFuture<ExecutionResult>();
            plugin.getScheduler().executeSync(() -> {
                if (!state.compareAndSet(0, 1)) return;
                try {
                    backend.credit(purchase);
                    completion.complete(ExecutionResult.of(OperationResultCode.SUCCEEDED, null,
                            "chunk-loader receipt durably saved"));
                } catch (UnsupportedOperationException unavailable) {
                    completion.complete(ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, unavailable.getMessage()));
                } catch (IllegalArgumentException invalid) {
                    completion.complete(ExecutionResult.of(OperationResultCode.FAILED, invalid.getMessage()));
                } catch (Throwable unknown) {
                    completion.completeExceptionally(unknown);
                }
            });
            try { return completion.get(15, TimeUnit.SECONDS); }
            catch (TimeoutException queued) {
                if (state.compareAndSet(0, 2)) return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED,
                        "Server thread did not start the chunk-loader credit");
                try { return completion.get(120, TimeUnit.SECONDS); }
                catch (Exception unknown) {
                    if (unknown instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new IllegalStateException("Chunk-loader credit outcome unknown", unknown);
                }
            } catch (Exception unknown) {
                if (unknown instanceof InterruptedException) Thread.currentThread().interrupt();
                if (state.compareAndSet(0, 2)) return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED,
                        "Chunk-loader credit was not started");
                throw new IllegalStateException("Chunk-loader credit outcome unknown", unknown);
            }
        };
    }
}
