# GW Launcher Reforged ↔ gwsctracker integration

How the **GW Launcher Reforged (GWRL)** launcher talks to **gwsctracker** to learn which
**GWToolbox++ plugins** a user is entitled to and to download them.

> **Scope:** the gated Toolbox plugins (`type: "plugin"`) **and** the launcher's own components
> (`type: "module"`) — see §7. gwsctracker hosts both.

- **gwsctracker side:** [08-module-entitlements](../backend/08-module-entitlements.md) (API),
  [07-deployment](../backend/07-deployment.md) (bucket / IAM).
- **Base URL:** `https://<GWSCTRACKER_BASE_URL>` — the public gwsctracker origin, HTTPS. All
  paths below are relative to it. Replace the placeholder with the real host.
- **Contract stability:** these endpoints are top-level (not under `/api/`), the same convention
  the GW1 SDK plugin already relies on — treated as a stable external contract.

GWRL is configured with the base URL and the user's **machine key**.

---

## 1. Model

| Piece | Owner | Notes |
|---|---|---|
| **Machine key** | gwsctracker | The user generates one on their gwsctracker **Account** page and pastes it into GWRL's config. It maps to their **account** (a user may hold several). GWRL sends it as the `X-Machine-Key` header. Revoking it on the Account page cuts GWRL off. |
| **Module** | gwsctracker registry | The registry's noun for one downloadable artifact — for GWRL's purposes, a GWToolbox++ plugin DLL. Identified by a lowercase-slug `key` (`^[a-z0-9][a-z0-9-]{0,63}$`) an admin assigns at registration (e.g. `sctracker`, `pp-vanquish`). These plugins are **gated** — a user sees one only if an admin granted it. |
| **`type`** | gwsctracker registry | Each module is `type: "plugin"` (a GWToolbox++ plugin DLL, loaded by the toolbox) or `type: "module"` (a launcher component, loaded by GWRL itself — `gwrl-install`, `gwrl-base`, `gwrl-<feature>`). Every response entry carries it, and both list endpoints take an optional `?type=plugin` / `?type=module` filter so each consumer fetches only its kind. |
| **Entitlement** | gwsctracker admin | Per `(user, module)` grant, managed from the admin **User Management** page. Checked **live** on every call, so a grant or revoke takes effect on GWRL's next request — no caching, no propagation delay. |
| **Artifact bytes** | private GCS bucket | gwsctracker proxies them; GWRL never talks to GCS directly. Plugin DLLs are published under `plugins/<Name>/` — see §5. |

There is **no plugin-version gate** on these endpoints. GWRL versions independently of the GW1
SCTracker plugin; a missing or stale `X-Plugin-Version` header is ignored here (it will never
get a `426`).

---

## 2. Endpoints GWRL calls

### `GET /module-entitlements` — what this user may use

Call on launch and on every update check.

```
GET /module-entitlements[?type=plugin|module]
X-Machine-Key: <the user's key>
```

| Status | Meaning | GWRL should… |
|---|---|---|
| `200` | authenticated | use the returned module list (below) |
| `401` | key missing, blank, unknown, or revoked | deny — show "invalid or revoked key, check your gwsctracker Account page", do not load any gated module |

**Body:**

```json
{
  "modules": [
    {
      "key": "pp-vanquish",
      "display_name": "Vanquish data aggregation",
      "type": "plugin",
      "is_public": false,
      "version": 3,
      "sha256": "9ab0…",
      "download_url": "/modules/pp-vanquish/download"
    }
  ]
}
```

- `?type=plugin` returns only the toolbox plugins the user is entitled to; `?type=module` only the
  launcher modules; no param returns both. Each entry also carries `type` so you can route locally.
- The list is **the complete set** the user is entitled to right now: every gated module granted
  to them (plus any `is_public` modules, which for GWRL's purposes is usually none). A module that
  was granted and later **revoked** simply stops appearing — GWRL should treat "not in the list" as
  "remove it / stop loading it".
- `version` is an integer, `null` until gwsctracker has seen the artifact's manifest at least
  once. `sha256` is the hex digest of the artifact bytes (also `null` until first seen).
- `download_url` is **app-relative** — prepend the base URL. Always use the value from the
  response; don't hardcode `/modules/{key}/download`.

### `GET /modules/{key}/download` — fetch one artifact

```
GET /modules/pp-vanquish/download
X-Machine-Key: <key>
```

| Status | Meaning |
|---|---|
| `200` | body is the raw artifact bytes |
| `401` | key missing/invalid/revoked |
| `403` | valid key but no grant for this module |
| `404` | no such module key, or it's disabled |
| `503` | the bytes aren't in the bucket yet (publish hasn't run) — retry later |

