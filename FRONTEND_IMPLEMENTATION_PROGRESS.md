# Frontend Implementation Progress

Tracks implementation of `specs/frontend/*.md`. Same pattern as `IMPLEMENTATION_PROGRESS.md` for the backend.

## Status as of this session

**Node/npm are now available, and this has all been run for real for the first time.** `npm install && npm run build` succeeds with zero changes needed — every by-hand check from the previous session (import paths, named-export cross-checks, CSS-Modules class names, `vite-env.d.ts`) held up against a real `tsc`. Added a Playwright e2e suite (`frontend/e2e/*.spec.ts`, 13 tests) exercising the app against a real running backend + MySQL: auth (signup/login/logout/redirect/wrong-password), characters (add/list/remove/duplicate-name), account (machine-key generate-once-reveal/revoke), and a full-stack flow test that posts a real `/upload-run` payload (machine-key authenticated, as the SDK plugin would), then drives the actual UI through run history → run detail → adding a character → the retroactive backfill → the leaderboard showing a personal best that didn't exist until the character was linked. All 13 passing. `npm run test:e2e` runs it (Playwright manages its own Vite dev server via `playwright.config.ts`'s `webServer`; the backend + MySQL are a documented prerequisite — see `make test-frontend` in the root `Makefile`, which brings both up automatically).

One real bug in the e2e suite itself, not the app: a `getByRole('cell', { name: '8' })` selector matched a date cell containing "8" as well as the actual "8 participants" cell (Playwright role selectors substring-match by default) — fixed with `exact: true`.

**All of F0–F5 are implemented** — full file tree below. Plus one addition not in the original specs: a run-duration-over-time chart on the Run History page, added mid-session per a direct request, built against the `dataviz` skill's method (form/color/marks/interaction procedure) rather than picked ad hoc.

**Update following a real payload sample (see `IMPLEMENTATION_PROGRESS.md` for the full backend-side story)**: `api/types.ts`'s `RunDetail.instance_start` (typed as a date string) was wrong — that field is confirmed to not be a timestamp at all, so it's renamed to `instance_start_ms` (a plain number, never format it as a date). Also added `is_player`/`is_hero`/`is_henchman` to `ParticipantEntry` and `indent` to `ObjectiveEntry` for parity with the backend's DTOs, though nothing renders them — a "Type" column briefly added to `RunDetail.tsx`'s participant table was reverted once the user confirmed real 8-man parties are always all human players, which would have made that column always read "Player" for every row.

## Dead code found and removed

`src/common/professions.ts` (a hardcoded profession-id → name map) was written during scaffolding on the assumption in `specs/frontend/00-overview.md` that the frontend would need one. It was never actually imported anywhere. Checked why: `specs/backend/06-run-history.md`'s `GET /api/runs/{id}` already resolves profession names server-side (`RunParticipant.primaryProfession.getName()`, confirmed by reading `ParticipantEntry.java`) — the frontend never receives a raw profession id to look up. Deleted the file and corrected the spec's "Static reference data" section, which had assumed otherwise.

## The chart (added mid-session)

