# 03 — Auth (signup, login, session, machine keys)

Website-only. Session-cookie auth plane (see [00-overview](00-overview.md)). No full Spring Security — hand-rolled `HttpSession` + an interceptor, `BCryptPasswordEncoder` from `spring-security-crypto` used standalone for hashing.

## Dependencies

Add `spring-security-crypto` to `pom.xml` (just the crypto module, not `spring-boot-starter-security` — we don't want the auto-configured security filter chain, login page, etc.).

## Endpoints

### `POST /api/signup`
Request: `{ "username": "string", "password": "string" }`
- `username` unique (case-sensitive exact match against `people.username`) → `409 { "error": "username taken" }` if not.
- `password` minimum 8 characters (default; adjust if the team wants a different bar) → `400` otherwise.
- On success: hash with `BCryptPasswordEncoder`, insert into `people`, start a session (see below), `201 { "id": 1, "username": "..." }`.

### `POST /api/login`
Request: `{ "username": "string", "password": "string" }`
- Look up by username, `BCryptPasswordEncoder.matches(...)`. No match → `401 { "error": "invalid credentials" }` (same message whether the username doesn't exist or the password is wrong — don't leak which).
- On success: start a session, `200 { "id": 1, "username": "..." }`.

### `POST /api/logout`
- Invalidates the current `HttpSession`. `200`.

### `GET /api/account/me`
- `200 { "id": 1, "username": "..." }` for the logged-in person, `401` if no active session.

## Session mechanics

- On successful signup/login: `request.getSession(true).setAttribute("personId", person.getId())`.
- `SessionAuthInterceptor implements HandlerInterceptor`: in `preHandle`, read `session.getAttribute("personId")`; if absent, `401 { "error": "not authenticated" }`. Registered on `/api/**` except `/api/signup` and `/api/login`. `/upload-run` is authenticated separately via machine key (spec 02) and isn't under `/api/**` at all, so this interceptor never runs on it; static resources (`/`, `/static/**`, `/index.html`, React Router fallback paths) are likewise outside `/api/**` and untouched by it (see [00-overview](00-overview.md)'s `/api` prefix & SPA fallback note).
- Downstream controllers read the current person via a small helper (e.g. `@ModelAttribute` or a `CurrentPerson` argument resolver reading the same session attribute) rather than re-parsing the session manually in every method.

## Cookie configuration

`application.properties`:
```properties
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict
server.servlet.session.timeout=7d
```
`server.servlet.session.cookie.secure=true` in `application-prod.properties` only (local dev over plain HTTP would otherwise silently drop the cookie).

## Open question: session storage across Cloud Run instances

Spring's default `HttpSession` is in-memory, per-instance. Cloud Run can run multiple instances behind a load balancer with no session affinity, which would make a user's session invisible to whichever instance handles their next request. Two options:

1. **`min-instances=1`, no autoscale beyond it** (v1 recommendation) — sidesteps the problem entirely at the cost of not scaling out. Fine for a guild-sized tool. Revisit if traffic ever justifies more than one instance.
2. **Spring Session JDBC**, backed by the same MySQL database — makes sessions instance-agnostic, enables normal autoscaling, but adds a dependency and a `SPRING_SESSION` schema/table to manage.

Cross-referenced in [07-deployment](07-deployment.md). **Needs explicit sign-off before enabling autoscaling** — don't silently pick option 2 later without updating this spec.

## Machine keys (self-service)

### `POST /api/account/machine-keys`
Request: `{ "label": "string (optional)" }`
- Generate 32 random bytes (`SecureRandom`), base64url-encode → the raw key.
- Store `SHA-256(rawKey)` hex digest in `machine_keys.key_hash`, tied to the logged-in `person_id`.
- Response (raw key shown **exactly once**, never retrievable again): `201 { "id": 1, "key": "<raw key>", "label": "..." }`

### `GET /api/account/machine-keys`
- `200 [ { "id": 1, "label": "...", "created_at": "...", "revoked_at": null }, ... ]` — never includes the raw key or the hash.

### `DELETE /api/account/machine-keys/{id}`
- Must belong to the requester (`machine_keys.person_id == session personId`), else `403`. Not found → `404`.
- Sets `revoked_at = NOW()` (soft revoke, not a hard delete — preserves the audit trail for past uploads). `204`.
