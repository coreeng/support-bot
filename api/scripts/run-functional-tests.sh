#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

NAMESPACE="${NAMESPACE:-support-bot-functional}"
SERVICE_CHART_PATH="${SERVICE_CHART_PATH:-${SCRIPT_DIR}/../k8s/service}"
TEST_CHART_PATH="${TEST_CHART_PATH:-${SCRIPT_DIR}/../k8s/functional-tests}"

DB_RELEASE="${DB_RELEASE:-support-bot-db}"
SERVICE_RELEASE="${SERVICE_RELEASE:-support-bot}"
JOB_RELEASE="${JOB_RELEASE:-support-bot-functional-tests}"

TIMEOUT="${TIMEOUT:-60}"

JOB_IMAGE_REPOSITORY="${JOB_IMAGE_REPOSITORY:?JOB_IMAGE_REPOSITORY is required}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
DEPLOY_SERVICE="${DEPLOY_SERVICE:-true}" # if true, deploy service before running tests
CLEAN_DEPLOY_DB="${CLEAN_DEPLOY_DB:-true}" # controls DB deployment via deploy-service.sh
SERVICE_IMAGE_REPOSITORY="${SERVICE_IMAGE_REPOSITORY:?SERVICE_IMAGE_REPOSITORY is required}"
SERVICE_IMAGE_TAG="${SERVICE_IMAGE_TAG:-latest}"
CLEANUP="${CLEANUP:-true}"

cleanup_all() {
  if [[ "$CLEANUP" == "true" ]]; then
    log "Cleaning up Helm releases in namespace: $NAMESPACE"
    helm uninstall "$JOB_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
    helm uninstall "$SERVICE_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
    helm uninstall "$DB_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
  else
    log_warning "Cleanup disabled. Releases will remain in namespace $NAMESPACE"
  fi
}

trap cleanup_all EXIT

main() {
  log "Starting functional test deployment and execution"
  log "  Namespace: $NAMESPACE"
  log "  Service Chart: $SERVICE_CHART_PATH"
  log "  Tests Chart:   $TEST_CHART_PATH"
  log "  Job Image:     $JOB_IMAGE_REPOSITORY:$IMAGE_TAG"
  log "  Service Image: $SERVICE_IMAGE_REPOSITORY:$SERVICE_IMAGE_TAG"
  log "  Timeout:       ${TIMEOUT}s"
  log "  Cleanup:       $CLEANUP"

  kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create ns "$NAMESPACE" >/dev/null

  # Drop any existing job release
  helm_uninstall_if_exists "$JOB_RELEASE" "$NAMESPACE"

  if [[ "$DEPLOY_SERVICE" == "true" ]]; then
    "${SCRIPT_DIR}/deploy-service.sh" \
      ACTION=deploy \
      NAMESPACE="$NAMESPACE" \
      SERVICE_RELEASE="$SERVICE_RELEASE" \
      SERVICE_CHART_PATH="$SERVICE_CHART_PATH" \
      VALUES_FILE="$SERVICE_CHART_PATH/values-functional.yaml" \
      DEPLOY_DB="$CLEAN_DEPLOY_DB" \
      DB_RELEASE="$DB_RELEASE" \
      SERVICE_IMAGE_REPOSITORY="$SERVICE_IMAGE_REPOSITORY" \
      SERVICE_IMAGE_TAG="$SERVICE_IMAGE_TAG" \
      WAIT_TIMEOUT=180
  else
    log_warning "Skipping service deployment per SKIP_SERVICE_DEPLOYMENT=true"
  fi

  log "Installing functional test job [$JOB_RELEASE]..."
  helm upgrade --install "$JOB_RELEASE" "$TEST_CHART_PATH" \
    -n "$NAMESPACE" \
    --set image.repository="$JOB_IMAGE_REPOSITORY" \
    --set image.tag="$IMAGE_TAG" \
    --set job.activeDeadlineSeconds="$TIMEOUT" \
    --wait --timeout=1m
  log_success "Functional test job deployed"

  if wait_for_job_with_logs "$JOB_RELEASE" "$NAMESPACE" "$TIMEOUT"; then
    log_success "Functional tests passed!"
    exit 0
  else
    log_error "Functional tests failed!"
    show_job_status "$JOB_RELEASE" "$NAMESPACE"
    exit 1
  fi
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  echo "Usage: $0 [options]"
  echo ""
  echo "Environment variables:"
  echo "  NAMESPACE                  Kubernetes namespace (default: support-bot-functional)"
  echo "  SERVICE_CHART_PATH         Path to service Helm chart (default: ../k8s/service)"
  echo "  TEST_CHART_PATH            Path to functional-tests Helm chart (default: ../k8s/functional-tests)"
  echo "  DB_RELEASE                 DB Helm release name (default: support-bot-db)"
  echo "  SERVICE_RELEASE            Service Helm release name (default: support-bot)"
  echo "  JOB_RELEASE                Job Helm release name (default: support-bot-functional-tests)"
  echo "  TIMEOUT                    Job timeout in seconds (default: 60)"
  echo "  JOB_IMAGE_REPOSITORY       Docker repository for tests job"
  echo "  IMAGE_TAG                  Docker image tag for job (default: latest)"
  echo "  SERVICE_IMAGE_REPOSITORY   Service Docker repository"
  echo "  SERVICE_IMAGE_TAG          Service Docker image tag (default: latest)"
  echo "  CLEANUP                    Cleanup resources after completion (default: true)"
  exit 0
fi

main "$@"

