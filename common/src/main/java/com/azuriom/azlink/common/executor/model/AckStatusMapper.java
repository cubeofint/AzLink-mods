package com.azuriom.azlink.common.executor.model;

/**
 * Maps local execution outcome to website ACK {@code status} (MinecraftOperationAckStatus).
 * {@code integrity_error} is not a website status — it ACKs as {@code failed} with result_code.
 */
public final class AckStatusMapper {

    private AckStatusMapper() {
    }

    public static String toAckStatus(OperationResultCode code) {
        if (code == null) {
            return OperationResultCode.UNCERTAIN.toWire();
        }
        switch (code) {
            case SUCCEEDED:
                return "succeeded";
            case ALREADY_APPLIED:
                return "already_applied";
            case STALE:
                return "stale";
            case RETRYABLE_FAILED:
                return "retryable_failed";
            case UNSUPPORTED:
                return "unsupported";
            case UNCERTAIN:
                return "uncertain";
            case INTEGRITY_ERROR:
            case FAILED:
                return "failed";
            default:
                return "failed";
        }
    }

    public static String toResultCode(OperationResultCode code) {
        if (code == null) {
            return "uncertain";
        }
        if (code == OperationResultCode.SUCCEEDED) {
            return "applied";
        }
        return code.toWire();
    }
}
