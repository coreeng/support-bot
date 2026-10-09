#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SCRIPT="${SCRIPT_DIR}/deploy-test-db.sh"
CHART_PATH="${TEST_DB_CHART_PATH:-${SCRIPT_DIR}/../k8s/test-postgres18}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if ! compgen -G "${CHART_PATH}/charts/core-platform-app-*.tgz" > /dev/null 2>&1 \
  && [[ ! -f "${CHART_PATH}/charts/core-platform-app/Chart.yaml" ]]; then
  helm repo add core-platform-assets https://coreeng.github.io/core-platform-assets >/dev/null 2>&1 || true
  helm dependency build "$CHART_PATH" >/dev/null
fi

rendered="$(helm template support-bot-db "$CHART_PATH" --set core-platform-app.fullnameOverride=support-bot-db-postgresql)"
for expected in \
  'name: support-bot-db-postgresql-headless' \
  'name: support-bot-db-postgresql' \
  "name: 'support-bot-db-postgresql'" \
  'serviceName: support-bot-db-postgresql-headless' \
  'image: "postgres:18.6-alpine3.23"' \
  'runAsUser: 70' \
  'runAsGroup: 70' \
  'pg_isready' \
  'POSTGRES_USER' \
  'secretKeyRef:' \
  'mountPath: /var/lib/postgresql' \
  'emptyDir: {}' \
  'requests:' \
  'limits:' \
  'kind: Secret'; do
  grep -Fq "$expected" <<< "$rendered" || { echo "render missing: $expected" >&2; exit 1; }
done
if grep -Eq 'persistentVolumeClaim:|kind: (ServiceAccount|VerticalPodAutoscaler|PodDisruptionBudget)' <<< "$rendered"; then
  echo "render contains persistent storage or an unnecessary chart-created resource" >&2
  exit 1
fi

mkdir -p "$TMP_DIR/bin"
cat > "$TMP_DIR/bin/helm" <<'HELM'
#!/usr/bin/env bash
case "$1" in
  list)
    if [[ "$*" == *" -q"* ]]; then
      [[ "${FAKE_PRESENT:-false}" == true ]] && echo "$DB_RELEASE"
      exit 0
    elif [[ "${FAKE_PRESENT:-false}" == true ]]; then
      printf '[{"chart":"%s","status":"%s"}]\n' "${FAKE_CHART:-supportbot-postgres18-test-1.0.0}" "${FAKE_STATUS:-deployed}"
    else
      echo '[]'
    fi
    ;;
  uninstall)
    echo "$1" >> "$FAKE_OPERATIONS"
    [[ "${FAKE_UNINSTALL_FAIL:-false}" != true ]]
    ;;
  upgrade)
    echo "$1" >> "$FAKE_OPERATIONS"
    ;;
  dependency) ;;
  *) echo "unexpected helm command: $*" >&2; exit 2 ;;
esac
HELM
cat > "$TMP_DIR/bin/kubectl" <<'KUBECTL'
#!/usr/bin/env bash
[[ "${FAKE_FAIL_FIELD:-}" == *serviceName* && "$*" == *serviceName* ]] && exit 1
case "$*" in
  *'jsonpath={.spec.template.spec.containers[?(@.name=="postgres")].image}'*) echo "${FAKE_IMAGE:-postgres:18.6-alpine3.23}" ;;
  *'jsonpath={.spec.volumeClaimTemplates'* ) echo "${FAKE_CLAIMS:-} ${FAKE_PVC:-}" ;;
  *'jsonpath={.spec.template.spec.volumes[?(@.name=="data")].emptyDir}'*) echo "${FAKE_EMPTYDIR:-{}}" ;;
  *'jsonpath={.spec.template.spec.containers[?(@.name=="postgres")].volumeMounts[?(@.name=="data")].mountPath}'*) echo "${FAKE_MOUNT:-/var/lib/postgresql}" ;;
  *'jsonpath={.spec.serviceName}'*) echo "${FAKE_SERVICE_NAME:-support-bot-db-postgresql-headless}" ;;
  *'get pod '*jsonpath*) echo fake-postgres-pod ;;
  *'exec '*) ;;
  *'get statefulset '*) ;;
  *) echo "unexpected kubectl command: $*" >&2; exit 2 ;;
esac
KUBECTL
chmod +x "$TMP_DIR/bin/helm" "$TMP_DIR/bin/kubectl"

run_case() {
  local name="$1" expected_status="$2" expected_operations="$3" actual_status actual_operations
  shift 3
  : > "$TMP_DIR/operations"
  set +e
  if [[ "${DEBUG_TEST:-false}" == true ]]; then
    env PATH="$TMP_DIR/bin:$PATH" FAKE_OPERATIONS="$TMP_DIR/operations" \
      NAMESPACE=test DB_RELEASE=support-bot-db TEST_DB_CHART_PATH="$CHART_PATH" "$@" \
      "$DEPLOY_SCRIPT"
  else
    env PATH="$TMP_DIR/bin:$PATH" FAKE_OPERATIONS="$TMP_DIR/operations" \
      NAMESPACE=test DB_RELEASE=support-bot-db TEST_DB_CHART_PATH="$CHART_PATH" "$@" \
      "$DEPLOY_SCRIPT" >/dev/null 2>&1
  fi
  actual_status=$?
  set -e
  actual_operations="$(cat "$TMP_DIR/operations")"
  [[ "$actual_status" -eq "$expected_status" ]] || { echo "$name: exit $actual_status, expected $expected_status" >&2; exit 1; }
  [[ "$actual_operations" == "$expected_operations" ]] || { echo "$name: operations '$actual_operations', expected '$expected_operations'" >&2; exit 1; }
}

run_case first-install 0 upgrade ACTION=deploy FAKE_PRESENT=false
run_case old-chart-recreate 0 $'uninstall\nupgrade' ACTION=deploy FAKE_PRESENT=true FAKE_SERVICE_NAME=support-bot-db-postgresql-hl
run_case old-chart-uninstall-failure 1 uninstall ACTION=deploy FAKE_PRESENT=true FAKE_SERVICE_NAME=support-bot-db-postgresql-hl FAKE_UNINSTALL_FAIL=true
run_case new-chart-upgrade 0 upgrade ACTION=deploy FAKE_PRESENT=true FAKE_CHART=supportbot-postgres18-test-1.1.0
run_case legacy-chart-rejected 1 '' ACTION=deploy FAKE_PRESENT=true FAKE_CHART=postgresql-15.5.0
run_case changed-image-rejected 1 '' ACTION=deploy FAKE_PRESENT=true FAKE_IMAGE=postgres:17-alpine
run_case pvc-rejected 1 '' ACTION=deploy FAKE_PRESENT=true FAKE_PVC=data
run_case unknown-service-rejected 1 '' ACTION=deploy FAKE_PRESENT=true FAKE_SERVICE_NAME=unrelated-service
run_case service-read-failure 1 '' ACTION=deploy FAKE_PRESENT=true FAKE_FAIL_FIELD=serviceName

echo "Disposable PostgreSQL chart and release guard checks passed"
