# 08 — Module entitlements & artifact hosting

gwsctracker hosts and gates the **GWToolbox++ plugin** DLLs that the **ProjectPotato (PP)** launcher
(a separate C#/.NET app) manages for a user — a user can download one only if an admin granted it. Every artifact lives
under one `plugins/<Name>/` bucket prefix; there are no hardcoded keys — modules are registered
through the admin API, or discovered (see below). The existing single-artifact SCTracker path
([07-deployment](07-deployment.md), "Plugin artifacts") is untouched — this is an additive
`com.howl.uwtracker.modules` package that reuses the same GCS bucket and the `PluginVersionMetadata`
manifest record. (Where PP's base launcher `PP.exe`/`PP.dll` is hosted and how it self-updates is a
separate open question — see specs/integrations/projectpotato.md §7.)

## Data model

| Table | Purpose |
|---|---|
| `modules` (changeset 043) | The registry: one row per hosted artifact. `module_key` (unique slug, `^[a-z0-9][a-z0-9-]{0,63}$`, immutable), `display_name`, `is_public`, `enabled`, `bucket_prefix` + `artifact_object` (bytes live at `bucket_prefix + "/" + artifact_object` in `PLUGIN_STORAGE_BUCKET` — every artifact uses the `plugins/<Name>/` layout), `manifest_object` (full path, nullable), `content_type`, and the cache columns `current_version` / `current_sha256` / `version_detected_at`. |
| `person_module_grants` (changeset 044) | Composite-PK join `(person_id, module_id)`; row existence == access, a revoke deletes it. `granted_by` = the admin's `people.id` (`ON DELETE SET NULL`). Both FKs `ON DELETE CASCADE`. |

Changeset 045 seeds one durable public row: `sctracker` (metadata-only — its bytes still come from
`GET /SCTracker.dll` / `PluginArtifactCache`, not the generic download path). Changeset 046 moves
it onto the uniform `plugins/SCTracker/` layout; changeset 047 drops the `pp-exe` / `pp-base`
placeholders that 045 also seeded. Every other row is created through the admin API (or
**Scan bucket** → import), same posture as the `admins` table being populated by hand. `sctracker`
is the only key the backend special-cases.

## Manifest cache

`ModuleManifestCache` is the per-module, **metadata-only** analogue of `PluginArtifactCache` — it
caches each module's manifest JSON (`plugin.storage.module-cache-ttl`, default 15m) but never the
artifact bytes, which stream from the bucket per request. A changed `sha256` publishes
`ModuleVersionChangedEvent`; `ModuleVersionInitializer` writes it to the module's `current_*`
columns (it does **not** touch the `plugin_dll_version` singleton). `ModuleManifestResolver`
centralises the "sctracker reuses `PluginArtifactCache`, everything else uses `ModuleManifestCache`"
decision.

## Endpoints

All top-level (not under `/api/**`), per the plugin-facing convention in [00-overview](00-overview.md).

| Route | Auth | Notes |
|---|---|---|
| `GET /artifacts` | none | Public list of every enabled module: `{ artifacts: [{ key, display_name, is_public, version, compiled_at, sha256, download_url }] }`. `download_url` is app-relative — `/SCTracker.dll` for `sctracker`, `/modules/{key}/download` otherwise. Models `GET /plugin-version`. |
| `GET /modules/{key}/download` | `X-Machine-Key` **iff** the module is not public | 404 unknown/disabled; for a gated module 401 (missing/invalid/revoked key) then 403 (key without a grant); 200 streams the bytes from the bucket (`Content-Disposition: attachment`, `ETag` = `current_sha256` when known, `Cache-Control: no-cache`), 503 if the object is unavailable or over `plugin.storage.max-module-download-bytes` (default 64 MiB). No caching — a revoke bites on the next request. |
| `GET /module-entitlements` | `X-Machine-Key` | PP polls this on start/update. Returns every enabled module that is public or granted to the key's person: `{ modules: [{ key, display_name, is_public, version, sha256, download_url }] }`. Closest sibling: `GET /can-report-run-failure`. |

`MachineKeyAuthenticationService.authenticateWithoutVersionCheck` is the auth used by the gated
download and the edge endpoint: key-only, **no** `recordPluginSeen` (PP sends no
`X-Plugin-Version`; stamping null would corrupt the "Players On An Outdated Plugin" signal) and
**no** `requireCurrentVersion` (PP versions independently of SCTracker and must never hit its 426
gate).

## Admin API (`/api/admin/**`, gated by `AdminAuthInterceptor`)

| Route | Notes |
|---|---|
| `GET /api/admin/modules` | Full registry (enabled or not), sort order. |
| `GET /api/admin/modules/discover` | Bucket scan: `plugins/<Folder>/` directories that have a `<Folder>.dll` but no registry row yet, with `bucket_prefix` / `artifact_object` / `manifest_object` pre-derived and a slugified `suggested_key`. Empty when no bucket is configured. The admin imports one via `POST /api/admin/modules`. |
| `POST /api/admin/modules` | Create. `module_key` validated + unique (409 on dup); `display_name` / `bucket_prefix` / `artifact_object` required; `is_public` defaults false, `sort_order` 0, `content_type` `application/octet-stream`. |
| `PATCH /api/admin/modules/{moduleKey}` | Partial update (any subset of the mutable fields). Evicts the manifest cache when object paths change. `module_key` is immutable. 404 unknown. |
| `DELETE /api/admin/modules/{moduleKey}` | Hard delete (grants cascade). 409 for `sctracker` (the one built-in key) — disable it instead. |
| `GET /api/admin/users/{personId}/modules` | Per-user checklist: every enabled module + `granted`. Public modules come back `granted:false`, shown as "always available". |
| `PUT /api/admin/users/{personId}/modules/{moduleKey}` | Grant (idempotent, keeps the original `granted_by/at`). 404 unknown user or module. |
| `DELETE /api/admin/users/{personId}/modules/{moduleKey}` | Revoke (idempotent). |

`AdminUserService` delegates the grant sub-resource to `ModuleGrantService` after its `requireUser`
check — the same shape it already uses for character management. `granted_by` comes from the
admin's session via `@CurrentPersonId`.

## CI / hosting

See [07-deployment](07-deployment.md) "Module artifacts". Same bucket, one prefix
(`plugins/<Name>/`) for every artifact; no new env var or IAM.
