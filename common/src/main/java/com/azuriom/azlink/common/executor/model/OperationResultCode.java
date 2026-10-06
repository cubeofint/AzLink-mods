package com.azuriom.azlink.common.executor.model;

import java.util.Locale;

/**
 * Wire result codes for {@code POST .../operations/ack}.
 */
public enum OperationResultCode {
    SUCCEEDED("succeeded"),
    ALREADY_APPLIED("already_applied"),
    STALE("stale"),
    FAILED("failed"),
    UNSUPPORTED("unsupported"),
    RETRYABLE_FAILED("retryable_failed"),
    UNCERTAIN("uncertain"),
    INTEGRITY_ERROR("integrity_error");

    private final String wire;

    OperationResultCode(String wire) {
        this.wire = wire;
    }

    public String toWire() {
        return this.wire;
    }

    public static OperationResultCode fromWire(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (OperationResultCode code : values()) {
            if (code.wire.equals(normalized)) {
                return code;
            }
        }
        return null;
    }

    public boolean isTerminalSuccess() {
        return this == SUCCEEDED || this == ALREADY_APPLIED || this == STALE;
    }
}
