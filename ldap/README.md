# LDAP module (local + integration)

OpenLDAP for Support Bot, independent of Dex. Use this for local smoke tests and for the integration cluster deployment (Dex LDAP connector wiring is Stage 3).

Operational **order** and **troubleshooting**: [docs/runbooks/auth-dex-ldap.md](../docs/runbooks/auth-dex-ldap.md) (local: start LDAP before Dex; integration: deploy LDAP before Dex).

## Local

1. Copy `ldap/.env.example` to `ldap/.env.local` and set `LDAP_ADMIN_PASSWORD`.
2. Start the stack:

```bash
make -C ldap run-local
```

- **LDAP:** `localhost:389` (plain LDAP, `LDAP_TLS=false`).
- **phpLDAPadmin:** http://localhost:18081 — bind as `cn=admin,dc=supportbot,dc=local` with your admin password. (Host port `18081` avoids conflict with the API management port `8081`.)

3. Stop:

```bash
make -C ldap down-local
```

### Seed data

`ldap/bootstrap/*.ldif` creates:

- `ou=People`, `ou=Groups` under `dc=supportbot,dc=local`
- Users `alice` / `bob` (password **`password123`** for both — dev only)
- Groups `developers`, `support-admins`

## Integration / Helm

Values live under `api/k8s/ldap/` and deploy with `core-platform-app`, same pattern as Dex.

Create secret `ldap-secrets` in the target namespace:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ldap-secrets
type: Opaque
stringData:
  admin-password: "<strong-password>"
```

From repo root:

```bash
make ldap-template
make ldap-deploy-integration
```

Or from this directory:

```bash
make -C ldap template
make -C ldap deploy-integration
```

### Service DNS (Kubernetes)

With release name `support-bot-ldap` and `fullnameOverride: ldap`, the ClusterIP service is typically **`ldap`** in the app namespace (for example `support-bot-ldap-integration`). Dex (Stage 3) should use a host like `ldap:389` when colocated, or the full in-cluster DNS name for your tenant.

## CI

- `.github/workflows/ldap-fast-feedback.yaml` — P2P fast feedback for the LDAP module (`make p2p-build` → Helm `template`).

## Helm note (core-platform-app)

Current `core-platform-app` chart test hooks under `templates/tests` produce invalid YAML when `envVarsArr` includes `valueFrom` (same pattern as Dex). The module script `ldap/scripts/helm_ldap.sh` pulls the chart and removes `templates/tests` before `helm template` / `helm upgrade`, so local and CI renders succeed. Remove this workaround when the platform chart fixes test templates.

## Keeping seeds in sync

Bootstrap LDIF is duplicated in `api/k8s/ldap/values.yaml` (`configMaps.ldap-bootstrap`). If you change files under `ldap/bootstrap/`, update the ConfigMap entries in `values.yaml` as well.

## Troubleshooting (quick)

| Issue | Hint |
|-------|------|
| **`chown` … Read-only file system** on bootstrap LDIF | The compose mount must be writable (not `:ro`); the image adjusts ownership on startup. |
| **`sed: can't read ... replication-disable.ldif`** / container restart loop | Set `LDAP_REMOVE_CONFIG_AFTER_SETUP=false` (already in `docker-compose.yaml`). If the DB volume was created during a broken run, `docker compose down -v` and start again. |
| Cannot connect from **Dex** container | Use `host.docker.internal:389` or attach Dex and LDAP to the same Docker network; see [runbook](../docs/runbooks/auth-dex-ldap.md). |
| **Wrong members** after seed change | Remove OpenLDAP volumes and recreate the stack so bootstrap LDIF runs on a fresh database (`docker compose down -v`). |
