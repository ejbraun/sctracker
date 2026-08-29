# Feature Spec — Fissure of Woe support + party-size as a first-class dimension

Status: draft / plan. Spans **plugin** (`GWToolboxpp/plugins/SCTracker`), **backend**
(`com.howl.uwtracker`), and **frontend** (`frontend/`). Builds on `specs/backend/*` and
`specs/frontend/*`; where this contradicts an assumption baked into those (e.g. "party size is
always 8", "maps is currently just Underworld"), this spec wins.

---

## 1. Goal & scope

Add **The Fissure of Woe (FoW)** as a second supported map, and make **party size** a selectable
dimension alongside map on Run History, Leaderboards, and Loserboards.

### In scope (v1)

- FoW map id **34**, seeded like Underworld (72).
- FoW v1 is a **2-person party, no heroes/henchmen** ("duo"). `party_members` has exactly 2 entries.
- Allowed party size is per **(map, size)** config, not per map — because FoW *8-man* (a later
  phase) has its own, more complex role model. v1 configs: `(UW, 8)` and `(FoW, 2)`. Validation,
  plugin publish-gating, and the registered-character floor all key off this config.
- `runs.party_size` stored column; every leaderboard / loserboard / history query gains an optional
  `partySize` filter.
- A **FoW-duo role model** distinct from UW's: for a FoW duo the role *is* the primary profession —
  Ranger-primary → `Ranger`, Dervish-primary → `Derv` (reuses the existing `Derv` code). Selected
  by the `(map, size)` config's `role_model` (`trapper` for `(UW,8)`, `primary_profession` for
  `(FoW,2)`). `role_objectives` for FoW-duo roles is seeded (all 11 objectives × both roles — a duo
  is present for everything).
- Frontend: one shared **map + party-size picker** used by Dashboard, Leaderboards, Loserboards,
  Run History. Route normalisation so Loserboards is `/loserboards/:mapId` like Leaderboards.

### Out of scope (v1) — see §8

- **FoW 8-man** (explicitly a later phase — different, more complex role model), UW duo, or any
  other map/size combo without a `map_configs` row.
- Per-objective role involvement finer than "both FoW-duo roles do every objective" (see §8).
- FoW-specific novelty boards (Gamblers Anonymous, Luckiest Players): the plugin does not collect
  gambling-stone / ecto data outside UW, so these panels are simply empty for FoW — hidden in the
  UI rather than removed (see §6.5).

### Established facts (decided, do not re-litigate)

| Fact | Value | Source |
|---|---|---|
| FoW `map_id` | `34` | `GWCA/Constants/Maps.h` — `MapID` enum, `None=0`, sequential; `The_Fissure_of_Woe` is the 34th; `The_Underworld` is 72 ✓ against the real payload sample |
| FoW zone name | `The Fissure of Woe` | plugin payload `objective.name` |
| FoW objective names (route order) | `ToC`, `Wailing Lord`, `Griffons`, `Defend`, `Forge`, `Menzies`, `Restore`, `Khobay`, `ToS`, `Burning Forest`, `The Hunt` | plugin payload (GWToolboxdll ObjectiveTimer FoW ObjectiveSet) |
| Duo roster shape | exactly 2 `party_members`, both `is_player=true`, no AI | user decision |
| "Party size" definition | `party_members.length` (roster length) | user decision |
| Party-size selector UX | fixed options per map (UW→{8}, FoW→{2}); selector only shown when a map has >1 configured size | user decision |
| FoW-duo role model | `role` = primary profession: Ranger→`Ranger`, Dervish→`Derv`. **Simple rule is duo-only**; FoW 8-man will be more complex (later phase). | user decision |
| Registered-character floor | 50% of roster, `ceil(size/2)` → UW 4, FoW-duo **1** | user decision |
| Real-time completion latch for FoW | not needed — ProcessSync `IsRunCompleted` fallback is sufficient | user decision |
| GWToolboxdll still emits a FoW `ObjectiveSet` | yes (confirmed) | user decision |

---

## 2. Data model changes (Liquibase)

New changesets appended to `db.changelog-master.xml`, continuing the existing numbering
(latest is `035`).

### 036 — `add-runs-party-size`

```sql
ALTER TABLE runs ADD COLUMN party_size TINYINT UNSIGNED NOT NULL DEFAULT 8;   -- default only for the backfill
```

- Backfill existing rows from the true roster count, then drop the default so it's always written
  explicitly by ingestion:

```sql
UPDATE runs r
SET party_size = (SELECT COUNT(*) FROM run_participants rp WHERE rp.run_id = r.id);
ALTER TABLE runs ALTER COLUMN party_size DROP DEFAULT;
```

- Add covering-ish index for the ranked queries:
  `KEY idx_runs_map_size_completed (map_id, party_size, completed)` — supersedes
  `idx_runs_map_completed` for the overall board; keep both or replace, decide against slow-query
  logs (same posture spec 01 takes).

### 037 — `create-map-configs`

