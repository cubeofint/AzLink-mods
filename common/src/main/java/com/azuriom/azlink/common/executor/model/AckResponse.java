package com.azuriom.azlink.common.executor.model;

import com.google.gson.annotations.SerializedName;

public class AckResponse {

    private boolean ok;

    @SerializedName("result_code")
    private String resultCode;

    private String message;

    public boolean isOk() {
        return this.ok;
    }

    public String getResultCode() {
        return this.resultCode;
    }

    public String getMessage() {
        return this.message;
    }
}
