MYSQL_HOST ?= localhost
MYSQL_PORT ?= 3306
MYSQL_DB ?= uwtracker
MYSQL_USER ?= uwtracker
MYSQL_PASSWORD ?= uwtracker
BACKEND_PORT ?= 8080
BACKEND_PID_FILE := .backend.pid
BACKEND_LOG_FILE := .backend.log

CHANGELOG := src/main/resources/db/changelog/db.changelog-master.xml
DRIVER_JAR := lib/mysql-connector-j.jar

.PHONY: mysql-up mysql-down mysql-logs mysql-wait driver migrate db-up \
	test-backend backend-up backend-down test-frontend

mysql-up:
	docker compose up -d mysql

mysql-down:
	docker compose down

mysql-logs:
	docker compose logs -f mysql

mysql-wait:
	@echo "Waiting for mysql to be healthy..."
	@until [ "$$(docker inspect -f '{{.State.Health.Status}}' uwtracker-mysql 2>/dev/null)" = "healthy" ]; do sleep 2; done
	@echo "mysql is healthy"

driver:
	@mkdir -p lib
	mvn -q dependency:copy \
		-Dartifact=com.mysql:mysql-connector-j:9.0.0 \
		-DoutputDirectory=lib \
		-Dmdep.stripVersion=true \
		-Dmdep.overWriteReleases=false \
		-Dmdep.overWriteSnapshots=false

migrate: driver
	liquibase \
		--changelog-file=$(CHANGELOG) \
		--url="jdbc:mysql://$(MYSQL_HOST):$(MYSQL_PORT)/$(MYSQL_DB)" \
		--username=$(MYSQL_USER) \
		--password=$(MYSQL_PASSWORD) \
		--classpath=$(DRIVER_JAR) \
		update

db-up: mysql-up mysql-wait migrate

# Backend integration tests (src/test/java/**/*IntegrationTest.java) run against a real MySQL via
# Testcontainers — self-contained, spins its own container up and down per run, no db-up needed.
test-backend:
	 ./mvnw test

# Starts the Spring Boot app in the background against docker-compose's mysql (db-up), for
# test-frontend below. Not meant to be left running — pair with backend-down.
backend-up: db-up
	@echo "Starting backend on :$(BACKEND_PORT) (log: $(BACKEND_LOG_FILE))..."
	mvn -q spring-boot:run > $(BACKEND_LOG_FILE) 2>&1 & \
	echo $$! > $(BACKEND_PID_FILE)
	@echo "Waiting for backend to respond..."
	@i=0; until curl -s -o /dev/null "http://localhost:$(BACKEND_PORT)/api/maps" || [ $$i -ge 60 ]; do sleep 2; i=$$((i+1)); done
	@curl -s -o /dev/null "http://localhost:$(BACKEND_PORT)/api/maps" || (echo "backend did not come up in time -- check $(BACKEND_LOG_FILE)" && exit 1)
	@echo "backend is ready"

backend-down:
	@if [ -f $(BACKEND_PID_FILE) ]; then \
		kill $$(cat $(BACKEND_PID_FILE)) 2>/dev/null || true; \
		rm -f $(BACKEND_PID_FILE); \
	fi

# Frontend Playwright e2e tests (frontend/e2e/*.spec.ts) against a real backend + MySQL. Playwright
# itself starts the Vite dev server (see frontend/playwright.config.ts); this target only has to
# bring up the backend it talks to, and tears it down afterward either way.
test-frontend: backend-up
	@cd frontend && npm run test:e2e; \
	status=$$?; \
	$(MAKE) backend-down; \
	exit $$status