**Response headers on `200`:**

| Header | Use |
|---|---|
| `Content-Disposition: attachment; filename="<Name>.dll"` | the on-disk filename to write |
| `Content-Length` | total size (when known) |
| `ETag: "<sha256>"` | the artifact's sha256, quoted. Compare against what's on disk to skip a redundant download; `Cache-Control: no-cache` means always revalidate, never blind-cache. |
| `Content-Type` | `application/octet-stream` for dlls |

### `GET /artifacts` — full catalog (no auth)

```
GET /artifacts[?type=plugin|module]
```

```json
{
  "artifacts": [
    { "key": "pp-vanquish", "display_name": "…", "type": "plugin", "is_public": false, "version": 3, "compiled_at": "…", "sha256": "…", "download_url": "/modules/pp-vanquish/download" }
  ]
}
```

Every enabled artifact with its current version and `compiled_at` (same optional `?type=` filter).
It does **not** tell you which gated modules a given user may use — that's `/module-entitlements`.

---

## 3. Recommended GWRL client flow

**On launch / "check for updates" (key required):**
1. `GET /module-entitlements?type=plugin` with `X-Machine-Key` (and `?type=module` for the
   launcher's own components — §7).
   - `401` → surface "your gwsctracker key is invalid or was revoked"; load only the base app.
2. For each module in the response:
   - not present on disk, or on-disk `sha256` ≠ `module.sha256` → `GET module.download_url` with
     the header, verify the downloaded bytes hash to `module.sha256`, write to disk.
3. For each module **on disk but not in the response** → it was revoked or retired; delete it /
   stop loading it.
4. Load the modules that are present and current.

**Entitlement is re-checked server-side on every `/module-entitlements` and every
`/modules/{key}/download`**, so GWRL doesn't need its own expiry logic — just re-run step 1 on the
cadence you want revocations to take effect (launch, and/or a periodic check).

---

## 4. Notes & edge cases

- **HTTPS only.** The key is a bearer credential in a header; don't log it, don't send it over
  plain HTTP.
- **`503` on a download** = the artifact object isn't in the bucket yet (a publish hasn't landed).
  Back off and retry; it's not a permanent error.
- **`version` / `sha256` can be `null`** briefly after a fresh gwsctracker deploy, before it has
  read the artifact's manifest. Fall back to downloading and hashing, or retry.
- **Multiple keys per user** is fine — any of the user's active keys authenticates identically.
- **Security bar is intentionally light.** This deters casual copying, not a determined attacker.
  Gated modules stop loading when revoked, checked on launch/refresh — no runtime kill-switch.

---

## 5. Publishing an artifact

