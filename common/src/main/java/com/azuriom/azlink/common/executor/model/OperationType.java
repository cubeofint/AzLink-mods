package com.azuriom.azlink.common.executor.model;

/**
 * Shop Semantic Operation types. Java constants stay SCREAMING_SNAKE; wire values are snake_case.
 */
public enum OperationType {
    PRIVILEGE_RECONCILE("privilege_reconcile"),
    PRIVILEGE_REVOKE("privilege_revoke"),
    KIT_REDEEM("kit_redeem");

    private final String wire;

    OperationType(String wire) {
        this.wire = wire;
    }

    public String toWire() {
        return this.wire;
    }

    /**
     * Accepts only site wire values (snake_case). SCREAMING_SNAKE is not a valid wire form.
     */
    public static OperationType fromWire(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        for (OperationType type : values()) {
            if (type.wire.equals(trimmed)) {
                return type;
            }
        }
        return null;
    }
}
