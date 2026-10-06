package com.azuriom.azlink.common.kits.manifest.client;

import com.azuriom.azlink.common.kits.manifest.model.IconUploadResult;
import com.azuriom.azlink.common.kits.manifest.model.KitEnsureRequest;
import com.azuriom.azlink.common.kits.manifest.model.KitLifecycleResponse;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadRequest;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadResponse;

import java.util.concurrent.CompletableFuture;

public interface KitManifestApiClient {

    CompletableFuture<ApiResult<IconUploadResult>> uploadIcon(byte[] pngBytes);

    CompletableFuture<ApiResult<KitManifestUploadResponse>> uploadManifest(KitManifestUploadRequest request);

    CompletableFuture<ApiResult<KitLifecycleResponse>> ensureKit(KitEnsureRequest request);

    CompletableFuture<ApiResult<KitLifecycleResponse>> retireKit(String deliveryKey);

    final class ApiResult<T> {
        private final boolean success;
        private final int httpStatus;
        private final T body;
        private final String errorBody;
        private final Throwable error;

        private ApiResult(boolean success, int httpStatus, T body, String errorBody, Throwable error) {
            this.success = success;
            this.httpStatus = httpStatus;
            this.body = body;
            this.errorBody = errorBody;
            this.error = error;
        }

        public static <T> ApiResult<T> ok(int status, T body) {
            return new ApiResult<T>(true, status, body, null, null);
        }

        public static <T> ApiResult<T> httpError(int status, String body) {
            return new ApiResult<T>(false, status, null, body, null);
        }

        public static <T> ApiResult<T> failure(Throwable error) {
            return new ApiResult<T>(false, 0, null, null, error);
        }

        public boolean isSuccess() {
            return this.success;
        }

        public int getHttpStatus() {
            return this.httpStatus;
        }

        public T getBody() {
            return this.body;
        }

        public String getErrorBody() {
            return this.errorBody;
        }

        public Throwable getError() {
            return this.error;
        }

        public boolean isUnauthorized() {
            return this.httpStatus == 401 || this.httpStatus == 403;
        }
    }
}
