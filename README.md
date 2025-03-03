# support-bot api and ui repository

## Requirements

* API needs Postgres to be configured and available
** on dev implemented using local postgress helm deployment

* API pods have to have access to GCP resources and must be able to the namespaces and rolebindings cross cluster it runs in
** on dev cluster level permissions are added manually (as of 03/03/2025), as p2p should not support RBAC permissions elevation via deploying helm chart, following config has been used:

```yaml
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: support-bot-api-rbac
rules:
- apiGroups:
  - ""
  resources:
  - namespaces
  verbs:
  - get
  - list
- apiGroups:
  - rbac.authorization.k8s.io
  resources:
  - rolebindings
  verbs:
  - get
  - list
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: support-bot-api-rbac-binding-extended
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: support-bot-api-rbac
subjects:
- kind: ServiceAccount
  name: support-bot-api
  namespace: support-bot-extended
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: support-bot-api-rbac-binding-functional
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: support-bot-api-rbac
subjects:
- kind: ServiceAccount
  name: support-bot-api
  namespace: support-bot-functional
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: support-bot-api-rbac-binding-integration
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: support-bot-api-rbac
subjects:
- kind: ServiceAccount
  name: support-bot-api
  namespace: support-bot-integration
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: support-bot-api-rbac-binding-nft
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: support-bot-api-rbac
subjects:
- kind: ServiceAccount
  name: support-bot-api
  namespace: support-bot-nft
```
