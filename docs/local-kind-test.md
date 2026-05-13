# Local kind test — bundled Dex + four staticUsers

End-to-end walkthrough for trying the chart's **bundled Dex + staticUsers** mode on a local kind cluster. You'll get Postgres + Support Bot API + UI + Dex running together, with four pre-seeded users (leadership / support / escalation / tenant) — log in as any of them and the JWT carries the right role. No external IdP, no LDAP, no real Slack workspace required.

For the chart options themselves, see [helm-chart/README.md § Bundled Dex](../helm-chart/README.md#bundled-dex). This document is the runnable recipe.

## Prerequisites

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

## 1. Build images locally

The chart deploys the API, UI, and (via subchart) upstream Dex from `ghcr.io/dexidp/dex`. We only need to build API and UI.

```bash
docker buildx build --load -t support-bot:dev    api/
docker buildx build --load -t support-bot-ui:dev ui/
```

(The repo's `Makefile` has `build-api-app` / `build-ui-app` targets that use p2p-tagged image names — easier to bypass for local kind testing.)

## 2. Create kind cluster and load images

```bash
kind create cluster --name support-bot

kind load docker-image support-bot:dev    --name support-bot
kind load docker-image support-bot-ui:dev --name support-bot
```

`kind load` copies the local image into the cluster's containerd so `imagePullPolicy: IfNotPresent` finds it without an external registry.

## 3. Install Postgres (Bitnami)

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

## 4. Generate bcrypt hashes for the four users

Each role needs an explicit `passwordHash` — the chart fails install if any are missing. Use `htpasswd` (Apache httpd; preinstalled on macOS, on Linux install `apache2-utils` / `httpd-tools`):

```bash
for role in leadership support escalation tenant; do
  hash=$(htpasswd -bnBC 10 "" 'changeme' | tr -d ':\n' | sed 's/^\$2y/\$2a/')
  echo "$role: $hash"
done
```

The `tr` strips htpasswd's leading `:` and trailing newline; the `sed` rewrites the `$2y$` prefix to `$2a$` (Dex's bcrypt accepts `$2a` only).

Capture the four `$2a$10$...` strings. Reusing the same password (`changeme`) across all four roles is fine for local testing.

## 5. Create the values overlay

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

## 6. Install the chart

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

## 7. Verify and port-forward

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

## 8. Log in

1. Browse to <http://localhost:3000>.
2. Click "Sign in" → you should be redirected to Dex's "Log in with Email" form.
3. Enter `leadership@supportbot.local` / `changeme` (or `support@…`, `escalation@…`, `tenant@…`).
4. Successful login redirects back to the UI; the session JWT carries the role.

To confirm the role end-to-end, copy the session cookie / JWT from the browser and decode it (jwt.io or `jq` after base64). Look for `ROLE_LEADERSHIP` / `ROLE_SUPPORT_ENGINEER` / `ROLE_ESCALATION` in the authorities claim. The "tenant" user should have no role (the API treats it as a plain tenant user — can file tickets but no admin actions).

## Likely failure modes and fixes

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

## Cleanup

```bash
helm uninstall support-bot
helm uninstall support-bot-db
kind delete cluster --name support-bot
```

## Notes

- This recipe exercises **mode (b)** — bundled Dex with `staticUsers`. For **mode (a)** (external IdP via `dex.config.connectors`), drop the `bundled.staticUsers` block, set `dex.config.connectors` instead (Google / Microsoft / LDAP / generic OIDC), and team membership comes from `platform-integration.static-user` / Azure / GCP rather than the chart's fan-out. See [helm-chart/README.md § Bundled Dex](../helm-chart/README.md#bundled-dex).
- The Spring `dex` profile is not active; the chart's `configMap.config` overlay handles `slack.mode: http` and `enable-request-verification: false` directly so the API runs without any profile flag.
- The four seeded users use the same password (`changeme` from step 4) — fine for local kind, do not reuse for anything you'd lose sleep over.
- If something explodes outside the troubleshooting table, capture `kubectl logs deploy/support-bot --previous` and `kubectl logs deploy/support-bot-dex` — those two cover the vast majority of bring-up failures.
