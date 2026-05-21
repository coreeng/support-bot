#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

# Config
NAMESPACE="${NAMESPACE:-support-bot-functional}"
SERVICE_RELEASE="${SERVICE_RELEASE:-support-bot}"
SERVICE_CHART_PATH="${SERVICE_CHART_PATH:-${SCRIPT_DIR}/../../helm-chart}"
VALUES_FILE="${VALUES_FILE:-}" # optional -f values.yaml
DEPLOY_DB="${DEPLOY_DB:-true}"  # true|false (affects deploy and delete)
DB_RELEASE="${DB_RELEASE:-support-bot-db}"
ACTION="${ACTION:-deploy}"  # deploy|delete
REDEPLOY="${REDEPLOY:-false}" # when ACTION=deploy, uninstall existing first
DELETE_DB="${DELETE_DB:-true}" # when ACTION=delete, also delete DB

IMAGE_REPOSITORY="${SERVICE_IMAGE_REPOSITORY:?SERVICE_IMAGE_REPOSITORY is required}"
IMAGE_TAG="${SERVICE_IMAGE_TAG:-latest}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-180}" # seconds

usage() {
  echo "Usage: ACTION=deploy|delete NAMESPACE=<ns> [options] $0"
  echo "Deploy options:"
  echo "  SERVICE_IMAGE_REPOSITORY=<repo> [SERVICE_IMAGE_TAG=<tag>] [DEPLOY_DB=true|false] [VALUES_FILE=path] [REDEPLOY=true|false]"
  echo "Delete options:"
  echo "  [DELETE_DB=true|false] [DEPLOY_DB=true|false]"
}

reset_db_schema() {
  local ns="$1" release="$2"
  log "Resetting database schema for release [${release}] in namespace ${ns}..."
  local db_pod
  db_pod=$(kubectl get pod -n "$ns" \
    -l "app.kubernetes.io/instance=${release},app.kubernetes.io/name=postgresql" \
    -o jsonpath='{.items[0].metadata.name}')
  kubectl exec -n "$ns" "$db_pod" -- \
    env PGPASSWORD=supportbotpassword \
    psql -U supportbot -d supportbot -c \
    "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
  log_success "Database schema reset complete"
}

deploy_db() {
  local ns="$1" release="$2"
  log "Installing PostgreSQL [${release}] in namespace ${ns}..."
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update bitnami
  helm upgrade --install "$release" bitnami/postgresql -n "$ns" \
    --set image.repository=bitnamilegacy/postgresql \
    --set global.postgresql.auth.postgresPassword=rootpassword \
    --set global.postgresql.auth.username=supportbot \
    --set global.postgresql.auth.password=supportbotpassword \
    --set global.postgresql.auth.database=supportbot \
    --set primary.pdb.create=false \
    --set primary.networkPolicy.enabled=false \
    --set primary.resourcesPreset=small \
    --set serviceAccount.create=false \
    --wait --atomic --timeout=3m
  log_success "PostgreSQL deployed"
  reset_db_schema "$ns" "$release"
}

ensure_chart_deps() {
  local chart_path="$1"
  # Chart.yaml declares dex as a subchart dependency. Helm requires the dep to
  # be present in charts/ before render, even when the active values disable
  # it via `dex.enabled: false`. Skip the fetch if it's already vendored
  # (e.g. baked into an image build, which is necessary for pods that run
  # `helm install` without outbound access to charts.dexidp.io). Helm may
  # leave the dep as a tarball (charts/dex-<ver>.tgz) or unpacked
  # (charts/dex/Chart.yaml).
  if compgen -G "${chart_path}/charts/dex-*.tgz" > /dev/null 2>&1 \
     || [[ -f "${chart_path}/charts/dex/Chart.yaml" ]]; then
    log "Chart dependencies already vendored at ${chart_path}/charts"
    return 0
  fi
  log "Vendoring chart dependencies for ${chart_path}..."
  helm repo add dex https://charts.dexidp.io >/dev/null
  helm repo update dex >/dev/null
  helm dependency build "$chart_path" >/dev/null
}

