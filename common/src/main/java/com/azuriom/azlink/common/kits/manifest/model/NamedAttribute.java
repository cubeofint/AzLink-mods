package com.azuriom.azlink.common.kits.manifest.model;

public final class NamedAttribute {

    private final String name;
    private final String value;

    public NamedAttribute(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public String getValue() {
        return this.value;
    }
}
