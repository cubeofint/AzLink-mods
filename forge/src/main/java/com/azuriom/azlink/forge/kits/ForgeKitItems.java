package com.azuriom.azlink.forge.kits;

import com.azuriom.azlink.common.kits.manifest.ServerKitItem;
import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;
import com.azuriom.azlink.common.kits.manifest.model.EnchantmentRef;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderProtocol;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.fml.ModList;
import java.util.*;

public final class ForgeKitItems {
    private ForgeKitItems() {}
    public static List<ServerKitItem> snapshot(List<ItemStack> stacks) {
        List<ServerKitItem> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String mod = ModList.get().getModContainerById(id.getNamespace()).map(m -> m.getModInfo().getDisplayName()).orElse(id.getNamespace());
            List<EnchantmentRef> enchants = new ArrayList<>();
            EnchantmentHelper.getEnchantments(stack).forEach((e, level) -> enchants.add(new EnchantmentRef(BuiltInRegistries.ENCHANTMENT.getKey(e).toString(), level)));
            ItemStackSnapshot snapshot = ItemStackSnapshot.builder().slot(result.size()).registryId(id.toString())
                    .semanticKey(id.getPath().substring(0, Math.min(64, id.getPath().length())))
                    .displayName(stack.getHoverName().getString()).quantity(stack.getCount()).modName(mod)
                    .rarity(stack.getRarity().name().toLowerCase(Locale.ROOT)).enchantments(enchants).build();
            result.add(new ServerKitItem(snapshot, encode(stack)));
        }
        return result;
    }
    public static byte[] encode(ItemStack stack) {
        CompoundTag tag = stack.save(new CompoundTag());
        tag.putInt("AzLinkCount", stack.getCount());
        tag.putByte("Count", (byte) 1);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(tag);
            if (buffer.readableBytes() > ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES) throw new IllegalArgumentException("Kit item NBT too large");
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); return bytes;
        } finally { buffer.release(); }
    }
    public static ItemStack decode(byte[] bytes) {
        if (bytes.length > ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES) throw new IllegalArgumentException("Kit item NBT too large");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            CompoundTag tag = buffer.readNbt();
            if (tag == null || buffer.isReadable()) throw new IllegalArgumentException("Invalid kit item NBT");
            int count = tag.getInt("AzLinkCount"); tag.remove("AzLinkCount");
            ItemStack stack = ItemStack.of(tag); stack.setCount(count);
            if (stack.isEmpty()) throw new IllegalArgumentException("Missing kit item");
            return stack;
        } finally { buffer.release(); }
    }
}
