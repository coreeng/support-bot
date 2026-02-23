# Set tenant and app name
P2P_TENANT_NAME ?= support-bot
P2P_APP_NAME ?= support-bot

P2P_IMAGE_NAMES := $(P2P_APP_NAME) $(P2P_APP_NAME)-ui $(P2P_APP_NAME)-ui-functional $(P2P_APP_NAME)-ui-nft

# Download and include p2p makefile
$(shell curl -fsSL "https://raw.githubusercontent.com/coreeng/p2p/v1/p2p.mk" -o ".p2p.mk")
include .p2p.mk

##@ Local Development

.PHONY: run-local
run-local: ## Run both API and UI locally (starts DB, Ctrl+C to stop)
	@echo "Starting PostgreSQL..."
	@docker compose -f api/service/docker-compose.yaml up -d db
	@echo "Waiting for PostgreSQL to be ready..."
	@until docker compose -f api/service/docker-compose.yaml exec -T db pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done
	@echo "PostgreSQL ready. Starting services..."
	@trap 'kill 0' EXIT; \
	(cd api && make run-local 2>&1 | sed 's/^/[API] /') & \
	(cd ui && make run-local 2>&1 | sed 's/^/[UI]  /') & \
	wait

.PHONY: run-local-api
run-local-api: ## Run only API locally (starts DB)
	cd api && make run-local

.PHONY: run-local-ui
run-local-ui: ## Run only UI locally
	cd ui && make run-local

.PHONY: db-up
db-up: ## Start PostgreSQL database container
	@docker compose -f api/service/docker-compose.yaml up -d db
	@echo "Waiting for PostgreSQL..."
	@until docker compose -f api/service/docker-compose.yaml exec -T db pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done
	@echo "PostgreSQL ready."

.PHONY: db-down
db-down: ## Stop PostgreSQL database container
	@docker compose -f api/service/docker-compose.yaml stop db

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

.PHONY: lint-api
lint-api: ## Lint API Dockerfiles
	docker run --rm -i docker.io/hadolint/hadolint < api/Dockerfile
	docker run --rm -i docker.io/hadolint/hadolint < api/functional/Dockerfile
	docker run --rm -i docker.io/hadolint/hadolint < api/integration-tests/Dockerfile
	docker run --rm -i docker.io/hadolint/hadolint < api/nft/Dockerfile

.PHONY: lint-ui
lint-ui: ## Lint UI Dockerfiles
	docker run --rm -i docker.io/hadolint/hadolint < ui/Dockerfile

.PHONY: lint-app
lint-app: lint-api lint-ui ## Lint all Dockerfiles

##@ Build targets

.PHONY: build-api-app
build-api-app: lint-api ## Build API
	docker buildx build $(p2p_image_cache) --tag "$(p2p_image_tag)" --build-arg P2P_VERSION="$(p2p_version)" api

.PHONY: build-ui-app
build-ui-app: lint-ui ## Build UI
	docker buildx build $(call p2p_image_cache,$(p2p_app_name)-ui) --tag "$(call p2p_image_tag,$(p2p_app_name)-ui)" --build-arg P2P_VERSION="$(p2p_version)" ui

.PHONY: build-app
build-app: build-api-app build-ui-app ## Build all apps

.PHONY: build-api-functional
build-api-functional: ## Build API functional test docker image
	docker buildx build $(p2p_image_cache) --tag "$(p2p_image_tag)" --file api/functional/Dockerfile api

.PHONY: build-ui-functional
build-ui-functional: ## Build UI functional test docker image
	docker buildx build $(call p2p_image_cache,$(p2p_app_name)-ui) --tag "$(call p2p_image_tag,$(p2p_app_name)-ui)" ui/p2p/tests/functional/

.PHONY: build-functional
build-functional: build-api-functional build-ui-functional ## Build functional test docker images

