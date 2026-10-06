#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "${SCRIPT_DIR}/lib.sh"

NAMESPACE="${NAMESPACE:-support-bot-integration}"
DB_RELEASE="${DB_RELEASE:-support-bot-db}"
ACTION="${ACTION:-deploy}" # check|deploy|delete
TEST_DB_CHART_PATH="${TEST_DB_CHART_PATH:-${SCRIPT_DIR}/../k8s/test-postgres18}"
TEST_DB_CHART_NAME="supportbot-postgres18-test"
TEST_DB_IMAGE="postgres:18.6-alpine3.23"
if [[ ! "$DB_RELEASE" =~ ^[a-z0-9]([-a-z0-9.]*[a-z0-9])?$ ]]; then
  log_error "Invalid Helm DB release name ${DB_RELEASE}; refusing database operation."
  exit 1
fi
DB_RELEASE_FILTER="^${DB_RELEASE//./\\.}$"

# Never adopt a release based only on its name. Existing releases must identify
# as our test chart and have the expected PostgreSQL image and emptyDir storage.
check_db_release() {
  local releases release_json chart status statefulset image claims data_volume data_mount
  if ! releases=$(helm list --all -n "$NAMESPACE" -f "$DB_RELEASE_FILTER" -q); then
    log_error "Cannot inspect Helm releases in ${NAMESPACE}; refusing database operation."
    return 1
  fi
  if ! grep -Fxq "$DB_RELEASE" <<< "$releases"; then
    return 0
  fi

  if ! release_json=$(helm list --all -n "$NAMESPACE" -f "$DB_RELEASE_FILTER" -o json); then
    log_error "Cannot inspect DB release ${DB_RELEASE} in ${NAMESPACE}; refusing database operation."
    return 1
  fi
  chart=$(sed -n 's/.*"chart":"\([^"]*\)".*/\1/p' <<< "$release_json")
  status=$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$release_json")
  if [[ "$chart" != "${TEST_DB_CHART_NAME}-"* ]]; then
    log_error "Refusing to modify DB release ${DB_RELEASE} in ${NAMESPACE}: chart is ${chart:-unknown}, expected ${TEST_DB_CHART_NAME}. Preserve this database; use a fresh test namespace or migrate it explicitly."
    return 1
  fi
  if [[ "$status" != "deployed" && "$status" != pending-* && "$status" != "failed" ]]; then
    log_error "Refusing to modify DB release ${DB_RELEASE} in ${NAMESPACE}: unexpected Helm status ${status:-unknown}."
    return 1
  fi

  statefulset="${DB_RELEASE}-postgresql"
  if ! kubectl get statefulset "$statefulset" -n "$NAMESPACE" >/dev/null 2>&1; then
    log_error "Refusing to modify DB release ${DB_RELEASE} in ${NAMESPACE}: expected test StatefulSet is missing."
    return 1
  fi
  if ! image=$(kubectl get statefulset "$statefulset" -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[?(@.name=="postgres")].image}'); then
    log_error "Cannot read DB image for ${DB_RELEASE} in ${NAMESPACE}; refusing database operation."
    return 1
  fi
  if ! claims=$(kubectl get statefulset "$statefulset" -n "$NAMESPACE" \
    -o jsonpath='{.spec.volumeClaimTemplates[*].metadata.name}{" "}{range .spec.template.spec.volumes[*]}{.persistentVolumeClaim.claimName}{" "}{end}'); then
    log_error "Cannot read DB volumes for ${DB_RELEASE} in ${NAMESPACE}; refusing database operation."
    return 1
  fi
  if ! data_volume=$(kubectl get statefulset "$statefulset" -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.volumes[?(@.name=="data")].emptyDir}'); then
    log_error "Cannot verify ephemeral DB storage for ${DB_RELEASE} in ${NAMESPACE}; refusing database operation."
    return 1
  fi
  if ! data_mount=$(kubectl get statefulset "$statefulset" -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[?(@.name=="postgres")].volumeMounts[?(@.name=="data")].mountPath}'); then
    log_error "Cannot verify PostgreSQL data mount for ${DB_RELEASE} in ${NAMESPACE}; refusing database operation."
    return 1
  fi

  if [[ "$image" != "$TEST_DB_IMAGE" || -n "${claims// /}" || ( "$data_volume" != "{}" && "$data_volume" != "map[]" ) || "$data_mount" != "/var/lib/postgresql" ]]; then
    log_error "Refusing to modify DB release ${DB_RELEASE} in ${NAMESPACE}: the live workload is not the expected PostgreSQL 18.6 emptyDir test database."
    return 1
  fi
}

case "$ACTION" in
  check)
    check_db_release
    ;;
  deploy)
    check_db_release
    if ! release_json=$(helm list --all -n "$NAMESPACE" -f "$DB_RELEASE_FILTER" -o json); then
      log_error "Cannot inspect DB release ${DB_RELEASE} in ${NAMESPACE}; refusing deployment."
      exit 1
    fi
    status=$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$release_json")
    if [[ "$status" == pending-* ]]; then
      helm uninstall "$DB_RELEASE" -n "$NAMESPACE" --wait --timeout=3m
    fi
    helm upgrade --install "$DB_RELEASE" "$TEST_DB_CHART_PATH" \
      -n "$NAMESPACE" --wait --atomic --timeout=3m
    pod=$(kubectl get pod -n "$NAMESPACE" \
      -l "app.kubernetes.io/instance=${DB_RELEASE},app.kubernetes.io/name=postgresql" \
      -o jsonpath='{.items[0].metadata.name}')
    kubectl exec -n "$NAMESPACE" "$pod" -- \
      env PGPASSWORD=supportbotpassword \
      psql -U supportbot -d supportbot -c \
      "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
    log_success "Disposable PostgreSQL 18 test DB ${DB_RELEASE} is ready in ${NAMESPACE}"
    ;;
  delete)
    check_db_release
    if ! releases=$(helm list --all -n "$NAMESPACE" -f "$DB_RELEASE_FILTER" -q); then
      log_error "Cannot inspect Helm releases in ${NAMESPACE}; refusing database deletion."
      exit 1
    fi
    if grep -Fxq "$DB_RELEASE" <<< "$releases"; then
      helm uninstall "$DB_RELEASE" -n "$NAMESPACE" --wait --timeout=3m
    fi
    ;;
  *)
    log_error "Unknown ACTION=${ACTION}. Use check|deploy|delete."
    exit 2
    ;;
esac
