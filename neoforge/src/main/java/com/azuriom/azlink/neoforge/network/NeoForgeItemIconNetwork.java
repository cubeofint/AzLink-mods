package com.azuriom.azlink.neoforge.network;

import com.azuriom.azlink.neoforge.AzLinkNeoForgeMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/**
 * Registers all item-icon payloads on BOTH physical sides.
 * Client-only handling for {@code playToClient} is injected without loading client classes on dedicated server.
 */
@EventBusSubscriber(modid = AzLinkNeoForgeMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeItemIconNetwork {

    private static volatile Consumer<ItemIconRenderPayloads.RequestPayload> clientRequestHandler = payload -> {
        // no-op on dedicated server; client installs a real handler at startup
    };

    private NeoForgeItemIconNetwork() {
    }

    public static void setClientRequestHandler(Consumer<ItemIconRenderPayloads.RequestPayload> handler) {
        clientRequestHandler = handler == null ? payload -> {
        } : handler;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional: a server with AzLink accepts clients that do not have the mod,
        // and a client with AzLink can still join a server that does not advertise these channels.
        PayloadRegistrar registrar = event.registrar("azlink").versioned("1").optional();

        registrar.playToClient(
                ItemIconRenderPayloads.RequestPayload.TYPE,
                ItemIconRenderPayloads.RequestPayload.CODEC,
                NeoForgeItemIconNetwork::onRequest);

        registrar.playToServer(
                ItemIconRenderPayloads.CapabilitiesPayload.TYPE,
                ItemIconRenderPayloads.CapabilitiesPayload.CODEC,
                NeoForgeItemIconNetwork::onCapabilities);
        registrar.playToServer(
                ItemIconRenderPayloads.ResponsePayload.TYPE,
                ItemIconRenderPayloads.ResponsePayload.CODEC,
                NeoForgeItemIconNetwork::onResponse);
    }

    private static void onRequest(ItemIconRenderPayloads.RequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientRequestHandler.accept(payload));
    }

    private static void onCapabilities(ItemIconRenderPayloads.CapabilitiesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AzLinkNeoForgeMod mod = AzLinkNeoForgeMod.getInstance();
            if (mod != null) {
                mod.getPlugin().getItemIconRenderCoordinator()
                        .onCapabilities(player.getUUID(), payload.toCapabilities());
            }
        });
    }

    private static void onResponse(ItemIconRenderPayloads.ResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AzLinkNeoForgeMod mod = AzLinkNeoForgeMod.getInstance();
            if (mod != null) {
                mod.getPlugin().getItemIconRenderCoordinator()
                        .onResponse(player.getUUID(), payload.toResponse());
            }
        });
    }
}
