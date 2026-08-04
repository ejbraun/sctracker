# 00 — Overview & Conventions (Frontend)

Cross-cutting reference for the other `specs/frontend/*` specs. Pairs with `specs/backend/00-overview.md` — read that first for the auth planes and API conventions this all builds on.

## Tech stack

- **React + TypeScript**, built with **Vite**.
- **React Router** (v6+) for client-side routing.
- **TanStack Query** for server state — caching, loading/error states, and cache invalidation after mutations (login, add/remove character, revoke key, etc.), instead of hand-rolled `useState`/`useEffect` fetch plumbing on every page.
- **CSS Modules** + a shared design-token stylesheet for a Guild Wars 1-inspired theme — see [06-theme](06-theme.md).

## Repo location

`frontend/` at repo root, sibling to `pom.xml` (see `specs/backend/00-overview.md`'s repo layout):
```
frontend/
  package.json
  vite.config.ts
  src/
    main.tsx
    App.tsx                # router setup
    api/client.ts          # fetch wrapper + ApiError
    auth/
      AuthContext.tsx
      ProtectedRoute.tsx
    pages/
      Login.tsx, Signup.tsx
      Account.tsx
      Characters.tsx
      Leaderboards.tsx
      RunHistory.tsx, RunDetail.tsx
    components/             # shared UI (tables, banners, badges)
```

## Build integration

`npm run build` → `frontend/dist/`. The Docker multi-stage build (`specs/backend/07-deployment.md`) copies this into `src/main/resources/static/` before `mvn package` — that copy happens only at build time, `dist/` is never committed and `static/` is never hand-edited.

## Routing: `/api` prefix

All backend session-plane endpoints live under `/api/*` (`specs/backend/00-overview.md`'s "Routing: `/api` prefix & SPA fallback" section) specifically so they never collide with the frontend's own unprefixed routes (`/runs` the page vs. `GET /api/runs` the endpoint, etc.). Every API call in this app targets `/api/...` — never a bare path.

## Local dev: same-origin via proxy

`npm run dev` runs the Vite dev server (default port 5173) with a proxy forwarding `/api/**` to `http://localhost:8080` (the Spring Boot app started via the existing `Makefile`/`docker-compose` local setup) — one proxy rule covers everything now that the backend is consistently namespaced. The browser only ever talks to `localhost:5173`, so every request is same-origin from its perspective — **no CORS configuration is needed anywhere**, in dev or prod (prod serves the built frontend from the same Spring Boot instance as the API).

This also means the API client doesn't need `credentials: 'include'` — the fetch default (`'same-origin'`) already sends the session cookie, since the frontend is always same-origin with the API by construction.

## API client (`src/api/client.ts`)

Thin `fetch` wrapper with a `/api` base path: JSON request/response, throws a typed `ApiError` (message + optional details) parsed from the `{ "error": ..., "details": ... }` shape defined in `specs/backend/00-overview.md`. All pages/hooks go through this rather than calling `fetch` directly, so error handling and the base path are defined once.

## Auth state

`AuthContext` wraps the app, calling `GET /api/account/me` (`specs/backend/03-auth.md`) once via TanStack Query on mount, exposing `{ person, isLoading }` (`person` is `null` if unauthenticated). `ProtectedRoute` reads this context and redirects to `/login?redirect=<current path>` when `person` is `null` and loading has finished.

## Route map

| Path | Page | Auth |
|---|---|---|
| `/login` | Login | public |
| `/signup` | Signup | public |
| `/` | Dashboard / map picker | protected |
| `/how-to-use` | Onboarding guide (setup + adding characters) | protected |
| `/account` | Profile + machine keys | protected |
| `/characters` | Character management | protected |
| `/leaderboards/:mapId` | Leaderboard for a map | protected |
| `/runs` | Run history (filterable list) | protected |
| `/runs/:id` | Run detail | protected |

## Loading & error conventions

TanStack Query's `isLoading`/`isError` drive per-page loading and error states; a shared `<ErrorBanner message={apiError.message} />` renders any `ApiError` consistently rather than each page inventing its own error UI.

## Static reference data

The 8 role codes are small and unchanging — hardcoded as a TypeScript constant in the frontend rather than fetched from an endpoint (the backend doesn't expose one, see `specs/backend/00-overview.md`). `maps` **is** fetched (`GET /api/maps`) since it grows as new zones get uploaded.

Professions turned out not to need a frontend-side constant at all: every endpoint that includes profession data (`specs/backend/06-run-history.md`'s `GET /api/runs/{id}`) already resolves it to a name string server-side (`RunParticipant.primaryProfession.getName()`) — the frontend never receives a raw profession id to look up. (An earlier draft of this spec assumed it would and called for a hardcoded id→name map; that map was dead code once actually checked against what the API returns, and was removed.)

## Styling

Guild Wars 1-inspired theme (dark bronze chrome, parchment content panels, gilded borders) — resolved in [06-theme](06-theme.md), which was an open question in earlier drafts of this spec.
