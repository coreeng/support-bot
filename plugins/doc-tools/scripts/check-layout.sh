#!/usr/bin/env bash
# check-layout.sh — structural checks on the doc-tools plugin.
#
#   1. every ${CLAUDE_PLUGIN_ROOT}/... path mentioned anywhere in the plugin resolves to a file or
#      directory that exists in the plugin
#   2. every doc-tools:<agent> name the skills spawn has a matching agents/<agent>.md whose
#      frontmatter `name:` agrees
#   3. plugin.json and the root marketplace.json parse as JSON and carry the required fields
#      (plugin: name; marketplace: name, owner.name, plugins[].name + source), and the
#      marketplace entry points at this plugin
#
# Uses grep, find and python3 only (no rg). Exits 1 on any failure.
set -euo pipefail

plugin_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
repo_root=$(cd "$plugin_root/../.." && pwd)
status=0

echo "== 1. \${CLAUDE_PLUGIN_ROOT} paths resolve"
paths=$(grep -rhoIE '\$\{CLAUDE_PLUGIN_ROOT\}[A-Za-z0-9_./-]*' "$plugin_root" --exclude-dir=scripts | sort -u)
count=0
while IFS= read -r p; do
  [[ -z "$p" ]] && continue
  rel=${p#\$\{CLAUDE_PLUGIN_ROOT\}}
  rel=${rel#/}
  count=$((count + 1))
  if [[ ! -e "$plugin_root/$rel" ]]; then
    echo "FAIL: $p does not exist in the plugin"
    grep -rnF "$p" "$plugin_root" --exclude-dir=scripts | sed "s#^$plugin_root/#  #" | head -5
    status=1
  fi
done <<< "$paths"
echo "   $count distinct paths checked"

echo "== 2. spawned agent names match agents/*.md"
names=$(grep -rhoE 'doc-tools:doc-[a-z]+(-[a-z]+)*' "$plugin_root/skills" | sort -u)
while IFS= read -r n; do
  [[ -z "$n" ]] && continue
  agent=${n#doc-tools:}
  [[ "$agent" == "doc-journeys" || "$agent" == "doc-run" ]] && continue   # skills, not agents
  file="$plugin_root/agents/$agent.md"
  if [[ ! -f "$file" ]]; then
    echo "FAIL: $n is spawned but $file does not exist"
    status=1
  elif ! grep -qE "^name: $agent\$" "$file"; then
    echo "FAIL: $file frontmatter name does not match $agent"
    status=1
  fi
done <<< "$names"
for f in "$plugin_root"/agents/*.md; do
  agent=$(basename "$f" .md)
  grep -rqF "doc-tools:$agent" "$plugin_root/skills/doc-run/SKILL.md" \
    || { echo "FAIL: agents/$agent.md is never spawned by doc-run under its namespaced name"; status=1; }
done
echo "   $(echo "$names" | grep -c . ) namespaced names checked"

echo "== 3. manifests"
python3 - "$plugin_root/.claude-plugin/plugin.json" "$repo_root/.claude-plugin/marketplace.json" <<'PY' || status=1
import json, re, sys
plugin_path, market_path = sys.argv[1], sys.argv[2]
ok = True
def fail(msg):
    global ok
    ok = False
    print(f"FAIL: {msg}")
kebab = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

with open(plugin_path) as f:
    plugin = json.load(f)
name = plugin.get("name")
if not name or not kebab.match(name):
    fail(f"plugin.json name must be kebab-case, got {name!r}")

with open(market_path) as f:
    market = json.load(f)
mname = market.get("name")
if not mname or not kebab.match(mname):
    fail(f"marketplace.json name must be kebab-case, got {mname!r}")
if not isinstance(market.get("owner"), dict) or not market["owner"].get("name"):
    fail("marketplace.json owner.name is required")
plugins = market.get("plugins")
if not isinstance(plugins, list) or not plugins:
    fail("marketplace.json plugins[] is required")
    plugins = []
entry = next((p for p in plugins if p.get("name") == name), None)
if entry is None:
    fail(f"marketplace.json does not list plugin {name!r}")
else:
    if entry.get("source") != "./plugins/doc-tools":
        fail(f"marketplace entry source should be ./plugins/doc-tools, got {entry.get('source')!r}")
    if entry.get("version") != plugin.get("version"):
        fail(f"version mismatch: plugin.json {plugin.get('version')!r} vs marketplace {entry.get('version')!r}")
for p in plugins:
    if not p.get("name") or not p.get("source"):
        fail(f"marketplace plugin entry missing name or source: {p}")
if ok:
    print(f"   plugin.json ({name} {plugin.get('version')}) and marketplace.json ({mname}) look valid")
sys.exit(0 if ok else 1)
PY

if [[ $status -eq 0 ]]; then echo "all layout checks passed"; fi
exit $status
