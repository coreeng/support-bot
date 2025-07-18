# Support Bot Helm Chart

This Helm chart deploys the Support Bot application, a Slack bot for managing support queries coming into a Slack channel.

## Database Setup

The chart expects a PostgreSQL database to be available. You can deploy PostgreSQL using the Bitnami chart:

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install support-bot-db bitnami/postgresql \
  --set global.postgresql.auth.postgresPassword=rootpassword \
  --set global.postgresql.auth.username=supportbot \
  --set global.postgresql.auth.password=supportbotpassword \
  --set global.postgresql.auth.database=supportbot
```

## Required Secrets

Before deploying the helm chart, you must create the following secrets in your cluster:

### Azure Secret
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: azure
type: Opaque
stringData:
  AZURE_CLIENT_ID: "your-azure-client-id"
  AZURE_TENANT_ID: "your-azure-tenant-id"
  AZURE_CLIENT_SECRET: "your-azure-client-secret"
```

### Support Bot Secret
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

Apply these secrets to your cluster before deploying:
```bash
kubectl apply -f azure.yaml
kubectl apply -f support-bot.yaml
```

### Alternative: Custom Environment Configuration

Alternatively, you can modify the `env` property in `values.yaml` to use your own secret management approach. This allows you to reference different secrets or use external secret management systems:

```yaml
env:
  - name: AZURE_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: my-custom-azure-secret
        key: client_id
  - name: SLACK_TOKEN
    valueFrom:
      secretKeyRef:
        name: my-custom-slack-secret
        key: bot_token
  # ... other environment variables
```

When using this approach, you'll need to ensure your custom secrets are created and managed according to your own secret management strategy.

## ConfigMap Management

The chart automatically creates ConfigMap to manage application configuration:

### ConfigMap Resource
Contains non-sensitive service configuration:
- Database URL and username
- Slack channel IDs

### Using External Resources

To use an existing ConfigMap resource instead of creating a new one:

```bash
# Use external configmap
helm install support-bot ./k8s/service \
  --set configMap.create=false \
  --set configMap.name=my-existing-configmap
```

## Health Checks

The application provides health endpoints on port 8081:
- `/health` - Health check endpoint used by liveness and readiness probes

## Metrics

When metrics are enabled, Prometheus metrics are available on port 8081.
