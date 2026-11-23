# Kit Manifest Sync (AzLink ↔ main-site)

Canonical side: **main-site** shop plugin for commercial fields (price, privilege links).
Minecraft is source of truth for **inventory** and kit shell lifecycle via AzLink.

## Flow (create / change on Minecraft)

1. FTB `KitManager.addKit` (also CointCore starter) → AzLink NeoForge hook
2. Extract `ItemStack[]` → metadata + client icon render (dedicated server only)
3. `POST .../kits/manifests` with `ensure_kit=true`, `auto_apply=true`
   - creates commercial Kit shell if missing (`delivery_key` = kit name)
   - uploads icons + manifest
   - **auto-applies** inventory (no admin Apply step)
4. Site kit becomes enabled with `current_manifest_id`

## Flow (delete on Minecraft)

1. FTB `KitManager.deleteKit` → `DELETE .../kits/{delivery_key}`
2. Site soft-disables (or hard-deletes if unused)

## Public API

```java
plugin.getKitManifestSyncCoordinator().publish(
    deliveryKey, version, serverKitItems, preferredAdminUuid,
    kitName, cooldownSeconds, /* mirrorToSite */ true);
plugin.getKitManifestSyncCoordinator().retireOnSite(deliveryKey);
```

Commercial price / privilege bindings remain editable in admin; inventory mirrors from Minecraft.

## Large kits

Manifests support up to 256 item positions. The website's `shop.kit_manifest.max_items`
must match; `max_payload_bytes` should be 2097152 (2 MiB). Existing metadata and PNG
validation limits remain in place.

Icon rendering is split into sequential requests of at most 64 items. The render
protocol and packet limits are unchanged, so existing compatible clients can render
large kits without a client update.
