# 05 — Leaderboards

Website-only, session auth required (see [03-auth](03-auth.md)). All endpoints scoped to a single map.

## Completion semantics (recap from spec 02)

- **Full-run** bests only ever consider `runs.completed = true`.
- **Section** (per-objective) bests consider *any* run regardless of `completed` — a wipe doesn't erase the time already posted on objectives that were finished before the wipe.

## `GET /api/leaderboards/maps/{mapId}/overall`
Top N (default 10, `?limit=`) fastest completed full runs for the map.

```sql
SELECT id, duration_ms, utc_start
FROM runs
WHERE map_id = ? AND completed = TRUE
ORDER BY duration_ms ASC
LIMIT ?
```
Backed by `idx_runs_map_completed` (spec 01); if this shows up slow in practice, extend it to a covering `(map_id, completed, duration_ms)` index rather than adding a new one preemptively.

Response:
```json
{ "items": [ { "run_id": 1, "duration_ms": 123456, "utc_start": "...", "participants": [ { "raw_name": "...", "character_name": null, "role": "T1" } ] } ] }
```
Participants are pulled per run via `run_participants` joined to `characters` (nullable name if unlinked).

## `GET /api/leaderboards/maps/{mapId}/sections/{objectiveName}`
Top N fastest times for a single named objective, across all runs for that map (wiped or not).

```sql
SELECT ro.run_id, ro.duration_ms, r.utc_start
FROM run_objectives ro
JOIN runs r ON r.id = ro.run_id
WHERE r.map_id = ? AND ro.name = ? AND ro.duration_ms IS NOT NULL
ORDER BY ro.duration_ms ASC
LIMIT ?
```

## `GET /api/leaderboards/me/maps/{mapId}/overall`
The logged-in person's PB full-run time for the map, aggregated across every character they've linked (`characters.person_id = session personId`).

```sql
SELECT MIN(r.duration_ms)
FROM runs r
JOIN run_participants rp ON rp.run_id = r.id
JOIN characters c ON c.id = rp.character_id
WHERE c.person_id = ? AND r.map_id = ? AND r.completed = TRUE
```
`null` result → `204` (no PB yet) rather than a fabricated `0`.

## `GET /api/leaderboards/me/maps/{mapId}/sections/{objectiveName}`
Same idea, section-level, any run — but **role-gated**: a participant's objective time only counts toward their personal best if their role in that run was actually involved in that objective, per the static `role_objectives` mapping (spec 01). Without this, e.g. a `spiker` would get credit for an `Escort` time they had no part in, just for having been in the party.

```sql
SELECT MIN(ro.duration_ms)
FROM run_objectives ro
JOIN run_participants rp ON rp.run_id = ro.run_id
JOIN characters c ON c.id = rp.character_id
JOIN role_objectives rol ON rol.map_id = ? AND rol.objective_name = ro.name AND rol.role = rp.role
WHERE c.person_id = ? AND ro.name = ?
  AND ro.run_id IN (SELECT id FROM runs WHERE map_id = ?)
```

The extra `role_objectives` join is exactly what makes this role-gated: a participant only contributes a row if `(map_id, objective_name, their role)` has an entry in the mapping. A participant with `role = NULL` (unresolved combo, spec 02) never matches anything here — correct, since we don't know what they were doing.

**Operational note**: until a `(map_id, objective_name)` pair has at least one `role_objectives` row (spec 01 — this table is seeded manually, not derived from uploads), this query returns no PB for *anyone* on that objective, even people who legitimately did it. That's a rollout gap to fill in per-dungeon, not a bug.

**Not applied to the non-personal "Overall" section endpoint above** — that endpoint reports `run_objectives.duration_ms`, a property of the run/objective itself, not something attributed to a specific participant's role, so there's no ambiguity to gate there.

## Future: caching

These are aggregate queries over a growing `runs`/`run_objectives` table — a natural spot for an in-memory cache (e.g. Caffeine) keyed on `(mapId)` / `(mapId, objectiveName)` / `(personId, mapId)`, invalidated on new-run insert in spec 02's ingestion path. **Not building this now** — flagged per the user's mention of wanting an in-memory cache eventually; revisit once real query volume/latency justifies it.
