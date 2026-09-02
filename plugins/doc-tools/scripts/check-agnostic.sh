#!/usr/bin/env bash
# check-agnostic.sh — fail if the doc-tools plugin carries anything consumer- or estate-specific.
#
# The plugin must not know who consumes it. Two denylists are grepped, case-insensitively, with a
# word-boundary prefix so that e.g. "sky" does not match "risky":
#
#   estate terms — banned everywhere under plugins/doc-tools/, the doc-settings starter included
#   site-generator terms — banned in the skill tree; skills/doc-journeys/assets/doc-settings/ may carry a clearly
#                          labelled example for one generator
#
# scripts/ is excluded: this file necessarily contains the terms. Uses grep and find only (no rg).
#
# Usage: plugins/doc-tools/scripts/check-agnostic.sh   (from anywhere; exits 1 on any hit)
set -euo pipefail

plugin_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

estate_terms=(
  core-community core-docs-docsy hugo_site tenant-wiki docs-proposed doc-journeys-reports
  src-sky dpe- sky core-platform core-networking skySlackChannelId feat/products-and-journeys
  Alex NODE_PATH cdBuild catalog-info Backstage file-references
)
ssg_terms=( Hugo Docsy shortcode pageinfo "alert title" )

join_pattern() {
  local out="" t
  for t in "$@"; do
    out+="${out:+|}\\b${t}"
  done
  printf '%s' "$out"
}

status=0

run_check() {
  local label=$1 pattern=$2; shift 2
  local hits
  # grep exits 1 on no match; that is the pass case.
  hits=$(grep -rniIE "$pattern" "$plugin_root" --exclude-dir=scripts "$@" || true)
  if [[ -n "$hits" ]]; then
    echo "FAIL: $label"
    echo "$hits" | sed "s#^$plugin_root/#  #"
    status=1
  else
    echo "ok: $label"
  fi
}

run_check "estate terms (whole plugin)" "$(join_pattern "${estate_terms[@]}")"
run_check "site-generator terms (skill tree; assets/doc-settings exempt)" \
  "$(join_pattern "${ssg_terms[@]}")" --exclude-dir=doc-settings

if [[ $status -ne 0 ]]; then
  echo
  echo "Consumer-specific content belongs in the consumer's .doc-settings/, not in the plugin."
fi
exit $status
