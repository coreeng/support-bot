# Dex deployment (dexidp Helm chart)

Dex runs the upstream image [`ghcr.io/dexidp/dex`](https://github.com/dexidp/dex). Kubernetes installs use the official chart **`dex/dex`** from [charts.dexidp.io](https://charts.dexidp.io) (pin `DEX_CHART_VERSION` in `dex/Makefile`, default `0.24.0`).

Configuration is the structured `config:` map in values; the chart renders it into a Secret as `config.yaml` (there is no `${ENV}` expansion in that file—put real strings in a private overlay or use `helm upgrade --set` for secrets).

## RBAC (GKE / restricted clusters)

The **dex/dex** chart creates **namespace** `Role` + `RoleBinding` for `dex.coreos.com` when `rbac.create` is true. With the default `rbac.createClusterScoped: true`, it also creates **ClusterRole** + **ClusterRoleBinding** so Dex can manage CRDs — that requires cluster-level IAM (e.g. `container.clusterRoles.create`) and is unnecessary for this repo’s setup (**sqlite** storage and config in a **Secret**).

`values-dexidp.yaml` sets **`rbac.createClusterScoped: false`** so Helm only applies namespaced RBAC. If you switch Dex to **Kubernetes storage** or an operator flow that must create CRDs, you may need cluster RBAC or a platform team to install those resources.

## Files

- `values-dexidp.yaml` — baseline: issuer, sqlite storage, web/telemetry ports, static client, empty `connectors: []`, namespaced RBAC only (`rbac.createClusterScoped: false`).
- `values-integration.yaml` — sample integration overrides (issuer, `staticClients`, resource bumps). **No** connector here. Ingress is **off** by default; reach Dex via full svc FQDN or port-forward.
- `values-dex-oidc-incluster.yaml` — Tier 2 overlay: in-cluster issuer (full svc FQDN) and `staticClients` for `http://127.0.0.1:8765/api/oauth/callback/dex`. Does **not** define `connectors`. Set `DEX_ISSUER_URI` and `DEX_INTERNAL_BASE_URL` to match `config.issuer`.

**Per-backend connector overlays** (one connector each — composed by `dex/scripts/compose_helm_values.py`, never passed straight to `helm -f`):

- `values-tls.yaml` — LDAP over **StartTLS/LDAPS** for non-ephemeral environments (see runbook).
- `values-integration-ldap-plaintext-ephemeral.yaml` — **opt-in** LDAP connector on port **389** without TLS (`insecureNoSSL: true`). Only for disposable integration namespaces. `helm_dex.sh deploy-integration` requires **`DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true`** to use this in place of `values-tls.yaml`.
- `values-google.yaml` — Dex Google connector (`type: google`). Used when `DEX_GOOGLE_ENABLED=true`.
- `values-microsoft.yaml` — Dex Microsoft / Entra connector (`type: microsoft`). Used when `DEX_MICROSOFT_ENABLED=true`.

Baseline **`values-dexidp.yaml`** sets **`enablePasswordDB: false`** (connectors only). Operators select connectors with `DEX_LDAP_ENABLED` / `DEX_GOOGLE_ENABLED` / `DEX_MICROSOFT_ENABLED`; the composer concatenates the corresponding overlays into one `config.connectors` list before `helm` runs. Set `enablePasswordDB: true` and `staticPasswords` in a private overlay only if you need Dex’s built-in email login alongside (or instead of) connectors.

## Required secret (`dex-secrets`)

The **dex/dex** chart does not wire `dex-secrets` into config automatically. For production, keep sensitive strings out of Git: use a private `-f` values file or automation that sets `config.staticClients[].secret`, `config.connectors[].config.bindPW`, and OAuth client IDs/secrets.

For **optional** Git / template checks, `values-dexidp.yaml` uses `envsubst` placeholders (`${PLACEHOLDER_CLIENT_SECRET}`, `${PLACEHOLDER_LDAP_BIND}`). The `helm_dex.sh` script substitutes them at deploy time via `envsubst`.

Example keys you may mirror from a Secret into values (conceptually):

- `client-secret` — same as `config.staticClients[].secret` for the Support Bot client.

For **Tier 2** integration tests, Kubernetes Secret `dex-secrets` is expected with **`client-id`** and **`client-secret`** (see [`api/integration-tests/README.md`](../../integration-tests/README.md)).
- `ldap-bind-password` — same as LDAP connector `bindPW` when LDAP is enabled.
- `google-client-id` / `google-client-secret` — when adding a Google connector to Dex (so Dex can act as the Google SSO gateway).
- `microsoft-client-id` / `microsoft-client-secret` — when adding a Microsoft connector to Dex.

## Install / upgrade

Selecting connectors is driven by env flags — `helm_dex.sh` reads them from the deployer's environment (or `dex/.env.local` when invoked through `make -C dex deploy-*`) and feeds the matching per-backend overlays through the composer:

```bash
# pick any combination
export DEX_LDAP_ENABLED=true       # uses values-tls.yaml unless DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true
export DEX_GOOGLE_ENABLED=true     # adds values-google.yaml
export DEX_MICROSOFT_ENABLED=true  # adds values-microsoft.yaml
export DEX_ISSUER=https://dex.example.com
export DEX_GOOGLE_CLIENT_ID=...    DEX_GOOGLE_CLIENT_SECRET=...
export DEX_MICROSOFT_CLIENT_ID=... DEX_MICROSOFT_CLIENT_SECRET=... DEX_MICROSOFT_TENANT=common
export LDAP_BOOTSTRAP_USER_PASSWORD=...

NAMESPACE=support-bot-prod bash dex/scripts/helm_dex.sh deploy-prod
```

The script:
1. composes a single `connectors:` list from the enabled overlays (Helm's last-wins overlay merge cannot append YAML lists, so per-backend overlays must not be layered directly with `-f`),
2. ensures the `dex-secrets` Kubernetes Secret with `client-id` / `client-secret` (and `ldap-bind-password` / `google-client-*` / `microsoft-client-*` keys when those envs are set),
3. runs `helm upgrade --install` against the composed values.

**Tier 2 OIDC Job** (in-cluster issuer aligned with `DEX_ISSUER_URI` and `DEX_INTERNAL_BASE_URL` using full svc FQDN; **ephemeral plaintext LDAP** — do not reuse on shared clusters):

```bash
DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true \
DEX_LDAP_ENABLED=true \
NAMESPACE=support-bot-integration \
bash dex/scripts/helm_dex.sh deploy-integration
```

Validate every supported combination renders against the chart (LDAP TLS, LDAP plaintext, Google, Microsoft, LDAP+Google, all-three TLS, all-three plaintext):

```bash
make dex-template
```

## TLS for the LDAP connector

Ephemeral plaintext LDAP lives in [`values-integration-ldap-plaintext-ephemeral.yaml`](./values-integration-ldap-plaintext-ephemeral.yaml) (**opt-in** at deploy time via `DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true` when using `dex/scripts/helm_dex.sh`). For anything beyond disposable integration, layer [`values-tls.yaml`](./values-tls.yaml) to switch to **StartTLS** (port 389) or **LDAPS** (port 636). If the LDAP cert is signed by a private CA, set `rootCAData` or mount the CA into the Dex pod. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md) § "Enabling LDAPS / StartTLS".

## Connectors: LDAP, Google, Microsoft

Each connector lives in its own per-backend overlay. Operators turn them on with env flags — any combination works.

| Connector  | Overlay                                                 | Enable flag             | Required env                                                                  |
|------------|---------------------------------------------------------|-------------------------|-------------------------------------------------------------------------------|
| LDAP (TLS) | [`values-tls.yaml`](./values-tls.yaml)                  | `DEX_LDAP_ENABLED=true` | `LDAP_BOOTSTRAP_USER_PASSWORD`                                                |
| LDAP (plaintext, ephemeral) | [`values-integration-ldap-plaintext-ephemeral.yaml`](./values-integration-ldap-plaintext-ephemeral.yaml) | `DEX_LDAP_ENABLED=true` + `DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true` | `LDAP_BOOTSTRAP_USER_PASSWORD` |
| Google     | [`values-google.yaml`](./values-google.yaml)            | `DEX_GOOGLE_ENABLED=true`     | `DEX_GOOGLE_CLIENT_ID`, `DEX_GOOGLE_CLIENT_SECRET`, `DEX_ISSUER`              |
| Microsoft  | [`values-microsoft.yaml`](./values-microsoft.yaml)      | `DEX_MICROSOFT_ENABLED=true`  | `DEX_MICROSOFT_CLIENT_ID`, `DEX_MICROSOFT_CLIENT_SECRET`, `DEX_ISSUER`, optional `DEX_MICROSOFT_TENANT` (default `common`) |

Setup notes:

- **LDAP** — JWT `groups` for Support Bot `jwt-groups` depends on this connector and Dex scopes. See [Bitnami/OpenLDAP DIT example](./values-tls.yaml).
- **Google** — register a **Web** OAuth client (Google Cloud Console) whose authorized redirect URI is **`${DEX_ISSUER}/callback`** (Dex's own callback), not the Support Bot API or UI URL. See [Dex docs](https://dexidp.io/docs/connectors/google/).
- **Microsoft** — register an Entra app with the same `${DEX_ISSUER}/callback` redirect URI; set `DEX_MICROSOFT_TENANT` to a tenant UUID (or `common` / `organizations` / `consumers`). See [Dex docs](https://dexidp.io/docs/connectors/microsoft/).

`dex/scripts/compose_helm_values.py` concatenates the enabled overlays into one `config.connectors` list before invoking `helm`. Per-backend overlays must **not** be layered directly with `-f`: Helm's last-wins overlay merge replaces YAML lists wholesale, so the second `-f` would silently drop the first connector. The composer also fails fast if two overlays produce the same connector `id` (e.g. enabling both LDAP TLS and LDAP plaintext at once).

User-facing SSO against Dex is the **only** OAuth2 client the API registers
(`DEX_CLIENT_ID`, `DEX_CLIENT_SECRET`, `DEX_ISSUER_URI`). Google, Azure AD, and LDAP
are reachable as **Dex connectors**, configured here in your Dex deployment, not as
separate front doors in the Support Bot app. See
[configuration.md](../../service/docs/configuration.md).

## Integration deploy order (with LDAP)

Deploy **LDAP** first so the Service exists, then Dex with LDAP `host` pointing at that Service (full svc FQDN on port 389 when colocated). Deploy or upgrade the **Support Bot API** after Dex with matching `DEX_*` env vars. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Support Bot API wiring

- `DEX_CLIENT_ID` = `config.staticClients[].id` (e.g. `support-bot-dex`).
- `DEX_CLIENT_SECRET` = same value as `config.staticClients[].secret`.
- `DEX_ISSUER_URI` = `config.issuer`.
- `DEX_INTERNAL_BASE_URL` (optional) = in-cluster base URL for token/keys/userinfo. Use the full svc FQDN (e.g. `http://dex.<namespace>.svc.cluster.local:5556`) so the API startup validation accepts it without warnings. If unset, `DEX_ISSUER_URI` is used.

Dex `staticClients.redirectURIs` must list **every** URL the app uses as `redirect_uri` — they differ by **path and origin**, not only by prod vs dev host:

- **Spring backend (`oauth2Login`)** — API origin, path `/login/oauth2/code/dex`: `https://<api-host>/login/oauth2/code/dex`, `http://localhost:8080/login/oauth2/code/dex`, `http://127.0.0.1:8080/login/oauth2/code/dex`.
- **UI-initiated OAuth (Next.js)** — UI origin, path `/api/oauth/callback/dex`: `https://<ui-host>/api/oauth/callback/dex`, `http://localhost:3000/api/oauth/callback/dex`, `http://127.0.0.1:3000/api/oauth/callback/dex`.

See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Stage 1 lifecycle

```bash
make dex-template
make dex-deploy-integration   # from repo root: sets DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true and DEX_LDAP_ENABLED=true
```

Workflow: `.github/workflows/dex-fast-feedback.yaml` runs P2P fast feedback in `dex/` (`make p2p-build` → `helm template` covering every connector combination).

**Ad-hoc** `make -C dex deploy-integration` must export **`DEX_LDAP_ENABLED=true`** plus **`DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true`** before running (or set the flags in `dex/.env.local`), or `helm_dex.sh` refuses to install the plaintext LDAP overlay. Set `DEX_GOOGLE_ENABLED` / `DEX_MICROSOFT_ENABLED` to add cloud IdPs alongside or instead.