.PHONY: build-api-nft
build-api-nft: ## Build API nft test docker image
	docker buildx build $(p2p_image_cache) --tag "$(p2p_image_tag)" --file api/nft/Dockerfile api

.PHONY: build-ui-nft
build-ui-nft: ## Build UI nft test docker image
	docker buildx build $(call p2p_image_cache,$(p2p_app_name)-ui) --tag "$(call p2p_image_tag,$(p2p_app_name)-ui)" ui/p2p/tests/nft/

.PHONY: build-nft
build-nft: build-api-nft build-ui-nft ## Build nft test docker images

.PHONY: build-integration
build-integration:
	docker buildx build --platform linux/amd64 "$(p2p_image_cache)" --tag "$(p2p_image_tag)" --file api/integration-tests/Dockerfile api --load

.PHONY: build-extended-test
build-extended-test:
	@echo "NOOP"

##@ Push targets

.PHONY: push-api-app
push-api-app: ## Push API app
	docker image push "$(p2p_image_tag)"

.PHONY: push-ui-app
push-ui-app: ## Push UI app
	docker image push "$(call p2p_image_tag,$(p2p_app_name)-ui)"

.PHONY: push-app
push-app: push-api-app push-ui-app ## Push all apps

.PHONY: push-api-functional
push-api-functional: ## Push API functional test docker image
	docker image push "$(p2p_image_tag)"

.PHONY: push-ui-functional
push-ui-functional: ## Push UI functional test docker image
	docker image push "$(call p2p_image_tag,$(p2p_app_name)-ui)"

.PHONY: push-functional
push-functional: push-api-functional push-ui-functional ## Push functional test docker images

.PHONY: push-api-nft
push-api-nft: ## Push API nft test docker image
	docker image push "$(p2p_image_tag)"

.PHONY: push-ui-nft
push-ui-nft: ## Push UI nft test docker image
	docker image push "$(call p2p_image_tag,$(p2p_app_name)-ui)"

.PHONY: push-nft
push-nft: push-api-nft push-ui-nft ## Push nft test docker images

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

.PHONY: deploy-api-nft
deploy-api-nft: ## Deploy service and DB for nft tests
	NAMESPACE="$(p2p_namespace)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	DB_RELEASE="$(p2p_app_name)-db" \
	SERVICE_RELEASE="$(p2p_app_name)" \
	ACTION=deploy \
	VALUES_FILE=api/k8s/service/values-nft.yaml \
	./api/scripts/deploy-service.sh

.PHONY: deploy-api-extended-test
deploy-api-extended-test: ## Deploy service and DB for extended test environment
	NAMESPACE="$(p2p_namespace)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	DB_RELEASE="$(p2p_app_name)-db" \
	SERVICE_RELEASE="$(p2p_app_name)" \
	ACTION=deploy \
	VALUES_FILE=api/k8s/service/values-extended-test.yaml \
	./api/scripts/deploy-service.sh

.PHONY: deploy-api-functional
deploy-api-functional: ## Deploy service and DB for functional tests, then run tests
	NAMESPACE="$(p2p_namespace)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	DB_RELEASE="$(p2p_app_name)-db" \
	SERVICE_RELEASE="$(p2p_app_name)" \
	ACTION=deploy \
	VALUES_FILE=api/k8s/service/values-functional.yaml \
	./api/scripts/deploy-service.sh

.PHONY: deploy-ui-%
deploy-ui-%: ## Add UI to existing API deployment
	helm upgrade --install "$(p2p_app_name)" ./api/k8s/service -n "$(p2p_namespace)" \
		-f api/k8s/service/values-$*.yaml \
		-f ui/p2p/config/common.yaml \
		-f ui/p2p/config/$*.yaml \
		--set image.repository="$(p2p_registry)/$(p2p_app_name)" \
		--set image.tag="$(p2p_version)" \
		--set ui.image.repository="$(p2p_registry)/$(p2p_app_name)-ui" \
		--set ui.image.tag="$(p2p_version)" \
		--set "ui.ingress.hosts[0].host=$(p2p_app_name)-ui$(p2p_app_url_suffix).$(BASE_DOMAIN)" \
		--atomic \
		--timeout 10m

