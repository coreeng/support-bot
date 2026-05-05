#!/usr/bin/env bash
# Render or deploy Dex via dexidp Helm chart (dex/dex).
#
# Operator-facing env flags (PT-392) — set in dex/.env.local or the deployer's environment:
#   DEX_LDAP_ENABLED      — adds LDAP connector overlay (TLS by default; plaintext when
#                           DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true)
#   DEX_GOOGLE_ENABLED    — adds Google connector overlay
#   DEX_MICROSOFT_ENABLED — adds Microsoft connector overlay
#
# Connector overlays are merged by dex/scripts/compose_helm_values.py, not by Helm
# layering — Helm's last-wins overlay merge replaces YAML lists wholesale, which
# would silently drop earlier connectors when more than one backend is enabled.
set -euo pipefail
OP="${1:?usage: $0 template|deploy-integration|deploy-prod}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEX_K8S="${ROOT}/../api/k8s/dex"
SCRIPTS="${ROOT}/scripts"
CHART_REPO_NAME="${DEX_CHART_REPO_NAME:-dex}"
CHART_REPO_URL="${DEX_CHART_REPO_URL:-https://charts.dexidp.io}"
CHART_NAME="${DEX_CHART_NAME:-dex}"
CHART_VERSION="${DEX_CHART_VERSION:-0.24.0}"

helm repo add "${CHART_REPO_NAME}" "${CHART_REPO_URL}" 2>/dev/null || true
helm repo update "${CHART_REPO_NAME}" >/dev/null

TMPDIR=$(mktemp -d)
trap 'rm -rf "${TMPDIR}"' EXIT

# Copy values files with placeholders replaced so secrets never appear in
# the process command line (vs --set-string which is visible in `ps`).
# Uses envsubst instead of sed to avoid delimiter-escaping issues with special characters in secrets.
prepare_values() {
	local src="$1" dest="${TMPDIR}/$(basename "$1")"
	PLACEHOLDER_CLIENT_SECRET="${DEX_CLIENT_SECRET:-helm-template-placeholder-client-secret}" \
	PLACEHOLDER_LDAP_BIND="${LDAP_BOOTSTRAP_USER_PASSWORD:-helm-template-placeholder-ldap-bind}" \
	PLACEHOLDER_GOOGLE_CLIENT_ID="${DEX_GOOGLE_CLIENT_ID:-helm-template-placeholder-google-client-id}" \
	PLACEHOLDER_GOOGLE_CLIENT_SECRET="${DEX_GOOGLE_CLIENT_SECRET:-helm-template-placeholder-google-client-secret}" \
	PLACEHOLDER_MICROSOFT_CLIENT_ID="${DEX_MICROSOFT_CLIENT_ID:-helm-template-placeholder-microsoft-client-id}" \
	PLACEHOLDER_MICROSOFT_CLIENT_SECRET="${DEX_MICROSOFT_CLIENT_SECRET:-helm-template-placeholder-microsoft-client-secret}" \
	PLACEHOLDER_MICROSOFT_TENANT="${DEX_MICROSOFT_TENANT:-common}" \
	PLACEHOLDER_DEX_ISSUER="${DEX_ISSUER:-https://dex.example.com}" \
		envsubst '$PLACEHOLDER_CLIENT_SECRET $PLACEHOLDER_LDAP_BIND $PLACEHOLDER_GOOGLE_CLIENT_ID $PLACEHOLDER_GOOGLE_CLIENT_SECRET $PLACEHOLDER_MICROSOFT_CLIENT_ID $PLACEHOLDER_MICROSOFT_CLIENT_SECRET $PLACEHOLDER_MICROSOFT_TENANT $PLACEHOLDER_DEX_ISSUER' < "${src}" > "${dest}"
	echo "${dest}"
}

is_truthy() {
	[[ "${1:-}" =~ ^(true|1|yes)$ ]]
}

# Emit per-backend overlay paths to stdout (one per line) based on DEX_*_ENABLED flags.
select_connector_overlays() {
	if is_truthy "${DEX_LDAP_ENABLED:-}"; then
		if [[ "${DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT:-}" == "true" ]]; then
			echo "${DEX_K8S}/values-integration-ldap-plaintext-ephemeral.yaml"
		else
			echo "${DEX_K8S}/values-tls.yaml"
		fi
	fi
	is_truthy "${DEX_GOOGLE_ENABLED:-}"    && echo "${DEX_K8S}/values-google.yaml"    || true
	is_truthy "${DEX_MICROSOFT_ENABLED:-}" && echo "${DEX_K8S}/values-microsoft.yaml" || true
}

# Render placeholders in each overlay then compose them into a single connectors values file.
# Args: <output-path> <overlay-path...>
compose_connectors() {
	local out="$1"; shift
	if [[ "$#" -eq 0 ]]; then
		printf 'config:\n  connectors: []\n' > "${out}"
		return
	fi
	local prepared=()
	local o
	for o in "$@"; do
		prepared+=("$(prepare_values "${o}")")
	done
	python3 "${SCRIPTS}/compose_helm_values.py" --out "${out}" "${prepared[@]}"
}

