# PrefixBridge (AzLink ↔ main-site)

Stable reflective API for **cointcore** (and other mods) to sync per-server chat
prefixes through the AzLink HTTP client. Same shape as [`CoinOperationsBridge`](../src/main/java/com/azuriom/azlink/common/coins/CoinOperationsBridge.java):
string JSON in/out, no compile dependency on AzLink internals.

Auth: `Azuriom-Link-Token` (server token → `server_id`), same as every other AzLink endpoint.

Feature flag on the site: `prefixes.enabled` (default off; endpoints answer 404).

Cabinet selection is **quest-only**. Donate / staff / media prefixes stay visible in game
and are not part of the site picker. The game may still report them on `/prefixes/report`.

## Java API

cointcore (PR #34) reflects **`CoinOperationsBridge`** first. The same names exist on
`PrefixBridge`. Paths passed in are `/azlink/prefixes/...`; AzLink `HttpClient` builds
`siteUrl + "/api" + path`.

```java
// CoinOperationsBridge and PrefixBridge — exact names cointcore looks up
static boolean isAvailable()
static CompletableFuture<String> postJson(String path, String json)
static CompletableFuture<String> getJson(String path)
static CompletableFuture<String> reportPrefixes(String json)
static CompletableFuture<String> reportPrefixCatalog(String json)
static CompletableFuture<String> fetchPrefixOperations(int limit)
static CompletableFuture<String> ackPrefix(String opId, String status, String reason, String selected)
```

`ackPrefix` status is **`applied` | `rejected`**. `selected` may be `null` (Авто).
If the dedicated methods are missing, cointcore falls back to `HttpClient.request`
via the `plugin` field on `CoinOperationsBridge`.

## Game → site: `POST /api/azlink/prefixes/report`

```json
{
  "players": [
    {
      "uuid": "f3fc162d-d344-32fa-8c9a-0b987b0791cf",
      "name": "Nick",
      "available": [
        {"group": "vip", "prefix_raw": "<gold>[VIP]</gold>", "category": "donate"},
        {"group": "quest_miner", "prefix_raw": "&a[Шахтёр]", "category": "quest"}
      ],
      "selected": "vip",
      "locked_staff": false
    }
  ]
}
```

- Upsert per `(user, server_id)`. User is resolved like other AzLink code: `game_id` (dashed / undashed UUID), then `name`.
- Stores `available`, `selected` (`null` = Авто), `locked_staff`, `synced_at`.
- Unknown players are skipped (`skipped` in the response). Does not create accounts.
- Idempotent last-write-wins for the snapshot.
- `category` is stored as given (no hard list). The cabinet only offers `quest`.

## Game → site: `POST /api/azlink/prefixes/catalog`

Per-server LuckPerms track export. Auth is the server token (`server_id`). Max 256 groups.

```json
{
  "schema": 1,
  "hash": "sha256-or-any-stable-digest",
  "full": true,
  "force": false,
  "groups": [
    {
      "group": "miner",
      "prefix_raw": "&a[Шахтёр]",
      "priority": 10,
      "track_index": 3,
      "display_name": "Шахтёр",
      "category": "quest"
    }
  ]
}
```

- Upsert by `(server_id, group)`. Updates only auto fields (`auto_display_name`,
  `auto_prefix_raw`, `auto_sort` from `track_index`, `priority`, `category`,
  `last_seen_at`). Visible `display_name` / `sort` / `enabled` follow an admin
  override when the matching flag is set; otherwise they track auto.
- Same `hash` for this server is a no-op (`unchanged: true`).
- `full=true` with a non-empty `groups` list marks missing rows (`missing_since`,
  `enabled=false`) without deleting them. They come back enabled unless
  `override_enabled` (manually disabled).
- Empty `groups` or `full=false` disables nothing.
- If more than 50% of currently present (not already missing) groups would
  disappear, the site answers `409 { "message": "force_required" }` unless
  `force: true`.

## Site → game: `GET /api/azlink/prefixes/operations?limit=50`

Pending `prefix_select` ops for **this** server. Taking a row leases it (`leased_until`, default 30s) so another poll does not re-deliver until the lease expires.

```json
{
  "operations": [
    {
      "op_id": "9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b",
      "type": "prefix_select",
      "uuid": "f3fc162d-d344-32fa-8c9a-0b987b0791cf",
      "group": "vip"
    }
  ]
}
```

`group` is `null` for Авто (clear / default prefix).

## Game → site: `POST /api/azlink/prefixes/ack`

```json
{"op_id":"9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b","status":"applied","reason":null,"selected":"vip"}
{"op_id":"9b2f6c1e-3d4a-4e0b-8f6a-1c2d3e4f5a6b","status":"rejected","reason":"unknown_group","selected":null}
```

- `status`: **`applied` | `rejected`** (not `failed` — that word is the coin queue).
- Idempotent: a second ack of a final op returns the stored status unchanged.
- On `applied`, site writes `selected` onto `user_server_prefixes`.

`currency_server_operations` / `ServerOperationQueue` stay gluon-only. Prefixes use `prefix_operations`.
