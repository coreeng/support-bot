#!/usr/bin/env bash
# Render or deploy Dex via dexidp Helm chart (dex/dex).
set -euo pipefail
OP="${1:?usage: $0 template|deploy-integration|deploy-prod}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEX_K8S="${ROOT}/../api/k8s/dex"
CHART_REPO_NAME="${DEX_CHART_REPO_NAME:-dex}"
CHART_REPO_URL="${DEX_CHART_REPO_URL:-https://charts.dexidp.io}"
CHART_NAME="${DEX_CHART_NAME:-dex}"
CHART_VERSION="${DEX_CHART_VERSION:-0.24.0}"

helm repo add "${CHART_REPO_NAME}" "${CHART_REPO_URL}" 2>/dev/null || true
helm repo update "${CHART_REPO_NAME}" >/dev/null

TMPDIR=$(mktemp -d)
trap 'rm -rf "${TMPDIR}"' EXIT

# Copy values files with secret placeholders replaced so secrets never appear in
# the process command line (vs --set-string which is visible in `ps`).
prepare_values() {
	local src="$1" dest="${TMPDIR}/$(basename "$1")"
	cp "${src}" "${dest}"
	if [[ -n "${DEX_CLIENT_SECRET:-}" ]]; then
		sed -i.bak "s|helm-template-placeholder-client-secret|${DEX_CLIENT_SECRET}|g" "${dest}"
	fi
	if [[ -n "${LDAP_BIND_PW:-}" ]]; then
		sed -i.bak "s|helm-template-placeholder-ldap-bind|${LDAP_BIND_PW}|g" "${dest}"
	fi
	rm -f "${dest}.bak"
	echo "${dest}"
}

ensure_dex_k8s_secret() {
	local ns="$1"
	echo "Ensuring K8s secret dex-secrets (client-id, client-secret)..."
	kubectl create secret generic dex-secrets \
		--from-literal=client-id="${DEX_CLIENT_ID:?Set DEX_CLIENT_ID}" \
		--from-literal=client-secret="${DEX_CLIENT_SECRET:?Set DEX_CLIENT_SECRET}" \
		-n "${ns}" --dry-run=client -o yaml | kubectl apply -f -
}

case "${OP}" in
template)
	helm template support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-f "${DEX_K8S}/values-dexidp.yaml" \
		-f "${DEX_K8S}/values-integration.yaml" >/dev/null
	;;
deploy-integration)
	NAMESPACE="${NAMESPACE:?Set NAMESPACE}"
	ensure_dex_k8s_secret "${NAMESPACE}"

	LDAP_BIND_PW="${LDAP_BOOTSTRAP_USER_PASSWORD:?Set LDAP_BOOTSTRAP_USER_PASSWORD}"

	vf_oidc=$(prepare_values "${DEX_K8S}/values-dex-oidc-incluster.yaml")

	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client)
	fi

	helm upgrade --install support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-n "${NAMESPACE}" \
		-f "${DEX_K8S}/values-dexidp.yaml" \
		-f "${DEX_K8S}/values-integration.yaml" \
		-f "${vf_oidc}" \
		"${extra[@]}"
	;;
deploy-prod)
	NAMESPACE="${NAMESPACE:?Set NAMESPACE}"
	ensure_dex_k8s_secret "${NAMESPACE}"

	LDAP_BIND_PW="${LDAP_BOOTSTRAP_USER_PASSWORD:?Set LDAP_BOOTSTRAP_USER_PASSWORD}"

	vf_base=$(prepare_values "${DEX_K8S}/values-dexidp.yaml")
	vf_tls=$(prepare_values "${DEX_K8S}/values-tls.yaml")

	helm upgrade --install support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-n "${NAMESPACE}" \
		-f "${vf_base}" \
		-f "${vf_tls}"
	;;
*)
	echo "usage: $0 template|deploy-integration|deploy-prod" >&2
	exit 1
	;;
esac
