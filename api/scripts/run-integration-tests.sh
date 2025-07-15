#!/bin/bash

set -euo pipefail

# Configuration
NAMESPACE="${NAMESPACE:-support-bot-integration}"
CHART_PATH="${CHART_PATH:-$(dirname "$0")/../k8s/integration-tests}"
RELEASE_NAME="${RELEASE_NAME:-support-bot-integration-tests}"
TIMEOUT="${TIMEOUT:-1800}"  # 30 minutes
JOB_IMAGE_REPOSITORY="${JOB_IMAGE_REPOSITORY}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SERVICE_IMAGE_REPOSITORY="${SERVICE_IMAGE_REPOSITORY}"
SERVICE_IMAGE_TAG="${SERVICE_IMAGE_TAG:-latest}"
CLEANUP="${CLEANUP:-true}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✓${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ✗${NC} $1"
}

cleanup_job() {
    if [[ "$CLEANUP" == "true" ]]; then
        log "Cleaning up Helm release: $RELEASE_NAME"
        helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" --ignore-not-found || true
    else
        log_warning "Cleanup disabled. Helm release $RELEASE_NAME will remain in namespace $NAMESPACE"
    fi
}

# Trap to ensure cleanup happens on script exit
trap cleanup_job EXIT


# Deploy the integration test chart
deploy_chart() {
    log "Deploying integration test chart..."
    log "  Release: $RELEASE_NAME"
    log "  Namespace: $NAMESPACE"
    log "  Chart: $CHART_PATH"
    log "  Job Image: $JOB_IMAGE_REPOSITORY:$IMAGE_TAG"
    log "  Service Image: $SERVICE_IMAGE_REPOSITORY:$SERVICE_IMAGE_TAG"
    
    # Check if release already exists and uninstall it
    if helm list -n "$NAMESPACE" -q | grep -q "^$RELEASE_NAME$"; then
        log_warning "Release $RELEASE_NAME already exists. Uninstalling..."
        helm uninstall "$RELEASE_NAME" -n "$NAMESPACE"
        sleep 5
    fi
    
    # Deploy the chart
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

# Wait for job completion and stream logs
wait_for_job_with_logs() {
    local job_name="$RELEASE_NAME"
    local start_time=$(date +%s)
    local end_time=$((start_time + TIMEOUT))
    local pod_name=""
    local logs_started=false
    
    log "Waiting for job $job_name to complete (timeout: ${TIMEOUT}s)..."
    
    while [[ $(date +%s) -lt $end_time ]]; do
        # Get job status
        local job_status=$(kubectl get job "$job_name" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || echo "")
        local job_failed=$(kubectl get job "$job_name" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}' 2>/dev/null || echo "")
        
        # Get pod name if not already found
        if [[ -z "$pod_name" ]]; then
            pod_name=$(kubectl get pods -n "$NAMESPACE" -l job-name="$job_name" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
        fi
        
        # Start streaming logs once pod is found and running
        if [[ -n "$pod_name" ]] && [[ "$logs_started" == false ]]; then
            local pod_phase=$(kubectl get pod "$pod_name" -n "$NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || echo "")
            if [[ "$pod_phase" == "Running" ]] || [[ "$pod_phase" == "Succeeded" ]] || [[ "$pod_phase" == "Failed" ]]; then
                log "Streaming logs from pod $pod_name:"
                echo "========================================"
                kubectl logs -f "$pod_name" -n "$NAMESPACE" &
                logs_started=true
            fi
        fi
        
        if [[ "$job_status" == "True" ]]; then
            # Wait a moment for final logs to be captured
            sleep 2
            log_success "Job completed successfully!"
            return 0
        elif [[ "$job_failed" == "True" ]]; then
            # Wait a moment for final logs to be captured
            sleep 2
            log_error "Job failed!"
            return 1
        fi
        
        # Show progress only if logs haven't started yet
        if [[ "$logs_started" == false ]]; then
            local active=$(kubectl get job "$job_name" -n "$NAMESPACE" -o jsonpath='{.status.active}' 2>/dev/null || echo "0")
            local elapsed=$(($(date +%s) - start_time))
            log "Job is running... (active pods: $active, elapsed: ${elapsed}s)"
        fi
        
        sleep 5
    done
    
    log_error "Job timed out after ${TIMEOUT} seconds"
    return 1
}


# Show job status and details
show_job_status() {
    local job_name="$RELEASE_NAME"
    
    log "Job status details:"
    kubectl describe job "$job_name" -n "$NAMESPACE" || log_warning "Could not describe job $job_name"
    
    log "Pod status:"
    kubectl get pods -n "$NAMESPACE" -l job-name="$job_name" || log_warning "Could not get pods for job $job_name"
}

# Main execution
main() {
    log "Starting integration test deployment and execution"
    log "Configuration:"
    log "  Namespace: $NAMESPACE"
    log "  Release: $RELEASE_NAME"
    log "  Chart: $CHART_PATH"
    log "  Job Image: $JOB_IMAGE_REPOSITORY:$IMAGE_TAG"
    log "  Service Image: $SERVICE_IMAGE_REPOSITORY:$SERVICE_IMAGE_TAG"
    log "  Timeout: ${TIMEOUT}s"
    log "  Cleanup: $CLEANUP"
    
    deploy_chart
    
    # Wait for job to complete and capture result
    if wait_for_job_with_logs; then
        log_success "Integration tests passed!"
        exit 0
    else
        log_error "Integration tests failed!"
        show_job_status
        exit 1
    fi
}

# Show usage information
usage() {
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
    echo ""
    echo "Examples:"
    echo "  # Run with default settings"
    echo "  $0"
    echo ""
    echo "  # Run with custom test image tag"
    echo "  IMAGE_TAG=v1.2.3 $0"
    echo ""
    echo "  # Run with specific service image"
    echo "  SERVICE_IMAGE_TAG=v2.1.0 $0"
    echo ""
    echo "  # Run with custom service image and tag"
    echo "  SERVICE_IMAGE_REPOSITORY=my-registry/my-service SERVICE_IMAGE_TAG=v1.0.0 $0"
    echo ""
    echo "  # Run without cleanup"
    echo "  CLEANUP=false $0"
}

# Handle command line arguments
if [[ "${1:-}" == "--help" ]] || [[ "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

# Run main function
main "$@"
