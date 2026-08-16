# 02 — Ingestion: `POST /upload-run`

The only endpoint the GW1 SDK plugin calls. Machine-key auth, not session auth (see [00-overview](00-overview.md)). Replaces the current `JsonController` stub (`POST /upload-runs`, plural — renamed).

## Auth

Request header: `X-Machine-Key: <raw key>`.

- Missing header → `401 { "error": "missing X-Machine-Key" }`
- Hash the provided key with SHA-256 (hex), look up `machine_keys WHERE key_hash = ? AND revoked_at IS NULL`.
- No match → `401 { "error": "invalid or revoked machine key" }`
- Match → proceed, with `person_id` from the matched row available for logging/audit (not persisted on the run itself — runs are shared, not owned by an uploader).

## Request payload

Real sample, captured from GWToolboxdll (party size 1 here — almost certainly a debug/test capture, not a payload that should pass validation; see the party-size rule below):

```json
{
  "party": {
    "utc_start": 1785745381,
    "map_id": 72,
    "character_name": "No Way No Hope",
    "end_reason": "resign",
    "party_members": [
      { "name": "No Way No Hope", "primary": 8, "secondary": 2, "is_player": true, "is_hero": false, "is_henchman": false }
    ]
  },
  "objective": {
    "name": "The Underworld",
    "instance_start": 96370352,
    "utc_start": 1785745382,
    "objectives": [
      { "name": "Chamber", "status": 0, "start": 4294967295, "done": 4294967295, "indent": 0, "duration": 4294967295 },
      { "name": "Restore", "status": 0, "start": 4294967295, "done": 4294967295, "indent": 0, "duration": 4294967295 }
    ],
    "duration": 4238
  }
}
```

Profession IDs use the official GW1 numbering from `professions` (spec 01): `1`=Warrior, `2`=Ranger, `3`=Monk, `4`=Necromancer, `5`=Mesmer, `6`=Elementalist, `7`=Assassin, `8`=Ritualist, `9`=Paragon, `10`=Dervish.

