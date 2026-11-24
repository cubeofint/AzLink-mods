package com.azuriom.azlink.forge.kits;

import com.azuriom.azlink.common.kits.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class ForgeKitCatalog implements PlatformKitCatalog {
    private Object manager() throws ReflectiveOperationException {
        return Class.forName("dev.ftb.mods.ftbessentials.kit.KitManager").getMethod("getInstance").invoke(null);
    }
    public List<String> names() {
        try {
            Object manager = manager();
            List<String> names = new ArrayList<>();
            for (Object kit : (Collection<?>) manager.getClass().getMethod("allKits").invoke(manager))
                names.add((String) kit.getClass().getMethod("getKitName").invoke(kit));
            return names;
        } catch (ReflectiveOperationException absent) { throw new IllegalStateException("FTB Essentials kit API unavailable", absent); }
    }
    @SuppressWarnings("unchecked")
    public Definition read(String name) {
        try {
            Object manager = manager();
            Object kit = ((Optional<?>) manager.getClass().getMethod("get", String.class).invoke(manager, name))
                    .orElseThrow(() -> new IllegalArgumentException("Kit not found: " + name));
            long cooldown = ((Number) kit.getClass().getMethod("getCooldown").invoke(kit)).longValue();
            return new Definition((String) kit.getClass().getMethod("getKitName").invoke(kit),
                    ForgeKitItems.snapshot((List<ItemStack>) kit.getClass().getMethod("getItems").invoke(kit)),
                    (int) Math.min(Integer.MAX_VALUE, Math.max(0, cooldown / 1000)));
        } catch (ReflectiveOperationException absent) { throw new IllegalStateException("FTB Essentials kit API unavailable", absent); }
    }
    public int credit(UUID player, String key, int quantity, String receipt) {
        return ReflectiveKitCredit.credit("Crazer.cubeofinterest.cointcoregto.kit.AzLinkKitBridge", player, key, quantity, receipt);
    }
}
