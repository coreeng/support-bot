# Functional tests

Functional tests are being run against a deployed version of application.
It mocks Slack API via Wiremock and calls the service on behalf of Slack to
notify about events such as "message posted", "reaction added" and others.

## Prerequisites
- A reachable PostgreSQL instance (see [service README](../service/README.md) for `make db-run`)
- Service listening on `http://localhost:8080` by default
  - Override target with `SERVICE_ENDPOINT=http://host:port` if different

## Steps to run
Start the database, then run the service with the `functionaltests` Spring profile.
Start the database first:
```bash
# from ../service folder
make db-clean db-run
```

Start the service with the `functionaltests` Spring profile:
```bash
# from ../service folder
SPRING_PROFILES_ACTIVE=functionaltests make run
```

Once it's done, you are ready to run the tests:
```bash
make test
```

Alternatively, you can skip the step with starting up the service and run this:
```bash
# This will check if the service is already running and if not,
# will start it, run the tests and shutdown the service
make test-integrated
```
