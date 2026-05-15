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
KEEP_ON_FAILURE="${KEEP_ON_FAILURE:-true}"
TEST_LOGS_DIR="${TEST_LOGS_DIR:-${SCRIPT_DIR}/../../reports/integration}"

# shellcheck source=deploy-service.sh
. "${SCRIPT_DIR}/deploy-service.sh"

JOB_FAILED=0

cleanup_job() {
  print_grafana_logs_url "$NAMESPACE" "integration-tests" || true
  save_job_logs "$RELEASE_NAME" "$NAMESPACE" "integration-tests" "$TEST_LOGS_DIR" || true

  if [[ "$JOB_FAILED" == "1" && "$KEEP_ON_FAILURE" == "true" ]]; then
    log_warning "Integration tests failed; preserving namespace ${NAMESPACE} so logs stay reachable."
    log_warning "  Local log snapshot: ${TEST_LOGS_DIR}/"
    log_warning "  Inspect pods:       kubectl get pods -n ${NAMESPACE}"
    log_warning "  Tail logs:          kubectl logs -n ${NAMESPACE} -l job-name=${RELEASE_NAME} --tail=-1"
    log_warning "  Manual cleanup:     helm uninstall ${RELEASE_NAME} support-bot-dex support-bot-ldap support-bot ${DB_RELEASE:-support-bot-db} -n ${NAMESPACE}"
    return 0
  fi

  if [[ "$CLEANUP" == "true" ]]; then
    sleep_for_log_flush
    log "Cleaning up Helm releases in namespace: $NAMESPACE"
    helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" --ignore-not-found || true
    helm uninstall support-bot-dex -n "$NAMESPACE" --ignore-not-found || true
    helm uninstall support-bot-ldap -n "$NAMESPACE" --ignore-not-found || true
    HELM_DRIVER=configmap helm uninstall support-bot -n "$NAMESPACE" --ignore-not-found || true
  else
    log_warning "Cleanup disabled. Helm releases will remain in namespace $NAMESPACE"
  fi

  if [[ "$DELETE_DB" == "true" && "$DEPLOY_DB" == "true" ]]; then
    log "Cleaning up DB release: $DB_RELEASE"
    helm uninstall "$DB_RELEASE" -n "$NAMESPACE" --ignore-not-found || true
  fi
}

LOGS_START=$(date +%s)
export LOGS_START

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

ensure_app_k8s_secrets() {
  log "Ensuring K8s secret support-bot (Slack credentials) in ${NAMESPACE}..."
  kubectl create secret generic support-bot \
    --from-literal=SLACK_TOKEN="${SLACK_TOKEN:?Set SLACK_TOKEN}" \
    --from-literal=SLACK_SOCKET_TOKEN="${SLACK_SOCKET_TOKEN:?Set SLACK_SOCKET_TOKEN}" \
    --from-literal=SLACK_SIGNING_SECRET="${SLACK_SIGNING_SECRET:?Set SLACK_SIGNING_SECRET}" \
    -n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

  log "Ensuring K8s secret azure (Azure credentials) in ${NAMESPACE}..."
  kubectl create secret generic azure \
    --from-literal=AZURE_TENANT_ID="${AZURE_TENANT_ID:?Set AZURE_TENANT_ID}" \
    --from-literal=AZURE_CLIENT_ID="${AZURE_CLIENT_ID:?Set AZURE_CLIENT_ID}" \
    --from-literal=AZURE_CLIENT_SECRET="${AZURE_CLIENT_SECRET:?Set AZURE_CLIENT_SECRET}" \
    -n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
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

  # Optionally deploy database first
  if [[ "$DEPLOY_DB" == "true" ]]; then
    deploy_db "$NAMESPACE" "$DB_RELEASE"
  else
    log_warning "DEPLOY_DB is false; assuming database already available in $NAMESPACE"
  fi

  ensure_app_k8s_secrets
  deploy_chart

  if wait_for_job_with_logs "$RELEASE_NAME" "$NAMESPACE" "$TIMEOUT" "integration-tests"; then
    log_success "Integration tests passed!"
    exit 0
  else
    JOB_FAILED=1
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
  echo "  TIMEOUT                    Job timeout in seconds (default: 600)"
  echo "  JOB_IMAGE_REPOSITORY       Docker repository for test job container (required)"
  echo "  IMAGE_TAG                  Docker image tag for test container (required)"
  echo "  SERVICE_IMAGE_REPOSITORY   Service Docker repository (required)"
  echo "  SERVICE_IMAGE_TAG          Service Docker image tag (required)"
  echo "  CLEANUP                    Cleanup resources after completion (default: true)"
  echo "  DEPLOY_DB                  Deploy DB before tests (default: true)"
  echo "  DB_RELEASE                 Helm release name for DB (default: support-bot-db)"
  echo "  DELETE_DB                  Delete DB during cleanup (default: true)"
  exit 0
fi

main "$@"
