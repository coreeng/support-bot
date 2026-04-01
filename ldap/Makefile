SHELL := /bin/bash

LDAP_K8S := ../api/k8s/ldap

##@ High level p2p targets
p2p-build: template ## Fast feedback: validate Helm values
p2p-functional:
p2p-nft:
p2p-integration: template deploy-integration
p2p-extended-test:
p2p-prod:

##@ Local LDAP (Docker)

.PHONY: run-local
run-local: ## Start OpenLDAP + phpLDAPadmin (requires ldap/.env.local)
	@test -f .env.local || (echo "Missing ldap/.env.local (copy from ldap/.env.example)" && exit 1)
	@docker compose up -d

.PHONY: down-local
down-local: ## Stop local LDAP stack
	@docker compose down

##@ LDAP module lifecycle (Helm / Bitnami OpenLDAP image)

.PHONY: template
template: ## Validate LDAP chart (support-bot-openldap) with values-bitnami + integration overlay
	@bash ./scripts/helm_ldap.sh template

.PHONY: deploy-integration
deploy-integration: ## Deploy LDAP module to integration environment
	@DRY_RUN="$(DRY_RUN)" bash ./scripts/helm_ldap.sh deploy-integration

.PHONY: sync-bootstrap-into-chart
sync-bootstrap-into-chart: ## Copy ldap/bootstrap/*.ldif into api/k8s/ldap/chart/files/bootstrap/
	@cp -f "$(CURDIR)/bootstrap/"*.ldif "$(CURDIR)/../api/k8s/ldap/chart/files/bootstrap/"
	@echo "Synced LDIF into api/k8s/ldap/chart/files/bootstrap/ (commit if changed)."
