# Site patch: bridge delivery (`bridge-delivery`)

Production site (`/var/www/main-site`, Laravel 12 / Azuriom) is not in this git repo.
Apply **on top of** `gluon-ledger-queue`, `gluon-movements-delivery`, `gluon-server-adjust`,
`hwid-admin`, and `prefix-selection`. Do **not** copy `src/` onto the server as a tree —
`src/` is the readable source; `git apply` the numbered patches from the site root.

These patches are **not** applied on production by this repository. Do not deploy from here.

Game-side contract is in the AzLink NeoForge mod (`SemanticExecutorService` + LuckPerms +
cointcore). See [`common/docs/BRIDGE_DELIVERY.md`](../../common/docs/BRIDGE_DELIVERY.md).

## What it adds

- Setting `shop.configured_delivery.servers` — CSV of Azuriom server ids (same shape as
  `currency.server_queue_servers`). **Empty = nobody.** `tick()` and
  `MinecraftOperationDispatcher` act only for listed servers.
- Renewal: `Scheduled` purchases whose `starts_at` has arrived are dispatched;
  `expireCurrent` accepts a successor that is already `PendingActivation`; after expire
  the successor reconcile is dispatched immediately.
- Refund / chargeback: `privilege_revoke` for configured purchases (status `Refunded` /
  `Revoked`, plus a Payment `updated` hook when `payment_id` exists). Shop packages with
  a bridge group on trigger `refund` / `chargeback` / `expiration` emit `group_revoke`.
- Package command group `mode=bridge` in `shop_packages.commands`:

```json
[
  {"mode":"bridge","trigger":"purchase","server":9,"require_online":false,"actions":[
    {"op":"grant_group","group":"{rank_name}","duration":"package"},
    {"op":"add_kit_credits","kit":"starter","qty":"{quantity}"}
  ]},
  {"trigger":"purchase","server":9,"require_online":false,"commands":["say {player} classic"]}
]
```

  `qty` may be `{quantity}`, `{duration_months}`, or a number. Kit credits **never expire**
  (no `valid_until`). `duration: package` → `expires_at = max(current end, now) + N calendar
  months` from `shop_subscriptions.ends_at` / `user_package_states`.
  `operation_key = pkg:{payment_item_id}:{index}`. UUID from `users.game_id`; without one
  the row is stored as `failed:no_uuid` and shows in admin.
  If the target server is not listed, the group is skipped with a log (classic
  `commands` groups still use `sendCommands`).
- Admin **Доставки**: filters (server, player, type, status), attempts, `last_error`,
  Retry, Mark delivered manually, problem counter.
- `php artisan shop:bridge-delivery-check` — read-only dry-run.

## cointcore API

Required (implemented in parallel in the cointcore repo):

```
com.mawlee.cointcore.kit.KitCreditService.addCredits(java.util.UUID, String kit, int qty)
```

Kit credits are paid items and never expire. Privileges (LuckPerms groups) still have
an absolute `expires_at`.

## Apply (after a DB dump + files backup)

```
cd /var/www/main-site
git apply /path/to/AzLink-mods/site-patches/bridge-delivery/0001-bridge-delivery-new-files.patch
git apply /path/to/AzLink-mods/site-patches/bridge-delivery/0002-bridge-delivery-dispatcher-tick-admin.patch
git apply /path/to/AzLink-mods/site-patches/bridge-delivery/0003-bridge-delivery-package-enum-hooks.patch
php artisan migrate --force
php artisan shop:bridge-delivery-check
# then set:
# shop.configured_delivery.servers = 9
# shop.configured_delivery.enabled = 1
```

`0003` touches Azuriom shop core (`Package::dispatchCommands`, package form, operation
enum). If `git apply` fails on the enum, add these cases by hand:

```
case GroupGrant = 'group_grant';
case GroupRevoke = 'group_revoke';
case KitCredit = 'kit_credit';
```

Do **not** replace `MinecraftOperationType.php` if production has extra cases.

Include the package editor partial in the package form if 0003 cannot patch `_form.blade.php`:

```
@include('shop::admin.packages._bridge_commands')
```

Rollback: restore the changed files from the backup, then:

```
php artisan migrate:rollback --step=1
```

(`step=1` drops `shop.configured_delivery.servers`). Flags can be set back to `0` / `''`
without rollback.

## Tests

```
cd /var/www/main-site
php artisan test --filter=BridgeDeliveryTest
```

In this repo (no Laravel):

```
php site-patches/bridge-delivery/check-bridge.php
```

## Patch order

1. `0001` — new classes, migration, admin deliveries view, package bridge editor, unit tests, check command.
2. `0002` — dispatcher methods, configured-delivery tick/renewal/refund, admin filters/retry/mark, command output.
3. `0003` — Package::dispatchCommands, enum cases, ShopServiceProvider hooks, admin routes, lifecycle successor, package form include.
