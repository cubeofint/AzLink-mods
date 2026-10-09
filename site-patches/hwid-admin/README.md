# Site patch: admin HWID panel (`hwid-admin`)

Production site (`/var/www/main-site`, Azuriom 1.2.8) is not in this git repo.
This patch is **live on production** since 2026-10-09 (originals backed up in
`/root/backups/site-hwid-panel-20261009/` on the site host). Apply with
`git apply -p1` (or `patch -p1`) from the site root. Requires the HWID schema
migration (extended `hwids` columns, `hwid_keys`, `hwid_disks`, `hwidLog`
extensions, `login_history`); without it the panel degrades to the legacy fields.

## What it changes

- `app/Support/HwidPanel.php` (new): data for the admin user page. Placeholder
  detection (`DEFAULTSTRING`, `To be filled by O.E.M.`, all-zero / repeated /
  too-short serials, ...), times converted from UTC to `app.timezone`.
  Returns the HWID row, disks / MACs / monitors, launcher key count and last use,
  the last 50 `hwidLog` rows for the HWID and the user, paginated `login_history`
  (25, page param `lh_page`) with stats (distinct IPs, launcher/site, failed), and
  matches: other accounts with the same HWID, a shared disk serial (`hwid_disks`),
  `system_uuid`, `machine_guid`, `device_tag` or IP (`login_history` /
  `last_login_ip`), placeholders ignored. Every query is wrapped and logs
  `[hwid-panel]` warnings instead of failing the page.
- `app/Support/LoginHistory.php` (new): `record(?userId, Request, result, source='site')`
  inserts into `login_history`; never throws.
- `app/Http/Controllers/Admin/HwidManageController.php` (new), routes in `routes/admin.php`
  (group `can:admin.users`, CSRF, JS confirm):
  - `POST admin/users/{user}/hwid/unlink` – `users.hwidId = NULL`, `hwidLog` event `unlink`
    (admin in `matched`); optional `remove_keys` deletes the row's `hwid_keys` and sets
    `hwids.publicKey = NULL` (event `keys_removed`).
  - `POST admin/users/{user}/hwid/split/{other}` – detaches another account sharing the
    row (event `split`), same optional key removal.
- `Auth/LoginController`: `success` row in `authenticated()` (covers password, 2FA and
  OAuth logins); `fail` row for an existing account on a wrong password.
- `Api/AuthController` (`/api/auth/authenticate`): `success` / `fail` rows.
- `resources/views/admin/users/_hwid.blade.php`: rewritten block (fields grid with grey
  "заглушка" badges, colored spoof score, copyable device tag, linked accounts with split
  buttons, matches, collapsible "Журнал HWID" and "История входов").
- `resources/lang/ru/admin.php`: strings under `admin.users.hwid.*`.

The existing HWID ban/unban buttons are unchanged.

## 0002: proxy/local IPs and virtual MAC

Apply on top of 0001. Launcher logins arrived as `127.0.0.1` (LaunchServer behind the local
Caddy without `netty.trustedProxies`), so every account matched every other by IP.

- `HwidPanel::isProxyIp()`: loopback, private (10/8, 172.16/12, 192.168/16, fc00::/7), CGNAT
  100.64/10, link-local, reserved and the server's own addresses (`SERVER_IPS`). Such IPs are
  excluded from IP matches and from the "unique IPs" count and shown greyed with
  «прокси/локальный» in the login history.
- `HwidPanel::isVirtualMac()`: RAS adapter `44:45:53:54:4F:53` (and all-zero/all-F) shown with
  «виртуальный» and never used for matching. MAC matches (`hwids.macs`) added as a signal.
- LaunchServer side: `netty.ipForwarding: true` and
  `netty.trustedProxies: ["127.0.0.1", "::1", "0:0:0:0:0:0:0:1"]` (Java reports IPv6 loopback in
  the long form), effective after a LaunchServer restart.
