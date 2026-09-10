# Implementation Spec — dungeons, backend + frontend (`com.howl.uwtracker`, `frontend/`)

Companion to the shared contract [`dungeons.md`](dungeons.md). Plugin side:
`GWToolboxpp/plugins/SCTracker/DUNGEONS.md`.

**Status: implemented** on branch `feat/dungeons`. `mvn test-compile` + frontend `tsc --noEmit`
pass; the integration tests need Docker (Testcontainers) and haven't been run here. The files below
are the source of truth — where this doc and a file disagree, the file wins:

| File | Change |
|---|---|
| `db/changelog/changes/055-seed-dungeons.xml` (+ master include) | **new** — 18 dungeon `maps` rows + `(map_id, 1..8, NULL)` via one `<sql>` cross-join |
| `ingestion/UploadRunService.java` | `REGISTRATION_FLOOR_EXEMPT_MAP_IDS` (`{474,34}`) → `REGISTRATION_FLOOR_ENFORCED_MAP_IDS = Set.of(72)`; the `if` inverted; Javadoc updated |
| `AbstractIntegrationTest.java` | `CATHEDRAL_OF_FLAMES_MAP_ID/_NAME` constants + `seedDungeons()` (18 maps × sizes 1–8) |
| `ingestion/DungeonUploadIntegrationTest.java` | **new** — clear / abandoned / low-man(3) / size-9-rejected / unseeded-id / no-registered / dedup |
| `maps/MapIntegrationTest.java` | `includesEveryDungeonConfigOnceSeeded` — 8 role-less configs per dungeon |
| `scripts/seed-dungeon-runs.mjs` | **new** |
| `frontend/src/common/maps.ts` | 18 `MAPS` entries (`partySizes: 1..8`, `defaultSize: 8`, `group: 'Dungeon'`) + `ROLE_MODEL` `<id>:<1..8>` entries + `group?` field |
| `frontend/src/components/MapSizePicker.tsx` | `<optgroup>` rendering by `group` |
| `frontend/src/pages/RunDetail.tsx` | `showProfessions` guard — hides the Professions column for a (dungeon, `party_size === 2`) run |

**TL;DR:** one Liquibase changeset, one small `UploadRunService` edit, one
`frontend/src/common/maps.ts` edit (+ `MapSizePicker` optgroups), plus tests and a seed script.
Everything else is a deliberate **no-op** — the board queries are already `partySize`-parameterised
from the FoW 1–8 work.

The whole feature rides on the FoW/DoA machinery from `fow-and-party-size.md` (`map_configs`,
`RoleModel` incl. its `null` case, `runs.party_size`, `MapSizePicker`, name-mode voting). If any of
that is unfamiliar, read that spec's §2–§4 and §11 first.

---

## 1. What a dungeon looks like to the backend

A single `(map_id, party_size) = (<entryMapId>, 8)` with `role_model = NULL` — **byte-for-byte the
Domain of Anguish shape** (`051-seed-domain-of-anguish.xml`). The upload payload:

```jsonc
{
  "party": {
    "utc_start": 1789000000,
    "map_id": 560,                 // Cathedral of Flames ENTRY level — never a deeper level
    "character_name": "...",
    "end_reason": "completed",     // metadata only; never consulted for `completed`
    "party_members": [ /* 8 entries, all is_player:true, role_hint absent/"unknown" */ ]
  },
  "objective": {
    "name": "Cathedral of Flames", // entry-level map name -> maps.name auto-populate
    "instance_start": 12345678,
    "utc_start": 1789000001,
    "objectives": [
      { "name": "Level 1", "status": 2, "start": ..., "done": ..., "indent": 0, "duration": ... },
      { "name": "Level 2", "status": 2, ... },
      { "name": "Level 3", "status": 2, ... }
    ],
    "duration": 1830000
  }
}
```

- `objectives[].name` is `"Level N"`, one per dungeon level (from GWToolboxdll's
  `AddDungeonObjectiveSet`). They collide across dungeons by string but every query is keyed
  `(map_id, objective_name)`, so it's fine (`dungeons.md` §4.2).
- `completed` derives from `objectives[last].status == 2` (`specs/backend/02` step 6), unchanged.
  GWToolboxdll sets the last `"Level N"` objective `Completed` on `EventType::DungeonReward`.
