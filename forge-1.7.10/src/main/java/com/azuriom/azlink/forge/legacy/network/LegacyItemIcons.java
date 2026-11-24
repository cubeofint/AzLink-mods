package com.azuriom.azlink.forge.legacy.network;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.kits.manifest.render.*;
import com.azuriom.azlink.forge.legacy.AzLinkForgeLegacyMod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;
import java.util.function.Consumer;

public final class LegacyItemIcons implements ItemIconRenderTransport {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("azlink_icons");
    private static Consumer<ItemIconRenderRequest> client = request -> {};
    private final AzLinkPlugin plugin;
    public LegacyItemIcons(AzLinkPlugin plugin) { this.plugin = plugin; }
    public static void register() {
        CHANNEL.registerMessage(CapHandler.class, Capabilities.class, 0, Side.SERVER);
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(ResponseHandler.class, Response.class, 2, Side.SERVER);
        FMLCommonHandler.instance().bus().register(new Events());
    }
    public static void clientHandler(Consumer<ItemIconRenderRequest> handler) { client = handler; }
    public static void advertise() { CHANNEL.sendToServer(new Capabilities(ItemIconWireCodec.capabilities(ClientCapabilities.withLocalizedNames(com.azuriom.azlink.common.utils.VersionInfo.VERSION)))); }
    public static void respond(ItemIconRenderResponse response) { CHANNEL.sendToServer(new Response(ItemIconWireCodec.response(response))); }
    // Vanilla/Forge 1.7.10 C17 packets are limited to 32 KiB. One 64x64 PNG fits; a batch may not.
    public int maxBatchItems() { return 1; }
    public void sendCapabilities(ClientCapabilities ignored) {}
    public void sendRequest(final UUID player, ItemIconRenderRequest request) {
        final byte[] bytes = ItemIconWireCodec.request(request);
        plugin.getScheduler().executeSync(() -> {
            MinecraftServer server = MinecraftServer.getServer();
            EntityPlayerMP target = null;
            if (server != null) for (Object value : server.getConfigurationManager().playerEntityList)
                if (((EntityPlayerMP) value).getUniqueID().equals(player)) { target = (EntityPlayerMP) value; break; }
            if (target == null) { plugin.getItemIconRenderCoordinator().onDisconnect(player); return; }
            CHANNEL.sendTo(new Request(bytes), target);
        });
    }
    public static final class Events {
        @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
            AzLinkForgeLegacyMod mod = AzLinkForgeLegacyMod.instance();
            if (mod != null) mod.getPlugin().getItemIconRenderCoordinator().onDisconnect(event.player.getUniqueID());
        }
    }
    public abstract static class Payload implements IMessage {
        byte[] data;
        public Payload() {}
        Payload(byte[] bytes) { data = bytes; }
        public void toBytes(ByteBuf buffer) {
            if (data.length > ItemIconWireCodec.MAX_PACKET_BYTES) throw new IllegalArgumentException("Icon packet too large");
            buffer.writeInt(data.length); buffer.writeBytes(data);
        }
        public void fromBytes(ByteBuf buffer) {
            int count = buffer.readInt();
            if (count < 0 || count > ItemIconWireCodec.MAX_PACKET_BYTES || count != buffer.readableBytes()) throw new IllegalArgumentException("Invalid icon packet length");
            data = new byte[count]; buffer.readBytes(data);
        }
    }
    public static final class Capabilities extends Payload { public Capabilities() {} Capabilities(byte[] bytes) { super(bytes); } }
    public static final class Request extends Payload { public Request() {} Request(byte[] bytes) { super(bytes); } }
    public static final class Response extends Payload { public Response() {} Response(byte[] bytes) { super(bytes); } }
    public static final class CapHandler implements IMessageHandler<Capabilities, IMessage> {
        public IMessage onMessage(Capabilities message, MessageContext context) {
            final UUID player = context.getServerHandler().playerEntity.getUniqueID();
            final ClientCapabilities caps = ItemIconWireCodec.capabilities(message.data);
            AzLinkPlugin plugin = AzLinkForgeLegacyMod.instance().getPlugin();
            plugin.getScheduler().executeSync(() -> plugin.getItemIconRenderCoordinator().onCapabilities(player, caps)); return null;
        }
    }
    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        public IMessage onMessage(Request message, MessageContext context) { client.accept(ItemIconWireCodec.request(message.data)); return null; }
    }
    public static final class ResponseHandler implements IMessageHandler<Response, IMessage> {
        public IMessage onMessage(Response message, MessageContext context) {
            final UUID player = context.getServerHandler().playerEntity.getUniqueID();
            final ItemIconRenderResponse response = ItemIconWireCodec.response(message.data);
            AzLinkPlugin plugin = AzLinkForgeLegacyMod.instance().getPlugin();
            plugin.getScheduler().executeSync(() -> plugin.getItemIconRenderCoordinator().onResponse(player, response)); return null;
        }
    }
}
