# 07 — Admin: Modules & per-user grants

Admin-only. Pairs with `specs/backend/08-module-entitlements.md`. Gated by `AdminRoute`
(`person.is_admin`); the nav shows a **Modules** link next to **User Management** / **Run Cleanup**.

## Modules page (`/admin/modules`)

`AdminModules.tsx` — the `modules` registry (the GWToolbox++ plugin DLLs gwsctracker hosts for
ProjectPotato, plus SCTracker). Per-user access lives on the User Management page, not here.

- **Scan bucket for new modules** button → `GET /api/admin/modules/discover` (lazy — only fetched
  after the first click). Lists each unregistered `plugins/<Folder>/` or `launcher/<Folder>/` as a
  row with an editable `suggested_key` / `suggested_display_name`, a `type` `<select>` pre-set from
  `suggested_type` (`module` for `launcher/` finds, `plugin` otherwise), an `is_public` checkbox
  (**default off**), and an **Import** button → `POST /api/admin/modules` with the pre-derived
  `bucket_prefix` / `artifact_object` / `manifest_object` plus the chosen `type`. On success it
  re-scans and the registry list refreshes.
- **Table** from `GET /api/admin/modules`: `module_key` (read-only `<code>`); a `type`
  `<select>` (`plugin` / `module`) that `PATCH`es on change; editable inputs for `display_name`,
  `bucket_prefix`, `artifact_object`, `manifest_object`, `content_type`, `sort_order`; toggle
  buttons for `is_public` (Public/Private) and `enabled` (Enabled/Disabled); read-only
  `current_version` + `version_detected_at`. A per-row **Save** (enabled only when the row is
  dirty) → `PATCH /api/admin/modules/{module_key}`. A **Delete** button (with `window.confirm`) →
  `DELETE /api/admin/modules/{module_key}`, hidden for `sctracker` (the one built-in key; the API
  409s on it anyway).
- **Add a module** form → `POST /api/admin/modules` (for artifacts a scan wouldn't find). `module_key`,
  `display_name`, `bucket_prefix`, `artifact_object` required; `type` select (default `plugin`);
  `manifest_object` / `content_type` optional; `is_public` checkbox. Server-side validation errors
  (bad key charset, duplicate, blank field) surface in an `<ErrorBanner>`.
- All mutations invalidate `['admin', 'modules']`.

## Per-user grants (in User Management)

`AdminUsers.tsx` — a **Modules** column (its own toggle + expansion state, independent of the
Characters column) renders `<UserModules>` in an expanded row.

- Table from `GET /api/admin/users/{personId}/modules`: `display_name` + `module_key`, an access
  label (`Public` / `Granted` / `No access`), and a **Grant** / **Revoke** button
  (`PUT` / `DELETE /api/admin/users/{personId}/modules/{module_key}`, both 204, both idempotent).
  Public modules render "always available" with no button.
- Mutations invalidate `['admin', 'users', personId, 'modules']`.
