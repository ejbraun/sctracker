# gwsctracker

A run tracker for GW1  speed-clear group. A companion
[GWToolbox++ plugin](https://github.com/ejbraun/GWToolboxpp/tree/party-log-plugin/plugins/SCTracker)
uploads run data automatically as members play; this app stores it and serves leaderboards, "loserboards,"
run history, and per-player stats over the web.

Live at **https://gwsctracker.com**.

## How it fits together

```
GWToolbox plugin (SCTracker.dll)          Browser
        |                                    |
        | POST /upload-run                   | everything else
        | (X-Machine-Key header)             | (session cookie)
        v                                    v
              Spring Boot app (single jar)
              - serves the built React SPA as static content
              - JSON API under /api/**
        |
        v
   MySQL (Liquibase-managed schema)
```

One deployable artifact: the backend is a normal Spring Boot app, and the built React frontend is
copied into `src/main/resources/static/` at Docker build time, so the same jar serves both the API
and the website — no separate frontend server or reverse proxy needed.

Two independent auth planes, since the plugin and the browser are different kinds of clients:

- **Machine key** (`X-Machine-Key` header) — the *only* thing the GW1 plugin ever sends. A
  high-entropy per-account key, generated from the Account page and shown exactly once. Guards
  `POST /upload-run`, nothing else.
- **Session cookie** — everything a logged-in browser does, under `/api/**`. Hand-rolled
  `HttpSession` + an interceptor rather than full Spring Security (only
  `spring-security-crypto` is pulled in, for `BCryptPasswordEncoder`).

Signup is invite-gated: a single-use signup key (same hashing scheme as machine keys) is required
alongside username/password, so the site isn't open to the public.

## Features

- **Ingestion** (`POST /upload-run`) — accepts the plugin's party/objective payload, dedups
  concurrent uploads from different party members into one `runs` row (a MySQL named lock scoped
  per-map), derives each player's role (T1–T4, LT, Spiker, Derv, SoS, Necro, RangerNecro, Emo) from their profession combo,
  and links participants to registered characters by exact name match.
- **Leaderboards** — fastest completed full runs, fastest per-objective ("section") times, and
  personal bests (aggregated across every character a person has linked). Section personal bests
  are role-gated against a `role_objectives` mapping, so e.g. a spiker doesn't get credit for an
  escort they had no part in.
- **Loserboards** — the mirror image: worst completion times, deaths by role, quest/objective
  fails by role, and resign counts by user.
- **Run history** — filterable (by person, character, role, map, date range, completion status),
  paginated list plus a full detail view per run (objectives + participants).
- **Characters** — link your GW1 character name(s) to your account. Adding a character
  retroactively links any past uploads under that raw name that predate the character existing.
- **Account** — set a display alias, generate/revoke machine keys, download the plugin DLL.
- **How to Use** (`/how-to-use`) — in-app onboarding walkthrough: set alias → add character(s) →
  generate machine key → download plugin → install into GWToolbox → play.

## Tech stack

- **Backend**: Spring Boot 3.5.4, Java 25, Maven (with a committed Maven Wrapper).
- **Database**: MySQL 8.4, schema managed entirely via Liquibase changelogs — auto-applied on
  boot, no manual migration step in prod.
- **Frontend**: React + TypeScript, Vite, TanStack Query for server state, React Router, hand-rolled
  CSS Modules for a Guild Wars 1-inspired theme (dark bronze chrome, parchment panels).
- **Deployment**: GCP Cloud Run (single always-on instance — see below), Cloud SQL for MySQL,
  Artifact Registry, Cloud Domains + Cloud Run domain mapping for the custom domain and managed
  TLS cert.

## Repo layout

```
pom.xml, mvnw                    # backend build
Dockerfile                       # multi-stage: npm build -> maven build -> slim JRE runtime
docker-compose.yml                # local MySQL for dev
Makefile                          # mysql-up/migrate/test-backend/test-frontend targets
src/main/java/com/howl/uwtracker/
  ingestion/                      # POST /upload-run
  auth/                           # signup/login/session/machine-keys
  characters/, leaderboards/, loserboards/, history/
  domain/, repository/            # JPA entities + Spring Data repos
src/main/resources/
  db/changelog/                   # Liquibase changesets (auto-run on boot)
  static/SCTracker.dll            # the plugin binary, served directly for download
  application.properties
frontend/
  src/pages/, components/, api/, auth/, common/, styles/
  e2e/                             # Playwright end-to-end tests
specs/backend/, specs/frontend/    # detailed per-feature design specs — start with the 00-overview
                                    # in each folder
```

## Local development

```
make db-up          # docker-compose MySQL + Liquibase migrate
mvn spring-boot:run  # backend on :8080
cd frontend && npm run dev   # Vite dev server on :5173, proxies /api and /SCTracker.dll to :8080
```

Tests: `mvn test` (integration tests spin up real MySQL via Testcontainers, no `db-up` needed) and
`make test-frontend` (Playwright e2e against a real backend + MySQL).

## Deployment

Single Cloud Run service pinned to `--min-instances=1 --max-instances=1` — sessions are plain
in-memory `HttpSession`, not backed by a shared store, so pinning to one instance sidesteps
cross-instance session visibility entirely at the cost of not autoscaling. (A redeploy or restart
logs everyone out, which is an accepted trade-off at this scale — see
[`specs/backend/03-auth.md`](specs/backend/03-auth.md) before changing instance counts.)

Cloud Run connects to Cloud SQL over a private Unix socket via
`com.google.cloud.sql:mysql-socket-factory-connector-j-8` — no public IP exposure. The DB password
lives in Secret Manager, injected as an env var at deploy time; nothing sensitive is baked into the
image or committed to this repo.

```
gcloud builds submit --tag us-central1-docker.pkg.dev/PROJECT/uwtracker-repo/uwtracker:latest .
gcloud run deploy uwtracker --image=... --region=us-central1
```

## Related repos

- [GWToolbox plugin (SCTracker)](https://github.com/ejbraun/GWToolboxpp/tree/party-log-plugin/plugins/SCTracker) — the GW1 client-side plugin that captures run data and uploads it here.
- This repo — website frontend + backend.

## Specs

`specs/backend/00-overview.md` and `specs/frontend/00-overview.md` are the entry points for
detailed, per-feature design docs (schema, endpoints, request/response shapes, and the reasoning
behind non-obvious decisions) — everything above is a summary of what's written out in full there.
