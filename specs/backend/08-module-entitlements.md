# 08 — Module entitlements & artifact hosting

gwsctracker hosts and gates the **GWToolbox++ plugin** DLLs that the **GW Launcher Reforged
(GWRL)** launcher (a separate C#/.NET app) manages for a user — a user can download one only if an
admin granted it — plus the launcher's own components (`gwrl-install` install archive, `gwrl-base`
self-update payload, `gwrl-<module>` feature modules), all `type: module`. GWToolbox plugins live
under `plugins/<Name>/`; launcher components under `launcher/<Name>/` — same one-prefix-per-artifact
rule. There are no hardcoded keys — modules are registered through the admin API, or discovered (see
below). The existing single-artifact SCTracker path ([07-deployment](07-deployment.md), "Plugin
artifacts") is untouched — this is an additive `com.howl.uwtracker.modules` package that reuses the
same GCS bucket and the `PluginVersionMetadata` manifest record (strictly one sidecar
`*.version.json` per artifact).

## Data model

| Table | Purpose |
|---|---|
| `modules` (changeset 043, `type` added by 048, `patch_notes_object` added by 052) | The registry: one row per hosted artifact. `module_key` (unique slug, `^[a-z0-9][a-z0-9-]{0,63}$`, immutable), `display_name`, `type` (`plugin` \| `module` — a GWToolbox plugin vs. a launcher component; the two list endpoints take an optional `?type=` filter), `is_public`, `enabled`, `bucket_prefix` + `artifact_object` (bytes live at `bucket_prefix + "/" + artifact_object` in `PLUGIN_STORAGE_BUCKET` — `plugins/<Name>/` for plugins, `launcher/<Name>/` for launcher components), `manifest_object` (full path, nullable), `patch_notes_object` (full path, nullable — an optional `<Name>.patch.txt` sidecar; CI/CD appends to it release over release and overwrites the bucket object with the accumulated text each publish, the backend just serves whatever's currently there), `content_type`, and the cache columns `current_version` / `current_sha256` / `version_detected_at`. |
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
columns. `ModuleManifestResolver` centralises the "sctracker reuses `PluginArtifactCache`,
everything else uses `ModuleManifestCache`" decision.

## Endpoints

All top-level (not under `/api/**`), per the plugin-facing convention in [00-overview](00-overview.md).

| Route | Auth | Notes |
|---|---|---|
| `GET /artifacts` | none | Public list of every enabled module: `{ artifacts: [{ key, display_name, is_public, version, compiled_at, sha256, download_url, patch_notes_url }] }`. `download_url` is app-relative — `/SCTracker.dll` for `sctracker`, `/modules/{key}/download` otherwise. `patch_notes_url` is `/modules/{key}/patch-notes`, or `null` when the module has no `patch_notes_object`. Models `GET /plugin-version`. |
| `GET /modules/{key}/download` | `X-Machine-Key` **iff** the module is not public | 404 unknown/disabled; for a gated module 401 (missing/invalid/revoked key) then 403 (key without a grant); 200 streams the bytes from the bucket (`Content-Disposition: attachment`, `ETag` = `current_sha256` when known, `Cache-Control: no-cache`), 503 if the object is unavailable or over `plugin.storage.max-module-download-bytes` (default 64 MiB). No caching — a revoke bites on the next request. |
| `GET /modules/{key}/patch-notes` | `X-Machine-Key` **iff** the module is not public | Same entitlement rule as the artifact download, plus 404 when the module has no `patch_notes_object` configured. 200 returns the whole object as `text/plain` (`Content-Disposition: attachment`, filename from the object's own basename). Read whole, not streamed — the text is expected to be small — and never cached, same as the artifact. |
| `GET /module-entitlements` | `X-Machine-Key` | GWRL polls this on start/update. Returns every enabled module that is public or granted to the key's person: `{ modules: [{ key, display_name, type, is_public, version, sha256, download_url, patch_notes_url }] }`. Closest sibling: `GET /can-report-run-failure`. |

`MachineKeyAuthenticationService.authenticateWithoutVersionCheck` is the auth used by the gated
download and the edge endpoint: key-only, **no** `recordPluginSeen` (GWRL sends no
`X-Plugin-Version`; stamping null would corrupt the "Players On An Outdated Plugin" signal) and
**no** `requireCurrentVersion` (GWRL versions independently of SCTracker and must never hit its 426
gate).

## Account API (`/api/account/**`, session auth)

The web counterpart to `GET /module-entitlements`, for the account page. `ModuleEntitlementService`
resolves both off the same logic (`forMachineKey` → `forPerson`).

| Route | Notes |
|---|---|
| `GET /api/account/modules` (`?type=`) | The logged-in person's entitlements — public + granted, same body shape as `/module-entitlements`. `download_url` / `patch_notes_url` are rewritten for this context: `/SCTracker.dll` for `sctracker`'s download, `/api/account/modules/{key}/download` (resp. `.../patch-notes`) for everything else; `patch_notes_url` stays `null` when the module has none. |
| `GET /api/account/modules/{key}/download` | Streams the bytes (same headers as `/modules/{key}/download`) after checking the **logged-in person's** grant — 403 without one, 404 unknown/disabled, 503 bytes missing. Exists because a browser `<a download>` link can't send `X-Machine-Key`, so this is how an entitled user pulls a gated component (e.g. `gwrl-install`) from the account page. |
| `GET /api/account/modules/{key}/patch-notes` | Session counterpart to `GET /modules/{key}/patch-notes` — same entitlement check and 404-when-unconfigured rule, against the **logged-in person's** grant. |

## Admin API (`/api/admin/**`, gated by `AdminAuthInterceptor`)

| Route | Notes |
|---|---|
| `GET /api/admin/modules` | Full registry (enabled or not), sort order. |
| `GET /api/admin/modules/discover` | One bucket scan, two lists: `{ discovered: [...], updates: [...] }`. `discovered` is `plugins/<Folder>/` **and** `launcher/<Folder>/` directories that have an artifact (`<Folder>.dll` / `.zip` / `.exe`, probed in that order) but no registry row yet, with `bucket_prefix` / `artifact_object` / `manifest_object` / `patch_notes_object` pre-derived (the latter two `null` — with `has_manifest` / `has_patch_notes` false — when `<Folder>.version.json` / `<Folder>.patch.txt` don't exist), a slugified `suggested_key`, and `suggested_type` from the prefix (`plugin` for `plugins/`, `module` for `launcher/` — the admin can override on import); the admin imports one via `POST /api/admin/modules`. `updates` is the opposite direction — **already-registered** modules whose folder now has a `.version.json` or `.patch.txt` the row's `manifest_object` / `patch_notes_object` doesn't reference yet (e.g. patch notes added well after the module was first registered), each entry carrying only the proposed value(s) for whichever field(s) are currently `null` — never proposes changing or clearing a field that's already set, and never touches `artifact_object`; the admin applies one via the normal `PATCH /api/admin/modules/{moduleKey}` with those fields. Both empty when no bucket is configured. |
| `POST /api/admin/modules` | Create. `module_key` validated + unique (409 on dup); `display_name` / `bucket_prefix` / `artifact_object` required; `is_public` defaults false, `sort_order` 0, `content_type` `application/octet-stream`, `manifest_object` / `patch_notes_object` both optional (nullable). |
| `PATCH /api/admin/modules/{moduleKey}` | Partial update (any subset of the mutable fields). Evicts the manifest cache when object paths (`bucket_prefix` / `artifact_object` / `manifest_object`) change — `patch_notes_object` alone does **not** evict it, since patch notes aren't manifest-cached. `module_key` is immutable. 404 unknown. |
| `DELETE /api/admin/modules/{moduleKey}` | Hard delete (grants cascade). 409 for `sctracker` (the one built-in key) — disable it instead. |
| `GET /api/admin/users/{personId}/modules` | Per-user checklist: every enabled module + `granted`. Public modules come back `granted:false`, shown as "always available". |
| `PUT /api/admin/users/{personId}/modules/{moduleKey}` | Grant (idempotent, keeps the original `granted_by/at`). 404 unknown user or module. |
| `DELETE /api/admin/users/{personId}/modules/{moduleKey}` | Revoke (idempotent). |

`AdminUserService` delegates the grant sub-resource to `ModuleGrantService` after its `requireUser`
check — the same shape it already uses for character management. `granted_by` comes from the
admin's session via `@CurrentPersonId`.

## CI / hosting

See [07-deployment](07-deployment.md) "Module artifacts". Same bucket, one prefix per artifact
(`plugins/<Name>/` for plugins, `launcher/<Name>/` for launcher components); no new env var or IAM.
No seed changeset for the launcher rows — they're uploaded to the bucket and registered via
**Scan bucket** → import (or *Add a module*), then granted per user like any gated module.
