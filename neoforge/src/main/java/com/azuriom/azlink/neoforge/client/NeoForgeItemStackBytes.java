package com.azuriom.azlink.neoforge.client;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

final class NeoForgeItemStackBytes {

    private NeoForgeItemStackBytes() {
    }

    static ItemStack decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return ItemStack.EMPTY;
        }
        RegistryAccess access = registryAccess();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), access);
        return ItemStack.STREAM_CODEC.decode(buf);
    }

    static byte[] encode(ItemStack stack) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
        ItemStack.STREAM_CODEC.encode(buf, stack);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    private static RegistryAccess registryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.registryAccess();
        }
        if (mc.level != null) {
            return mc.level.registryAccess();
        }
        throw new IllegalStateException("no registry access");
    }
}
