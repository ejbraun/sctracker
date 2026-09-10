# Feature Spec — Eye of the North dungeons as supported maps (shared)

Status: **in implementation** (branches `feat/dungeons` in uwtracker, `feat/sctracker-dungeons` in
GWToolboxpp). **This is the cross-repo contract.** Implementation detail lives in two sibling docs:

- **Backend + frontend** (`com.howl.uwtracker`, `frontend/`): [`dungeons-impl-backend.md`](dungeons-impl-backend.md)
- **Plugin** (`GWToolboxpp/plugins/SCTracker`): `GWToolboxpp/plugins/SCTracker/DUNGEONS.md`

Builds on [`fow-and-party-size.md`](fow-and-party-size.md). Read that first — this feature reuses
its `map_configs` / party-size / role-less (`role_model = NULL`) machinery wholesale. Where this
spec contradicts an assumption in `specs/backend/*` or `specs/frontend/*` (e.g. "a run is one game
instance"), this spec wins.

---

## 1. Goal & scope

Add **every Guild Wars: Eye of the North dungeon GWToolboxdll's Objective Timer follows** (18) as
supported maps, so their full clears and per-level times land on the leaderboards / loserboards /
run history alongside the Underworld, the Fissure of Woe and Domain of Anguish.

Dungeons are **role-less at every party size 1–8** — the exact `role_model = NULL` shape the
Fissure of Woe's non-duo sizes and Domain of Anguish use (`fow-and-party-size.md` §10–§11). The
backend and frontend need almost no new logic. **The new work is in the plugin**, because a
*multi-level* dungeon is the first **multi-instance run** the tracker handles: one logical run
spans 2–5 chained explorable areas, each its own `MapID`, each firing its own `InstanceLoadInfo`
*and* `GameSrvTransfer`.

### In scope (v1)

- All 18 dungeons in `ObjectiveTimerWindow::AddObjectiveSet`'s switch (§2 for the id table), each
  seeded at **every party size 1–8**: one `maps` row + a `map_configs` row `(entryMapId, n, NULL)`
  for `n ∈ 1..8`, no `role_objectives`. Same shape as the Fissure of Woe's non-duo sizes. Party
  size is the **all-human** roster count (`party_members.length`) — a low-man run padded with
  heroes/henchmen is dropped client-side by the plugin's real-player gate, exactly as for FoW.
  - **14 multi-level** (2–5 levels): tracked as one multi-instance run.
  - **4 single-instance**: Ooze Pit, Secret Lair of the Snowmen, Fronis Irontoe's Lair (1 level
    each), and **Slavers' Exile** (GWToolboxdll only times its final level,
    `Slavers_Exile_Level_5`). These are tracked exactly like Domain of Anguish — no new plugin
    lifecycle code.
- Plugin support for multi-instance runs: capture the party once on the entry level, keep outcome
  tracking (deaths / wipe / resign / completion) alive across level transitions, finalize the run
  **once**, when the dungeon is actually left.
- `party.map_id` on every upload is the **entry-level** map id.
- Real-time completion latch off `GAME_SMSG_DUNGEON_REWARD`.
- Name-mode post-run vote (credit/blame a **character**, not a role) — already works for role-less
  configs end to end; dungeons inherit it.
- Frontend: one `MAPS` entry per dungeon (grouped under a `Dungeon` `<optgroup>`); every page is
  already data-driven.

### Out of scope (v1) — see §7, and §9 of `dungeons-impl-backend.md`

- **`Forsaken_Tunnels` / `Forsaken_Tunnels_Presearing`** — GWCA enum members
  `ObjectiveTimerWindow` cases on defensively (ids 880 / 789). Cut / pre-Searing content, not live
  speed-clear dungeons. Not seeded; the plugin doesn't track them.
- **Hero/henchman-assisted low-man runs** — `party_members.length` is the all-human count; a
  padded roster's human count won't match a config and is dropped client-side (FoW rule).
