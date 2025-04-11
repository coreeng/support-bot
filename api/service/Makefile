SHELL := /bin/bash

.PHONY: codegen
codegen:
	../gradlew jooqCodegen

.PHONY: build
build: codegen
	../gradlew build

.PHONY: build-continuously
build-continuously: codegen
	../gradlew build --continuously

.PHONY: run
run: codegen
	../gradlew bootRun

.PHONY: lint
lint:
	../gradlew pmdMain pmdTest

.PHONY: test
test: codegen
	../gradlew test

# Run tests + all the other checks (like linting)
.PHONY: check
check:
	../gradlew check

TAG=latest
IMAGE_NAME=ghcr.io/coreeng/support-bot
PUBLISH=false
# Specify next variables in case PUBLISH=true
USERNAME=
PASSWORD=
.ONESHELL: docker-build
.PHONY: docker-build
docker-build:
	@export password="$(PASSWORD)"
	@export args="-DimageName=$(IMAGE_NAME):$(TAG)"
	@if [[ "$(PUBLISH)" == "true" ]]; then \
		args+=" --publishImage -Dusername=$(USERNAME) -Dpassword=$${password}"; \
	fi
	@../gradlew bootBuildImage $${args}


.PHONY: db-run
db-run:
	docker compose up db

.PHONY: db-clean
db-clean:
	rm -rf ./db-data

