package com.azuriom.azlink.neoforge;

import com.azuriom.azlink.common.kits.manifest.render.ClientCapabilities;
import com.azuriom.azlink.common.utils.VersionInfo;
import com.azuriom.azlink.neoforge.client.NeoForgeClientItemIconRenderer;
import com.azuriom.azlink.neoforge.network.ItemIconRenderPayloads;
import com.azuriom.azlink.neoforge.network.NeoForgeItemIconNetwork;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Client-only AzLink entry: rendering worker / courier.
 * No site URL, no Azuriom-Link-Token, no HTTP to main-site.
 * Payload types are registered in {@link NeoForgeItemIconNetwork} (both sides).
 */
@Mod(value = AzLinkNeoForgeMod.MODID, dist = Dist.CLIENT)
public final class AzLinkNeoForgeClient {

    private static final NeoForgeClientItemIconRenderer RENDERER = new NeoForgeClientItemIconRenderer();

    public AzLinkNeoForgeClient() {
        NeoForgeItemIconNetwork.setClientRequestHandler(
                payload -> RENDERER.handleRequest(payload.toRequest()));
        NeoForge.EVENT_BUS.register(ClientGameEvents.class);
    }

    public static final class ClientGameEvents {
        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            if (!NetworkRegistry.hasChannel(event.getConnection(), ConnectionProtocol.PLAY,
                    ItemIconRenderPayloads.CAPABILITIES_ID)) {
                return;
            }
            PacketDistributor.sendToServer(ItemIconRenderPayloads.CapabilitiesPayload.from(
                    ClientCapabilities.v1(VersionInfo.VERSION)));
        }
    }
}
