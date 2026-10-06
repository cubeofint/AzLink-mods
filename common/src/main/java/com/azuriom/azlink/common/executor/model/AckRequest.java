package com.azuriom.azlink.common.executor.model;

import com.google.gson.annotations.SerializedName;

/**
 * ACK body for {@code POST /api/shop/azlink/v1/operations/ack} (site canonical).
 */
public class AckRequest {

    @SerializedName("operation_id")
    private final String operationId;

    @SerializedName("claim_token")
    private final String claimToken;

    @SerializedName("payload_hash")
    private final String payloadHash;

    private final String status;

    @SerializedName("result_code")
    private final String resultCode;

    @SerializedName("applied_entitlement_version")
    private final Long appliedEntitlementVersion;

    @SerializedName("executor_version")
    private final String executorVersion;

    private final String message;

    public AckRequest(String operationId, String claimToken, String payloadHash,
                      OperationResultCode result, Long appliedEntitlementVersion,
                      String executorVersion, String message) {
        this.operationId = operationId;
        this.claimToken = claimToken;
        this.payloadHash = payloadHash;
        this.status = AckStatusMapper.toAckStatus(result);
        this.resultCode = AckStatusMapper.toResultCode(result);
        this.appliedEntitlementVersion = appliedEntitlementVersion;
        this.executorVersion = executorVersion;
        this.message = message;
    }

    public String getOperationId() {
        return this.operationId;
    }

    public String getClaimToken() {
        return this.claimToken;
    }

    public String getPayloadHash() {
        return this.payloadHash;
    }

    public String getStatus() {
        return this.status;
    }

    public String getResultCode() {
        return this.resultCode;
    }

    public Long getAppliedEntitlementVersion() {
        return this.appliedEntitlementVersion;
    }

    public String getExecutorVersion() {
        return this.executorVersion;
    }

    public String getMessage() {
        return this.message;
    }
}
