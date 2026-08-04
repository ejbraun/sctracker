# 01 — Schema & Migrations

MySQL schema, applied via Liquibase. Changelog moves to `src/main/resources/db/changelog/db.changelog-master.xml` (classpath, so `spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml` auto-runs it on boot); the `Makefile`'s `migrate` target updates its `CHANGELOG` path to match, so `make migrate` and the app's own boot-time migration both apply from the same source of truth. The existing `db/changelog/changes/001-create-runs-table.xml` placeholder is deleted.

Changesets are applied in this order (one file per table, numbered):

## 001 — `professions`

Static reference table. 10 rows, fixed GW1 profession IDs — seeded in the same changeset via `<insert>`.

```sql
CREATE TABLE professions (
  id   TINYINT UNSIGNED PRIMARY KEY,
  name VARCHAR(20) NOT NULL,
  UNIQUE KEY uq_professions_name (name)
);
```

Seed data (official GW1 profession IDs):

| id | name |
|---|---|
| 1 | Warrior |
| 2 | Ranger |
| 3 | Monk |
| 4 | Necromancer |
| 5 | Mesmer |
| 6 | Elementalist |
| 7 | Assassin |
| 8 | Ritualist |
| 9 | Paragon |
| 10 | Dervish |

## 002 — `people`

```sql
CREATE TABLE people (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_people_username (username)
);
```

`password_hash` holds a BCrypt hash (~60 chars); 255 is headroom, not a real constraint.

## 003 — `characters`

```sql
CREATE TABLE characters (
  id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  person_id      BIGINT UNSIGNED NOT NULL,
  character_name VARCHAR(64) NOT NULL,
  default_role   VARCHAR(16) NULL,
  created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_characters_name (character_name),
  KEY idx_characters_person (person_id),
  CONSTRAINT fk_characters_person FOREIGN KEY (person_id) REFERENCES people(id) ON DELETE CASCADE
);
```

`character_name` is globally unique (GW1 character names are unique across the whole game, not just per-account). `default_role` is advisory only — one of the 8 role codes (`T1`/`T2`/`T3`/`T4`/`LT`/`spiker`/`sos`/`emo`), validated at the application layer rather than a DB enum/check constraint, so adding a role later doesn't require a migration.

## 004 — `machine_keys`

```sql
CREATE TABLE machine_keys (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  person_id   BIGINT UNSIGNED NOT NULL,
  key_hash    CHAR(64) NOT NULL,
  label       VARCHAR(64) NULL,
  created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  revoked_at  DATETIME(6) NULL,
  UNIQUE KEY uq_machine_keys_hash (key_hash),
  KEY idx_machine_keys_person (person_id),
  CONSTRAINT fk_machine_keys_person FOREIGN KEY (person_id) REFERENCES people(id) ON DELETE CASCADE
);
```

`key_hash` is the hex-encoded SHA-256 digest of the raw key (**not** BCrypt — this is a high-entropy generated secret, not a user-chosen password, so a fast deterministic hash is appropriate and lets auth do a plain indexed lookup instead of an O(n) BCrypt comparison loop). The raw key is shown to the user exactly once at generation time (spec 03) and never stored.

## 005 — `maps`

```sql
CREATE TABLE maps (
  id         INT UNSIGNED PRIMARY KEY,
  name       VARCHAR(128) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
```

`id` is **not** auto-increment — it's the GW1 client's own numeric `map_id`. No seed data: rows are upserted lazily by the ingestion path the first time a given `map_id` is seen (`name` starts `NULL`, backfilled manually/by an admin tool later). This avoids hand-maintaining a full GW1 zone-ID table that isn't otherwise needed.

## 006 — `runs`

