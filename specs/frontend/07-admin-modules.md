# 07 — Admin: Modules & per-user grants

Admin-only. Pairs with `specs/backend/08-module-entitlements.md`. Gated by `AdminRoute`
(`person.is_admin`); the nav shows a **Modules** link next to **User Management** / **Run Cleanup**.

## Modules page (`/admin/modules`)

`AdminModules.tsx` — the `modules` registry (the artifacts gwsctracker hosts for the ProjectPotato
launcher). Per-user access lives on the User Management page, not here.

- **Table** from `GET /api/admin/modules`: `module_key` (read-only `<code>`), then editable inputs for
  `display_name`, `bucket_prefix`, `artifact_object`, `manifest_object`, `content_type`,
  `sort_order`; toggle buttons for `is_public` (Public/Private) and `enabled` (Enabled/Disabled);
  read-only `current_version` + `version_detected_at`. A per-row **Save** (enabled only when the
  row is dirty) → `PATCH /api/admin/modules/{module_key}`. A **Delete** button (with
  `window.confirm`) → `DELETE /api/admin/modules/{module_key}`, hidden for the built-in keys
  `sctracker` / `pp-exe` / `pp-base` (the API 409s on those anyway).
- **Add a module** form → `POST /api/admin/modules`. `module_key`, `display_name`, `bucket_prefix`,
  `artifact_object` required; `manifest_object` / `content_type` optional; `is_public` checkbox.
  Server-side validation errors (bad key charset, duplicate, blank field) surface in an
  `<ErrorBanner>`.
- All mutations invalidate `['admin', 'modules']`.

## Per-user grants (in User Management)

`AdminUsers.tsx` — the expanded row for a user now renders `<UserModules>` under `<UserCharacters>`.

- Table from `GET /api/admin/users/{personId}/modules`: `display_name` + `module_key`, an access
  label (`Public` / `Granted` / `No access`), and a **Grant** / **Revoke** button
  (`PUT` / `DELETE /api/admin/users/{personId}/modules/{module_key}`, both 204, both idempotent).
  Public modules render "always available" with no button.
- Mutations invalidate `['admin', 'users', personId, 'modules']`.