- `role` for every participant is `NULL` (role-less config).

---

## 2. Data model — Liquibase

### `055-seed-dungeons.xml` — **written**

`src/main/resources/db/changelog/changes/055-seed-dungeons.xml` + its include in
`db.changelog-master.xml` (after `054`). 18 `maps` `<insert>` rows (need the names) + one `<sql>`
block that cross-joins the 18 ids × sizes `1..8` into 144 `map_configs` rows, all `role_model`
NULL, with a matching `<rollback>`. The **18 dungeons**:

| id | name | id | name |
|---|---|---|---|
| 560 | Cathedral of Flames | 617 | Raven's Point |
| 570 | Catacombs of Kathandrax | 623 | Slavers' Exile |
| 573 | Rragar's Menagerie | 628 | Sepulchre of Dragrimmar |
| 576 | Ooze Pit | 630 | Frostmaw's Burrows |
| 578 | Oola's Lab | 635 | Darkrime Delves |
| 581 | Shards of Orr | 701 | Secret Lair of the Snowmen |
| 584 | Arachni's Haunt | 704 | Fronis Irontoe's Lair |
| 604 | Vloxen Excavations | | |
| 607 | Heart of the Shiverpeaks | | |
| 612 | Bloodstone Caves | | |
| 615 | Bogroot Growths | | |

`maps.name` uses the canonical Guild Wars area names — worth an in-game check against
`Resources::GetMapName(Level_1)` (a mismatch is cosmetic; ingestion won't overwrite a non-null
name). A wrong `map_id` is worse: a dead `map_configs` row, every upload 400s.

### Registration-character floor — **done (Option B)**

`UploadRunService` now inverts the exempt-list to an enforced-list:

```java
// was: REGISTRATION_FLOOR_EXEMPT_MAP_IDS = Set.of(474, 34);  if (!EXEMPT.contains(mapId)) { floor }
private static final Set<Integer> REGISTRATION_FLOOR_ENFORCED_MAP_IDS = Set.of(72);
...
if (REGISTRATION_FLOOR_ENFORCED_MAP_IDS.contains(party.mapId())) { /* floor check, unchanged */ }
```

The Underworld is now the only map that enforces the floor; every future seeded map is exempt by
default. `minRegisteredFor` stays (still called by `AdminRunService`'s map-generic retroactive-wipe
bar). No other code referenced the old constant (only comments mentioning `minRegisteredFor`, which
still exists). `FowDuoUploadIntegrationTest` / `UploadRunIntegrationTest` didn't reference the
constant directly — no test edits needed there.

---

## 3. Backend code — the no-op inventory

Each of these is confirmed to need **no change**. Listed so a reviewer can check them off.

| Component | Why it already works |
|---|---|
| `UploadRunService.processUpload` | `mapConfigRepository.findById(new MapConfigId(560, 8))` resolves once `055` is applied. `config.getRoleModel()` is `null` → `RoleDerivation.resolveRoles(members, null)` returns an all-`null` list. `maybeClaimUploaderCharacter` + floor: see §2. |
| `RoleDerivation.resolveRoles(members, null)` | Already returns `Collections.nCopies(members.size(), null)` — the FoW-8 / DoA path. `restrictHintsToSelf` runs first, harmless. |
| `UploadRunWriter.ingest` / `.createRun` / `.findDedupMatch` | `run.setPartySize(members.size())` → 8. `inferRemainingTrapperRoleByElimination` is gated on `RoleModel.TRAPPER`, so it no-ops. Dedup lock is per-`map_id`; the exact-roster check separates two parties in the same dungeon within the 60s window. The entry-level `utc_start` (both clients stamp it identically) is the dedup key — a longer multi-level wall-clock run doesn't matter. |
| `MapController.list` / `MapResponse.from` | Iterates `gameMapRepository.findAll()` + `mapConfigRepository.findByIdMapIdOrderByIdPartySizeAsc`. New maps appear with `configs = [{ partySize: 8, roleModel: null }]` automatically. |
| `LeaderboardService` / `LeaderboardController` | Every endpoint already takes optional `partySize`. `requireConcretePartySize(560, null)` → the map's sole config size (8), no 400. `isRoleGated(560, 8)` → `false` (config `role_model` is `NULL`) → personal section bests use the un-gated `findPersonalSectionBest*` path; the global `sectionEntries` shows the full party (`gated == false`). |
| `LoserboardService` / `LoserboardController` | Same `partySize` / role-gating story. `role-deaths` / `role-failure-reasons` filter `WHERE rp.role IS NOT NULL` → empty for dungeons → frontend hides the panels. |
| `RunHistoryService` / `RunSpecifications` | `hasMap` + optional `hasPartySize` already exist; a dungeon run is just `map_id = 560, party_size = 8`. |
| `FailureReportService.submit` / `MvpReportService.submit` | Already accept `roles[]` as either role names *or* character `raw_name`s and resolve at window close against `map_configs.role_model` — the role-less branch is exactly what a dungeon uses (name-mode vote). No party-size gate. The `run.isCompleted()` / `!run.isCompleted()` outcome guards (changeset 054) apply unchanged. |
| `MachineKeyAuthenticationService` / plugin-version gate | Unchanged. Don't raise `plugin.min-version`. |

