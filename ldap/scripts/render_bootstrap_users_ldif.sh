#!/usr/bin/env bash
# Generate ldap/bootstrap/20-users.ldif from 20-users.ldif.template using SSHA from LDAP_BOOTSTRAP_USER_PASSWORD.
# Prefer host slappasswd (ldap-utils); fall back to Docker (osixia/openldap) when unavailable.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
TEMPLATE="${ROOT}/bootstrap/20-users.ldif.template"
OUT="${ROOT}/bootstrap/20-users.ldif"
PASSWORD="${LDAP_BOOTSTRAP_USER_PASSWORD:-}"

if [[ -z "${PASSWORD}" ]]; then
	echo "error: LDAP_BOOTSTRAP_USER_PASSWORD is not set or empty" >&2
	exit 1
fi

if [[ ! -f "${TEMPLATE}" ]]; then
	echo "error: missing template ${TEMPLATE}" >&2
	exit 1
fi

hash_ssha() {
	local pw="$1"
	local h=""
	if command -v slappasswd >/dev/null 2>&1; then
		h=$(slappasswd -h '{SSHA}' -s "${pw}" 2>/dev/null || true)
	fi
	if [[ -z "${h}" ]] && command -v docker >/dev/null 2>&1; then
		# Pin matches ldap/docker-compose.yaml openldap image for consistency.
		h=$(docker run --rm --entrypoint slappasswd osixia/openldap:1.5.0 -h '{SSHA}' -s "${pw}" 2>/dev/null || true)
	fi
	if [[ -z "${h}" ]]; then
		echo "error: need slappasswd (apt install ldap-utils) or docker for osixia/openldap:1.5.0" >&2
		exit 1
	fi
	printf '%s' "${h}"
}

HASH=$(hash_ssha "${PASSWORD}")
export LDAP_RENDER_ROOT="${ROOT}"
export LDAP_HASH="${HASH}"
python3 - <<'PY'
import os
import pathlib

root = pathlib.Path(os.environ["LDAP_RENDER_ROOT"])
h = os.environ["LDAP_HASH"]
template = root / "bootstrap" / "20-users.ldif.template"
out = root / "bootstrap" / "20-users.ldif"
out.write_text(template.read_text().replace("__USER_PASSWORD_HASH__", h))
PY
