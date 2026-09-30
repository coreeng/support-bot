# doc-tools skills

Two agent skills and the review agents they spawn. `doc-journeys` runs in any agent that follows
a `SKILL.md`; `doc-run` also needs the agent to run subagents (see [Installing](#installing)). New here? Read [From zero to a merged run](#from-zero-to-a-merged-run)
first.

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

With the GitHub CLI, vendored into the consumer repository and committed there, so a clone has
the skills without installing anything. Two locations, depending on which agents the consumer
runs:

```bash
# Agent-neutral: .agents/skills/, the directory Copilot, Codex, Cursor, Gemini CLI, Amp,
# OpenCode and others share. Recommended when the agent is not fixed (a CI/CD pipeline, say).
gh skill install coreeng/support-bot doc-journeys --agent universal --scope project
gh skill install coreeng/support-bot doc-run      --agent universal --scope project
ln -s ../.agents/skills .claude/skills            # Claude Code reads only .claude/skills/

# Claude Code only: straight into .claude/skills/
gh skill install coreeng/support-bot doc-journeys --agent claude-code --scope project
gh skill install coreeng/support-bot doc-run      --agent claude-code --scope project

# Any other agent gh supports (see `gh skill install --help`), e.g. Cursor. At project scope
# most land in .agents/skills/; use --dir <path> for an agent-specific directory instead.
gh skill install coreeng/support-bot doc-journeys --agent <agent> --scope project
gh skill install coreeng/support-bot doc-run      --agent <agent> --scope project

gh skill update --all                             # later
```

Claude Code follows the symlink and reads `${CLAUDE_SKILL_DIR}` as the symlinked path, so
`doc-run` still finds `doc-journeys` beside it. Skills are discovered when a session starts:
restart an open session after installing or updating, or `/doc-run` reports an unknown skill.

Pin a release with `--pin <tag>`; releases are cut with `gh skill publish --tag doc-tools/vX.Y.Z`
(prefixed, because plain `v*` tags belong to the application pipeline in this repository).
Re-pin with the same command plus `--force`. Never edit the vendored copies: change them here,
release, re-install.

With the `skills` CLI, which also targets other agents and keeps a `skills-lock.json`:

```bash
npx skills add coreeng/support-bot --skill doc-journeys --skill doc-run -a claude-code -y
```

`${CLAUDE_SKILL_DIR}` is a Claude Code substitution — the directory containing the `SKILL.md`
being read. Both skills say so near the top, so an agent that does not substitute it (or a human)
can read the paths literally and still resolve them. `doc-journeys` is usable that way from any
agent that follows a `SKILL.md`. `doc-run` also needs the agent to spawn subagents and to send
messages to one that is still running (its builder keeps its context for the whole run); it has
been used with Claude Code and Cursor.

**Running a skill.** Open the consumer repository in your agent, start a new chat or session and
type `/doc-run <documentation request>` (or `/doc-journeys …`). The skill takes the request from
the invocation's arguments, or from the agent's notice of which command ran, so nothing else
needs configuring. Three things to know:

  * A run lives in that session: it lasts hours, so keep it and the machine awake until the
    close-out. If the session errors or is closed mid-run, see
    [If a run stops early](doc-run/README.md#if-a-run-stops-early).
  * Choose a model with a long context window; the builder carries a product's whole discovery
    record for the run.
  * Start a new session after installing or updating the skills — an open one keeps the copy it
    started with.

## From zero to a merged run

The whole workflow, in order. Each step links to the detail.

1. **Install both skills into the consumer repository** and commit them there ([Installing](#installing)).
   Start a new agent session afterwards: skills are discovered at session start.
2. **Check out the source repositories side by side.** With `source_root:
   parent-of-consumer-root` (the starter's default), the source root is the directory that holds
   the consumer checkout, and every git repository directly under it is a source — including the
   consumer itself, whose existing documentation is consolidated. Clone the repositories the
   product is built from next to the consumer checkout (full clones, not shallow) and `git pull`
   them before a run: a run reads what is on disk. Every repository present is scanned, so
   remove the ones no product you are running needs.
3. **Set up `.doc-settings/`** from the starter and work through its checklist
   ([Setting up a consumer](#setting-up-a-consumer)). This is done once per consumer. One
   decision shapes every page: whether the output **replaces** your existing documentation or
   **coexists** with it. Set `prior_art_policy` accordingly — `coexist` (the default) links
   each page to the existing page it overlaps; `replace` never links to it and carries its
   content over instead. See
   [Replace or coexist](doc-journeys/README.md#replace-or-coexist-prior_art_policy).
4. **Declare the product** ([Declaring a product](#declaring-a-product)): its definition, its
   brief, its catalogue entry and its section in the estate adapter.
5. **Commit settings and declarations on `base_branch`.** A run branches from the committed
   state of `base_branch`; uncommitted edits in the main checkout are invisible to it.
6. **Optionally plan first**: `/doc-journeys plan mode for <product>` prints the page set without
   writing anything.
7. **Run it**: `/doc-run document <product>`. Expect one to three hours and a substantial token
   spend. Runs for different products can go in parallel — each has its own worktree and
   branch — and merge separately.
8. **Review and merge.** Read the close-out summary and the run report
   (`<reports_dir>/<slug>.md`), preview the pages at `preview_path`, then merge from
   `base_branch` and tidy up:

   ```bash
   git merge --no-ff doc-run/<run-id>
   git worktree remove <worktree_dir>/doc-run-<run-id>
   git branch -d doc-run/<run-id>
   ```

   The pipeline never pushes or opens a pull request.
9. **If a run stops before its close-out**, see
   [If a run stops early](doc-run/README.md#if-a-run-stops-early): its pages are usually
   complete and can be closed out by hand rather than re-run.
10. **Later**, `/doc-run refresh <product>` updates only what changed in the sources, and never
    overwrites a page a human has edited.

To change the skills themselves, edit them here, release, and re-install in the consumer; never
edit the vendored copies. A run in progress keeps the copy that was committed when its worktree
was created.

## Declaring a product

A run can draft a missing declaration itself, but a product declared by hand beforehand gets a
far better run. Declaring one means four files, all in the consumer repository:

1. **`product-definition/products/<slug>/brief.md`** — what the product is, in its owner's words.
   If your organisation keeps a product catalogue, extract the brief from it verbatim per
   [`references/product-definition.md`](doc-journeys/references/product-definition.md#the-extraction-procedure):
   every field rendered as the catalogue states it, frontmatter pinning the source and the
   revision it was taken from, and no corrections — errors in a brief are evidence of what the
   source said. Refresh it by re-extracting, never by editing. Otherwise the product owner
   writes it.
2. **`product-definition/products/<slug>/product.md`** — the declaration
   ([schema](doc-journeys/references/product-definition.md#productmd-schema)):
   * `name` and `owners` — the owning team, as the brief states it
   * `features` — the brief's capabilities *plus* the words the source documentation actually
     uses for them (component names, resource kinds, commands). This is the vocabulary discovery
     searches for; terms nobody writes find nothing
   * `repos` — only the repositories that are this product's own. A repository shared with other
     products is named in the estate adapter instead, so its other products' material is not
     attributed to this one
   * no `journeys/` directory for a first run: the run is then *product-only*, consolidating the
     existing documentation into the Diátaxis buckets. Add journeys later
   * below the frontmatter, inside an HTML comment marked as not documentation, record the
     decisions a later editor needs: the scope, which existing documentation the product's pages
     replace, its boundaries with neighbouring products, and why each repository is or is not in
     `repos`
3. **`product-definition/catalogue.md`** — add the slug, so batch runs include it.
4. **A section for the product in the estate adapter** (`.doc-settings/source-discovery.md`) —
   what discovery must read, may only use to verify, and must leave out. See
   [Per-product sections](doc-journeys/assets/doc-settings/source-discovery.md#per-product-sections-template)
   in the starter. This is the biggest single lever on output quality: source repositories mix
   material for the product's users with the owning team's own runbooks, designs and history,
   and this section is where that is sorted, file by file where needed.

Then update the estate adapter's **Repo scope** to list the repositories now checked out, check
that every path the new section lists exists, and commit. A run that finds a different set of
repositories than the adapter states says so in its report.

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
