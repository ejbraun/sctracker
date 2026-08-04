# 05 — Run History

Protected pages. Pairs with `specs/backend/06-run-history.md`.

## `/runs`
Filter bar driving `GET /api/runs` query params (all optional, combinable — see backend spec):
- `map` — dropdown from `GET /api/maps`
- `role` — dropdown of the 8 static role codes
- `person` / `character` — text/typeahead (exact scope TBD when there's a search-by-name endpoint to back it; a plain id input is an acceptable fallback if not)
- `from` / `to` — date inputs
- `completed` — tri-state checkbox (any / completed only / wiped only)

Results: paginated table (`run_id` → link, map name, date, completed/wipe badge, duration `mm:ss` or `—` if not completed, participant count) with prev/next bound to the response envelope's `page`/`totalPages` (`specs/backend/00-overview.md` pagination convention). Filter changes reset to `page=0`.

## `/runs/:id`
`GET /api/runs/{id}`:
- Header: map name, date, completed/wipe badge, total duration.
- Objectives table, in `sequence` order: name, status (icon: done/in-progress/not-reached), start/done/duration formatted `mm:ss` (blank for `null`, i.e. sentinel-mapped "not reached" values from spec 02).
- Participants table, in `party_index` order: name (linked character name if `character_id` is set, else `raw_name` styled as unlinked), profession combo (e.g. "Ranger / Assassin"), role badge (or "unresolved" styling if `role` is `null`, per the role-derivation spec's unmatched-combo case).

Not found → a 404 empty state, not a crash.
