# LDAP deployment (Bitnami OpenLDAP image)

Helm manifests live in [`chart/`](./chart): a small **support-bot-openldap** chart that runs the [`bitnami/openldap`](https://hub.docker.com/r/bitnami/openldap) container with the same bootstrap DIT as local Docker (`dc=supportbot,dc=local`, seed users/groups).

The vendor Helm package is also published as **`oci://registry-1.docker.io/bitnamicharts/openldap`**. Pulling that OCI chart usually requires [Docker Hub authentication](https://helm.sh/docs/topics/registries/) (`helm registry login registry-1.docker.io`). This repository’s bundled chart avoids that dependency for `helm template` / CI while using the same image and env semantics (`LDAP_ROOT`, `LDAP_CUSTOM_LDIF_DIR`, etc.) described in the [Bitnami OpenLDAP image docs](https://github.com/bitnami/containers/tree/main/bitnami/openldap).

## Files

- `chart/` — Helm chart (Deployment, Service, ConfigMap from `chart/files/bootstrap/*.ldif`, optional PVC). **`20-users.ldif` is not committed**; `make -C ldap template` / `deploy-integration` generate it from [`ldap/bootstrap/20-users.ldif.template`](../../../ldap/bootstrap/20-users.ldif.template) and copy bootstrap LDIF into `chart/files/bootstrap/` before Helm.
- `values-bitnami.yaml` — Support Bot defaults (`fullnameOverride: ldap`, image, persistence, `ldap-secrets`).
- `values-integration.yaml` — integration overrides (e.g. `persistence.enabled: false`, larger resources).
- `values-integration-ldap-plaintext-ephemeral.yaml` — **opt-in** `tls.enabled: false` for disposable integration (plain **389**). Merged by `ldap/scripts/helm_ldap.sh` for `template` and for `deploy-integration` when **`LDAP_DEPLOY_INSECURE_PLAINTEXT=true`**. Do not use on shared clusters.
- `values-tls.yaml` — LDAPS / TLS (`tls.certSecret`, port **636**).

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

Admin DN for binds (e.g. Dex): `cn=admin,dc=supportbot,dc=local`.

## Install / upgrade

**Ephemeral integration (plain 389)** — same value chain as `make ldap-deploy-integration` from repo root (sets **`LDAP_DEPLOY_INSECURE_PLAINTEXT=true`**):

```bash
helm upgrade --install support-bot-ldap ./api/k8s/ldap/chart \
  -f api/k8s/ldap/values-bitnami.yaml \
  -f api/k8s/ldap/values-integration.yaml \
  -f api/k8s/ldap/values-integration-ldap-plaintext-ephemeral.yaml
```

Without the plaintext overlay, the chart defaults expect **`tls.certSecret`** when `tls.enabled` is true (see `chart/values.yaml`); Helm fails fast to avoid ambiguous insecure deploys.

Validate render only:

```bash
make ldap-template
```

(`ldap/scripts/helm_ldap.sh` wraps the chart path and value files.)

### Optional: upstream Bitnami OCI chart

After logging in to `registry-1.docker.io`, pin a version with `helm show chart oci://registry-1.docker.io/bitnamicharts/openldap` and install that chart instead, mapping the same domain, admin secret, and custom LDIF. The bundled chart’s `values-bitnami.yaml` is a useful reference for ports and env.

## TLS / LDAPS

**Chart defaults** (`chart/values.yaml`) use **`tls.enabled: true`** until you either supply **`tls.certSecret`** (LDAPS — use [`values-tls.yaml`](./values-tls.yaml)) or explicitly opt into ephemeral plaintext with [`values-integration-ldap-plaintext-ephemeral.yaml`](./values-integration-ldap-plaintext-ephemeral.yaml) (`tls.enabled: false`). For production clusters, use **`values-tls.yaml`** and a real TLS Secret:

```bash
helm upgrade --install support-bot-ldap ./api/k8s/ldap/chart \
  -f api/k8s/ldap/values-bitnami.yaml \
  -f api/k8s/ldap/values-tls.yaml \
  [-f api/k8s/ldap/values-integration.yaml]
```

This requires a TLS Secret (`kubectl create secret tls ldap-tls --cert=... --key=...`). The Dex LDAP connector must also switch to StartTLS or LDAPS — see [`api/k8s/dex/values-tls.yaml`](../dex/values-tls.yaml) and [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Integration deploy order (with Dex)

Deploy **LDAP** before Dex when Dex uses the LDAP connector, so the Service is available. With `fullnameOverride: ldap`, colocated Dex should use **`ldap:389`** (or `ldap:636` with TLS). Then deploy Dex and the Support Bot API. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Module docs

See `ldap/README.md` for local Docker usage and seed users.
