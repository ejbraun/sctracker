# 02 — Account (Profile & Machine Keys)

Protected page, `/account`. Pairs with `specs/backend/03-auth.md`'s machine-key endpoints.

## Profile section
Read-only display of `username` from the already-loaded `AuthContext.person` (`GET /api/account/me`) — no separate fetch needed.

## Downloads section
A **SCTracker** panel — headed `SCTracker` with a **`required`** badge — with a static
`<a href="/SCTracker.dll" download>` link (version suffix from the top-level `GET /plugin-version`).
Its copy states plainly that this is the only plugin the site needs (it's what uploads runs), and
that anything below it is an optional extra.

Below it, download panels driven by `GET /api/account/modules` (`AccountModulesResponse` — the
logged-in user's public + granted modules, see `specs/backend/08-module-entitlements.md`):
- **One panel per `type: "plugin"` entry** other than `sctracker` (in the backend's `sort_order`)
  — e.g. the GWToolbox and DBBox plugin dlls, both public so always present. Each is headed with
  the module name plus an **`optional`** badge, and its copy opens with "Optional — not needed to
  submit runs." Copy is otherwise the generic "drop the .dll in GWToolbox's Plugins folder" except
  where `PLUGIN_BLURB` overrides it by key (GWToolboxdll is the toolbox itself, not a drop-in).
- **Launcher** — rendered only when a `gwrl-install` entry is present, i.e. the user has been
  granted the gated launcher. Missing entry ⇒ no panel.

Each links `entry.download_url` with a `download` attribute (`/api/account/modules/{key}/download`,
a session-authed stream) and shows `(v{version})` only when `entry.version` is a positive integer
(a manifest with no `version` deserializes to 0). The query failing just omits these panels.

## Machine keys section
Table listing `GET /api/account/machine-keys`: columns `label`, `created_at`, status (`Active` / `Revoked <date>` derived from `revoked_at`).

**Generate**: a "Generate new key" button opens a small form (optional `label` input) → `POST /api/account/machine-keys`. On success, the raw key is shown **exactly once** in a modal or dismissible banner:
- Monospace display of the raw key, a "copy to clipboard" button, and an explicit warning ("this won't be shown again — store it in the GW1 plugin config now").
- The raw key exists only in that response and component-local state; it's never persisted anywhere client-side (not in TanStack Query's cache, not in localStorage) and disappears once the modal is dismissed or the page is left, matching the backend never storing it either (`specs/backend/01-schema-and-migrations.md`).
- After dismissal, refetch the key list so the new (label-only) row appears.

**Revoke**: a "Revoke" action per active row → confirmation dialog ("uploads using this key will stop working") → `DELETE /api/account/machine-keys/{id}` → refetch the list. Revoked keys stay visible in the table (struck through or greyed, per the soft-delete semantics in spec 03) rather than disappearing, so there's a visible audit trail of what used to exist.
