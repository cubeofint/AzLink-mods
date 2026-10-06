package com.azuriom.azlink.common.kits.manifest;

/**
 * Limits aligned with {@code shop.kit_manifest} on the website.
 */
public final class KitManifestLimits {

    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_ITEMS = 54;
    public static final int MAX_TOOLTIP_LINES = 32;
    public static final int MAX_TOOLTIP_LINE_LENGTH = 500;
    public static final int MAX_ATTRIBUTES = 32;
    public static final int MAX_ENCHANTMENTS = 32;
    public static final int MAX_METADATA_KEYS = 32;
    public static final int MAX_STRING = 255;
    public static final int MAX_DISPLAY_NAME = 100;
    public static final int MAX_SEMANTIC_KEY = 64;
    public static final int MAX_REGISTRY_ID = 191;
    public static final int MAX_ICON_BYTES = 262144;
    public static final int MAX_PAYLOAD_BYTES = 262144;

    private KitManifestLimits() {
    }
}
