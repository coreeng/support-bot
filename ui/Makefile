SHELL := /bin/bash

.PHONY: help
help: ## Display this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ Development

# Marker file to track when yarn install was last run
.yarn-install: package.json yarn.lock
	yarn install
	@touch .yarn-install

.PHONY: install
install: .yarn-install ## Install dependencies (only if needed)

.PHONY: run-local
run-local: install ## Run UI dev server locally
	yarn dev

.PHONY: build
build: install ## Build for production
	yarn build

.PHONY: lint
lint: ## Run linter
	yarn lint

.PHONY: test
test: ## Run tests
	yarn test
