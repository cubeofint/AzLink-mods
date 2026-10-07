package com.azuriom.azlink.common.users;

import com.google.gson.annotations.SerializedName;

/**
 * Result of a {@code /coins/...} mutation. The site returns different shapes:
 * add/remove return the per-server balance ({@code old_balance}/{@code new_balance}),
 * withdraw/deposit return {@code *_global_balance} and {@code *_server_balance}.
 * Only {@code new_global_balance} may update the cached shared site balance.
 */
public class EditMoneyResult {
    @SerializedName("old_balance")
    private Double oldBalance;
    @SerializedName("new_balance")
    private Double newBalance;
    @SerializedName("new_global_balance")
    private Double newGlobalBalance;
    @SerializedName("new_server_balance")
    private Double newServerBalance;
    @SerializedName("replayed")
    private boolean replayed;

    public EditMoneyResult(double oldBalance, double newBalance) {
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
    }

    public double getOldBalance() {
        return oldBalance != null ? oldBalance : 0;
    }

    /** Per-server balance for add/remove (or server balance for withdraw/deposit). */
    public double getNewBalance() {
        if (newBalance != null) return newBalance;
        return newServerBalance != null ? newServerBalance : 0;
    }

    /** Shared site (global) balance, or {@code null} when the operation did not report it. */
    public Double getNewGlobalBalance() {
        return newGlobalBalance;
    }

    public boolean isReplayed() {
        return replayed;
    }
}
