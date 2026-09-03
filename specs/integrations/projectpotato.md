# ProjectPotato ↔ gwsctracker integration

How the **ProjectPotato (PP)** launcher talks to **gwsctracker** for per-user feature
entitlements and artifact downloads, and what PP's build pipeline has to publish.

- **gwsctracker side:** [08-module-entitlements](../backend/08-module-entitlements.md) (API),
  [07-deployment](../backend/07-deployment.md) (bucket / IAM).
- **Base URL:** `https://<GWSCTRACKER_BASE_URL>` — the public gwsctracker origin, HTTPS. All
  paths below are relative to it. Replace the placeholder with the real host.
- **Contract stability:** these endpoints are top-level (not under `/api/`), the same convention
  the GW1 SDK plugin already relies on — treated as a stable external contract.

PP is configured with three things: the base URL, the user's **machine key**, and its own
**launcher module key** (see §1 — recommend `pp`).

---

## 1. Model

| Piece | Owner | Notes |
|---|---|---|
| **Machine key** | gwsctracker | The user generates one on their gwsctracker **Account** page and pastes it into PP's config. It maps to their **account** (a user may hold several). PP sends it as the `X-Machine-Key` header. Revoking it on the Account page cuts PP off. |
| **Module** | gwsctracker registry | One downloadable artifact — the launcher exe, the base dll, or a feature dll — identified by a lowercase-slug `key` (`^[a-z0-9][a-z0-9-]{0,63}$`). There are **no reserved keys**: an admin registers each artifact and picks its key. Convention: `pp` for the launcher, `pp-<feature>` for feature modules. The launcher/base are registered **public**; feature modules are **gated** (a user sees one only if an admin granted it). |
| **Entitlement** | gwsctracker admin | Per `(user, module)` grant, managed from the admin **User Management** page. Checked **live** on every call, so a grant or revoke takes effect on PP's next request — no caching, no propagation delay. |
| **Artifact bytes** | private GCS bucket | gwsctracker proxies them; PP never talks to GCS directly. PP's build pipeline **publishes** its artifacts into the bucket under `plugins/<Name>/` — see §5. |

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
      "key": "pp",
      "display_name": "ProjectPotato launcher",
      "is_public": true,
      "version": 7,
      "sha256": "0d1f…",
      "download_url": "/modules/pp/download"
    },
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

- The list is **the complete set** the user is entitled to right now: every public module plus
  every gated module granted to them. A module that was granted and later **revoked** simply
  stops appearing — PP should treat "not in the list" as "remove it / stop loading it".
- `version` is an integer, `null` until gwsctracker has seen the artifact's manifest at least
  once. `sha256` is the hex digest of the artifact bytes (also `null` until first seen).
- `download_url` is **app-relative** — prepend the base URL. Always use the value from the
  response; don't hardcode `/modules/{key}/download` (the `sctracker` key, if it ever shows up,
  uses `/SCTracker.dll`).

### `GET /modules/{key}/download` — fetch one artifact

```
GET /modules/pp-vanquish/download
X-Machine-Key: <key>          # required only for gated modules; harmless to always send
```

| Status | Meaning |
|---|---|
| `200` | body is the raw artifact bytes |
| `401` | gated module, key missing/invalid/revoked |
| `403` | gated module, valid key but no grant for it |
| `404` | no such module key, or it's disabled |
| `503` | the bytes aren't in the bucket yet (publish hasn't run) — retry later |

**Response headers on `200`:**

| Header | Use |
|---|---|
| `Content-Disposition: attachment; filename="<Name>.<ext>"` | the on-disk filename to write |
| `Content-Length` | total size (when known) |
| `ETag: "<sha256>"` | the artifact's sha256, quoted. Compare against what's on disk to skip a redundant download; `Cache-Control: no-cache` means always revalidate, never blind-cache. |
| `Content-Type` | `application/octet-stream` for dlls, `application/vnd.microsoft.portable-executable` for the exe (whatever `content_type` the admin set on the row) |

Public modules (the launcher, the base dll) download with **no** `X-Machine-Key` at all — the
bootstrapper can fetch them before the user has entered a key.

### `GET /artifacts` — full catalog (no auth)

```
GET /artifacts
```

```json
{
  "artifacts": [
    { "key": "pp",          "display_name": "…", "is_public": true,  "version": 7, "compiled_at": "2026-09-02T18:40:11Z", "sha256": "…", "download_url": "/modules/pp/download" },
    { "key": "pp-base",     "display_name": "…", "is_public": true,  "version": 7, "compiled_at": "…",                     "sha256": "…", "download_url": "/modules/pp-base/download" },
    { "key": "pp-vanquish", "display_name": "…", "is_public": false, "version": 3, "compiled_at": "…",                     "sha256": "…", "download_url": "/modules/pp-vanquish/download" }
  ]
}
```

