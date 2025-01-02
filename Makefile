.PHONY: build
build:
	./gradlew build

.PHONY: build-continuously
build-continuously:
	./gradlew build --continuously

.PHONY: run
run:
	./gradlew bootRun

.PHONY: lint
lint:
	./gradlew pmdMain pmdTest

.PHONY: test
test:
	./gradlew test

# Run tests + all the other checks (like linting)
.PHONY: check
check:
	./gradlew check

TAG=latest
PUBLISH=false
# Specify next variables in case PUBLISH=true
USERNAME=
PASSWORD=
.ONESHELL: docker-build
.PHONY: docker-build
docker-build:
	@export password="$(PASSWORD)"
	@export args="-DimageTag=$(TAG)"
	@if [[ "$(PUBLISH)" == "true" ]]; then \
		args+=" --publishImage -Dusername=$(USERNAME) -Dpassword=$${password}"; \
	fi
	@./gradlew bootBuildImage $${args}

