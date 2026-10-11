# Site patch: per-server chat prefix selection (`prefix-selection`)

Production site (`/var/www/main-site`, Laravel 12 / Azuriom) is not in this git repo.
Apply **on top of** the live gluon series (`gluon-ledger-queue`, `gluon-movements-delivery`,
`gluon-server-adjust` 0001–0005) and `hwid-admin`. Do **not** copy `src/` onto the
server as a tree — `src/` is the readable source of the new files; `git apply` the
numbered patches from the site root.

These patches are **not** applied on production by this repository. Do not deploy
from here.

`ServerOperationQueue` / `currency_server_operations` stay gluon-only (amount +
direction). Prefixes use a dedicated `prefix_operations` table. Gluon visibility
(`CurrencyServerMovement::visibleTo`) is not touched.

Game-side contract is implemented in parallel by cointcore. Keep it exact.
The AzLink mod exposes the names cointcore reflects on **`CoinOperationsBridge`**
(`postJson` / `getJson` / `reportPrefixes` / `fetchPrefixOperations` / `ackPrefix`)
and the same methods on [`PrefixBridge`](../../common/src/main/java/com/azuriom/azlink/common/prefixes/PrefixBridge.java).
HttpClient paths are `/azlink/prefixes/...` → site `/api/azlink/prefixes/...`.
See [`common/docs/PREFIX_BRIDGE.md`](../../common/docs/PREFIX_BRIDGE.md).

## What it adds

- Tables: `prefix_catalog`, `user_server_prefixes`, `prefix_operations` (up/down).
- AzLink API (`server.token`, same as other AzLink endpoints):
  - `POST /api/azlink/prefixes/report` `{players:[{uuid,name,available:[{group,prefix_raw,category}],selected,locked_staff}]}`
  - `GET  /api/azlink/prefixes/operations` pending `prefix_select` `{op_id,type,uuid,group}` with lease
  - `POST /api/azlink/prefixes/ack` `{op_id,status:applied|rejected,reason,selected}` (idempotent)
- Cabinet tab «Префиксы»: per-server pills, rendered preview (`&` / MiniMessage → HTML),
  current selection, «Авто», select → queue, pending/applied/rejected, staff lock
  «Префикс стаффа нельзя изменить». Disabled/hidden catalog rows are not shown.
- Admin: catalog CRUD with preview, player prefixes per server + reset to Авто,
  operation log.
- Flag `prefixes.enabled` (default **off**, endpoints 404). `prefixes.lease_seconds` default `30`.
- `php artisan prefixes:check` — read-only dry-run.

## JSON contract

### Game → site: `POST /api/azlink/prefixes/report`

```json
{"players":[{"uuid":"f3fc162d-d344-32fa-8c9a-0b987b0791cf","name":"Nick","available":[{"group":"vip","prefix_raw":"<gold>[VIP]</gold>","category":"donate"}],"selected":"vip","locked_staff":false}]}
```

User link: `users.game_id` (dashed or undashed UUID), then exact `name`. Unknown players are skipped.

### Site → game: `GET /api/azlink/prefixes/operations?limit=50`

```json
{"operations":[{"op_id":"9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b","type":"prefix_select","uuid":"f3fc162d-d344-32fa-8c9a-0b987b0791cf","group":"vip"}]}
```

`group` is `null` for Авто. Taking a row sets `leased_until`; another poll skips it until the lease expires.

### Game → site: `POST /api/azlink/prefixes/ack`

```json
{"op_id":"9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b","status":"applied","reason":null,"selected":"vip"}
{"op_id":"9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b","status":"rejected","reason":"unknown_group","selected":null}
```

`status` is **`applied` | `rejected`** (not the coin-queue `failed`). Replays of a final op are no-ops.

## Apply (after a DB dump + files backup, with confirmation)

```
cd /var/www/main-site
git apply /path/to/AzLink-mods/site-patches/prefix-selection/0001-Prefix-selection-schema-API-queue.patch
git apply /path/to/AzLink-mods/site-patches/prefix-selection/0002-Prefix-selection-cabinet-admin.patch
php artisan migrate --force
php artisan prefixes:check
# enable only when ready:
# Setting::updateSettings(['prefixes.enabled' => '1']);
```

Optional: add `App\Providers\PrefixSelectionServiceProvider::class` to
`bootstrap/providers.php` so the cabinet POST is also `/user/prefixes/select`.
Without it the form uses `POST /api/profile/prefixes/select` (`web`+`auth`) from `routes/api.php`.

Admin URLs: `/admin/prefixes`, `/admin/prefixes/operations`. Add a sidebar link to
`route('admin.prefixes.index')` if you want it in the nav (lang key `prefixes.admin.nav`).

Rollback: restore the changed files from the backup, then:

```
php artisan migrate:rollback --step=1
```

(`step=1` drops the three tables and the two settings). Flags can be set back to `0`
without rollback.

## Tests

```
cd /var/www/main-site
php artisan test --filter=PrefixSelectionTest
```

Run the file directly (`--filter` avoids unrelated existing tests). The series also adds `php artisan prefixes:check` for a no-write sanity pass
on the site. In this repo, `php check-formatter.php` (no Laravel) checks
MiniMessage / legacy → HTML conversion.

## Patch order

1. `0001` — migrations, models, services, AzLink API + cabinet POST, artisan check, Feature tests.
2. `0002` — cabinet tab, admin catalog/log/user reset, lang, theme partials, admin/profile route hunks.
