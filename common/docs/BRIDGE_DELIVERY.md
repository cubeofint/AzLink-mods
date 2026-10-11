# Bridge delivery (privileges and kits)

Shop privileges and kit credits are delivered through `shop_minecraft_operations`
(`MinecraftOperationTransport` poll/ack + `SemanticExecutorService`). Console-command
packages are unchanged.

## Operation types

| type | payload | effect |
| --- | --- | --- |
| `privilege_reconcile` / `privilege_revoke` | existing entitlement + capabilities | LuckPerms group + meta/permissions |
| `kit_redeem` | existing kit_key + redemption_id | one FTB/cointcore credit via delivery key |
| `group_grant` | `{uuid, group, expires_at, entitlement_key, version}` | grant group, replace same entitlement, drop older version |
| `group_revoke` | `{uuid, group, entitlement_key, version}` | remove the stored grant |
| `kit_credit` | `{uuid, kit, qty}` | `KitCreditService.addCredits(UUID, String, int)` |

`kit_credit` is a paid item: credits **never expire**. There is no `valid_until`.

Required cointcore API (implemented in parallel in the cointcore repo):

```
com.mawlee.cointcore.kit.KitCreditService.addCredits(java.util.UUID, String, int)
```

## LuckPerms

Nodes are written **without** `azlink-entitlement=<id>` context (no ContextCalculator
provides it, so those groups never applied). Granted group / meta / expiry are stored
in `EntitlementVersionLedger` and revoked by exact match. Leftover context-tagged nodes
from older builds are cleared on the next reconcile/revoke.

## Addressing

UUID only (`users.game_id` on the site). Offline-safe. Ack after a successful local
write. Idempotent via the operation ledger (`operation_id`) and entitlement versions.
