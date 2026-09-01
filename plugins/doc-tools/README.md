# doc-tools

A Claude Code plugin carrying two skills and the six agents they spawn:

  * **`doc-journeys`** — consolidates documentation scattered across many repositories into a
    coherent, Diátaxis-typed set of pages in a documentation site. A reorganiser, not an author:
    every claim traces to prose that already existed. [`skills/doc-journeys/README.md`](skills/doc-journeys/README.md)
  * **`doc-run`** — wraps `doc-journeys` in an unattended, reviewed run: plan, gated build,
    structural gate, parallel adversarial review, verification of findings, an automatic fix loop
    and a close-out that hands back a branch to merge. [`skills/doc-run/README.md`](skills/doc-run/README.md)
  * **agents** — `doc-builder`, `doc-structure-reviewer`, `doc-gap-auditor`,
    `doc-entity-verifier`, `doc-routing-reviewer`, `doc-finding-verifier`.

The plugin is **consumer-agnostic**. It does not know where your site is, which repositories are
sources, how the site builds or what an unattended run may do. All of that lives in the
repository being documented — the *consumer* — under `.doc-settings/`, beside the
`product-definition/` it already owns. `templates/doc-settings/` is a documented starter.

## Installing

Everything is namespaced by the plugin name: skills are `/doc-tools:doc-journeys` and
`/doc-tools:doc-run`; agents are spawned as `doc-tools:doc-builder` and so on.

**From a local checkout** (development, or before the plugin is published):

```bash
claude --plugin-dir /path/to/support-bot/plugins/doc-tools
```

**From the `coreeng` marketplace** — in the consumer repository's `.claude/settings.json`, so
that everyone who opens it gets the plugin:

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

The marketplace manifest is [`.claude-plugin/marketplace.json`](../../.claude-plugin/marketplace.json)
at the root of this repository.

## Setting up a consumer

1. Copy `templates/doc-settings/` to `<consumer root>/.doc-settings/` and edit every value marked
   `EDIT` — the template's README lists what each file is for.
2. Create `product-definition/` per `skills/doc-journeys/references/product-definition.md`.
3. Check the prerequisites: `worktree_dir` gitignored, `base_branch` exists, the site's dependency
   directory present at the consumer root, `build_command` green from a worktree.
4. Run `/doc-tools:doc-journeys plan mode for <product>` before anything writes.

## Layout

```
plugins/doc-tools/
  .claude-plugin/plugin.json
  skills/doc-journeys/{SKILL.md,README.md,references/*.md}
  skills/doc-run/{SKILL.md,README.md}
  agents/doc-*.md
  templates/doc-settings/        starter .doc-settings/ for a new consumer
  scripts/check-agnostic.sh      denylist: nothing consumer-specific may enter the plugin
  scripts/check-layout.sh        plugin-root paths resolve, agent names match, manifests valid
```

Reference files are addressed as `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/<file>.md`;
Claude Code substitutes the variable when a plugin skill loads, and `doc-run` pins the resolved
value into every agent's spawn prompt as the *plugin root*.

## Checks

Both scripts run in CI (`.github/workflows/doc-tools-plugin.yaml`) on any change under
`plugins/doc-tools/` or `.claude-plugin/`, and locally from anywhere:

```bash
plugins/doc-tools/scripts/check-agnostic.sh
plugins/doc-tools/scripts/check-layout.sh
claude plugin validate plugins/doc-tools --strict
```

Worked examples in the skills use **Foglight**, a fictional observability product
(`skills/doc-journeys/references/examples.md`). If you find a real repository, site or team
named anywhere in the plugin, it belongs in a consumer's `.doc-settings/` — the denylist check
exists to keep it there.