A map supports one or more **(party size, role model)** configurations. This is a table, not a
`maps` column, precisely because FoW will later support both a 2-man and an 8-man config with
*different* role models.

```sql
CREATE TABLE map_configs (
  map_id      INT UNSIGNED    NOT NULL,
  party_size  TINYINT UNSIGNED NOT NULL,
  role_model  VARCHAR(24)     NULL,          -- NULL = this config has no role model (roles stay NULL, me/sections not gated)
  PRIMARY KEY (map_id, party_size),
  CONSTRAINT fk_map_configs_map FOREIGN KEY (map_id) REFERENCES maps(id)
);
```

Seed the existing map in the same changeset:

```xml
<insert tableName="map_configs"><column name="map_id" valueNumeric="72"/><column name="party_size" valueNumeric="8"/><column name="role_model" value="trapper"/></insert>
```

- **Allowed party sizes for a map** = the `party_size` values present here for it.
- **Role model for a run** = `map_configs.role_model` for `(run.map_id, run.party_size)`.
- `role_model` is app-validated (`RoleModel` enum below), not a DB enum — same posture as
  `end_reason` / `default_role` in spec 01.
- A map with **no** `map_configs` row = unsupported (ingestion rejects, same as an unseeded map id
  today). This replaces the "curated `maps` set" gate with a "curated `(map, size)` set" gate.

### 038 — `seed-fow`

```xml
<insert tableName="maps">
  <column name="id" valueNumeric="34"/>
  <column name="name" value="The Fissure of Woe"/>
</insert>
<insert tableName="map_configs">
  <column name="map_id" valueNumeric="34"/>
  <column name="party_size" valueNumeric="2"/>
  <column name="role_model" value="primary_profession"/>
</insert>
```

Mirrors `011-seed-supported-maps.xml`. With these rows present, FoW-duo uploads start flowing.
(No `(34, 8)` row → FoW 8-man uploads are rejected until that later phase adds one.)

### 039 — `seed-fow-duo-role-objectives`

Seed `role_objectives` for map 34: **every** FoW objective × **both** FoW-duo roles (`Ranger`,
`Derv`). A duo is present for the whole run, so both roles are "involved" in every objective — no
finer gating in v1 (§8). Objective names exactly as they arrive from the plugin:

```
ToC, Wailing Lord, Griffons, Defend, Forge, Menzies, Restore, Khobay, ToS, Burning Forest, The Hunt
```

→ 22 rows (`<insert>` per row, same style as `013-seed-underworld-role-objectives.xml`). This makes
FoW's `me/sections` "Your best" cells show real times rather than the spec-05 "no PB for everyone"
gap.

> `role_objectives` is keyed by `(map_id, objective_name, role)` — no `party_size` column. FoW
> 8-man's future roles are different strings, so they'll coexist here without collision; the
> `me/sections` join already filters by `run_participants.role`, which is size-appropriate.

### Entities

- `Run`: add `private Integer partySize;` + getter, mapped
  `@Column(name = "party_size", nullable = false, columnDefinition = "TINYINT UNSIGNED")` (the
  `columnDefinition` is load-bearing — without it Hibernate `ddl-auto=validate` expects a plain
  `INTEGER` column and fails at boot; same for `MapConfigId.partySize`). Set in the `Run(...)`
  constructor used by `UploadRunWriter.createRun`.
- New `MapConfig` entity + `MapConfigRepository` — `@IdClass`/`@EmbeddedId` on `(mapId, partySize)`,
  same shape as `RoleObjective`. `role_model` maps to a `RoleModel` enum
  (`TRAPPER`, `PRIMARY_PROFESSION`; `null` allowed) via a converter or a `fromDb(String)` lookup so
  the string literals live in one place.
- `GameMap`: unchanged (id + name only).

---

## 3. Concepts

### 3.1 Party size

`party_size` = number of rows the upload's `party_members[]` array carries, frozen at run-creation
time (first-writer-wins, same as every other run-level field). Not recomputed as later uploads
attach participants. For a FoW duo it is `2`; for UW it is `8`.

### 3.2 `(map, size)` config — `map_configs`

A run's `(map_id, party_size)` must match a `map_configs` row or the upload is rejected. That row
also carries the `role_model`. v1 rows: `(72, 8, 'trapper')`, `(34, 2, 'primary_profession')`.
Adding FoW 8-man later = one new row `(34, 8, '<future model>')` plus its `RoleDerivation` branch
and `role_objectives` rows — no schema change.

### 3.3 Role model per `(map, size)` — `map_configs.role_model`

Selects how `RoleDerivation` assigns a participant's `role` (§4.3), and whether the personal
section-best query is role-gated:

