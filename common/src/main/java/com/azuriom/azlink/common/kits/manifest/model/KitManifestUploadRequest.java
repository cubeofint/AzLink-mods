package com.azuriom.azlink.common.kits.manifest.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * Wire body for {@code POST /api/shop/azlink/v1/kits/manifests}.
 */
public final class KitManifestUploadRequest {

    @SerializedName("protocol_version")
    private final int protocolVersion;

    @SerializedName("executor_version")
    private final String executorVersion;

    @SerializedName("delivery_key")
    private final String deliveryKey;

    @SerializedName("manifest_version")
    private final int manifestVersion;

    @SerializedName("manifest_hash")
    private final String manifestHash;

    private final List<ManifestItem> items;

    @SerializedName("auto_apply")
    private final Boolean autoApply;

    @SerializedName("ensure_kit")
    private final Boolean ensureKit;

    @SerializedName("kit_name")
    private final String kitName;

    @SerializedName("cooldown_seconds")
    private final Integer cooldownSeconds;

    public KitManifestUploadRequest(int protocolVersion, String executorVersion, String deliveryKey,
                                    int manifestVersion, String manifestHash, List<ManifestItem> items) {
        this(protocolVersion, executorVersion, deliveryKey, manifestVersion, manifestHash, items,
                null, null, null, null);
    }

    public KitManifestUploadRequest(int protocolVersion, String executorVersion, String deliveryKey,
                                    int manifestVersion, String manifestHash, List<ManifestItem> items,
                                    Boolean autoApply, Boolean ensureKit, String kitName,
                                    Integer cooldownSeconds) {
        this.protocolVersion = protocolVersion;
        this.executorVersion = executorVersion;
        this.deliveryKey = deliveryKey;
        this.manifestVersion = manifestVersion;
        this.manifestHash = manifestHash;
        this.items = items == null ? Collections.<ManifestItem>emptyList() : items;
        this.autoApply = autoApply;
        this.ensureKit = ensureKit;
        this.kitName = kitName;
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public String getExecutorVersion() {
        return this.executorVersion;
    }

    public String getDeliveryKey() {
        return this.deliveryKey;
    }

    public int getManifestVersion() {
        return this.manifestVersion;
    }

    public String getManifestHash() {
        return this.manifestHash;
    }

    public List<ManifestItem> getItems() {
        return this.items;
    }

    public Boolean getAutoApply() {
        return this.autoApply;
    }

    public Boolean getEnsureKit() {
        return this.ensureKit;
    }

    public String getKitName() {
        return this.kitName;
    }

    public Integer getCooldownSeconds() {
        return this.cooldownSeconds;
    }

    public KitManifestUploadRequest withLifecycle(boolean autoApply, boolean ensureKit,
                                                   String kitName, Integer cooldownSeconds) {
        return new KitManifestUploadRequest(
                this.protocolVersion,
                this.executorVersion,
                this.deliveryKey,
                this.manifestVersion,
                this.manifestHash,
                this.items,
                Boolean.valueOf(autoApply),
                Boolean.valueOf(ensureKit),
                kitName,
                cooldownSeconds
        );
    }
}
