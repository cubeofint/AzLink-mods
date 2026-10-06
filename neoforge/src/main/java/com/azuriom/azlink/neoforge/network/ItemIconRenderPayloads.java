package com.azuriom.azlink.neoforge.network;

import com.azuriom.azlink.common.kits.manifest.render.ClientCapabilities;
import com.azuriom.azlink.common.kits.manifest.render.IconRenderResult;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderProtocol;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderRequest;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderResponse;
import com.azuriom.azlink.common.kits.manifest.render.RenderItemPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NeoForge payloads for client-assisted item icon rendering.
 * Never carries Azuriom-Link-Token / site URL.
 */
public final class ItemIconRenderPayloads {

    public static final ResourceLocation CAPABILITIES_ID =
            ResourceLocation.fromNamespaceAndPath("azlink", "item_icon_capabilities");
    public static final ResourceLocation REQUEST_ID =
            ResourceLocation.fromNamespaceAndPath("azlink", "item_icon_render_request");
    public static final ResourceLocation RESPONSE_ID =
            ResourceLocation.fromNamespaceAndPath("azlink", "item_icon_render_response");

    private ItemIconRenderPayloads() {
    }

    public record CapabilitiesPayload(int protocolVersion, boolean supportsRendering,
                                      String rendererVersion, int iconSize, String imageFormat)
            implements CustomPacketPayload {
        public static final Type<CapabilitiesPayload> TYPE = new Type<>(CAPABILITIES_ID);
        public static final StreamCodec<FriendlyByteBuf, CapabilitiesPayload> CODEC =
                CustomPacketPayload.codec(CapabilitiesPayload::write, CapabilitiesPayload::read);

        public static CapabilitiesPayload from(ClientCapabilities caps) {
            return new CapabilitiesPayload(
                    caps.getProtocolVersion(),
                    caps.supportsItemIconRendering(),
                    caps.getRendererVersion(),
                    caps.getSupportedIconSize(),
                    caps.getSupportedImageFormat());
        }

        public ClientCapabilities toCapabilities() {
            return new ClientCapabilities(protocolVersion, supportsRendering, rendererVersion, iconSize, imageFormat);
        }

        private static CapabilitiesPayload read(FriendlyByteBuf buf) {
            return new CapabilitiesPayload(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(100),
                    buf.readVarInt(),
                    buf.readUtf(16));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(protocolVersion);
            buf.writeBoolean(supportsRendering);
            buf.writeUtf(rendererVersion == null ? "" : rendererVersion, 100);
            buf.writeVarInt(iconSize);
            buf.writeUtf(imageFormat == null ? "" : imageFormat, 16);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestPayload(UUID requestId, int protocolVersion, int iconSize,
                                 List<ItemEntry> items) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(REQUEST_ID);
        public static final StreamCodec<FriendlyByteBuf, RequestPayload> CODEC =
                CustomPacketPayload.codec(RequestPayload::write, RequestPayload::read);

        public record ItemEntry(String renderKey, int slot, byte[] stackBytes) {
        }

        public static RequestPayload from(ItemIconRenderRequest request) {
            List<ItemEntry> entries = new ArrayList<>();
            for (RenderItemPayload item : request.getItems()) {
                entries.add(new ItemEntry(item.getRenderKey(), item.getSlot(), item.getItemStackBytes()));
            }
            return new RequestPayload(request.getRequestId(), request.getProtocolVersion(),
                    request.getIconSize(), entries);
        }

        public ItemIconRenderRequest toRequest() {
            List<RenderItemPayload> payloads = new ArrayList<>();
            for (ItemEntry entry : items) {
                payloads.add(new RenderItemPayload(entry.renderKey(), entry.slot(), entry.stackBytes()));
            }
            return new ItemIconRenderRequest(requestId, protocolVersion, iconSize, payloads);
        }

        private static RequestPayload read(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            int protocol = buf.readVarInt();
            int size = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > ItemIconRenderProtocol.MAX_ITEMS_PER_REQUEST) {
                throw new IllegalArgumentException("invalid item count");
            }
            List<ItemEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String key = buf.readUtf(128);
                int slot = buf.readVarInt();
                int len = buf.readVarInt();
                if (len < 0 || len > ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES) {
                    throw new IllegalArgumentException("invalid stack bytes");
                }
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                entries.add(new ItemEntry(key, slot, bytes));
            }
            return new RequestPayload(id, protocol, size, entries);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(requestId);
            buf.writeVarInt(protocolVersion);
            buf.writeVarInt(iconSize);
            buf.writeVarInt(items.size());
            for (ItemEntry entry : items) {
                buf.writeUtf(entry.renderKey(), 128);
                buf.writeVarInt(entry.slot());
                byte[] bytes = entry.stackBytes() == null ? new byte[0] : entry.stackBytes();
                buf.writeVarInt(bytes.length);
                buf.writeBytes(bytes);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ResponsePayload(UUID requestId, int protocolVersion, String rendererVersion,
                                  List<IconEntry> icons) implements CustomPacketPayload {
        public static final Type<ResponsePayload> TYPE = new Type<>(RESPONSE_ID);
        public static final StreamCodec<FriendlyByteBuf, ResponsePayload> CODEC =
                CustomPacketPayload.codec(ResponsePayload::write, ResponsePayload::read);

        public record IconEntry(String renderKey, String status, byte[] png, String sha256, String error) {
        }

        public static ResponsePayload from(ItemIconRenderResponse response) {
            List<IconEntry> entries = new ArrayList<>();
            for (IconRenderResult icon : response.getIcons()) {
                entries.add(new IconEntry(
                        icon.getRenderKey(),
                        icon.getStatus().name().toLowerCase(),
                        icon.getPngBytes(),
                        icon.getClientSha256(),
                        icon.getErrorCode()));
            }
            return new ResponsePayload(response.getRequestId(), response.getProtocolVersion(),
                    response.getRendererVersion(), entries);
        }

        public ItemIconRenderResponse toResponse() {
            List<IconRenderResult> results = new ArrayList<>();
            for (IconEntry entry : icons) {
                if ("rendered".equalsIgnoreCase(entry.status())) {
                    results.add(IconRenderResult.rendered(entry.renderKey(), entry.png(), entry.sha256()));
                } else if ("unsupported".equalsIgnoreCase(entry.status())) {
                    results.add(IconRenderResult.unsupported(entry.renderKey(), entry.error()));
                } else {
                    results.add(IconRenderResult.failed(entry.renderKey(), entry.error()));
                }
            }
            return new ItemIconRenderResponse(requestId, protocolVersion, rendererVersion, results);
        }

        private static ResponsePayload read(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            int protocol = buf.readVarInt();
            String renderer = buf.readUtf(100);
            int count = buf.readVarInt();
            if (count < 0 || count > ItemIconRenderProtocol.MAX_ITEMS_PER_REQUEST) {
                throw new IllegalArgumentException("invalid icon count");
            }
            List<IconEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String key = buf.readUtf(128);
                String status = buf.readUtf(32);
                int len = buf.readVarInt();
                if (len < 0 || len > ItemIconRenderProtocol.MAX_PNG_BYTES) {
                    throw new IllegalArgumentException("invalid png size");
                }
                byte[] png = null;
                if (len > 0) {
                    png = new byte[len];
                    buf.readBytes(png);
                }
                String sha = buf.readUtf(64);
                String err = buf.readUtf(64);
                entries.add(new IconEntry(key, status, png, sha.isEmpty() ? null : sha, err.isEmpty() ? null : err));
            }
            return new ResponsePayload(id, protocol, renderer, entries);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(requestId);
            buf.writeVarInt(protocolVersion);
            buf.writeUtf(rendererVersion == null ? "" : rendererVersion, 100);
            buf.writeVarInt(icons.size());
            for (IconEntry entry : icons) {
                buf.writeUtf(entry.renderKey(), 128);
                buf.writeUtf(entry.status() == null ? "failed" : entry.status(), 32);
                byte[] png = entry.png() == null ? new byte[0] : entry.png();
                buf.writeVarInt(png.length);
                if (png.length > 0) {
                    buf.writeBytes(png);
                }
                buf.writeUtf(entry.sha256() == null ? "" : entry.sha256(), 64);
                buf.writeUtf(entry.error() == null ? "" : entry.error(), 64);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
