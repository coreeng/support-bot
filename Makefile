# Set tenant and app name
P2P_TENANT_NAME ?= support-bot
P2P_APP_NAME ?= support-bot

P2P_IMAGE_NAMES := $(P2P_APP_NAME) $(P2P_APP_NAME)-ui

# Download and include p2p makefile
$(shell curl -fsSL "https://raw.githubusercontent.com/coreeng/p2p/v1/p2p.mk" -o ".p2p.mk")
include .p2p.mk

##@ General

# The help target prints out all targets with their descriptions organized
# beneath their categories. The categories are represented by '##@' and the
# target descriptions by '##'. The awk commands is responsible for reading the
# entire set of makefiles included in this invocation, looking for lines of the
# file as xyz: ## something, and then pretty-format the target and help. Then,
# if there's a line with ##@ something, that gets pretty-printed as a category.
# More info on the usage of ANSI control characters for terminal formatting:
# https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_parameters
# More info on the awk command:
# http://linuxcommand.org/lc3_adv_awk.php

.PHONY: help
help: ## Display this help.
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+%?:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)


##@ High level p2p targets
p2p-build:         build-app           push-app                                                  ## Build support-bot
p2p-functional:    build-functional    push-functional    deploy-functional    run-functional    ## p2p functional tests
p2p-nft:           build-nft           push-nft           deploy-nft           run-nft           ## p2p nft tests
p2p-integration:   build-integration   push-integration   deploy-integration   run-integration   ## p2p integration tests
p2p-extended-test: build-extended-test push-extended-test deploy-extended-test run-extended-test ## p2p extended tests
p2p-prod:          publish-prod                           deploy-prod                            ## p2p release to production


##@ Lint targets

.PHONY: lint-api-app
lint-api-app: ## Lint api app
	docker run --rm -i docker.io/hadolint/hadolint < support-bot-api/Dockerfile
	docker run --rm -i docker.io/hadolint/hadolint < support-bot-api/functional/Dockerfile

.PHONY: lint-ui-app
lint-ui-app: ## Lint ui app
	docker run --rm -i docker.io/hadolint/hadolint < support-bot-ui/Dockerfile

.PHONY: lint-app
lint-app: lint-api-app lint-ui-app ## Lint api & ui app


##@ Build targets

.PHONY: build-api-app
build-api-app: lint-api-app ## Build api app
	docker buildx build $(p2p_image_cache) --tag "$(p2p_image_tag)" --build-arg P2P_VERSION="$(p2p_version)" support-bot-api

.PHONY: build-ui-app
build-ui-app: lint-ui-app ## Build ui app
	docker buildx build $(call p2p_image_cache,$(p2p_app_name)-ui) --tag "$(call p2p_image_tag,$(p2p_app_name)-ui)" --build-arg P2P_VERSION="$(p2p_version)" support-bot-ui

.PHONY: build-app
build-app: build-api-app build-ui-app ## Build api & ui apps



.PHONY: build-api-functional
build-api-functional: ## Build api functional test docker image
	docker buildx build $(p2p_image_cache) --tag "$(p2p_image_tag)" --file support-bot-api/functional/Dockerfile support-bot-api

.PHONY: build-ui-functional
build-ui-functional: ## Build ui functional test frontend plugin
	@echo "WARNING: $@ not implemented"

.PHONY: build-functional
build-functional: build-api-functional build-ui-functional ## Build functional tests

.phony: build-nft
build-nft:
	@echo "warning: $@ not implemented"

.phony: build-integration
build-integration:
	@echo "warning: $@ not implemented"

.PHONY: build-extended-test
build-extended-test:
	@echo "WARNING: $@ not implemented"


##@ Push targets

.PHONY: push-api-app
push-api-app: ## Push api app
	docker image push "$(p2p_image_tag)"

.PHONY: push-ui-app
push-ui-app: ## Push ui app
	docker image push "$(call p2p_image_tag,$(p2p_app_name)-ui)"

.PHONY: push-app ## Push api & ui apps
push-app: push-api-app push-ui-app

.PHONY: push-api-functional
push-api-functional: ## Push api functional test docker image
	docker image push "$(p2p_image_tag)"