| `role_model` | Config | Role assignment | `me/sections` gating |
|---|---|---|---|
| `trapper` | `(UW, 8)` | Elvar override → `role_hint` T1/T2/T3 → elimination → ordered (primary,secondary) combo table | role-gated: `role_objectives` join kept |
| `primary_profession` | `(FoW, 2)` | `role` = primary profession — `Ranger`-primary → `Ranger`, `Dervish`-primary → `Derv`; any other primary → that profession's name | role-gated: `role_objectives` join kept (rows seeded by changeset 039) |
| *(future)* | `(FoW, 8)` | TBD — more complex, later phase | TBD |
| `NULL` | any config left without a model | no role assignment (`role` stays `NULL`) | **not** role-gated: join skipped, PB is plain `MIN(duration_ms)` over the person's own rows |

All non-null models are role-gated through the *same* `role_objectives` join — the only difference
is which role strings land in `run_participants.role` and which rows exist in `role_objectives`.

---

## 4. Backend changes

### 4.1 `GET /api/maps`

`MapController.list()` already returns all `maps` rows, so FoW appears once seeded. **Extend
`MapResponse`** to include the map's configs so the frontend picker is data-driven:

```java
public record MapResponse(Integer id, String name, List<Config> configs) {
    public record Config(int partySize, String roleModel) {}   // roleModel may be null
}
```

`configs` is that map's `map_configs` rows, ascending by `partySize`. The frontend uses the
`partySize` values as the size-selector options (§6.1); `roleModel` is informational.

### 4.2 Ingestion — `UploadRunService.processUpload`

Hard-coded `8`/`4` checks become `(map, size)`-config-driven. The order shifts slightly: the
map/size lookup has to happen before the size and registered-floor checks.

1. **`(map, size)` must be supported** — replace the `gameMapRepository.existsById(mapId)` check
   with `mapConfigRepository.findById(new MapConfigId(mapId, size))`. Not found → `400
   "unsupported map/party-size combination: <mapId>/<size>"` (WARN with `personId, mapId, size`).
   This one check now also covers "unknown map id" (no configs at all) and "wrong size for this
   map". `size` here is `party_members.size()` (already validated non-null/non-empty).
2. **Registered-character floor** — `MIN_REGISTERED_CHARACTERS = 4` →
   ```
   static int minRegisteredFor(int partySize) { return (int) Math.ceil(partySize / 2.0); }
   ```
   → UW(8): 4 (unchanged). FoW-duo(2): 1. `AdminRunService`'s retroactive-wipe bar calls the same
   helper (replacing its `MIN_REGISTERED_CHARACTERS` reference) so there's one source of truth.
3. `objective` required — unchanged.
4. Role derivation — `RoleDerivation.resolveRoles(restrictedMembers, config.roleModel())` (§4.3).
5. `mapDedupLock.withLock(mapId, …)` — unchanged. (Per-map is still correct: a FoW duo and a
   hypothetical FoW 8-man on the same `map_id` would share the lock, which is harmless — the
   exact-roster check in `findDedupMatch` keeps them from merging.)

`UploadRunWriter.createRun` sets `run.setPartySize(members.size())`.

### 4.3 `RoleDerivation` — dispatch on `RoleModel`

New signature: `resolveRoles(List<PartyMemberDto> members, RoleModel model)`. Dispatch:

- **`TRAPPER`** — today's algorithm verbatim (Elvar override → hints → in-upload elimination →
  ordered profession-combo table). Keep the `size != 8` guard here — `trapper` is only ever
  configured for `(UW, 8)`, so a non-8 list reaching this branch is a bug worth failing on.
- **`PRIMARY_PROFESSION`** — new `resolveByPrimaryProfession(members)`: for each member,
  `role = professionName(member.primary())` with `Dervish → "Derv"` (reuse the existing code) and
  every other primary mapped to its own name (`Ranger → "Ranger"`, `Warrior → "Warrior"`, …). Never
  `NULL` for a known profession id; unknown id → `NULL` + WARN (same as an unresolved combo today).
  No dependence on `secondary`, `role_hint`, or party position, so it's size-agnostic.
- **`null`** — return an all-`NULL` list (no model for this config).

`resolveByPrimaryProfession` reuses the profession-id constants already duplicated across
`RoleDerivation` / `UploadRunWriter` / tests (no shared constants class exists yet; not introducing
one here).

`restrictHintsToSelf` still runs before dispatch (harmless when the model ignores hints).

**`UploadRunWriter.inferRemainingTrapperRoleByElimination`** — gate its call on
`model == RoleModel.TRAPPER`. It can't meaningfully fire for a duo anyway, but a FoW ranger is
`Ranger`-primary and could be `Ranger/Assassin` secondary, which would wrongly match its
`primary == RANGER && secondary == ASSASSIN` filter — so gate it explicitly rather than relying on
"probably won't happen".

**Frontend `ROLES` / plugin `kVoteRoles`** must gain `"Ranger"` (see §5.4, §6.6).

### 4.4 `partySize` filter — scoped to Run History in v1

**Run History (`GET /api/runs`)** — gets a real, independent `partySize` filter now, because that
list can span maps *and* sizes:

- **`RunSpecifications`** — add `public static Specification<Run> hasPartySize(Integer size)`
  (`cb.equal(root.get("partySize"), size)`, null-guarded, same shape as `hasMap`). Wired into
  `RunHistoryService.search`.
