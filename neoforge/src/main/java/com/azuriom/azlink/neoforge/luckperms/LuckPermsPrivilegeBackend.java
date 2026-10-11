package com.azuriom.azlink.neoforge.luckperms;

import com.azuriom.azlink.common.executor.ledger.EntitlementVersionLedger;
import com.azuriom.azlink.common.executor.ledger.GrantedNodes;
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
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PermissionNode;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Writes shop entitlements into LuckPerms without extra context.
 * Granted nodes are recorded in {@link EntitlementVersionLedger} and revoked by exact match.
 * Older {@code azlink-entitlement} context tags (which never applied in game) are cleaned up.
 */
public final class LuckPermsPrivilegeBackend implements PrivilegeBackend {

    private EntitlementVersionLedger ledger;

    @Override
    public void bindLedger(EntitlementVersionLedger ledger) {
        this.ledger = ledger;
    }

    @Override
    public ExecutionResult reconcile(ShopOperation operation, LoggerAdapter logger) {
        PrivilegePlan plan = PrivilegePlan.reconcile(operation);
        return applyPlan(operation, plan, grantKey(operation), logger);
    }

    @Override
    public ExecutionResult revoke(ShopOperation operation, LoggerAdapter logger) {
        PrivilegePlan plan = PrivilegePlan.revoke(operation);
        return applyPlan(operation, plan, grantKey(operation), logger);
    }

