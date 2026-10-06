package com.azuriom.azlink.neoforge.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies FTB Essentials kit hooks only when {@code ftbessentials} is present.
 */
public final class AzLinkMixinPlugin implements IMixinConfigPlugin {

    private static final String FTB_PREFIX = "com.azuriom.azlink.neoforge.mixin.ftbessentials.";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(FTB_PREFIX)) {
            return isModLoaded("ftbessentials");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isModLoaded(String modId) {
        return LoadingModList.get().getMods().stream()
                .anyMatch(mod -> modId.equals(mod.getModId()));
    }
}
