package com.azuriom.azlink.common.executor.parse;

import com.azuriom.azlink.common.executor.ExecutorConfig;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.OperationType;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates claimed operations against site-canonical payload schema.
 */
public class OperationParser {

    private final Set<String> supportedCapabilities;
    private final Set<String> supportedTypes;
    private final boolean acceptAllCapabilities;

    public OperationParser(ExecutorConfig config) {
        this.supportedCapabilities = toLowerSet(config.getSupportedCapabilities());
        this.supportedTypes = toLowerSet(config.getSupportedOperationTypes());
        this.acceptAllCapabilities = config.isAcceptAllCapabilities()
                || this.supportedCapabilities.contains("*");
    }

    public ExecutionResult validate(ShopOperation operation) {
        if (operation == null) {
            return ExecutionResult.of(OperationResultCode.FAILED, "operation is null");
        }
        if (isBlank(operation.getOperationId())) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing operation_id");
        }
        if (isBlank(operation.getClaimToken())) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing claim_token");
        }
        if (isBlank(operation.getPayloadHash())) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing payload_hash");
        }
        if (operation.getPayloadHash().length() != 64) {
            return ExecutionResult.of(OperationResultCode.FAILED, "invalid payload_hash length");
        }
        if (isBlank(operation.getOperationTypeRaw())) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing type");
        }

        OperationType type = operation.getOperationType();
        if (type == null) {
            return ExecutionResult.of(OperationResultCode.FAILED,
                    "unknown type " + operation.getOperationTypeRaw());
        }
        if (!this.supportedTypes.contains(type.toWire())) {
            return ExecutionResult.of(OperationResultCode.UNSUPPORTED,
                    "unsupported type " + type.toWire());
        }

        JsonObject payload = operation.getPayload();
        if (payload == null || payload.entrySet().isEmpty()) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing or empty payload");
        }

        UUID playerUuid = operation.getPlayerUuid();
        if (playerUuid == null) {
            return ExecutionResult.of(OperationResultCode.FAILED, "invalid or missing player uuid");
        }

        if (isBlank(operation.getSemanticKey())) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing semantic key");
        }

        switch (type) {
            case PRIVILEGE_RECONCILE:
                return validatePrivilegeReconcile(operation);
            case PRIVILEGE_REVOKE:
                return validatePrivilegeRevoke(operation);
            case KIT_REDEEM:
                return validateKitPayload(operation);
            default:
                return ExecutionResult.of(OperationResultCode.FAILED, "unhandled type");
        }
    }

    private ExecutionResult validatePrivilegeReconcile(ShopOperation operation) {
        ExecutionResult base = validatePrivilegeIdentity(operation);
        if (base.getResultCode() != OperationResultCode.SUCCEEDED) {
            return base;
        }
        JsonObject capabilities = operation.getCapabilities();
        if (capabilities == null) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing capabilities");
        }
        return validateCapabilities(capabilities);
    }

    private ExecutionResult validatePrivilegeRevoke(ShopOperation operation) {
        return validatePrivilegeIdentity(operation);
    }

    private ExecutionResult validatePrivilegeIdentity(ShopOperation operation) {
        if (isBlank(operation.getEntitlementId())) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing entitlement.id");
        }
        if (operation.getEntitlementVersion() == null || operation.getEntitlementVersion() < 1) {
            return ExecutionResult.of(OperationResultCode.FAILED, "invalid entitlement.version");
        }
        return ExecutionResult.of(OperationResultCode.SUCCEEDED);
    }

    private ExecutionResult validateKitPayload(ShopOperation operation) {
        if (isBlank(operation.getRedemptionId())) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing redemption_id");
        }
        return ExecutionResult.of(OperationResultCode.SUCCEEDED);
    }

    /**
     * Every key in {@code capabilities} is a required capability name (site poll semantics).
     */
    private ExecutionResult validateCapabilities(JsonObject capabilities) {
        for (Map.Entry<String, JsonElement> entry : capabilities.entrySet()) {
            String capability = entry.getKey();
            if (capability == null || capability.trim().isEmpty()) {
                return ExecutionResult.of(OperationResultCode.FAILED, "empty capability key");
            }
            String normalized = capability.trim().toLowerCase(Locale.ROOT);
            if (this.acceptAllCapabilities) {
                continue;
            }
            if (!this.supportedCapabilities.contains(normalized)) {
                return ExecutionResult.of(OperationResultCode.UNSUPPORTED,
                        "unsupported capability: " + capability);
            }
        }
        return ExecutionResult.of(OperationResultCode.SUCCEEDED);
    }

    private static Set<String> toLowerSet(Iterable<String> values) {
        Set<String> set = new HashSet<String>();
        for (String value : values) {
            if (value != null) {
                set.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
