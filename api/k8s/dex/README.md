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
  # Required when dex.ldap.enabled is true (bind password for LDAP connector).
  ldap-bind-password: ""
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

## Integration deploy order (with LDAP)

When both modules run in Kubernetes, apply **LDAP before Dex** so the LDAP Service exists, then point Dex at it (`dex.ldap.host`, e.g. `ldap:389` when colocated). Deploy or upgrade the **Support Bot API** after Dex with matching `DEX_*` env vars. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Support Bot API wiring

Set these on the Support Bot API deployment:

- `DEX_CLIENT_ID` = `dex.clientId` from Dex values.
- `DEX_CLIENT_SECRET` = the same secret as `dex-secrets/client-secret`.
- `DEX_ISSUER_URI` = `dex.issuer` from Dex values.

Ensure Dex redirect URI list includes:

- `https://<your-api-domain>/login/oauth2/code/dex`

The issuer URL must match ingress host/path exactly.

## Stage 1 lifecycle

This module is operated through root `Makefile` commands:

```bash
# Validate Dex values render against core-platform-app
make dex-template

# Deploy Dex module in integration
make dex-deploy-integration

# Deploy Dex module in production
make dex-deploy-prod
```

Automation workflows:

- `.github/workflows/dex-fast-feedback.yaml`
