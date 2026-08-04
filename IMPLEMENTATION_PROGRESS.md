# Backend Implementation Progress

Tracks implementation of `specs/backend/*.md`. Updated as work happens — if a session ends mid-task, check here for exact state before resuming (what's done, what's in-flight, what's untested).

Legend: `[ ]` not started · `[~]` in progress / partially done · `[x]` done

## Status as of this session

**Docker and a real MySQL are now available, and the app has been exercised for real for the first time.** Added a full Testcontainers-backed integration test suite (`src/test/java/**/*IntegrationTest.java`, ~60 tests across ingestion, auth, machine keys, characters, leaderboards, run history, maps, and the SPA fallback route — see `AbstractIntegrationTest` for the shared setup) plus `make test-backend` to run it. All green as of this session. The packaged jar was also run standalone against `docker-compose.yml`'s MySQL (`make db-up` equivalent + `java -jar target/uwtracker-1.0-SNAPSHOT.jar`) and boots clean — Liquibase applies all 10 changesets, Hibernate schema validation passes, the app serves real requests. This closes out the "top priority: run against real MySQL" item from the previous session.

**Five real bugs surfaced by this — none catchable by `mvn compile`/`mvn test` alone, all now fixed:**
1. **`machine_keys.key_hash` type mismatch**: changelog declares `CHAR(64)` (correct — a SHA-256 hex digest is always exactly 64 chars), but `MachineKey.keyHash` was a bare `String` field with no `columnDefinition`, so Hibernate's schema validator assumed `VARCHAR(64)` and refused to boot. Fixed with an explicit `columnDefinition = "CHAR(64)"`.
2. **Narrow `UNSIGNED` integer columns didn't validate against `Integer` fields**: `TINYINT UNSIGNED`/`SMALLINT UNSIGNED` columns (`professions.id`, `run_objectives.status`/`sequence`/`indent`, `run_participants.party_index` and its profession FK columns) land in a different JDBC type category than the `INTEGER` Hibernate infers for a bare `Integer` field. `INT UNSIGNED`/`BIGINT UNSIGNED` (used everywhere else — PKs, FKs, `*_ms` columns) are fine as-is since they match `Integer`/`Long`'s natural category; only the narrower ones needed an explicit `columnDefinition`. Fixed on `Profession.id`, `RunObjective.status`/`sequence`/`indent`, `RunParticipant.partyIndex` and its two profession `@JoinColumn`s.
3. **Boolean columns were plain `tinyint`, not `tinyint(1)`**: Liquibase's abstract `BOOLEAN` type on MySQL doesn't round-trip to something mysql-connector-j's `tinyInt1isBit` heuristic recognizes as a boolean — it comes back as a numeric `TINYINT`, not `Types.BIT`, so schema validation failed on `runs.completed` and `run_participants.is_player`/`is_hero`/`is_henchman`. Fixed by changing those four changeset columns from `type="BOOLEAN"` to the explicit `type="TINYINT(1)"` MySQL actually needs.
4. **`SpaFallbackController` never actually booted**: `forwardNested()`'s mapping (`/**/{path:[^.]*}`) mixes `**` with a trailing regex-constrained variable — valid under the legacy `AntPathMatcher`, but Spring Boot 3's default `PathPatternParser` rejects it outright at context startup ("No more pattern data allowed after `{*...}` or `**` pattern element"). Nothing before this session ever booted the full web MVC context far enough to hit it. Fixed by setting `spring.mvc.pathmatch.matching-strategy=ant-path-matcher` in `application.properties` rather than rewriting the controller — preserves the original intended behavior exactly.
5. **`LeaderboardQueryRepository`'s personal-best queries would 500 in production**: both `findPersonalOverallBestMs`/`findPersonalSectionBestMs` cast `rs.getObject(1)` directly to `Long`, but `duration_ms` is `BIGINT UNSIGNED` — mysql-connector-j returns unsigned `BIGINT` values as `java.math.BigInteger`, not `Long` (its range can exceed `Long.MAX_VALUE`), so the cast threw `ClassCastException` on every real call to `GET /api/leaderboards/me/maps/{id}/overall` or `.../sections/{name}`. Fixed with `rs.getLong()` + `wasNull()` instead of a direct cast.

**One infrastructure bug in the test setup itself, worth flagging for anyone adding more integration tests**: `AbstractIntegrationTest`'s singleton MySQL container was originally annotated `@Container` under a class-level `@Testcontainers`. That combination causes JUnit's extension to `stop()` the container in an `@AfterAll`-equivalent hook scoped to whichever test class finishes first — but the field is `static` and shared by every subclass, so the *next* test class's `@BeforeAll` restarted it as a brand-new container on a new port, while Spring's test-context cache kept reusing the *first* class's cached `ApplicationContext`/`HikariPool`, which still pointed at the now-dead old port. Symptom: every test in the second-and-later classes failed with `CannotGetJdbcConnectionException` / `Communications link failure`, each hanging for exactly 30s (Hikari's connection-timeout) before failing. Fixed by dropping `@Container`/`@Testcontainers` entirely and starting the container once in a `static` initializer instead — the documented Testcontainers "singleton container" pattern. Ryuk still reaps it at JVM exit.

