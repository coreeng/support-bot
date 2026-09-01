---
# Adapter files in this directory, read by the skill at the points named in the plugin's
# skills/doc-journeys/references/settings.md. Filenames are free; these are the starter's.
source_discovery: source-discovery.md
output: output.md
authorisations: authorisations.md

# Every path below is relative to the REPO ROOT — the checkout (or run worktree) the pipeline
# reads content from and writes content to. The examples assume a site under `site/` whose
# content tree is `site/content/docs/`. EDIT all four.
output_root: site/content/docs/generated
reports_dir: site/content/docs/generated/reports
proposals_root: doc-tools/proposals
plan_file: doc-tools/plan.md

# Existing documentation the output may overlap. Scanned by the prior-art pass, cited as tier-5
# prose, never edited. output_root is excluded from it implicitly. EDIT.
prior_art_roots:
  - site/content/docs

# The only paths a run may write. The orchestrator baselines and cross-checks `git status` over
# exactly these. Keep product-definition/ in the list: a run may declare there. EDIT the first two.
write_locations:
  - site/content/docs/generated/
  - doc-tools/proposals/
  - product-definition/

# Orchestration. base_branch is the branch run worktrees branch from and merge back to;
# worktree_dir must be gitignored. EDIT base_branch if it is not your default branch.
base_branch: main
worktree_dir: .worktrees

# Discovery. source_root is a rule or an absolute path. `parent-of-consumer-root` means: the
# parent directory of the MAIN checkout of this repo (never of a run worktree, never
# $HOME-relative). Every direct child of it that is a git repository is a source repo, this repo
# included. List repositories to leave out under source_exclude_repos (directory names).
source_root: parent-of-consumer-root
source_exclude_repos: []
# Subtrees of THIS repo that are the pipeline's own output and must never be cited as a source
# (a run would otherwise cite itself). Mirror output_root, the proposals/plan directory and
# worktree_dir. EDIT.
source_exclude_paths:
  - site/content/docs/generated/
  - doc-tools/
  - .worktrees/

# Site build. `<consumer root>` and `<scratch dir>` are substituted at run time. The command
# must work from a RUN WORKTREE, which lacks anything gitignored (dependency directories live only
# at the consumer root — point the build at `<consumer root>/node_modules` or equivalent where the
# theme needs it). Run it once by hand from a worktree before the first run. EDIT.
build_command: "cd site && foglight-docs build --destination <scratch dir>"
# Version-manager shim to run the build through (`mise`, `asdf`, …). Optional; leave empty for none.
build_toolchain: ""
# CSS selector the structure reviewer uses to find rendered tables. Themes add classes to <table>,
# so a bare `table` can miss every one; check one rendered page. EDIT if needed.
render_check: "table"
# Where a reviewer is sent to read the output. If your landing page redirects, this must be the
# direct link — see output.md.
preview_path: /docs/generated/

# Authoring. style_guide is binding house style for authored pages; the three markers are the
# site's own syntax and are written verbatim onto pages. EDIT all four to your site's syntax —
# output.md carries a labelled Docsy example.
style_guide: site/STYLE.md
unverified_marker: '> **Unverified:** …'
low_confidence_banner: '> **Low confidence — review before relying on this page.** …'
report_banner: '> **Run diagnostic, not documentation.** …'

# Strongest corroborators for contact points (chat channels, group handles, distribution lists):
# schema-validated fields in source repos. Repo-relative to the source root. EDIT or set to []
# if the estate has none — contact points then need corroboration from prose in two repos.
contact_corroborators:
  - repo: foglight-config
    path: teams/
    field: supportChannelId
---

# Doc-tools settings for this repository

This directory holds everything about **this** repository and **this** estate that the
`doc-journeys` and `doc-run` skills need and must not carry themselves. The skills are
consumer-agnostic; they read this file first and stop if it is absent.

Three files sit beside it, named in the frontmatter above:

  * `source-discovery.md` — the **estate adapter**: how the source root is derived, which
    repositories are in scope, where prior art lives, which files are always worth reading, and
    the known contact-point traps in this estate
  * `output.md` — the **site adapter**: the output section and its furniture, the site's
    frontmatter conventions, template tags, the Weight table, the build, and any navigation quirk
    that hides the output
  * `authorisations.md` — the standing authorisations under which unattended runs operate

`product-definition/` at the repository root is the fourth consumer-owned input.

## Path roles

| Role | Meaning | Used for |
| --- | --- | --- |
| plugin root | where the doc-tools plugin is installed (`${CLAUDE_PLUGIN_ROOT}`) | `SKILL.md`, `references/`, agent definitions |
| consumer root | the **main** checkout of this repository: `dirname "$(git -C <repo root> rev-parse --path-format=absolute --git-common-dir)"` | `.doc-settings/`, the site's dependency directory |
| repo root | the checkout or run worktree content is read from and written to | every path in the frontmatter above; `product-definition/` |
| source root | per `source_root` above | discovery |

In the main checkout, consumer root and repo root are the same directory. In an orchestrated run
the repo root is a worktree under `worktree_dir` and the consumer root is its parent checkout —
which is why `build_command` may need to name `<consumer root>`.

## Key reference

| Key | Read by | Meaning |
| --- | --- | --- |
| `source_discovery`, `output`, `authorisations` | both skills | the adapter filenames in this directory |
| `output_root` | doc-journeys, structure reviewer, routing reviewer | the one directory pages and reports are written under |
| `reports_dir` | doc-journeys, structure reviewer | where run reports go; must sit under `output_root` |
| `proposals_root` | doc-journeys, doc-run | sidecar proposals for human-edited pages; must sit **outside** `output_root` so it never publishes |
| `plan_file` | doc-builder | where the builder persists its plan before stopping at the gate; outside `output_root` |
| `prior_art_roots` | doc-journeys, gap auditor, routing reviewer | existing documentation the output may overlap; read-only |
| `write_locations` | doc-run, structure reviewer | the `git status` scope for the pre-run baseline and the manifest cross-check |
| `base_branch` | doc-run | the branch run worktrees branch from and merge back to |
| `worktree_dir` | doc-run | where run worktrees are created; must be gitignored |
| `source_root` | doc-journeys, every reviewer | rule or absolute path |
| `source_exclude_repos` | doc-journeys | source-root children never scanned |
| `source_exclude_paths` | doc-journeys, gap auditor, entity verifier | subtrees of this repo that are pipeline output |
| `build_command`, `build_toolchain` | structure reviewer, doc-run | the pinned site build |
| `render_check` | structure reviewer | selector for rendered tables |
| `preview_path` | doc-run, doc-journeys | the link reviewers are given |
| `style_guide` | doc-journeys | binding house style for authored pages |
| `unverified_marker`, `low_confidence_banner`, `report_banner` | doc-journeys, structure reviewer | the site's marker syntax; quoted forms must be escaped per `output.md` |
| `contact_corroborators` | doc-journeys, entity verifier | tier-1 corroborators for contact points |
