## NFT module

This module contains the Gatling-based **non-functional tests** (NFT) for the Support Bot.

### What it does
- Reuses the shared `:testkit` to talk to the service and Slack stub.
- Defines the main Gatling simulation `TicketFlowSimulation` that covers:
  - tenant posting a support message,
  - support reacting with the initial emoji to create a ticket,
  - opening the full summary modal,
  - submitting the summary form.
- Verifies behavior via `RequestJournalVerifier`, which inspects WireMock's request journal instead of poking the DB directly.
- Uses an `application-nft.yaml` Spring profile on the service side so test data (teams, tags, users) matches what the simulation expects.

### How to run locally
- Start the Postgres instance
- Start the service with the `nft` profile and its dependencies.
- From the `api` directory, run:
  - `./gradlew :nft:gatlingRunIntegrated` – starts the service (if not already running) via `ServiceLifecycle` and runs the Gatling simulation, then stops the service.

### How it runs in CI / Kubernetes
- Built into a dedicated Docker image via `api/nft/Dockerfile`.
- A K8s Job (Helm chart `api/k8s/nft-tests`) runs this image in-cluster, pointing `SERVICE_ENDPOINT` at the deployed `support-bot` service.
- Gatling HTML reports are copied from `nft/build/reports/gatling` into `/mnt/nft-reports`, which is backed by a PVC or `emptyDir`.
- `api/scripts/run-nft-tests.sh` installs the chart, waits for the Job to finish, and uses a helper pod to `kubectl cp` `/mnt/nft-reports` into `reports/nft` so CI can upload the HTML reports.

