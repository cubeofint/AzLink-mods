# Site patch: gluon ledger + site<->server transfer queue

The production site (`/var/www/main-site` on proxy-estony, Azuriom with local currency changes) is not in git.
These patches are made against the production files as of 2026-10-07 (baseline = commit "Baseline" in the
patch series, i.e. the current server files) and are **not applied on production**.

## What it adds
- `CurrencyLedger`: same idempotency key from concurrent requests returns the stored entry (unique-index race);
  every operation id applies exactly once. Balances only move together with an append-only `currency_transactions` row.
- `currency_server_operations` table: queue of transfers with `pending` / `applied` / `failed`.
- AzLink API (token-authenticated, `server.token` middleware):
  - `GET  /api/azlink/coins/operations?limit=50` → `{"operations":[{id,user_id,game_id,name,amount,direction}]}`
  - `POST /api/azlink/coins/operations/{id}/ack` `{"status":"applied|failed","error":"..."}` (idempotent)
- `to_server`: site wallet debited on enqueue (`op:{id}:debit`), refunded on `failed` (`op:{id}:refund`).
  `from_server`: site wallet credited only after `applied` (`op:{id}:credit`).
- Feature flag setting `currency.server_queue_enabled` (default `0`, endpoints answer 404 when off).
- `php artisan currency:verify-ledger` (read-only), `php artisan currency:queue-transfer <user> <server> <to_server|from_server> <amount>`.

## Apply (after a DB dump + files backup, with confirmation)
```
cd /var/www/main-site
git apply 0001-*.patch 0002-*.patch
php artisan migrate --force          # creates the table + flag (off)
php artisan currency:verify-ledger
# enable only when ready:
# setting currency.server_queue_enabled = 1
```
Rollback: restore the changed files from backup and `php artisan migrate:rollback --step=1`.
