# Client-assisted Item Icon Rendering (security model)

## Invariants

- **CLIENT → SITE direct HTTP?** NO
- **CLIENT has Azuriom-Link-Token?** NO
- **DEDICATED SERVER is sole site uploader?** YES

## Roles

| Side | Responsibilities |
|---|---|
| Admin Client AzLink | Capability handshake, ItemStack → 64×64 PNG, respond to server only |
| Dedicated Server AzLink | Authoritative metadata, render_key, cache, PNG validate+SHA-256, HTTP upload |
| main-site | Store icons/manifests, PENDING_REVIEW |

## Public API (kit hooks)

```java
plugin.getKitManifestSyncCoordinator().publish(
    deliveryKey,
    manifestVersion,
    serverKitItems,      // ServerKitItem(snapshot, itemStackBytes)
    preferredAdminUuid   // render worker; no random fallback
);
```

NeoForge: FTB Essentials `KitManager.addKit` / `deleteKit` mixins call publish (create/replace) or log (delete).


## Protocol

`ITEM_ICON_RENDER_PROTOCOL_VERSION = 1` (separate from Shop API).

Packets: capabilities / render request / render response — never carry token or site URL.
