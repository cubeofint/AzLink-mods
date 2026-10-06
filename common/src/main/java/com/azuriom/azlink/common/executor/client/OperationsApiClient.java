package com.azuriom.azlink.common.executor.client;

import com.azuriom.azlink.common.executor.model.AckRequest;
import com.azuriom.azlink.common.executor.model.AckResponse;
import com.azuriom.azlink.common.executor.model.PollRequest;
import com.azuriom.azlink.common.executor.model.PollResponse;

import java.util.concurrent.CompletableFuture;

public interface OperationsApiClient {

    CompletableFuture<PollResult> poll(PollRequest request);

    CompletableFuture<AckResult> ack(AckRequest request);

    final class PollResult {
        private final int httpStatus;
        private final PollResponse body;
        private final String errorBody;
        private final Throwable error;

        public PollResult(int httpStatus, PollResponse body, String errorBody, Throwable error) {
            this.httpStatus = httpStatus;
            this.body = body;
            this.errorBody = errorBody;
            this.error = error;
        }

        public static PollResult success(int status, PollResponse body) {
            return new PollResult(status, body, null, null);
        }

        public static PollResult httpError(int status, String errorBody) {
            return new PollResult(status, null, errorBody, null);
        }

        public static PollResult failure(Throwable error) {
            return new PollResult(-1, null, null, error);
        }

        public boolean isSuccess() {
            return this.error == null && this.httpStatus >= 200 && this.httpStatus < 300 && this.body != null;
        }

        public boolean isUnauthorized() {
            return this.httpStatus == 401 || this.httpStatus == 403;
        }

        public int getHttpStatus() {
            return this.httpStatus;
        }

        public PollResponse getBody() {
            return this.body;
        }

        public String getErrorBody() {
            return this.errorBody;
        }

        public Throwable getError() {
            return this.error;
        }
    }

    final class AckResult {
        private final int httpStatus;
        private final AckResponse body;
        private final String errorBody;
        private final Throwable error;

        public AckResult(int httpStatus, AckResponse body, String errorBody, Throwable error) {
            this.httpStatus = httpStatus;
            this.body = body;
            this.errorBody = errorBody;
            this.error = error;
        }

        public static AckResult success(int status, AckResponse body) {
            return new AckResult(status, body, null, null);
        }

        public static AckResult httpError(int status, String errorBody) {
            return new AckResult(status, null, errorBody, null);
        }

        public static AckResult failure(Throwable error) {
            return new AckResult(-1, null, null, error);
        }

        public boolean isSuccess() {
            return this.error == null && this.httpStatus >= 200 && this.httpStatus < 300;
        }

        public int getHttpStatus() {
            return this.httpStatus;
        }

        public AckResponse getBody() {
            return this.body;
        }

        public String getErrorBody() {
            return this.errorBody;
        }

        public Throwable getError() {
            return this.error;
        }
    }
}
