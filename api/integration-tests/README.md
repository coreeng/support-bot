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

[`DexLdapInfraTest`](src/test/java/com/coreeng/supportbot/teams/rest/DexLdapInfraTest.java) applies a short-lived **Kubernetes Job** that:

1. Checks Dex **telemetry** liveness URL: `http://dex:5558/healthz/live` (matches [dexidp chart](https://charts.dexidp.io) probes).
2. Queries LDAP at `ldap:389` for seed user `uid=alice` under `dc=supportbot,dc=local`, binding as `cn=admin,dc=supportbot,dc=local` using Secret **`ldap-secrets`** key **`admin-password`**.

The Job manifest lives at [`src/test/resources/k8s/dex-ldap-infra-job.yaml`](src/test/resources/k8s/dex-ldap-infra-job.yaml).

### Prerequisites (same namespace as `integration-test-local.yaml`)

1. **Namespace** — e.g. `support-bot-integration` from [`integration-test-local.yaml`](src/test/resources/integration-test-local.yaml) (`INTEGRATION_TEST_CONFIG` if overridden).
2. **Helm releases** (install before running the test; not done by the test itself):
   - LDAP: e.g. `make ldap-deploy-integration` / [`ldap/scripts/helm_ldap.sh`](../ldap/scripts/helm_ldap.sh) with [`values-bitnami.yaml`](../k8s/ldap/values-bitnami.yaml) so the Service is named **`ldap`** and plain LDAP is on **389**.
   - Dex: e.g. `make dex-deploy-integration` / [`dex/scripts/helm_dex.sh`](../dex/scripts/helm_dex.sh) with [`values-dexidp.yaml`](../k8s/dex/values-dexidp.yaml) + integration overlay so the Service is **`dex`** and telemetry listens on **5558**.
3. **Secret** `ldap-secrets` in that namespace with `admin-password` (see [`api/k8s/ldap/README.md`](../k8s/ldap/README.md)).

Service DNS names **`ldap`** and **`dex`** must match the Job env defaults; override the chart `fullnameOverride` if you use different names, and adjust the Job YAML accordingly.

### How to run

The test class runs by default. Disable all LDAP/Dex tests with:

```bash
export DISABLE_INTEGRATION_LDAP_DEX_TESTS=true
```

Run it (point `kubectl` at your cluster first):

```bash
cd api && ./gradlew :integration-tests:test --tests 'com.coreeng.supportbot.teams.rest.DexLdapInfraTest'
```

JUnit tags: `integration`, `ldap-infra` (for selective CI filters).

## Tier 2: Dex LDAP OAuth code + API exchange + jwt-groups (`oidc`)

[`DexOidcInClusterTest`](src/test/java/com/coreeng/supportbot/teams/rest/DexOidcInClusterTest.java) applies a **ConfigMap** (Python script from [`dex-ldap-oidc-flow.py`](src/test/resources/k8s/dex-ldap-oidc-flow.py)) and a **Job** ([`dex-ldap-oidc-job.yaml`](src/test/resources/k8s/dex-ldap-oidc-job.yaml)) that:

1. Listens on `http://127.0.0.1:8765/api/oauth/callback/dex` and drives Dex’s LDAP login for bootstrap user `alice@supportbot.local` (password from a Secret, not from Git).
2. Sends the authorization **code** once to Support Bot **`POST /auth/oauth/exchange`** with `provider=dex` and the same `redirectUri` (codes are single-use; the Job does not call Dex’s token endpoint itself).
3. Decodes the **API-issued JWT** and asserts team code `core` is present (from `platform-integration.jwt-groups` mapping `developers` → `core` under profile `integrationtests-oidc`).
4. Calls **`GET /auth/me`** with `Authorization: Bearer` and asserts the same team.

Log markers: `OK_TOKEN`, `OK_GROUPS`, `OK_API`, `OK_ALL`.

### Prerequisites

1. Same namespace and kube context as Tier 1 ([`integration-test-local.yaml`](src/test/resources/integration-test-local.yaml)).
2. **Dex** installed with issuer **identical** to the API’s `DEX_ISSUER_URI`. For in-cluster Jobs and Services, use the optional overlay [`values-dex-oidc-incluster.yaml`](../k8s/dex/values-dex-oidc-incluster.yaml) so `config.issuer` uses the full svc FQDN (e.g. `http://dex.support-bot-integration.svc.cluster.local:5556`) and apply after [`values-integration.yaml`](../k8s/dex/values-integration.yaml) (see [`api/k8s/dex/README.md`](../k8s/dex/README.md)). [`values-integration.yaml`](../k8s/dex/values-integration.yaml) also registers `http://127.0.0.1:8765/api/oauth/callback/dex` on the static client.
3. **Support Bot API** deployed for integration tests with **OIDC** env and profiles, e.g. Helm values [`values-integrationtests-oidc.yaml`](../k8s/service/values-integrationtests-oidc.yaml) (`SPRING_PROFILES_ACTIVE=integrationtests,integrationtests-oidc`, `DEX_*`, test-bypass off). `DEX_ISSUER_URI` and `DEX_INTERNAL_BASE_URL` must match Dex `config.issuer`.
4. **Secrets** in that namespace:
   - `dex-secrets` with keys **`client-id`** and **`client-secret`** (same as Dex `staticClients` for `support-bot-dex`).
   - **`integration-ldap-test-user`** with key **`password`**: plaintext password for `alice@supportbot.local`. It must **match** the password used when LDAP bootstrap **`20-users.ldif`** was rendered (`LDAP_BOOTSTRAP_USER_PASSWORD` — for integration, set from a **GitHub Actions secret** and use the same value for `kubectl create secret generic integration-ldap-test-user --from-literal=password="$SECRET" -n <namespace>`). See [`ldap/README.md`](../../ldap/README.md).

The Job uses full svc FQDNs for **`dex`**, **`ldap`**, and **`support-bot`** in the `support-bot-integration` namespace; adjust the Job manifest if your release names or namespace differ.

### How to run

```bash
cd api && ./gradlew :integration-tests:test --tests 'com.coreeng.supportbot.teams.rest.DexOidcInClusterTest'
```

To disable, set `DISABLE_INTEGRATION_LDAP_DEX_TESTS=true` (same flag as Tier 1).

JUnit tags: `integration`, `oidc`.

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
