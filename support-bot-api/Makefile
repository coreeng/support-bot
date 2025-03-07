.PHONY: docker
docker: ## Build api app
	docker buildx build --tag abc:1.1.3 --file service/Dockerfile .


.PHONY: gradle
gradle: ## Build api app
	docker pull postgres:17.2-alpine
	./gradlew :service:jooqCodege :service:build :service:test :service:bootBuildImage -DimageName=abc:1.1.2
