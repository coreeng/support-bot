#!/usr/bin/env bash
# Render or deploy LDAP via api/k8s/ldap/chart (Bitnami OpenLDAP image + bootstrap LDIF).
set -euo pipefail
OP="${1:?usage: $0 template|deploy-integration}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LDAP_K8S="${ROOT}/../api/k8s/ldap"
CHART="${LDAP_K8S}/chart"
NAMESPACE="${NAMESPACE:-support-bot-integration}"
PLAIN="${LDAP_K8S}/values-integration-ldap-plaintext-ephemeral.yaml"

if [[ -z "${LDAP_BOOTSTRAP_USER_PASSWORD:-}" ]]; then
	echo "error: LDAP_BOOTSTRAP_USER_PASSWORD is required (local: ldap/.env.local; CI: secret LDAP_BOOTSTRAP_USER_PASSWORD / P2P env_vars — see ldap/README.md)." >&2
	exit 1
fi
bash "${ROOT}/scripts/render_bootstrap_users_ldif.sh"

TMPDIR=$(mktemp -d)
trap 'rm -rf "${TMPDIR}"' EXIT
cp -a "${CHART}" "${TMPDIR}/chart"
cp -f "${ROOT}/bootstrap/"*.ldif "${TMPDIR}/chart/files/bootstrap/"

case "${OP}" in
template)
	helm template support-bot-ldap "${TMPDIR}/chart" \
		-f "${LDAP_K8S}/values-bitnami.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" \
		-f "${PLAIN}" >/dev/null
	;;
deploy-integration)
	if [[ "${LDAP_DEPLOY_INSECURE_PLAINTEXT:-}" != "true" ]]; then
		echo "Refusing to deploy LDAP with ephemeral plaintext overlay (port 389, no TLS)." >&2
		echo "That mode is only for disposable integration namespaces." >&2
		echo "Set LDAP_DEPLOY_INSECURE_PLAINTEXT=true to confirm, or deploy with values-tls.yaml" >&2
		echo "and a real tls cert Secret (no plaintext overlay)." >&2
		echo "Repo Makefile target ldap-deploy-integration sets this for integration-test infra." >&2
		echo "See api/k8s/ldap/README.md and docs/runbooks/auth-dex-ldap.md." >&2
		exit 1
	fi

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
		extra+=(--dry-run=client)
	fi
	helm upgrade --install support-bot-ldap "${TMPDIR}/chart" \
		-n "${NAMESPACE}" \
		-f "${LDAP_K8S}/values-bitnami.yaml" \
		-f "${LDAP_K8S}/values-integration.yaml" \
		-f "${PLAIN}" \
		"${extra[@]}"
	;;
*)
	echo "usage: $0 template|deploy-integration" >&2
	exit 1
	;;
esac