---

## 4. Completion derivation & the abandoned-dungeon risk

`completed = objectives.length > 0 && objectives[last].status == 2`.

- **Cleared dungeon** → GWToolboxdll marks the last `"Level N"` objective `Completed` on
  `DungeonReward` → `completed = true`. Fine.
- **Abandoned / wiped dungeon** → depends on whether GWToolboxdll writes an `ObjectiveSet` to
  `ObjectiveTimerRuns_*.json` at all for a dungeon left without a reward. `StopObjectives()` (which
  marks unfinished objectives `Failed`) only runs when the *next* `ObjectiveSet` is added, and
  returning to an outpost adds nothing. **Action:** the plugin spec (`DUNGEONS.md` §8) owns
  verifying this in `ObjectiveTimerWindow`. Backend impact: if GWToolboxdll never flushes the
  incomplete set, the plugin's `PartyLog` entry times out of its sync queue and is dropped — the
  backend simply never sees that run. Acceptable; document it in the run-history spec note.
- If it *does* flush with the last objective not `Completed` → `completed = false`, which is
  correct. Cover both in `DungeonUploadIntegrationTest`.

---

## 5. Tests

### `AbstractIntegrationTest`

- `cleanDatabase()` (currently reseeds only Underworld at lines ~170–171) — **also** reseed the
  three dungeon `maps` + `map_configs` rows, OR add a `seedDungeons()` helper (mirroring
  `seedFissureOfWoe()` / `seedDomainOfAnguish()` at ~188/~211) and have the dungeon test call it.
  Prefer the helper — most tests don't need dungeons, same rationale the FoW/DoA helpers use.
- Constants next to `DOMAIN_OF_ANGUISH_MAP_ID` (~95):

  ```java
  public static final int CATHEDRAL_OF_FLAMES_MAP_ID = 560;
  public static final String CATHEDRAL_OF_FLAMES_MAP_NAME = "Cathedral of Flames";
  public static final int CATACOMBS_OF_KATHANDRAX_MAP_ID = 570;
  public static final String CATACOMBS_OF_KATHANDRAX_MAP_NAME = "Catacombs of Kathandrax";
  public static final int FROSTMAWS_BURROWS_MAP_ID = 630;
  public static final String FROSTMAWS_BURROWS_MAP_NAME = "Frostmaw's Burrows";
  ```

  ```java
  protected void seedDungeons() {
      record D(int id, String name) {}
      for (D d : List.of(
              new D(CATHEDRAL_OF_FLAMES_MAP_ID, CATHEDRAL_OF_FLAMES_MAP_NAME),
              new D(CATACOMBS_OF_KATHANDRAX_MAP_ID, CATACOMBS_OF_KATHANDRAX_MAP_NAME),
              new D(FROSTMAWS_BURROWS_MAP_ID, FROSTMAWS_BURROWS_MAP_NAME))) {
          jdbcTemplate.update("INSERT INTO maps (id, name) VALUES (?, ?)", d.id(), d.name());
          jdbcTemplate.update("INSERT INTO map_configs (map_id, party_size, role_model) VALUES (?, 8, NULL)", d.id());
      }
  }
  ```

