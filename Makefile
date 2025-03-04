# Set tenant and app name
P2P_TENANT_NAME ?= support-bot
P2P_APP_NAME ?= support-bot

P2P_IMAGE_NAMES := support-bot-api support-bot-ui

# Download and include p2p makefile
$(shell curl -fsSL "https://raw.githubusercontent.com/coreeng/p2p/v1/p2p.mk" -o ".p2p.mk")
include .p2p.mk

# Define required p2p targets
p2p-build:         build-app           push-app
p2p-functional:    build-functional    push-functional    deploy-functional    run-functional
p2p-nft:           build-nft           push-nft           deploy-nft           run-nft
p2p-integration:   build-integration   push-integration   deploy-integration   run-integration
p2p-extended-test: build-extended-test push-extended-test deploy-extended-test run-extended-test
p2p-prod:                                                 deploy-prod



.PHONY: lint-api-app
lint-api-app: ## Lint api app
	docker pull postgres:17.2-alpine
	cd support-bot-api; ./gradlew pmdMain pmdTest

.PHONY: lint-ui-app
lint-ui-app: ## Lint ui app
	docker run --rm -i docker.io/hadolint/hadolint < support-bot-ui/Dockerfile

.PHONY: lint
lint: lint-api-app lint-ui-app ## Lint api & ui app



.PHONY: build-api-app
build-api-app: lint-api-app ## Build api app
	docker pull postgres:17.2-alpine
	cd support-bot-api; ./gradlew jooqCodegen build test bootBuildImage -DimageName=$(call p2p_image_tag,support-bot-api)

.PHONY: build-ui-app
build-ui-app: lint-ui-app ## Build ui app
	docker buildx build $(p2p_image_cache) --tag "$(call p2p_image_tag,support-bot-ui)" ./support-bot-ui

.PHONY: build-app
build-app: build-api-app build-ui-app ## Build api & ui apps



.PHONY: push-api-app
push-api-app: ## Push api app
	docker image push "$(call p2p_image_tag,support-bot-api)"

.PHONY: push-ui-app
push-ui-app: ## Push ui app
	docker image push "$(call p2p_image_tag,support-bot-ui)"

.PHONY: push-app ## Push api & ui apps
push-app: push-api-app push-ui-app

.PHONY: push-%
push-%:
	@echo "WARNING: $@ not implemented"



.PHONY: build-functional
build-functional:
	@echo "WARNING: $@ not implemented"

.PHONY: build-nft
build-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: build-integration
build-integration:
	@echo "WARNING: $@ not implemented"

.PHONY: build-extended-test
build-extended-test:
	@echo "WARNING: $@ not implemented"



.PHONY: deploy-functional
deploy-functional:
	@echo "WARNING: $@ not implemented"

.PHONY: deploy-nft
deploy-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: deploy-extended-test
deploy-extended-test:
	@echo "WARNING: $@ not implemented"

.PHONY: deploy-%
deploy-%:
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm upgrade --install support-bot-db bitnami/postgresql -n "$(p2p_namespace)" \
		--set global.postgresql.auth.postgresPassword=rootpassword \
		--set global.postgresql.auth.username=supportbot \
		--set global.postgresql.auth.password=supportbotpassword \
		--set global.postgresql.auth.database=supportbot \
	  --set primary.pdb.create=false
	helm upgrade --install "support-bot-api" helm-charts/app -n "$(p2p_namespace)" \
		--set nameOverride="support-bot-api" \
		--set tenantName="$(p2p_tenant_name)" \
		--set image.repository="$(p2p_registry)/support-bot-api" \
		--set image.tag="$(p2p_version)" \
		--set envVarsMap.DB_URL="jdbc:postgresql://support-bot-db-postgresql.$(p2p_namespace).svc.cluster.local:5432/supportbot" \
		--set envVarsMap.DB_USERNAME="supportbot" \
		--set envVarsMap.DB_PASSWORD="supportbotpassword" \
		--set envVarsMap.SLACK_TOKEN="$${SUPPORT_BOT_SLACK_TOKEN}" \
		--set envVarsMap.SLACK_SOCKET_TOKEN="$${SUPPORT_BOT_SLACK_SOCKET_TOKEN}" \
		--set envVarsMap.SLACK_SIGNING_SECRET="$${SUPPORT_BOT_SLACK_SIGNING_SECRET}" \
		--set envVarsMap.SLACK_TICKET_CHANNEL_ID="$${SUPPORT_BOT_SLACK_TICKET_CHANNEL_ID}" \
		--set envVarsMap.SLACK_ESCALATION_CHANNEL_ID="$${SUPPORT_BOT_SLACK_ESCALATION_CHANNEL_ID}" \
		--set service.port="8080" \
		--set metrics.enabled="true" \
		--set metrics.port="8081" \
		--set ingress.enabled=true \
		--set ingress.appUrlSuffix="$(p2p_app_url_suffix)" \
		--set ingress.domain="$(INTERNAL_SERVICES_DOMAIN)" \
		--set ingress.hosts[0].paths[0].path="/" \
		--set ingress.hosts[0].paths[0].pathType="ImplementationSpecific" \
		--set serviceAccount.name="support-bot-api" \
		--set serviceAccount.annotations.iam\\.gke\\.io/gcp-service-account="support-bot-ca@$(PROJECT_ID).iam.gserviceaccount.com"
	helm repo add coreeng https://coreeng.github.io/core-platform-assets
	helm upgrade --install "support-bot-ui" coreeng/app -n "$(p2p_namespace)" \
		--set appName="support-bot-ui" \
		--set appUrlSuffix="$(p2p_app_url_suffix)" \
		--set registry="$(p2p_registry)" \
		--set tag="$(p2p_version)" \
		--set tenantName="$(p2p_tenant_name)" \
		--set image="support-bot-ui" \
		--set ingress.enabled=true \
		--set ingress.domain="$(INTERNAL_SERVICES_DOMAIN)" \
		--set port=7007

.PHONY: run-api-app
run-api-app: ## Run api app
	docker run --rm -P --name "$(p2p_app_name)" "$(call p2p_image_tag,support-bot-api)"

.PHONY: run-ui-app
run-ui-app: ## Run ui app
	docker run --rm -P --name "$(p2p_app_name)" "$(call p2p_image_tag,support-bot-ui)"

.PHONY: run-app ## Run api & ui apps
run-app:
	@echo "WARNING: $@ not implemented"

.PHONY: run-functional
run-functional:
	@echo "WARNING: $@ not implemented"

.PHONY: run-nft
run-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: run-integration
run-integration:
	@echo "WARNING: $@ not implemented"

.PHONY: run-extended-test
run-extended-test:
	@echo "WARNING: $@ not implemented"
