package com.azuriom.azlink.neoforge.kits;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.kits.manifest.KitDeliveryKeys;
import com.azuriom.azlink.common.kits.manifest.KitManifestSyncResult;
import com.azuriom.azlink.common.kits.manifest.ServerKitItem;
import com.azuriom.azlink.common.kits.manifest.model.KitLifecycleResponse;
import com.azuriom.azlink.neoforge.AzLinkNeoForgeMod;
import dev.ftb.mods.ftbessentials.kit.Kit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bridges FTB Essentials kit mutations → site kit shell + inventory mirror.
 */
public final class FtbKitManifestHooks {

    private static final ThreadLocal<UUID> PREFERRED_RENDER_PLAYER = new ThreadLocal<UUID>();

    private FtbKitManifestHooks() {
    }

    public static void setPreferredRenderPlayer(UUID playerId) {
        if (playerId == null) {
            PREFERRED_RENDER_PLAYER.remove();
        } else {
            PREFERRED_RENDER_PLAYER.set(playerId);
        }
    }

    public static void clearPreferredRenderPlayer() {
        PREFERRED_RENDER_PLAYER.remove();
    }

    public static void onKitSaved(Kit kit) {
        if (kit == null) {
            return;
        }
        AzLinkPlugin plugin = pluginOrNull();
        if (plugin == null || plugin.getKitManifestSyncCoordinator() == null) {
            return;
        }
        if (!plugin.isConfigured() || !plugin.isDedicatedServerSiteLinkAllowed()) {
            return;
        }

        Optional<String> deliveryKey = KitDeliveryKeys.normalize(kit.getKitName());
        if (!deliveryKey.isPresent()) {
            plugin.getLogger().warn("[KitManifest] skip publish: kit name is not a valid delivery_key: "
                    + kit.getKitName());
            return;
        }

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            plugin.getLogger().warn("[KitManifest] skip publish: server not ready delivery_key="
                    + deliveryKey.get());
            return;
        }

        List<ItemStack> items = kit.getItems();
        List<ServerKitItem> serverItems = NeoForgeItemStackSnapshots.fromStacks(server, items);
        if (serverItems.isEmpty()) {
            plugin.getLogger().warn("[KitManifest] skip publish: empty items delivery_key="
                    + deliveryKey.get());
            return;
        }

        UUID preferred = PREFERRED_RENDER_PLAYER.get();
        if (preferred == null) {
            preferred = findOnlineAdmin(server);
        }

        final String key = deliveryKey.get();
        Integer cooldownSeconds = toCooldownSeconds(kit.getCooldown());
        int version = plugin.getKitManifestVersionStore().allocateNext(key);
        plugin.getLogger().info("[KitManifest] mirror+publish scheduled delivery_key=" + key
                + " version=" + version + " items=" + serverItems.size()
                + (cooldownSeconds == null ? "" : " cooldown_s=" + cooldownSeconds));

        CompletableFuture<KitManifestSyncResult> future = plugin.getKitManifestSyncCoordinator()
                .publish(key, version, serverItems, preferred, kit.getKitName(), cooldownSeconds, true);

        future.whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().warn("[KitManifest] publish failed delivery_key=" + key
                        + ": " + error.getMessage());
                return;
            }
            if (result == null) {
                return;
            }
            if (result.isSuccess()) {
                if (result.getResponse() != null && result.getResponse().getManifestVersion() != null) {
                    plugin.getKitManifestVersionStore().remember(key,
                            result.getResponse().getManifestVersion().intValue());
                }
                plugin.getLogger().info("[KitManifest] mirrored delivery_key=" + key
                        + " outcome=" + (result.getResponse() == null ? "ok" : result.getResponse().getOutcome()));
            } else {
                plugin.getLogger().warn("[KitManifest] publish rejected delivery_key=" + key
                        + " HTTP " + result.getHttpStatus()
                        + (result.getError() == null ? "" : " " + result.getError()));
            }
        });
    }

    public static void onKitDeleted(String kitName) {
        AzLinkPlugin plugin = pluginOrNull();
        if (plugin == null) {
            return;
        }
        if (!plugin.isConfigured() || !plugin.isDedicatedServerSiteLinkAllowed()) {
            return;
        }
        Optional<String> deliveryKey = KitDeliveryKeys.normalize(kitName);
        if (!deliveryKey.isPresent()) {
            plugin.getLogger().warn("[KitManifest] skip retire: invalid delivery_key name=" + kitName);
            return;
        }

        final String key = deliveryKey.get();
        plugin.getLogger().info("[KitManifest] retire scheduled delivery_key=" + key);
        plugin.getKitManifestSyncCoordinator().retireOnSite(key)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        plugin.getLogger().warn("[KitManifest] retire failed delivery_key=" + key
                                + ": " + error.getMessage());
                        return;
                    }
                    if (result == null) {
                        return;
                    }
                    if (result.isSuccess()) {
                        KitLifecycleResponse body = result.getBody();
                        plugin.getLogger().info("[KitManifest] retired delivery_key=" + key
                                + " outcome=" + (body == null ? "ok" : body.getOutcome()));
                    } else {
                        plugin.getLogger().warn("[KitManifest] retire rejected delivery_key=" + key
                                + " HTTP " + result.getHttpStatus()
                                + (result.getErrorBody() == null ? "" : " " + result.getErrorBody()));
                    }
                });
    }

    private static AzLinkPlugin pluginOrNull() {
        AzLinkNeoForgeMod mod = AzLinkNeoForgeMod.getInstance();
        return mod == null ? null : mod.getPlugin();
    }

    /**
     * FTB stores cooldown in milliseconds; {@code <= 0} means one-time / no cooldown.
     */
    static Integer toCooldownSeconds(long cooldownMs) {
        if (cooldownMs <= 0L) {
            return Integer.valueOf(0);
        }
        long seconds = TimeUnit.MILLISECONDS.toSeconds(cooldownMs);
        if (seconds < 1L) {
            seconds = 1L;
        }
        if (seconds > 31536000L) {
            seconds = 31536000L;
        }
        return Integer.valueOf((int) seconds);
    }

    private static UUID findOnlineAdmin(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                return player.getUUID();
            }
        }
        return null;
    }
}
