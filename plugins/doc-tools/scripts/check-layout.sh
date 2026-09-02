#!/usr/bin/env bash
# check-layout.sh — structural checks on the doc-tools plugin.
#
# The plugin is dual-mode: it loads as a Claude Code plugin AND its two skills install standalone
# (gh skill install / npx skills). Both layouts keep doc-journeys and doc-run as sibling
# directories, so every path is written relative to a skill directory:
#
#   ${CLAUDE_SKILL_DIR}/...              inside a SKILL.md or reference file — resolves to that skill's dir
#   ${CLAUDE_SKILL_DIR}/../doc-journeys  from doc-run — its sibling
#   <tools root>/...                     in agent prompt files — the directory holding both skills,
#                                        pinned by doc-run in every spawn prompt
#
# Checks:
#   1. every such path resolves to a file or directory in the plugin
#   2. every agent doc-run spawns has a prompt file under skills/doc-run/agents/, and vice versa;
#      no plugin-style agent names (doc-tools:doc-*) remain
#   3. plugin.json and the root marketplace.json parse as JSON and carry the required fields;
#      both skills carry name (matching the directory), description (<= 1024 chars) and license
#
# Uses grep, find and python3 only (no rg). Exits 1 on any failure.
set -euo pipefail

plugin_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
repo_root=$(cd "$plugin_root/../.." && pwd)
skills_root="$plugin_root/skills"
status=0

echo "== 1. skill-relative paths resolve"
count=0
while IFS=: read -r file match; do
  [[ -z "$file" ]] && continue
  rel=${file#$plugin_root/}
  skill=$(printf '%s' "$rel" | sed -nE 's#^skills/([^/]+)/.*#\1#p')
  case "$match" in
    '${CLAUDE_SKILL_DIR}'*)
      if [[ -z "$skill" ]]; then
        [[ "$rel" == "README.md" ]] && continue   # the plugin README documents the convention
        echo "FAIL: $rel uses \${CLAUDE_SKILL_DIR} but is not inside a skill"; status=1; continue
      fi
      target="$skills_root/$skill/${match#\$\{CLAUDE_SKILL_DIR\}/}" ;;
    '<tools root>')
      target="$skills_root" ;;
    '<tools root>'*)
      target="$skills_root/${match#<tools root>/}" ;;
  esac
  target=${target%/}
  count=$((count + 1))
  if [[ ! -e "$target" ]]; then
    echo "FAIL: $rel references $match, which does not resolve ($target)"
    status=1
  fi
done < <(grep -rHoIE '(\$\{CLAUDE_SKILL_DIR\}|<tools root>)[A-Za-z0-9_./-]*' "$plugin_root" --exclude-dir=scripts | sort -u)
echo "   $count path references checked"

if grep -rqI 'CLAUDE_PLUGIN_ROOT' "$plugin_root" --exclude-dir=scripts; then
  echo "FAIL: \${CLAUDE_PLUGIN_ROOT} is undefined for standalone skills — use \${CLAUDE_SKILL_DIR}"
  grep -rnI 'CLAUDE_PLUGIN_ROOT' "$plugin_root" --exclude-dir=scripts | sed "s#^$plugin_root/#  #"
  status=1
fi

echo "== 2. agent prompt files"
agents_dir="$skills_root/doc-run/agents"
run_skill="$skills_root/doc-run/SKILL.md"
if grep -rqE 'doc-tools:doc-(builder|structure-reviewer|gap-auditor|entity-verifier|routing-reviewer|finding-verifier)' "$skills_root"; then
  echo "FAIL: plugin-style agent names remain (agents are prompt files run as general-purpose):"
  grep -rnE 'doc-tools:doc-(builder|structure-reviewer|gap-auditor|entity-verifier|routing-reviewer|finding-verifier)' "$skills_root" | sed "s#^$skills_root/#  #"
  status=1
fi
n=0
for f in "$agents_dir"/doc-*.md; do
  agent=$(basename "$f" .md)
  n=$((n + 1))
  grep -qF "\`$agent\`" "$run_skill" \
    || { echo "FAIL: agents/$agent.md exists but doc-run/SKILL.md never spawns \`$agent\`"; status=1; }
  head -1 "$f" | grep -qE "^# $agent " \
    || { echo "FAIL: agents/$agent.md does not open with a '# $agent' header"; status=1; }
done
while IFS= read -r name; do
  [[ -f "$agents_dir/$name.md" ]] \
    || { echo "FAIL: doc-run spawns \`$name\` but agents/$name.md does not exist"; status=1; }
done < <(grep -oE '`doc-(builder|structure-reviewer|gap-auditor|entity-verifier|routing-reviewer|finding-verifier)`' "$run_skill" | tr -d '`' | sort -u)
echo "   $n prompt files checked"

echo "== 3. manifests and skill frontmatter"
python3 - "$plugin_root" "$repo_root/.claude-plugin/marketplace.json" <<'PY' || status=1
import json, os, re, sys
plugin_root, market_path = sys.argv[1], sys.argv[2]
ok = True
def fail(msg):
    global ok
    ok = False
    print(f"FAIL: {msg}")
kebab = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

with open(os.path.join(plugin_root, ".claude-plugin", "plugin.json")) as f:
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
plugins = market.get("plugins") or []
if not plugins:
    fail("marketplace.json plugins[] is required")
entry = next((p for p in plugins if p.get("name") == name), None)
if entry is None:
    fail(f"marketplace.json does not list plugin {name!r}")
else:
    if entry.get("source") != "./plugins/doc-tools":
        fail(f"marketplace entry source should be ./plugins/doc-tools, got {entry.get('source')!r}")
    if entry.get("version") != plugin.get("version"):
        fail(f"version mismatch: plugin.json {plugin.get('version')!r} vs marketplace {entry.get('version')!r}")

# Agent Skills spec: name == directory, description <= 1024 chars; license recommended by gh skill publish
skills_root = os.path.join(plugin_root, "skills")
for skill in sorted(os.listdir(skills_root)):
    path = os.path.join(skills_root, skill, "SKILL.md")
    if not os.path.isfile(path):
        fail(f"skills/{skill}/SKILL.md missing"); continue
    text = open(path, encoding="utf-8").read()
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        fail(f"skills/{skill}/SKILL.md has no frontmatter"); continue
    fm = {}
    for line in m.group(1).splitlines():
        if ":" in line and not line.startswith((" ", "\t")):
            k, v = line.split(":", 1)
            fm[k.strip()] = v.strip()
    if fm.get("name") != skill:
        fail(f"skills/{skill}: frontmatter name {fm.get('name')!r} must equal the directory name")
    desc = fm.get("description", "")
    if not desc:
        fail(f"skills/{skill}: description missing")
    elif len(desc) > 1024:
        fail(f"skills/{skill}: description is {len(desc)} chars (max 1024)")
    if not fm.get("license"):
        fail(f"skills/{skill}: license missing")
if ok:
    print(f"   plugin.json ({name} {plugin.get('version')}), marketplace.json ({mname}) and skill frontmatter look valid")
sys.exit(0 if ok else 1)
PY

if [[ $status -eq 0 ]]; then echo "all layout checks passed"; fi
exit $status
