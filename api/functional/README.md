# Functional tests

Functional tests are being run against a deployed version of application.
It mocks Slack API via Wiremock and calls the service on behalf of Slack to
notify about events such as "message posted", "reaction added" and others.

## Steps to run
Before running the tests itself you'll need to start the service itself with
the configuration adjusted to the functional tests.
In practice it means that you'll need to start it with `functionaltests`
Spring profile active (make sure database is accessible first).
Before running the service start the database first:
```bash
# from ../service folder
make db-clean db-run
```

After that in a separate terminal start the service:
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
make testIntegrated
```