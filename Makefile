# Set tenant and app name
P2P_TENANT_NAME ?= support-bot
P2P_APP_NAME ?= support-bot

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
p2p-prod:          publish-prod        publish-chart                                             ## p2p release to production

##@ Lint targets

.PHONY: lint-app
lint-app: ## Lint app
	docker run --rm -i docker.io/hadolint/hadolint < api/Dockerfile
	docker run --rm -i docker.io/hadolint/hadolint < api/functional/Dockerfile
	docker run --rm -i docker.io/hadolint/hadolint < api/integration-tests/Dockerfile

##@ Build targets

.PHONY: build-app
build-app: lint-app ## Build app
	docker buildx build $(p2p_image_cache) --tag "$(p2p_image_tag)" --build-arg P2P_VERSION="$(p2p_version)" api

.PHONY: build-functional
build-functional: ## Build functional test docker image
	docker buildx build $(p2p_image_cache) --tag "$(p2p_image_tag)" --file api/functional/Dockerfile api

.PHONY: build-nft
build-nft:
	@echo "warning: $@ not implemented"

.PHONY: build-integration
build-integration:
	docker buildx build --platform linux/amd64 "$(p2p_image_cache)" --tag "$(p2p_image_tag)" --file api/integration-tests/Dockerfile api --load

.PHONY: build-extended-test
build-extended-test:
	@echo "NOOP"

##@ Push targets

.PHONY: push-app
push-app: ## Push app
	docker image push "$(p2p_image_tag)"

.PHONY: push-functional
push-functional: ## Push functional test docker image
	docker image push "$(p2p_image_tag)"

.PHONY: push-nft
push-nft: ## Push nft test docker image
	@echo "WARNING: $@ not implemented"

.PHONY: push-integration
push-integration: ## Push integration test docker image
	docker image push "$(p2p_image_tag)"

.PHONY: push-extended-test
push-extended-test: ## Uses promoted image (no push needed)
	@echo "NOOP"

##@ Deploy targets

.PHONY: deploy-integration
deploy-integration:
	echo "Service deployment is managed by tests"

.PHONY: deploy-nft
deploy-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: deploy-extended-test
deploy-extended-test: ## Deploy service and DB for extended test environment
	NAMESPACE="$(p2p_namespace)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	DB_RELEASE="$(p2p_app_name)-db" \
	SERVICE_RELEASE="$(p2p_app_name)" \
	ACTION=deploy \
	VALUES_FILE=api/k8s/service/values-extended-test.yaml \
	./api/scripts/deploy-service.sh

.PHONY: deploy-functional
deploy-functional: ## Deploy service and DB for functional tests, then run tests
	NAMESPACE="$(p2p_namespace)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	DB_RELEASE="$(p2p_app_name)-db" \
	SERVICE_RELEASE="$(p2p_app_name)" \
	ACTION=deploy \
	VALUES_FILE=api/k8s/service/values-functional.yaml \
	./api/scripts/deploy-service.sh
##@ Run targets

.PHONY: run-app
run-app: ## Run app
	@docker network inspect "$(p2p_app_name)" >/dev/null 2>&1 || docker network create "$(p2p_app_name)" >/dev/null
	docker run --rm --network "$(p2p_app_name)" --name "$(p2p_app_name)" \
		-p 8080:8080 \
		"$(p2p_image_tag)"

.PHONY: run-functional
run-functional:
	NAMESPACE="$(p2p_namespace)" \
	JOB_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)-functional" \
	IMAGE_TAG="$(p2p_version)" \
	DEPLOY_SERVICE=false \
	./api/scripts/run-functional-tests.sh

	NAMESPACE="$(p2p_namespace)" \
	SERVICE_RELEASE="$(p2p_app_name)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	DB_RELEASE="$(p2p_app_name)-db" \
	ACTION=delete \
	DELETE_DB=true \
	DEPLOY_DB=true \
	./api/scripts/deploy-service.sh

.PHONY: run-nft
run-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: run-integration
run-integration:
	NAMESPACE="$(p2p_namespace)" \
	JOB_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)-integration" \
	IMAGE_TAG="$(p2p_version)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	api/scripts/run-integration-tests.sh

.PHONY: run-extended-test
run-extended-test:
	@echo "WARNING: $@ not implemented"

##@ Publish target

.PHONY: publish-prod
publish-prod: ## Publish container image
	@printf "Login to ghcr.io... "
	@echo "$(GITHUB_TOKEN)" | skopeo login --username "$(or $(GITHUB_ACTOR),anonymous)" --password-stdin ghcr.io
	skopeo copy --all --preserve-digests "docker://$(p2p_registry)/$(p2p_app_name):$(p2p_version)" "docker://ghcr.io/coreeng/$(p2p_app_name):$(p2p_version)"

.PHONY: publish-chart
publish-chart: ## Package and publish Helm chart (version aligned to image)
	@echo "Packaging Helm chart with version $(p2p_version)..."
	@mkdir -p dist/charts
	helm package api/k8s/service \
	  --version "$(p2p_version)" \
	  --app-version "$(p2p_version)" \
	  --destination dist/charts
	@printf "Login to ghcr.io for Helm... "
	@echo "$(GITHUB_TOKEN)" | helm registry login ghcr.io \
	  --username "$(or $(GITHUB_ACTOR),anonymous)" --password-stdin
	# Push to GHCR as an OCI Helm chart. Result path: ghcr.io/coreeng/charts/support-bot:$(p2p_version)
	helm push "dist/charts/support-bot-$(p2p_version).tgz" oci://ghcr.io/coreeng/charts

##@ Monitoring

.PHONY: monitoring-deploy
monitoring-deploy: ## Deploy monitoring stack (Prometheus + Grafana) for support-bot
	helm repo add coreeng https://coreeng.github.io/core-platform-assets
	helm repo update
	helm upgrade --install support-bot-monitoring coreeng/monitoring-stack \
	  --version 0.1.5 \
	  --set tenantName=$${TENANT_NAME} \
	  --set internalServicesDomain=$${INTERNAL_SERVICES_DOMAIN} \
	  --set prometheus.ingress.enabled=true \
	  --set grafana.ingress.enabled=true \
	  $(if $(DRY_RUN),--dry-run --debug,)
	helm upgrade --install support-bot-dashboard ./api/k8s/dashboard \
	  $(if $(DRY_RUN),--dry-run --debug,)