.PHONY: push-ui-functional
push-ui-functional: ## Push ui functional test frontend plugin
	@echo "WARNING: $@ not implemented"

.PHONY: push-functional
push-functional: push-api-functional push-ui-functional ## Push functional tests images

.PHONY: push-api-nft
push-api-nft: ## Push api nft test docker image
	@echo "WARNING: $@ not implemented"

.PHONY: push-ui-nft
push-ui-nft: ## Push ui nft test frontend plugin
	@echo "WARNING: $@ not implemented"

.PHONY: push-nft
push-nft: push-api-nft push-ui-nft ## Push nft tests images
	@echo "WARNING: $@ not implemented"

.PHONY: push-api-integration
push-api-integration: ## Push api integration test docker image
	@echo "WARNING: $@ not implemented"

.PHONY: push-ui-integration
push-ui-integration: ## Push ui integration test frontend plugin
	@echo "WARNING: $@ not implemented"

.PHONY: push-integration
push-integration: push-api-integration push-ui-integration ## Push integration tests images
	@echo "WARNING: $@ not implemented"

.PHONY: push-api-extended-test
push-api-extended-test: ## Push api extended-test test docker image
	@echo "WARNING: $@ not implemented"

.PHONY: push-ui-extended-test
push-ui-extended-test: ## Push ui extended-test test frontend plugin
	@echo "WARNING: $@ not implemented"

.PHONY: push-extended-test
push-extended-test: push-api-extended-test push-ui-extended-test ## Push extended-test tests images
	@echo "WARNING: $@ not implemented"

##@ Deploy targets

.PHONY: deploy-nft
deploy-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: deploy-extended-test
deploy-extended-test:
	@echo "WARNING: $@ not implemented"

.PHONY: deploy-%
deploy-%: ## Deploy mathing target `deploy-%`
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm upgrade --install "$(p2p_app_name)-db" bitnami/postgresql -n "$(p2p_namespace)" \
		--set global.postgresql.auth.postgresPassword=rootpassword \
		--set global.postgresql.auth.username=supportbot \
		--set global.postgresql.auth.password=supportbotpassword \
		--set global.postgresql.auth.database=supportbot \
		--set primary.pdb.create=false
	helm repo add core-platform-assets https://coreeng.github.io/core-platform-assets
	helm upgrade --install "$(p2p_app_name)" core-platform-assets/core-platform-app -n "$(p2p_namespace)" \
		-f support-bot-api/helm-values.yaml \
		--set nameOverride="$(p2p_app_name)" \
		--set tenantName="$(p2p_tenant_name)" \
		--set image.repository="$(p2p_registry)/$(p2p_app_name)" \
		--set image.tag="$(p2p_version)" \
		--set ingress.appUrlSuffix="$(p2p_app_url_suffix)" \
		--set ingress.domain="$(INTERNAL_SERVICES_DOMAIN)" \
		--set serviceAccount.annotations.iam\\.gke\\.io/gcp-service-account="$(p2p_tenant_name)-ca@$(PROJECT_ID).iam.gserviceaccount.com" \
		--set envVarsMap.DB_URL="jdbc:postgresql://$(p2p_app_name)-db-postgresql.$(p2p_namespace).svc.cluster.local:5432/supportbot" \
		--set envVarsMap.DB_USERNAME="supportbot" \
		--set envVarsMap.DB_PASSWORD="supportbotpassword" \
		--set envVarsMap.SLACK_TOKEN="$${SUPPORT_BOT_SLACK_TOKEN}" \
		--set envVarsMap.SLACK_SOCKET_TOKEN="$${SUPPORT_BOT_SLACK_SOCKET_TOKEN}" \
		--set envVarsMap.SLACK_SIGNING_SECRET="$${SUPPORT_BOT_SLACK_SIGNING_SECRET}" \
		--set envVarsMap.SLACK_TICKET_CHANNEL_ID="$${SUPPORT_BOT_SLACK_TICKET_CHANNEL_ID}" \
		--set envVarsMap.SLACK_ESCALATION_CHANNEL_ID="$${SUPPORT_BOT_SLACK_ESCALATION_CHANNEL_ID}"
	helm upgrade --install "$(p2p_app_name)-ui" core-platform-assets/core-platform-app -n "$(p2p_namespace)" \
		-f support-bot-ui/helm-values.yaml \
		--set nameOverride="$(p2p_app_name)-ui" \
		--set tenantName="$(p2p_tenant_name)" \
		--set image.repository="$(p2p_registry)/$(p2p_app_name)-ui" \
		--set image.tag="$(p2p_version)" \
		--set ingress.appUrlSuffix="$(p2p_app_url_suffix)" \
		--set ingress.domain="$(INTERNAL_SERVICES_DOMAIN)"