.PHONY: deploy-functional
deploy-functional: deploy-api-functional deploy-ui-functional

.PHONY: deploy-nft
deploy-nft: deploy-api-nft deploy-ui-nft

.PHONY: deploy-extended-test
deploy-extended-test: deploy-api-extended-test deploy-ui-extended-test
##@ Run targets

.PHONY: run-api-app
run-api-app: ## Run API app
	@docker network inspect "$(p2p_app_name)" >/dev/null 2>&1 || docker network create "$(p2p_app_name)" >/dev/null
	docker run --rm --network "$(p2p_app_name)" --name "$(p2p_app_name)" \
		-p 8080:8080 \
		"$(p2p_image_tag)"

.PHONY: run-ui-app
run-ui-app: ## Run UI app
	@docker network inspect "$(p2p_app_name)" >/dev/null 2>&1 || docker network create "$(p2p_app_name)" >/dev/null
	docker run --rm --network "$(p2p_app_name)" --name "$(p2p_app_name)-ui" \
		-p 3000:3000 \
		"$(call p2p_image_tag,$(p2p_app_name)-ui)"

.PHONY: run-app
run-app: ## Run app
	@echo "WARNING: use run-api-app or run-ui-app"

.PHONY: run-api-functional
run-api-functional:
	NAMESPACE="$(p2p_namespace)" \
	JOB_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)-functional" \
	IMAGE_TAG="$(p2p_version)" \
	DEPLOY_SERVICE=false \
	./api/scripts/run-functional-tests.sh

.PHONY: run-ui-functional
run-ui-functional:
	bash ui/p2p/scripts/helm-test.sh functional "$(p2p_namespace)" "$(p2p_app_name)" false "30m" "$(p2p_app_name)-ui-functional-test"

.PHONY: run-functional
run-functional: run-ui-functional run-api-functional

.PHONY: run-api-nft
run-api-nft:
	NAMESPACE="$(p2p_namespace)" \
	JOB_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)-nft" \
	IMAGE_TAG="$(p2p_version)" \
	SERVICE_IMAGE_REPOSITORY="$(p2p_registry)/$(p2p_app_name)" \
	SERVICE_IMAGE_TAG="$(p2p_version)" \
	DEPLOY_SERVICE=false \
	./api/scripts/run-nft-tests.sh

.PHONY: run-ui-nft
run-ui-nft:
	bash ui/p2p/scripts/helm-test.sh nft "$(p2p_namespace)" "$(p2p_app_name)" false "30m" "$(p2p_app_name)-ui-nft-test"

.PHONY: run-nft
run-nft: run-ui-nft run-api-nft

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

.PHONY: login-ghcr
login-ghcr:
	@printf "Login to ghcr.io... "
	@echo "$(GITHUB_TOKEN)" | skopeo login --username "$(or $(GITHUB_ACTOR),anonymous)" --password-stdin ghcr.io

.PHONY: publish-api-prod
publish-api-prod: login-ghcr ## Publish API container image
	skopeo copy --all --preserve-digests "docker://$(p2p_registry)/$(p2p_app_name):$(p2p_version)" "docker://ghcr.io/coreeng/$(p2p_app_name):$(p2p_version)"

.PHONY: publish-ui-prod
publish-ui-prod: login-ghcr ## Publish UI container image
	skopeo copy --all --preserve-digests "docker://$(p2p_registry)/$(p2p_app_name)-ui:$(p2p_version)" "docker://ghcr.io/coreeng/$(p2p_app_name)-ui:$(p2p_version)"

.PHONY: publish-prod
publish-prod: publish-api-prod publish-ui-prod ## Publish all container images

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