deploy_service() {
  local ns="$1" release="$2" chart_path="$3" image_repo="$4" image_tag="$5"
  ensure_chart_deps "$chart_path"
  log "Installing service [${release}] in ${ns} from ${chart_path}..."
  local args=(upgrade --install "$release" "$chart_path" -n "$ns" \
    --set image.repository="$image_repo" \
    --set image.tag="$image_tag" \
    --wait --atomic --timeout=5m)
  if [[ -n "${VALUES_FILE}" ]]; then
    args+=( -f "$VALUES_FILE" )
  fi
  helm "${args[@]}"
  log_success "Service deployed"
}

wait_for_service() {
  local ns="$1" release="$2" timeout_secs="${3:-180}"
  # Deployment name often equals release; allow override via RELEASE_DEPLOYMENT_NAME var
  local deploy_name="${RELEASE_DEPLOYMENT_NAME:-$release}"
  log "Waiting for deployment/${deploy_name} rollout..."
  kubectl rollout status deployment/"$deploy_name" -n "$ns" --timeout=${timeout_secs}s
  log "Waiting for pods of ${deploy_name} to be Ready..."
  # Get the current ReplicaSet pod-template-hash to avoid waiting for old terminating pods
  local pod_template_hash=$(kubectl get rs -n "$ns" -l app.kubernetes.io/name="$deploy_name" -o jsonpath='{.items[?(@.spec.replicas>0)].metadata.labels.pod-template-hash}' | head -n1)
  if [[ -n "$pod_template_hash" ]]; then
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name="$deploy_name",pod-template-hash="$pod_template_hash" -n "$ns" --timeout=${timeout_secs}s
  else
    # Fallback to waiting for any pod with the app label (for backwards compatibility)
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name="$deploy_name" -n "$ns" --timeout=${timeout_secs}s
  fi
  log_success "Service pods are Ready"
}

main() {
  if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then usage; exit 0; fi

  case "$ACTION" in
    deploy)
      log "Service deployment configuration:"
      log "  Namespace:        $NAMESPACE"
      log "  Service release:  $SERVICE_RELEASE"
      log "  Chart path:       $SERVICE_CHART_PATH"
      log "  Values file:      ${VALUES_FILE:-<none>}"
      log "  Image:            $IMAGE_REPOSITORY:$IMAGE_TAG"
      log "  Deploy DB:        $DEPLOY_DB (release=$DB_RELEASE)"
      log "  Redeploy:         $REDEPLOY"

      if [[ "$REDEPLOY" == "true" ]]; then
        helm_uninstall_if_exists "$SERVICE_RELEASE" "$NAMESPACE"
        if [[ "$DEPLOY_DB" == "true" ]]; then
          helm_uninstall_if_exists "$DB_RELEASE" "$NAMESPACE"
        fi
      fi

      if [[ "$DEPLOY_DB" == "true" ]]; then
        deploy_db "$NAMESPACE" "$DB_RELEASE"
      fi
      deploy_service "$NAMESPACE" "$SERVICE_RELEASE" "$SERVICE_CHART_PATH" "$IMAGE_REPOSITORY" "$IMAGE_TAG"
      wait_for_service "$NAMESPACE" "$SERVICE_RELEASE" "$WAIT_TIMEOUT"
      ;;
    delete)
      log "Deleting service deployment:"
      log "  Namespace:        $NAMESPACE"
      log "  Service release:  $SERVICE_RELEASE"
      log "  Delete DB:        $DELETE_DB (release=$DB_RELEASE)"
      helm uninstall "$SERVICE_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
      if [[ "$DELETE_DB" == "true" && "$DEPLOY_DB" == "true" ]]; then
        helm uninstall "$DB_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
      fi
      log_success "Deletion finished"
      ;;
    *)
      log_error "Unknown ACTION=$ACTION. Use deploy|delete"
      exit 1
      ;;
  esac
}

# Allow this script to be sourced for reusing functions without executing main
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
