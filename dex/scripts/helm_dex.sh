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
deploy-integration)
	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client --debug)
	fi
	helm upgrade --install support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-f "${DEX_K8S}/values-dexidp.yaml" \
		-f "${DEX_K8S}/values-integration.yaml" \
		"${extra[@]}"
	;;
deploy-integration-oidc)
	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client --debug)
	fi
	helm upgrade --install support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-f "${DEX_K8S}/values-dexidp.yaml" \
		-f "${DEX_K8S}/values-integration.yaml" \
		-f "${DEX_K8S}/values-dex-oidc-incluster.yaml" \
		"${extra[@]}"
	;;
*)
	echo "usage: $0 template|deploy-integration|deploy-integration-oidc" >&2
	exit 1
	;;
esac
