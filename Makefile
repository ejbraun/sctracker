MYSQL_HOST ?= localhost
MYSQL_PORT ?= 3306
MYSQL_DB ?= uwtracker
MYSQL_USER ?= uwtracker
MYSQL_PASSWORD ?= uwtracker

CHANGELOG := db/changelog/db.changelog-master.xml
DRIVER_JAR := lib/mysql-connector-j.jar

.PHONY: mysql-up mysql-down mysql-logs mysql-wait driver migrate db-up

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
