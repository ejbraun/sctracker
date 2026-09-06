# 09 — Downloads (Plugins & Launcher)

Two protected pages, `/plugins` and `/launcher`, split off from what used to be the Account page's
"Downloads section". Both are driven by `GET /api/account/modules` (`AccountModulesResponse` — the
logged-in user's public + granted modules, see `specs/backend/08-module-entitlements.md`), each with
a `?type=` filter. Top-level nav links sit next to **Account**.

A module row with `ui_visible = false` never comes back from `GET /api/account/modules` (the backend
filters it out for the web UI only — the machine-key path still serves it), so neither page ever has
to reason about the launcher-internal artifacts (the GWToolbox host DLL, `gwrl-base`,
`gwrl-<feature>`).

Shared helpers: `versionSuffix()` (`src/common/modules.ts`) renders `(v{version})` only for a
positive integer — a manifest with no `version` deserializes to 0. `<PatchNotes url={...} />`
(`src/components/PatchNotes.tsx`) is a `<details>` disclosure that lazy-fetches the plain-text notes
from `entry.patch_notes_url` on first expand; rendered only when that field is non-null.

## `/plugins`

Fetches `GET /api/account/modules?type=plugin`.

- **SCTracker** panel — hand-written, always shown, headed `SCTracker` with a **`required`** badge.
  Static `<a href="/SCTracker.dll" download>` link with a version suffix from the top-level
  `GET /plugin-version` (not from the modules list). Copy states this is the only plugin the site
  needs (it uploads runs) and that anything below is optional. The matching `sctracker` entry in the
  modules list is consulted only for its `patch_notes_url`.
- **One panel per other `type: "plugin"` entry** (in the backend's `sort_order`), each headed with
  the module name plus an **`optional`** badge; copy opens "Optional — not needed to submit runs."
  Generic "drop the .dll in GWToolbox's Plugins folder" wording unless `PLUGIN_BLURB` / `PLUGIN_TAG`
  overrides it by key. `entry.download_url` (`/api/account/modules/{key}/download`, a session-authed
  stream) with a `download` attribute.

## `/launcher`

Fetches `GET /api/account/modules?type=module`, looks for the `gwrl-install` entry.

- Entry present ⇒ a **GW Launcher Reforged** panel: copy, an `entry.download_url` `download` link
  ("Download launcher" + version suffix), and `<PatchNotes>` when configured.
- Entry absent (not granted) ⇒ an informational panel telling the user the launcher is granted per
  account and to ask an admin. (`gwrl-install` is `is_public = false`, so it only appears once
  granted.)
