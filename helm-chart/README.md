# Support Bot Helm Chart

This chart deploys the Support Bot (a Slack bot that manages support requests) to Kubernetes.

## Install

```bash
helm install support-bot oci://ghcr.io/coreeng/charts/support-bot \
  --set image.repository="ghcr.io/coreeng/support-bot" \
  --set image.tag="<your-image-tag>"
```

Notes:
- `image.tag` defaults to the chart `appVersion` when empty which corresponds to the bot version that it was built with.
- If your image requires credentials, set `imagePullSecrets` (see Configuration).
- Dex is bundled as a subchart by default — see [Bundled Dex](#bundled-dex) for the two ways to wire authentication, or set `dex.enabled: false` to opt out.
- For a full end-to-end try-out on a local kind cluster (Postgres + API + UI + bundled Dex + four pre-seeded users), see [docs/local-kind-test.md](../docs/local-kind-test.md).

## Database

Support Bot requires PostgreSQL. One simple option is to install Bitnami PostgreSQL and use the default service name expected by this chart’s default `DB_URL`:

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install support-bot-db bitnami/postgresql \
  --set image.repository=bitnamilegacy/postgresql \
  --set global.postgresql.auth.postgresPassword=rootpassword \
  --set global.postgresql.auth.username=supportbot \
  --set global.postgresql.auth.password=supportbotpassword \
  --set global.postgresql.auth.database=supportbot
```

By default the chart sets environment variables:
- `DB_URL=jdbc:postgresql://support-bot-db-postgresql:5432/supportbot`
- `DB_USERNAME=supportbot`
- `DB_PASSWORD=supportbotpassword`
- `DB_SCHEMA=public`

Use Secrets for credentials in real environments by overriding the `env` entries (see below).

### Non-`public` schema

To target a schema other than `public` (e.g. when the connecting role's `search_path` is locked down by a DBA, or when you want to isolate Support Bot tables), override `DB_SCHEMA`:

```yaml
env:
  - name: DB_SCHEMA
    value: "supportbot"
```

The chart will:
- Run Flyway against that schema (including `flyway_schema_history`, so migration state is deterministic).
- Set `search_path` on every Hikari connection, so all queries — jOOQ-generated and raw SQL alike — resolve to the configured schema at runtime.

The schema must exist and the connecting role must be able to create objects in it. Have your DBA run, once, before the first install:

```sql
CREATE SCHEMA IF NOT EXISTS supportbot;
GRANT CREATE, USAGE ON SCHEMA supportbot TO <support-bot-role>;
```

## Required Secrets

Create a Kubernetes Secret named `support-bot` with Slack credentials referenced by `values.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: support-bot
type: Opaque
stringData:
  SLACK_TOKEN: "your-slack-bot-token"
  SLACK_SOCKET_TOKEN: "your-slack-socket-token"
  SLACK_SIGNING_SECRET: "your-slack-signing-secret"
```

Apply it before installing the chart:

```bash
kubectl apply -f support-bot.yaml
```

This secret is referenced by default from environment variables.
If you override environment variables, you might not need to set up this secret, depending on your configuration.

## Configuration

### ConfigMap

This chart can template an `application.yaml` and mount it at `/app/config` when enabled.

- `configMap.create` (bool): Create and mount a ConfigMap. Default `true`.
- `configMap.enabled` (bool): Enable ConfigMap mount. If true and `configMap.create` is false, it's expected that you created a ConfigMap yourself.
- `configMap.name` (string): Optional name for the created ConfigMap; defaults to the chart’s full name.
- `configMap.annotations` (map): Annotations for the ConfigMap.
- `configMap.config` (map): Content rendered into `application.yaml`.

Example in `values.yaml`:

```yaml
configMap:
  create: true
  config:
    spring:
      profiles:
        active: default
    some:
      key: value
```

## Auth Allow-List

Restrict SSO login to specific email addresses and/or domains:

```yaml
auth:
  allowedDomains:
    - example.com
    - corp.io
  allowedEmails:
    - external.contractor@partner.com
    - special.user@other.org
```

- `auth.allowedEmails` (list): Email addresses allowed to log in. Default `[]`.
- `auth.allowedDomains` (list): Email domains allowed to log in (any user `@domain`). Default `[]`.

When both lists are empty (the default), all SSO-authenticated users are allowed. When either list is configured, only matching users can log in. Users not in the allow-list see an "Access Restricted" page.

These values are rendered into the ConfigMap `application.yaml` under `security.allow-list`.

## Health and Metrics

- Health endpoint: `/health` on port `8081` (used by liveness/readiness).
- When `metrics.enabled=true`, metrics are exposed on port `8081` and a `metrics` Service port is added.

## Bundled Dex

The chart depends on the upstream [`dex/dex`](https://charts.dexidp.io) subchart and installs it by default. The API's `DEX_CLIENT_ID` / `DEX_CLIENT_SECRET` / `DEX_ISSUER_URI` / `DEX_INTERNAL_BASE_URL` env vars are wired automatically; the UI's `BACKEND_URL` is set to the in-cluster API svc.

To **opt out** (e.g. you deploy Dex separately and don't want a second one), set `dex.enabled: false`.

Two ways to wire authentication when `dex.enabled=true`:

### Mode (a) — external IdP

Point Dex at your real IdP via [Dex connectors](https://dexidp.io/docs/connectors/) (Google, Microsoft, LDAP, generic OIDC). Team membership keeps coming from your existing `platform-integration` sources (Azure / GCP / static-user / `jwt-groups`).

```yaml
publicWebOrigin: https://support-bot.example.com   # one URL → UI_ORIGIN, NEXTAUTH_URL, Dex redirectURIs

dex:
  config:
    issuer: https://dex.example.com                # browser-reachable Dex URL
    staticClients:
      - id: support-bot
        name: Support Bot
        secret: <dex-oauth-client-secret>          # required; no default
    connectors:
      - type: google
        id: google
        name: Google
        config:
          clientID: <google-client-id>
          clientSecret: <google-client-secret>
          redirectURI: https://dex.example.com/callback
```

The chart auto-derives Dex `redirectURIs` from `publicWebOrigin` (both `/login/oauth2/code/dex` and `/api/oauth/callback/dex`); override `dex.config.staticClients[0].redirectURIs` if you need more.

### Mode (b) — bundled static users

Pre-seed four users (one per role) without any external IdP. Useful for demos, local tests, ephemeral environments. Each user logs in at Dex's "Log in with Email" form; the chart fans the four emails out into both Dex (`staticPasswords`) and the API's role wiring (`team.support.static`, `team.leadership.static`, `platform-integration.static-user`, `enums.escalation-teams`) so login → role works end-to-end.

```yaml
publicWebOrigin: http://localhost:3000

dex:
  config:
    issuer: http://localhost:5556
    staticClients:
      - id: support-bot
        name: Support Bot
        secret: <dex-oauth-client-secret>

bundled:
  staticUsers:
    enabled: true
    leadership: { email: leadership@supportbot.local, userID: 11111111-1111-1111-1111-111111111111, passwordHash: <bcrypt> }
    support:    { email: support@supportbot.local,    userID: 22222222-2222-2222-2222-222222222222, passwordHash: <bcrypt> }
    escalation: { email: escalation@supportbot.local, userID: 33333333-3333-3333-3333-333333333333, passwordHash: <bcrypt> }
    tenant:     { email: tenant@supportbot.local,     userID: 44444444-4444-4444-4444-444444444444, passwordHash: <bcrypt> }
```

Generate each bcrypt hash with `htpasswd` (Apache httpd — preinstalled on macOS; on Linux install `apache2-utils` / `httpd-tools`):

```bash
htpasswd -bnBC 10 "" 'changeme' | tr -d ':\n' | sed 's/^\$2y/\$2a/'
```

The `tr` drops the leading `:` and trailing newline that `htpasswd` emits; the `sed` rewrites the `$2y$` prefix to `$2a$` (Dex's bcrypt library accepts `$2a` only).

**Resulting JWT roles:**

| User email | API role |
|---|---|
| `leadership@…` | `ROLE_LEADERSHIP` |
| `support@…` | `ROLE_SUPPORT_ENGINEER` |
| `escalation@…` | `ROLE_ESCALATION` |
| `tenant@…` | *(none — plain tenant user)* |

For a full end-to-end walkthrough (kind cluster, Postgres, image build, install, port-forward, log in), see [docs/local-kind-test.md](../docs/local-kind-test.md).

### Required values (fail-fast validation)

The chart fails at `helm install` time — not at pod-runtime — if any of these are missing:

| Value | Required when | Error message |
|---|---|---|
| `dex.config.staticClients[0].secret` | `dex.enabled=true` | `dex.config.staticClients[0].secret is required when dex.enabled=true (generate with: openssl rand -hex 32)` |
| `bundled.staticUsers.<role>.passwordHash` (×4) | `bundled.staticUsers.enabled=true` | ``bundled.staticUsers.<role>.passwordHash is required when bundled.staticUsers.enabled=true (generate with: htpasswd -bnBC 10 "" PASSWORD \| tr -d ':\n' \| sed 's/^\$2y/\$2a/')`` |
| `ui.env` includes `AUTH_SECRET` or `NEXTAUTH_SECRET` | `ui.enabled=true` | `ui.env must include AUTH_SECRET (or NEXTAUTH_SECRET) when ui.enabled=true (generate with: openssl rand -base64 32)` |

### Bundled Dex install steps

```bash
# Pull the dex subchart (creates helm-chart/Chart.lock + helm-chart/charts/dex-<ver>.tgz)
helm dependency update helm-chart/

# Install with your overlay
helm install support-bot ./helm-chart -f your-values.yaml
```

`Chart.lock` is committed — `helm dependency update` only re-pulls when `Chart.yaml` changes.

### Air-gapped / private registry

For clusters with no public-internet access (e.g. EKS with restricted egress), the chart-images **and** the Dex subchart need to be mirrored. None of the chart logic changes — the overrides go in your values overlay.

**Images.** Override repositories to point at your internal registry. The API/UI fields are the existing chart values; the Dex field flows into the `dex/dex` subchart natively:

```yaml
image:
  repository: <private-registry>/coreeng/support-bot          # API
  tag: <pinned-version>
ui:
  image:
    repository: <private-registry>/coreeng/support-bot-ui     # UI
    tag: <pinned-version>
dex:
  image:
    repository: <private-registry>/dexidp/dex                 # Dex (subchart)
    tag: v2.44.0
```

Image pull secrets are **per-chart** — the top-level `imagePullSecrets` only attaches to the API/UI pods; the Dex pod is owned by the subchart and reads its own `dex.imagePullSecrets` field. Set both if the same Secret covers all three:

```yaml
imagePullSecrets:
  - name: private-registry-creds
dex:
  imagePullSecrets:
    - name: private-registry-creds
```

(Create the Secret in the install namespace before `helm install`.)

**Subchart.** `helm dependency update` reaches out to `https://charts.dexidp.io` to fetch `dex-0.24.0.tgz`. In an air-gapped cluster the install host can't either. Two options:

1. **Pre-pull on an internet-connected machine and ship the bundle.** Run `helm dependency update helm-chart/` somewhere with internet, then transfer the whole `helm-chart/` directory — including `charts/dex-*.tgz` — to the air-gapped host. `helm install ./helm-chart` works without any further network access.
2. **Mirror the Dex chart repo.** Host `charts.dexidp.io` contents on your internal chart repo (Harbor / Artifactory / Nexus), then change `Chart.yaml`'s `dependencies[0].repository` to the internal URL and re-run `helm dependency update`.

(Postgres is installed separately via the Bitnami chart — mirror that chart and its `bitnamilegacy/postgresql` image the same way.)
