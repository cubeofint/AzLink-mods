package com.azuriom.azlink.common.users;

import java.util.Locale;

/**
 * Actions for the site game-currency API ({@code /api/azlink/user/{id}/coins/...}).
 * Donate site money ({@code /money/...}) is intentionally not used — the site rejects it with 403.
 */
public enum MoneyAction {
    ADD, REMOVE, DEPOSIT, WITHDRAW;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MoneyAction fromString(String action) {
        try {
            return MoneyAction.valueOf(action.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
