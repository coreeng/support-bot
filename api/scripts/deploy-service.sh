#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

# Config
NAMESPACE="${NAMESPACE:-support-bot-functional}"
SERVICE_RELEASE="${SERVICE_RELEASE:-support-bot}"
SERVICE_CHART_PATH="${SERVICE_CHART_PATH:-${SCRIPT_DIR}/../k8s/service}"
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
    --set serviceAccount.create=false \
    --wait --atomic --timeout=3m
  log_success "PostgreSQL deployed"
}

deploy_service() {
  local ns="$1" release="$2" chart_path="$3" image_repo="$4" image_tag="$5"
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
  kubectl wait --for=condition=ready pod -l app.kubernetes.io/name="$deploy_name" -n "$ns" --timeout=${timeout_secs}s
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
