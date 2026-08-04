# 06 — Run History

Website-only, session auth required (see [03-auth](03-auth.md)).

## `GET /api/runs`
All filters optional and combinable (AND semantics). Offset-paginated per [00-overview](00-overview.md)'s convention.

Query params:
| Param | Meaning |
|---|---|
| `person` | person id — any run where at least one of this person's linked characters participated |
| `character` | character id — any run that specific character participated in |
| `role` | one of the 8 role codes |
| `map` | map id |
| `from`, `to` | date range on `runs.utc_start` (inclusive) |
| `completed` | `true`/`false` |
| `page`, `size` | pagination |

Implementation: `Specification<Run>` (Spring Data JPA `JpaSpecificationExecutor`) building predicates only for params actually present, joined through `run_participants`/`characters` when `person`/`character`/`role` are used. Avoids a combinatorial explosion of hand-written query methods for every filter combination.

Response:
```json
{
  "items": [
    {
      "run_id": 1,
      "map_id": 234,
      "map_name": "...",
      "utc_start": "...",
      "end_reason": "wipe",
      "completed": false,
      "duration_ms": null,
      "participant_count": 8
    }
  ],
  "page": 0, "size": 25, "totalElements": 1, "totalPages": 1
}
```

Deliberately a summary shape — no objectives/participants inline, to keep list responses light. Full detail is a separate call:

## `GET /api/runs/{id}`
```json
{
  "run_id": 1,
  "map_id": 234,
  "map_name": "...",
  "utc_start": "...",
  "instance_start": "...",
  "end_reason": "wipe",
  "completed": false,
  "duration_ms": null,
  "objectives": [
    { "sequence": 0, "name": "Escort", "status": 2, "start_ms": 0, "done_ms": 45000, "duration_ms": 45000 }
  ],
  "participants": [
    { "party_index": 0, "raw_name": "...", "character_id": null, "character_name": null, "primary_profession": "Ranger", "secondary_profession": "Assassin", "role": "T1" }
  ]
}
```
Not found → `404`.
