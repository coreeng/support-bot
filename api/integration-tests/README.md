# Integration tests

The support bot is integrated with different external systems such as:

- Kubernetes cluster
- Cloud providers
- Slack

These tests are meant to be testing the bot against these external systems.

Since whether the bot integrated with an external system or not depends on a configuration
that it reads on startup, we need to bootstrap the service multiple times during
the testing process with different configuration provided.

## Tier 1: LDAP + Dex in-cluster smoke (`ldap-infra`)

[`DexLdapInfraIT`](src/test/java/com/coreeng/supportbot/teams/rest/DexLdapInfraIT.java) applies a short-lived **Kubernetes Job** that:

1. `curl`s Dex **telemetry** liveness URL: `http://dex:5558/healthz/live` (matches [dexidp chart](https://charts.dexidp.io) probes).
2. Runs `ldapsearch` against `ldap:389` for seed user `uid=alice` under `dc=supportbot,dc=local`, binding as `cn=admin,dc=supportbot,dc=local` using Secret **`ldap-secrets`** key **`admin-password`**.

The Job manifest lives at [`src/test/resources/k8s/dex-ldap-infra-job.yaml`](src/test/resources/k8s/dex-ldap-infra-job.yaml).

### Prerequisites (same namespace as `integration-test-local.yaml`)

1. **Namespace** — e.g. `support-bot-integration` from [`integration-test-local.yaml`](src/test/resources/integration-test-local.yaml) (`INTEGRATION_TEST_CONFIG` if overridden).
2. **Helm releases** (install before running the test; not done by the test itself):
   - LDAP: e.g. `make ldap-deploy-integration` / [`ldap/scripts/helm_ldap.sh`](../ldap/scripts/helm_ldap.sh) with [`values-bitnami.yaml`](../k8s/ldap/values-bitnami.yaml) so the Service is named **`ldap`** and plain LDAP is on **389**.
   - Dex: e.g. `make dex-deploy-integration` / [`dex/scripts/helm_dex.sh`](../dex/scripts/helm_dex.sh) with [`values-dexidp.yaml`](../k8s/dex/values-dexidp.yaml) + integration overlay so the Service is **`dex`** and telemetry listens on **5558**.
3. **Secret** `ldap-secrets` in that namespace with `admin-password` (see [`api/k8s/ldap/README.md`](../k8s/ldap/README.md)).

Service DNS names **`ldap`** and **`dex`** must match the Job env defaults; override the chart `fullnameOverride` if you use different names, and adjust the Job YAML accordingly.

### How to run

The test class is **disabled by default** so normal `./gradlew test` does not require LDAP/Dex.

Enable it and point `kubectl` at your cluster:

```bash
export INTEGRATION_LDAP_DEX_SMOKE=true
cd api && ./gradlew :integration-tests:test --tests 'com.coreeng.supportbot.teams.rest.DexLdapInfraIT'
```

JUnit tags: `integration`, `ldap-infra` (for selective CI filters).

# Steps to run (existing service tests)

Open the `integration-test-local.yaml` file under `src/test/resources` folder and change
the service image repository and the tag, so that you will test against the specific version of the service.

Ensure you have a connection to the external systems you will test against.
At the moment it's:

1. Kubernetes cluster
2. Azure groups

Finally, run the actual tests:

```bash
make test
```