- **`RunHistoryFilter`** — add `Integer partySize`; `RunHistoryController` reads
  `@RequestParam(required = false) Integer partySize`.

**Leaderboards / Loserboards — no `partySize` param in v1.** Today `(map → party_size)` is 1:1
(`72→8`, `34→2`), so `/api/leaderboards/maps/34/...` *already* means "FoW duo" and
`/api/leaderboards/maps/72/...` means "UW 8-man" — an extra `partySize` filter would change no
result while touching ~20 native-SQL / JPQL queries. The frontend still shows the size beside the
map (as a label, §6.2), so the "which party size am I looking at" affordance the user asked for is
present; it just isn't a filter that can disagree with the map yet.

**When FoW 8-man lands** (later phase), that's the trigger to thread an optional `partySize`
(`(:partySize IS NULL OR r.party_size = :partySize)`) through `LeaderboardController` /
`LoserboardController` / their services / `LeaderboardQueryRepository` / `LoserboardQueryRepository`
/ the `RunObjectiveRepository` section queries — a well-scoped mechanical pass, tracked here.

**`me/sections` role gating** — no change needed in v1. FoW's `role_model` is
`primary_profession` with `role_objectives` rows seeded (changeset 039), so the *existing*
role-gated query already returns correct PBs for `Ranger`/`Derv`. A `role_model = NULL` config
(which would need the join dropped) does not exist in v1.

### 4.5 DTOs

- `RunSummaryResponse` / `RunDetailResponse` — add `partySize` (the frozen roster count).
  `participantCount` stays for now but can lag while multi-uploads trickle in; UI shows `partySize`.
- `MapResponse` — `configs[]` (§4.1).

### 4.6 Tests

- **`AbstractIntegrationTest`** — `cleanDatabase()` now truncates `map_configs` and reseeds
  `(72, 8, 'trapper')` alongside the `maps` row; new `seedFissureOfWoe()` helper (map 34 + its
  `(34, 2, 'primary_profession')` config + the 22 duo `role_objectives` rows) + `FISSURE_OF_WOE_*`
  constants. *Done.*
- **`FowDuoUploadIntegrationTest`** (new) — duo happy path (`party_size=2` persisted, roles
  `Ranger`/`Derv`, `completed` from last objective `status==2`); size 3 → 400; `(34, 8)` → 400;
  0 registered chars → 400; exactly 1 registered → 200. *Done.*
- **`UploadRunIntegrationTest`** — existing `rejectsAnUnsupportedMapId` / `rejectsPartySizeOtherThanEight`
  still pass (they only assert `400` + no run); the 6 `new Run(...)` call sites across the suite got
  a trailing `, 8`. *Done.*
- **`MapIntegrationTest`** — asserts `configs=[{8,"trapper"}]` for UW and, with `seedFissureOfWoe()`,
  `[{2,"primary_profession"}]` for FoW. *Done.*
- **`RoleDerivationTest`** — its 31 direct calls now target `resolveByTrapperModel` (unchanged
  behaviour, 35/35 green). A `PRIMARY_PROFESSION` case (Ranger→`Ranger`, Dervish→`Derv`) is still
  worth adding. *Partly done.*
- Still TODO: a `LeaderboardIntegrationTest` case for FoW `me/sections` returning a real PB via the
  seeded `Ranger`/`Derv` rows; a `RunHistory` `partySize`-filter case.
- `scripts/seed-uw-runs.mjs` — add a sibling `seed-fow-runs.mjs` (or parameterise by map) producing
  a handful of 2-person FoW runs across the 11-objective route for e2e/local.

### 4.7 Plugin-version gate

Bump `static/SCTracker.version.json` `version` + `compiled_at` and drop the new `SCTracker.dll`
into `src/main/resources/static/` **in the same deploy** as the plugin release that adds FoW
(`MachineKeyAuthenticationService` 426s any client below the declared version — see
`PluginVersionMetadataLoader`).

---

## 5. Plugin changes (`GWToolboxpp/plugins/SCTracker`)

`SCTracker.cpp` / `SCTracker.h`. The plugin already publishes a generic
`party + objective` payload sourced from GWToolboxdll's `ObjectiveTimerRuns_*.json`; the UW-specific
parts are (a) the tracked-map allowlist, (b) Dhuum-based death/end-reason/gambling logic, (c) the
"only 8-man parties" publish gate.

### 5.1 Tracked maps

```cpp
const std::unordered_set<uint32_t> kTrackedMapIds = {
    static_cast<uint32_t>(GW::Constants::MapID::The_Underworld),      // 72
    static_cast<uint32_t>(GW::Constants::MapID::The_Fissure_of_Woe),  // 34
};
```

**Confirmed**: GWToolboxdll's `ObjectiveTimerWindow` still emits a FoW `ObjectiveSet`, so the
objective payload (`ToC … The Hunt`) flows with **no further objective-parsing code** in the plugin
— it just reads the JSON GWToolboxdll already writes to `ObjectiveTimerRuns_*.json`.

