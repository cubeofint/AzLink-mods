package com.azuriom.azlink.common.users;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.data.UserInfo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {

    private final Map<String, UserInfo> usersByName = new ConcurrentHashMap<>();
    private final AzLinkPlugin plugin;

    public UserManager(AzLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<UserInfo> getUserByName(String name) {
        return Optional.ofNullable(this.usersByName.get(name));
    }

    public void addUser(UserInfo user) {
        this.usersByName.put(user.getName(), user);
    }

    public CompletableFuture<UserInfo> editCoins(UserInfo user, MoneyAction action, double amount) {
        // One key per logical mutation — HttpClient has no automatic retry loop that would regenerate it.
        String idempotencyKey = java.util.UUID.randomUUID().toString().replace("-", "");
        return this.plugin.getHttpClient().editCoins(user, action.toString(), amount, idempotencyKey)
                .thenApply(result -> {
                    user.setCoins(result.getNewBalance());
                    return user;
                });
    }

    /**
     * @deprecated Use {@link #editCoins(UserInfo, MoneyAction, double)} instead.
     */
    @Deprecated
    public CompletableFuture<UserInfo> editMoney(UserInfo user, MoneyAction action, double amount) {
        return editCoins(user, action, amount);
    }

    /**
     * @deprecated Use {@link #editCoins(UserInfo, MoneyAction, double)} instead.
     */
    @Deprecated
    public CompletableFuture<UserInfo> editMoney(UserInfo user, String action, double amount) {
        return editCoins(user, MoneyAction.valueOf(action.toUpperCase()), amount);
    }
}
