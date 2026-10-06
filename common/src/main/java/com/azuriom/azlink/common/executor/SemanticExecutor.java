package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.executor.handlers.MockKitRedeemHandler;
import com.azuriom.azlink.common.executor.handlers.MockPrivilegeReconcileHandler;
import com.azuriom.azlink.common.executor.handlers.MockPrivilegeRevokeHandler;
import com.azuriom.azlink.common.executor.handlers.OperationHandler;
import com.azuriom.azlink.common.privileges.PrivilegeBackend;
import com.azuriom.azlink.common.executor.ledger.EntitlementVersionLedger;
import com.azuriom.azlink.common.executor.ledger.LedgerEntry;
import com.azuriom.azlink.common.executor.ledger.OperationLedger;
import com.azuriom.azlink.common.executor.model.AckRequest;
import com.azuriom.azlink.common.executor.model.AckStatusMapper;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.OperationStatus;
import com.azuriom.azlink.common.executor.model.OperationType;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.executor.parse.OperationParser;
import com.azuriom.azlink.common.logger.LoggerAdapter;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Protocol executor: validate → idempotency/version ledger → apply → durable record.
 * Privilege operations use {@link PrivilegeBackend} when LuckPerms is installed.
 */
public class SemanticExecutor {

    private final ExecutorConfig config;
    private final OperationLedger operationLedger;
    private final EntitlementVersionLedger entitlementVersions;
    private final OperationParser parser;
    private final LoggerAdapter logger;
    private final Map<OperationType, OperationHandler> handlers;

    private int mockApplyCount;

    public SemanticExecutor(ExecutorConfig config, OperationLedger operationLedger,
                            EntitlementVersionLedger entitlementVersions, LoggerAdapter logger) {
        this(config, operationLedger, entitlementVersions, logger, null);
    }

    public SemanticExecutor(ExecutorConfig config, OperationLedger operationLedger,
                            EntitlementVersionLedger entitlementVersions, LoggerAdapter logger,
                            PrivilegeBackend privilegeBackend) {
        this.config = config;
        this.operationLedger = operationLedger;
        this.entitlementVersions = entitlementVersions;
        this.parser = new OperationParser(config);
        this.logger = logger;
        this.handlers = new EnumMap<OperationType, OperationHandler>(OperationType.class);
        if (privilegeBackend == null) {
            this.handlers.put(OperationType.PRIVILEGE_RECONCILE, new MockPrivilegeReconcileHandler());
            this.handlers.put(OperationType.PRIVILEGE_REVOKE, new MockPrivilegeRevokeHandler());
        } else {
            this.handlers.put(OperationType.PRIVILEGE_RECONCILE, privilegeBackend::reconcile);
            this.handlers.put(OperationType.PRIVILEGE_REVOKE, privilegeBackend::revoke);
        }
        this.handlers.put(OperationType.KIT_REDEEM, new MockKitRedeemHandler());
    }

    public ExecutorConfig getConfig() {
        return this.config;
    }

    public OperationLedger getOperationLedger() {
        return this.operationLedger;
    }

    public EntitlementVersionLedger getEntitlementVersions() {
        return this.entitlementVersions;
    }

    public int getMockApplyCount() {
        return this.mockApplyCount;
    }

    /**
     * Process one claimed operation and return the ACK payload to send.
     */
    public ProcessedOperation process(ShopOperation operation) {
        ExecutionResult result = processInternal(operation);
        logObservability(operation, result);
        AckRequest ack = buildAck(operation, result.getResultCode(),
                result.getAppliedEntitlementVersion(), result.getMessage());
        return new ProcessedOperation(result, ack);
    }

    private AckRequest buildAck(ShopOperation operation, OperationResultCode code,
                                Long appliedEntitlementVersion, String message) {
        return new AckRequest(
                operation.getOperationId(),
                operation.getClaimToken(),
                operation.getPayloadHash(),
                code,
                appliedEntitlementVersion,
                this.config.getExecutorVersion(),
                message
        );
    }

