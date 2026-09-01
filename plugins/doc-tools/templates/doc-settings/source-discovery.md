---
name: Estate adapter — source discovery
description: Everything about this source estate that the generic discovery funnel in the plugin's references/source-discovery.md does not know — source-root derivation, repo scope, prior-art location, always-add candidates, term-expansion vocabulary, and the known contact-point traps. Loaded by the skill immediately after references/source-discovery.md.
---

# Source discovery — estate adapter

The skill's `references/source-discovery.md` specifies *how* to find, rank and score evidence.
This file supplies the facts about *this* estate that it deliberately does not carry. Every
example below is for the fictional **Foglight** estate — replace it with yours.

## Source root

`settings.md` sets `source_root: parent-of-consumer-root`. Resolve it as **the parent directory
of the MAIN checkout of this repository**, not as a literal path — and not as the parent of the
working directory, because orchestrated runs execute in a git worktree under `worktree_dir`,
whose parent is inside the repo:

```bash
CONSUMER_ROOT=$(dirname "$(git -C <repo root> rev-parse --path-format=absolute --git-common-dir)")
SRC_ROOT=$(dirname "$CONSUMER_ROOT")
```

In the main checkout this collapses to the plain parent directory, so it is correct in both
cases. When a source root is supplied in a spawn prompt, use it verbatim.

Do **not** rely on `~` expanding to the directory that holds the checkout; derive the absolute
path once at the start of the run and use it verbatim in every subsequent command.

## Repo scope

Every direct child of the source root containing a `.git` entry is a source repo. Print the
resolved list and count before discovery begins. `EDIT:` state how many repositories that is
today, so a run that finds a very different number knows something moved.

**This repository is a source repo like any other**, including its existing documentation under
`prior_art_roots`. `EDIT:` say whether the output is intended to sit beside that documentation or
eventually replace it — the routing reviewer treats overlap differently in the two cases. Three
subtrees are **excluded as sources** (`source_exclude_paths`), because they are the pipeline's
own output and would let a run cite itself:

  * `site/content/docs/generated/` — the generated section
  * `doc-tools/` — sidecar proposals and the builder's plan file
  * `.worktrees/` — parallel runs' worktrees, each a full copy of the repo

When the run writes into a worktree of this repo, scan the **main checkout** as the source — the
content is identical at the branch point, and the worktree's output section is in-flight output.

`source_exclude_repos` is empty: nothing else is excluded by default. `EDIT` if archived or
mirror repositories sit under the source root.

## Prior art

`prior_art_roots` is `site/content/docs/` — the whole existing site, minus the output root
(always excluded from prior art). Everything under it is read-only. Never edit, move or delete
anything under a prior-art root, including content an authored page supersedes. Retiring an
existing page is a human decision the report may recommend.

Prior-art hits are cited with `repo: <this repository's directory name under the source root>`.

## Always-add candidates

Beyond the generic always-add list (`README.md`, `CONTRIBUTING.md`, `DEVELOPMENT.md`, `docs/`,
`documentation/`), `EDIT:` name any file every repository in this estate carries that identifies
the component and its owning team — a service-catalogue descriptor, an ownership file — and
score it as the rubric's "component identity file" row. Example: every Foglight repository
carries `component.yaml` naming the owning team.

## Term expansion vocabulary

Examples of the expansion step's reasoning against this estate, for Pass 0. `EDIT` with the
component names your journeys' vocabulary should expand to:

  * "ingest telemetry" → `foglight-agent`, `collector`, `otlp`, `receiver`, `pipeline`
  * "alert" → `foglight-alerting`, `rule`, `notifier`, `silence`, `escalation`
  * "dashboard" → `foglight-dashboard`, `panel`, `query`, `datasource`

## Instance directories

The collapse rule in the skill exists because configuration repositories hold many near-identical
per-tenant, per-namespace or per-environment files. `EDIT:` name the repositories and directories
in this estate that have that shape, so a shortlist swamped by them is recognised early. Example:
`foglight-config/tenants/<team>/` holds one near-identical file per team.

## Known contact-point traps

The generic rule ("search every form; former names persist; anchor exclusions to the repo root")
is in the skill's `references/authoring.md`. These are the instances in this estate — `EDIT` all
four:

  * **Strongest corroborator.** `supportChannelId` in `foglight-config/teams/<team>.yaml`,
    validated by the config pipeline against an allowed chat host — a malformed value fails the
    pipeline, so a well-formed one has been checked by something other than a human's memory.
    Declared as `contact_corroborators` in `settings.md`.
  * **Former names in live use.** `#foglight-support` is channel ID `C0FOGLIGHT`; two source
    repos still call it by its old name `#foglight-help`. A reader who greps concludes there are
    two channels.
  * **Prefix collisions.** `#foglight-alerts` matches inside `#foglight-alerts-staging`; anchor
    the boundary — `grep -rlE '#foglight-alerts([^-]|$)'`.
  * **Permalink form.** `https://<workspace>.slack.com/archives/<ID>`.
  * **Excluding this repo from a corroboration count.** Anchor to the repo root:
    `grep -v '^<this repo directory name>/'` — or exclude at the tool level with `--exclude-dir`,
    because GNU grep emits no `./` prefix and an unanchored filter is inert.
