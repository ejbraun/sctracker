# 10 — Admin password reset

An admin generates a one-time link for a specific user; the user opens it and sets a new password.
There is no email on `people`, so the admin copies the link and sends it out of band (same model as
signup links, `08-admin-signup-links.md`). Pairs with `specs/backend/03-auth.md` ("Password reset").

## Admin side — `AdminUsers.tsx`

Each user row has a **Reset password** button (its own "Password" column). It asks `window.confirm`
("…Any earlier unused link for them stops working."), then `POST /api/admin/users/{id}/password-reset`.
The raw token comes back **exactly once**: a dismissible reveal box above the table shows
`<origin>/reset-password?token=<token>` (`data-testid="password-reset-url"`) with the username, the
expiry (`formatDate(expires_at)`), a "Copy link" button and a "won't be shown again" warning. The URL is
assembled client-side; the token lives only in component state. Errors surface in an `<ErrorBanner>`.
Admins may reset their own password too.

## User side — `ResetPassword.tsx` at `/reset-password`

Public route (outside `ProtectedRoute`, next to `/signup`), styled like `Signup.tsx` (`AuthPage.module.css`).

- No `?token=` → a short "link is missing its token, ask an admin" message with a link to `/login`.
- Otherwise a form: **New password** + **Confirm new password**. Client-side: at least 8 characters and
  the two must match. Submits `POST /api/password-reset` `{ token, password }`.
- On success (204) it navigates to `/login` with router state `{ passwordReset: true }`; `Login.tsx` shows
  "Password updated. Log in with your new password." A reset does **not** start a session.
- Bad / expired / used link → the backend's generic `400 invalid or expired reset link` in an `<ErrorBanner>`.
