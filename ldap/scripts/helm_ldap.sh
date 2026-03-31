#!/usr/bin/env bash
# Render or deploy LDAP via core-platform-app; drops chart templates/tests (see patched_platform_chart note in ldap/README).
set -euo pipefail
OP="${1:?usage: $0 template|deploy-integration}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LDAP_K8S="${ROOT}/../api/k8s/ldap"
TMP=$(mktemp -d)
trap 'rm -rf "${TMP}"' EXIT
helm repo add coreeng https://coreeng.github.io/core-platform-assets 2>/dev/null || true
helm repo update >/dev/null
helm pull coreeng/core-platform-app --untar --untardir "${TMP}"
rm -rf "${TMP}/core-platform-app/templates/tests"
CHART="${TMP}/core-platform-app"

case "${OP}" in
template)
	helm template support-bot-ldap "${CHART}" \
		-f "${LDAP_K8S}/values.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" >/dev/null
	;;
deploy-integration)
	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client --debug)
	fi
	helm upgrade --install support-bot-ldap "${CHART}" \
		-f "${LDAP_K8S}/values.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" \
		--set "tenantName=${TENANT_NAME}" \
		"${extra[@]}"
	;;
*)
	echo "usage: $0 template|deploy-integration" >&2
	exit 1
	;;
esac
