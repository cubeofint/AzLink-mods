package com.azuriom.azlink.neoforge.mixin.ftbessentials;

import com.azuriom.azlink.neoforge.kits.FtbKitManifestHooks;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbessentials.kit.Kit;
import dev.ftb.mods.ftbessentials.kit.KitManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After successful FTB kit create/replace/delete, notify AzLink (site publish).
 */
@Mixin(value = KitManager.class, remap = false)
public abstract class KitManagerMixin {

    @Inject(method = "addKit", at = @At("RETURN"), remap = false)
    private void azlink$afterAddKit(Kit kit, boolean replace, CallbackInfo ci) {
        FtbKitManifestHooks.onKitSaved(kit);
    }

    @Inject(method = "deleteKit", at = @At("RETURN"), remap = false)
    private void azlink$afterDeleteKit(String name, CallbackInfo ci) throws CommandSyntaxException {
        FtbKitManifestHooks.onKitDeleted(name);
    }
}
