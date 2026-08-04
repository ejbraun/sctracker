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

**`objectives[]` has one field not in the original draft**: `indent` (integer, nesting depth — always `0` in samples seen so far). Stored on `run_objectives` for fidelity; not yet used by anything.

**Timestamp format — confirmed against the real sample above** (superseding the original draft's "assume epoch milliseconds for everything"):
- `party.utc_start` and `objective.utc_start` are `time(nullptr)` — real wall-clock epoch **seconds**. Parse via `Instant.ofEpochSecond`, not `ofEpochMilli`.
- `objective.instance_start` is **not a timestamp** — a `std::chrono::steady_clock`/load-screen millisecond counter, zeroed at an arbitrary point tied to system boot. Not comparable across runs or machines, no absolute meaning. Store the raw number (`runs.instance_start_ms`, a plain `BIGINT`); never attempt to convert it to a date.
- `objectives[].start`/`done`/`duration` and the top-level `objective.duration` are milliseconds *relative to* `instance_start` — genuinely millisecond values, just not epoch-anchored. Storage is unaffected (still a raw `BIGINT` millisecond value); only the sentinel mapping and null-handling matter here, not a unit conversion.

## Processing pipeline

Executed in a single transaction per request.

1. **Parse** the JSON body. Malformed body → `400 { "error": "malformed request body" }`.
2. **Validate party size**: `party.party_members.length == 8`, else log `{machine_key_id, map_id, party_size}` at WARN and return `400 { "error": "party size must be 8" }`. This is the *only* validation failure that rejects the upload — everything else below degrades gracefully (nulls, unresolved roles) rather than failing the request.
3. **Sentinel mapping**: replace `4294967295` with `null` in every numeric field independently — each objective's `start`/`done`/`duration`, and the top-level `objective.duration`.
4. **Derive completion**: `completed = objectives.length > 0 && objectives[last].status == 2`. `end_reason` is stored as metadata only and never consulted for this.
5. **Resolve the map row**: `INSERT IGNORE INTO maps (id) VALUES (?)` — no-ops if already present, otherwise creates it with `name = NULL`; then `UPDATE maps SET name = ? WHERE id = ? AND name IS NULL` using `objective.name`, so the zone name populates automatically the first time a map is seen instead of needing manual backfill.
6. **Dedup / find-or-create the run**, guarded by a MySQL named lock scoped to the map (`GET_LOCK(CONCAT('run-dedup:map:', map_id), 10)` … `RELEASE_LOCK(...)`) so concurrent uploads from different party members' clients for the same run can't race into two `runs` rows. The lock is per-map rather than per-time-bucket: a time-bucketed key would need consistent bucket boundaries and two clients whose `utc_start` straddle a boundary could still land in different buckets and race. Per-map is coarser but fully correct, and fine at this traffic volume (a small guild, a handful of concurrent uploads at most).
   - Query: `SELECT id FROM runs WHERE map_id = ? AND utc_start BETWEEN ? - INTERVAL 5 SECOND AND ? + INTERVAL 5 SECOND ORDER BY ABS(TIMESTAMPDIFF(MICROSECOND, utc_start, ?)) LIMIT 1`
   - **Found** → reuse that `run_id`. Do **not** overwrite `end_reason`/`completed`/`duration_ms`/objectives on the existing row — first upload for a run wins on run-level fields; later uploads only attach participants (step 7). This is a first-writer-wins policy; flagged as a default, not something the spec text mandated explicitly.
   - **Not found** → `INSERT INTO runs (map_id, utc_start, instance_start_ms, objective_start, end_reason, completed, duration_ms) VALUES (...)`, then insert all rows into `run_objectives` (one per array element, `sequence` = array index, plus `indent`).
7. **Attach participants**: for each of the `party_members[]` entries (regardless of whether the run was found or newly created):
   - Resolve `character_id` via `SELECT id FROM characters WHERE character_name = ?` (exact match against `raw_name`).
   - Derive `role` (algorithm below).
   - `INSERT INTO run_participants (run_id, character_id, raw_name, primary_profession_id, secondary_profession_id, role, party_index, is_player, is_hero, is_henchman) VALUES (...) ON DUPLICATE KEY UPDATE character_id = VALUES(character_id), primary_profession_id = VALUES(primary_profession_id), secondary_profession_id = VALUES(secondary_profession_id), role = VALUES(role), is_player = VALUES(is_player), is_hero = VALUES(is_hero), is_henchman = VALUES(is_henchman)` keyed on `(run_id, raw_name)`.
   - This makes resends idempotent (no duplicate rows) while still letting a resend correct stale data (e.g. a character registered after the first upload, or a role derivation bugfix re-ingested) rather than being a pure no-op.
8. Commit; release the named lock.

## Role derivation

Pure function, `resolveRoles(partyMembers[8]) -> String[8]` (or an equivalent array-in/array-out shape) — keep it isolated from the DB layer so it's directly unit-testable against fixture party arrays.

```
role[0] = "T1"
role[1] = "T2"
role[2] = "T3"
```
These three are positional, not profession-based — they share the same profession combo (Ranger/Assassin) and can't be told apart any other way.

For indices 3–7, match the (primary, secondary) pair — **ordered**, primary first, matching the "X/Y" GW1 community shorthand used in the requirements (primary `X`, secondary `Y`):

| Role | Combo (primary/secondary) |
|---|---|
| `T4` | Elementalist / Mesmer |
| `LT` | Mesmer / Assassin |
| `emo` | Elementalist / Monk |
| `spiker` | Dervish / *(any secondary)* **or** Mesmer / Ranger |
| `sos` | Ritualist / Ranger **or** Necromancer / Ranger |

No match → `role = null`, log a WARN with the run context and the raw `(primary, secondary)` pair, but do **not** reject the upload.

**Assumption flagged in spec 00**: this ordered interpretation is inferred from the requirements' notation (`spiker = Dervish/anything` only makes sense as a wildcard on the *secondary* slot of an ordered pair) but hasn't been validated against real party data. If real uploads show roles resolving to `null` unexpectedly, check this first before assuming the algorithm is wrong.

## Response

- New run: `200 { "run_id": 123, "created": true }`
- Matched existing run (participants attached/updated): `200 { "run_id": 123, "created": false }`

## Error cases

| Status | Condition |
|---|---|
| 400 | Malformed JSON body |
| 400 | `party_members.length != 8` |
| 401 | Missing/invalid/revoked `X-Machine-Key` |
| 500 | Unexpected failure (DB error, etc.) |
