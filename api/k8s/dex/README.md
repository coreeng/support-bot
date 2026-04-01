# Dex deployment values (core-platform-app)

This directory contains Dex values for deploying with the platform chart
`core-platform-app`.

Dex runs on the upstream image `ghcr.io/dexidp/dex` and reads config from a
mounted `config.yaml`.

## Files

- `values.yaml` - baseline values.
- `values-integration.yaml` - sample integration overrides.

Set **`dex.enablePasswordDb: false`** in values to disable Dex’s static email/password screen and rely on connectors only (ensure LDAP and/or Google/Microsoft is enabled).

## Required secret

Create a secret named `dex-secrets` with OAuth client secret used by Dex
`staticClients`, plus optional connector credentials:

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
  # When dex.google.enabled is true — Google OAuth client for Dex (not the API’s GOOGLE_* vars).
  google-client-id: ""
  google-client-secret: ""
  # When dex.microsoft.enabled is true — Entra ID app for Dex (not the API’s AZURE_* SSO vars).
  microsoft-client-id: ""
  microsoft-client-secret: ""
```

Set `dex.microsoft.tenant` in Helm values (e.g. `common` or your tenant UUID). Use non-empty client id/secret only for connectors you enable; empty keys keep the Deployment valid when a connector is off.

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

## Connectors: LDAP, Google, Microsoft (Azure AD)

`values.yaml` can enable any combination of:

- `**dex.ldap.enabled**` — LDAP with group search (JWT `groups` for Support Bot `jwt-groups`).
- `**dex.google.enabled**` — [Dex Google connector](https://dexidp.io/docs/connectors/google/). Register a **Web application** OAuth client in Google Cloud whose **authorized redirect URI** is exactly:
  `{dex.issuer}/callback`  
  (example: `https://dex.example.com/callback` — no path on the Support Bot API.)
- `**dex.microsoft.enabled`** — [Dex Microsoft connector](https://dexidp.io/docs/connectors/microsoft/). Register an app in Microsoft Entra ID; add the same `{dex.issuer}/callback` as a **Web** redirect URI. Set `dex.microsoft.tenant` in values to your tenant ID or `common` / `organizations` as appropriate.

Support Bot still uses only the **Dex** OIDC client (`DEX_CLIENT_ID` / `DEX_ISSUER_URI` on the API). End users authenticate via Dex; Dex routes them to LDAP, Google, or Microsoft. For **Dex-only login buttons** on the Support Bot UI while `GOOGLE_`* / `AZURE_*` remain set for other uses, configure `security.oauth2.login-providers: [dex]` on the API (see [configuration.md](../../service/docs/configuration.md)).

## Integration deploy order (with LDAP)

When both modules run in Kubernetes, apply **LDAP before Dex** so the LDAP Service exists, then point Dex at it (`dex.ldap.host`, e.g. `ldap:389` when colocated). Deploy or upgrade the **Support Bot API** after Dex with matching `DEX_`* env vars. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

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

