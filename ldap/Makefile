SHELL := /bin/bash

LDAP_K8S := ../api/k8s/ldap

##@ High level p2p targets
p2p-build: template ## Fast feedback: validate Helm values
p2p-functional:
p2p-nft:
p2p-integration: template ## P2P: render then deploy (sets LDAP_DEPLOY_INSECURE_PLAINTEXT for plaintext overlay)
	@$(MAKE) deploy-integration LDAP_DEPLOY_INSECURE_PLAINTEXT=true
p2p-promote-to-extended-test: ## Delegate image promotion to repo root
	@$(MAKE) -C .. p2p-promote-to-extended-test
p2p-extended-test:
p2p-prod:

##@ Local LDAP (Docker)

.PHONY: apply-bootstrap-users-ldif
apply-bootstrap-users-ldif: ## Require LDAP_BOOTSTRAP_USER_PASSWORD and write bootstrap/20-users.ldif
	@bash -c 'set -euo pipefail; cd "$(CURDIR)"; \
		if [[ -z "$${LDAP_BOOTSTRAP_USER_PASSWORD:-}" ]]; then \
			echo "error: LDAP_BOOTSTRAP_USER_PASSWORD is required (local: ldap/.env.local; CI: secret LDAP_BOOTSTRAP_USER_PASSWORD / P2P env_vars — see ldap/README.md)." >&2; \
			exit 1; \
		fi; \
		bash scripts/render_bootstrap_users_ldif.sh'

.PHONY: run-local
run-local: ## Start OpenLDAP + phpLDAPadmin (requires ldap/.env.local); loads .env.example then .env.local so LDAP_BOOTSTRAP_USER_PASSWORD has a default
	@test -f .env.local || (echo "Missing ldap/.env.local — copy from ldap/.env.example (see ldap/README.md)." >&2 && exit 1)
	@cd "$(CURDIR)" && set -euo pipefail && set -a && if [[ -f .env.example ]]; then . ./.env.example; fi && . ./.env.local && set +a && $(MAKE) apply-bootstrap-users-ldif
	@cd "$(CURDIR)" && docker compose up -d

.PHONY: down-local
down-local: ## Stop local LDAP stack
	@docker compose down

##@ LDAP module lifecycle (Helm / Bitnami OpenLDAP image)

.PHONY: render-bootstrap-users
render-bootstrap-users: apply-bootstrap-users-ldif ## Generate bootstrap/20-users.ldif (requires LDAP_BOOTSTRAP_USER_PASSWORD in environment)

.PHONY: template
template: ## Validate LDAP chart (bitnami + integration + ephemeral plaintext overlay for render)
	@bash ./scripts/helm_ldap.sh template

NAMESPACE ?= support-bot-integration
DRY_RUN ?=

.PHONY: deploy-integration
deploy-integration: ## Deploy LDAP to integration (requires LDAP_DEPLOY_INSECURE_PLAINTEXT=true unless root Makefile sets it)
	@bash -c 'set -eo pipefail; cd "$(CURDIR)"; \
		if [[ -f .env.local ]]; then set -a; source .env.local; set +a; fi; \
		NAMESPACE="$(NAMESPACE)" DRY_RUN="$(DRY_RUN)" \
		LDAP_DEPLOY_INSECURE_PLAINTEXT="$(LDAP_DEPLOY_INSECURE_PLAINTEXT)" \
		bash ./scripts/helm_ldap.sh deploy-integration'

.PHONY: sync-bootstrap-into-chart
sync-bootstrap-into-chart: ## Render 20-users.ldif then copy ldap/bootstrap/*.ldif into api/k8s/ldap/chart/files/bootstrap/
	@$(MAKE) render-bootstrap-users
	@cp -f "$(CURDIR)/bootstrap/"*.ldif "$(CURDIR)/../api/k8s/ldap/chart/files/bootstrap/"
	@echo "Synced LDIF into api/k8s/ldap/chart/files/bootstrap/ (commit 10-ou / 30-groups changes only; 20-users.ldif is gitignored)."
