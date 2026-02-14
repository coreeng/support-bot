# Support Bot Helm Chart

This chart deploys the Support Bot (a Slack bot that manages support requests) to Kubernetes.

## Install

From the repository root (or any path where `k8s/service` is accessible):

```bash
helm install support-bot oci://ghcr.io/coreeng/charts/support-bot \
  --set image.repository="ghcr.io/coreeng/support-bot" \
  --set image.tag="<your-image-tag>"
```

Notes:
- `image.tag` defaults to the chart `appVersion` when empty which corresponds to the bot version that it was built with.
- If your image requires credentials, set `imagePullSecrets` (see Configuration).

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

Use Secrets for credentials in real environments by overriding the `env` entries (see below).

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
