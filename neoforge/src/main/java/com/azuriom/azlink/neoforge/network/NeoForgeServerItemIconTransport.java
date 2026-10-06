package com.azuriom.azlink.neoforge.network;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.kits.manifest.render.ClientCapabilities;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderRequest;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderTransport;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Function;

/**
 * Dedicated-server transport: sends render requests to a specific player.
 */
public final class NeoForgeServerItemIconTransport implements ItemIconRenderTransport {

    private final AzLinkPlugin plugin;
    private final Function<UUID, ServerPlayer> playerLookup;

    public NeoForgeServerItemIconTransport(AzLinkPlugin plugin, Function<UUID, ServerPlayer> playerLookup) {
        this.plugin = plugin;
        this.playerLookup = playerLookup;
    }

    @Override
    public void sendRequest(UUID playerId, ItemIconRenderRequest request) {
        ServerPlayer player = this.playerLookup.apply(playerId);
        if (player == null) {
            throw new IllegalStateException("player offline: " + playerId);
        }
        if (!player.connection.hasChannel(ItemIconRenderPayloads.REQUEST_ID)) {
            throw new IllegalStateException("client has no item icon channel: " + playerId);
        }
        PacketDistributor.sendToPlayer(player, ItemIconRenderPayloads.RequestPayload.from(request));
        this.plugin.getLogger().info("[ItemIconRender] sent request_id=" + request.getRequestId()
                + " to=" + player.getGameProfile().getName()
                + " items=" + request.getItems().size());
    }

    @Override
    public void sendCapabilities(ClientCapabilities capabilities) {
        // server does not send capabilities upstream to site
    }
}
