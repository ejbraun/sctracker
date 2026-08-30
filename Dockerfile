# See the deploy plan for the full GCP provisioning steps this image feeds into (Cloud Run,
# Artifact Registry, Cloud SQL via the mysql-socket-factory dependency in pom.xml).

# --- frontend ---
FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- backend ---
FROM eclipse-temurin:25-jdk-jammy AS backend-build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Invoked via `sh` rather than `./mvnw` because the executable bit doesn't survive a Windows
# git checkout, so `./mvnw` fails with "Permission denied" in the Linux build container.
RUN sh mvnw -q -DskipTests dependency:go-offline
COPY src/ src/
# The React build is the sole source of src/main/resources/static/ now — the SCTracker plugin
# binary is no longer committed here; the running app fetches it from a GCS bucket
# (com.howl.uwtracker.plugin.GcsPluginStorageClient). COPY creates static/ if absent.
COPY --from=frontend-build /app/dist/ src/main/resources/static/
RUN sh mvnw -q -DskipTests package

# --- runtime ---
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
