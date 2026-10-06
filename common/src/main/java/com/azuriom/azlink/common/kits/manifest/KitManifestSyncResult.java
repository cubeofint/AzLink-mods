package com.azuriom.azlink.common.kits.manifest;

import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadResponse;

/**
 * Outcome of {@link KitManifestProvider#sync}.
 */
public final class KitManifestSyncResult {

    private final boolean success;
    private final int httpStatus;
    private final KitManifestUploadResponse response;
    private final String error;
    private final String manifestHash;

    private KitManifestSyncResult(boolean success, int httpStatus, KitManifestUploadResponse response,
                                  String error, String manifestHash) {
        this.success = success;
        this.httpStatus = httpStatus;
        this.response = response;
        this.error = error;
        this.manifestHash = manifestHash;
    }

    public static KitManifestSyncResult ok(int httpStatus, KitManifestUploadResponse response, String hash) {
        return new KitManifestSyncResult(true, httpStatus, response, null, hash);
    }

    public static KitManifestSyncResult fail(int httpStatus, String error, String hash) {
        return new KitManifestSyncResult(false, httpStatus, null, error, hash);
    }

    public static KitManifestSyncResult fail(Throwable error) {
        return new KitManifestSyncResult(false, 0, null,
                error == null ? "unknown" : error.getMessage(), null);
    }

    public boolean isSuccess() {
        return this.success;
    }

    public int getHttpStatus() {
        return this.httpStatus;
    }

    public KitManifestUploadResponse getResponse() {
        return this.response;
    }

    public String getError() {
        return this.error;
    }

    public String getManifestHash() {
        return this.manifestHash;
    }
}
