# 08 — Admin: Signup Links

Admin-only page for minting multi-use signup links — one shareable URL that lets up to `max_uses`
people (default 10) create an account, instead of handing out single-use signup keys one at a time.
Pairs with `specs/backend/03-auth.md` ("Signup links (admin)").

## Access

`AdminSignupLinks.tsx` at `/admin/signup-links`, gated by `AdminRoute` (`person.is_admin`). The nav
shows a **Signup Links** link next to **Modules** / **User Management** / **Run Cleanup**.

## Page

`<h1>Signup Links</h1>` + a single `<Panel>` with a one-paragraph explainer, then:

- **Generate** — a form with an optional `label` and an optional `max_uses` number input
  (placeholder `10`, 1–100) → `POST /api/admin/signup-links`. On success the raw token comes back
  **exactly once**: show `<origin>/signup?invite=<token>` in a dismissible reveal box
  (`data-testid="signup-link-url"`), with a "Copy link" button (`navigator.clipboard.writeText`)
  and a "won't be shown again" warning. The URL is assembled client-side from
  `window.location.origin` — the backend only returns the token. The token lives only in component
  state, never persisted.
- **Table** — `label` / `created` (`formatDate`) / uses (`use_count / max_uses`) / status
  (`revoked_at` → `Revoked <date>`; else `use_count >= max_uses` → `Used up`; else `Active`) /
  a per-row **Revoke** button behind `window.confirm`, hidden once the link is revoked. Revoked or
  used-up rows are greyed.

## Data

TanStack Query key `['admin', 'signup-links']`; both mutations invalidate it. Types `SignupLink` /
`GeneratedSignupLink` in `frontend/src/api/types.ts`. Server-side validation errors surface in an
`<ErrorBanner>`.