### 5.2 Per-map expected party size (publish + vote gate) — *implemented*

Anon-namespace helper `ExpectedPartySize(map_id)` → `2` for `The_Fissure_of_Woe`, `8` otherwise.
`ProcessSync`'s hard `CountRealPlayers(front.party_members) != 8` becomes
`!= ExpectedPartySize(front.map_id)` — this one change gates **both** publishing and the post-run
vote (a skipped entry calls `CancelPendingVoteIfMatching`). For FoW a run only publishes with a
**2-entry, players-only** `party_members`; heroes/henchmen push the real-player count off 2 and it's
dropped, matching "duo, no AI".

### 5.3 Dhuum-specific logic → guarded to UW — *implemented*

New helper `MapHasDhuumMechanics(map_id)` (true only for `The_Underworld`), checked against
`pending_map_id` (set in `CaptureParty` for the active run):

- **`OnAgentUpdateAllegiance`** (`dhuum_started` latch) and **`OnObjectiveDone`** (`dhuum_completed`
  latch) both early-return when `!MapHasDhuumMechanics(pending_map_id)` — so neither latch can ever
  fire off the Underworld.
- **Death tracking** (`OnUpdateAgentState`): unchanged. It short-circuits on `dhuum_started`, which
  now provably stays false on FoW → deaths are counted for the whole run (after the 60s grace).
- **Gambling stone** (`OnWriteToChatLog`): gate is now `!MapHasDhuumMechanics(pending_map_id) ||
  !dhuum_completed` → the chat parsing never runs on FoW; `gambling_stone_net` stays `null` →
  backend treats it as "didn't gamble" → excluded from Gamblers Anonymous.
- **End-reason `completed` shortcut** (`OnGameSrvTransfer`: `dhuum_completed` → `completed`):
  unchanged; inert on FoW because `dhuum_completed` can't latch there. FoW completion is decided by
  `ProcessSync`'s map-agnostic `IsRunCompleted` (`objectives.back().status == Completed`) — no
  real-time latch in v1 (confirmed sufficient).

### 5.4 Voting (failure / MVP) for FoW duos — *implemented*

**Backend: no change** — `FailureReportService` / `MvpReportService` have no party-size gate and
validate each voted role against `findDistinctRolesByRunId(runId)` (= `{Ranger, Derv}` + `Nobody`
for a FoW duo).

**Plugin:**

1. 8-man vote wall — fixed by §5.2's `ExpectedPartySize` change.
2. `kVoteRoles` — `"Ranger"` added before `"Nobody"` (array `12 → 13`; `vote_role_checked` array
   `12 → 13`; `kNobodyVoteRoleIndex` still `size()-1`).
3. Popup role filter — implemented as a **map-level** filter (simpler than a per-run snapshot):
   `pending_vote_map_id` is captured in `OpenVote` (new `map_id` param on all 3 call sites),
   `ResetVoteState` clears it. `DrawVotePopup`'s role loop `continue`s past any role for which
   `VoteRoleVisibleForMap(pending_vote_map_id, i)` is false — UW shows all roles (plugin can't see
   the server's combo derivation), FoW shows only `Ranger` / `Derv` / `Nobody`.

### 5.5 README / version — *implemented*

- `README.md` "What it does" updated: tracks UW (8-man) + FoW (2-person duos); publish gate is
  "the map's expected size".
- `SCTRACKER_PLUGIN_VERSION` bumped `9 → 10` in `cmake/gwtoolboxdll_plugins.cmake` (feeds both
  `kPluginVersion` and the generated `SCTracker.version.json`).
- **Not done here** (deploy-time, needs a real build): committing the rebuilt `SCTracker.dll` +
  regenerated `SCTracker.version.json` (`version: 10`) into the backend's
  `src/main/resources/static/`. Bumping the backend JSON *before* the v10 dll exists would 426
  every live v9 client — do it in the same deploy as the dll (§4.7).

---

## 6. Frontend changes (`frontend/`)

### 6.1 Map + party-size registry — `src/common/maps.ts`

Replace the lone `DEFAULT_MAP_ID` with a small registry (still static reference data per
`specs/frontend/00-overview.md`; `/api/maps` remains the source for names/ids but the ordering,
short labels, and size options live here):

```ts
export interface MapChoice {
  id: string;            // GW map_id as string (route param form)
  short: string;         // "UW", "FoW"
  name: string;          // "The Underworld"
  partySizes: number[];  // [8] for UW, [2] for FoW — ascending; [0] = default
}

export const MAPS: MapChoice[] = [
  { id: '72', short: 'UW',  name: 'The Underworld',     partySizes: [8] },
  { id: '34', short: 'FoW', name: 'The Fissure of Woe', partySizes: [2] },
];

export const DEFAULT_MAP_ID = '72';
export const mapById = (id: string) => MAPS.find((m) => m.id === id);
export const defaultPartySize = (mapId: string) => mapById(mapId)?.partySizes[0];
export const sizeLabel = (n: number) => (n <= 2 ? 'Duo' : `${n}-man`);
export const mapSupportsGambling = (mapId: string) => mapId === '72';
```

`partySizes` mirrors each map's `map_configs` rows. Keep it static (avoids a fetch on first paint);
optionally reconcile against `MapResponse.configs` (§4.1) once `/api/maps` loads, so a
later-added `(34, 8)` config surfaces without a frontend deploy.

### 6.2 Shared control — `src/components/MapSizePicker.tsx`

One component, used by all four pages. Props: `mapId`, `partySize`, `onChange({mapId, partySize})`,
plus `showSize?` (default: auto). Behaviour:

- **Map**: `<select>` (or segmented buttons) over `MAPS`, showing `short` + `name`.
- **Party size**: rendered only when the selected map's `partySizes.length > 1` (per the user's
  "fixed options per map" decision). When it's a single value, show it as a static, non-interactive
  label (e.g. a chip `8-man` / `Duo`) so the user still *sees* the size but isn't offered a no-op
  dropdown. `partySizes: [2]` → label "Duo"; `[8]` → "8-man"; helper `sizeLabel(n)`.
