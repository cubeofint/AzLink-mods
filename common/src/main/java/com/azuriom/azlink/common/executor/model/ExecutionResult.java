package com.azuriom.azlink.common.executor.model;

public final class ExecutionResult {

    private final OperationResultCode resultCode;
    private final Long appliedEntitlementVersion;
    private final String message;
    private final boolean ledgerHit;

    private ExecutionResult(OperationResultCode resultCode, Long appliedEntitlementVersion,
                            String message, boolean ledgerHit) {
        this.resultCode = resultCode;
        this.appliedEntitlementVersion = appliedEntitlementVersion;
        this.message = message;
        this.ledgerHit = ledgerHit;
    }

    public static ExecutionResult of(OperationResultCode code) {
        return new ExecutionResult(code, null, null, false);
    }

    public static ExecutionResult of(OperationResultCode code, String message) {
        return new ExecutionResult(code, null, message, false);
    }

    public static ExecutionResult of(OperationResultCode code, Long appliedEntitlementVersion, String message) {
        return new ExecutionResult(code, appliedEntitlementVersion, message, false);
    }

    public ExecutionResult withLedgerHit(boolean hit) {
        return new ExecutionResult(this.resultCode, this.appliedEntitlementVersion, this.message, hit);
    }

    public OperationResultCode getResultCode() {
        return this.resultCode;
    }

    public Long getAppliedEntitlementVersion() {
        return this.appliedEntitlementVersion;
    }

    public String getMessage() {
        return this.message;
    }

    public boolean isLedgerHit() {
        return this.ledgerHit;
    }
}
