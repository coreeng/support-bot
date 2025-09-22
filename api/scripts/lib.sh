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

