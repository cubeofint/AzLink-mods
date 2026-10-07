# Site patches: pieces 3–4 (currency movements + in-game donate delivery)

Production site (`/var/www/main-site`) is not in this git repo. Apply these patches
**on top of** `site-patches/gluon-ledger-queue`, after a DB dump and file backup.
They are **not** applied on production by this repository.

## What it adds

- `currency_server_movements`: batches from the game via `POST /api/azlink/coins/movements`
  (idempotent `server_id + movement_id`, `accepted_up_to`). Does not change site balances.
- Player tab «Движение валют» and admin «На серверах».
- Shop configured delivery (`ConfiguredDeliveryService`, `shop:configured-delivery`):
  pending purchase → `privilege_reconcile` + `kit_redeem` per kit line;
  all ACK succeeded → purchase active / delivered;
  term end → `privilege_revoke` (or successor reconcile on renewal).
- Deterministic `operation_key` → exactly-once dispatch (replay-safe).
- Flags (default **off**): `currency.server_movements_enabled`, `shop.configured_delivery.enabled`.

## Capability keys on poll (422 fix)

`POST /api/shop/azlink/v1/operations/poll` (the test-server path
`/api/azlink/v1/operations/poll` uses the same body) validates
`supported_capabilities.*`.

The mod used to advertise `*` when LuckPerms was present and `executor.json` had an
empty list. Laravel then answered **422** «supported_capabilities.0 имеет недопустимый формат».
`meta:...` is also invalid on the wire (it is only a LuckPerms encoding inside the mod).

**Agreed format:** plain shop keys, e.g. `fly`, `claim_chunks`, `cointcore.bonus_claim_chunks`.

```
^[a-z][a-z0-9._-]{0,63}$
```

Patch `0005` adds `PollOperationsRequest` with that rule. If the class already exists
on the server, replace only the `supported_capabilities.*` regex (do not accept `*`
or `:`).

## Apply

```
cd /var/www/main-site
git apply ../AzLink-mods/site-patches/gluon-ledger-queue/*.patch
git apply ../AzLink-mods/site-patches/gluon-movements-delivery/*.patch
php artisan migrate --force
# enable only when ready:
# setting currency.server_movements_enabled = 1
# setting shop.configured_delivery.enabled = 1
```

If `PollOperationsRequest` already exists, `git apply` of `0005` may fail on that
file: copy the `supported_capabilities.*` rule from the patch by hand.

## Rollback

Restore the changed files from the backup taken before `git apply`, then:

```
php artisan migrate:rollback --step=2
```

(`step=2` covers movements table + configured-delivery setting; adjust if other
migrations ran in between.) Flags can also be set back to `0` without rollback.
