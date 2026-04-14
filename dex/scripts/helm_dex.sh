#!/usr/bin/env bash
# Render or deploy Dex via dexidp Helm chart (dex/dex).
set -euo pipefail
OP="${1:?usage: $0 template|deploy-integration|deploy-integration-oidc}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEX_K8S="${ROOT}/../api/k8s/dex"
CHART_REPO_NAME="${DEX_CHART_REPO_NAME:-dex}"
CHART_REPO_URL="${DEX_CHART_REPO_URL:-https://charts.dexidp.io}"
CHART_NAME="${DEX_CHART_NAME:-dex}"
CHART_VERSION="${DEX_CHART_VERSION:-0.24.0}"

helm repo add "${CHART_REPO_NAME}" "${CHART_REPO_URL}" 2>/dev/null || true
helm repo update "${CHART_REPO_NAME}" >/dev/null

case "${OP}" in
template)
	helm template support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-f "${DEX_K8S}/values-dexidp.yaml" \
		-f "${DEX_K8S}/values-integration.yaml" >/dev/null
	;;
deploy-integration|deploy-integration-oidc)
	NAMESPACE="${NAMESPACE:?Set NAMESPACE}"

	echo "Ensuring K8s secret dex-secrets (client-id, client-secret)..."
	kubectl create secret generic dex-secrets \
		--from-literal=client-id="${DEX_CLIENT_ID:?Set DEX_CLIENT_ID}" \
		--from-literal=client-secret="${DEX_CLIENT_SECRET:?Set DEX_CLIENT_SECRET}" \
		-n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client --debug)
	fi

	values_files=(
		-f "${DEX_K8S}/values-dexidp.yaml"
		-f "${DEX_K8S}/values-integration.yaml"
	)
	if [[ "${OP}" == "deploy-integration-oidc" ]]; then
		values_files+=(-f "${DEX_K8S}/values-dex-oidc-incluster.yaml")
	fi

	LDAP_BIND_PW="${LDAP_BOOTSTRAP_USER_PASSWORD:?Set LDAP_BOOTSTRAP_USER_PASSWORD}"

	helm upgrade --install support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-n "${NAMESPACE}" \
		"${values_files[@]}" \
		--set-string "config.staticClients[0].secret=${DEX_CLIENT_SECRET}" \
		--set-string "config.connectors[0].config.bindPW=${LDAP_BIND_PW}" \
		"${extra[@]}"
	;;
*)
	echo "usage: $0 template|deploy-integration|deploy-integration-oidc" >&2
	exit 1
	;;
esac
