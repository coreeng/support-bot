# Include P2P Makefile
include Makefile.p2p

P2P_IMAGE_NAMES := support-bot-api support-bot-ui

.PHONY: lint
lint:
	docker run --rm -i docker.io/hadolint/hadolint < support-bot-api/Dockerfile
	docker run --rm -i docker.io/hadolint/hadolint < support-bot-ui/Dockerfile



.PHONY: run-app
run-app:
	@echo "WARNING: $@ not implemented"
	docker run --rm -P --name "$(p2p_app_name)" "$(call p2p_image_tag,support-bot-api)"

.PHONY: build-app
build-app:
	docker build --tag "$(call p2p_image_tag,support-bot-api)" ./support-bot-api
	docker build --tag "$(call p2p_image_tag,support-bot-ui)" ./support-bot-ui
	docker image push "$(call p2p_image_tag,support-bot-api)"
	docker image push "$(call p2p_image_tag,support-bot-ui)"



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



.PHONY: test-functional
test-functional:
	@echo "WARNING: $@ not implemented"

.PHONY: test-nft
test-nft:
	@echo "WARNING: $@ not implemented"

.PHONY: test-integration
test-integration:
	@echo "WARNING: $@ not implemented"

.PHONY: test-extended-test
test-extended-test:
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
	helm repo add coreeng https://coreeng.github.io/core-platform-assets
	helm upgrade --install "support-bot-api" coreeng/app -n $(p2p_namespace) \
		--set appName="support-bot-api" \
		--set appUrlSuffix="$(p2p_app_url_suffix)" \
		--set registry=$(p2p_registry) \
		--set tag="$(p2p_version)" \
		--set tenantName=$(p2p_tenant_name) \
		--set image="support-bot-api" \
		--set ingress.enabled=true \
		--set ingress.domain="$(INTERNAL_SERVICES_DOMAIN)" \
		--set port=9898 \
		--set service.environmentVariables.FOO="bar"
	helm upgrade --install "support-bot-ui" coreeng/app -n $(p2p_namespace) \
		--set appName="support-bot-ui" \
		--set appUrlSuffix="$(p2p_app_url_suffix)" \
		--set registry=$(p2p_registry) \
		--set tag="$(p2p_version)" \
		--set tenantName=$(p2p_tenant_name) \
		--set image="support-bot-ui" \
		--set ingress.enabled=true \
		--set ingress.domain="$(INTERNAL_SERVICES_DOMAIN)" \
		--set port=9898 \
		--set service.environmentVariables.FOO="bar"
