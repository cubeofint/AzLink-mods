package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.executor.client.OperationsApiClient;
import com.azuriom.azlink.common.executor.model.AckRequest;
import com.azuriom.azlink.common.executor.model.PollRequest;
import com.azuriom.azlink.common.executor.model.PollResponse;
import com.azuriom.azlink.common.executor.model.ShopOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Periodically polls shop operations and ACKs results.
 */
public class SemanticPollTask implements Runnable {

    private final AzLinkPlugin plugin;
    private final SemanticExecutor executor;
    private final OperationsApiClient client;

    public SemanticPollTask(AzLinkPlugin plugin, SemanticExecutor executor, OperationsApiClient client) {
        this.plugin = plugin;
        this.executor = executor;
        this.client = client;
    }

    @Override
    public void run() {
        pollOnce().exceptionally(ex -> {
            this.plugin.getLogger().warn("[SemanticExecutor] poll cycle failed: " + ex.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> pollOnce() {
        if (!this.plugin.isConfigured() || !this.executor.getConfig().isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        ExecutorConfig config = this.executor.getConfig();
        PollRequest request = new PollRequest(
                config.getProtocolVersion(),
                config.getExecutorVersion(),
                new ArrayList<String>(config.getSupportedOperationTypes()),
                new ArrayList<String>(config.getSupportedCapabilities()),
                config.getMaxBatch()
        );

        return this.client.poll(request).thenCompose(pollResult -> {
            if (pollResult.isUnauthorized()) {
                this.plugin.getLogger().warn("[SemanticExecutor] poll auth failure HTTP "
                        + pollResult.getHttpStatus());
                return CompletableFuture.completedFuture(null);
            }
            if (!pollResult.isSuccess()) {
                int status = pollResult.getHttpStatus();
                String body = pollResult.getErrorBody();
                String detail = pollResult.getError() != null
                        ? pollResult.getError().getMessage()
                        : ("HTTP " + status + (body != null && !body.isEmpty() ? " " + body : ""));
                if (status == 422) {
                    this.plugin.getLogger().warn("[SemanticExecutor] poll validation error: " + detail);
                } else if (status >= 500) {
                    this.plugin.getLogger().warn("[SemanticExecutor] poll server error (retry next cycle): "
                            + detail);
                } else {
                    this.plugin.getLogger().warn("[SemanticExecutor] poll failed: " + detail);
                }
                return CompletableFuture.completedFuture(null);
            }

            PollResponse response = pollResult.getBody();
            Integer protocolVersion = response.getProtocolVersion();
            if (protocolVersion == null || protocolVersion != config.getProtocolVersion()) {
                this.plugin.getLogger().warn("[SemanticExecutor] unsupported protocol_version="
                        + protocolVersion + " expected=" + config.getProtocolVersion()
                        + " — batch skipped");
                return CompletableFuture.completedFuture(null);
            }

            List<ShopOperation> operations = response.getOperations();
            if (operations.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            this.plugin.getLogger().info("[SemanticExecutor] polled " + operations.size()
                    + " operation(s) protocol=" + protocolVersion);

            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (ShopOperation operation : operations) {
                chain = chain.thenCompose(v -> processAndAck(operation));
            }
            return chain;
        });
    }

    private CompletableFuture<Void> processAndAck(ShopOperation operation) {
        SemanticExecutor.ProcessedOperation processed;
        try {
            processed = this.executor.process(operation);
        } catch (RuntimeException e) {
            this.plugin.getLogger().error("[SemanticExecutor] unexpected process failure operation_id="
                    + operation.getOperationId(), e);
            return sendAck(this.executor.uncertainAck(operation, e.getMessage()));
        }

        return sendAck(processed.getAckRequest());
    }

    private CompletableFuture<Void> sendAck(AckRequest ack) {
        return this.client.ack(ack).thenAccept(ackResult -> {
            if (ackResult.isSuccess()) {
                this.plugin.getLogger().info("[SemanticExecutor] ACK ok operation_id=" + ack.getOperationId()
                        + " status=" + ack.getStatus()
                        + " result_code=" + ack.getResultCode());
            } else {
                int status = ackResult.getHttpStatus();
                String body = ackResult.getErrorBody();
                String detail = ackResult.getError() != null
                        ? ackResult.getError().getMessage()
                        : ("HTTP " + status + (body != null && !body.isEmpty() ? " " + body : ""));
                if (status == 422) {
                    this.plugin.getLogger().warn("[SemanticExecutor] ACK validation error operation_id="
                            + ack.getOperationId() + " detail=" + detail);
                } else if (status == 401 || status == 403) {
                    this.plugin.getLogger().warn("[SemanticExecutor] ACK auth failure operation_id="
                            + ack.getOperationId() + " HTTP " + status);
                } else {
                    this.plugin.getLogger().warn("[SemanticExecutor] ACK failed operation_id="
                            + ack.getOperationId()
                            + " status=" + ack.getStatus()
                            + " detail=" + detail);
                }
            }
        });
    }
}
