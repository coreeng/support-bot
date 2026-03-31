# Dex deployment values (core-platform-app)

This directory contains Dex values for deploying with the platform chart
`core-platform-app`.

Dex runs on the upstream image `ghcr.io/dexidp/dex` and reads config from a
mounted `config.yaml`.

## Files

- `values.yaml` - baseline values.
- `values-integration.yaml` - sample integration overrides.

## Required secret

Create a secret named `dex-secrets` with OAuth client secret used by Dex
`staticClients`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: dex-secrets
type: Opaque
stringData:
  client-secret: "<support-bot-dex-client-secret>"
```

Apply:

```bash
kubectl apply -f dex-secrets.yaml
```

## Install / upgrade

If you have the chart locally:

```bash
helm upgrade --install support-bot-dex \
  "/Users/tomaszbartosiewicz/projects/cecg/core-platform-assets/charts/core-platform-app" \
  -f api/k8s/dex/values.yaml
```

With env overrides:

```bash
helm upgrade --install support-bot-dex \
  "/Users/tomaszbartosiewicz/projects/cecg/core-platform-assets/charts/core-platform-app" \
  -f api/k8s/dex/values.yaml \
  -f api/k8s/dex/values-integration.yaml
```

## Support Bot API wiring

Set these on the Support Bot API deployment:

- `DEX_CLIENT_ID` = `dex.clientId` from Dex values.
- `DEX_CLIENT_SECRET` = the same secret as `dex-secrets/client-secret`.
- `DEX_ISSUER_URI` = `dex.issuer` from Dex values.

Ensure Dex redirect URI list includes:

- `https://<your-api-domain>/login/oauth2/code/dex`

The issuer URL must match ingress host/path exactly.
