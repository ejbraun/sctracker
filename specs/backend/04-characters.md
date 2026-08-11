# 04 — Characters

Website-only, session auth required (see [03-auth](03-auth.md)).

## `GET /api/characters`
Lists the logged-in person's own characters: `200 [ { "id": 1, "character_name": "...", "default_role": "..." } ]`. No cross-account listing in v1 — this is "my characters" only, not a guild-wide roster browser. Backs the character management page in `specs/frontend/03-characters.md`.

## `POST /api/characters`
Request: `{ "character_name": "string", "default_role": "string (optional)" }`
- `character_name` must be unique across the whole `characters` table (GW1 names are globally unique — see [01-schema-and-migrations](01-schema-and-migrations.md)). Taken → `409 { "error": "character already registered" }`.
- `default_role`, if provided, must be one of the 11 known role codes (`T1`/`T2`/`T3`/`T4`/`LT`/`Spiker`/`Derv`/`SoS`/`Necro`/`RangerNecro`/`Emo`) → `400` otherwise. It's advisory metadata only, never consulted by ingestion's role derivation (spec 02).
- Owned by the logged-in person: `person_id` = session `personId`.
- On success: `201 { "id": 1, "character_name": "...", "default_role": "...", "person_id": 1 }`.
- **Retroactive backfill**: after inserting, run `UPDATE run_participants SET character_id = ? WHERE character_id IS NULL AND raw_name = ?` — links this character to any past `/upload-run` participant rows that were ingested before the character existed (backed by `idx_run_participants_raw_name` from spec 01). Recommended default so leaderboards/history immediately reflect prior runs; flagged as a judgment call since the requirements didn't specify this explicitly — the alternative is leaving historical rows unlinked until the next time that run happens to be re-uploaded (unlikely to ever happen).

## `DELETE /api/characters/{id}`
- Must be owned by the requester (`characters.person_id == session personId`), else `403`. Not found → `404`.
- Hard delete. `run_participants.character_id` referencing it is set to `NULL` automatically (`ON DELETE SET NULL`, spec 01) — historical run/leaderboard data is preserved, just unlinked from any account.
- `204` on success.

## Not in scope for v1
- No rename/edit endpoint (`default_role` can only be set at creation). Add a `PATCH /api/characters/{id}` later if the requirements grow to need it — not building it speculatively now.
- No admin override to move a character between accounts (relevant if a guild member's character was registered under the wrong account, or by someone else on their behalf) — flag as a manual DB fix for now, revisit if it comes up often.
