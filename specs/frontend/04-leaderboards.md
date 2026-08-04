# 04 — Leaderboards

Protected pages. Pairs with `specs/backend/05-leaderboards.md`.

## `/` — Dashboard / map picker
Fetches `GET /api/maps` (`specs/backend/00-overview.md`) and renders a searchable dropdown/list. Selecting a map navigates to `/leaderboards/:mapId`. Maps with a `null` name (not yet backfilled, per `specs/backend/01-schema-and-migrations.md`) display as their raw numeric id rather than being hidden — they're still valid runs, just not pretty-named yet.

## `/leaderboards/:mapId`
Three sections:

**Overall** — `GET /api/leaderboards/maps/{mapId}/overall`, a ranked table: rank, duration formatted `mm:ss`, run date, participant roster (raw/character name + role badge per slot).

**Your best** — `GET /api/leaderboards/me/maps/{mapId}/overall`. A `204` (no PB yet) renders an explicit empty state ("no completed run yet"), never a fabricated `0:00`.

Note for the section personal-bests specifically: the backend role-gates these against a `role_objectives` mapping (`specs/backend/05-leaderboards.md`) that has to be seeded per map/objective before it returns anything — a `204` there can mean either "no PB yet" or "mapping not seeded for this objective." The API can't currently distinguish the two, so don't read a section `204` as proof the mapping is broken.

**Sections** — a sub-tab or expandable row per objective name. The set of objective names for a map isn't statically known (it comes from whatever `run_objectives` rows exist for that map) — pull the distinct names from the most recently viewed run's detail response as a starting point, or add a small "distinct objective names for this map" backend lookup if that proves awkward in practice; flagging as an implementation detail to settle when building rather than over-specifying now. Each section shows `GET /api/leaderboards/maps/{mapId}/sections/{name}` and the personal equivalent (`GET /api/leaderboards/me/maps/{mapId}/sections/{name}`) side by side, same empty-state handling as Overall.
