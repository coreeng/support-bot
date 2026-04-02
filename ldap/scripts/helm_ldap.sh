#!/usr/bin/env bash
# Render or deploy LDAP via api/k8s/ldap/chart (Bitnami OpenLDAP image + bootstrap LDIF).
set -euo pipefail
OP="${1:?usage: $0 template|deploy-integration}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LDAP_K8S="${ROOT}/../api/k8s/ldap"
CHART="${LDAP_K8S}/chart"

# Default password is for helm template / local dev only; integration should set LDAP_BOOTSTRAP_USER_PASSWORD (e.g. from GitHub Actions secrets).
export LDAP_BOOTSTRAP_USER_PASSWORD="${LDAP_BOOTSTRAP_USER_PASSWORD:-password123}"
bash "${ROOT}/scripts/render_bootstrap_users_ldif.sh"
cp -f "${ROOT}/bootstrap/"*.ldif "${CHART}/files/bootstrap/"

case "${OP}" in
template)
	helm template support-bot-ldap "${CHART}" \
		-f "${LDAP_K8S}/values-bitnami.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" >/dev/null
	;;
deploy-integration)
	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client --debug)
	fi
	helm upgrade --install support-bot-ldap "${CHART}" \
		-f "${LDAP_K8S}/values-bitnami.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" \
		"${extra[@]}"
	;;
*)
	echo "usage: $0 template|deploy-integration" >&2
	exit 1
	;;
esac
