package com.azuriom.azlink.common.platform;

public enum PlatformType {

    FORGE("Forge"),
    NEOFORGE("NeoForge");

    private final String name;

    PlatformType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
