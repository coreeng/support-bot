# Dex deployment (dexidp Helm chart)

Dex runs the upstream image [`ghcr.io/dexidp/dex`](https://github.com/dexidp/dex). Kubernetes installs use the official chart **`dex/dex`** from [charts.dexidp.io](https://charts.dexidp.io) (pin `DEX_CHART_VERSION` in `dex/Makefile`, default `0.24.0`).

Configuration is the structured `config:` map in values; the chart renders it into a Secret as `config.yaml` (there is no `${ENV}` expansion in that file—put real strings in a private overlay or use `helm upgrade --set` for secrets).

## RBAC (GKE / restricted clusters)

The **dex/dex** chart creates **namespace** `Role` + `RoleBinding` for `dex.coreos.com` when `rbac.create` is true. With the default `rbac.createClusterScoped: true`, it also creates **ClusterRole** + **ClusterRoleBinding** so Dex can manage CRDs — that requires cluster-level IAM (e.g. `container.clusterRoles.create`) and is unnecessary for this repo’s setup (**sqlite** storage and config in a **Secret**).

`values-dexidp.yaml` sets **`rbac.createClusterScoped: false`** so Helm only applies namespaced RBAC. If you switch Dex to **Kubernetes storage** or an operator flow that must create CRDs, you may need cluster RBAC or a platform team to install those resources.

## Files

- `values-dexidp.yaml` — baseline: issuer, sqlite storage, web/telemetry ports, static client, optional empty `connectors: []`, namespaced RBAC only (`rbac.createClusterScoped: false`).
- `values-integration.yaml` — sample integration overrides (issuer, LDAP connector using full svc FQDN, resource bumps). Ingress is **off** by default; reach Dex via full svc FQDN or port-forward.
- `values-dex-oidc-incluster.yaml` — optional Tier 2 overlay: in-cluster issuer with full svc FQDN, static client redirect `http://127.0.0.1:8765/callback`, full LDAP connector (list replace-safe). Use when the API and integration Job talk to Dex only in-cluster; set `DEX_ISSUER_URI` and `DEX_INTERNAL_BASE_URL` to the same FQDN.
- `values-legacy-core-platform-app.yaml` — archived `core-platform-app` + templated `config.yaml` with `${DEX_*}` placeholders.

Baseline **`values-dexidp.yaml`** sets **`enablePasswordDB: false`** (connectors only). Add LDAP / Google / Microsoft under `config.connectors` via `values-integration.yaml` or another overlay, or set `enablePasswordDB: true` and `staticPasswords` in a private overlay if you need Dex’s built-in email login.

## Required secret (`dex-secrets`)

The **dex/dex** chart does not wire `dex-secrets` into config automatically. For production, keep sensitive strings out of Git: use a private `-f` values file or automation that sets `config.staticClients[].secret`, `config.connectors[].config.bindPW`, and OAuth client IDs/secrets.

For **optional** Git / template checks, `values-dexidp.yaml` uses obvious placeholders (`helm-template-placeholder-*`). Replace them in your cluster overlay.

Example keys you may mirror from a Secret into values (conceptually):

- `client-secret` — same as `config.staticClients[].secret` for the Support Bot client.

For **Tier 2** integration tests, Kubernetes Secret `dex-secrets` is expected with **`client-id`** and **`client-secret`** (see [`api/integration-tests/README.md`](../../integration-tests/README.md)).
- `ldap-bind-password` — same as LDAP connector `bindPW` when LDAP is enabled.
- `google-client-id` / `google-client-secret` — when adding a Google connector.
- `microsoft-client-id` / `microsoft-client-secret` — when adding a Microsoft connector.

## Install / upgrade

```bash
helm repo add dex https://charts.dexidp.io
helm repo update dex
helm upgrade --install support-bot-dex dex/dex --version 0.24.0 \
  -f api/k8s/dex/values-dexidp.yaml \
  -f api/k8s/dex/values-integration.yaml
```

**Tier 2 OIDC Job** (in-cluster issuer aligned with `DEX_ISSUER_URI` and `DEX_INTERNAL_BASE_URL` using full svc FQDN):

```bash
helm upgrade --install support-bot-dex dex/dex --version 0.24.0 \
  -f api/k8s/dex/values-dexidp.yaml \
  -f api/k8s/dex/values-integration.yaml \
  -f api/k8s/dex/values-dex-oidc-incluster.yaml
```

Validate render only:

```bash
make dex-template
```

## TLS for the LDAP connector

The default `values-integration.yaml` uses **`insecureNoSSL: true`** (plaintext on port 389). For production, layer [`values-tls.yaml`](./values-tls.yaml) to switch to **StartTLS** (port 389) or **LDAPS** (port 636). If the LDAP cert is signed by a private CA, set `rootCAData` or mount the CA into the Dex pod. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md) § "Enabling LDAPS / StartTLS".

