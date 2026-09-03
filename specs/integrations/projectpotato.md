# ProjectPotato ↔ gwsctracker integration

How the **ProjectPotato (PP)** launcher talks to **gwsctracker** for per-user **feature module**
entitlements and downloads, and what PP's build pipeline publishes for them.

> **Scope:** this covers the gated feature-module DLLs only. How the base launcher (`PP.exe` /
> `PP.dll`) discovers and updates *itself* is an **open question** — see §7. gwsctracker currently
> serves only the feature modules.

- **gwsctracker side:** [08-module-entitlements](../backend/08-module-entitlements.md) (API),
  [07-deployment](../backend/07-deployment.md) (bucket / IAM).
- **Base URL:** `https://<GWSCTRACKER_BASE_URL>` — the public gwsctracker origin, HTTPS. All
  paths below are relative to it. Replace the placeholder with the real host.
- **Contract stability:** these endpoints are top-level (not under `/api/`), the same convention
  the GW1 SDK plugin already relies on — treated as a stable external contract.

PP is configured with the base URL and the user's **machine key**.

---

## 1. Model

| Piece | Owner | Notes |
|---|---|---|
| **Machine key** | gwsctracker | The user generates one on their gwsctracker **Account** page and pastes it into PP's config. It maps to their **account** (a user may hold several). PP sends it as the `X-Machine-Key` header. Revoking it on the Account page cuts PP off. |
| **Module** | gwsctracker registry | One downloadable feature-DLL artifact, identified by a lowercase-slug `key` (`^[a-z0-9][a-z0-9-]{0,63}$`) an admin assigns at registration. Convention: `pp-<feature>`. Feature modules are **gated** — a user sees one only if an admin granted it. (`is_public` modules exist in the registry — e.g. the GW1 `sctracker` plugin — but PP's feature modules are all gated.) |
| **Entitlement** | gwsctracker admin | Per `(user, module)` grant, managed from the admin **User Management** page. Checked **live** on every call, so a grant or revoke takes effect on PP's next request — no caching, no propagation delay. |
| **Artifact bytes** | private GCS bucket | gwsctracker proxies them; PP never talks to GCS directly. Feature DLLs are published under `plugins/<Name>/` — see §5. |

There is **no plugin-version gate** on these endpoints. PP versions independently of the GW1
SCTracker plugin; a missing or stale `X-Plugin-Version` header is ignored here (it will never
get a `426`).

---

## 2. Endpoints PP calls

### `GET /module-entitlements` — what this user may use

Call on launch and on every update check.

```
GET /module-entitlements
X-Machine-Key: <the user's key>
```

| Status | Meaning | PP should… |
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
      "is_public": false,
      "version": 3,
      "sha256": "9ab0…",
      "download_url": "/modules/pp-vanquish/download"
    }
  ]
}
```

- The list is **the complete set** the user is entitled to right now: every gated module granted
  to them (plus any `is_public` modules, which for PP's purposes is usually none). A module that
  was granted and later **revoked** simply stops appearing — PP should treat "not in the list" as
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
GET /artifacts
```

```json
{
  "artifacts": [
    { "key": "pp-vanquish", "display_name": "…", "is_public": false, "version": 3, "compiled_at": "…", "sha256": "…", "download_url": "/modules/pp-vanquish/download" }
  ]
}
```

Every enabled artifact with its current version and `compiled_at`. It does **not** tell you which
gated modules a given user may use — that's `/module-entitlements`.

---

## 3. Recommended PP client flow

**On launch / "check for updates" (key required):**
1. `GET /module-entitlements` with `X-Machine-Key`.
   - `401` → surface "your gwsctracker key is invalid or was revoked"; load only the base app.
2. For each module in the response:
   - not present on disk, or on-disk `sha256` ≠ `module.sha256` → `GET module.download_url` with
     the header, verify the downloaded bytes hash to `module.sha256`, write to disk.
3. For each module **on disk but not in the response** → it was revoked or retired; delete it /
   stop loading it.
4. Load the modules that are present and current.

**Entitlement is re-checked server-side on every `/module-entitlements` and every
`/modules/{key}/download`**, so PP doesn't need its own expiry logic — just re-run step 1 on the
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

## 5. What PP's build pipeline must publish (feature modules)

gwsctracker serves artifacts from a **private GCS bucket** (`PLUGIN_STORAGE_BUCKET`, shared with
the GW1 plugin). Whatever pipeline builds a feature DLL uploads it under one prefix per module:

| Object | What |
|---|---|
| `plugins/<Name>/<Name>.dll` | the feature module dll |
| `plugins/<Name>/<Name>.version.json` | its manifest (schema below) |

Modules built in the GWToolbox fork are already published there by that repo's `cmake.yml`.

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

## 6. Registering a feature module (gwsctracker admin — Evan)

A published DLL isn't usable until an admin registers it as a module, then grants it per user.

**Scan (fastest):** gwsctracker admin → **Modules** → **Scan bucket for new modules**. Every
`plugins/<Name>/` folder with a dll but no registry row shows up with the paths pre-filled; set the
display name, leave **Public** unticked, **Import**.

**Manual:** **Modules** → *Add a module*:
- `module_key` = `pp-<feature>`
- `bucket_prefix` = `plugins/<Name>`
- `artifact_object` = `<Name>.dll`
- `manifest_object` = `plugins/<Name>/<Name>.version.json`
- `is_public` = unchecked

Then: **User Management** → expand the user → **Modules** → **Grant**. Revoke from the same place;
**Disable** (Modules page) pulls a module for everyone without deleting grants.

---

## 7. Open question — launcher self-update

The feature-module flow above is settled. **How `PP.exe` / `PP.dll` find and update themselves is
not.** To resolve, we need to pick:

1. **Where the launcher bytes live.**
   - *This bucket, as a module* — `plugins/PP/PP.exe` etc., registered like any module. Reuses
     everything below; needs point 2.
   - *Somewhere else* — Dan's own host, GitHub Releases, a CDN. Then gwsctracker isn't involved in
     launcher updates at all and PP keeps whatever bootstrap mechanism it already has.

2. **How the running exe identifies "the launcher" artifact** (only relevant if it's a module
   here). `GET /artifacts` exposes `key`, `display_name`, `version`, `sha256`, `download_url` — but
   **not** the filename — so the bootstrapper can't match by `PP.exe`. Options:
   - a **fixed key convention** (e.g. `pp` / `pp-base`) that PP is compiled to look for;
   - a **config value** — PP ships with its launcher module key in config alongside the base URL;
   - a **dedicated endpoint** — e.g. `GET /launcher` returning the current exe's version + URL,
     independent of the registry.

3. **Auth.** Presumably none — the launcher download has to work before the user has entered a
   machine key. So it must be a **public** module (or an unauthenticated endpoint).

4. **Versioning / manifest.** If it's a module, it uses the same integer `*.version.json` scheme as
   §5. If it's hosted elsewhere, whatever that host provides.

Until this is decided, treat launcher updates as PP's existing concern and use gwsctracker only for
the gated feature modules.