```sql
CREATE TABLE runs (
  id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  map_id             INT UNSIGNED NOT NULL,
  utc_start          DATETIME(6) NOT NULL,
  instance_start_ms  BIGINT UNSIGNED NULL,
  objective_start    DATETIME(6) NULL,
  end_reason         VARCHAR(16) NOT NULL,
  completed          BOOLEAN NOT NULL,
  duration_ms        BIGINT UNSIGNED NULL,
  created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_runs_map_start (map_id, utc_start),
  KEY idx_runs_map_completed (map_id, completed),
  CONSTRAINT fk_runs_map FOREIGN KEY (map_id) REFERENCES maps(id)
);
```

- `utc_start` = `party.utc_start` from the payload (real wall-clock epoch seconds, confirmed against a real GWToolboxdll sample — see spec 00/02) — this is the dedup key (spec 02).
- `instance_start_ms` = `objective.instance_start` — **not a timestamp**, despite the original draft's assumption. Confirmed to be a `std::chrono::steady_clock`-based millisecond counter with no absolute meaning (zeroed at an arbitrary point tied to system boot, not comparable across runs/machines) — stored as a raw `BIGINT`, not `DATETIME`, precisely because attempting a wall-clock conversion would produce a syntactically valid but meaningless date. `objective_start` = `objective.utc_start` (real wall-clock, same as `utc_start`) — kept distinct for fidelity even though they may usually coincide.
- `end_reason` is one of `wipe` / `resign` / `unknown` (app-validated, not a DB enum, same reasoning as `default_role`).
- `completed` is **derived at ingestion time** (last objective's `status == 2`), stored rather than computed on every read since it's read far more often than written and drives both the dedup response and every leaderboard/history query.
- `idx_runs_map_completed` backs "best full-run time per map" queries (spec 05); consider extending to a covering index `(map_id, completed, duration_ms)` if that query shows up in slow-query logs — not added preemptively.

## 007 — `run_objectives`

```sql
CREATE TABLE run_objectives (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  run_id       BIGINT UNSIGNED NOT NULL,
  sequence     SMALLINT UNSIGNED NOT NULL,
  name         VARCHAR(128) NOT NULL,
  status       TINYINT UNSIGNED NOT NULL,
  start_ms     BIGINT UNSIGNED NULL,
  done_ms      BIGINT UNSIGNED NULL,
  duration_ms  BIGINT UNSIGNED NULL,
  indent       TINYINT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uq_run_objectives_run_seq (run_id, sequence),
  KEY idx_run_objectives_run_name (run_id, name),
  CONSTRAINT fk_run_objectives_run FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE
);
```

`sequence` is the 0-based index in the payload's `objectives[]` array — preserves order, which is what determines "last objective" for completion derivation, and what section-level leaderboard/personal-best queries group on (joined with `name` across a person's runs for the same map). `start_ms`/`done_ms`/`duration_ms` are milliseconds *relative to* `runs.instance_start_ms`, not absolute — see spec 00/02. `indent` (nesting depth, found in a real payload sample, always `0` so far) is stored for fidelity; nothing uses it yet.

## 008 — `run_participants`

```sql
CREATE TABLE run_participants (
  id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  run_id                   BIGINT UNSIGNED NOT NULL,
  character_id             BIGINT UNSIGNED NULL,
  raw_name                 VARCHAR(64) NOT NULL,
  primary_profession_id    TINYINT UNSIGNED NOT NULL,
  secondary_profession_id  TINYINT UNSIGNED NULL,
  role                     VARCHAR(16) NULL,
  party_index              TINYINT UNSIGNED NOT NULL,
  is_player                BOOLEAN NOT NULL,
  is_hero                  BOOLEAN NOT NULL,
  is_henchman              BOOLEAN NOT NULL,
  created_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_run_participants_run_name (run_id, raw_name),
  KEY idx_run_participants_character (character_id),
  KEY idx_run_participants_role (role),
  KEY idx_run_participants_raw_name (raw_name),
  CONSTRAINT fk_run_participants_run FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE,
  CONSTRAINT fk_run_participants_character FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE SET NULL,
  CONSTRAINT fk_run_participants_primary_prof FOREIGN KEY (primary_profession_id) REFERENCES professions(id),
  CONSTRAINT fk_run_participants_secondary_prof FOREIGN KEY (secondary_profession_id) REFERENCES professions(id)
);
```

