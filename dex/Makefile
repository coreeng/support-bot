SHELL := /bin/bash

DEX_K8S := ../api/k8s/dex

##@ High level p2p targets
p2p-build: template ## Fast feedback: validate Helm values
p2p-functional:    
p2p-nft:           
p2p-integration:   template deploy-integration 
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

.PHONY: template
template: ## Validate Dex values by rendering dex/dex chart (charts.dexidp.io)
	@DEX_CHART_VERSION="$(DEX_CHART_VERSION)" bash ./scripts/helm_dex.sh template

.PHONY: deploy-integration
deploy-integration: ## Deploy Dex module to integration environment
	@DEX_CHART_VERSION="$(DEX_CHART_VERSION)" DRY_RUN="$(DRY_RUN)" bash ./scripts/helm_dex.sh deploy-integration

.PHONY: deploy-integration-oidc
deploy-integration-oidc: ## Deploy Dex with OIDC in-cluster overlay (Tier 2 integration tests)
	@DEX_CHART_VERSION="$(DEX_CHART_VERSION)" DRY_RUN="$(DRY_RUN)" bash ./scripts/helm_dex.sh deploy-integration-oidc

# .PHONY: deploy-prod
# deploy-prod: ## Deploy Dex module to production environment
# 	@DEX_CHART_VERSION="$(DEX_CHART_VERSION)" bash ./scripts/helm_dex.sh deploy-prod
