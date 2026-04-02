# LDAP module (local + integration)

OpenLDAP for Support Bot, independent of Dex. Use this for local smoke tests and for the integration cluster deployment (Dex LDAP connector wiring is Stage 3).

Operational **order** and **troubleshooting**: [docs/runbooks/auth-dex-ldap.md](../docs/runbooks/auth-dex-ldap.md) (local: start LDAP before Dex; integration: deploy LDAP before Dex).

## Local

1. Copy `ldap/.env.example` to `ldap/.env.local` and set **`LDAP_ADMIN_PASSWORD`**. Set **`LDAP_BOOTSTRAP_USER_PASSWORD`** too unless you are fine with the example default: `make run-local` loads `.env.example` first, then `.env.local`, so a password for `alice` / `bob` is always defined when the example file is present.
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

Tracked under `ldap/bootstrap/`: `10-ou.ldif`, `30-groups.ldif`, and **`20-users.ldif.template`** (no password hashes in git). User entries are generated as **`ldap/bootstrap/20-users.ldif`** by:

```bash
make -C ldap render-bootstrap-users
```

Export **`LDAP_BOOTSTRAP_USER_PASSWORD`** in the shell before **`make render-bootstrap-users`**, or rely on **`.env.local`** when using **`make run-local`** (which loads it). **Integration** and **CI** should set the same variable from a secret (e.g. **`INTEGRATION_LDAP_USERS_PASSWORD`** or repository secret **`LDAP_BOOTSTRAP_USER_PASSWORD`**).

Prerequisites: **`slappasswd`** (`apt install ldap-utils` on Linux) or **Docker** (script uses `osixia/openldap:1.5.0` as fallback).

The DIT includes:

- `ou=People`, `ou=Groups` under `dc=supportbot,dc=local`
- Users `alice` / `bob` (password = `LDAP_BOOTSTRAP_USER_PASSWORD` at render time)
- Groups `developers`, `support-admins`, `ldap-leadership` (alice is in all three for local JWT-group testing)

## Integration / Helm

Values and chart live under `api/k8s/ldap/` (`chart/` + `values-bitnami.yaml`). Deploy with `make ldap-deploy-integration` or `helm upgrade` as in [`api/k8s/ldap/README.md`](../api/k8s/ldap/README.md).

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

(`template` / `deploy-integration` render `20-users.ldif` and copy all `ldap/bootstrap/*.ldif` into the chart before Helm.)

Or from this directory:

```bash
make -C ldap template
make -C ldap deploy-integration
```

Set **`LDAP_BOOTSTRAP_USER_PASSWORD`** in the environment when deploying integration (e.g. from **GitHub Actions secrets**).

### Keeping Helm bootstrap in sync

Helm reads LDIF from `api/k8s/ldap/chart/files/bootstrap/`. After changing tracked files (`10-ou.ldif`, `30-groups.ldif`, or `20-users.ldif.template`), run:

```bash
make -C ldap sync-bootstrap-into-chart
```

Commit only changes to **`10-ou.ldif`** / **`30-groups.ldif`** under the chart. **`20-users.ldif`** is generated and **gitignored** in both `ldap/bootstrap/` and the chart directory.

### Service DNS (Kubernetes)

With release name `support-bot-ldap` and `fullnameOverride: ldap`, the ClusterIP service is typically **`ldap`** in the app namespace. Dex should use `ldap:389` when colocated, or the full in-cluster DNS name for your tenant.

## CI

- [`.github/workflows/ldap-fast-feedback.yaml`](../.github/workflows/ldap-fast-feedback.yaml) uses **`coreeng/p2p`** fast feedback (`make p2p-build` in **`ldap/`** → **`template`**). Create repository secret **`LDAP_BOOTSTRAP_USER_PASSWORD`** (plaintext for bootstrap users in rendered LDIF). The workflow merges it with **`secrets.env_vars`** so you do not need to edit the monolithic `env_vars` secret for LDAP. **Fork PRs** do not receive upstream secrets; CI may fail until a maintainer runs checks.

## Troubleshooting (quick)

| Issue | Hint |
|-------|------|
| **`chown` … Read-only file system** on bootstrap LDIF | The compose mount must be writable (not `:ro`); the image adjusts ownership on startup. |
| **`sed: can't read ... replication-disable.ldif`** / container restart loop | Set `LDAP_REMOVE_CONFIG_AFTER_SETUP=false` (already in `docker-compose.yaml`). If the DB volume was created during a broken run, `docker compose down -v` and start again. |
| Cannot connect from **Dex** container | Use `host.docker.internal:389` or attach Dex and LDAP to the same Docker network; see [runbook](../docs/runbooks/auth-dex-ldap.md). |
| **Wrong members** after seed change | Remove OpenLDAP volumes and recreate the stack so bootstrap LDIF runs on a fresh database (`docker compose down -v`). |
| **`render-bootstrap-users` fails** | Install `ldap-utils` or ensure Docker can run `osixia/openldap:1.5.0`. |
| **`LDAP_BOOTSTRAP_USER_PASSWORD is required`** | Export it, add it to **`.env.local`** for `run-local`, or configure the **GitHub Actions** secret / deploy env (see CI section above). |
