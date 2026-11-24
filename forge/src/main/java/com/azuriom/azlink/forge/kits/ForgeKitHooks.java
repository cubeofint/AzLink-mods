package com.azuriom.azlink.forge.kits;

import com.azuriom.azlink.forge.AzLinkForgeMod;

public final class ForgeKitHooks {
    private ForgeKitHooks() {}
    public static void saved(String name) { changed(name, false); }
    public static void removed(String name) { changed(name, true); }
    private static void changed(String name, boolean removed) {
        AzLinkForgeMod mod = AzLinkForgeMod.instance();
        if (mod == null || mod.getPlugin().getPlatformKitPublisher() == null) return;
        if (removed) mod.getPlugin().getPlatformKitPublisher().removed(name);
        else mod.getPlugin().getPlatformKitPublisher().saved(name);
    }
}
