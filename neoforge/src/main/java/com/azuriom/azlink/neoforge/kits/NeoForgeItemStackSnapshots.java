package com.azuriom.azlink.neoforge.kits;

import com.azuriom.azlink.common.kits.manifest.ServerKitItem;
import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;
import com.azuriom.azlink.common.kits.manifest.model.EnchantmentRef;
import com.azuriom.azlink.neoforge.NeoForgeServerKitItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side ItemStack → {@link ItemStackSnapshot} + {@link ServerKitItem}.
 * Icons are filled later via client render courier; this path is metadata-authoritative.
 */
public final class NeoForgeItemStackSnapshots {

    private NeoForgeItemStackSnapshots() {
    }

    public static List<ServerKitItem> fromStacks(MinecraftServer server, List<ItemStack> stacks) {
        List<ServerKitItem> out = new ArrayList<ServerKitItem>();
        if (stacks == null || server == null) {
            return out;
        }
        int slot = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            out.add(NeoForgeServerKitItems.of(server, stack, snapshot(stack, slot)));
            slot++;
        }
        return out;
    }

    public static ItemStackSnapshot snapshot(ItemStack stack, int slot) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String registryId = id == null ? "minecraft:air" : id.toString();
        String semanticKey = id == null ? "air" : id.getPath();
        if (semanticKey.length() > 64) {
            semanticKey = semanticKey.substring(0, 64);
        }

        String displayName = stack.getHoverName().getString();
        if (displayName == null || displayName.isEmpty()) {
            displayName = registryId;
        }

        String modName = null;
        if (id != null) {
            String namespace = id.getNamespace();
            Optional<? extends net.neoforged.fml.ModContainer> mod = ModList.get().getModContainerById(namespace);
            modName = mod.map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
        }

        String rarity = rarityName(stack.getRarity());

        ItemStackSnapshot.Builder builder = ItemStackSnapshot.builder()
                .slot(slot)
                .semanticKey(semanticKey)
                .registryId(registryId)
                .displayName(displayName)
                .quantity(Integer.valueOf(stack.getCount()))
                .modName(modName)
                .rarity(rarity);

        Integer customModelData = null;
        var customModel = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (customModel != null) {
            customModelData = Integer.valueOf(customModel.value());
        }
        if (customModelData != null) {
            builder.putMetadata("custom_model_data", customModelData);
        }

        List<EnchantmentRef> enchantments = new ArrayList<EnchantmentRef>();
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        enchants.entrySet().forEach(entry -> {
            Holder<net.minecraft.world.item.enchantment.Enchantment> holder = entry.getKey();
            String name = holder.getRegisteredName();
            if (name == null || name.isEmpty()) {
                name = holder.unwrapKey()
                        .map(k -> k.location().toString())
                        .orElse("unknown");
            }
            enchantments.add(new EnchantmentRef(name, entry.getIntValue()));
        });
        builder.enchantments(enchantments);

        return builder.build();
    }

    private static String rarityName(Rarity rarity) {
        if (rarity == null) {
            return "common";
        }
        return rarity.getSerializedName();
    }
}
