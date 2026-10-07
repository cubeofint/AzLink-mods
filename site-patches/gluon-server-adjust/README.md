# Site patch: gluon per-server `adjust` queue (`gluon-server-adjust`)

Production site (`/var/www/main-site`) is not in this git repo. Apply these patches
**on top of** `gluon-ledger-queue` and `gluon-movements-delivery` (already live).
Do **not** apply the `baseline/` tree. These patches are **not** applied on production
by this repository. E2E will run on ATM10-Test against the prod site, test account only,
after explicit approval.

## What it adds

Site per-server gluon (`user_server_balances`, mc-azlink + queue) is authoritative.
The in-game cointcore wallet converges via `direction=adjust` operations. Game-side
changes (pay, trader, in-game admin) come back as movements with `deltas` and update
the site copy. No absolute overwrite in either direction. `adjust` never touches
`users.coins`.

- Setting `currency.server_queue_servers` (comma-separated server ids; **empty = all
  mc-azlink**). Helper `ServerOperationQueue::enabledFor($server)`: global flag on
  AND type `mc-azlink` AND (empty list or id listed). Prod can enable only server `#9`.
- `CurrencyServerSync::adjust(...)`: queue on → create `adjust` op (signed amount) and
  apply the delta to the site copy immediately (`TYPE_SERVER_ADJUST`, key `op:{id}:site`),
  except `source=reconcile` (corrective / 0-probe: op only). Same `operationId` returns
  the existing row. Debit below zero → `RuntimeException('insufficient_server_coins')`.
  Queue off → current `adjustServerBalance` (`TYPE_ADMIN`).
- Ack: optional `balance_after`. `adjust` applied stores it in `meta`; failed writes
  compensating ledger `op:{id}:revert`. Non-pending acks ignored. Then drift: if no
  pending ops and site ≠ game, at most one pending reconcile `adjust` (`source=reconcile`,
  amount = site − game). `to_server`/`from_server` also update the site copy on applied
  (`op:{id}:server`) when `enabledFor`.
- Movements: store `site_op_id` and `deltas`. New row only: skip balance if `site_op_id`
  set or `deltas` missing (old mod = log only); otherwise apply each linked delta
  (`TYPE_SERVER_SYNC`, key `mv:{server}:{movement}:{uuid}`), clamp at 0, then drift.
- Admin user edit: for `enabledFor` servers, signed «Начислить/списать в игре» + required
  «Причина», read-only site balance, last 10 ops. Other servers keep set-balance.
- Artisan: `currency:server-sync-probe {server} {user?}`. `currency:verify-ledger` is
  unchanged (server-scope rows already skipped).

### Callers of per-server balances (this snapshot)

| Caller | Queue on (`enabledFor`) | Queue off |
| --- | --- | --- |
| Admin user edit set-balance | `CurrencyServerSync::adjust` (signed delta + reason) | `adjustServerBalance` as before |
| Admin `transferCoins` | `ServerOperationQueue::enqueue` to_server/from_server | `withdrawToServer` / `depositFromServer` |
| Profile `transferCoins` | same enqueue | same ledger methods |
| Shop / quark exchange | **not in this snapshot** — call `CurrencyServerSync::adjust` (`source=shop` / `quark_exchange`) when those land | — |
| `CurrencyLedger::adjustServerBalance` itself | unchanged (used by the sync/ack path) | unchanged |

## Flags (default off / empty)

- `currency.server_queue_enabled` — reused, default `0`
- `currency.server_queue_servers` — default `''` (all mc-azlink **once the global flag is on**; set `9` on prod)
- `currency.server_movements_enabled` — still required for the movements endpoint

Existing behaviour is unchanged while the global queue flag is off.

## Apply (after DB dump + file backup, with confirmation)

```
cd /var/www/main-site
git apply ../AzLink-mods/site-patches/gluon-server-adjust/0001-*.patch
git apply ../AzLink-mods/site-patches/gluon-server-adjust/0002-*.patch
php artisan migrate --force
php artisan currency:verify-ledger
# enable only when ready, e.g. server 9 only:
# setting currency.server_queue_enabled = 1
# setting currency.server_queue_servers = 9
# setting currency.server_movements_enabled = 1   # already used for movements
```

## Tests

```
cd /var/www/main-site
php artisan test --filter=CurrencyServerSyncTest
```

Needs Azuriom’s PHPUnit + sqlite/MySQL and a `User` factory (or adapt `setUp`). This
repo cannot boot Azuriom; the test file is shipped inside patch `0002`.

## Rollback

Restore the changed files from the pre-apply backup, then:

```
php artisan migrate:rollback --step=1
```

(`step=1` drops `currency.server_queue_servers` and the movement `deltas`/`site_op_id`
columns). Flags can be set back to `0` / `''` without rollback.

## JSON contract

### Site → game: `GET /api/azlink/coins/operations?limit=50`

```json
{"operations":[{"id":"9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b","user_id":2275,"game_id":"f3fc162d-d344-32fa-8c9a-0b987b0791cf","name":"Nick","direction":"adjust","amount":-50,"reason":"Компенсация за баг","source":"admin_user_edit"}]}
```

- `direction`: `to_server` | `from_server` (amount > 0, unchanged) | `adjust` (signed int; 0 = probe with `source:reconcile`)
- `reason` ≤ 190, `source` informational (`admin_user_edit` | `quark_exchange` | `shop` | `reconcile` | `api` | …)

### Game → site: `POST /api/azlink/coins/operations/{id}/ack`

```json
{"status":"applied","error":null,"balance_after":950}
{"status":"failed","error":"insufficient_server_balance","balance_after":30}
```

Idempotent. `balance_after` optional (old mods omit it).

### Game → site: `POST /api/azlink/coins/movements`

Existing fields plus optional `deltas` and `site_op_id`. If `site_op_id` is set, store
only. If `deltas` is missing, log only. Otherwise apply each delta to the site mirror.
