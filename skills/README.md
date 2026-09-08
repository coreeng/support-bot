# doc-tools skills

Two Claude Code skills and the review agents they spawn:

  * **`doc-journeys`** — consolidates documentation scattered across many repositories into a
    coherent, Diátaxis-typed set of pages in a documentation site. A reorganiser, not an author:
    every claim traces to prose that already existed. [`doc-journeys/README.md`](doc-journeys/README.md)
  * **`doc-run`** — wraps `doc-journeys` in an unattended, reviewed run: plan, gated build,
    structural gate, parallel adversarial review, verification of findings, an automatic fix loop
    and a close-out that hands back a branch to merge. [`doc-run/README.md`](doc-run/README.md)
  * **agents** — `doc-builder`, `doc-structure-reviewer`, `doc-gap-auditor`,
    `doc-entity-verifier`, `doc-routing-reviewer`, `doc-finding-verifier`, shipped as prompt files
    under `doc-run/agents/` and run as `general-purpose` subagents. Nothing beyond the two skills
    has to be installed.

The pipeline is **consumer-agnostic**. It does not know where your site is, which repositories
are sources, how the site builds or what an unattended run may do. All of that lives in the
repository being documented — the *consumer* — under `.doc-settings/`, beside the
`product-definition/` it already owns. `doc-journeys/assets/doc-settings/` is a documented
starter.

## Installing

Both skills follow the [Agent Skills](https://agentskills.io) standard and live under `skills/`,
the layout every installer discovers. **Install both**: `doc-run` needs `doc-journeys` beside it
and stops if it is missing.

With the GitHub CLI, vendored into the consumer's `.claude/skills/`:

```bash
gh skill install coreeng/support-bot doc-journeys --agent claude-code
gh skill install coreeng/support-bot doc-run --agent claude-code
gh skill update --all           # later
```

Pin a release with `--pin <tag>`; releases are cut with `gh skill publish --tag doc-tools/vX.Y.Z`
(prefixed, because plain `v*` tags belong to the application pipeline in this repository).

With the `skills` CLI, which also targets other agents and keeps a `skills-lock.json`:

```bash
npx skills add coreeng/support-bot --skill doc-journeys --skill doc-run -a claude-code -y
```

The skills are written for Claude Code — doc-run drives its Agent, SendMessage and Skill tools,
and `${CLAUDE_SKILL_DIR}` is a Claude Code substitution. Other agents receive the same files but
will not run the pipeline.

## Setting up a consumer

1. Copy `doc-journeys/assets/doc-settings/` to `<consumer root>/.doc-settings/` and edit every
   value marked `EDIT` — the starter's README lists what each file is for.
2. Create `product-definition/` per `doc-journeys/references/product-definition.md`.
3. Work through the **Before your first run** checklist in
   [`doc-journeys/assets/doc-settings/README.md`](doc-journeys/assets/doc-settings/README.md#before-your-first-run)
   — required keys, gitignored worktree directory, build dependencies at the consumer root,
   toolchain shim, and the rest.
4. Run `/doc-journeys plan mode for <product>` before anything writes.

## Layout

```
skills/
  doc-journeys/
    SKILL.md  README.md  references/*.md
    assets/doc-settings/           starter .doc-settings/ for a new consumer
  doc-run/
    SKILL.md  README.md
    agents/doc-*.md                prompt files for the six general-purpose agents
  check_layout.py                  paths resolve, agent prompts match, frontmatter valid, no machine-specific paths
```

Paths are written relative to a skill directory so they survive installation anywhere: inside a
skill `${CLAUDE_SKILL_DIR}/references/<file>.md`, from doc-run `${CLAUDE_SKILL_DIR}/../doc-journeys/…`,
and in agent prompt files `<tools root>/doc-journeys/…`, where the *tools root* is the directory
holding both skills and doc-run pins it into every spawn prompt.

## Checks

`check_layout.py` runs in CI (`.github/workflows/doc-tools-skills.yaml`) on any change under
`skills/`, together with `gh skill publish --dry-run`, and locally from anywhere:

```bash
python3 skills/check_layout.py
gh skill publish --dry-run .
npx skills add . -l            # what the skills CLI would offer consumers
```

Worked examples in the skills use **Foglight**, a fictional observability product
(`doc-journeys/references/examples.md`). If you find a real repository, site or team named
anywhere under `skills/`, it belongs in a consumer's `.doc-settings/`; keep the skills free of it.
