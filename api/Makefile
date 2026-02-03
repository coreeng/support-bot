SHELL := /bin/bash

.PHONY: help
help: ## Display this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ Development

.PHONY: run-local
run-local: install ## Run API locally with .env.local loaded (installs deps first)
	@if [ -f .env.local ]; then \
		set -a && source .env.local && set +a && \
		cd service && ../gradlew bootRun; \
	else \
		echo "Error: .env.local not found. Copy from .envrc.example and update values."; \
		exit 1; \
	fi

.PHONY: install
install: ## Download dependencies
	cd service && ../gradlew dependencies --quiet

.PHONY: build
build: ## Build the API
	cd service && ../gradlew build

.PHONY: test
test: ## Run tests
	cd service && ../gradlew test

.PHONY: codegen
codegen: ## Generate JOOQ classes
	cd service && ../gradlew jooqCodegen
