#!/usr/bin/env bash
# Render or deploy LDAP via api/k8s/ldap/chart (Bitnami OpenLDAP image + bootstrap LDIF).
set -euo pipefail
OP="${1:?usage: $0 template|deploy-integration}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LDAP_K8S="${ROOT}/../api/k8s/ldap"
CHART="${LDAP_K8S}/chart"
NAMESPACE="${NAMESPACE:-support-bot-integration}"

if [[ -z "${LDAP_BOOTSTRAP_USER_PASSWORD:-}" ]]; then
	echo "error: LDAP_BOOTSTRAP_USER_PASSWORD is required (local: ldap/.env.local; CI: secret LDAP_BOOTSTRAP_USER_PASSWORD / P2P env_vars — see ldap/README.md)." >&2
	exit 1
fi
bash "${ROOT}/scripts/render_bootstrap_users_ldif.sh"
cp -f "${ROOT}/bootstrap/"*.ldif "${CHART}/files/bootstrap/"

case "${OP}" in
template)
	helm template support-bot-ldap "${CHART}" \
		-f "${LDAP_K8S}/values-bitnami.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" >/dev/null
	;;
deploy-integration)
	kubectl create namespace "${NAMESPACE}" 2>/dev/null || true

	echo "Ensuring K8s secret ldap-secrets (admin-password) in ${NAMESPACE}..."
	kubectl create secret generic ldap-secrets \
		--from-literal=admin-password="${LDAP_BOOTSTRAP_USER_PASSWORD}" \
		-n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

	echo "Ensuring K8s secret integration-ldap-test-user (password for bootstrap users) in ${NAMESPACE}..."
	kubectl create secret generic integration-ldap-test-user \
		--from-literal=password="${LDAP_BOOTSTRAP_USER_PASSWORD}" \
		-n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client --debug)
	fi
	helm upgrade --install support-bot-ldap "${CHART}" \
		-n "${NAMESPACE}" \
		-f "${LDAP_K8S}/values-bitnami.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" \
		"${extra[@]}"
	;;
*)
	echo "usage: $0 template|deploy-integration" >&2
	exit 1
	;;
esac
