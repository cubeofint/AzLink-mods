package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.utils.VersionInfo;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Local Semantic Executor config ({@code executor.json} in AzLink data directory).
 * Provider mappings intentionally omitted at this stage.
 */
public class ExecutorConfig {

    public static final int DEFAULT_PROTOCOL_VERSION = 1;
    public static final int DEFAULT_POLL_INTERVAL_SECONDS = 15;
    public static final int DEFAULT_MAX_BATCH = 20;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;

    private boolean enabled = true;

    @SerializedName("protocol_version")
    private int protocolVersion = DEFAULT_PROTOCOL_VERSION;

    @SerializedName("executor_version")
    private String executorVersion = VersionInfo.VERSION;

    @SerializedName("supported_operation_types")
    private List<String> supportedOperationTypes = defaultOperationTypes();

    @SerializedName("supported_capabilities")
    private List<String> supportedCapabilities = defaultCapabilities();

    @SerializedName("poll_interval_seconds")
    private int pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;

    @SerializedName("max_batch")
    private int maxBatch = DEFAULT_MAX_BATCH;

    @SerializedName("request_timeout_ms")
    private int requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;

    public static ExecutorConfig defaults() {
        return new ExecutorConfig();
    }

    private static List<String> defaultOperationTypes() {
        return new ArrayList<String>(Arrays.asList(
                "privilege_reconcile",
                "privilege_revoke",
                "kit_redeem"
        ));
    }

    private static List<String> defaultCapabilities() {
        // Capability keys match shop capability definitions (e.g. fly). Empty = site will not
        // claim privilege_reconcile ops that require capabilities; kit_redeem/revoke still work.
        return new ArrayList<String>();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public String getExecutorVersion() {
        return this.executorVersion == null || this.executorVersion.isEmpty()
                ? VersionInfo.VERSION
                : this.executorVersion;
    }

    public List<String> getSupportedOperationTypes() {
        return this.supportedOperationTypes == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(this.supportedOperationTypes);
    }

    public List<String> getSupportedCapabilities() {
        return this.supportedCapabilities == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(this.supportedCapabilities);
    }

    public void setSupportedCapabilities(List<String> supportedCapabilities) {
        this.supportedCapabilities = supportedCapabilities == null
                ? new ArrayList<String>()
                : new ArrayList<String>(supportedCapabilities);
    }

    public int getPollIntervalSeconds() {
        return this.pollIntervalSeconds <= 0 ? DEFAULT_POLL_INTERVAL_SECONDS : this.pollIntervalSeconds;
    }

    public int getMaxBatch() {
        return this.maxBatch <= 0 ? DEFAULT_MAX_BATCH : this.maxBatch;
    }

    public int getRequestTimeoutMs() {
        return this.requestTimeoutMs <= 0 ? DEFAULT_REQUEST_TIMEOUT_MS : this.requestTimeoutMs;
    }
}