    @Override
    public ExecutionResult grantGroup(ShopOperation operation, LoggerAdapter logger) {
        String group = PrivilegePlan.groupName(operation.getGroup());
        if (group == null) {
            return ExecutionResult.of(OperationResultCode.FAILED, "invalid group");
        }
        String key = operation.getEntitlementKey();
        if (key == null || key.trim().isEmpty()) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing entitlement_key");
        }
        Instant expiresAt = null;
        String rawExpiry = operation.getAbsoluteExpiresAt();
        if (rawExpiry != null && !rawExpiry.trim().isEmpty()) {
            try {
                expiresAt = Instant.parse(rawExpiry.trim());
            } catch (DateTimeParseException e) {
                return ExecutionResult.of(OperationResultCode.FAILED, "invalid expires_at");
            }
        }
        boolean expired = expiresAt != null && !expiresAt.isAfter(Instant.now());
        PrivilegePlan plan = expired
                ? PrivilegePlan.clear(key.trim(), group)
                : PrivilegePlan.groupGrant(key.trim(), group, expiresAt);
        return applyPlan(operation, plan, key.trim(), logger);
    }

    @Override
    public ExecutionResult revokeGroup(ShopOperation operation, LoggerAdapter logger) {
        String key = operation.getEntitlementKey();
        if (key == null || key.trim().isEmpty()) {
            return ExecutionResult.of(OperationResultCode.FAILED, "missing entitlement_key");
        }
        PrivilegePlan plan = PrivilegePlan.revoke(operation);
        return applyPlan(operation, plan, key.trim(), logger);
    }

    private ExecutionResult applyPlan(ShopOperation operation, PrivilegePlan plan,
                                      String entitlementKey, LoggerAdapter logger) {
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

        GrantedNodes previous = this.ledger == null
                ? null
                : this.ledger.getGrant(playerId, entitlementKey).orElse(null);

        try {
            if (!plan.isClearOnly() && plan.getGroupName() != null) {
                ensureGroup(api, plan.getGroupName());
            }
            api.getUserManager().modifyUser(playerId, user -> mutate(user, plan, previous)).get(15, TimeUnit.SECONDS);
        } catch (CompletionException e) {
            logger.error("[LuckPerms] uncertain entitlement=" + entitlementKey
                    + " player=" + playerId, e);
            return ExecutionResult.of(OperationResultCode.UNCERTAIN, "luckperms modify failed");
        } catch (Exception e) {
            logger.error("[LuckPerms] uncertain entitlement=" + entitlementKey
                    + " player=" + playerId, e);
            return ExecutionResult.of(OperationResultCode.UNCERTAIN, "luckperms modify failed");
        }

        try {
            recordGrant(playerId, entitlementKey, operation, plan);
        } catch (IOException e) {
            logger.error("[LuckPerms] grant ledger write failed entitlement=" + entitlementKey
                    + " player=" + playerId, e);
            return ExecutionResult.of(OperationResultCode.UNCERTAIN, "grant ledger persist failed");
        }

        logger.info("[LuckPerms] " + (plan.isClearOnly() ? "cleared" : "reconciled")
                + " player=" + playerId
                + " entitlement=" + entitlementKey
                + " group=" + plan.getGroupName()
                + " permissions=" + plan.getPermissions().size());
        return ExecutionResult.of(OperationResultCode.SUCCEEDED, operation.getEntitlementVersion(),
                plan.isClearOnly() ? "luckperms entitlement cleared" : "luckperms entitlement applied");
    }

    private void recordGrant(UUID playerId, String entitlementKey, ShopOperation operation,
                             PrivilegePlan plan) throws IOException {
        if (this.ledger == null) {
            return;
        }
        if (plan.isClearOnly()) {
            this.ledger.removeGrant(playerId, entitlementKey);
            return;
        }
        List<String> meta = new ArrayList<String>();
        List<String> permissions = new ArrayList<String>();
        for (String permission : plan.getPermissions()) {
            if (permission.startsWith(PrivilegePlan.META_PREFIX)) {
                meta.add(permission.substring(PrivilegePlan.META_PREFIX.length()));
            } else {
                permissions.add(permission);
            }
        }
        this.ledger.putGrant(playerId, new GrantedNodes(
                entitlementKey,
                operation.getEntitlementVersion() == null ? 0L : operation.getEntitlementVersion(),
                plan.getGroupName(),
                meta,
                permissions,
                plan.getExpiresAt(),
                Instant.now()
        ));
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

    private static void mutate(User user, PrivilegePlan plan, GrantedNodes previous) {
        String entitlementId = plan.getEntitlementId();
        user.data().clear(node -> matchesPrevious(node, previous)
                || leftoverContext(node, entitlementId));

        if (plan.isClearOnly() || plan.getGroupName() == null) {
            return;
        }

        InheritanceNode.Builder group = InheritanceNode.builder(plan.getGroupName());
        applyExpiry(group, plan.getExpiresAt());
        user.data().add(group.build());

        for (String permission : plan.getPermissions()) {
            if (permission.startsWith(PrivilegePlan.META_PREFIX)) {
                String kv = permission.substring(PrivilegePlan.META_PREFIX.length());
                int eq = kv.indexOf('=');
                MetaNode.Builder meta = MetaNode.builder(kv.substring(0, eq), kv.substring(eq + 1));
                applyExpiry(meta, plan.getExpiresAt());
                user.data().add(meta.build());
                continue;
            }
            PermissionNode.Builder node = PermissionNode.builder(permission).value(true);
            applyExpiry(node, plan.getExpiresAt());
            user.data().add(node.build());
        }
    }

    private static boolean leftoverContext(Node node, String entitlementId) {
        return entitlementId != null
                && node.getContexts().contains(PrivilegePlan.ENTITLEMENT_CONTEXT, entitlementId);
    }

    private static boolean matchesPrevious(Node node, GrantedNodes previous) {
        if (previous == null) {
            return false;
        }
        if (!sameExpiry(node.getExpiry(), previous.getExpiresAt())) {
            return false;
        }
        if (node instanceof InheritanceNode) {
            InheritanceNode inheritance = (InheritanceNode) node;
            return previous.getGroup() != null
                    && previous.getGroup().equalsIgnoreCase(inheritance.getGroupName());
        }
        if (node instanceof MetaNode) {
            MetaNode meta = (MetaNode) node;
            String encoded = meta.getMetaKey() + "=" + meta.getMetaValue();
            return previous.getMeta().contains(encoded);
        }
        if (node instanceof PermissionNode) {
            PermissionNode permission = (PermissionNode) node;
            return previous.getPermissions().contains(permission.getPermission());
        }
        return false;
    }

    private static boolean sameExpiry(Instant left, Instant right) {
        return Objects.equals(left, right);
    }

    private static <B extends NodeBuilder<?, B>> void applyExpiry(B builder, Instant expiresAt) {
        if (expiresAt != null) {
            builder.expiry(expiresAt);
        }
    }

    private static String grantKey(ShopOperation operation) {
        String id = operation.getEntitlementId();
        if (id != null && !id.trim().isEmpty()) {
            return id.trim();
        }
        return operation.getEntitlementKey();
    }
}
