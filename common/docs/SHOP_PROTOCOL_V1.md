# Shop Semantic Operations v1 — wire contract (AzLink ↔ main-site)

Canonical side: **main-site** (`plugins/shop` Minecraft Operation API).
Classic AzLink (`/api/azlink`) remains a separate channel; do not merge.

## Auth

- Header: `Azuriom-Link-Token`
- Server id comes from `VerifyServerToken` middleware — never from request body.

## Poll

`POST /api/shop/azlink/v1/operations/poll`

- Request: `protocol_version`, `executor_version`, `supported_operation_types` (snake_case),
  `supported_capabilities`, `max_batch`
- `supported_capabilities` items are plain shop keys (`fly`, `claim_chunks`,
  `cointcore.bonus_claim_chunks`). Regex: `^[a-z][a-z0-9._-]{0,63}$`.
  Do **not** send `*`, `meta:...`, or LuckPerms permission nodes.
- Response: top-level `protocol_version`, `operations[]` with field `type` (not `operation_type`)
- Operation types: `privilege_reconcile`, `privilege_revoke`, `kit_redeem`

## ACK

`POST /api/shop/azlink/v1/operations/ack`

Required: `operation_id`, `claim_token`, `payload_hash`, `status`, `executor_version`.
Optional: `result_code`, `applied_entitlement_version`, `message`.

`status` ∈ MinecraftOperationAckStatus (`succeeded`, `already_applied`, `stale`,
`retryable_failed`, `failed`, `unsupported`, `uncertain`).
`result_code` is a machine detail (e.g. `applied`, `integrity_error`) and does **not** replace `status`.

## Coins (Classic)

`POST /api/azlink/user/{id}/coins/{add|remove|withdraw|deposit}` requires `idempotency_key`
(stable per logical mutation; reused on HTTP retry).

## Tech debt (non-blocking)

- `known_players`: mod may send; site ignores
- `topPlayers` / `topPlayersLabel`: mod does not send
