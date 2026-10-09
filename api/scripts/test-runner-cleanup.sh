#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

RUNNER_DIR="${TMP_DIR}/scripts"
BIN_DIR="${TMP_DIR}/bin"
TRACE="${TMP_DIR}/trace"
mkdir -p "$RUNNER_DIR" "$BIN_DIR"
cp "${SCRIPT_DIR}/run-functional-tests.sh" "${SCRIPT_DIR}/run-integration-tests.sh" \
  "${SCRIPT_DIR}/run-nft-tests.sh" "${SCRIPT_DIR}/deploy-service.sh" "$RUNNER_DIR/"

cat > "${RUNNER_DIR}/lib.sh" <<'LIB'
record() { printf '%s\n' "$*" >> "$TRACE"; }
log() { :; }
log_warning() { printf '%s\n' "$*" >&2; }
log_error() { printf '%s\n' "$*" >&2; }
log_success() { :; }
print_grafana_logs_url() { :; }
save_job_logs() { :; }
sleep_for_log_flush() { :; }
helm_uninstall_if_exists() { record "helm-if-exists:$1"; }
wait_for_job_with_logs() { record "wait-job:$1"; return "${JOB_STATUS:-0}"; }
show_job_status() { :; }
LIB

cat > "${RUNNER_DIR}/deploy-test-db.sh" <<'DB'
#!/usr/bin/env bash
set -euo pipefail
printf 'db-%s\n' "$ACTION" >> "$TRACE"
case "$ACTION" in
  check|deploy)
    if [[ "${GUARD_FAIL:-false}" == true ]]; then
      echo 'Error: DB ownership guard refused operation' >&2
      exit 1
    fi
    ;;
  delete)
    if [[ "${DELETE_STATUS:-0}" -ne 0 ]]; then
      echo 'Error: failed to delete release: support-bot-db' >&2
      exit "$DELETE_STATUS"
    fi
    ;;
  *) exit 2 ;;
esac
DB
chmod +x "${RUNNER_DIR}/deploy-test-db.sh"

cat > "${BIN_DIR}/helm" <<'HELM'
#!/usr/bin/env bash
printf 'helm:%s\n' "$*" >> "$TRACE"
HELM
cat > "${BIN_DIR}/kubectl" <<'KUBECTL'
#!/usr/bin/env bash
printf 'kubectl:%s\n' "$*" >> "$TRACE"
KUBECTL
chmod +x "${BIN_DIR}/helm" "${BIN_DIR}/kubectl"

assert_absent() {
  if grep -Eq "$1" "$2"; then
    echo "Unexpected trace entry: $1" >&2
    cat "$2" >&2
    exit 1
  fi
}

run_case() {
  local runner="$1" name="$2" expected="$3" actual
  shift 3
  : > "$TRACE"
  set +e
  env PATH="${BIN_DIR}:${PATH}" TRACE="$TRACE" \
    NAMESPACE=cleanup-test JOB_IMAGE_REPOSITORY=test/job IMAGE_TAG=test \
    SERVICE_IMAGE_REPOSITORY=test/service SERVICE_IMAGE_TAG=test \
    DEPLOY_SERVICE=false CLEAN_DEPLOY_DB=true DEPLOY_DB=true DELETE_DB=true \
    CLEANUP=true KEEP_ON_FAILURE=false JOB_STATUS=0 DELETE_STATUS=0 GUARD_FAIL=false \
    SLACK_TOKEN=test SLACK_SOCKET_TOKEN=test SLACK_SIGNING_SECRET=test \
    AZURE_TENANT_ID=test AZURE_CLIENT_ID=test AZURE_CLIENT_SECRET=test \
    "$@" "${RUNNER_DIR}/${runner}" > "${TMP_DIR}/output" 2>&1
  actual=$?
  set -e
  if [[ "$actual" -ne "$expected" ]]; then
    cat "${TMP_DIR}/output" >&2
    echo "$runner $name: exit $actual, expected $expected" >&2
    exit 1
  fi
  if [[ "$name" == db-failure || "$name" == test-and-db-failure ]]; then
    grep -Fq 'Error: failed to delete release: support-bot-db' "${TMP_DIR}/output"
  fi
  if [[ "$name" == successful-cleanup ]]; then
    grep -Fq 'db-delete' "$TRACE"
    awk '/helm:uninstall/ {helm=NR} /db-delete/ {db=NR} END {exit !(helm && db && helm < db)}' "$TRACE"
  fi
  if [[ "$name" == keep-on-failure ]]; then
    assert_absent 'db-delete' "$TRACE"
    assert_absent 'helm:uninstall' "$TRACE"
  fi
  if [[ "$name" == guard-refusal ]]; then
    grep -Fq 'Cleanup failed because the DB release could not be verified' "${TMP_DIR}/output"
    assert_absent 'db-delete' "$TRACE"
    assert_absent 'helm:' "$TRACE"
    assert_absent 'kubectl:(create|apply|delete|exec)' "$TRACE"
  fi
}

