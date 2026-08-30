# 00 — Overview & Conventions

Cross-cutting reference for all other specs in this folder. Read this first.

## Tech stack

- **Backend**: Spring Boot 3.5.4, Java 25, Maven.
- **Database**: MySQL 8.4, schema managed via Liquibase (`src/main/resources/db/changelog/`, see [01-schema-and-migrations](01-schema-and-migrations.md)).
- **Frontend**: React, built to static assets and served directly from Spring Boot's `src/main/resources/static` — single deployable artifact, no separate frontend server.
- **Deployment**: GCP Cloud Run + Cloud SQL (MySQL) — see [07-deployment](07-deployment.md).

## Repo layout (target state after these specs are implemented)

```
pom.xml
Makefile
docker-compose.yml                      # local MySQL for dev
db/                                      # legacy — superseded, see note below
frontend/                                # React app — see specs/frontend/00-overview.md
  package.json
  vite.config.ts
  src/
src/main/java/com/howl/uwtracker/
  Application.java
  ingestion/                             # POST /upload-run (spec 02)
  auth/                                  # signup/login/session (spec 03)
  characters/                            # spec 04
  leaderboards/                          # spec 05
  history/                               # spec 06
  domain/                                # JPA entities (spec 01)
  repository/                            # Spring Data repositories (spec 01)
src/main/resources/
  db/changelog/                          # Liquibase changelog (moved here so Spring Boot auto-runs it on boot)
  static/                                # built frontend/dist lands here at Docker-build time — never committed here directly
  application.properties
  application-prod.properties            # Cloud Run / Cloud SQL overrides
specs/
  backend/                               # this folder
  frontend/                              # React app specs — see specs/frontend/00-overview.md
```

Package names are `com.howl.uwtracker.*`, matching `pom.xml`'s `groupId`/`artifactId` (`com.howl` / `uwtracker`).

## Two auth planes

| Plane | Header/mechanism | Endpoints |
|---|---|---|
| Machine key | `X-Machine-Key` request header | `POST /upload-run` only |
| Session cookie | `HttpSession` via browser cookie | Everything else, namespaced under `/api/*` (signup/login are public but still session-issuing; all other `/api/*` endpoints require an active session) |

These are deliberately separate. The SDK plugin that uploads runs never sees a session cookie; the website never sends a machine key. See [02-ingestion-upload-run](02-ingestion-upload-run.md) and [03-auth](03-auth.md) respectively.

## Routing: `/api` prefix & SPA fallback

The built React app and the JSON API are served from the same origin (see repo layout above), so an unprefixed path like `/runs` is ambiguous — the SPA's `/runs` page, or the backend's run-history endpoint? To disambiguate, every session-cookie-plane endpoint is namespaced under `/api/*` (`/api/signup`, `/api/characters`, `/api/runs`, ...); the frontend's own routes stay unprefixed (`/runs`, `/characters`, ...) since React Router owns those client-side.

`POST /upload-run` is the one exception — it's never browser-navigated and never collides with a frontend route, so it stays unprefixed as a stable external contract for the SDK plugin. A few other plugin-facing endpoints follow the same convention: `/report-run-failure`, `/can-report-run-failure`, `/report-run-mvp`, `/plugin-version`, and `/SCTracker.dll` (`PluginDllController` — streams the plugin binary from the GCS-backed cache, replacing what used to be a committed static file).

This requires one more piece on the backend: a catch-all controller forwarding any request that isn't an explicit `@RequestMapping` (`/api/**`, `/upload-run`, `/SCTracker.dll`, ...) or a static asset (any path with a `.`) to `index.html`, so React Router can render deep links (e.g. a browser hitting `/runs/42` directly) instead of getting a 404.

## Shared conventions

**Numeric sentinel**: `4294967295` (`2^32 - 1`, i.e. `uint32` max) appearing in *any* numeric field of the `/upload-run` payload means "not reached" and must be mapped to `null` before persisting. This applies per-field, independently — e.g. one objective's `done` can be the sentinel (not yet done) while its `start` is a real value.

