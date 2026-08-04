# 01 — Auth Pages

Public pages plus the session-boundary components every other page depends on. Pairs with `specs/backend/03-auth.md`.

## `/login`
Username + password form → `POST /api/login`.
- On success: invalidate the `account/me` TanStack Query cache entry (triggers `AuthContext` to refetch and pick up the logged-in person), then `navigate(redirectParam ?? '/')`.
- On `401`: show the `ApiError` message inline (same generic "invalid credentials" text the backend returns — don't editorialize on whether it was the username or password).

## `/signup`
Username + password + confirm-password (confirm is client-side only, never sent) → `POST /api/signup`.
- Client-side validates password length ≥ 8 as a UX nicety, mirroring the backend's minimum — the backend remains the source of truth; a `400` from the server is still handled and shown the same way.
- `409` (username taken) shown inline on the username field.
- On success: same redirect flow as login (signup also starts a session per spec 03).

## Logout
A nav-bar button (rendered only when `AuthContext.person` is non-null) → `POST /api/logout`, then `queryClient.clear()` (drop all cached server state, not just the auth query — stale leaderboard/history data from the previous account shouldn't linger) and `navigate('/login')`.

## `ProtectedRoute`
Wraps the authenticated route tree in `App.tsx`. Reads `AuthContext`:
- While `isLoading`: render a neutral loading state (avoid a login-page flash before the `/account/me` check resolves).
- `person === null`: `<Navigate to={\`/login?redirect=${currentPath}\`} />`.
- Otherwise: render the matched route.