## Connectors: LDAP, Google, Microsoft

- **LDAP** — `values-integration.yaml` shows a connector matching the Bitnami/OpenLDAP DIT (full svc FQDN on port 389, `cn=admin,dc=supportbot,dc=local`, group search for `groupOfUniqueNames`). For TLS, see the section above. JWT `groups` for Support Bot `jwt-groups` depends on this connector and Dex scopes.
- **Google** — add a `connectors` entry with `type: google` per [Dex docs](https://dexidp.io/docs/connectors/google/). Register a **Web** OAuth client whose redirect URI is **`{issuer}/callback`** (e.g. `https://dex.example.com/callback`), not the API’s `/login/oauth2/code/...` URL.
- **Microsoft** — add `type: microsoft` per [Dex docs](https://dexidp.io/docs/connectors/microsoft/) with the same `{issuer}/callback` redirect URI and `tenant` in `config`.

Because `connectors` is a YAML list, merge carefully: the last values file that sets `config.connectors` **replaces** the whole list. Copy the LDAP block and add Google/Microsoft entries in the same file when combining.

User-facing SSO against Dex uses the **Dex** OAuth2 registration on the API (`DEX_CLIENT_ID`, `DEX_CLIENT_SECRET`, `DEX_ISSUER_URI`). The API can also register **Google** and **Azure AD** directly; by default **all** fully configured IdPs are shown. To show **only Dex** on the login UI while `GOOGLE_*` / `AZURE_*` remain set (e.g. for Azure Cloud integration), set `security.oauth2.login-providers: [dex]` (see [configuration.md](../../service/docs/configuration.md)).

## Integration deploy order (with LDAP)

Deploy **LDAP** first so the Service exists, then Dex with LDAP `host` pointing at that Service (full svc FQDN on port 389 when colocated). Deploy or upgrade the **Support Bot API** after Dex with matching `DEX_*` env vars. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Support Bot API wiring

- `DEX_CLIENT_ID` = `config.staticClients[].id` (e.g. `support-bot-dex`).
- `DEX_CLIENT_SECRET` = same value as `config.staticClients[].secret`.
- `DEX_ISSUER_URI` = `config.issuer`.
- `DEX_INTERNAL_BASE_URL` (optional) = in-cluster base URL for token/keys/userinfo. Use the full svc FQDN (e.g. `http://dex.<namespace>.svc.cluster.local:5556`) so the API startup validation accepts it without warnings. If unset, `DEX_ISSUER_URI` is used.

Dex `staticClients.redirectURIs` must include `https://<api-host>/login/oauth2/code/dex` (and localhost variants for dev).

## Stage 1 lifecycle

```bash
make dex-template
make dex-deploy-integration
```

Workflow: `.github/workflows/dex-fast-feedback.yaml` (and optional `support-bot-dex-fast-feedback.yaml`) run P2P fast feedback in `dex/` (`make p2p-build` → `helm template`).