gwsctracker serves artifacts from a **private GCS bucket** (`PLUGIN_STORAGE_BUCKET`, shared with
the GW1 plugin). Toolbox plugins are built and published by the GWToolbox++ fork (`cmake.yml`, see 07-deployment); any other pipeline (incl. the launcher's own build) follows the same layout. One prefix per artifact:

| Object | What |
|---|---|
| `plugins/<Name>/<Name>.dll` | a Toolbox plugin dll |
| `plugins/<Name>/<Name>.version.json` | its manifest (schema below) |
| `plugins/GWToolboxdll/GWToolboxdll.dll` | the toolbox build itself — served as a public plugin under module key `gwtoolbox`; its `.version.json` carries no integer `version` |
| `launcher/<Name>/<Name>.{zip,exe,dll}` | a launcher component (`gwrl-install` archive, `gwrl-base` exe, `gwrl-<feature>` dll) |
| `launcher/<Name>/<Name>.version.json` | its manifest — one per component |

Modules built in the GWToolbox fork are already published there by that repo's `cmake.yml`, which
also publishes `GWToolboxdll.dll` + its manifest to `plugins/GWToolboxdll/`. The launcher repo
publishes its own `launcher/<Name>/` objects the same way.

**Manifest schema** (`*.version.json`) — matches gwsctracker's `PluginVersionMetadata`:

```json
{
  "name": "<Name>",
  "version": 3,
  "compiled_at": "2026-09-02T18:40:11Z",
  "sha256": "0d1f8b…"
}
```

- `version` — **integer**, bumped on every release you want clients to pick up.
- `compiled_at` — ISO-8601 UTC.
- `sha256` — hex digest of the sibling dll's bytes.

**Upload rules:**
- **Bytes first, manifest second.** Upload `<Name>.dll`, then `<Name>.version.json`. A gwsctracker
  refresh racing the two uploads then sees a manifest that lags the bytes by at most one cycle,
  never one that points ahead of them.
- `gcloud storage cp <file> gs://$BUCKET/plugins/<Name>/<Name>.dll --cache-control=no-cache` (the
  `--cache-control` flag keeps any CDN layer from stacking staleness on gwsctracker's own cache TTL).
- No backend redeploy needed — gwsctracker picks up a new manifest within its cache TTL (15 min
  for modules) and re-reads `current_version` / `current_sha256`.

**IAM:** the publishing CI service account needs `roles/storage.objectAdmin` **on that bucket
only**. Evan grants it (or shares the existing `GCP_SA_KEY` used by the GWToolbox fork).

---

## 6. Registering an artifact (gwsctracker admin — Evan)

A published artifact isn't usable until an admin registers it as a module, then grants it per user.

**Scan (fastest):** gwsctracker admin → **Modules** → **Scan bucket for new modules**. Every
`plugins/<Name>/` or `launcher/<Name>/` folder with an artifact but no registry row shows up with
the paths pre-filled and `type` pre-set (`module` for `launcher/` finds); set the display name,
leave **Public** unticked (tick it only for a component that must download before the user has a
key), **Import**.

**Manual:** **Modules** → *Add a module*:
- `module_key` = the slug (e.g. `sctracker`, `pp-vanquish`, `gwrl-install`)
- `type` = `plugin` for a Toolbox plugin (the default); `module` for a launcher component
- `bucket_prefix` = `plugins/<Name>` or `launcher/<Name>`
- `artifact_object` = `<Name>.dll` / `<Name>.zip` / `<Name>.exe`
- `manifest_object` = `<bucket_prefix>/<Name>.version.json`
- `is_public` = unchecked

Then: **User Management** → expand the user → **Modules** → **Grant**. Revoke from the same place;
**Disable** (Modules page) pulls a module for everyone without deleting grants.

---

## 7. Launcher components (`type: "module"`)

The launcher's own artifacts are hosted here too, all `type: "module"`, under `launcher/<Name>/`
(same one-prefix-per-artifact rule as `plugins/<Name>/`, same integer `*.version.json` scheme as
§5 — one sidecar manifest per artifact). Fixed keys GWRL is compiled to look for:

| Key | What | Access |
|---|---|---|
| `gwrl-install` | Full install archive — everything needed to run the launcher. | **Gated.** The user gets it from their gwsctracker **Account** page once an admin grants it (a session-authed `GET /api/account/modules/{key}/download`, since a browser link can't send `X-Machine-Key`). Not part of GWRL's own sync loop. |
| `gwrl-base` | Launcher self-update payload. | Grant as needed. |
| `gwrl-<feature>` | Individual launcher feature modules, versioned independently. | Grant per user. |

Once running, GWRL syncs `gwrl-base` / `gwrl-<feature>` with the same machine-key flow as the Toolbox
plugins — `GET /module-entitlements?type=module` on launch / on demand, then
`GET module.download_url` per entry. `download_url` is app-relative and comes from the response
(`/modules/{key}/download`); never hardcode it. Entitlement is re-checked server-side per call, so a
revoke drops the component on GWRL's next sync.

### Provisioning a user for the launcher (gwsctracker admin — Evan)

The **Launcher** download panel on a user's Account page renders **only** when `gwrl-install` comes
back from `GET /api/account/modules` for that user — i.e. the row exists, is enabled, and is
public **or** granted to them. Since `gwrl-install` is gated, both of these are required:

1. **Register `gwrl-install`** — Modules → *Scan bucket* (once `launcher/gwrl-install/…` is
   uploaded) and Import, or *Add a module* manually: `type = module`,
   `bucket_prefix = launcher/gwrl-install`, `artifact_object = gwrl-install.zip`,
   `manifest_object = launcher/gwrl-install/gwrl-install.version.json`, **Public unchecked**.
2. **Grant it** — User Management → expand the user → Modules → **Grant** `gwrl-install`.

The bytes need not exist yet for the panel to appear — `version` shows blank and the download
`503`s until `launcher/gwrl-install/…` is in the bucket. A user with no grant sees no panel (by
design); revoking hides it again on their next page load.