# Run `helm template` for one combo. Args: <label> <connector-overlay-path...>
helm_template_combo() {
	local label="$1"; shift
	local merged="${TMPDIR}/connectors-${label}.yaml"
	compose_connectors "${merged}" "$@"
	local vf_base vf_int vf_oidc
	vf_base=$(prepare_values "${DEX_K8S}/values-dexidp.yaml")
	vf_int=$(prepare_values "${DEX_K8S}/values-integration.yaml")
	vf_oidc=$(prepare_values "${DEX_K8S}/values-dex-oidc-incluster.yaml")
	echo "Rendering combo: ${label}"
	helm template support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-f "${vf_base}" \
		-f "${vf_int}" \
		-f "${vf_oidc}" \
		-f "${merged}" >/dev/null
}

ensure_dex_k8s_secret() {
	local ns="$1"
	echo "Ensuring K8s secret dex-secrets..."
	local args=(
		--from-literal=client-id="${DEX_CLIENT_ID:?Set DEX_CLIENT_ID}"
		--from-literal=client-secret="${DEX_CLIENT_SECRET:?Set DEX_CLIENT_SECRET}"
	)
	[[ -n "${LDAP_BOOTSTRAP_USER_PASSWORD:-}" ]] && args+=(--from-literal=ldap-bind-password="${LDAP_BOOTSTRAP_USER_PASSWORD}")
	[[ -n "${DEX_GOOGLE_CLIENT_ID:-}" ]]         && args+=(--from-literal=google-client-id="${DEX_GOOGLE_CLIENT_ID}")
	[[ -n "${DEX_GOOGLE_CLIENT_SECRET:-}" ]]     && args+=(--from-literal=google-client-secret="${DEX_GOOGLE_CLIENT_SECRET}")
	[[ -n "${DEX_MICROSOFT_CLIENT_ID:-}" ]]      && args+=(--from-literal=microsoft-client-id="${DEX_MICROSOFT_CLIENT_ID}")
	[[ -n "${DEX_MICROSOFT_CLIENT_SECRET:-}" ]]  && args+=(--from-literal=microsoft-client-secret="${DEX_MICROSOFT_CLIENT_SECRET}")
	kubectl create secret generic dex-secrets "${args[@]}" \
		-n "${ns}" --dry-run=client -o yaml | kubectl apply -f -
}

# Read selected overlays into SELECTED_OVERLAYS (portable to bash 3.2 / macOS).
read_selected_overlays() {
	SELECTED_OVERLAYS=()
	local line
	while IFS= read -r line; do
		[[ -n "${line}" ]] && SELECTED_OVERLAYS+=("${line}")
	done < <(select_connector_overlays)
}

require_at_least_one_backend() {
	if [[ "${#SELECTED_OVERLAYS[@]}" -eq 0 ]]; then
		echo "No connector enabled. Set at least one of DEX_LDAP_ENABLED, DEX_GOOGLE_ENABLED, DEX_MICROSOFT_ENABLED." >&2
		echo "See dex/.env.example and api/k8s/dex/README.md." >&2
		exit 1
	fi
}

# Fail loudly if a backend is enabled but its credentials are missing — without this,
# prepare_values() would substitute the helm-template-placeholder-* defaults into a
# real deploy, producing a Dex pod that starts cleanly but rejects every login.
# Skipped by the template op (no real cluster, placeholders are fine for chart validation).
require_credentials_for_enabled_backends() {
	local missing=()
	if is_truthy "${DEX_LDAP_ENABLED:-}"; then
		[[ -n "${LDAP_BOOTSTRAP_USER_PASSWORD:-}" ]] || missing+=("LDAP_BOOTSTRAP_USER_PASSWORD (DEX_LDAP_ENABLED)")
	fi
	if is_truthy "${DEX_GOOGLE_ENABLED:-}"; then
		[[ -n "${DEX_GOOGLE_CLIENT_ID:-}" ]]     || missing+=("DEX_GOOGLE_CLIENT_ID (DEX_GOOGLE_ENABLED)")
		[[ -n "${DEX_GOOGLE_CLIENT_SECRET:-}" ]] || missing+=("DEX_GOOGLE_CLIENT_SECRET (DEX_GOOGLE_ENABLED)")
	fi
	if is_truthy "${DEX_MICROSOFT_ENABLED:-}"; then
		[[ -n "${DEX_MICROSOFT_CLIENT_ID:-}" ]]     || missing+=("DEX_MICROSOFT_CLIENT_ID (DEX_MICROSOFT_ENABLED)")
		[[ -n "${DEX_MICROSOFT_CLIENT_SECRET:-}" ]] || missing+=("DEX_MICROSOFT_CLIENT_SECRET (DEX_MICROSOFT_ENABLED)")
	fi
	if is_truthy "${DEX_GOOGLE_ENABLED:-}" || is_truthy "${DEX_MICROSOFT_ENABLED:-}"; then
		[[ -n "${DEX_ISSUER:-}" ]] || missing+=("DEX_ISSUER (required for Google/Microsoft redirectURI; must match config.issuer in your Dex values)")
	fi
	if [[ "${#missing[@]}" -gt 0 ]]; then
		echo "Missing required env vars for enabled Dex connectors:" >&2
		local m
		for m in "${missing[@]}"; do
			echo "  - ${m}" >&2
		done
		echo "See dex/.env.example and api/k8s/dex/README.md." >&2
		exit 1
	fi
}

