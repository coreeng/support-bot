# skills/

Project-level [agentskills.io](https://agentskills.io/specification) skills shared across all coding agents that work in this repo. The contents are deliberately **LLM-agnostic** — every `SKILL.md` uses tool-agnostic verbs and the open spec, so it can be loaded by Claude Code, Copilot CLI, Gemini CLI, Codex, or any other runtime that supports the spec.

This sits alongside the repo's `AGENTS.md` / `CLAUDE.md` shim convention: a single source of truth, plus per-platform adapters as needed.

## What's here

| Skill | Purpose |
|-------|---------|
| [`diataxis-scoring/`](./diataxis-scoring/SKILL.md) | Score a doc (or directory of docs) along two axes: [Diataxis](https://diataxis.fr/) mode-purity (tutorial / how-to / reference / explanation) and, optionally, journey-fit for a named product user-journey. Outputs 0–100 scores and points to the lines/steps dragging the result. |

## Wiring per platform

Each agent runtime discovers skills slightly differently. The safe, portable approach is a **symlink** from the runtime's expected skills directory into the path under this repo. That way the canonical content stays in one place and every runtime sees the same SKILL.md.

### Claude Code

Symlink into the project-local skills directory the harness scans by default:

```bash
mkdir -p .claude/skills
ln -s ../../skills/diataxis-scoring .claude/skills/diataxis-scoring
```

Then verify the skill appears in Claude Code's available-skills list and triggers on phrases like "score this doc against Diataxis".

### Copilot CLI

Copilot CLI discovers skills via its `skill` tool path. Symlink under your Copilot skills directory (consult `gh copilot help` for the current location), e.g.:

```bash
ln -s "$(pwd)/skills/diataxis-scoring" ~/.copilot/skills/diataxis-scoring
```

### Gemini CLI

Gemini activates skills on demand via `activate_skill`. Declare the skill path in your repo-local `GEMINI.md`, or symlink under your Gemini skills directory.

### Codex

Codex reads skills from `~/.agents/skills/`. Symlink there:

```bash
ln -s "$(pwd)/skills/diataxis-scoring" ~/.agents/skills/diataxis-scoring
```

### Anything else

Any runtime that supports the [agentskills.io spec](https://agentskills.io/specification) can use these skills directly — point its skill-search path at this `skills/` directory.

## Authoring conventions for new skills

If you add a skill to this directory, keep it portable:

- YAML frontmatter limited to spec-required fields (`name`, `description`); ≤1024 chars total.
- `name` uses `[a-z0-9-]` only.
- `description` states **triggering conditions only** — never a workflow summary, never first-person, never platform-specific.
- Body uses tool-agnostic verbs ("load the file", "scan the content") — never "use the Read tool" or "call Grep".
- If a skill *must* reference a platform-specific tool, isolate that in a `references/platform-tools.md` so the main `SKILL.md` stays neutral.
- One excellent example beats many mediocre ones — pick a representative case from this repo's docs.
- Keep `SKILL.md` ≤500 words; offload depth into `references/*.md` (loaded only when the skill is active).
