SHELL := /bin/bash

DEX_K8S := ../api/k8s/dex

##@ High level p2p targets
p2p-build:
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

##@ Dex module lifecycle (Helm / core-platform-app)

.PHONY: template
template: ## Validate Dex values by rendering core-platform-app chart
	helm repo add coreeng https://coreeng.github.io/core-platform-assets
	helm repo update
	helm template support-bot-dex coreeng/core-platform-app \
	  -f $(DEX_K8S)/values.yaml \
	  -f $(DEX_K8S)/values-integration.yaml >/dev/null

.PHONY: deploy-integration
deploy-integration: ## Deploy Dex module to integration environment
	helm repo add coreeng https://coreeng.github.io/core-platform-assets
	helm repo update
	helm upgrade --install support-bot-dex coreeng/core-platform-app \
	  -f $(DEX_K8S)/values.yaml \
	  -f $(DEX_K8S)/values-integration.yaml \
	  --set tenantName=$${TENANT_NAME} \
	  $(if $(DRY_RUN),--dry-run --debug,)

# .PHONY: deploy-prod
# deploy-prod: ## Deploy Dex module to production environment
# 	helm repo add coreeng https://coreeng.github.io/core-platform-assets
# 	helm repo update
# 	helm upgrade --install support-bot-dex coreeng/core-platform-app \
# 	  -f $(DEX_K8S)/values.yaml \
# 	  --set tenantName=$${TENANT_NAME} \
# 	  $(if $(DRY_RUN),--dry-run --debug,)
