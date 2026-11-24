package com.azuriom.azlink.forge.legacy.kits;

import com.azuriom.azlink.common.kits.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import java.util.*;

public final class LegacyKitCatalog implements PlatformKitCatalog {
    private Class<?> manager() throws ClassNotFoundException { return Class.forName("coint.commands.kit.KitManager"); }
    @SuppressWarnings("unchecked")
    public List<String> names() {
        try { return new ArrayList<String>((Collection<String>) manager().getMethod("getKitNames", MinecraftServer.class).invoke(null, MinecraftServer.getServer())); }
        catch (ReflectiveOperationException absent) { throw new IllegalStateException("CointCore GTNH kit API unavailable", absent); }
    }
    @SuppressWarnings("unchecked")
    public Definition read(String name) {
        try {
            Object kit = manager().getMethod("getKit", MinecraftServer.class, String.class).invoke(null, MinecraftServer.getServer(), name);
            if (kit == null) throw new IllegalArgumentException("Kit not found: " + name);
            return new Definition((String) kit.getClass().getMethod("getName").invoke(kit),
                    LegacyKitItems.snapshot((List<ItemStack>) kit.getClass().getMethod("getItems").invoke(kit)), null);
        } catch (ReflectiveOperationException absent) { throw new IllegalStateException("CointCore GTNH kit API unavailable", absent); }
    }
    public int credit(UUID player, String key, int quantity, String receipt) {
        return ReflectiveKitCredit.credit("coint.commands.kit.AzLinkKitBridge", player, key, quantity, receipt);
    }
}
