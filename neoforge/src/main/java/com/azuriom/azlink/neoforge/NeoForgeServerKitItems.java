package com.azuriom.azlink.neoforge;

import com.azuriom.azlink.common.kits.manifest.ServerKitItem;
import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side helper: ItemStack → opaque network bytes + metadata snapshot builder input.
 */
public final class NeoForgeServerKitItems {

    private NeoForgeServerKitItems() {
    }

    public static byte[] encodeItemStack(MinecraftServer server, ItemStack stack) {
        RegistryAccess access = server.registryAccess();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        ItemStack.STREAM_CODEC.encode(buf, stack);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    public static ServerKitItem of(MinecraftServer server, ItemStack stack, ItemStackSnapshot snapshot) {
        return new ServerKitItem(snapshot, encodeItemStack(server, stack));
    }
}
