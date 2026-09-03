# 07 — Deployment (GCP Cloud Run + Cloud SQL)

## Dockerfile (multi-stage)

1. **Node stage**: build the React app (`npm ci && npm run build`), producing a static bundle.
2. **Maven stage**: copy the React build output into `src/main/resources/static/` *before* `mvn package`, so Spring Boot serves it as static content from the fat jar — one artifact, no separate frontend server or nginx sidecar. (The React bundle is the *only* thing in `static/` now — the plugin binary is fetched from a GCS bucket at runtime, see "Plugin artifacts" below.)
3. **Runtime stage**: minimal JRE base image (matching the Java 25 build), copy only the built jar from stage 2, `ENTRYPOINT ["java", "-jar", "app.jar"]`.

Container listens on `8080` (matches `server.port`, spec 00's repo layout).

## Cloud Run service

- **Session storage vs. autoscaling** (cross-ref [03-auth](03-auth.md)): v1 runs with `min-instances=1` and no autoscale-out, since sessions are in-memory per-instance. Don't raise `max-instances` above 1 without first switching to Spring Session JDBC — silently doing so would cause random logouts as requests bounce between instances.
- Environment: `SPRING_PROFILES_ACTIVE=prod` activates `application-prod.properties`.

## Cloud SQL connection

Use the Cloud Run's native Cloud SQL integration (`--add-cloudsql-instances` / the Cloud SQL connection field in the console) rather than a public IP + allowlist. This mounts a unix socket at `/cloudsql/<INSTANCE_CONNECTION_NAME>`; the app connects via the Cloud SQL MySQL Socket Factory:

`pom.xml`: `com.google.cloud.sql:mysql-socket-factory-connector-j-8`

`application-prod.properties`:
```properties
spring.datasource.url=jdbc:mysql:///${DB_NAME}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME}&socketFactory=com.google.cloud.sql.mysql.SocketFactory
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```
This is the GCP-recommended pattern for Cloud Run + Cloud SQL — avoids managing IP allowlists or SSL certs by hand, and the connector handles credential rotation/mTLS transparently.

## Secrets

`DB_USERNAME`/`DB_PASSWORD` (and `INSTANCE_CONNECTION_NAME`/`DB_NAME` if not baked into the image) come from Secret Manager, injected via Cloud Run's `--set-secrets` at deploy time — never baked into the image or committed to the repo.

## Plugin artifacts (GCS bucket)

`SCTracker.dll` + `SCTracker.version.json` are **not** in the image. The app fetches them from a private GCS bucket at runtime (`com.howl.uwtracker.plugin.GcsPluginStorageClient`), caches for `plugin.storage.cache-ttl` (default 1h), streams the dll at `GET /SCTracker.dll`, and re-detects `plugin_dll_version` when the manifest `sha256` changes — so a plugin-only update needs no backend redeploy.

- **Env var**: `PLUGIN_STORAGE_BUCKET=<bucket>` on the Cloud Run service (`gcloud run services update uwtracker --region us-central1 --update-env-vars PLUGIN_STORAGE_BUCKET=<bucket>`). Blank/unset → plugin storage disabled: `/plugin-version` and `/SCTracker.dll` 503, version enforcement fails open. Optional overrides: `plugin.storage.manifest-object`, `plugin.storage.dll-object`, `plugin.storage.cache-ttl`.
- **IAM**: the Cloud Run runtime service account needs `roles/storage.objectViewer` on the bucket (a 403 in the logs = this is missing). Auth is ADC via the metadata server — no key file.
- **Publisher**: GWToolboxpp CI (`cmake.yml`) uploads to `gs://<bucket>/sctracker/` on every `master` build, authenticated with a dedicated SA JSON key (`GCP_SA_KEY` repo secret) that has `roles/storage.objectAdmin` on the bucket only.

## Module & launcher artifacts (GCS bucket)

The ProjectPotato launcher's artifacts are hosted the same way — **same bucket** (`PLUGIN_STORAGE_BUCKET`), **same runtime IAM** (`roles/storage.objectViewer`), no new env var. See [08-module-entitlements](08-module-entitlements.md) for the API. Object layout:

- `plugins/<Name>/<Name>.dll` + `plugins/<Name>/<Name>.version.json` — one prefix per GWToolboxpp plugin.
- `launcher/PP.exe`, `launcher/PP.dll`, `launcher/PP.version.json` — ProjectPotato launcher + base.
- `sctracker/SCTracker.dll` + `sctracker/SCTracker.version.json` — unchanged (legacy layout).

`ModuleManifestCache` reads each `*.version.json` (`plugin.storage.module-cache-ttl`, default 15m) and streams the artifact bytes per request at `GET /modules/{key}/download` — bytes are never held in memory. Optional overrides: `plugin.storage.module-cache-ttl`, `plugin.storage.max-module-download-bytes` (default 64 MiB).

- **Publishers**:
  - GWToolboxpp `cmake.yml` — the existing SCTracker step still writes `sctracker/`; a second step loops over every other staged plugin and writes `plugins/<Name>/`. Same `GCP_SA_KEY` / `roles/storage.objectAdmin`.
  - The ProjectPotato launcher repo publishes `launcher/PP.exe` / `PP.dll` (+ a `PluginVersionMetadata`-shaped `PP.version.json`) with its own CI step or a one-off `gcloud storage cp`; its SA needs `roles/storage.objectAdmin` on the bucket (or reuse `GCP_SA_KEY`).
- The backend tolerates a missing object (503 on that one download) so publishing is decoupled from any backend deploy.

## Migrations on boot

`spring.liquibase.enabled=true` — the app applies pending changesets on every startup. Liquibase's `DATABASECHANGELOGLOCK` table serializes concurrent instance startups safely (only one instance actually runs the migration; others wait), so this is safe even during a Cloud Run rolling deploy. Runbook note: if an instance crashes mid-migration and leaves the lock held, it needs a manual `liquibase releaseLocks` (via the same `make migrate`-style CLI invocation pointed at prod, spec 01) before the next deploy can proceed — not automated, just documented here so it's not a mystery when it happens.

## Local vs. prod config

- `application.properties` — local defaults, MySQL via `docker-compose.yml` on `localhost:3306`.
- `application-prod.properties` — Cloud SQL socket URL, `cookie.secure=true` (spec 03), activated via `SPRING_PROFILES_ACTIVE=prod`.
