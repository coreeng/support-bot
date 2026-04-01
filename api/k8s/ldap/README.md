# LDAP deployment (Bitnami OpenLDAP image)

Helm manifests live in [`chart/`](./chart): a small **support-bot-openldap** chart that runs the [`bitnami/openldap`](https://hub.docker.com/r/bitnami/openldap) container with the same bootstrap DIT as local Docker (`dc=supportbot,dc=local`, seed users/groups).

The vendor Helm package is also published as **`oci://registry-1.docker.io/bitnamicharts/openldap`**. Pulling that OCI chart usually requires [Docker Hub authentication](https://helm.sh/docs/topics/registries/) (`helm registry login registry-1.docker.io`). This repository’s bundled chart avoids that dependency for `helm template` / CI while using the same image and env semantics (`LDAP_ROOT`, `LDAP_CUSTOM_LDIF_DIR`, etc.) described in the [Bitnami OpenLDAP image docs](https://github.com/bitnami/containers/tree/main/bitnami/openldap).

## Files

- `chart/` — Helm chart (Deployment, Service, ConfigMap from `chart/files/bootstrap/*.ldif`, optional PVC).
- `values-bitnami.yaml` — Support Bot defaults (`fullnameOverride: ldap`, image, persistence, `ldap-secrets`).
- `values-integration.yaml` — integration overrides (e.g. `persistence.enabled: false`, larger resources).
- `values-legacy-core-platform-app.yaml` — archived values for the former `core-platform-app` / osixia layout.

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

```bash
helm upgrade --install support-bot-ldap ./api/k8s/ldap/chart \
  -f api/k8s/ldap/values-bitnami.yaml \
  -f api/k8s/ldap/values-integration.yaml
```

Validate render only:

```bash
make ldap-template
```

(`ldap/scripts/helm_ldap.sh` wraps the chart path and value files.)

### Optional: upstream Bitnami OCI chart

After logging in to `registry-1.docker.io`, pin a version with `helm show chart oci://registry-1.docker.io/bitnamicharts/openldap` and install that chart instead, mapping the same domain, admin secret, and custom LDIF. The bundled chart’s `values-bitnami.yaml` is a useful reference for ports and env.

## Integration deploy order (with Dex)

Deploy **LDAP** before Dex when Dex uses the LDAP connector, so the Service is available. With `fullnameOverride: ldap`, colocated Dex should use **`ldap:389`**. Then deploy Dex and the Support Bot API. See [docs/runbooks/auth-dex-ldap.md](../../../docs/runbooks/auth-dex-ldap.md).

## Module docs

See `ldap/README.md` for local Docker usage and seed users.
