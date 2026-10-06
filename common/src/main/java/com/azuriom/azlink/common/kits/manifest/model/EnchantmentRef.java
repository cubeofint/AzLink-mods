package com.azuriom.azlink.common.kits.manifest.model;

public final class EnchantmentRef {

    private final String name;
    private final int level;

    public EnchantmentRef(String name, int level) {
        this.name = name;
        this.level = level;
    }

    public String getName() {
        return this.name;
    }

    public int getLevel() {
        return this.level;
    }
}
