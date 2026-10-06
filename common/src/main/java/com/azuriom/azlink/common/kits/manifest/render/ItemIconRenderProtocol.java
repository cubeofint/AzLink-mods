package com.azuriom.azlink.common.kits.manifest.render;

/**
 * Client↔server item-icon render protocol (independent from Shop API protocol_version).
 */
public final class ItemIconRenderProtocol {

    public static final int VERSION = 1;
    public static final int ICON_SIZE = 64;
    public static final String IMAGE_FORMAT = "png";

    public static final int MAX_ITEMS_PER_REQUEST = 64;
    public static final int MAX_ITEM_STACK_BYTES = 64 * 1024;
    public static final int MAX_PNG_BYTES = 262144;
    public static final long REQUEST_TIMEOUT_MS = 20_000L;
    public static final int MAX_IN_FLIGHT_PER_PLAYER = 2;

    private ItemIconRenderProtocol() {
    }
}
