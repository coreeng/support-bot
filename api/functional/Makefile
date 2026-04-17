.PHONY: test
test: # Requires API at SERVICE_ENDPOINT; opt-in env avoids accidental runs from root ./gradlew test
	RUN_FUNCTIONAL_TESTS=true ../gradlew :functional:test

.PHONY: test-integrated
test-integrated: # Start the service and run tests in the same command
	../gradlew :functional:testIntegrated
