#!/usr/bin/env python3
"""Structural checks on the doc-tools plugin.

The plugin is dual-mode: it loads as a Claude Code plugin AND its two skills install standalone
(gh skill install / npx skills). Both layouts keep doc-journeys and doc-run as sibling
directories, so every path is written relative to a skill directory:

  ${CLAUDE_SKILL_DIR}/...              inside a SKILL.md or reference file: that skill's dir
  ${CLAUDE_SKILL_DIR}/../doc-journeys  from doc-run: its sibling
  <tools root>/...                     in agent prompt files: the directory holding both skills,
                                       pinned by doc-run in every spawn prompt

Checks:
  1. every such path resolves to a file or directory in the plugin; ${CLAUDE_PLUGIN_ROOT} is
     not used (it is undefined for standalone skills)
  2. every agent doc-run spawns has a prompt file under skills/doc-run/agents/, and vice versa;
     no plugin-style agent names (doc-tools:doc-*) remain
  3. plugin.json and the root marketplace.json parse and carry the required fields; both skills
     carry name (matching the directory), description (<= 1024 chars) and license
  4. nothing machine-specific: no absolute home paths or ~/ paths in the skill tree (the
     consumer's .doc-settings/ owns every real path)

Standard library only. Exits 1 on any failure.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

PLUGIN_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = PLUGIN_ROOT.parent.parent
SKILLS_ROOT = PLUGIN_ROOT / "skills"
AGENTS_DIR = SKILLS_ROOT / "doc-run" / "agents"
DOC_RUN_SKILL = SKILLS_ROOT / "doc-run" / "SKILL.md"
MARKETPLACE = REPO_ROOT / ".claude-plugin" / "marketplace.json"
PLUGIN_MANIFEST = PLUGIN_ROOT / ".claude-plugin" / "plugin.json"

AGENT_NAMES = [
    f"doc-{a}"
    for a in ("builder", "structure-reviewer", "gap-auditor", "entity-verifier",
              "routing-reviewer", "finding-verifier")
]
AGENT_ALTERNATION = "|".join(a.removeprefix("doc-") for a in AGENT_NAMES)

SKILL_DIR_VAR = "${CLAUDE_SKILL_DIR}"
TOOLS_ROOT_TOKEN = "<tools root>"
PATH_REF = re.compile(r"(\$\{CLAUDE_SKILL_DIR\}|<tools root>)[A-Za-z0-9_./-]*")
PLUGIN_AGENT_NAME = re.compile(rf"doc-tools:doc-({AGENT_ALTERNATION})")
SPAWNED_AGENT = re.compile(rf"`doc-({AGENT_ALTERNATION})`")
MACHINE_PATH = re.compile(r"(/Users/[A-Za-z]|/home/[a-z]|(^|[ `\"(])~/[A-Za-z.])")
KEBAB = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
FRONTMATTER = re.compile(r"^---\n(.*?)\n---\n", re.S)
MAX_DESCRIPTION = 1024

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)
    print(f"FAIL: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PLUGIN_ROOT))


def plugin_files() -> list[Path]:
    """Every regular file in the plugin except this scripts directory."""
    return sorted(
        p for p in PLUGIN_ROOT.rglob("*")
        if p.is_file() and "scripts" not in p.relative_to(PLUGIN_ROOT).parts
    )


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def load_json(path: Path) -> dict:
    try:
        return json.loads(read(path))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"{path.relative_to(REPO_ROOT)}: cannot load ({exc})")
        return {}


def skill_of(path: Path) -> str | None:
    parts = path.relative_to(PLUGIN_ROOT).parts
    return parts[1] if len(parts) > 2 and parts[0] == "skills" else None


def check_paths_resolve() -> None:
    print("== 1. skill-relative paths resolve")
    seen: set[tuple[str, str]] = set()
    for path in plugin_files():
        text = read(path)
        skill = skill_of(path)
        for match in PATH_REF.finditer(text):
            ref = match.group(0)
            if (rel(path), ref) in seen:
                continue
            seen.add((rel(path), ref))
            if ref.startswith(SKILL_DIR_VAR):
                if skill is None:
                    if rel(path) == "README.md":
                        continue  # the plugin README documents the convention
                    fail(f"{rel(path)} uses {SKILL_DIR_VAR} but is not inside a skill")
                    continue
                target = SKILLS_ROOT / skill / ref[len(SKILL_DIR_VAR):].lstrip("/")
            else:
                target = SKILLS_ROOT / ref[len(TOOLS_ROOT_TOKEN):].lstrip("/")
            if not target.exists():
                fail(f"{rel(path)} references {ref}, which does not resolve ({target})")
        if "CLAUDE_PLUGIN_ROOT" in text:
            fail(f"{rel(path)} uses ${{CLAUDE_PLUGIN_ROOT}}, undefined for standalone skills; "
                 f"use {SKILL_DIR_VAR}")
    print(f"   {len(seen)} path references checked")


def check_agent_prompts() -> None:
    print("== 2. agent prompt files")
    for path in sorted(SKILLS_ROOT.rglob("*.md")):
        for line_no, line in enumerate(read(path).splitlines(), 1):
            if PLUGIN_AGENT_NAME.search(line):
                fail(f"{rel(path)}:{line_no}: plugin-style agent name remains "
                     f"(agents are prompt files run as general-purpose)")
    doc_run = read(DOC_RUN_SKILL)
    spawned = {f"doc-{m.group(1)}" for m in SPAWNED_AGENT.finditer(doc_run)}
    prompt_files = sorted(AGENTS_DIR.glob("doc-*.md"))
    for prompt in prompt_files:
        agent = prompt.stem
        if f"`{agent}`" not in doc_run:
            fail(f"agents/{agent}.md exists but doc-run/SKILL.md never spawns `{agent}`")
        first_line = read(prompt).splitlines()[0] if read(prompt) else ""
        if not first_line.startswith(f"# {agent} "):
            fail(f"agents/{agent}.md does not open with a '# {agent}' header")
    for agent in sorted(spawned):
        if not (AGENTS_DIR / f"{agent}.md").is_file():
            fail(f"doc-run spawns `{agent}` but agents/{agent}.md does not exist")
    print(f"   {len(prompt_files)} prompt files checked")


def parse_frontmatter(text: str) -> dict[str, str] | None:
    match = FRONTMATTER.match(text)
    if not match:
        return None
    fields: dict[str, str] = {}
    for line in match.group(1).splitlines():
        if ":" in line and not line.startswith((" ", "\t")):
            key, value = line.split(":", 1)
            fields[key.strip()] = value.strip()
    return fields


def check_manifests_and_frontmatter() -> None:
    print("== 3. manifests and skill frontmatter")
    plugin = load_json(PLUGIN_MANIFEST)
    name = plugin.get("name")
    if not name or not KEBAB.match(name):
        fail(f"plugin.json name must be kebab-case, got {name!r}")

    market = load_json(MARKETPLACE)
    market_name = market.get("name")
    if not market_name or not KEBAB.match(market_name):
        fail(f"marketplace.json name must be kebab-case, got {market_name!r}")
    owner = market.get("owner")
    if not isinstance(owner, dict) or not owner.get("name"):
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
            fail(f"version mismatch: plugin.json {plugin.get('version')!r} "
                 f"vs marketplace {entry.get('version')!r}")

    # Agent Skills spec: name == directory, description <= 1024 chars; license recommended
    for skill_dir in sorted(p for p in SKILLS_ROOT.iterdir() if p.is_dir()):
        skill = skill_dir.name
        skill_md = skill_dir / "SKILL.md"
        if not skill_md.is_file():
            fail(f"skills/{skill}/SKILL.md missing")
            continue
        fields = parse_frontmatter(read(skill_md))
        if fields is None:
            fail(f"skills/{skill}/SKILL.md has no frontmatter")
            continue
        if fields.get("name") != skill:
            fail(f"skills/{skill}: frontmatter name {fields.get('name')!r} must equal the directory name")
        description = fields.get("description", "")
        if not description:
            fail(f"skills/{skill}: description missing")
        elif len(description) > MAX_DESCRIPTION:
            fail(f"skills/{skill}: description is {len(description)} chars (max {MAX_DESCRIPTION})")
        if not fields.get("license"):
            fail(f"skills/{skill}: license missing")
    print(f"   plugin.json ({name} {plugin.get('version')}), marketplace.json ({market_name}) "
          f"and skill frontmatter checked")


def check_no_machine_paths() -> None:
    print("== 4. no machine-specific paths")
    targets = sorted(SKILLS_ROOT.rglob("*")) + [PLUGIN_ROOT / "README.md"]
    hits = 0
    for path in targets:
        if not path.is_file():
            continue
        for line_no, line in enumerate(read(path).splitlines(), 1):
            if MACHINE_PATH.search(line):
                hits += 1
                fail(f"{rel(path)}:{line_no}: absolute or home-relative path: {line.strip()[:120]}")
    if not hits:
        print("   none found")


def main() -> int:
    check_paths_resolve()
    check_agent_prompts()
    check_manifests_and_frontmatter()
    check_no_machine_paths()
    if failures:
        print(f"\n{len(failures)} failure(s)")
        return 1
    print("all layout checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