case "${OP}" in
template)
	# Render every meaningful combination so values mistakes for any backend are caught in CI,
	# not only when an operator first enables that backend on a real cluster.
	helm_template_combo "ldap-tls"        "${DEX_K8S}/values-tls.yaml"
	helm_template_combo "ldap-plaintext"  "${DEX_K8S}/values-integration-ldap-plaintext-ephemeral.yaml"
	helm_template_combo "google"          "${DEX_K8S}/values-google.yaml"
	helm_template_combo "microsoft"       "${DEX_K8S}/values-microsoft.yaml"
	helm_template_combo "ldap-tls-google" "${DEX_K8S}/values-tls.yaml" "${DEX_K8S}/values-google.yaml"
	helm_template_combo "all-three-tls"   "${DEX_K8S}/values-tls.yaml" "${DEX_K8S}/values-google.yaml" "${DEX_K8S}/values-microsoft.yaml"
	helm_template_combo "all-three-plain" "${DEX_K8S}/values-integration-ldap-plaintext-ephemeral.yaml" "${DEX_K8S}/values-google.yaml" "${DEX_K8S}/values-microsoft.yaml"
	;;
deploy-integration)
	if is_truthy "${DEX_LDAP_ENABLED:-}" && [[ "${DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT:-}" != "true" ]]; then
		echo "Refusing to deploy Dex with plaintext LDAP (insecureNoSSL on port 389)." >&2
		echo "That connector must only be used on disposable integration namespaces." >&2
		echo "Set DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true to confirm, or unset DEX_LDAP_ENABLED" >&2
		echo "and use values-tls.yaml via deploy-prod for LDAP/TLS." >&2
		echo "Repo Makefile target dex-deploy-integration sets this for integration-test infra." >&2
		echo "See api/k8s/dex/README.md and docs/runbooks/auth-dex-ldap.md." >&2
		exit 1
	fi

	NAMESPACE="${NAMESPACE:?Set NAMESPACE}"

	read_selected_overlays
	require_at_least_one_backend
	require_credentials_for_enabled_backends

	ensure_dex_k8s_secret "${NAMESPACE}"

	merged="${TMPDIR}/connectors.yaml"
	compose_connectors "${merged}" "${SELECTED_OVERLAYS[@]}"

	vf_base=$(prepare_values "${DEX_K8S}/values-dexidp.yaml")
	vf_int=$(prepare_values "${DEX_K8S}/values-integration.yaml")
	vf_oidc=$(prepare_values "${DEX_K8S}/values-dex-oidc-incluster.yaml")

	extra=()
	if [[ -n "${DRY_RUN:-}" ]]; then
		extra+=(--dry-run=client)
	fi

	helm upgrade --install support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-n "${NAMESPACE}" \
		-f "${vf_base}" \
		-f "${vf_int}" \
		-f "${vf_oidc}" \
		-f "${merged}" \
		"${extra[@]}"
	;;
deploy-prod)
	if [[ "${DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT:-}" == "true" ]]; then
		echo "deploy-prod refuses DEX_DEPLOY_INSECURE_LDAP_PLAINTEXT=true. Use values-tls.yaml (DEX_LDAP_ENABLED=true with the plaintext flag unset)." >&2
		exit 1
	fi

	NAMESPACE="${NAMESPACE:?Set NAMESPACE}"

	read_selected_overlays
	require_at_least_one_backend
	require_credentials_for_enabled_backends

	ensure_dex_k8s_secret "${NAMESPACE}"

	merged="${TMPDIR}/connectors.yaml"
	compose_connectors "${merged}" "${SELECTED_OVERLAYS[@]}"

	vf_base=$(prepare_values "${DEX_K8S}/values-dexidp.yaml")

	helm upgrade --install support-bot-dex "${CHART_REPO_NAME}/${CHART_NAME}" --version "${CHART_VERSION}" \
		-n "${NAMESPACE}" \
		-f "${vf_base}" \
		-f "${merged}"
	;;
*)
	echo "usage: $0 template|deploy-integration|deploy-prod" >&2
	exit 1
	;;
esac
