package com.azuriom.azlink.common.kits.manifest;

import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public Java API for Kit Manifest Sync (dedicated server only).
 *
 * <p>FTB Essentials hooks (NeoForge) and other callers use
 * {@code plugin.getKitManifestSyncCoordinator().publish(...)} — never from the Minecraft client.</p>
 */
public interface KitManifestProvider {

    /**
     * Legacy/simple sync when PNG bytes are already server-side (tests / offline icons).
     * Still dedicated-server + site-token only.
     */
    CompletableFuture<KitManifestSyncResult> sync(String deliveryKey, int manifestVersion,
                                                  List<ItemStackSnapshot> items);

    /**
     * Upload a single PNG icon (content-addressed). Dedicated server only.
     */
    CompletableFuture<String> uploadIconPng(byte[] pngBytes);
}
