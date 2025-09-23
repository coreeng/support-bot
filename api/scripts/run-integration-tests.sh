#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

# Configuration
NAMESPACE="${NAMESPACE:-support-bot-integration}"
CHART_PATH="${CHART_PATH:-${SCRIPT_DIR}/../k8s/integration-tests}"
RELEASE_NAME="${RELEASE_NAME:-support-bot-integration-tests}"
TIMEOUT="${TIMEOUT:-600}" # 10 mins
JOB_IMAGE_REPOSITORY="${JOB_IMAGE_REPOSITORY:?required argument}"
IMAGE_TAG="${IMAGE_TAG:?required argument}"
SERVICE_IMAGE_REPOSITORY="${SERVICE_IMAGE_REPOSITORY:?required argument}"
SERVICE_IMAGE_TAG="${SERVICE_IMAGE_TAG:?required argument}"
CLEANUP="${CLEANUP:-true}"

cleanup_job() {
  if [[ "$CLEANUP" == "true" ]]; then
    log "Cleaning up Helm release: $RELEASE_NAME"
    helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" --ignore-not-found || true
  else
    log_warning "Cleanup disabled. Helm release $RELEASE_NAME will remain in namespace $NAMESPACE"
  fi
}
trap cleanup_job EXIT

deploy_chart() {
  log "Deploying integration test chart..."
  log "  Release: $RELEASE_NAME"
  log "  Namespace: $NAMESPACE"
  log "  Chart: $CHART_PATH"
  log "  Job Image: $JOB_IMAGE_REPOSITORY:$IMAGE_TAG"
  log "  Service Image: $SERVICE_IMAGE_REPOSITORY:$SERVICE_IMAGE_TAG"

  helm_uninstall_if_exists "$RELEASE_NAME" "$NAMESPACE"

  helm install "$RELEASE_NAME" "$CHART_PATH" \
    --namespace "$NAMESPACE" \
    --set image.repository="$JOB_IMAGE_REPOSITORY" \
    --set image.tag="$IMAGE_TAG" \
    --set configMap.config.namespace="$NAMESPACE" \
    --set configMap.config.service.image.repository="$SERVICE_IMAGE_REPOSITORY" \
    --set configMap.config.service.image.tag="$SERVICE_IMAGE_TAG" \
    --wait --timeout=5m

  log_success "Chart deployed successfully"
}

main() {
  log "Starting integration test deployment and execution"
  log "  Namespace: $NAMESPACE"
  log "  Release: $RELEASE_NAME"
  log "  Chart: $CHART_PATH"
  log "  Job Image: $JOB_IMAGE_REPOSITORY:$IMAGE_TAG"
  log "  Service Image: $SERVICE_IMAGE_REPOSITORY:$SERVICE_IMAGE_TAG"
  log "  Timeout: ${TIMEOUT}s"
  log "  Cleanup: $CLEANUP"

  deploy_chart

  if wait_for_job_with_logs "$RELEASE_NAME" "$NAMESPACE" "$TIMEOUT" "integration-tests"; then
    log_success "Integration tests passed!"
    exit 0
  else
    log_error "Integration tests failed!"
    show_job_status "$RELEASE_NAME" "$NAMESPACE"
    exit 1
  fi
}

if [[ "${1:-}" == "--help" ]] || [[ "${1:-}" == "-h" ]]; then
  echo "Usage: $0 [options]"
  echo ""
  echo "Environment variables:"
  echo "  NAMESPACE                  Kubernetes namespace (default: support-bot-integration)"
  echo "  CHART_PATH                 Path to Helm chart (default: ../k8s/integration-tests)"
  echo "  RELEASE_NAME               Helm release name (default: support-bot-integration-tests)"
  echo "  TIMEOUT                    Job timeout in seconds (default: 1800)"
  echo "  JOB_IMAGE_REPOSITORY       Docker repository for test job container"
  echo "  IMAGE_TAG                  Docker image tag for test container (default: latest)"
  echo "  SERVICE_IMAGE_REPOSITORY   Service Docker repository"
  echo "  SERVICE_IMAGE_TAG          Service Docker image tag (default: latest)"
  echo "  CLEANUP                    Cleanup resources after completion (default: true)"
  exit 0
fi

main "$@"
