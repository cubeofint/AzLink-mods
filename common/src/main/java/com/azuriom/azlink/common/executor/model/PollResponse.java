package com.azuriom.azlink.common.executor.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * Poll response wrapper — {@code protocol_version} is top-level (site canonical).
 */
public class PollResponse {

    @SerializedName("protocol_version")
    private Integer protocolVersion;

    @SerializedName("server_time")
    private String serverTime;

    private List<ShopOperation> operations;

    @SerializedName("has_more")
    private Boolean hasMore;

    @SerializedName("compatibility_errors")
    private List<JsonObject> compatibilityErrors;

    public PollResponse() {
    }

    public PollResponse(Integer protocolVersion, List<ShopOperation> operations) {
        this.protocolVersion = protocolVersion;
        this.operations = operations;
    }

    public Integer getProtocolVersion() {
        return this.protocolVersion;
    }

    public String getServerTime() {
        return this.serverTime;
    }

    public List<ShopOperation> getOperations() {
        return this.operations == null ? Collections.<ShopOperation>emptyList() : this.operations;
    }

    public Boolean getHasMore() {
        return this.hasMore;
    }

    public List<JsonObject> getCompatibilityErrors() {
        return this.compatibilityErrors == null
                ? Collections.<JsonObject>emptyList()
                : this.compatibilityErrors;
    }
}
