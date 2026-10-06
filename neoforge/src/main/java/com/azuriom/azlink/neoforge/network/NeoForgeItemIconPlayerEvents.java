package com.azuriom.azlink.neoforge.network;

import com.azuriom.azlink.neoforge.AzLinkNeoForgeMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AzLinkNeoForgeMod.MODID)
public final class NeoForgeItemIconPlayerEvents {

    private NeoForgeItemIconPlayerEvents() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AzLinkNeoForgeMod mod = AzLinkNeoForgeMod.getInstance();
        if (mod != null) {
            mod.getPlugin().getItemIconRenderCoordinator().onDisconnect(player.getUUID());
        }
    }
}