    private ExecutionResult processInternal(ShopOperation operation) {
        ExecutionResult validation = this.parser.validate(operation);
        if (validation.getResultCode() != OperationResultCode.SUCCEEDED) {
            persistIfTerminalFailure(operation, validation);
            return validation.withLedgerHit(false);
        }

        Optional<LedgerEntry> existing = this.operationLedger.find(operation.getOperationId());
        if (existing.isPresent()) {
            LedgerEntry entry = existing.get();
            if (!Objects.equals(entry.getPayloadHash(), operation.getPayloadHash())) {
                ExecutionResult integrity = ExecutionResult.of(OperationResultCode.INTEGRITY_ERROR,
                        "payload_hash mismatch for operation_id").withLedgerHit(true);
                return integrity;
            }
            if (entry.isSuccessfullyApplied()) {
                return ExecutionResult.of(OperationResultCode.ALREADY_APPLIED,
                                entry.getAppliedEntitlementVersion(),
                                "ledger already applied")
                        .withLedgerHit(true);
            }
            // Prior non-success terminal: allow re-evaluation only for retryable/uncertain paths.
            if (entry.getResultCode() == OperationResultCode.RETRYABLE_FAILED
                    || entry.getResultCode() == OperationResultCode.UNCERTAIN) {
                this.logger.info("[SemanticExecutor] ledger miss-retry operation_id="
                        + operation.getOperationId() + " prior=" + entry.getResultCode());
            } else if (entry.getResultCode() == OperationResultCode.STALE
                    || entry.getResultCode() == OperationResultCode.FAILED
                    || entry.getResultCode() == OperationResultCode.UNSUPPORTED
                    || entry.getResultCode() == OperationResultCode.INTEGRITY_ERROR) {
                return ExecutionResult.of(entry.getResultCode(), entry.getAppliedEntitlementVersion(),
                        "ledger terminal replay").withLedgerHit(true);
            }
        }

        OperationType type = operation.getOperationType();
        if (type == OperationType.PRIVILEGE_RECONCILE || type == OperationType.PRIVILEGE_REVOKE) {
            ExecutionResult versionGate = evaluateEntitlementVersion(operation);
            if (versionGate.getResultCode() != OperationResultCode.SUCCEEDED) {
                persist(operation, versionGate);
                return versionGate.withLedgerHit(existing.isPresent());
            }
        }

        OperationHandler handler = this.handlers.get(type);
        if (handler == null) {
            ExecutionResult unsupported = ExecutionResult.of(OperationResultCode.UNSUPPORTED,
                    "no handler for " + type);
            persist(operation, unsupported);
            return unsupported.withLedgerHit(false);
        }

        ExecutionResult applied;
        try {
            applied = handler.apply(operation, this.logger);
            this.mockApplyCount++;
        } catch (RuntimeException e) {
            // Handler threw after unknown side effects → uncertain (never retryable_failed).
            this.logger.error("[SemanticExecutor] uncertain after handler exception operation_id="
                    + operation.getOperationId() + " type=" + type, e);
            ExecutionResult uncertain = ExecutionResult.of(OperationResultCode.UNCERTAIN,
                    "handler exception: " + e.getMessage());
            persist(operation, uncertain);
            return uncertain.withLedgerHit(false);
        }

        if (applied.getResultCode() == OperationResultCode.SUCCEEDED
                && (type == OperationType.PRIVILEGE_RECONCILE || type == OperationType.PRIVILEGE_REVOKE)) {
            try {
                this.entitlementVersions.putAppliedVersion(operation.getPlayerUuid(),
                        operation.getEntitlementVersion());
            } catch (IOException e) {
                this.logger.error("[SemanticExecutor] entitlement version ledger write failed"
                        + " operation_id=" + operation.getOperationId(), e);
                ExecutionResult uncertain = ExecutionResult.of(OperationResultCode.UNCERTAIN,
                        "entitlement version persist failed");
                // Still try to record uncertain in operation ledger for observability.
                persist(operation, uncertain);
                return uncertain.withLedgerHit(false);
            }
        }

        if (!persist(operation, applied)) {
            // Apply may have completed locally; never advertise retryable_failed.
            ExecutionResult uncertain = ExecutionResult.of(OperationResultCode.UNCERTAIN,
                    applied.getAppliedEntitlementVersion(),
                    "operation ledger persist failed after apply");
            return uncertain.withLedgerHit(false);
        }
        return applied.withLedgerHit(false);
    }

