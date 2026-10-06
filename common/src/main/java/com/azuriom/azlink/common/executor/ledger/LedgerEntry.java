package com.azuriom.azlink.common.executor.ledger;

import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.OperationStatus;
import com.google.gson.annotations.SerializedName;

import java.time.Instant;

public class LedgerEntry {

    @SerializedName("operation_id")
    private String operationId;

    @SerializedName("payload_hash")
    private String payloadHash;

    @SerializedName("operation_type")
    private String operationType;

    private String status;

    @SerializedName("applied_entitlement_version")
    private Long appliedEntitlementVersion;

    @SerializedName("completed_at")
    private Instant completedAt;

    @SerializedName("result_code")
    private String resultCode;

    public LedgerEntry() {
    }

    public LedgerEntry(String operationId, String payloadHash, String operationType,
                       OperationStatus status, Long appliedEntitlementVersion,
                       Instant completedAt, OperationResultCode resultCode) {
        this.operationId = operationId;
        this.payloadHash = payloadHash;
        this.operationType = operationType;
        this.status = status.toWire();
        this.appliedEntitlementVersion = appliedEntitlementVersion;
        this.completedAt = completedAt;
        this.resultCode = resultCode.toWire();
    }

    public String getOperationId() {
        return this.operationId;
    }

    public String getPayloadHash() {
        return this.payloadHash;
    }

    public String getOperationType() {
        return this.operationType;
    }

    public OperationStatus getStatus() {
        return OperationStatus.fromWire(this.status);
    }

    public Long getAppliedEntitlementVersion() {
        return this.appliedEntitlementVersion;
    }

    public Instant getCompletedAt() {
        return this.completedAt;
    }

    public OperationResultCode getResultCode() {
        return OperationResultCode.fromWire(this.resultCode);
    }

    public boolean isSuccessfullyApplied() {
        OperationResultCode code = getResultCode();
        return code == OperationResultCode.SUCCEEDED || code == OperationResultCode.ALREADY_APPLIED;
    }
}
