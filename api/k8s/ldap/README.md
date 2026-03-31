# LDAP deployment values (core-platform-app)

Helm values for OpenLDAP (`osixia/openldap`) via the platform chart `core-platform-app`.

## Files

- `values.yaml` — baseline deployment, bootstrap LDIF, probes, `ldap-secrets` wiring.
- `values-integration.yaml` — integration resource overrides.

## Required secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ldap-secrets
type: Opaque
stringData:
  admin-password: "<openldap-admin-password>"
```

## Install / upgrade

```bash
helm repo add coreeng https://coreeng.github.io/core-platform-assets
helm repo update
helm upgrade --install support-bot-ldap coreeng/core-platform-app \
  -f api/k8s/ldap/values.yaml \
  -f api/k8s/ldap/values-integration.yaml \
  --set tenantName="${TENANT_NAME}"
```

Validate render only:

```bash
make ldap-template
```

(`ldap` uses `scripts/helm_ldap.sh` to work around `core-platform-app` test-hook YAML issues with `secretKeyRef` in `envVarsArr`; see `ldap/README.md`.)

## Integration deploy order (with Dex)

Deploy **LDAP** (this chart) **before** Dex when Dex uses `dex.ldap.enabled`, so the `ldap` Service is available. Then deploy Dex with `dex.ldap.host` set to that Service. Finish with the Support Bot API and `platform-integration.jwt-groups` if you map LDAP groups to teams. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Module docs

See `ldap/README.md` for local Docker usage and seed users.
