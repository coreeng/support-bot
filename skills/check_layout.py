#!/usr/bin/env python3
"""Structural checks on the doc-tools skills (skills/doc-journeys, skills/doc-run).

The skills install standalone (gh skill install / npx skills add) and land as sibling directories
in the consumer's skills directory, so every path is written relative to a skill directory:

  ${CLAUDE_SKILL_DIR}/...              inside a SKILL.md or reference file: that skill's dir
  ${CLAUDE_SKILL_DIR}/../doc-journeys  from doc-run: its sibling
  <tools root>/...                     in agent prompt files: the directory holding both skills,
                                       pinned by doc-run in every spawn prompt

Checks:
  1. every such path resolves to a file or directory under skills/; ${CLAUDE_PLUGIN_ROOT} is
     not used (it exists only inside Claude Code plugins)
  2. every agent doc-run spawns has a prompt file under skills/doc-run/agents/, and vice versa;
     no plugin-style agent names (doc-tools:doc-*) remain
  3. both skills carry frontmatter name (matching the directory, as gh skill and the Agent
     Skills spec require), description (<= 1024 chars) and license
  4. nothing machine-specific: no absolute home paths or ~/ paths in the skill tree (the
     consumer's .doc-settings/ owns every real path)

Standard library only. Exits 1 on any failure.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

SKILLS_ROOT = Path(__file__).resolve().parent
SKILL_NAMES = ("doc-journeys", "doc-run")
AGENTS_DIR = SKILLS_ROOT / "doc-run" / "agents"
DOC_RUN_SKILL = SKILLS_ROOT / "doc-run" / "SKILL.md"
README = SKILLS_ROOT / "README.md"

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
FRONTMATTER = re.compile(r"^---\n(.*?)\n---\n", re.S)
MAX_DESCRIPTION = 1024

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)
    print(f"FAIL: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(SKILLS_ROOT))


def skill_files() -> list[Path]:
    """Every regular file under the two skill directories, plus the top-level README."""
    files = [p for name in SKILL_NAMES for p in (SKILLS_ROOT / name).rglob("*") if p.is_file()]
    return sorted(files) + [README]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def skill_of(path: Path) -> str | None:
    parts = path.relative_to(SKILLS_ROOT).parts
    return parts[0] if len(parts) > 1 and parts[0] in SKILL_NAMES else None


def check_paths_resolve() -> None:
    print("== 1. skill-relative paths resolve")
    seen: set[tuple[str, str]] = set()
    for path in skill_files():
        text = read(path)
        skill = skill_of(path)
        for match in PATH_REF.finditer(text):
            ref = match.group(0)
            if (rel(path), ref) in seen:
                continue
            seen.add((rel(path), ref))
            if ref.startswith(SKILL_DIR_VAR):
                if skill is None:
                    continue  # the top-level README documents the convention
                target = SKILLS_ROOT / skill / ref[len(SKILL_DIR_VAR):].lstrip("/")
            else:
                target = SKILLS_ROOT / ref[len(TOOLS_ROOT_TOKEN):].lstrip("/")
            if not target.exists():
                fail(f"{rel(path)} references {ref}, which does not resolve ({target})")
        if "CLAUDE_PLUGIN_ROOT" in text:
            fail(f"{rel(path)} uses ${{CLAUDE_PLUGIN_ROOT}}, which exists only in Claude Code "
                 f"plugins; use {SKILL_DIR_VAR}")
    print(f"   {len(seen)} path references checked")


def check_agent_prompts() -> None:
    print("== 2. agent prompt files")
    for path in skill_files():
        if path.suffix != ".md":
            continue
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
            fail(f"doc-run/agents/{agent}.md exists but doc-run/SKILL.md never spawns `{agent}`")
        text = read(prompt)
        first_line = text.splitlines()[0] if text else ""
        if not first_line.startswith(f"# {agent} "):
            fail(f"doc-run/agents/{agent}.md does not open with a '# {agent}' header")
    for agent in sorted(spawned):
        if not (AGENTS_DIR / f"{agent}.md").is_file():
            fail(f"doc-run spawns `{agent}` but doc-run/agents/{agent}.md does not exist")
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


def check_frontmatter() -> None:
    print("== 3. skill frontmatter")
    for skill in SKILL_NAMES:
        skill_md = SKILLS_ROOT / skill / "SKILL.md"
        if not skill_md.is_file():
            fail(f"{skill}/SKILL.md missing")
            continue
        fields = parse_frontmatter(read(skill_md))
        if fields is None:
            fail(f"{skill}/SKILL.md has no frontmatter")
            continue
        if fields.get("name") != skill:
            fail(f"{skill}: frontmatter name {fields.get('name')!r} must equal the directory name")
        description = fields.get("description", "")
        if not description:
            fail(f"{skill}: description missing")
        elif len(description) > MAX_DESCRIPTION:
            fail(f"{skill}: description is {len(description)} chars (max {MAX_DESCRIPTION})")
        if not fields.get("license"):
            fail(f"{skill}: license missing")
    print(f"   {len(SKILL_NAMES)} skills checked")


def check_no_machine_paths() -> None:
    print("== 4. no machine-specific paths")
    hits = 0
    for path in skill_files():
        for line_no, line in enumerate(read(path).splitlines(), 1):
            if MACHINE_PATH.search(line):
                hits += 1
                fail(f"{rel(path)}:{line_no}: absolute or home-relative path: {line.strip()[:120]}")
    if not hits:
        print("   none found")


def main() -> int:
    check_paths_resolve()
    check_agent_prompts()
    check_frontmatter()
    check_no_machine_paths()
    if failures:
        print(f"\n{len(failures)} failure(s)")
        return 1
    print("all layout checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
