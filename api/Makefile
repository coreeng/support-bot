SHELL := /bin/bash

.PHONY: help
help: ## Display this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ Development

.PHONY: db-up
db-up: ## Start PostgreSQL database container
	@docker compose -f service/docker-compose.yaml up -d db
	@echo "Waiting for PostgreSQL..."
	@until docker compose -f service/docker-compose.yaml exec -T db pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done
	@echo "PostgreSQL ready."

.PHONY: db-down
db-down: ## Stop PostgreSQL database container
	@docker compose -f service/docker-compose.yaml stop db

.PHONY: run-local
run-local: db-up ## Run API locally (starts DB, loads .env.local)
	@if [ -f .env.local ]; then \
		set -a && source .env.local && set +a && \
		cd service && ../gradlew bootRun; \
	else \
		echo "Error: .env.local not found. Copy from .env.example and update values."; \
		exit 1; \
	fi

.PHONY: build
build: ## Build the API
	cd service && ../gradlew build

.PHONY: test
test: ## Run tests
	cd service && ../gradlew test

.PHONY: codegen
codegen: ## Generate JOOQ classes
	cd service && ../gradlew jooqCodegen