- `character_id` is resolved by matching `raw_name` against `characters.character_name` — for *any* of the 8 party members, not just the uploader, since character names are globally unique. `ON DELETE SET NULL` (not `CASCADE`): removing a character (spec 04) unlinks it from historical runs without deleting the run history itself.
- `idx_run_participants_raw_name` backs the retroactive backfill in spec 04 (when a character is added after runs already exist under that raw name) and general history lookups by name.
- `party_index` (0–7) is the raw array position — kept even though `role` is precomputed, so roles can be recomputed later without re-ingesting if the derivation algorithm changes.
- `(run_id, raw_name)` uniqueness is what makes participant attachment idempotent on resend (spec 02).
- `is_player`/`is_hero`/`is_henchman` — found in a real payload sample, not in the original spec draft: party slots can be AI-controlled heroes/henchmen. **Resolved**: real guild 8-man parties are always all human players — the existing party-size-8 validation (spec 02) already guarantees this, so these columns are stored for fidelity only and don't drive any role-derivation, validation, or eligibility logic. (Smaller parties, like solo/duo runs with hero/henchman fill-ins, are rejected by the party-size-8 rule before these fields would ever matter.)

## 009 — `role_objectives`

```sql
CREATE TABLE role_objectives (
  map_id          INT UNSIGNED NOT NULL,
  objective_name  VARCHAR(128) NOT NULL,
  role            VARCHAR(16) NOT NULL,
  PRIMARY KEY (map_id, objective_name, role),
  CONSTRAINT fk_role_objectives_map FOREIGN KEY (map_id) REFERENCES maps(id)
);
```

Static mapping of which roles are actually "involved" in a given objective, per map — e.g. if `spiker` never touches the `Escort` objective on a given map, there should be no `(map_id, 'Escort', 'spiker')` row, and a spiker's `Escort` time should never count toward their personal best for it (spec 05). Composite PK, no surrogate id — this is pure association data, not an entity with its own identity or lifecycle.

**Not populated by ingestion** — unlike `maps` (upserted lazily as new map ids are seen), this table is deliberately *not* auto-derived from uploaded runs. It's maintained separately: seeded via a Liquibase `<insert>` changeset once the actual role/objective associations per map/dungeon are supplied, or a future admin tool. **This spec doesn't define the seed data itself** — that's GW1 dungeon-mechanics knowledge that has to come from the guild, not something inferable from the upload payload. Until a given `(map_id, objective_name)` pair has at least one row here, personal-best queries for that objective will return "no PB" for everyone, even people who've legitimately done it — see the operational note in spec 05.

## JPA entities

One entity per table, in `com.howl.uwtracker.domain`, with matching `com.howl.uwtracker.repository.*Repository extends JpaRepository<Entity, Id>`:

| Table | Entity class | Notes |
|---|---|---|
| `professions` | `Profession` | |
| `people` | `Person` | |
| `characters` | `PlayerCharacter` | Named to avoid shadowing `java.lang.Character` |
| `machine_keys` | `MachineKey` | |
| `maps` | `GameMap` | Named to avoid shadowing `java.util.Map`; `@Id` is not generated |
| `role_objectives` | `RoleObjective` | Composite key (`map_id`, `objective_name`, `role`) via `@EmbeddedId` — no surrogate id |
| `runs` | `Run` | |
| `run_objectives` | `RunObjective` | |
| `run_participants` | `RunParticipant` | |

`RunHistoryRepository` (or an extension on `RunRepository`) additionally implements `JpaSpecificationExecutor<Run>` for the dynamic filters in spec 06.

Hibernate should run with `spring.jpa.hibernate.ddl-auto=validate` — Liquibase owns the schema, Hibernate only verifies entity mappings match it at boot. `spring.jpa.open-in-view=false`.
