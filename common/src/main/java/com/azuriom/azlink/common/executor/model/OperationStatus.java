package com.azuriom.azlink.common.executor.model;

import java.util.Locale;

public enum OperationStatus {
    APPLIED,
    FAILED,
    UNSUPPORTED,
    STALE,
    UNCERTAIN;

    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OperationStatus fromWire(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OperationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static OperationStatus fromResult(OperationResultCode code) {
        switch (code) {
            case SUCCEEDED:
            case ALREADY_APPLIED:
                return APPLIED;
            case STALE:
                return STALE;
            case UNSUPPORTED:
                return UNSUPPORTED;
            case UNCERTAIN:
                return UNCERTAIN;
            case FAILED:
            case INTEGRITY_ERROR:
            case RETRYABLE_FAILED:
            default:
                return FAILED;
        }
    }
}