`objective.name` (`"The Underworld"` above) is the human-readable zone name — populated into `maps.name` automatically at ingestion (only when currently `NULL`, so a later manual correction isn't silently overwritten). No admin backfill needed for it, unlike the original draft assumed.

**`party_members[]` has three fields not in the original draft**, found in this sample: `is_player`, `is_hero`, `is_henchman` (booleans). Party slots can be AI-controlled heroes/henchmen, not just human players — e.g. a solo player running with a full hero/henchman team, like the sample above. **Resolved**: real guild 8-man parties are always all human players, so the party-size-8 rule below already guarantees this — no special handling needed for role derivation, validation, or leaderboard eligibility. (Smaller/mixed parties, like this sample's solo capture, are simply rejected by the size check before it would matter.) Stored on `run_participants` for fidelity, not read by any logic.

**`party_members[]` also carries an optional `role_hint`** (string, `"t1"`/`"t2"`/`"t3"`, or `"unknown"` before one of those resolves), set by newer plugin builds for a Ranger/Assassin-primary member once they cast one of a fixed set of trapping skills — see "Role derivation" below for how it's consumed. Absent on older plugin builds, and per-member rather than guaranteed for the whole party.

**`objectives[]` has one field not in the original draft**: `indent` (integer, nesting depth — always `0` in samples seen so far). Stored on `run_objectives` for fidelity; not yet used by anything.

**Timestamp format — confirmed against the real sample above** (superseding the original draft's "assume epoch milliseconds for everything"):
- `party.utc_start` and `objective.utc_start` are `time(nullptr)` — real wall-clock epoch **seconds**. Parse via `Instant.ofEpochSecond`, not `ofEpochMilli`.
- `objective.instance_start` is **not a timestamp** — a `std::chrono::steady_clock`/load-screen millisecond counter, zeroed at an arbitrary point tied to system boot. Not comparable across runs or machines, no absolute meaning. Store the raw number (`runs.instance_start_ms`, a plain `BIGINT`); never attempt to convert it to a date.
- `objectives[].start`/`done`/`duration` and the top-level `objective.duration` are milliseconds *relative to* `instance_start` — genuinely millisecond values, just not epoch-anchored. Storage is unaffected (still a raw `BIGINT` millisecond value); only the sentinel mapping and null-handling matter here, not a unit conversion.

## Processing pipeline

Executed in a single transaction per request.

1. **Parse** the JSON body. Malformed body → `400 { "error": "malformed request body" }`.
2. **Validate party size**: `party.party_members.length == 8`, else log `{machine_key_id, map_id, party_size}` at WARN and return `400 { "error": "party size must be 8" }`.
3. **Validate objective section present**: `objective` must not be null/missing, else log `{machine_key_id, map_id}` at WARN and return `400 { "error": "objective is required" }`. A party-only upload (no objective/timing data at all) can never be leaderboard-eligible — `completed` requires at least one objective row — so it isn't worth accepting; the GW1 SDK plugin drops these client-side rather than publishing them. These two checks are the *only* validation failures that reject the upload — everything else below degrades gracefully (nulls *within* a present objective section, unresolved roles) rather than failing the request.
4. **Sentinel mapping**: replace `4294967295` with `null` in every numeric field independently — each objective's `start`/`done`/`duration`, and the top-level `objective.duration`.
5. **Derive completion**: `completed = objectives.length > 0 && objectives[last].status == 2`. `end_reason` is stored as metadata only and never consulted for this.
6. **Resolve the map row**: `INSERT IGNORE INTO maps (id) VALUES (?)` — no-ops if already present, otherwise creates it with `name = NULL`; then `UPDATE maps SET name = ? WHERE id = ? AND name IS NULL` using `objective.name`, so the zone name populates automatically the first time a map is seen instead of needing manual backfill.
7. **Dedup / find-or-create the run**, guarded by a MySQL named lock scoped to the map (`GET_LOCK(CONCAT('run-dedup:map:', map_id), 10)` … `RELEASE_LOCK(...)`) so concurrent uploads from different party members' clients for the same run can't race into two `runs` rows. The lock is per-map rather than per-time-bucket: a time-bucketed key would need consistent bucket boundaries and two clients whose `utc_start` straddle a boundary could still land in different buckets and race. Per-map is coarser but fully correct, and fine at this traffic volume (a small guild, a handful of concurrent uploads at most).
   - Query: `SELECT id FROM runs WHERE map_id = ? AND utc_start BETWEEN ? - INTERVAL 60 SECOND AND ? + INTERVAL 60 SECOND ORDER BY ABS(TIMESTAMPDIFF(MICROSECOND, utc_start, ?)) LIMIT 5`, filtered down to whichever candidate's existing `run_participants` roster (raw names) exactly matches the incoming upload's roster — a wide window alone can catch more than one candidate (e.g. two unrelated parties starting close together on the same globally-shared `map_id`); the exact-roster check is what actually guards against merging them, independently of window size (`UploadRunWriter.findDedupMatch`).
   - **Found** → reuse that `run_id`. Do **not** overwrite `end_reason`/`completed`/`duration_ms`/objectives on the existing row — first upload for a run wins on run-level fields; later uploads only attach participants (step 8). This is a first-writer-wins policy; flagged as a default, not something the spec text mandated explicitly.
   - **Not found** → `INSERT INTO runs (map_id, utc_start, instance_start_ms, objective_start, end_reason, completed, duration_ms) VALUES (...)`, then insert all rows into `run_objectives` (one per array element, `sequence` = array index, plus `indent`).
8. **Attach participants**: for each of the `party_members[]` entries (regardless of whether the run was found or newly created):
   - Resolve `character_id` via `SELECT id FROM characters WHERE character_name = ?` (exact match against `raw_name`).
   - Derive `role` (algorithm below).
   - `INSERT INTO run_participants (run_id, character_id, raw_name, primary_profession_id, secondary_profession_id, role, party_index, is_player, is_hero, is_henchman) VALUES (...) ON DUPLICATE KEY UPDATE character_id = VALUES(character_id), primary_profession_id = VALUES(primary_profession_id), secondary_profession_id = VALUES(secondary_profession_id), role = VALUES(role), is_player = VALUES(is_player), is_hero = VALUES(is_hero), is_henchman = VALUES(is_henchman)` keyed on `(run_id, raw_name)`.
   - This makes resends idempotent (no duplicate rows) while still letting a resend correct stale data (e.g. a character registered after the first upload, or a role derivation bugfix re-ingested) rather than being a pure no-op.
9. Commit; release the named lock.

## Role derivation

Pure function, `resolveRoles(partyMembers[8]) -> String[8]` (or an equivalent array-in/array-out shape) — keep it isolated from the DB layer so it's directly unit-testable against fixture party arrays. (`com.howl.uwtracker.ingestion.RoleDerivation.resolveRoles`.)

**`role_hint`** (optional, per party member): the plugin sets this once a Ranger/Assassin-primary
member casts one of a fixed set of trapping skills — first-match-wins, value is `"t1"`/`"t2"`/`"t3"`
(lowercase on the wire; case-normalized to `T1`/`T2`/`T3` here), or `"unknown"` if that hasn't
happened yet.

**As of the multi-upload role_hint reconciliation change, a single upload's `role_hint` is only
ever trustworthy for the uploader's own character** — observing another real player's skill casts
only works within the local client's compass/network range, which isn't guaranteed once a party
spreads out (e.g. during pulls), so the plugin no longer even attempts to guess at anyone else's.
Before `resolveRoles` runs, `UploadRunService` calls `RoleDerivation.restrictHintsToSelf(party.character_name, party_members)`, which clears `role_hint` on every entry except the one whose `name` matches `party.character_name` — enforced server-side so an old or misbehaving client's leftover guess for someone else is never trusted, not just relied on as a client-side convention. A missing/unmatched `party.character_name` degrades safely to "trust nobody's hint this upload," same as any other unresolvable case below.

Within a single upload, resolution order is then:

1. **Hints first**: for any member with a valid, non-duplicate `role_hint` of `"t1"`/`"t2"`/`"t3"`,
   assign that label directly, wherever they actually sit in the party array. A missing `role_hint`
   or a literal `"unknown"` (case-insensitive) leaves that member's role unresolved (`null`) with no
   log — it's the plugin's normal not-yet-resolved state, not an error. Any other invalid value, or
   a duplicate claim of a label another member already took, is logged as a WARN and also treated as
   absent for that member. Given the self-only restriction above, in practice at most one member per
   upload can ever resolve this way.
2. **No positional fallback for T2/T3**: a member with no valid hint stays `null` rather than being
   guessed from its position (`0`/`1`/`2`) — Ranger/Assassin members are otherwise indistinguishable.
   `T2`/`T3` can only come from an explicit hint.
3. **T1 by elimination, within this upload only**: once both `T2` and `T3` have been assigned by
   hint *within this same upload* and exactly one Ranger/Assassin member is still unassigned, that
   member is labeled `T1` here. Since a single upload can supply at most one real hint now, this
   essentially never fires in practice — it's retained for the (now largely theoretical) case of a
   payload carrying more than one real hint. The meaningful elimination now happens across uploads,
   server-side — see "Cross-upload merging" below.
4. **Profession combo**: everything still unassigned (normally indices 3–7, plus any T1/T2/T3 slot
   left unresolved by steps 1–3) resolves as below.

For indices 3–7, match the (primary, secondary) pair — **ordered**, primary first, matching the "X/Y" GW1 community shorthand used in the requirements (primary `X`, secondary `Y`):

| Role | Combo (primary/secondary) |
|---|---|
| `T4` | Mesmer / Elementalist |
| `LT` | Mesmer / Assassin |
| `Emo` | Elementalist / Monk |
| `Derv` | Dervish / *(any secondary)* |
| `Spiker` | Mesmer / Ranger |
| `SoS` | Ritualist / Ranger |
| `Necro` | Necromancer / Ranger |
| `RangerNecro` | Ranger / Necromancer |

No match → `role = null`, log a WARN with the run context and the raw `(primary, secondary)` pair, but do **not** reject the upload.

**Assumption flagged in spec 00**: this ordered interpretation is inferred from the requirements' notation (`Derv = Dervish/anything` only makes sense as a wildcard on the *secondary* slot of an ordered pair) but hasn't been validated against real party data. If real uploads show roles resolving to `null` unexpectedly, check this first before assuming the algorithm is wrong.

**History**: Derv and Necro used to be folded into Spiker and SoS respectively (Dervish primaries counted as Spiker; Necromancer/Ranger counted as SoS) — split into their own roles in changelog 019, which also normalized the role codes from lowercase (`spiker`/`sos`/`emo`) to this Title-Case scheme.

**`RangerNecro`** (Ranger/Necromancer — the reverse combo of `Necro`) fills the same party niche as `Necro`, so it shares `Necro`'s `role_objectives` gating (spec 05) verbatim — seeded by changelog 020. Adjust independently later if RangerNecro's actual trial involvement turns out to differ.

### Cross-upload merging

If multiple members of the same party each run the plugin under their own machine key, the backend receives multiple independent uploads for what is logically the same run (correlated via the dedup match in step 7 above — already handles this, no separate correlation logic needed). Each upload only ever carries one member's self-reported hint (or none), so merging their roles correctly requires two behaviors in `UploadRunWriter`, beyond what a single call to `resolveRoles` can do:

1. **Never let "no data" erase a known role.** When attaching participants (step 8), a `null` role computed for *this* upload — meaning this upload had no reliable info about that member, not that their role is genuinely unknown — never overwrites an already-recorded role from an earlier upload. Only a non-null role overwrites: for a self-report that's the authoritative update; for a profession-combo role it's the same deterministic value every upload would compute anyway.
2. **Elimination across the accumulated roster.** After attaching this upload's participants, `UploadRunWriter.inferRemainingTrapperRoleByElimination` re-reads all of the run's Ranger/Assassin-combo participants from the DB (not just this upload's own data). If exactly two of `T1`/`T2`/`T3` are now known among them and exactly one such participant is still unassigned, that participant is labeled with whichever role is missing — the same "don't guess when ambiguous" guard as the in-upload elimination above, just operating on the run's current persisted state instead of one upload's in-memory array, and generalized to whichever of the three roles is missing (not just `T1`) since self-reporting means any of them could end up being the one nobody's uploaded for yet.

A run where only one of the three ever runs the plugin themselves keeps the other two `null` indefinitely — this degrades gracefully with adoption, from "only the uploader resolved" up to "all three resolved," never regressing.

## Response

- New run: `200 { "run_id": 123, "created": true }`
- Matched existing run (participants attached/updated): `200 { "run_id": 123, "created": false }`

## Error cases

| Status | Condition |
|---|---|
| 400 | Malformed JSON body |
| 400 | `party_members.length != 8` |
| 400 | Missing/null `objective` section |
| 401 | Missing/invalid/revoked `X-Machine-Key` |
| 500 | Unexpected failure (DB error, etc.) |