    private ExecutionResult evaluateEntitlementVersion(ShopOperation operation) {
        UUID player = operation.getPlayerUuid();
        long incoming = operation.getEntitlementVersion();
        Optional<Long> appliedOpt = this.entitlementVersions.getAppliedVersion(player);

        if (!appliedOpt.isPresent()) {
            return ExecutionResult.of(OperationResultCode.SUCCEEDED);
        }

        long applied = appliedOpt.get();
        if (incoming < applied) {
            this.logger.info("[SemanticExecutor] stale detection operation_id=" + operation.getOperationId()
                    + " player=" + player + " incoming=" + incoming + " applied=" + applied);
            return ExecutionResult.of(OperationResultCode.STALE, applied,
                    "incoming entitlement_version < applied");
        }
        if (incoming == applied) {
            return ExecutionResult.of(OperationResultCode.ALREADY_APPLIED, applied,
                    "entitlement_version already applied");
        }
        return ExecutionResult.of(OperationResultCode.SUCCEEDED);
    }

    private void persistIfTerminalFailure(ShopOperation operation, ExecutionResult result) {
        if (operation == null || isBlank(operation.getOperationId())) {
            return;
        }
        // Do not persist retryable network-level issues here; validation failures are permanent.
        if (result.getResultCode() == OperationResultCode.RETRYABLE_FAILED) {
            return;
        }
        persist(operation, result);
    }

    private boolean persist(ShopOperation operation, ExecutionResult result) {
        if (operation == null || isBlank(operation.getOperationId())) {
            return false;
        }
        try {
            LedgerEntry entry = new LedgerEntry(
                    operation.getOperationId(),
                    operation.getPayloadHash(),
                    operation.getOperationTypeRaw(),
                    OperationStatus.fromResult(result.getResultCode()),
                    result.getAppliedEntitlementVersion() != null
                            ? result.getAppliedEntitlementVersion()
                            : operation.getEntitlementVersion(),
                    Instant.now(),
                    result.getResultCode()
            );
            this.operationLedger.put(entry);
            return true;
        } catch (IOException e) {
            this.logger.error("[SemanticExecutor] failed to write operation ledger operation_id="
                    + operation.getOperationId(), e);
            return false;
        }
    }

    /**
     * Build ACK for transient failures that occurred before local apply (safe to retry).
     */
    public AckRequest retryableAck(ShopOperation operation, String message) {
        return buildAck(operation, OperationResultCode.RETRYABLE_FAILED, null, message);
    }

    public AckRequest uncertainAck(ShopOperation operation, String message) {
        return buildAck(operation, OperationResultCode.UNCERTAIN, null, message);
    }

    private void logObservability(ShopOperation operation, ExecutionResult result) {
        String player = operation.getPlayerUuid() == null ? "?" : operation.getPlayerUuid().toString();
        this.logger.info("[SemanticExecutor] operation_id=" + operation.getOperationId()
                + " type=" + operation.getOperationTypeRaw()
                + " player=" + player
                + " entitlement_version=" + operation.getEntitlementVersion()
                + " status=" + AckStatusMapper.toAckStatus(result.getResultCode())
                + " result_code=" + AckStatusMapper.toResultCode(result.getResultCode())
                + " ledger=" + (result.isLedgerHit() ? "hit" : "miss")
                + (result.getMessage() != null ? " msg=" + result.getMessage() : ""));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class ProcessedOperation {
        private final ExecutionResult result;
        private final AckRequest ackRequest;

        public ProcessedOperation(ExecutionResult result, AckRequest ackRequest) {
            this.result = result;
            this.ackRequest = ackRequest;
        }

        public ExecutionResult getResult() {
            return this.result;
        }

        public AckRequest getAckRequest() {
            return this.ackRequest;
        }
    }
}
