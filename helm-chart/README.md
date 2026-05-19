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
- Dex ships as an optional subchart for self-contained auth — disabled by default; set `dex.enabled: true` to use it. See [Bundled Dex](#bundled-dex) for the two wiring modes.
- For a full end-to-end try-out on a local kind cluster (Postgres + API + UI + bundled Dex + four pre-seeded users), see [Local kind end-to-end walkthrough](#local-kind-end-to-end-walkthrough) below.

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

The chart depends on the upstream [`dex/dex`](https://charts.dexidp.io) subchart. It's **disabled by default** — set `dex.enabled: true` to use it. When enabled, the API's `DEX_CLIENT_ID` / `DEX_CLIENT_SECRET` / `DEX_ISSUER_URI` / `DEX_INTERNAL_BASE_URL` env vars are wired automatically; the UI's `BACKEND_URL` is set to the in-cluster API svc.

Two ways to wire authentication when `dex.enabled=true`:

### Mode (a) — external IdP

Point Dex at your real IdP via [Dex connectors](https://dexidp.io/docs/connectors/) (Google, Microsoft, LDAP, generic OIDC). Team membership keeps coming from your existing `platform-integration` sources (Azure / GCP / static-user / `jwt-groups`).

```yaml
publicWebOrigin: https://support-bot.example.com   # one URL → UI_ORIGIN, NEXTAUTH_URL, Dex redirectURIs

dex:
  enabled: true
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
  enabled: true
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

For a full end-to-end walkthrough (kind cluster, Postgres, image build, install, port-forward, log in), see [Local kind end-to-end walkthrough](#local-kind-end-to-end-walkthrough) below.

### Required values (fail-fast validation)

The chart fails at `helm install` time — not at pod-runtime — if any of these are missing:

| Value | Required when | Error message |
|---|---|---|
| `dex.config.staticClients[0].secret` **or** `dex.envVars[DEX_CLIENT_SECRET]` | `dex.enabled=true` | `dex client secret is required when dex.enabled=true. Set EITHER dex.config.staticClients[0].secret (inline, generate with: openssl rand -hex 32) OR add an entry to dex.envVars named DEX_CLIENT_SECRET with valueFrom.secretKeyRef pointing to your externally-managed Secret.` |
| `bundled.staticUsers.<role>.passwordHash` (×4) | `bundled.staticUsers.enabled=true` | ``bundled.staticUsers.<role>.passwordHash is required when bundled.staticUsers.enabled=true (generate with: htpasswd -bnBC 10 "" PASSWORD \| tr -d ':\n' \| sed 's/^\$2y/\$2a/')`` |
| `ui.env` includes `AUTH_SECRET` or `NEXTAUTH_SECRET` | `ui.enabled=true` | `ui.env must include AUTH_SECRET (or NEXTAUTH_SECRET) when ui.enabled=true (generate with: openssl rand -base64 32)` |

### Externalising the Dex client secret

For operators using sealed-secrets, external-secrets-operator, vault, etc., the client secret can live in an externally-managed Kubernetes Secret instead of in `values.yaml`. Leave `dex.config.staticClients[0].secret` empty and add an entry to `dex.envVars`:

```yaml
dex:
  enabled: true
  envVars:
    - name: DEX_CLIENT_SECRET
      valueFrom:
        secretKeyRef:
          name: support-bot-dex-creds   # operator-managed
          key: client-secret
  config:
    issuer: https://dex.example.com
    staticClients:
      - id: support-bot
        name: Support Bot
        # `secret:` intentionally omitted — sourced from dex.envVars above
```

When the chart sees the `DEX_CLIENT_SECRET` envVars entry it:

- writes `secret: $DEX_CLIENT_SECRET` into the rendered Dex config Secret (Dex's `expand_env` feature flag is default-true in v2.44.0, so `os.ExpandEnv` resolves the placeholder at pod startup);
- skips the `client-secret` key in the chart's mirror Secret;
- points the API container's `DEX_CLIENT_SECRET` env at the *same* `valueFrom` the operator provided — so the operator manages one Secret in one place.

Setting both `dex.config.staticClients[0].secret` **and** a `DEX_CLIENT_SECRET` entry in `dex.envVars` is ambiguous and fails fast.

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

## Local kind end-to-end walkthrough

End-to-end walkthrough for trying the chart's **bundled Dex + staticUsers** mode on a local kind cluster. You'll get Postgres + Support Bot API + UI + Dex running together, with four pre-seeded users (leadership / support / escalation / tenant) — log in as any of them and the JWT carries the right role. No external IdP, no LDAP, no real Slack workspace required.

For the chart options themselves, see [Bundled Dex](#bundled-dex) above. This section is the runnable recipe.

### Prerequisites

- A working Docker daemon (Docker Desktop, Colima, Rancher Desktop, or Lima — anything that gives you `docker` and an OCI runtime)
- `kind` v0.20+
- `kubectl` v1.28+
- `helm` v3.13+
- This repo checked out locally

On macOS with Homebrew:

```bash
brew install kind kubectl helm
```

All commands below assume your CWD is the repo root.

### 1. Build images locally

The chart deploys the API, UI, and (via subchart) upstream Dex from `ghcr.io/dexidp/dex`. We only need to build API and UI.

```bash
docker buildx build --load -t support-bot:dev    api/
docker buildx build --load -t support-bot-ui:dev ui/
```

(The repo's `Makefile` has `build-api-app` / `build-ui-app` targets that use p2p-tagged image names — easier to bypass for local kind testing.)

### 2. Create kind cluster and load images

```bash
kind create cluster --name support-bot

kind load docker-image support-bot:dev    --name support-bot
kind load docker-image support-bot-ui:dev --name support-bot
```

`kind load` copies the local image into the cluster's containerd so `imagePullPolicy: IfNotPresent` finds it without an external registry.

### 3. Install Postgres (Bitnami)

The chart's default env points at a service called `support-bot-db-postgresql:5432`, so this release name is load-bearing.

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update bitnami

helm install support-bot-db bitnami/postgresql \
  --set image.repository=bitnamilegacy/postgresql \
  --set global.postgresql.auth.postgresPassword=rootpassword \
  --set global.postgresql.auth.username=supportbot \
  --set global.postgresql.auth.password=supportbotpassword \
  --set global.postgresql.auth.database=supportbot \
  --wait
```

Verify:

```bash
kubectl get pod -l app.kubernetes.io/instance=support-bot-db
# expect: support-bot-db-postgresql-0   1/1   Running
```

### 4. Generate bcrypt hashes for the four users

Each role needs an explicit `passwordHash` — the chart fails install if any are missing. Use `htpasswd` (Apache httpd; preinstalled on macOS, on Linux install `apache2-utils` / `httpd-tools`):

```bash
for role in leadership support escalation tenant; do
  hash=$(htpasswd -bnBC 10 "" 'changeme' | tr -d ':\n' | sed 's/^\$2y/\$2a/')
  echo "$role: $hash"
done
```

The `tr` strips htpasswd's leading `:` and trailing newline; the `sed` rewrites the `$2y$` prefix to `$2a$` (Dex's bcrypt accepts `$2a` only).

Capture the four `$2a$10$...` strings. Reusing the same password (`changeme`) across all four roles is fine for local testing.

### 5. Create the values overlay

Save as `/tmp/kind-values.yaml`. Replace `<HASH_*>` placeholders with the four bcrypt hashes from step 4.

```yaml
# UI's public URL (port-forwarded). The chart sets:
#   - UI_ORIGIN on the API
#   - NEXTAUTH_URL on the UI
#   - Dex staticClients[0].redirectURIs (both /login/oauth2/code/dex and /api/oauth/callback/dex)
publicWebOrigin: http://localhost:3000

image:
  repository: support-bot
  tag: dev
  pullPolicy: IfNotPresent

# Override the default env list so we don't depend on a `support-bot` Secret with real Slack creds.
env:
  - name: DB_URL
    value: "jdbc:postgresql://support-bot-db-postgresql:5432/supportbot"
  - name: DB_USERNAME
    value: supportbot
  - name: DB_PASSWORD
    value: supportbotpassword
  - name: JWT_SECRET
    value: dev-jwt-secret-min-256-bits-please-rotate-for-anything-real
  - name: SLACK_TOKEN
    value: dummy-slack-bot-token
  - name: SLACK_SOCKET_TOKEN
    value: dummy-slack-socket-token
  - name: SLACK_SIGNING_SECRET
    value: dummy-slack-signing-secret
  - name: SLACK_TICKET_CHANNEL_ID
    value: C0000000000

# Disable Slack live wiring so the pod can start without a real workspace.
configMap:
  config:
    slack:
      mode: http
      enable-request-verification: false

ui:
  enabled: true
  image:
    repository: support-bot-ui
    tag: dev
    pullPolicy: IfNotPresent
  # NextAuth requires AUTH_SECRET (or NEXTAUTH_SECRET) — the UI fails to start without it.
  # Generate once: openssl rand -base64 32
  env:
    - name: AUTH_SECRET
      value: "<run: openssl rand -base64 32 and paste the output here>"

# Dex subchart values. Issuer is the browser-reachable URL — for port-forward access,
# that's http://localhost:5556. The chart computes DEX_INTERNAL_BASE_URL separately
# (svc FQDN) for in-cluster server-to-server calls.
dex:
  enabled: true
  config:
    issuer: http://localhost:5556
    staticClients:
      - id: support-bot
        name: Support Bot
        # Generate once: openssl rand -hex 32
        secret: "<run: openssl rand -hex 32 and paste the output here>"

bundled:
  staticUsers:
    enabled: true
    leadership:
      email: leadership@supportbot.local
      userID: 11111111-1111-1111-1111-111111111111
      passwordHash: "<HASH_LEADERSHIP>"
    support:
      email: support@supportbot.local
      userID: 22222222-2222-2222-2222-222222222222
      passwordHash: "<HASH_SUPPORT>"
    escalation:
      email: escalation@supportbot.local
      userID: 33333333-3333-3333-3333-333333333333
      passwordHash: "<HASH_ESCALATION>"
    tenant:
      email: tenant@supportbot.local
      userID: 44444444-4444-4444-4444-444444444444
      passwordHash: "<HASH_TENANT>"
```

### 6. Install the chart

The chart depends on `dex/dex` from `charts.dexidp.io`, so pull the subchart first:

```bash
helm dependency update helm-chart/
```

Render once to sanity-check before applying:

```bash
helm template support-bot ./helm-chart -f /tmp/kind-values.yaml > /tmp/rendered.yaml
# Skim the rendered output: should contain Deployment/support-bot, Deployment/support-bot-ui,
# Deployment/support-bot-dex, Secret/support-bot-dex-config, Secret/support-bot-dex-client.
```

Install:

```bash
helm install support-bot ./helm-chart -f /tmp/kind-values.yaml --wait --timeout 5m
```

If `--wait` times out, drop it and inspect with `kubectl get pod` / `kubectl describe pod` — the API may need a couple of restarts while Postgres warms up.

### 7. Verify and port-forward

```bash
kubectl get pod
# Expect all of:
#   support-bot-xxxxx              1/1  Running
#   support-bot-ui-xxxxx           1/1  Running
#   support-bot-dex-xxxxx          1/1  Running
#   support-bot-db-postgresql-0    1/1  Running
```

Open three port-forwards in separate terminals (or background with `&`):

```bash
kubectl port-forward svc/support-bot-ui 3000:3000      # UI         → http://localhost:3000
kubectl port-forward svc/support-bot-dex 5556:5556     # Dex issuer → http://localhost:5556
kubectl port-forward svc/support-bot 8080:8080         # API (optional, for debugging) → http://localhost:8080
```

### 8. Log in

1. Browse to <http://localhost:3000>.
2. Click "Sign in" → you should be redirected to Dex's "Log in with Email" form.
3. Enter `leadership@supportbot.local` / `changeme` (or `support@…`, `escalation@…`, `tenant@…`).
4. Successful login redirects back to the UI; the session JWT carries the role.

To confirm the role end-to-end, copy the session cookie / JWT from the browser and decode it (jwt.io or `jq` after base64). Look for `ROLE_LEADERSHIP` / `ROLE_SUPPORT_ENGINEER` / `ROLE_ESCALATION` in the authorities claim. The "tenant" user should have no role (the API treats it as a plain tenant user — can file tickets but no admin actions).

### Likely failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| `helm install` fails with `bundled.staticUsers.<role>.passwordHash is required` | Skipped step 4 or empty hash in values | Run the bcrypt loop, paste hashes into the overlay |
| `helm install` fails with `dex.config.staticClients[0].secret is required` | Empty `dex.config.staticClients[0].secret` | Set it (any non-empty string for local) |
| API pod CrashLoopBackOff with `Could not resolve placeholder 'SLACK_TICKET_CHANNEL_ID'` | Missing env var | Verify the full `env:` block in step 5 made it into the overlay |
| API pod CrashLoopBackOff complaining about `UI_ORIGIN` or `redirect-uri` | `publicWebOrigin` mismatch with NEXTAUTH_URL origin | Both come from `publicWebOrigin` automatically — check `kubectl describe pod support-bot-xxxxx` env to confirm `UI_ORIGIN=http://localhost:3000` |
| Dex login redirect fails with `redirect_uri did not match` | redirectURIs not derived correctly | Inspect Secret/support-bot-dex-config: `kubectl get secret support-bot-dex-config -o jsonpath='{.data.config\.yaml}' \| base64 -d` — should list `http://localhost:3000/login/oauth2/code/dex` and `http://localhost:3000/api/oauth/callback/dex` |
| Browser redirected to `http://support-bot-dex.default.svc.cluster.local:5556/...` | Issuer wasn't overridden to `localhost:5556` | Confirm `dex.config.issuer: http://localhost:5556` in the overlay and reinstall |
| Pod `ImagePullBackOff` for `support-bot:dev` | Image wasn't loaded into kind | Re-run `kind load docker-image support-bot:dev --name support-bot` |
| UI pod CrashLoopBackOff with `Missing required environment variables: AUTH_SECRET` | `ui.env` doesn't include `AUTH_SECRET` | Add it to the overlay (see step 5) and `helm upgrade` |
| Pod logs end with `to create fsnotify watcher: too many open files` | Host inotify limit hit (common on Docker Desktop / Lima after many crash loops) | `docker exec -it <kind-node> sysctl fs.inotify.max_user_instances=512 fs.inotify.max_user_watches=524288` or restart Docker |

### Cleanup

```bash
helm uninstall support-bot
helm uninstall support-bot-db
kind delete cluster --name support-bot
```

### Notes

- This recipe exercises **mode (b)** — bundled Dex with `staticUsers`. For **mode (a)** (external IdP via `dex.config.connectors`), drop the `bundled.staticUsers` block, set `dex.config.connectors` instead (Google / Microsoft / LDAP / generic OIDC), and team membership comes from `platform-integration.static-user` / Azure / GCP rather than the chart's fan-out. See [Bundled Dex](#bundled-dex) above.
- The Spring `dex` profile is not active; the chart's `configMap.config` overlay handles `slack.mode: http` and `enable-request-verification: false` directly so the API runs without any profile flag.
- The four seeded users use the same password (`changeme` from step 4) — fine for local kind, do not reuse for anything you'd lose sleep over.
- If something explodes outside the troubleshooting table, capture `kubectl logs deploy/support-bot --previous` and `kubectl logs deploy/support-bot-dex` — those two cover the vast majority of bring-up failures.
