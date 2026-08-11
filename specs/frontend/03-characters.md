# 03 — Characters

Protected page, `/characters`. Pairs with `specs/backend/04-characters.md` (which gained a `GET /api/characters` "list mine" endpoint as part of this frontend pass — it wasn't in the original backend spec, but a management UI needs a way to see what already exists).

## List
Table from `GET /api/characters`: `character_name`, `default_role` (blank if unset), a "Remove" action per row.

## Add
A form: `character_name` (required text), `default_role` (optional dropdown of the 11 role codes — `T1`/`T2`/`T3`/`T4`/`LT`/`Spiker`/`Derv`/`SoS`/`Necro`/`RangerNecro`/`Emo`, hardcoded per `specs/frontend/00-overview.md`'s static-reference-data note) → `POST /api/characters`.
- `409` (name already registered — by this person or anyone else, since names are globally unique) shown inline on the name field.
- On success: refetch the list, clear the form.

## Remove
"Remove" per row → confirmation dialog (mention that past runs stay in history, just unlinked from the account — matches `specs/backend/04-characters.md`'s `ON DELETE SET NULL` behavior, worth surfacing so users aren't afraid removing a character deletes their run history) → `DELETE /api/characters/{id}` → refetch.