- **Hard mode vs normal mode** — no difficulty dimension exists; an HM and NM clear of the same
  dungeon share one board.
- **Per-level role composition** — no `RoleDerivation` branch, no `role_objectives`, no by-role
  panels.
- **Dungeon-specific item-drop boards.**
- **The full Slavers' Exile route** (Forgewight / Rand / Ilsundur / Justiciar branches then
  Duncan) — only the Duncan room is timed, matching GWToolboxdll. Extending it is a later,
  GWToolboxdll-side change.

---

## 2. Established facts

### 2.1 Entry map ids

The value each dungeon's runs are published under — the entry (Level 1) map id for a multi-level
dungeon, the single map id for a 1-level one, and `Slavers_Exile_Level_5` for Slavers. Computed
from the GWCA `MapID` enum (`GWToolboxpp/Dependencies/GWCA/include/GWCA/Constants/Maps.h`) and
cross-checked against its anchors (`Five_Team_Test = 592`, `Vloxen_Excavations_Level_1 = 604`,
`Bloodstone_Caves_Level_1 = 612`, `The_Norn_Fighting_Tournament = 700`, `Unknown_879 = 879`).
Worth one live `party.map_id` capture to confirm before production.

| id | Dungeon | Levels | Tracking |
|---|---|---|---|
| 560 | Cathedral of Flames | 3 | multi-instance |
| 570 | Catacombs of Kathandrax | 3 | multi-instance |
| 573 | Rragar's Menagerie | 3 | multi-instance |
| 576 | Ooze Pit | 1 | single |
| 578 | Oola's Lab | 3 | multi-instance |
| 581 | Shards of Orr | 3 | multi-instance |
| 584 | Arachni's Haunt | 2 | multi-instance |
| 604 | Vloxen Excavations | 3 | multi-instance |
| 607 | Heart of the Shiverpeaks | 3 | multi-instance |
| 612 | Bloodstone Caves | 3 | multi-instance |
| 615 | Bogroot Growths | 2 | multi-instance |
| 617 | Raven's Point | 3 | multi-instance |
| 623 | Slavers' Exile (`Slavers_Exile_Level_5`) | final level only | single |
| 628 | Sepulchre of Dragrimmar | 2 | multi-instance |
| 630 | Frostmaw's Burrows | 5 | multi-instance |
| 635 | Darkrime Delves | 3 | multi-instance |
| 701 | Secret Lair of the Snowmen | 1 | single |
| 704 | Fronis Irontoe's Lair | 1 | single |

### 2.2 Mechanics

| Fact | Value | Source |
|---|---|---|
| A dungeon run is **N chained explorable instances** | 1–5, one `MapID` per level | `GWToolboxpp/GWToolboxdll/Windows/ObjectiveTimerWindow.cpp` — `AddObjectiveSet(MapID)` switch + `AddDungeonObjectiveSet(levels[])` |
| GWToolboxdll emits **one** `ObjectiveSet` per dungeon run | `name` = entry-level map name (`Resources::GetMapName(levels[0])`); `utc_start` stamped once at entry; objectives named `"Level 1"`, `"Level 2"`, …; last objective ends on `EventType::DungeonReward` | `ObjectiveTimerWindow.cpp` `AddDungeonObjectiveSet` |
| The serialized `ObjectiveSet` carries **no map id** | fields: `name`, `instance_start`, `utc_start`, `objectives[]`, `duration` | `ObjectiveTimerWindow.h` — `ObjectiveSet::Serialized` |
| Backend's only map identifier is **`party.map_id`** from the plugin | `objective.name` only feeds the `maps.name` auto-populate (`UPDATE … WHERE name IS NULL`) | `specs/backend/02-ingestion-upload-run.md` step 7 |
| Plugin ↔ GWToolboxdll run correlation is **`utc_start` ±2s only** | `TryReadMatchingObjectiveEntry` | `SCTracker.cpp` |
| `GW::Packet::StoC::DungeonReward` exists | `GAME_SMSG_DUNGEON_REWARD`, fired at the reward screen | `GWToolboxpp/Dependencies/GWCA/include/GWCA/Packets/StoC.h:277` |
| `GW::RegionType::Dungeon` / `GW::Map::GetInstanceType()` are available | region-type cross-check for "still inside the dungeon" | `GWCA/GameEntities/Map.h:34`, `GWCA/Managers/MapMgr.h:134` |
| Dungeons have **no fixed role composition** | `role_model = NULL` — identical to DoA / FoW-8 | user decision |
| Name-mode (character) failure/MVP voting already works for `role_model = NULL` | plugin lists `party_members` names; backend collects `roles[]` as `raw_name`s, resolved at window close | `SCTracker.cpp` `OpenVote`; `FailureReportService.submit` / `MvpReportService.submit` |

