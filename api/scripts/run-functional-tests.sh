#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

NAMESPACE="${NAMESPACE:-support-bot-functional}"
SERVICE_CHART_PATH="${SERVICE_CHART_PATH:-${SCRIPT_DIR}/../../helm-chart}"
TEST_CHART_PATH="${TEST_CHART_PATH:-${SCRIPT_DIR}/../k8s/functional-tests}"
WIREMOCK_VALUES_FILE="${WIREMOCK_VALUES_FILE:-${SCRIPT_DIR}/../k8s/wiremock-values.yaml}"
WIREMOCK_CHART_REPO_NAME="${WIREMOCK_CHART_REPO_NAME:-wiremock}"
WIREMOCK_CHART_REPO_URL="${WIREMOCK_CHART_REPO_URL:-https://wiremock.github.io/helm-charts}"
WIREMOCK_CHART_NAME="${WIREMOCK_CHART_NAME:-wiremock}"
WIREMOCK_CHART_VERSION="${WIREMOCK_CHART_VERSION:-1.11.0}"

DB_RELEASE="${DB_RELEASE:-support-bot-db}"
SERVICE_RELEASE="${SERVICE_RELEASE:-support-bot}"
JOB_RELEASE="${JOB_RELEASE:-support-bot-functional-tests}"
WIREMOCK_RELEASE="${WIREMOCK_RELEASE:-support-bot-functional-tests-wiremock}"

TIMEOUT="${TIMEOUT:-180}"

JOB_IMAGE_REPOSITORY="${JOB_IMAGE_REPOSITORY:?JOB_IMAGE_REPOSITORY is required}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
WIREMOCK_IMAGE_REPOSITORY="${WIREMOCK_IMAGE_REPOSITORY:-wiremock/wiremock}"
WIREMOCK_IMAGE_TAG="${WIREMOCK_IMAGE_TAG:-3.13.2}"
DEPLOY_SERVICE="${DEPLOY_SERVICE:-true}" # if true, deploy service before running tests
CLEAN_DEPLOY_DB="${CLEAN_DEPLOY_DB:-true}" # controls DB deployment via deploy-service.sh
SERVICE_IMAGE_REPOSITORY="${SERVICE_IMAGE_REPOSITORY:-}"
SERVICE_IMAGE_TAG="${SERVICE_IMAGE_TAG:-}"
CLEANUP="${CLEANUP:-true}"
KEEP_ON_FAILURE="${KEEP_ON_FAILURE:-true}"
TEST_LOGS_DIR="${TEST_LOGS_DIR:-${SCRIPT_DIR}/../../reports/functional}"

JOB_FAILED=0

cleanup_all() {
  print_grafana_logs_url "$NAMESPACE" "functional-tests" || true
  save_job_logs "$JOB_RELEASE" "$NAMESPACE" "functional-tests" "$TEST_LOGS_DIR" || true

  if [[ "$JOB_FAILED" == "1" && "$KEEP_ON_FAILURE" == "true" ]]; then
    log_warning "Functional tests failed; preserving namespace ${NAMESPACE} so logs stay reachable."
    log_warning "  Local log snapshot: ${TEST_LOGS_DIR}/"
    log_warning "  Inspect pods:       kubectl get pods -n ${NAMESPACE}"
    log_warning "  Tail logs:          kubectl logs -n ${NAMESPACE} -l job-name=${JOB_RELEASE} --tail=-1"
    log_warning "  Manual cleanup:     helm uninstall ${JOB_RELEASE} ${SERVICE_RELEASE} ${DB_RELEASE} -n ${NAMESPACE}"
    return 0
  fi

  if [[ "$CLEANUP" == "true" ]]; then
    sleep_for_log_flush
    log "Cleaning up Helm releases in namespace: $NAMESPACE"
    helm uninstall "$JOB_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
    helm uninstall "$WIREMOCK_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
    helm uninstall "$SERVICE_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
    helm uninstall "$DB_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
  else
    log_warning "Cleanup disabled. Releases will remain in namespace $NAMESPACE"
  fi
}

LOGS_START=$(date +%s)
export LOGS_START

trap cleanup_all EXIT

