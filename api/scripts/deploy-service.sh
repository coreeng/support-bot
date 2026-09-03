#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

# Config
NAMESPACE="${NAMESPACE:-support-bot-functional}"
SERVICE_RELEASE="${SERVICE_RELEASE:-support-bot}"
SERVICE_CHART_PATH="${SERVICE_CHART_PATH:-${SCRIPT_DIR}/../../helm-chart}"
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

reset_db_schema() {
  local ns="$1" release="$2"
  log "Resetting database schema for release [${release}] in namespace ${ns}..."
  local db_pod
  db_pod=$(kubectl get pod -n "$ns" \
    -l "app.kubernetes.io/instance=${release},app.kubernetes.io/name=postgresql" \
    -o jsonpath='{.items[0].metadata.name}')
  kubectl exec -n "$ns" "$db_pod" -- \
    env PGPASSWORD=supportbotpassword \
    psql -U supportbot -d supportbot -c \
    "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
  log_success "Database schema reset complete"
}

# A pending-* release younger than this many seconds is treated as a live helm
# operation (possibly from another CI run against the same namespace) rather
# than a wreck. It must exceed the longest helm --timeout any caller uses on
# these releases: 3m (deploy_db), 5m (deploy_service) and 10m (the root
# Makefile's deploy-ui-% upgrade of the service release), so 10m + 1m grace.
STUCK_RELEASE_MIN_AGE="${STUCK_RELEASE_MIN_AGE:-660}" # seconds
STUCK_RELEASE_POLL_INTERVAL="${STUCK_RELEASE_POLL_INTERVAL:-10}" # seconds

# Print "<status> <age-seconds>" for a release from `helm list -o json`; age is
# empty when the timestamp cannot be parsed. Uses sed + GNU/BSD date only, as
# the rest of this script does (no jq: the integration-tests image is a bare
# ubi9 runtime, and the script also runs from there).
release_status_and_age() {
  local ns="$1" release="$2" json status updated epoch
  json=$(helm list -n "$ns" -a -f "^${release}\$" -o json 2>/dev/null || true)
  status=$(printf '%s' "$json" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')
  # helm prints Go's time.String() form ("2024-05-01 12:34:56.123456789 +0000
  # UTC"); drop the fractional seconds and zone name so date can parse it.
  updated=$(printf '%s' "$json" | sed -n 's/.*"updated":"\([^"]*\)".*/\1/p' \
    | sed -E 's/\.[0-9]+//; s/ [A-Za-z]+$//')
  epoch=""
  if [[ -n "$updated" ]]; then
    epoch=$(date -u -d "$updated" +%s 2>/dev/null \
      || date -u -j -f '%Y-%m-%d %H:%M:%S %z' "$updated" +%s 2>/dev/null \
      || true)
  fi
  if [[ "$epoch" =~ ^[0-9]+$ ]]; then
    echo "$status $(( $(date +%s) - epoch ))"
  else
    echo "$status"
  fi
}

# A cancelled CI run kills helm mid-install, leaving the release stuck in a
# pending-* state that blocks every subsequent install with "another operation
# (install/upgrade/rollback) is in progress". Clear such wrecks before deploying.
#
# A live helm install/upgrade from a concurrent run looks exactly the same, so
# never uninstall on status alone: helm stamps the release's `updated` time when
# the operation starts, so a pending-* release is only a wreck once it is older
# than the longest helm --timeout that could still be running against it. Until
# then wait for the operation to finish (the status leaves pending-*). If the
# age cannot be determined, leave the release alone and let helm fail loudly
# with "another operation in progress" rather than destroy a possibly live one.
clear_stuck_release() {
  local ns="$1" release="$2" status age
  while :; do
    read -r status age <<<"$(release_status_and_age "$ns" "$release")"
    [[ "$status" == pending-* ]] || return 0
    if [[ -z "$age" ]]; then
      log_warning "Release ${release} is ${status} but its last-updated time could not be read; leaving it alone"
      return 0
    fi
    if (( age >= STUCK_RELEASE_MIN_AGE )); then
      break
    fi
    log "Release ${release} is ${status}, updated ${age}s ago (< ${STUCK_RELEASE_MIN_AGE}s): may be a live helm operation from another run; waiting ${STUCK_RELEASE_POLL_INTERVAL}s..."
    sleep "$STUCK_RELEASE_POLL_INTERVAL"
  done
  log "Release ${release} is stuck in ${status} (updated ${age}s ago); uninstalling it first..."
  helm uninstall "$release" -n "$ns" --wait --timeout=2m || true
}

deploy_db() {
  local ns="$1" release="$2"
  log "Installing PostgreSQL [${release}] in namespace ${ns}..."
  clear_stuck_release "$ns" "$release"
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
    --set primary.resourcesPreset=small \
    --set serviceAccount.create=false \
    --wait --atomic --timeout=3m
  log_success "PostgreSQL deployed"
  reset_db_schema "$ns" "$release"
}

ensure_chart_deps() {
  local chart_path="$1"
  # Chart.yaml declares dex as a subchart dependency. Helm requires the dep to
  # be present in charts/ before render, even when the active values disable
  # it via `dex.enabled: false`. Skip the fetch if it's already vendored
  # (e.g. baked into an image build, which is necessary for pods that run
  # `helm install` without outbound access to charts.dexidp.io). Helm may
  # leave the dep as a tarball (charts/dex-<ver>.tgz) or unpacked
  # (charts/dex/Chart.yaml).
  if compgen -G "${chart_path}/charts/dex-*.tgz" > /dev/null 2>&1 \
     || [[ -f "${chart_path}/charts/dex/Chart.yaml" ]]; then
    log "Chart dependencies already vendored at ${chart_path}/charts"
    return 0
  fi
  log "Vendoring chart dependencies for ${chart_path}..."
  helm repo add dex https://charts.dexidp.io >/dev/null
  helm repo update dex >/dev/null
  helm dependency build "$chart_path" >/dev/null
}

deploy_service() {
  local ns="$1" release="$2" chart_path="$3" image_repo="$4" image_tag="$5"
  ensure_chart_deps "$chart_path"
  log "Installing service [${release}] in ${ns} from ${chart_path}..."
  clear_stuck_release "$ns" "$release"
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
  # Get the current ReplicaSet pod-template-hash to avoid waiting for old terminating pods
  local pod_template_hash=$(kubectl get rs -n "$ns" -l app.kubernetes.io/name="$deploy_name" -o jsonpath='{.items[?(@.spec.replicas>0)].metadata.labels.pod-template-hash}' | head -n1)
  if [[ -n "$pod_template_hash" ]]; then
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name="$deploy_name",pod-template-hash="$pod_template_hash" -n "$ns" --timeout=${timeout_secs}s
  else
    # Fallback to waiting for any pod with the app label (for backwards compatibility)
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name="$deploy_name" -n "$ns" --timeout=${timeout_secs}s
  fi
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