- Changing the map resets `partySize` to that map's `defaultPartySize`.

### 6.3 Routing — normalise Loserboards to match Leaderboards

Current inconsistency the user flagged: `/leaderboards/72` (map in path) vs `/loserboards` (no map).

- `App.tsx`: add `<Route path="/loserboards/:mapId" element={<LoserboardsPage />} />`; keep
  `<Route path="/loserboards" …>` as a redirect to `/loserboards/${DEFAULT_MAP_ID}` (small
  `<Navigate>` wrapper), so old links/bookmarks still work.
- **Party size in the URL**: v1 leaderboards/loserboards need no size param — the map path segment
  already implies the size (`(map → size)` is 1:1, §4.4). The picker's size control there is a
  display label. Run History uses `partySize` as a filter key on `/runs` (real filter). When FoW
  8-man lands, add `?partySize=` to the two board routes (read via `useSearchParams`).

### 6.4 Page wiring

| Page | Today | Change |
|---|---|---|
| `Dashboard.tsx` | map `<select>` that no-ops (1 map), `DEFAULT_MAP_ID` | Use `MapSizePicker`; the "view" links become `/leaderboards/${mapId}`, `/loserboards/${mapId}`, and `/runs?map=${mapId}&partySize=${size}`. |
| `LeaderboardPage.tsx` | `:mapId` from route; objective names derived from fastest run detail (already map-agnostic) | Add `MapSizePicker` header control (map → `navigate('/leaderboards/'+mapId)`; size is a label in v1). No query-string change. |
| `LoserboardsPage.tsx` | hard-codes `DEFAULT_MAP_ID`, no route param | Take `:mapId` from route (§6.3); add `MapSizePicker`; replace `DEFAULT_MAP_ID` usages with the route value. |
| `RunHistory.tsx` | `Filters.map` (has a maps `<select>` already, fed by `/api/maps`); `EMPTY_FILTERS.map = DEFAULT_MAP_ID` | Add `partySize` to `Filters` + `EMPTY_FILTERS` (`= defaultPartySize(DEFAULT_MAP_ID)`); render via `MapSizePicker` (replacing the bare map select); add `partySize` to `buildQuery`. Map select lists `MAPS`. Role filter (`ROLES`, now incl. `Ranger`) still works — a FoW view can filter to `Ranger` / `Derv`. |

### 6.5 Empty / not-applicable panels for FoW

- **Gamblers Anonymous** and **Luckiest Players** (Leaderboards) and any UW-flavoured Loserboard
  panel that depends on gambling/ecto data: when `mapId === '34'`, hide the panel entirely rather
  than showing a perpetually-empty table. Simplest: a `mapSupportsGambling(mapId)` guard in
  `common/maps.ts` (`id === '72'`).