---

## 3. Data model — the shared contract

Per v1 dungeon, seeded by one Liquibase changeset (`055-seed-dungeons.xml`, detail in
`dungeons-impl-backend.md` §2):

```
maps:         (id = <entryMapId>, name = "<exact Resources::GetMapName(Level_1)>")
map_configs:  (map_id = <entryMapId>, party_size = n, role_model = NULL)  for n = 1..8
role_objectives:  (none)
```

Both repos key off these rows:

- **Backend** — `UploadRunService` rejects any `(map_id, party_size)` with no `map_configs` row
  (`party_size` = `party_members.length`); the matched row's `role_model` (`NULL`) selects the
  all-null `RoleDerivation` path.
- **Plugin** — publishes only all-human parties whose real-player count is 1–8 for a dungeon
  (`IsAcceptablePartySize`); a hero/henchman-padded roster whose human count is < the roster size
  is dropped client-side, never uploaded.

Adding a dungeon later = one changeset (18 rows via a cross-join) + one `kDungeonLevelToEntry`
entry (if multi-level) + one `MAPS` entry. No schema change.

---

## 4. Concepts

### 4.1 Multi-instance run

A dungeon run is **one logical run spanning 2–5 game instances**. It is keyed everywhere — the
plugin's `PartyLog_*.json` entry, GWToolboxdll's `ObjectiveTimerRuns_*.json` `ObjectiveSet`, the
backend's dedup — by the **entry-level `utc_start`**, stamped once when the party zones into
`_Level_1`. Every later level load is the *same* run: no new `utc_start`, no new party capture, no
premature finalize.

`party.map_id` is the **entry-level map id**. This is load-bearing: it is what the backend's
`map_configs` lookup uses, what the frontend routes on (`/leaderboards/560`), and what must agree
with `objective.name` (the entry-level map name) for the `maps.name` auto-populate to hit the right
row.

Because the plugin correlates on `utc_start` only, and GWToolboxdll stamps its `ObjectiveSet
.utc_start` once at set creation (entry level), the two sides line up **iff the plugin stamps once,
on the entry level, and never re-stamps on deeper levels.**

### 4.2 Objective names — `"Level 1"`, `"Level 2"`, …

GWToolboxdll names dungeon objectives `"Level N"`, not descriptive quest names. These collide
across dungeons by string, but every backend query that touches objectives is keyed by
`(map_id, objective_name)`, so map id disambiguates and nothing breaks. Descriptive display labels
are optional frontend polish (`dungeons-impl-backend.md` §6.3).

### 4.3 No difficulty dimension

NM and HM clears of the same dungeon upload with the same `map_id` and share one board. Accepted
for v1.

---

## 5. Division of responsibility

