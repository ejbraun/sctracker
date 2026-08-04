# 02 — Account (Profile & Machine Keys)

Protected page, `/account`. Pairs with `specs/backend/03-auth.md`'s machine-key endpoints.

## Profile section
Read-only display of `username` from the already-loaded `AuthContext.person` (`GET /api/account/me`) — no separate fetch needed.

## Machine keys section
Table listing `GET /api/account/machine-keys`: columns `label`, `created_at`, status (`Active` / `Revoked <date>` derived from `revoked_at`).

**Generate**: a "Generate new key" button opens a small form (optional `label` input) → `POST /api/account/machine-keys`. On success, the raw key is shown **exactly once** in a modal or dismissible banner:
- Monospace display of the raw key, a "copy to clipboard" button, and an explicit warning ("this won't be shown again — store it in the GW1 plugin config now").
- The raw key exists only in that response and component-local state; it's never persisted anywhere client-side (not in TanStack Query's cache, not in localStorage) and disappears once the modal is dismissed or the page is left, matching the backend never storing it either (`specs/backend/01-schema-and-migrations.md`).
- After dismissal, refetch the key list so the new (label-only) row appears.

**Revoke**: a "Revoke" action per active row → confirmation dialog ("uploads using this key will stop working") → `DELETE /api/account/machine-keys/{id}` → refetch the list. Revoked keys stay visible in the table (struck through or greyed, per the soft-delete semantics in spec 03) rather than disappearing, so there's a visible audit trail of what used to exist.
