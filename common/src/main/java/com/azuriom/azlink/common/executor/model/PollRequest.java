package com.azuriom.azlink.common.executor.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class PollRequest {

    @SerializedName("protocol_version")
    private final int protocolVersion;

    @SerializedName("executor_version")
    private final String executorVersion;

    @SerializedName("supported_operation_types")
    private final List<String> supportedOperationTypes;

    @SerializedName("supported_capabilities")
    private final List<String> supportedCapabilities;

    @SerializedName("max_batch")
    private final int maxBatch;

    public PollRequest(int protocolVersion, String executorVersion,
                       List<String> supportedOperationTypes, List<String> supportedCapabilities,
                       int maxBatch) {
        this.protocolVersion = protocolVersion;
        this.executorVersion = executorVersion;
        this.supportedOperationTypes = new ArrayList<String>(supportedOperationTypes);
        this.supportedCapabilities = new ArrayList<String>(supportedCapabilities);
        this.maxBatch = maxBatch;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public String getExecutorVersion() {
        return this.executorVersion;
    }

    public List<String> getSupportedOperationTypes() {
        return this.supportedOperationTypes;
    }

    public List<String> getSupportedCapabilities() {
        return this.supportedCapabilities;
    }

    public int getMaxBatch() {
        return this.maxBatch;
    }
}
