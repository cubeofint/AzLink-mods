package com.azuriom.azlink.neoforge.luckperms;

import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.privileges.PrivilegeBackend;
import com.azuriom.azlink.common.privileges.PrivilegePlan;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Writes shop entitlements into LuckPerms.
 * Every node is stored with context {@code azlink-entitlement=<id>} so a revoke
 * cannot remove groups or permissions granted by something else.
 */
public final class LuckPermsPrivilegeBackend implements PrivilegeBackend {

    @Override
    public ExecutionResult reconcile(ShopOperation operation, LoggerAdapter logger) {
        PrivilegePlan plan = PrivilegePlan.reconcile(operation);
        return apply(operation, plan, logger);
    }

    @Override
    public ExecutionResult revoke(ShopOperation operation, LoggerAdapter logger) {
        PrivilegePlan plan = PrivilegePlan.revoke(operation);
        return apply(operation, plan, logger);
    }

    private ExecutionResult apply(ShopOperation operation, PrivilegePlan plan, LoggerAdapter logger) {
        if (plan.getError() != null) {
            return ExecutionResult.of(OperationResultCode.FAILED, plan.getError());
        }
        UUID playerId = operation.getPlayerUuid();
        if (playerId == null) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing player uuid");
        }

        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            return ExecutionResult.of(OperationResultCode.RETRYABLE_FAILED, "luckperms not ready");
        }

        try {
            if (!plan.isClearOnly() && plan.getGroupName() != null) {
                ensureGroup(api, plan.getGroupName());
            }
            api.getUserManager().modifyUser(playerId, user -> mutate(user, plan)).get(15, TimeUnit.SECONDS);
        } catch (CompletionException e) {
            logger.error("[LuckPerms] uncertain entitlement=" + plan.getEntitlementId()
                    + " player=" + playerId, e);
            return ExecutionResult.of(OperationResultCode.UNCERTAIN, "luckperms modify failed");
        } catch (Exception e) {
            logger.error("[LuckPerms] uncertain entitlement=" + plan.getEntitlementId()
                    + " player=" + playerId, e);
            return ExecutionResult.of(OperationResultCode.UNCERTAIN, "luckperms modify failed");
        }

        logger.info("[LuckPerms] " + (plan.isClearOnly() ? "cleared" : "reconciled")
                + " player=" + playerId
                + " entitlement=" + plan.getEntitlementId()
                + " group=" + plan.getGroupName()
                + " permissions=" + plan.getPermissions().size());
        return ExecutionResult.of(OperationResultCode.SUCCEEDED, operation.getEntitlementVersion(),
                plan.isClearOnly() ? "luckperms entitlement cleared" : "luckperms entitlement applied");
    }

    private static void ensureGroup(LuckPerms api, String groupName) throws Exception {
        if (api.getGroupManager().getGroup(groupName) != null) {
            return;
        }
        Optional<Group> loaded = api.getGroupManager().loadGroup(groupName).get(15, TimeUnit.SECONDS);
        if (loaded.isPresent()) {
            return;
        }
        api.getGroupManager().createAndLoadGroup(groupName).get(15, TimeUnit.SECONDS);
    }

    private static void mutate(User user, PrivilegePlan plan) {
        String entitlementId = plan.getEntitlementId();
        user.data().clear(node -> node.getContexts().contains(PrivilegePlan.ENTITLEMENT_CONTEXT, entitlementId));
        if (plan.isClearOnly() || plan.getGroupName() == null) {
            return;
        }

        InheritanceNode.Builder group = InheritanceNode.builder(plan.getGroupName())
                .withContext(PrivilegePlan.ENTITLEMENT_CONTEXT, entitlementId);
        applyExpiry(group, plan.getExpiresAt());
        user.data().add(group.build());

        for (String permission : plan.getPermissions()) {
            PermissionNode.Builder node = PermissionNode.builder(permission)
                    .value(true)
                    .withContext(PrivilegePlan.ENTITLEMENT_CONTEXT, entitlementId);
            applyExpiry(node, plan.getExpiresAt());
            user.data().add(node.build());
        }
    }

    private static <B extends NodeBuilder<?, B>> void applyExpiry(B builder, Instant expiresAt) {
        if (expiresAt != null) {
            builder.expiry(expiresAt);
        }
    }
}