`src/components/RunTimelineChart.tsx` + `.module.css` — scatter plot (not a line: each run is an independent event), x = `utc_start`, y = `duration_ms`, rendered on `RunHistory.tsx` above the existing filterable table. Built against the `dataviz` skill:
- **Status colors are the skill's fixed/pre-validated palette** (`good`/`warning`/`serious`/`critical` — hex values from `references/palette.md`), not invented GW1-tinted colors — status colors are explicitly "fixed, never themed" in that skill, unlike chart chrome (surface/gridlines/axis), which *is* adapted to the parchment theme.
- **Could not run `scripts/validate_palette.js`** — no Node.js here either. Used the skill's own pre-validated values specifically *because* I couldn't validate my own choices, rather than inventing colors I had no way to check. This is the one thing about the chart I'd most want re-verified for real (run the validator once Node is available: `node scripts/validate_palette.js "#0ca30c,#fab219,#ec835a,#d03b3b" --mode light`).
- Four run outcomes (completed/wipe/resign/unknown) map to the four status roles, each with a **distinct shape** (circle/X/triangle/diamond, `StatusIcon.tsx`) plus a dark stroke outline — never color-alone, since warning/serious are sub-3:1 contrast on a light surface by the skill's own numbers.
- Legend always present; per-point hover/focus tooltip (value leads, label follows, `textContent` via JSX — never `innerHTML`); 24px transparent hit target per point (skill's minimum, well above the 11px visible mark).
- "Scrolled over" (the literal ask) implemented as fixed pixel-per-day density inside a horizontally-scrolling container — not a zoom/brush interaction, which wasn't asked for.
- The existing paginated run table (same page, below the chart) is the "table view" the skill's accessibility check wants — not duplicated inside the chart component.
- **Step 7 of the skill's procedure — "render it and look at it" — still hasn't been done.** A browser is available now (Playwright's Chromium), but this session's e2e suite doesn't visit `/runs` and assert anything about the chart specifically — it wasn't the focus of the integration-testing work. Still the single most likely place for a real visual bug (label collision, overflow, an off-by-one in the tooltip clamping); worth a manual look or a dedicated Playwright screenshot test.

## File tree

```
frontend/
  package.json, vite.config.ts, tsconfig.json, index.html
  src/
    main.tsx, App.tsx, vite-env.d.ts
    api/client.ts, api/types.ts
    auth/AuthContext.tsx, auth/ProtectedRoute.tsx
    common/roles.ts, common/format.ts, common/runStatus.ts
    styles/theme.css
    components/
      Panel, ErrorBanner, RoleBadge, StatusBadge, StatusIcon, Layout, RunTimelineChart
      (each with a .module.css sibling except StatusIcon, which is pure SVG shapes)
    pages/
      Login, Signup (share AuthPage.module.css)
      Account, Characters, Dashboard, LeaderboardPage, RunHistory, RunDetail
  e2e/
    helpers.ts, auth.spec.ts, characters.spec.ts, account.spec.ts, run-flow.spec.ts
  playwright.config.ts
```

## Phase checklist

- [x] **F0 — Scaffolding**: package.json, vite.config.ts (dev proxy), tsconfig.json, index.html, main.tsx, App.tsx, api/client.ts + types.ts, common/roles.ts + format.ts + runStatus.ts, styles/theme.css, Panel/ErrorBanner/RoleBadge/StatusBadge/StatusIcon/Layout
- [x] **F1 — Auth**: AuthContext, ProtectedRoute, Login, Signup, logout in Layout
- [x] **F2 — Account**: profile + machine-key generate (one-time reveal)/list/revoke
- [x] **F3 — Characters**: list/add/remove
- [x] **F4 — Leaderboards**: Dashboard (map picker), LeaderboardPage (overall/your-best/sections)
- [x] **F5 — Run history**: RunHistory (filters + chart + paginated table), RunDetail
- [x] **E2E**: Playwright suite against a real backend — auth, characters, account/machine-keys, and a full upload→history→detail→character-link→leaderboard flow. `npm run test:e2e` / `make test-frontend`.

## Notes / things worth a second look

1. **The chart's visual render** — still not actually looked at, see above; the e2e suite doesn't cover it.
2. **`RunHistory.tsx`'s chart fetch size (500)** — a pragmatic bound I picked, not specified anywhere; revisit if a guild's run history is large enough that 500 isn't representative, or if it's overkill and slow.
3. **Person/character filters on `/runs`** are plain numeric ID inputs (spec 05 explicitly allows this as a fallback: "a plain id input is an acceptable fallback if not [a search-by-name endpoint]") — fine for now, but not very usable; a typeahead-by-name would need a new backend endpoint if it's wanted later.
4. **The e2e suite has no DB reset between runs** — unlike the backend's Testcontainers-backed integration tests (fresh container, truncated between tests), e2e tests run against whatever local dev MySQL is up, and every test generates unique usernames/character names/map ids (`e2e/helpers.ts`'s `uniqueName`) specifically so repeated runs don't collide with leftover data. Don't assume a clean slate when adding new e2e tests — seed and filter defensively, the way `run-flow.spec.ts` picks a random `mapId` rather than assuming it's the only run for that map.
