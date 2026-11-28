package com.azuriom.azlink.forge.legacy.kits;

import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;
import com.azuriom.azlink.common.kits.manifest.ServerKitItem;
import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;
import com.azuriom.azlink.common.kits.manifest.model.EnchantmentRef;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderProtocol;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.Loader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.enchantment.Enchantment;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public final class LegacyKitItems {
    private static final Pattern UNSUPPORTED_KEY_CHARS = Pattern.compile("[^a-z0-9._-]+");

    private LegacyKitItems() {}
    public static List<ServerKitItem> snapshot(List<ItemStack> stacks) {
        List<ServerKitItem> result = new ArrayList<ServerKitItem>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null || stack.stackSize < 1) continue;
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            if (id == null) throw new IllegalArgumentException("Unregistered kit item");
            String registry = id.modId + ":" + id.name;
            String mod = Loader.instance().getIndexedModList().containsKey(id.modId) ? Loader.instance().getIndexedModList().get(id.modId).getName() : id.modId;
            List<EnchantmentRef> enchants = new ArrayList<EnchantmentRef>();
            NBTTagList list = stack.getEnchantmentTagList();
            if (list != null) for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound e = list.getCompoundTagAt(i); int index = e.getShort("id");
                Enchantment enchantment = index >= 0 && index < Enchantment.enchantmentsList.length ? Enchantment.enchantmentsList[index] : null;
                enchants.add(new EnchantmentRef(enchantment == null ? "enchantment:" + index : enchantment.getName(), e.getShort("lvl")));
            }
            ItemStackSnapshot snapshot = ItemStackSnapshot.builder().slot(result.size()).registryId(registry)
                    .semanticKey(semanticKey(id.name)).displayName(stack.getDisplayName())
                    .quantity(stack.stackSize).modName(mod).rarity(stack.getRarity().name().toLowerCase(Locale.ROOT)).enchantments(enchants).build();
            result.add(new ServerKitItem(snapshot, encode(stack)));
        }
        return result;
    }

    static String semanticKey(String registryName) {
        String key = UNSUPPORTED_KEY_CHARS.matcher(registryName.toLowerCase(Locale.ROOT)).replaceAll("_");
        if (key.isEmpty() || !Character.isLetterOrDigit(key.charAt(0))) {
            key = "item_" + key;
        }
        return key.substring(0, Math.min(KitManifestLimits.MAX_SEMANTIC_KEY, key.length()));
    }

    public static byte[] encode(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound(); stack.writeToNBT(tag);
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (id == null) throw new IllegalArgumentException("Unregistered kit item");
        tag.setString("AzLinkRegistry", id.modId + ":" + id.name); tag.setInteger("AzLinkCount", stack.stackSize); tag.setByte("Count", (byte) 1);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.write(tag, new DataOutputStream(bytes));
            if (bytes.size() > ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES) throw new IOException("Kit item NBT too large");
            return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalArgumentException("Cannot encode kit item", error); }
    }
    public static ItemStack decode(byte[] bytes) throws IOException {
        if (bytes.length > ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES) throw new IOException("Kit item NBT too large");
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        NBTTagCompound tag = CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(2097152L));
        if (input.available() != 0) throw new IOException("Trailing kit item data");
        String[] id = tag.getString("AzLinkRegistry").split(":", 2);
        Item item = id.length == 2 ? GameRegistry.findItem(id[0], id[1]) : null;
        if (item == null) throw new IOException("Kit item missing on this client");
        int count = tag.getInteger("AzLinkCount"); tag.removeTag("AzLinkRegistry"); tag.removeTag("AzLinkCount");
        tag.setShort("id", (short) Item.getIdFromItem(item));
        ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
        if (stack == null || count < 1) throw new IOException("Invalid kit item");
        stack.stackSize = count; return stack;
    }
}
