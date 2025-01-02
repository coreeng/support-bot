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
.PHONY: docker-build
docker-build:
	./gradlew bootBuildImage "-DimageTag=$(TAG)"

