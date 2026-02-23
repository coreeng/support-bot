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

  log "Cleaning up Helm releases in namespace: ${NAMESPACE}"
  helm uninstall "${JOB_RELEASE}" -n "${NAMESPACE}" --ignore-not-found || true
  helm uninstall "${SERVICE_RELEASE}" -n "${NAMESPACE}" --ignore-not-found || true
  helm uninstall "${DB_RELEASE}" -n "${NAMESPACE}" --ignore-not-found || true
}

trap cleanup EXIT

collect_nft_artifacts() {
  # Copy Gatling HTML reports from the PVC used by the nft-tests Job, via a
  # short-lived helper pod that mounts the same claim. This avoids relying on
  # exec access to the main Job pod, which may already be in Failed phase.

  local root_dir
  root_dir="${ARTIFACTS_BASE_DIR:-${SCRIPT_DIR}/../..}"
  local dest_dir
  dest_dir="${root_dir}/reports/nft"

  mkdir -p "${dest_dir}"

  # Discover the PVC backing the dedicated reports volume on the Job template.
  local pvc_name
  pvc_name=$(kubectl get job "${JOB_RELEASE}" -n "${NAMESPACE}" \
    -o jsonpath='{.spec.template.spec.volumes[?(@.name=="nft-reports")].persistentVolumeClaim.claimName}' 2>/dev/null || echo "")

  if [[ -z "${pvc_name}" ]]; then
    log_warning "No PVC-backed tmp volume found on job ${JOB_RELEASE}; skipping PVC-based artifact collection"
    return 0
  fi

  local helper_pod
  helper_pod="${JOB_RELEASE}-reports-helper-$(date +%s)"
  log "Creating helper pod ${helper_pod} to copy Gatling reports from PVC ${pvc_name}"

  cat <<EOF | kubectl apply -n "${NAMESPACE}" -f -
apiVersion: v1
kind: Pod
metadata:
  name: ${helper_pod}
spec:
  restartPolicy: Never
  volumes:
    - name: nft-reports
      persistentVolumeClaim:
        claimName: ${pvc_name}
  containers:
    - name: helper
      image: alpine:3.19
      command: ["sh", "-c", "sleep 3600"]
      volumeMounts:
        - name: nft-reports
          mountPath: /mnt/nft-reports
EOF

  # Wait for helper pod to be Running so we can kubectl cp from it
  local attempt=0
  local max_attempts=60
  while (( attempt < max_attempts )); do
    attempt=$((attempt + 1))
    local phase
    phase=$(kubectl get pod "${helper_pod}" -n "${NAMESPACE}" -o jsonpath='{.status.phase}' 2>/dev/null || echo "")

    if [[ "${phase}" == "Running" ]]; then
      break
    fi

    if [[ "${phase}" == "Failed" || "${phase}" == "Succeeded" ]]; then
      log_warning "Helper pod ${helper_pod} reached phase '${phase}' before becoming Running; giving up on artifact collection"
      kubectl delete pod "${helper_pod}" -n "${NAMESPACE}" --ignore-not-found >/dev/null 2>&1 || true
      return 0
    fi

    log "[artifacts] Waiting for helper pod ${helper_pod} to be Running (current phase: ${phase:-Unknown})"
    sleep 5
  done

  if (( attempt >= max_attempts )); then
    log_warning "Timed out waiting for helper pod ${helper_pod} to be Running; skipping artifact collection"
    kubectl delete pod "${helper_pod}" -n "${NAMESPACE}" --ignore-not-found >/dev/null 2>&1 || true
    return 0
  fi

  # Gatling is configured in Gradle to write results directly under
  # /mnt/nft-reports inside the test container, which is backed by this PVC.
  # The helper pod mounts the same PVC at /mnt/nft-reports, so we can copy
  # that directory as-is.
  local src_path
  src_path="/mnt/nft-reports"
  log "Collecting Gatling reports from helper pod ${helper_pod}:${src_path} into ${dest_dir}"
  if kubectl cp "${NAMESPACE}/${helper_pod}:${src_path}" "${dest_dir}" -c helper; then
    log_success "Copied Gatling reports to ${dest_dir}"
  else
    log_warning "Failed to copy Gatling reports from helper pod ${helper_pod}; artifacts may be missing"
  fi

  kubectl delete pod "${helper_pod}" -n "${NAMESPACE}" --ignore-not-found >/dev/null 2>&1 || true
}

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
	  local job_exit_code=0
	  if ! wait_for_job_with_logs "${JOB_RELEASE}" "${NAMESPACE}" "${TIMEOUT}" "nft-tests"; then
	    job_exit_code=1
	    show_job_status "${JOB_RELEASE}" "${NAMESPACE}"
	  fi

	  # Attempt to collect Gatling reports from the PVC via helper pod, even if
	  # the job failed.
	  collect_nft_artifacts || true
	
	  if [[ "${job_exit_code}" -ne 0 ]]; then
	    log_error "NFT tests failed"
	    exit "${job_exit_code}"
	  fi
	
	  log_success "NFT tests completed successfully"
}

main "$@"

