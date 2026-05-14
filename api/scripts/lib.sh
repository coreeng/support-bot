#!/bin/bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()        { echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $*"; }
log_success(){ echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✓${NC} $*"; }
log_warning(){ echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠${NC} $*"; }
log_error()  { echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ✗${NC} $*"; }

helm_uninstall_if_exists() {
  local release="$1" ns="$2"
  if helm list -n "$ns" -q | grep -q "^${release}$"; then
    log_warning "Uninstalling existing release ${release} in ${ns}"
    helm uninstall "$release" -n "$ns" || true
  fi
}

wait_for_job_with_logs() {
  local job_name="$1" ns="$2" timeout="${3:-60}" logs_container="${4:?logs_container is required}"
  local start
  start=$(date +%s)
  local end=$(( start + timeout ))
  local pod=""
  local logs_started=false

  log "Waiting for job ${job_name} to complete (timeout: ${timeout}s)..."
  while [[ $(date +%s) -lt $end ]]; do
    local job_status
    job_status=$(kubectl get job "$job_name" -n "$ns" -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || echo "")
    local job_failed
    job_failed=$(kubectl get job "$job_name" -n "$ns" -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}' 2>/dev/null || echo "")

    if [[ -z "$pod" ]]; then
      pod=$(kubectl get pods -n "$ns" -l job-name="$job_name" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    fi

    if [[ -n "$pod" && "$logs_started" == false ]]; then
      local phase
      phase=$(kubectl get pod "$pod" -n "$ns" -o jsonpath='{.status.phase}' 2>/dev/null || echo "")
      if [[ "$phase" == "Running" || "$phase" == "Succeeded" || "$phase" == "Failed" ]]; then
        log "Streaming logs from pod $pod:"
        echo "========================================"
        kubectl logs -f "$pod" -n "$ns" -c "$logs_container" &
        logs_started=true
      fi
    fi

    if [[ "$job_status" == "True" ]]; then
      sleep 1
      log_success "Job completed successfully!"
      return 0
    elif [[ "$job_failed" == "True" ]]; then
      sleep 1
      log_error "Job failed!"
      return 1
    fi

    if [[ "$logs_started" == false ]]; then
      local active elapsed
      active=$(kubectl get job "$job_name" -n "$ns" -o jsonpath='{.status.active}' 2>/dev/null || echo "0")
      elapsed=$(($(date +%s) - start))
      log "Job is running... (active pods: $active, elapsed: ${elapsed}s)"
    fi
    sleep 2
  done

  log_error "Job timed out after ${timeout} seconds"
  return 1
}

show_job_status() {
  local job_name="$1" ns="$2"
  log "Job status details:"
  kubectl describe job "$job_name" -n "$ns" || log_warning "Could not describe job $job_name"
  log "Pod status:"
  kubectl get pods -n "$ns" -l job-name="$job_name" || log_warning "Could not get pods for job $job_name"
}

# Print a Grafana Cloud-Logging deep link covering the test run. Mirrors the
# behaviour of ui/p2p/scripts/helm-test.sh so test pods that have already been
# cleaned up by `helm uninstall` can still be inspected via GCP Cloud Logging.
#
# Required env vars (set by caller before invoking):
#   INTERNAL_SERVICES_DOMAIN  e.g. gcp-dev.cecg.platform.cecg.io
#   PROJECT_ID                GCP project id
#   LOGS_START                unix timestamp (s) captured before the run
#
# Args:
#   $1 namespace
#   $2 container_name (the test container, e.g. functional-tests / nft-tests)
print_grafana_logs_url() {
  local ns="$1" container="$2"
  if [[ -z "${INTERNAL_SERVICES_DOMAIN:-}" || -z "${PROJECT_ID:-}" || -z "${LOGS_START:-}" ]]; then
    return 0
  fi
  local now
  now=$(date +%s)
  local url
  url="https://grafana.${INTERNAL_SERVICES_DOMAIN}/explore?orgId=1&left=%7B%22datasource%22:%22CloudLogging%22,%22queries%22:%5B%7B%22refId%22:%22A%22,%22queryText%22:%22resource.type%3D%5C%22k8s_container%5C%22%5Cnresource.labels.namespace_name%3D%5C%22${ns}%5C%22%5Cnresource.labels.container_name%3D%5C%22${container}%5C%22%22,%22projectId%22:%22${PROJECT_ID}%22,%22bucketId%22:%22global%2Fbuckets%2F_Default%22,%22viewId%22:%22_AllLogs%22%7D%5D,%22range%22:%7B%22from%22:%22${LOGS_START}000%22,%22to%22:%22${now}000%22%7D%7D"
  echo "Logs: ${url}"
}

# Best-effort: dump kubectl logs from a job's pods to a local file BEFORE we
# uninstall the release. Logs survive the pod even after helm uninstall.
#
# Args:
#   $1 job_release name (matches metadata.name and helm release)
#   $2 namespace
#   $3 container name inside the test pod
#   $4 destination dir (created if missing)
save_job_logs() {
  local release="$1" ns="$2" container="$3" dest_dir="$4"
  mkdir -p "$dest_dir" 2>/dev/null || return 0

  local pods
  pods=$(kubectl get pods -n "$ns" -l job-name="$release" \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true)

  if [[ -z "$pods" ]]; then
    log_warning "save_job_logs: no pods found for job ${release} in ${ns}"
    return 0
  fi

  while IFS= read -r pod; do
    [[ -z "$pod" ]] && continue
    local out_file="${dest_dir}/${pod}.log"
    log "Saving logs: pod=${pod} container=${container} -> ${out_file}"
    {
      echo "=== current container ==="
      kubectl logs -n "$ns" "$pod" -c "$container" --tail=-1 2>&1 || true
      echo
      echo "=== previous container instance (if any) ==="
      kubectl logs -n "$ns" "$pod" -c "$container" --previous --tail=-1 2>&1 || true
    } >"$out_file"
  done <<<"$pods"
}

# Sleep so node log shippers (fluent-bit) get a chance to push pod logs to
# GCP Cloud Logging before we delete the pods. Default 30s leaves comfortable
# headroom over fluent-bit's typical 5–15s flush+batch cycle to Cloud Logging.
# Overridable via LOG_FLUSH_SECONDS (set to 0 to skip in dev).
sleep_for_log_flush() {
  local secs="${LOG_FLUSH_SECONDS:-30}"
  if [[ "$secs" =~ ^[0-9]+$ ]] && (( secs > 0 )); then
    log "Sleeping ${secs}s to let fluent-bit ship pod logs to Cloud Logging..."
    sleep "$secs"
  fi
}

