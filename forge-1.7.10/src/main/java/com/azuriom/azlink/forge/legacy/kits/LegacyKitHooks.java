package com.azuriom.azlink.forge.legacy.kits;
import com.azuriom.azlink.forge.legacy.AzLinkForgeLegacyMod;
public final class LegacyKitHooks {
    private LegacyKitHooks() {}
    public static void saved(String name) { changed(name, false); }
    public static void removed(String name) { changed(name, true); }
    private static void changed(String name, boolean removed) {
        AzLinkForgeLegacyMod mod = AzLinkForgeLegacyMod.instance();
        if (mod == null || mod.getPlugin().getPlatformKitPublisher() == null) return;
        if (removed) mod.getPlugin().getPlatformKitPublisher().removed(name);
        else mod.getPlugin().getPlatformKitPublisher().saved(name);
    }
}