| Area | Owner | Change |
|---|---|---|
| `maps` / `map_configs` seed | backend repo | new changeset `055` — 18 dungeons × sizes 1–8 |
| Ingestion validation, role derivation, dedup | backend repo | **none** — `map_configs` lookup + `role_model = NULL` path already handle it |
| Leaderboards / loserboards / sections / run history | backend repo | **none** — every board query is already `partySize`-parameterised (FoW 1–8 work); role-less already un-gated |
| Registration-character floor | backend repo | invert `UploadRunService` exempt-list → enforced-list `Set.of(72)` (§7.3) |
| `/api/maps` response | backend repo | **none** — returns new maps + `configs` automatically once seeded |
| Frontend map registry (`MAPS`, `ROLE_MODEL`) | backend repo (`frontend/`) | one entry per dungeon |
| Frontend pages / picker / routing | backend repo (`frontend/`) | picker `<optgroup>`; `RunDetail` hides the Professions column for a (dungeon, party_size 2) run; rest data-driven |
| Tracked-map allowlist (`kTrackedMapIds`) | plugin repo | += entry ids; new `kDungeonLevelToEntry` |
| **Multi-instance run lifecycle** | plugin repo | **new** — defer finalize across level transitions |
| Completion latch (`DungeonReward`) | plugin repo | new hook + `dungeon_completed` |
| `IsAcceptablePartySize` | plugin repo | dungeon ids → `1..8` (new `IsDungeonRun` helper) |
| Vote gates (`MapSizeHasRoles`, `MapHasDhuumMechanics`) | plugin repo | **none** — dungeons hit the role-less / non-Dhuum `default` at every size |
| Plugin version + patch notes + README | plugin repo | `SCTRACKER_PLUGIN_VERSION` 15→16; append `SCTracker.patch.txt` |

---

## 6. Rollout sequence (cross-repo)

1. **Backend** — changeset `055`, floor-exemption decision, tests. UW/FoW/DoA behaviour unchanged.
   Deploy → `/api/maps` lists the dungeons; nothing uploads there yet.
2. **Frontend** — `MAPS` + `ROLE_MODEL` entries, copy. Deploy → users can select a dungeon; boards
   are empty.
3. **Plugin** — `kTrackedMapIds` += entry ids, multi-instance lifecycle, `DungeonReward` latch,
   version bump + patch notes. A GWToolboxpp `master` build ships the dll + manifest to the plugin
   storage bucket via CI; the running backend picks them up within `plugin.storage.cache-ttl`
   (~1h). Dungeon uploads begin.
4. **Seed** a few dungeon runs per environment for board smoke-testing.

Steps 1–2 are independent of 3; 3 can slip without breaking anything. Do **not** raise the
backend's enforced minimum plugin version — old clients simply never tracked dungeons.

---

## 7. Decisions made

1. **Which dungeons.** All 18 in `ObjectiveTimerWindow.cpp`'s switch (§2.1). `Forsaken_Tunnels*`
   excluded (cut / pre-Searing content).
2. **Party sizes.** Every dungeon supports **1–8** (all-human), each size role-less — the FoW
   non-duo shape. Seeded as an 18×8 cross-join in `055`. Hero/henchman-padded low-man runs are
   dropped client-side (human count < roster).
3. **NM vs HM share a board** (no difficulty dimension). Accepted for v1. A `runs.hard_mode`
   dimension is a separate, larger spec.
4. **Registration-character floor** — dungeons are **exempt**, leaning on the plugin's real-player
   gate as the pug filter (like FoW/DoA). Implemented by inverting `UploadRunService`'s exempt-list
   to an *enforced*-list (`Set.of(72)` — the Underworld only), so any future seeded map is exempt
   by default.
5. **`maps.name` strings** — seeded from the canonical Guild Wars area names in changeset `055`;
   still worth an in-game check against `Resources::GetMapName(Level_1)` (a mismatch is cosmetic —
   ingestion won't overwrite a non-null name).
6. **Completion latch** — the `DungeonReward` real-time latch ships in the same plugin release
   (v16), so the MVP-vote prompt lands at the reward screen. `ProcessSync`'s `IsRunCompleted`
   fallback still covers a mid-run joiner.
7. **Joined-mid-run** (loading straight into Level 2+) — tracked: the run starts then, published
   under the entry id, with no early-level timing (same degradation UW/FoW tolerate).
