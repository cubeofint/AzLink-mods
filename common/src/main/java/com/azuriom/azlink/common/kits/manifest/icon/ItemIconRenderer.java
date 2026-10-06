package com.azuriom.azlink.common.kits.manifest.icon;

/**
 * Platform SPI: render an item icon to PNG bytes.
 * Common module does not depend on Minecraft rendering.
 */
public interface ItemIconRenderer<T> {

    /**
     * @return PNG bytes, or {@code null} if rendering is unavailable
     */
    byte[] renderPng(T itemStack);
}