##@ Run targets

.PHONY: run-api-app
run-api-app: ## Run api app
	docker run --rm -P --name "$(p2p_app_name)" "$(p2p_image_tag)"

.PHONY: run-ui-app
run-ui-app: ## Run ui app
	docker run --rm -P --name "$(p2p_app_name)-ui" "$(call p2p_image_tag,$(p2p_app_name)-ui)"

.PHONY: run-app ## Run api & ui apps
run-app:
	@echo "WARNING: $@ not implemented"


.PHONY: run-api-functional
run-api-functional: ## run api functional test
	cd support-bot-api; bash scripts/helm-test.sh functional "$(p2p_namespace)" "$(p2p_app_name)" true

.PHONY: run-ui-functional
run-ui-functional: ## run ui functional test
	@echo "WARNING: $@ not implemented"

.PHONY: run-functional
run-functional: run-api-functional run-ui-functional ## Run functional tests
	@echo "WARNING: $@ WIP"


.PHONY: run-nft
run-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: run-integration
run-integration:
	@echo "WARNING: $@ not implemented"

.PHONY: run-extended-test
run-extended-test:
	@echo "WARNING: $@ not implemented"


##@ Publish targets

.PHONY: publish-api-prod
publish-api-prod: ## Publish api container image
	@printf "Login to ghcr.io... "
	@echo "$(GITHUB_TOKEN)" | skopeo login --username "$(or $(GITHUB_ACTOR),anonymous)" --password-stdin ghcr.io
	skopeo copy --all --preserve-digests "docker://$(p2p_registry)/$(p2p_app_name):$(p2p_version)" "docker://ghcr.io/coreeng/$(p2p_app_name):$(p2p_version)"

.PHONY: publish-ui-prod
publish-ui-prod: ## Publish ui npm package
	@npm set "//npm.pkg.github.com/:_authToken=$(GITHUB_TOKEN)"
	@npm set "@coreeng:registry=https://npm.pkg.github.com/"
	docker create --name plugin-container "$(p2p_registry)/$(p2p_app_name)-ui:$(p2p_version)"
	docker cp plugin-container:/app/coreeng-$(p2p_app_name).tgz ./coreeng-$(p2p_app_name).tgz
	docker rm plugin-container
	@published_shasum=`npm view @coreeng/$(p2p_app_name)@$(p2p_version) dist.shasum 2>/dev/null || echo none` ; \
	if [ "$$published_shasum" = "none" ] ; then \
		echo "Publishing npm package $(p2p_version)" ; \
		npm publish coreeng-$(p2p_app_name).tgz ; \
	else \
		local_shasum=`shasum coreeng-$(p2p_app_name).tgz | awk '{print $$1}'` ; \
		if [ "$$published_shasum" = "$$local_shasum" ] ; then \
			echo "Publishing npm package $(p2p_version) skipped as it already exists with same shasum" ; \
		else \
			echo "Publishing npm package $(p2p_version) failed as it already exists with a different shasum" ; \
			exit 1 ; \
		fi ; \
	fi
	@printf "Login to ghcr.io... "
	@echo "$(GITHUB_TOKEN)" | skopeo login --username "$(or $(GITHUB_ACTOR),anonymous)" --password-stdin ghcr.io
	skopeo copy --all --preserve-digests "docker://$(p2p_registry)/$(p2p_app_name):$(p2p_version)" "docker://ghcr.io/coreeng/$(p2p_app_name)-ui:$(p2p_version)"

.PHONY: publish-prod
publish-prod: publish-api-prod publish-ui-prod ## Publish api & ui artifacts