- **Sections** panels: fully data-driven (objective names come from the fastest run's detail), so
  FoW's 11 objectives render automatically. `Your best` cells show real times — FoW-duo is
  role-gated via the seeded `Ranger`/`Derv` × all-objectives rows (changeset 039).
- **Role-based panels** (Blamed By Role, MVP By Role, role-deaths): for FoW these show `Ranger` /
  `Derv` rows (roles are always resolved under `primary_profession`). No sparse-`NULL` problem.

### 6.6 Types & copy

- `src/common/roles.ts` — add `'Ranger'` to `ROLES` (matches `RoleDerivation`'s
  `primary_profession` output and the plugin's `kVoteRoles`). Check `RoleBadge.module.css` /
  `RoleBadge.tsx` for a per-role colour and add a `Ranger` entry.
- `src/api/types.ts` — add `party_size` to `RunSummary` / `RunDetail`; change `GameMap` to carry
  `configs: { party_size: number; role_model: string | null }[]` (§4.1).
- `HowToUse.tsx` — one line: FoW duos are tracked automatically by the updated plugin; use the
  map/size picker on Leaderboards, Loserboards, and Run History to switch views.
- `RunDetail.tsx` — already shows `map_name`; add a `sizeLabel(party_size)` chip ("Duo" / "8-man").

### 6.7 e2e

- `frontend/e2e/` — parameterise `run-history-filters.spec.ts` and the leaderboard/loserboard specs
  over `{map, size}`; add a FoW-duo path using `seed-fow-runs.mjs` data. Assert the picker switches
  the board and that UW/FoW results don't cross-contaminate.

---

## 7. Rollout sequence

1. **Backend** — changesets 036–039, `MapConfig` entity/repo, `RoleModel` dispatch in
   `RoleDerivation`, `map_configs`-driven ingestion validation, `runs.party_size` persisted,
   `GET /api/maps` returns configs, Run History `partySize` filter + DTO fields. UW behaviour
   unchanged. **Status: done — full `mvn test` green (175 tests, 0 fail/err), incl. new
   `FowDuoUploadIntegrationTest` (5) + updated `MapIntegrationTest`; `seed-fow-runs.mjs` added.**
   Deploy → `GET /api/maps` lists FoW with its config; nothing uploads there yet.
2. **Frontend** — `MapSizePicker`, `MAPS` registry, `/loserboards/:mapId` route (+ redirect from
   bare `/loserboards`), Run History `partySize` filter + Size column, `Ranger` role, gambling
   panels hidden off-UW, Dashboard is now a map/size chooser, size chip on RunDetail. **Status:
   implemented; `tsc` + `vite build` clean; affected e2e specs (dashboard/auth/run-flow) updated,
   not run here (need backend).** Deploy. Users can select FoW/Duo; boards are empty.
3. **Plugin (`SCTracker`)** — `kTrackedMapIds += FoW`, `ExpectedPartySize` per-map publish+vote
   gate, `MapHasDhuumMechanics` guards on the Dhuum latches + gambling, `Ranger` in `kVoteRoles` +
   map-level popup role filter, `SCTRACKER_PLUGIN_VERSION 9→10`. **Status: done — builds clean in
   `~/repos/GWToolboxpp` (`SCTracker.dll` v10 in `bin/Release/`).** Release step: commit the built
   dll + regenerated `SCTracker.version.json` (v10) into the backend's `static/`, deploy backend +
   dll together. FoW duo uploads begin.
4. **Seed a few FoW runs** (`seed-fow-runs.mjs`) per environment for smoke-testing the boards.

Steps 1–2 are independent of 3; 3 can slip without breaking anything. **FoW 8-man** is a
self-contained follow-on — see §9.

---

## 8. Open questions / decisions still needed

1. **Non-Ranger/Derv primaries — DECIDED: only Ranger + Derv.** Changeset 039 seeds exactly those
   two roles. `resolveByPrimaryProfession` still returns the profession name for any other primary
   (so a stray build isn't silently `NULL` in history), but such a role has no `role_objectives`
   rows and so contributes nothing to section PBs — acceptable.
2. **`me/sections` `partySize` requirement.** For a map with one config it's inferable; once FoW
   8-man exists the endpoint needs `partySize` to pick the role model. Spec assumes:
   default-to-sole-config, 400 when a multi-config map is queried without `partySize`.
3. **Param name — DECIDED: `partySize` everywhere** (URLs, `/runs` filter key, backend
   `@RequestParam`).
4. **Popup role filter — DONE at map granularity** (not per-run): FoW popup shows `Ranger` / `Derv`
   / `Nobody` only. A per-run filter (hide `Derv` in a Ranger+Ranger duo) is possible later but
   needs a party snapshot at `OpenVote` time — not worth it for a 2-role vocabulary.

---

## 9. FoW 8-man — shipping together with the duo (implemented)

**Decided with the guild:** FoW 8-man has **no set role composition** (unlike UW 8-man's
T1/T2/T3/…). So it's a **`role_model = NULL`** config, not a new derivation strategy. Roster is
**8 real players, no AI**. **No post-run vote** for these runs (role-based blame/MVP is meaningless
with no roles; player-name voting is a possible later add — §9.6).

Shipping this alongside the duo means the `partySize` work deferred in §4.4 is done now — map 34
has two configs, so every map-scoped board filters by size.

**Status: implemented.** `mvn test` green; new `FowLeaderboardIntegrationTest` +
`FowDuoUploadIntegrationTest` (8-man cases); frontend `tsc`/`vite build` green; plugin builds
green. Below is what was done.

### 9.1 Data — changeset `040-seed-fow-8man-config`

```sql
INSERT INTO map_configs (map_id, party_size, role_model) VALUES (34, 8, NULL);
```

No `role_objectives` rows for `(34, 8)` — a null-model config is never role-gated.

### 9.2 Backend

**Ingestion — no code change.** `config.getRoleModel()` is `null` → `RoleDerivation.resolveRoles(members, null)`
already returns an all-`null` list; `UploadRunWriter` already gates elimination on `TRAPPER`;
`minRegisteredFor(8) = 4`. The `(34, 8)` row alone makes 8-real-player FoW uploads valid.

**`partySize` filter — now build it (the §4.4 deferral).** Add optional
`@RequestParam Integer partySize` to every `/api/leaderboards/maps/{mapId}/**` and
`/api/loserboards/maps/{mapId}/**` endpoint (incl. `/me/**`). Thread it:
- Specification-based (`overall`, `worst`) → `.and(RunSpecifications.hasPartySize(partySize))`.
- `LeaderboardQueryRepository` / `LoserboardQueryRepository` native SQL → `AND (:partySize IS NULL OR r.party_size = :partySize)` on each of the ~15 queries.
- `RunObjectiveRepository` JPQL section queries → same `(:partySize IS NULL OR r.partySize = :partySize)` clause.
- Omitted → all sizes (back-compat); present → filter. The frontend always sends it for a
  multi-config map.

**`me/sections` role-model branch.** Resolve `role_model` for `(mapId, partySize)` (require
`partySize` when the map has >1 config; else default to the sole one). Non-null → today's
`role_objectives`-joined query. Null → new
`findPersonalSectionBestRunUngated(personId, mapId, partySize, objectiveName, from, to)`: the same
query minus the `JOIN role_objectives` clause, plus `AND r.party_size = ?` so UW-8 and FoW-8 rows
don't cross-contaminate. Same for the `/start` and `/finish` variants.

**Global `sections` participant list.** `LeaderboardService.section` / `sectionStart` /
`sectionFinish` filter the shown party to `gatedRoles.contains(rp.getRole())` — empty when roles
are null. Add `boolean roleGated = mapConfig(mapId, partySize).roleModel != null`; when false, show
the full party (same as loserboards' already-un-gated "slowest to reach objective").

**Role-breakdown boards** (`role-mvp-awards`, loserboard `role-deaths` / `role-failure-reasons`) —
no change: they `WHERE rp.role IS NOT NULL`, so they return nothing for FoW 8-man. Frontend hides
the panels (§9.3).

### 9.3 Frontend

- `common/maps.ts`: `{ id: '34', short: 'FoW', name: 'The Fissure of Woe', partySizes: [2, 8] }` —
  `MapSizePicker`'s size control becomes a real `<select>` for FoW (UW stays a chip).
- `LeaderboardPage` / `LoserboardsPage`: `partySize` now in state + URL (`?partySize=`, via
  `useSearchParams`); `MapSizePicker` gets a real `onSizeChange`; thread `partySize` into every
  `api.get` URL and query key.
- New `configHasRoles(mapId, partySize)` — derived from `/api/maps` `configs[].roleModel` (static
  fallback for first paint). Gates:
  - `LeaderboardPage`: hide **MVP By Role**.
  - `LoserboardsPage`: hide **Deaths By Role**, **Blamed By Role**.
  - Sections "Your best" column renders unchanged — the backend returns an un-gated time, no
    frontend logic needed.
- `RunHistory` role filter: left as-is (a FoW-8 filter-by-role just returns nothing — harmless).

### 9.4 Plugin (`SCTracker`) — still version `10` (one combined release)

- Publish gate: `ExpectedPartySize(map_id)` replaced with `IsAcceptablePartySize(map_id, count)` —
  UW → `count == 8`; FoW → `count == 2 || count == 8`. `ProcessSync`'s check is now
  `!IsAcceptablePartySize(front.map_id, CountRealPlayers(front.party_members))`.
- Voting: new `MapSizeHasRoles(map_id, real_player_count)` — UW → true, FoW/2 → true, FoW/8 →
  false. The `OpenVote` calls in `OnGameSrvTransfer` and the ProcessSync late-MVP path are guarded
  with it (real-player count from `party_members` / `next.party_members`). `VoteRoleVisibleForMap`
  now keys on "is FoW" directly; unreachable for FoW/8.
- No Dhuum changes (`MapHasDhuumMechanics(FoW)` is already false at any size).

### 9.5 Tests

- `FowEightManUploadIntegrationTest` — seed `(34, 8, NULL)`; 8-real-player upload → 200,
  `party_size = 8`, all participant roles `null`, `completed` derivation unaffected.
- Leaderboard: a FoW/2 run and a FoW/8 run don't appear on each other's boards (`?partySize=`).
- `me/sections` for FoW/8 → a real PB via the un-gated query (not the spec-05 "no PB" gap).
- `RoleDerivationTest` — `resolveRoles(list, null)` → all-`null`.

### 9.6 Open question left for FoW 8-man

**Player-name voting** (the "B" option) — deferred. If the guild later wants MVP/blame for FoW
8-man, add it as: popup lists the 8 character names, `FailureReportService` / `MvpReportService`
match the vote against `run_participants.raw_name` instead of `role` (both already attach the row
to a `run_participant`). No schema change.
