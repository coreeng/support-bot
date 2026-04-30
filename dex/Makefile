SHELL := /bin/bash

DEX_K8S := ../api/k8s/dex

##@ High level p2p targets
p2p-build: template ## Fast feedback: validate Helm values
p2p-functional:    
p2p-nft:           
p2p-integration: template ## P2P: render then deploy (enables LDAP plaintext overlay for Dex integration)
	@$(MAKE) deploy-integration DEX_LDAP_ENABLED=true DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true
p2p-promote-to-extended-test: ## No image artifact to promote for Dex infra module
	@echo "NOOP: Dex uses upstream images and produces no P2P image artifact"
p2p-promote-to-prod: ## No image artifact to promote for Dex infra module
	@echo "NOOP: Dex uses upstream images and produces no P2P image artifact"
p2p-extended-test: 
p2p-prod:          

##@ Local Dex (Docker)

.PHONY: render-config
render-config: ## Render dex/config/config.yaml from dex/.env.local
	@test -f .env.local || (echo "Missing dex/.env.local (copy from dex/.env.example)" && exit 1)
	@python3 scripts/render_config.py
	@echo "Rendered dex/config/config.yaml"

.PHONY: run-local
run-local: render-config ## Render config and start local Dex
	@docker compose up -d

.PHONY: down-local
down-local: ## Stop local Dex
	@docker compose down

##@ Dex module lifecycle (Helm / dexidp dex chart)

DEX_CHART_VERSION ?= 0.24.0
NAMESPACE ?= support-bot-integration
DRY_RUN ?=

.PHONY: template
template: ## Validate Dex values by rendering dex/dex chart (charts.dexidp.io)
	@DEX_CHART_VERSION="$(DEX_CHART_VERSION)" bash ./scripts/helm_dex.sh template

.PHONY: deploy-integration
deploy-integration: ## Deploy Dex to integration (LDAP/Google/Microsoft via DEX_*_ENABLED in env or .env.local; plaintext LDAP requires DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true)
	@bash -c 'set -eo pipefail; cd "$(CURDIR)"; \
	if [[ -f .env.local ]]; then set -a; source .env.local; set +a; fi; \
	: $${DEX_LDAP_ENABLED:=$(DEX_LDAP_ENABLED)}; export DEX_LDAP_ENABLED; \
	: $${DEX_GOOGLE_ENABLED:=$(DEX_GOOGLE_ENABLED)}; export DEX_GOOGLE_ENABLED; \
	: $${DEX_MICROSOFT_ENABLED:=$(DEX_MICROSOFT_ENABLED)}; export DEX_MICROSOFT_ENABLED; \
	: $${DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT:=$(DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT)}; export DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT; \
	NAMESPACE="$(NAMESPACE)" DEX_CHART_VERSION="$(DEX_CHART_VERSION)" DRY_RUN="$(DRY_RUN)" \
	bash ./scripts/helm_dex.sh deploy-integration'

.PHONY: deploy-prod
deploy-prod: ## Deploy Dex to production (LDAP/Google/Microsoft via DEX_*_ENABLED in env or .env.local)
	@bash -c 'set -eo pipefail; cd "$(CURDIR)"; \
	if [[ -f .env.local ]]; then set -a; source .env.local; set +a; fi; \
	: $${DEX_LDAP_ENABLED:=$(DEX_LDAP_ENABLED)}; export DEX_LDAP_ENABLED; \
	: $${DEX_GOOGLE_ENABLED:=$(DEX_GOOGLE_ENABLED)}; export DEX_GOOGLE_ENABLED; \
	: $${DEX_MICROSOFT_ENABLED:=$(DEX_MICROSOFT_ENABLED)}; export DEX_MICROSOFT_ENABLED; \
	NAMESPACE="$(NAMESPACE)" DEX_CHART_VERSION="$(DEX_CHART_VERSION)" bash ./scripts/helm_dex.sh deploy-prod'
