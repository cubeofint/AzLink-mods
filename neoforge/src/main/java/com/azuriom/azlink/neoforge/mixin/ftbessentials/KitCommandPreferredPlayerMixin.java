package com.azuriom.azlink.neoforge.mixin.ftbessentials;

import com.azuriom.azlink.neoforge.kits.FtbKitManifestHooks;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prefer the admin who runs {@code /ftb_kit create*} as the icon render worker.
 */
@Mixin(value = dev.ftb.mods.ftbessentials.commands.impl.kit.KitCommand.class, remap = false)
public abstract class KitCommandPreferredPlayerMixin {

    @Inject(method = "createKitFromPlayer", at = @At("HEAD"), remap = false)
    private static void azlink$capturePreferredFromPlayer(CommandSourceStack source, String name,
                                                          String cooldown, boolean hotbarOnly,
                                                          CallbackInfoReturnable<Integer> cir)
            throws CommandSyntaxException {
        capture(source);
    }

    @Inject(method = "createKitFromPlayer", at = @At("RETURN"), remap = false)
    private static void azlink$clearPreferredFromPlayer(CommandSourceStack source, String name,
                                                        String cooldown, boolean hotbarOnly,
                                                        CallbackInfoReturnable<Integer> cir) {
        FtbKitManifestHooks.clearPreferredRenderPlayer();
    }

    @Inject(method = "createKitFromBlock", at = @At("HEAD"), remap = false)
    private static void azlink$capturePreferredFromBlock(CommandSourceStack source, String name,
                                                         String cooldown,
                                                         CallbackInfoReturnable<Integer> cir)
            throws CommandSyntaxException {
        capture(source);
    }

    @Inject(method = "createKitFromBlock", at = @At("RETURN"), remap = false)
    private static void azlink$clearPreferredFromBlock(CommandSourceStack source, String name,
                                                       String cooldown,
                                                       CallbackInfoReturnable<Integer> cir) {
        FtbKitManifestHooks.clearPreferredRenderPlayer();
    }

    private static void capture(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            FtbKitManifestHooks.setPreferredRenderPlayer(player.getUUID());
        }
    }
}
