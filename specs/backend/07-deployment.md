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

`SCTracker.dll` + `SCTracker.version.json` are **not** in the image. The app fetches them from a private GCS bucket at runtime (`com.howl.uwtracker.plugin.GcsPluginStorageClient`), caches for `plugin.storage.cache-ttl` (default 1h), and streams the dll at `GET /SCTracker.dll` — so a plugin-only update needs no backend redeploy. The manifest's `version` drives the 426 upload gate and the website's "new plugin version available" banner; both pick up a new build within the cache TTL.

- **Env var**: `PLUGIN_STORAGE_BUCKET=<bucket>` on the Cloud Run service (`gcloud run services update uwtracker --region us-central1 --update-env-vars PLUGIN_STORAGE_BUCKET=<bucket>`). Blank/unset → plugin storage disabled: `/plugin-version` and `/SCTracker.dll` 503, version enforcement fails open. Optional overrides: `plugin.storage.manifest-object`, `plugin.storage.dll-object`, `plugin.storage.cache-ttl`.
- **IAM**: the Cloud Run runtime service account needs `roles/storage.objectViewer` on the bucket (a 403 in the logs = this is missing). Auth is ADC via the metadata server — no key file.
- **Publisher**: GWToolboxpp CI (`cmake.yml`) uploads to `gs://<bucket>/plugins/SCTracker/` on every `master` build, authenticated with a dedicated SA JSON key (`GCP_SA_KEY` repo secret) that has `roles/storage.objectAdmin` on the bucket only. (SCTracker used the flat `gs://<bucket>/sctracker/` path before the `plugins/<Name>/` migration — see below.)

## Module artifacts (GCS bucket)

The registry-driven module artifacts (SCTracker, the GWToolbox++ plugins the GW Launcher Reforged (GWRL) launcher manages, and GWRL's own components) are hosted the same way as the bundled SCTracker plugin above — **same bucket** (`PLUGIN_STORAGE_BUCKET`), **same runtime IAM** (`roles/storage.objectViewer`), no new env var. See [08-module-entitlements](08-module-entitlements.md) for the API. **One object-layout rule:**

- `plugins/<Name>/<Name>.dll` + `plugins/<Name>/<Name>.version.json` — one prefix per Toolbox plugin, e.g. `plugins/SCTracker/…`, `plugins/PP-Vanquish/…`. The toolbox build itself rides this layout too: `plugins/GWToolboxdll/GWToolboxdll.dll` (+ manifest), served as a public plugin under key `gwtoolbox`; its manifest has no integer `version`, so the backend reports `version 0` for it (cosmetic — the account page hides a non-positive version).
- `launcher/<Name>/<Name>.{zip,exe,dll}` + `launcher/<Name>/<Name>.version.json` — one prefix per launcher component (`type: module`), e.g. `launcher/gwrl-install/…`, `launcher/gwrl-base/…`. Published by the launcher repo's own CI, same SA / IAM.

`ModuleManifestCache` reads each `*.version.json` (`plugin.storage.module-cache-ttl`, default 15m) and streams the artifact bytes per request at `GET /modules/{key}/download` — bytes are never held in memory. Optional overrides: `plugin.storage.module-cache-ttl`, `plugin.storage.max-module-download-bytes` (default 64 MiB).

- **Publishers**: GWToolboxpp `cmake.yml` — one loop over every staged plugin `.dll`, each to `gs://<bucket>/plugins/<Name>/`, then a step that publishes `GWToolboxdll.dll` + a generated manifest to `gs://<bucket>/plugins/GWToolboxdll/`. Same `GCP_SA_KEY` / `roles/storage.objectAdmin`. Any other pipeline building a plugin DLL (or the launcher repo publishing to `gs://<bucket>/launcher/<Name>/`) follows the same path + IAM. An admin then registers each as a module — or hits **Scan bucket** on the Modules page, which lists unregistered `plugins/<Name>/` and `launcher/<Name>/` folders for one-click import.
- The backend tolerates a missing object (503 on that one download) so publishing is decoupled from any backend deploy.

### One-time SCTracker `sctracker/` → `plugins/SCTracker/` migration

`PluginStorageProperties` defaults and the `sctracker` row in `modules` (changeset 046) both point at
`plugins/SCTracker/` now. Before the backend carrying that change is deployed, the bytes must exist
there or `GET /SCTracker.dll` 503s until they do (fail-open — the plugin keeps its current dll and
re-checks next launch). Either:

1. Merge the GWToolboxpp `cmake.yml` change and let one `master` build run (it writes the new path), **or**
2. Copy the objects once:
   ```
   gcloud storage cp gs://$BUCKET/sctracker/SCTracker.dll         gs://$BUCKET/plugins/SCTracker/SCTracker.dll
   gcloud storage cp gs://$BUCKET/sctracker/SCTracker.version.json gs://$BUCKET/plugins/SCTracker/SCTracker.version.json
   ```

If the Cloud Run service sets `PLUGIN_STORAGE_DLL_OBJECT` / `PLUGIN_STORAGE_MANIFEST_OBJECT`
explicitly (it currently doesn't — it relies on the code defaults), update or unset them too. After
the cutover, delete the dead `gs://$BUCKET/sctracker/` objects.

## Migrations on boot

`spring.liquibase.enabled=true` — the app applies pending changesets on every startup. Liquibase's `DATABASECHANGELOGLOCK` table serializes concurrent instance startups safely (only one instance actually runs the migration; others wait), so this is safe even during a Cloud Run rolling deploy. Runbook note: if an instance crashes mid-migration and leaves the lock held, it needs a manual `liquibase releaseLocks` (via the same `make migrate`-style CLI invocation pointed at prod, spec 01) before the next deploy can proceed — not automated, just documented here so it's not a mystery when it happens.

## Local vs. prod config

- `application.properties` — local defaults, MySQL via `docker-compose.yml` on `localhost:3306`.
- `application-prod.properties` — Cloud SQL socket URL, `cookie.secure=true` (spec 03), activated via `SPRING_PROFILES_ACTIVE=prod`.
