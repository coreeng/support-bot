#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

# Configuration (can be overridden via environment)
NAMESPACE="${NAMESPACE:-support-bot-nft}"
JOB_RELEASE="${JOB_RELEASE:-support-bot-nft-tests}"
DB_RELEASE="${DB_RELEASE:-support-bot-db}"
SERVICE_RELEASE="${SERVICE_RELEASE:-support-bot}"

JOB_IMAGE_REPOSITORY="${JOB_IMAGE_REPOSITORY:?JOB_IMAGE_REPOSITORY is required}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

SERVICE_IMAGE_REPOSITORY="${SERVICE_IMAGE_REPOSITORY:-}"
SERVICE_IMAGE_TAG="${SERVICE_IMAGE_TAG:-}"

TIMEOUT="${TIMEOUT:-1200}"
CLEANUP="${CLEANUP:-true}"
DEPLOY_SERVICE="${DEPLOY_SERVICE:-true}"

SERVICE_CHART_PATH="${SERVICE_CHART_PATH:-${SCRIPT_DIR}/../k8s/service}"
TEST_CHART_PATH="${TEST_CHART_PATH:-${SCRIPT_DIR}/../k8s/nft-tests}"

cleanup() {
  if [[ "${CLEANUP}" != "true" ]]; then
    log_warning "CLEANUP=false - leaving resources in place"
    return
  fi

  log "Cleaning up nft test job ${JOB_RELEASE} in namespace ${NAMESPACE}"
  helm_uninstall_if_exists "${JOB_RELEASE}" "${NAMESPACE}" || true
}

trap cleanup EXIT

main() {
  log "Running NFT tests in namespace ${NAMESPACE}"
  log "Job image: ${JOB_IMAGE_REPOSITORY}:${IMAGE_TAG}"

  # Ensure namespace exists
  if ! kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1; then
    log "Namespace ${NAMESPACE} does not exist, creating..."
    kubectl create namespace "${NAMESPACE}"
  fi

  # Optionally deploy the support-bot service if image coordinates are provided
  if [[ "${DEPLOY_SERVICE}" == "true" ]]; then
    if [[ -z "${SERVICE_IMAGE_REPOSITORY}" || -z "${SERVICE_IMAGE_TAG}" ]]; then
      log_warning "DEPLOY_SERVICE=true but SERVICE_IMAGE_REPOSITORY or SERVICE_IMAGE_TAG not set; skipping service deployment"
    else
      log "Deploying support-bot service for NFT tests"
      NAMESPACE="${NAMESPACE}" \
      SERVICE_IMAGE_REPOSITORY="${SERVICE_IMAGE_REPOSITORY}" \
      SERVICE_IMAGE_TAG="${SERVICE_IMAGE_TAG}" \
      DB_RELEASE="${DB_RELEASE}" \
      SERVICE_RELEASE="${SERVICE_RELEASE}" \
      ACTION=deploy \
	      VALUES_FILE="${SERVICE_CHART_PATH}/values-nft.yaml" \
      "${SCRIPT_DIR}/deploy-service.sh"
    fi
  fi

  # Uninstall any existing job release before installing a fresh one
  helm_uninstall_if_exists "${JOB_RELEASE}" "${NAMESPACE}"

  log "Installing/Upgrading nft-tests Helm chart"
  helm upgrade --install "${JOB_RELEASE}" "${TEST_CHART_PATH}" \
    --namespace "${NAMESPACE}" \
    --set image.repository="${JOB_IMAGE_REPOSITORY}" \
    --set image.tag="${IMAGE_TAG}" \
    --set job.activeDeadlineSeconds="${TIMEOUT}"

  # Wait for job completion and stream logs from the nft-tests container
  if ! wait_for_job_with_logs "${JOB_RELEASE}" "${NAMESPACE}" "${TIMEOUT}" "nft-tests"; then
    show_job_status "${JOB_RELEASE}" "${NAMESPACE}"
    exit 1
  fi

  log_success "NFT tests completed successfully"
}

main "$@"

