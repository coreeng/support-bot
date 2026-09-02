# doc-tools

Two skills and the review agents they spawn:

  * **`doc-journeys`** — consolidates documentation scattered across many repositories into a
    coherent, Diátaxis-typed set of pages in a documentation site. A reorganiser, not an author:
    every claim traces to prose that already existed. [`skills/doc-journeys/README.md`](skills/doc-journeys/README.md)
  * **`doc-run`** — wraps `doc-journeys` in an unattended, reviewed run: plan, gated build,
    structural gate, parallel adversarial review, verification of findings, an automatic fix loop
    and a close-out that hands back a branch to merge. [`skills/doc-run/README.md`](skills/doc-run/README.md)
  * **agents** — `doc-builder`, `doc-structure-reviewer`, `doc-gap-auditor`,
    `doc-entity-verifier`, `doc-routing-reviewer`, `doc-finding-verifier`, shipped as prompt files
    under `skills/doc-run/agents/` and run as `general-purpose` subagents. Nothing beyond the two
    skills has to be installed.

The pipeline is **consumer-agnostic**. It does not know where your site is, which repositories
are sources, how the site builds or what an unattended run may do. All of that lives in the
repository being documented — the *consumer* — under `.doc-settings/`, beside the
`product-definition/` it already owns. `skills/doc-journeys/assets/doc-settings/` is a
documented starter.

## Installing

The same tree installs two ways. Both skills follow the [Agent Skills](https://agentskills.io)
standard, and `doc-run` needs `doc-journeys` installed beside it.

**As a Claude Code plugin** — skills are namespaced `/doc-tools:doc-journeys` and
`/doc-tools:doc-run`. From a local checkout:

```bash
claude --plugin-dir /path/to/support-bot/plugins/doc-tools
```

or from the `coreeng` marketplace, in the consumer repository's `.claude/settings.json`:

```json
{
  "extraKnownMarketplaces": {
    "coreeng": {
      "source": { "source": "github", "repo": "coreeng/support-bot" }
    }
  },
  "enabledPlugins": {
    "doc-tools@coreeng": true
  }
}
```

**As plain skills** — vendored into the consumer's `.claude/skills/` with the GitHub CLI, invoked
as `/doc-journeys` and `/doc-run`:

```bash
gh skill install coreeng/support-bot doc-journeys --agent claude-code
gh skill install coreeng/support-bot doc-run --agent claude-code
gh skill update --all           # later
```

`gh skill` discovers the `plugins/*/skills/*/SKILL.md` layout directly. Pin a release with
`--pin <tag>`; releases are cut with `gh skill publish --tag doc-tools/vX.Y.Z` (prefixed, because
plain `v*` tags belong to the application pipeline in this repository).

## Setting up a consumer

1. Copy `skills/doc-journeys/assets/doc-settings/` to `<consumer root>/.doc-settings/` and edit
   every value marked `EDIT` — the starter's README lists what each file is for.
2. Create `product-definition/` per `skills/doc-journeys/references/product-definition.md`.
3. Check the prerequisites: `worktree_dir` gitignored, `base_branch` exists, the site's dependency
   directory present at the consumer root, `build_command` green from a worktree.
4. Run `/doc-journeys plan mode for <product>` (or the `/doc-tools:` form) before anything writes.

## Layout

```
plugins/doc-tools/
  .claude-plugin/plugin.json
  skills/doc-journeys/
    SKILL.md  README.md  references/*.md
    assets/doc-settings/           starter .doc-settings/ for a new consumer
  skills/doc-run/
    SKILL.md  README.md
    agents/doc-*.md                prompt files for the six general-purpose agents
  scripts/check-agnostic.sh        denylist: nothing consumer-specific may enter the plugin
  scripts/check-layout.sh          paths resolve, agent prompts match, manifests and frontmatter valid
```

Paths are written relative to a skill directory so they work in both layouts: inside a skill
`${CLAUDE_SKILL_DIR}/references/<file>.md`, from doc-run `${CLAUDE_SKILL_DIR}/../doc-journeys/…`,
and in agent prompt files `<tools root>/doc-journeys/…`, where the *tools root* is the directory
holding both skills and doc-run pins it into every spawn prompt. Claude Code substitutes
`${CLAUDE_SKILL_DIR}` when a skill loads, in plugin and standalone installs alike.

## Checks

Both scripts run in CI (`.github/workflows/doc-tools-plugin.yaml`) on any change under
`plugins/doc-tools/` or `.claude-plugin/`, together with `gh skill publish --dry-run`, and
locally from anywhere:

```bash
plugins/doc-tools/scripts/check-agnostic.sh
plugins/doc-tools/scripts/check-layout.sh
claude plugin validate plugins/doc-tools --strict
gh skill publish --dry-run .
```

Worked examples in the skills use **Foglight**, a fictional observability product
(`skills/doc-journeys/references/examples.md`). If you find a real repository, site or team
named anywhere in the plugin, it belongs in a consumer's `.doc-settings/` — the denylist check
exists to keep it there.