### `DungeonUploadIntegrationTest` (new)

Mirror `FowDuoUploadIntegrationTest`. Cases:

| Case | Expect |
|---|---|
| 8-man upload, `objectives = ["Level 1","Level 2","Level 3"]`, last `status = 2` | `200`; `runs.party_size = 8`; every `run_participants.role` is `NULL`; `runs.completed = true`; `maps.name` populated from `objective.name` if it was seeded null |
| Same but last `status = 1` (abandoned mid-level) | `200`; `runs.completed = false` |
| Party size 7 (or 3) | `400` "unsupported map/party-size combination: 560/7" |
| Unseeded dungeon id (e.g. 561) | `400` (no `map_configs` row) |
| 0 registered characters | `200` (floor exempt per §2) — assert this explicitly so a regression on the exempt set is caught |
| Two 8-man uploads for the same `(map_id, utc_start, roster)` from different keys | one `runs` row, participants merged (dedup) |

### `MapIntegrationTest`

With `seedDungeons()`, assert `GET /api/maps` returns each dungeon with `configs = [{8, null}]`.

### `LeaderboardIntegrationTest` / `LoserboardIntegrationTest`

- A dungeon `GET /api/leaderboards/me/maps/560/sections/Level%201` returns a real PB via the
  **un-gated** query (not the "no PB for anyone" gap that a role-gated map with unseeded
  `role_objectives` would hit).
- A dungeon run and a UW run don't appear on each other's `overall` / `sections` boards.
- `GET /api/leaderboards/maps/560/overall` with no `partySize` param works (single config).

### `RoleDerivationTest`

Already covers `resolveRoles(list, null)` → all-`null` (FoW-8). No new case strictly needed; add a
dungeon-flavoured fixture if convenient.

### `scripts/seed-dungeon-runs.mjs` (new)

Sibling of `scripts/seed-fow-runs.mjs`. Differences:

- `const DUNGEONS = [{ mapId: 560, route: ['Level 1','Level 2','Level 3'] }, { mapId: 570, route: [...] }, { mapId: 630, route: ['Level 1',...,'Level 5'] }];`
- 8 personas, each with one character (no Ranger/Derv pairing — role-less).
- Each run: `party_members` = 8 players (mixed professions, doesn't matter), `objectives` = that
  dungeon's `route` with `status = 2` for completed runs / last `status = 1` for a couple of
  abandoned ones.
- `PLUGIN_VERSION` default bumped to match the shipped `SCTracker.version.json` `version`.
- Header comment: requires changeset `055` applied.

---

## 6. Frontend (`frontend/`)

### 6.1 `src/common/maps.ts` — the only required change

Add one `MAPS` entry and one `ROLE_MODEL` entry per dungeon:

```ts
export const MAPS: MapChoice[] = [
  { id: '72',  short: 'UW',  name: 'The Underworld',        partySizes: [8] },
  { id: '34',  short: 'FoW', name: 'The Fissure of Woe',    partySizes: [1,2,3,4,5,6,7,8], defaultSize: 2 },
  { id: '474', short: 'DoA', name: 'Domain of Anguish',     partySizes: [8] },
  { id: '560', short: 'CoF', name: 'Cathedral of Flames',       partySizes: [8], group: 'Dungeon' },
  { id: '570', short: 'CoK', name: 'Catacombs of Kathandrax',   partySizes: [8], group: 'Dungeon' },
  { id: '630', short: 'Frostmaw', name: "Frostmaw's Burrows",   partySizes: [8], group: 'Dungeon' },
];

const ROLE_MODEL: Record<string, string | null> = {
  '72:8': 'trapper',
  // …FoW / DoA entries…
  '560:8': null,
  '570:8': null,
  '630:8': null,
};
```

`configHasRoles('560', 8)` → `false` (by-role panels hide), `rolesForConfig` → `[]`,
`mapSupportsGambling` stays `'72'`-only, `defaultPartySize('560')` → `8`. All existing helpers
already handle an unknown id gracefully; the explicit rows just make first paint (pre-`/api/maps`)
correct.

**`group?` field (optional):** add `group?: 'Elite Area' | 'Dungeon'` to the `MapChoice` interface.
If present, `MapSizePicker` renders the map `<select>` with `<optgroup label={group}>`. Skip if the
6-entry flat list still reads fine — this is cosmetic.

### 6.2 No-op inventory (frontend)

| File | Why no change |
|---|---|
| `components/MapSizePicker.tsx` | Renders `MAPS`; single-size map → read-only "8-Man" chip (same as UW/DoA). Only touched if adding `<optgroup>`. |
| `App.tsx` | `/leaderboards/:mapId`, `/loserboards/:mapId` already parametric. |
| `pages/LeaderboardPage.tsx` | `objectiveNames` derived from the fastest run's detail (`firstRunDetailQuery.data.objectives.map(o => o.name)`) → renders `"Level 1".."Level N"` automatically. By-role panels gated on `configHasRoles` → auto-hidden. |
| `pages/LoserboardsPage.tsx` | Same; `Deaths By Role` / `Blamed By Role` auto-hidden. |
| `pages/RunHistory.tsx` | Map `<select>` fed by `MAPS`; `partySize` filter already present; role filter returning nothing for a dungeon is harmless. |
| `pages/RunDetail.tsx` | Shows `map_name` + a `sizeLabel(party_size)` chip; hides the Role column when no participant has a role (already the FoW-8/DoA behaviour). |
| `pages/Dashboard.tsx` | `MapSizePicker` + "view" links `/leaderboards/${mapId}` etc. — new maps appear in the picker with no code change. |
| `api/types.ts` | `GameMap.configs`, `RunSummary.party_size`, `RunDetail.party_size` already exist. |

### 6.3 Optional polish — descriptive section labels

`"Level 1"` is uninformative. Add to `common/maps.ts`:

```ts
// Display-only. Backend objective_name stays "Level N"; this just prettifies the Sections panel.
export const sectionLabel = (mapId: string, objectiveName: string): string => {
  const m = mapById(mapId);
  return m?.group === 'Dungeon' ? `${m.short} · ${objectiveName}` : objectiveName;
};
```

Use it in `LeaderboardPage`'s Sections `<th>`/row rendering. Zero backend impact. Skip for v1 if
time-boxed.

### 6.4 Copy + e2e

- `pages/HowToUse.tsx` / `Dashboard.tsx` — one line: the listed dungeons are tracked automatically
  by the updated plugin (v16+); use the map picker to switch views.
- `frontend/e2e/` — parameterise `run-history-filters.spec.ts` and the leaderboard/loserboard specs
  over `{ map }`; add a dungeon path using `seed-dungeon-runs.mjs` data. Assert the picker switches
  the board and dungeon/UW results don't cross-contaminate. `tsc` + `vite build` must stay clean.

---

## 7. Deploy checklist (backend + frontend)

1. `mvn test` green, incl. new `DungeonUploadIntegrationTest` + updated `MapIntegrationTest`.
2. Deploy backend → `GET /api/maps` lists the three dungeons, each `configs: [{partySize:8, roleModel:null}]`.
3. `curl` an `/upload-run` with a dungeon payload + a valid machine key → `200 {created:true}`;
   a size-7 variant → `400`.
4. Deploy frontend → dungeons selectable in every `MapSizePicker`; boards render empty (no runs
   yet); by-role panels absent; Sections panel shows "no data yet".
5. (After the plugin ships and real runs arrive) spot-check a run-detail page: 8 participants, no
   Role column, `party_size` chip "8-Man", objectives "Level 1..N".

---

## 8. Decisions / remaining

- **`seedDungeons()` is opt-in** (not in `cleanDatabase()`), matching FoW/DoA. Seeds all 18 with a
  placeholder name, then sets Cathedral of Flames' real name (the one tests assert).
- **`<optgroup>` shipped** — `MapChoice.group` + `MapSizePicker` renders `Dungeon` / `Elite Area`
  groups (18 dungeons made the flat list unwieldy).
- **Registration floor** — Option B (invert to UW-only enforced). Done.
- **Section label prettification** (§6.3) — deferred; `"Level N"` renders as-is for now.
- **Still TODO:** run integration tests with Docker up (`mvn test`); a `specs/backend/06-run-history.md`
  note on abandoned dungeons that never reach the backend; the e2e specs in §6.4.