Every enabled artifact, public or not, with its current version and `compiled_at`. Useful for the
updater to discover the current launcher/base version without a key. It does **not** tell you
which gated modules a given user may use — that's `/module-entitlements`.

---

## 3. Recommended PP client flow

**Bootstrap (launcher self-update, no key needed):**
1. `GET /artifacts`, find the entry whose `key` is your configured launcher key (`pp`), and the base
   dll (`pp-base`).
2. If its `sha256` ≠ the running exe's, `GET` its `download_url` (no header), replace on disk, relaunch.

**On launch / "check for updates" (key required):**
1. `GET /module-entitlements` with `X-Machine-Key`.
   - `401` → surface "your gwsctracker key is invalid or was revoked"; load only the base app.
2. For each module in the response:
   - not present on disk, or on-disk `sha256` ≠ `module.sha256` → `GET module.download_url` with
     the header, verify the downloaded bytes hash to `module.sha256`, write to disk.
3. For each module **on disk but not in the response** → it was revoked or retired; delete it /
   stop loading it.
4. Load the modules that are present and current.

**Entitlement is re-checked server-side on every `/module-entitlements` and every gated
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

## 5. What PP's build pipeline must publish

gwsctracker serves artifacts from a **private GCS bucket** (`PLUGIN_STORAGE_BUCKET`, shared with
the GW1 plugin). PP's CI uploads into it; gwsctracker reads from it. **One prefix rule — every
artifact under `plugins/<Name>/`:**

| Object | What |
|---|---|
| `plugins/PP/PP.exe` | the bootstrapper |
| `plugins/PP/PP.dll` | the base app |
| `plugins/PP/PP.version.json` | manifest — covers both `PP.exe` and `PP.dll` (they ship as a set at one version) |
| `plugins/<Name>/<Name>.dll` | a feature module dll (only if PP's own pipeline builds it — modules built in the GWToolbox fork are already auto-published there by that repo's `cmake.yml`) |
| `plugins/<Name>/<Name>.version.json` | manifest for that module |

**Manifest schema** (`*.version.json`) — matches gwsctracker's `PluginVersionMetadata`:

```json
{
  "name": "PP",
  "version": 7,
  "compiled_at": "2026-09-02T18:40:11Z",
  "sha256": "0d1f8b…"
}
```

- `version` — **integer**, bumped on every release you want clients to pick up.
- `compiled_at` — ISO-8601 UTC.
- `sha256` — hex digest of the primary artifact's bytes (`PP.exe` for `plugins/PP/PP.version.json`).

**Upload rules:**
- **Bytes first, manifest second.** Upload `PP.exe` / `PP.dll`, then `PP.version.json`. A gwsctracker
  refresh racing the uploads then sees a manifest that lags the bytes by at most one cycle, never one
  that points ahead of them.
- `gcloud storage cp <file> gs://$BUCKET/plugins/PP/PP.exe --cache-control=no-cache` (the
  `--cache-control` flag keeps any CDN layer from stacking staleness on gwsctracker's own cache TTL).
- No backend redeploy needed — gwsctracker picks up a new manifest within its cache TTL (15 min
  for modules) and re-reads `current_version` / `current_sha256`.

**IAM:** PP's CI service account needs `roles/storage.objectAdmin` **on that bucket only**. Evan
grants it (or shares the existing `GCP_SA_KEY` used by the GWToolbox fork). No other GCP access.

---

## 6. Registering an artifact (gwsctracker admin — Evan)

A published artifact isn't usable until an admin registers it as a module. Two paths:

**Scan (fastest):** gwsctracker admin → **Modules** → **Scan bucket for new modules**. Every
`plugins/<Name>/` folder with a dll but no registry row shows up with the paths pre-filled; set the
display name, tick **Public** for the launcher/base (leave it off for a feature module), **Import**.

**Manual** (for anything a scan won't find, e.g. the `.exe`): **Modules** → *Add a module*:
- `module_key` — `pp` for the launcher, `pp-base` for the base dll, `pp-<feature>` for a feature
- `bucket_prefix` = `plugins/PP` (launcher/base) or `plugins/<Name>` (feature)
- `artifact_object` = `PP.exe` / `PP.dll` / `<Name>.dll`
- `manifest_object` = `plugins/PP/PP.version.json` (launcher/base share it) or `plugins/<Name>/<Name>.version.json`
- `content_type` = `application/vnd.microsoft.portable-executable` for `PP.exe`, else leave default
- `is_public` = checked for the launcher/base, unchecked for a feature module

Then for a gated module: **User Management** → expand the user → **Modules** → **Grant**. Revoke
from the same place; **Disable** (Modules page) pulls a module for everyone without deleting grants.