for runner in run-functional-tests.sh run-integration-tests.sh run-nft-tests.sh; do
  run_case "$runner" successful-cleanup 0
  run_case "$runner" db-failure 1 DELETE_STATUS=7
  run_case "$runner" test-and-db-failure 1 JOB_STATUS=1 DELETE_STATUS=7
  run_case "$runner" keep-on-failure 1 JOB_STATUS=1 KEEP_ON_FAILURE=true
  run_case "$runner" guard-refusal 1 GUARD_FAIL=true
done

# Test the actual Make recipes from a copy without its deploy prerequisite.
MAKE_ROOT="${TMP_DIR}/make-root"
mkdir -p "${MAKE_ROOT}/api/scripts"
sed '/Download and include p2p makefile/,+2d; /^integration-test-local:/s/ deploy-integration//' \
  "${ROOT}/Makefile" > "${MAKE_ROOT}/Makefile"
cat > "${MAKE_ROOT}/api/scripts/deploy-test-db.sh" <<'DB'
#!/usr/bin/env bash
case "$ACTION" in
  check) exit "${GUARD_STATUS:-0}" ;;
  delete) exit "${DELETE_STATUS:-0}" ;;
  *) exit 2 ;;
esac
DB
cat > "${MAKE_ROOT}/api/gradlew" <<'GRADLE'
#!/usr/bin/env bash
exit "${TEST_STATUS:-0}"
GRADLE
chmod +x "${MAKE_ROOT}/api/scripts/deploy-test-db.sh" "${MAKE_ROOT}/api/gradlew"

run_make_case() {
  local name="$1" expected="$2" reported="$3" actual
  shift 3
  : > "$TRACE"
  set +e
  (cd "$MAKE_ROOT" && env PATH="${BIN_DIR}:${PATH}" TRACE="$TRACE" "$@" make -s integration-test-local) \
    > "${TMP_DIR}/make-output" 2>&1
  actual=$?
  set -e
  [[ "$actual" -eq "$expected" ]] || {
    cat "${TMP_DIR}/make-output" >&2
    echo "integration-test-local $name: exit $actual, expected $expected" >&2
    exit 1
  }
  if ! grep -Fq "Error $reported" "${TMP_DIR}/make-output"; then
    cat "${TMP_DIR}/make-output" >&2
    echo "integration-test-local $name: expected reported status $reported" >&2
    exit 1
  fi
}

run_make_case cleanup-failure 2 1 TEST_STATUS=0 DELETE_STATUS=7
run_make_case original-test-failure 2 6 TEST_STATUS=6 DELETE_STATUS=7
run_make_case guard-refusal 2 1 TEST_STATUS=0 GUARD_STATUS=1
assert_absent 'db-delete' "$TRACE"
assert_absent 'helm:' "$TRACE"

echo "Functional, integration, NFT, and Make cleanup exit checks passed"