**Phases 1–6 (schema, ingestion, auth, characters, leaderboards, run history) are all implemented, integration-tested against real MySQL, and passing.** Phase 7 (deployment) is intentionally still not started.

**Update: a real GWToolboxdll payload sample surfaced a confirmed bug and two new fields.** The original spec's "assume epoch milliseconds for every timestamp field" was wrong for `party.utc_start`/`objective.utc_start` (they're epoch **seconds** — `time(nullptr)`) and wrong in a different way for `objective.instance_start` (it's **not a timestamp at all** — a `std::chrono::steady_clock` millisecond counter with no absolute meaning, confirmed by the user directly). Fixed:
- `UploadRunWriter`: `Instant.ofEpochSecond` (was `ofEpochMilli`) for `party.utc_start`/`objective.utc_start`.
- `runs.instance_start` (`DATETIME(6)`) → `runs.instance_start_ms` (`BIGINT UNSIGNED`), same change in `Run.java` (`Instant instanceStart` → `Long instanceStartMs`) and the frontend's `RunDetail` type. Storing a raw offset instead of attempting a wall-clock conversion that would have silently produced a syntactically valid but meaningless date.
- Also found in the sample: `party_members[]` carries `is_player`/`is_hero`/`is_henchman` (party slots can be AI-controlled), and `objectives[]` carries `indent` (nesting depth). Added columns/entity fields/DTO fields for both, for fidelity. **The user then confirmed real 8-man parties are always all-human players** — the existing party-size-8 validation already guarantees this, so `is_player`/`is_hero`/`is_henchman` don't drive any actual logic, just stored as given.
- Bonus fix enabled by the same sample: `objective.name` (`"The Underworld"`) is the human-readable zone name — `maps.name` now auto-populates from it at ingestion (`GameMapRepository.fillNameIfMissing`, only when currently `NULL`), removing the "admin manually backfills map names" workaround the original spec assumed was necessary.
- All of `specs/backend/00`, `01`, `02` updated to match. `mvn clean test package` passes; the packaged jar now boots all the way up and serves real requests against real MySQL (see above).

**Still open / worth doing next:**
1. **Concurrent-upload race on `MapDedupLock`** — reasoned through carefully (see Phase 2 notes) and exercised sequentially by the integration suite (resend-within-window dedups, resend-outside-window creates a second run), but never exercised under genuine concurrent load. Would need a dedicated multi-threaded test to fully trust.
2. **`role_objectives` is still empty in the real dev DB** — the integration tests seed their own rows directly to exercise the role-gating logic, but nobody's given the actual per-map role↔objective data yet (flagged back when spec 05 was written), so every section personal-best will 204 against real data until that's seeded.
3. Phase 7 (deployment) — Dockerfile, `application-prod.properties` — still not started.

## Phase 1 — Foundation (spec 01) — DONE, verified against real MySQL

- [x] `pom.xml`: added `spring-boot-starter-data-jpa`, `liquibase-core`, `spring-security-crypto`, `spring-boot-starter-test`; removed `spring-boot-starter-jdbc` (superseded by data-jpa)
- [x] Deleted old placeholder root-level `db/changelog/`
- [x] Liquibase changelog under `src/main/resources/db/changelog/`, changesets 001–009, all present and XML-valid:
  - [x] 001 `professions` (+ seed 10 rows)
  - [x] 002 `people`
  - [x] 003 `characters`
  - [x] 004 `machine_keys`
  - [x] 005 `maps`
  - [x] 006 `runs`
  - [x] 007 `run_objectives`
  - [x] 008 `run_participants`
  - [x] 009 `role_objectives`
- [x] `application.properties`: `spring.liquibase.change-log`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, session cookie config (pulled forward from spec 03 since it's a one-line addition)
- [x] `Makefile`: `CHANGELOG` path updated to `src/main/resources/db/changelog/...`
- [x] JPA entities in `com.howl.uwtracker.domain`: `Profession`, `Person`, `PlayerCharacter`, `MachineKey`, `GameMap`, `RoleObjective`/`RoleObjectiveId`, `Run`, `RunObjective`, `RunParticipant`
- [x] Spring Data repositories in `com.howl.uwtracker.repository` for each entity (`RunRepository` includes the native dedup-lookup query from spec 02 — JPQL's `function()` escape doesn't survive `TIMESTAMPDIFF`'s unit-keyword argument reliably, so that one's a native query)
- [x] `mvn compile` — clean
- [x] Changelog structural validation via `liquibase validate` in offline mode — all changesets parse and resolve correctly (got through changelog parsing into offline-mode bookkeeping, which is as far as offline mode goes without a bootstrap snapshot file)
- [x] **Verified against a real MySQL container**: `make db-up` equivalent (docker-compose's `mysql:8.4`) + packaged jar boots clean, all 10 changesets apply, Hibernate schema validation passes. Two real changelog/entity mismatches found and fixed this session — see "Status as of this session" above (`machine_keys.key_hash`, the narrow-`UNSIGNED`-int columns, and the `BOOLEAN`→`TINYINT(1)` fix).

## Phase 2 — Ingestion: `POST /upload-run` (spec 02) — DONE, verified against real MySQL

- [x] Removed `JsonController` stub, replaced by `UploadRunController`
- [x] Request DTOs (`com.howl.uwtracker.ingestion.dto`): `UploadRunRequest`, `PartyDto`, `PartyMemberDto`, `ObjectiveSectionDto`, `ObjectiveDto`, `UploadRunResponse` — rely on `spring.jackson.property-naming-strategy=SNAKE_CASE` (added to `application.properties`) rather than per-field `@JsonProperty`
- [x] Machine-key auth: `MachineKeyHasher` (SHA-256, `com.howl.uwtracker.web` — shared with spec 03's key generation later) + lookup in `UploadRunService.authenticate()`
- [x] `SentinelMapper` (`4294967295` → `null`) — applied only to `objectives[].start/done/duration` and top-level `objective.duration`, per spec 02's precise field list (NOT `instance_start`/`objective.utc_start`, which the spec doesn't list)
- [x] Completion derivation in `UploadRunWriter.createRun()` (last objective `status == 2`)
- [x] Dedup: `RunRepository.findDedupMatch` (native query — `TIMESTAMPDIFF`'s unit keyword doesn't survive JPQL's `function()` escape) + `MapDedupLock` (per-map named lock)
  - **Correctness note worth a second pair of eyes**: `MapDedupLock` deliberately holds its own JDBC `Connection` across the whole locked call (including the transactional writer's commit) rather than doing `GET_LOCK`/`RELEASE_LOCK` through the connection pool inside the same `@Transactional` method — releasing before commit would let a second thread's dedup lookup run against not-yet-visible data. Reasoned through carefully but **not verified against real concurrent load** (no DB here to test against).
  - `UploadRunWriter` is a separate bean from `UploadRunService` specifically so `@Transactional` actually applies — self-invocation within one class bypasses Spring's proxy.
- [x] `RoleDerivation` — pure function, `com.howl.uwtracker.ingestion`, unit tested
- [x] Participant upsert in `UploadRunWriter.attachParticipants()` — find-by-`(run_id, raw_name)` then update-or-insert (JPA dirty-checking handles the update on the managed entity; no explicit `ON DUPLICATE KEY UPDATE` needed since we look the row up first)
- [x] Controller (`UploadRunController`) + `ApiException`/`ApiExceptionHandler`/`ApiErrorResponse` (`com.howl.uwtracker.web`) for the `{error, details}` shape from spec 00 — shared infra, will be reused by every later phase
- [x] Unit tests: 11 cases in `RoleDerivationTest`, all passing (all 8 roles incl. both `spiker`/`sos` combo variants, unresolved-combo → null not an exception, wrong party size → exception, and an explicit check that the *reversed* T4 combo does NOT match — guards the ordered-vs-unordered assumption)
- [x] `mvn compile` / `mvn test` / `mvn package` all clean
- [x] **`UploadRunIntegrationTest`** (13 cases, real MySQL via Testcontainers): full valid-party round trip (roles, objectives, participants all persisted correctly), `utc_start`/`objective.utc_start` confirmed interpreted as epoch seconds (not millis), `instance_start_ms` confirmed stored as a raw offset, sentinel→null mapping confirmed per-field-independent, map-name auto-backfill from `objective.name`, dedup-within-window upserts instead of duplicating, resend-outside-window creates a second run, party-size/machine-key/profession-id validation all rejected correctly
- [x] Dedup and participant upsert now proven end to end (sequential, not concurrent — see "Still open" above for the concurrency caveat)

## Phase 3 — Auth (spec 03) — DONE, verified against real MySQL

- [x] `POST /api/signup`, `POST /api/login`, `POST /api/logout`, `GET /api/account/me` (`com.howl.uwtracker.auth.AuthController` + `AuthService`)
- [x] `BCryptPasswordEncoder` bean (`PasswordEncoderConfig`) — `spring-security-crypto` only, not the full framework
- [x] `SessionAuthInterceptor` registered on `/api/**` excluding `/api/signup`, `/api/login` (`WebMvcConfig`, `com.howl.uwtracker.web`)
- [x] `CurrentPersonId` annotation + `CurrentPersonIdArgumentResolver` — controllers declare `@CurrentPersonId Long personId` instead of re-parsing `HttpSession` each time (per spec 03's suggested pattern)
- [x] Cookie config already in `application.properties` from Phase 1
- [x] Machine-key self-service: `MachineKeyController`/`MachineKeyService` — generate (reveals raw key once), list, revoke (403 if not owned, 404 if missing)
- [x] Bonus, not originally scoped to this phase but small and directly related: `SpaFallbackController` (`com.howl.uwtracker.web`) implementing spec 00's "forward non-`/api`, non-static, non-`/upload-run` requests to `index.html`" piece — untestable until the frontend exists, but the routing logic itself doesn't depend on that
- [x] `mvn test` clean
- [x] **`AuthIntegrationTest`** (8 cases) + **`MachineKeyIntegrationTest`** (5 cases), real MySQL: signup/login/logout/`me` session round trip via `MockMvc` + a real `MockHttpSession`, duplicate-username/short-password/wrong-password rejections (and confirmed login doesn't leak *which* field was wrong), every protected endpoint 401s with no session, machine-key generate/list/revoke including ownership checks (403 not-yours, 404 missing). `CurrentPersonIdArgumentResolver` and `WebMvcConfig`'s path-pattern exclusions confirmed working end to end.

## Phase 4 — Characters (spec 04) — DONE, verified against real MySQL

- [x] `GET /api/characters`, `POST /api/characters`, `DELETE /api/characters/{id}` (`CharacterController`/`CharacterService`)
- [x] Retroactive `character_id` backfill on creation (`RunParticipantRepository.backfillCharacter`, JPQL bulk update)
- [x] `com.howl.uwtracker.common.Roles` — the 8 role codes as a shared constant (used here for `default_role` validation, and will be reused by run-history filtering)
- [x] **`CharacterIntegrationTest`** (8 cases, real MySQL): add/list/remove, duplicate-name conflict, invalid-role rejection, ownership checks, and the retroactive backfill confirmed against a `run_participants` row seeded *before* the character existed (checked via the raw `character_id` column, not by navigating the lazy `RunParticipant.character` association from outside a transaction — that was the one test-authoring mistake this phase's suite had, now fixed)

## Phase 5 — Leaderboards (spec 05) — DONE, verified against real MySQL

- [x] `GET /api/leaderboards/maps/{mapId}/overall`, `.../sections/{objectiveName}`, `/me/maps/{mapId}/overall`, `/me/maps/{mapId}/sections/{objectiveName}` (role-gated via `role_objectives` — `LeaderboardQueryRepository` native queries matching spec 05's SQL exactly)
- [x] `?limit=` support (default 10) — switched `RunRepository`/`RunObjectiveRepository` from fixed `findTop10...` methods to `Pageable`-accepting ones
- [x] Bonus, small and directly needed by the frontend's map picker (spec 00's "Reference data" section, not originally scoped to this phase): `GET /api/maps` (`com.howl.uwtracker.maps`)

**Bug found and fixed while building this phase (previous session)**: `spring.jpa.open-in-view=false` (a deliberate choice from spec 01, avoiding the OSIV anti-pattern) means the Hibernate session closes as soon as each repository call returns. `LeaderboardService.overall()`/`section()` build DTOs that touch lazy associations (`RunParticipant.character`, `RunObjective.run`) — if that mapping happens *after* the repository call returns rather than inside an open transaction, it throws `LazyInitializationException`. Fixed by adding `@Transactional(readOnly = true)` to those service methods so the repository calls *and* the DTO mapping share one session. Applied the same pattern to `CharacterService`.

**Second bug found this session, in the personal-best queries specifically** — see `LeaderboardQueryRepository`'s `ClassCastException` fix in "Status as of this session" above.

- [x] **`LeaderboardIntegrationTest`** (5 cases, real MySQL): overall board fastest-first + `?limit=` + completed-only filtering, section board fastest-per-objective, personal overall best (min across every linked character, completed-only), personal best 204 when none exist, and role-gating confirmed with a case specifically designed to prove a *faster* time in the wrong role does NOT count — this is the suite that caught the `LeaderboardQueryRepository` bug.

## Phase 6 — Run history (spec 06) — DONE, verified against real MySQL

- [x] `GET /api/runs` — filterable (person/character/role/map/from/to/completed, all combinable), paginated (`RunHistoryService.search`, `RunSpecifications` building predicates only for supplied filters, person/character/role combined into one `EXISTS` subquery against `run_participants`)
- [x] `GET /api/runs/{id}` — full detail (objectives in sequence order, participants in party-index order)
- [x] `PageResponse<T>` (`com.howl.uwtracker.web`) — generic pagination envelope from spec 00, reusable by any future paginated endpoint
- [x] Both service methods `@Transactional(readOnly = true)` from the start (lesson from the Phase 5 bug applied immediately here rather than found the hard way again)
- [x] **`RunHistoryIntegrationTest`** (7 cases, real MySQL): every filter individually and combined, pagination envelope shape, run detail's nested objectives (sequence order) and participants (party-index order) — this is the other place the Phase 5-style lazy-loading bug could have hidden, and it didn't (both service methods were already correctly `@Transactional`)

## Phase 7 — Deployment (spec 07)

- [ ] Multi-stage `Dockerfile`
- [ ] `application-prod.properties` (Cloud SQL socket URL, secure cookie)
- The app now runs locally end-to-end (this session's whole point), so the original blocker for starting this phase is gone — just not picked up yet.

## Testing

- `make test-backend` — `mvn test`, self-contained (Testcontainers starts its own MySQL, no `db-up` needed). ~60 tests, all passing.
- `make test-frontend` — brings up `db-up` + the packaged/dev backend on `:8080`, then runs the frontend's Playwright suite against it. See `FRONTEND_IMPLEMENTATION_PROGRESS.md`.
- Maven and Docker weren't on `PATH` in this working environment despite being installed — Maven was found bundled with the IntelliJ install (`...\plugins\maven-plugin\lib\maven3\bin`), and Docker Desktop needed a permissions fix outside this session's control. Worth checking your own shell's `PATH` if `mvn`/`docker` come back "not found" despite `where`/an installer suggesting otherwise.
- Testcontainers' Docker client (this session used 1.21.3, then bumped to 1.21.4 via the `testcontainers.version` property in `pom.xml`) had a version-negotiation incompatibility with a very new Docker Desktop/Engine on Windows npipe — 1.21.3 hardcoded an old default API version and got rejected outright ("client version 1.32 is too old"). The bump fixed it; if this recurs, check `~/.testcontainers.properties` for a stale cached client strategy and delete it before retrying.

## Notes / blockers encountered

- (see "Status as of this session" above for the five real bugs this session's integration tests found and fixed)