main() {
  log "Starting functional test deployment and execution"
  log "  Namespace: $NAMESPACE"
  log "  Service Chart: $SERVICE_CHART_PATH"
  log "  Tests Chart:   $TEST_CHART_PATH"
  log "  WireMock Chart:${WIREMOCK_CHART_REPO_NAME}/${WIREMOCK_CHART_NAME}@${WIREMOCK_CHART_VERSION}"
  log "  WireMock Values:${WIREMOCK_VALUES_FILE}"
  log "  Job Image:     $JOB_IMAGE_REPOSITORY:$IMAGE_TAG"
  log "  WireMock Image:${WIREMOCK_IMAGE_REPOSITORY}:${WIREMOCK_IMAGE_TAG}"
  log "  Service Image: $SERVICE_IMAGE_REPOSITORY:$SERVICE_IMAGE_TAG"
  log "  Timeout:       ${TIMEOUT}s"
  log "  Cleanup:       $CLEANUP"

  kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create ns "$NAMESPACE" >/dev/null

  # Drop any existing job release
  helm_uninstall_if_exists "$JOB_RELEASE" "$NAMESPACE"
  helm_uninstall_if_exists "$WIREMOCK_RELEASE" "$NAMESPACE"

  helm repo add "$WIREMOCK_CHART_REPO_NAME" "$WIREMOCK_CHART_REPO_URL" 2>/dev/null || true
  helm repo update "$WIREMOCK_CHART_REPO_NAME" >/dev/null

  log "Installing functional test WireMock [$WIREMOCK_RELEASE]..."
  helm upgrade --install "$WIREMOCK_RELEASE" "$WIREMOCK_CHART_REPO_NAME/$WIREMOCK_CHART_NAME" \
    -n "$NAMESPACE" \
    --version "$WIREMOCK_CHART_VERSION" \
    -f "$WIREMOCK_VALUES_FILE" \
    --set fullnameOverride="$WIREMOCK_RELEASE" \
    --set image.repository="$WIREMOCK_IMAGE_REPOSITORY" \
    --set image.tag="$WIREMOCK_IMAGE_TAG" \
    --wait --timeout=3m
  log_success "Functional test WireMock deployed"

  if [[ "$DEPLOY_SERVICE" == "true" ]]; then
    ACTION=deploy \
    NAMESPACE="$NAMESPACE" \
    SERVICE_RELEASE="$SERVICE_RELEASE" \
    SERVICE_CHART_PATH="$SERVICE_CHART_PATH" \
    VALUES_FILE="$SERVICE_CHART_PATH/values-functional.yaml" \
    DEPLOY_DB="$CLEAN_DEPLOY_DB" \
    DB_RELEASE="$DB_RELEASE" \
    SERVICE_IMAGE_REPOSITORY="$SERVICE_IMAGE_REPOSITORY" \
    SERVICE_IMAGE_TAG="$SERVICE_IMAGE_TAG" \
    WAIT_TIMEOUT=180 \
    "${SCRIPT_DIR}/deploy-service.sh"
  else
    log_warning "Skipping service deployment per DEPLOY_SERVICE=false"
  fi

  log "Installing functional test job [$JOB_RELEASE]..."
  helm upgrade --install "$JOB_RELEASE" "$TEST_CHART_PATH" \
    -n "$NAMESPACE" \
    --set image.repository="$JOB_IMAGE_REPOSITORY" \
    --set image.tag="$IMAGE_TAG" \
    --set job.activeDeadlineSeconds="$TIMEOUT" \
    --wait --timeout=3m
  log_success "Functional test job deployed"

  if wait_for_job_with_logs "$JOB_RELEASE" "$NAMESPACE" "$TIMEOUT" "functional-tests"; then
    log_success "Functional tests passed!"
    exit 0
  else
    JOB_FAILED=1
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
  echo "  SERVICE_CHART_PATH         Path to service Helm chart (default: ../../helm-chart)"
  echo "  TEST_CHART_PATH            Path to functional-tests Helm chart (default: ../k8s/functional-tests)"
  echo "  WIREMOCK_VALUES_FILE       Values overlay for upstream WireMock chart (default: ../k8s/wiremock-values.yaml)"
  echo "  WIREMOCK_CHART_REPO_NAME   Helm repo name for WireMock chart (default: wiremock)"
  echo "  WIREMOCK_CHART_REPO_URL    Helm repo URL for WireMock chart (default: https://wiremock.github.io/helm-charts)"
  echo "  WIREMOCK_CHART_NAME        Upstream WireMock chart name (default: wiremock)"
  echo "  WIREMOCK_CHART_VERSION     Upstream WireMock chart version (default: 1.11.0)"
  echo "  DB_RELEASE                 DB Helm release name (default: support-bot-db)"
  echo "  SERVICE_RELEASE            Service Helm release name (default: support-bot)"
  echo "  JOB_RELEASE                Job Helm release name (default: support-bot-functional-tests)"
  echo "  WIREMOCK_RELEASE           WireMock Helm release name (default: support-bot-functional-tests-wiremock)"
  echo "  TIMEOUT                    Job timeout in seconds (default: 180)"
  echo "  JOB_IMAGE_REPOSITORY       Docker repository for tests job"
  echo "  IMAGE_TAG                  Docker image tag for job (default: latest)"
  echo "  WIREMOCK_IMAGE_REPOSITORY  WireMock Docker repository (default: wiremock/wiremock)"
  echo "  WIREMOCK_IMAGE_TAG         WireMock Docker image tag (default: 3.13.2)"
  echo "  SERVICE_IMAGE_REPOSITORY   Service Docker repository"
  echo "  SERVICE_IMAGE_TAG          Service Docker image tag (default: latest)"
  echo "  CLEANUP                    Cleanup resources after completion (default: true)"
  exit 0
fi

main "$@"
