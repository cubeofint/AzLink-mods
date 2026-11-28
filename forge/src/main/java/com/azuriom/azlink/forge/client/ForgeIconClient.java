package com.azuriom.azlink.forge.client;

import com.azuriom.azlink.forge.network.ForgeItemIcons;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ForgeIconClient {
    public static void init() {
        ForgeItemIcons.clientHandler(new ForgeClientItemIconRenderer()::handleRequest);
        MinecraftForge.EVENT_BUS.register(new ForgeIconClient());
    }
    @SubscribeEvent public void login(ClientPlayerNetworkEvent.LoggingIn event) {
        if (event.getConnection() != null && ForgeItemIcons.present(event.getConnection())) {
            ForgeItemIcons.advertise(event.getConnection());
        }
    }
}
