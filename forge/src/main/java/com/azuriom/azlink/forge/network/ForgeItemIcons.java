package com.azuriom.azlink.forge.network;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.kits.manifest.render.*;
import com.azuriom.azlink.common.utils.VersionInfo;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.*;

public final class ForgeItemIcons implements ItemIconRenderTransport {
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation("azlink", "item_icons");
    private static final String TOOLTIPS_CHANNEL_VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CHANNEL_ID,
            () -> TOOLTIPS_CHANNEL_VERSION, version -> true, version -> true);
    private static Consumer<ItemIconRenderRequest> client = request -> {};
    private final AzLinkPlugin plugin;
    private final Function<UUID, ServerPlayer> lookup;
    public ForgeItemIcons(AzLinkPlugin plugin, Function<UUID, ServerPlayer> lookup) { this.plugin = plugin; this.lookup = lookup; }

    public static void register() {
        CHANNEL.registerMessage(0, Capabilities.class, (m,b) -> b.writeByteArray(m.data), b -> new Capabilities(read(b)),
            (m, supplier) -> server(supplier, p -> com.azuriom.azlink.forge.AzLinkForgeMod.instance().getPlugin()
                    .getItemIconRenderCoordinator().onCapabilities(p.getUUID(), ItemIconWireCodec.capabilities(m.data))), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Request.class, (m,b) -> b.writeByteArray(m.data), b -> new Request(read(b)),
            (m, supplier) -> { var context = supplier.get(); context.enqueueWork(() -> client.accept(ItemIconWireCodec.request(m.data))); context.setPacketHandled(true); }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Response.class, (m,b) -> b.writeByteArray(m.data), b -> new Response(read(b)),
            (m, supplier) -> server(supplier, p -> com.azuriom.azlink.forge.AzLinkForgeMod.instance().getPlugin()
                    .getItemIconRenderCoordinator().onResponse(p.getUUID(), ItemIconWireCodec.response(m.data))), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        MinecraftForge.EVENT_BUS.register(ForgeItemIcons.class);
    }
    private static byte[] read(FriendlyByteBuf buffer) { return buffer.readByteArray(ItemIconWireCodec.MAX_PACKET_BYTES); }
    private static void server(Supplier<NetworkEvent.Context> supplier, Consumer<ServerPlayer> work) {
        var context = supplier.get(); ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> work.accept(sender)); context.setPacketHandled(true);
    }
    public static void clientHandler(Consumer<ItemIconRenderRequest> handler) { client = handler; }
    public static boolean present(Connection connection) { return CHANNEL.isRemotePresent(connection); }

    public static void advertise(Connection connection) {
        ConnectionData data = NetworkHooks.getConnectionData(connection);
        boolean supportsTooltips = data != null && TOOLTIPS_CHANNEL_VERSION.equals(data.getChannels().get(CHANNEL_ID));
        ClientCapabilities capabilities = supportsTooltips
                ? new ClientCapabilities(ItemIconRenderProtocol.TOOLTIPS_VERSION, true, VersionInfo.VERSION,
                        ItemIconRenderProtocol.ICON_SIZE, ItemIconRenderProtocol.IMAGE_FORMAT)
                : ClientCapabilities.withLocalizedNames(VersionInfo.VERSION);
        CHANNEL.sendToServer(new Capabilities(ItemIconWireCodec.capabilities(capabilities)));
    }
    public static void respond(ItemIconRenderResponse response) { CHANNEL.sendToServer(new Response(ItemIconWireCodec.response(response))); }
    // ServerboundCustomPayloadPacket in 1.20.1 is limited to 32767 bytes, including the response.
    public int maxBatchItems() { return 1; }
    public void sendCapabilities(ClientCapabilities ignored) {}
    public void sendRequest(UUID player, ItemIconRenderRequest request) {
        byte[] bytes = ItemIconWireCodec.request(request);
        plugin.getScheduler().executeSync(() -> {
            ServerPlayer target = lookup.apply(player);
            if (target == null || !CHANNEL.isRemotePresent(target.connection.connection)) { plugin.getItemIconRenderCoordinator().onDisconnect(player); return; }
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new Request(bytes));
        });
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var mod = com.azuriom.azlink.forge.AzLinkForgeMod.instance();
        if (mod != null) mod.getPlugin().getItemIconRenderCoordinator().onDisconnect(event.getEntity().getUUID());
    }
    private static final class Capabilities { final byte[] data; Capabilities(byte[] data) { this.data=data; } }
    private static final class Request { final byte[] data; Request(byte[] data) { this.data=data; } }
    private static final class Response { final byte[] data; Response(byte[] data) { this.data=data; } }
}