**Timestamps**: All persisted as UTC (`DATETIME(6)`, no offset stored — the app treats every stored timestamp as UTC by convention). **Confirmed against a real GWToolboxdll payload sample** (superseding an earlier draft's epoch-milliseconds assumption for everything):
- `party.utc_start` and `objective.utc_start` are `time(nullptr)` — real wall-clock epoch **seconds**, comparable across machines/days. These are what dedup keys off (spec 02).
- `objective.instance_start` is **not a timestamp at all** — a `std::chrono::steady_clock`-based (or TimerWidget load-screen) millisecond counter zeroed at an arbitrary point tied to system boot. It has no absolute meaning and isn't comparable across runs or machines; its only purpose is as the zero-point each objective's `start`/`done`/`duration` is measured relative to, in milliseconds. Stored as a raw `BIGINT` offset (`runs.instance_start_ms`), never converted to a wall-clock value.
- `objectives[].start`/`done`/`duration` and the top-level `objective.duration` **are** milliseconds — but relative to `instance_start`, not absolute epoch milliseconds. Storage as a raw `BIGINT` millisecond value is unaffected either way; only `instance_start` itself needed a type change.

**Error response shape**:
```json
{ "error": "human-readable summary", "details": { } }
```
`details` is optional and endpoint-specific (e.g. validation field errors). Used for all 4xx/5xx JSON responses.

**Pagination** (used by [06-run-history](06-run-history.md)): offset-based, 0-indexed.
- Request: `?page=0&size=25`
- Response envelope:
```json
{ "items": [ ], "page": 0, "size": 25, "totalElements": 0, "totalPages": 0 }
```

## Reference data

### `GET /api/maps`
Session-auth plane (any logged-in website user, not the SDK plugin). Lists every map that has ever appeared in an uploaded run:
```json
[ { "id": 234, "name": "..." } ]
```
`name` may be `null` if not yet backfilled (spec 01). Backs the map picker in [05-leaderboards](05-leaderboards.md) and the map filter in [06-run-history](06-run-history.md) — documented once here since both consume it. `professions` and the 8 role codes are *not* exposed via an endpoint — they're static, small, and hardcoded as frontend constants instead (see `specs/frontend/00-overview.md`).

## Superseding the current scaffolding

The repo currently has a placeholder `db/changelog/db.changelog-master.xml` + `db/changelog/changes/001-create-runs-table.xml` (a single generic `runs` table with a raw JSON payload column) and a stub `JsonController` at `POST /upload-runs` that just logs the raw body. Both predate this spec set and get replaced wholesale:
- The real schema lives in [01-schema-and-migrations](01-schema-and-migrations.md); the changelog moves from `db/changelog/` to `src/main/resources/db/changelog/` so Spring Boot can auto-apply it on boot (in addition to the existing `make migrate` CLI path, both pointed at the same file).
- The real ingestion endpoint is `POST /upload-run` (singular) per [02-ingestion-upload-run](02-ingestion-upload-run.md), replacing `JsonController`.

## Open questions carried across specs

These are flagged in more detail in their owning spec, listed here for visibility:
1. ~~Epoch-millis assumption for all payload timestamp/duration fields~~ — **resolved**: confirmed against a real payload sample that `utc_start` fields are epoch seconds and `instance_start` is a non-absolute steady_clock offset, not a timestamp. See "Timestamps" above.
2. Ordered vs. unordered primary/secondary profession matching for role derivation (spec 02) — spec assumes ordered.
3. Cross-instance session storage on Cloud Run (spec 03 / 07) — v1 recommendation is `min-instances=1` rather than a shared session store; needs sign-off before autoscaling is enabled.
4. `role_objectives` seed data (spec 01 / 05) — the schema and role-gated personal-best query are specified, but the actual per-map role↔objective associations are GW1 dungeon-mechanics knowledge that has to come from the guild before section personal bests will return anything.
