---
name: Consumer settings
description: How the skill locates the consumer's `.doc-settings/` directory, which keys it requires, the four path roles every other file uses, and what to do when settings are absent (stop). Load first, before the product definition, in every mode except legacy `audit`.
---

# Consumer settings

This skill is **consumer-agnostic**. It knows how to discover evidence, author Diátaxis-typed
pages, score confidence and refresh safely. It does not know where your documentation site is,
which repositories are in scope, what your frontmatter looks like, how your site builds, or what
you have authorised an unattended run to do. All of that lives in the **consumer repository**,
under `.doc-settings/`, beside the `product-definition/` it already owns.

Load this file first. Every other reference file names settings keys — `output_root`,
`prior_art_roots`, `build_command` — and expects them to be resolved.

## Path roles

Four roots, used consistently across every reference file and every agent definition:

| Role | Meaning | Used for |
| --- | --- | --- |
| **tools root** | the directory holding the `doc-journeys` and `doc-run` skill directories — `${CLAUDE_SKILL_DIR}/..` from either skill (the plugin's `skills/`, or `.claude/skills/` in a vendored install); in an orchestrated run, pinned verbatim in every spawn prompt | `SKILL.md`, `references/`, the agent prompt files under `doc-run/agents/` |
| **consumer root** | the **main** checkout of the repository being documented | `.doc-settings/`, `node_modules` and anything else that is gitignored and therefore absent from a worktree |
| **repo root** | the checkout content is read from and written to — in an orchestrated run, a worktree under the consumer's `worktree_dir` | `product-definition/`, every path in `settings.md` |
| **source root** | per `source_root` in `settings.md` | discovery |

Derive the consumer root from the repo root, never the other way round, and never from the
working directory:

```bash
CONSUMER_ROOT=$(dirname "$(git -C <repo root> rev-parse --path-format=absolute --git-common-dir)")
```

In a main checkout this returns the repo root itself. In a worktree it returns the checkout the
worktree was created from. When a spawn prompt supplies any of these paths, use them verbatim.

## Locating settings

```
<consumer root>/.doc-settings/settings.md
```

**If the file is absent, stop and tell the user.** Do not fall back to the working directory, do
not guess an output root from the repository's layout, and do not proceed with defaults. The
same posture applies as to a missing source root: a run that cannot find its settings would
write somewhere nobody intended. Tell the user what the file is for and point them at the
`${CLAUDE_SKILL_DIR}/assets/doc-settings/` starter that ships with the plugin (or at another consumer's
`.doc-settings/` as a worked example).

Legacy `audit` mode is the one exception — it classifies markdown in a single repository and
writes there, so it reads no settings.

## Required keys

`settings.md` is a markdown file whose frontmatter carries every scalar the pipeline pins. Its
body is free prose for the consumer's own notes. All keys are required unless marked optional;
paths are relative to the **repo root**.

| Key | Type | Meaning |
| --- | --- | --- |
| `source_discovery` | filename | the **estate adapter**, loaded immediately after `${CLAUDE_SKILL_DIR}/references/source-discovery.md`: source-root derivation, repo scope, prior-art location, always-add candidates, term-expansion vocabulary, known contact-point traps |
| `output` | filename | the **site adapter**, loaded alongside `${CLAUDE_SKILL_DIR}/references/output.md`: output section and furniture, frontmatter conventions, template tags and marker syntax, the Weight table, build notes, house style |
| `authorisations` | filename | the standing authorisations an unattended run operates under — who granted what, when |
| `output_root` | path | the one directory pages and reports are written under |
| `reports_dir` | path | where run reports go; under `output_root` |
| `proposals_root` | path | sidecar proposals for human-edited pages; **outside** `output_root` |
| `plan_file` | path | where the builder persists its plan before stopping at the gate; outside `output_root` |
| `prior_art_roots` | list of paths | existing documentation the output may overlap — scanned as tier-5 prose, cited, never edited; `output_root` is always excluded from them |
| `write_locations` | list of paths | the only paths a run may write; the orchestrator's `git status` scope |
| `base_branch` | string | the branch run worktrees branch from |
| `worktree_dir` | path | where run worktrees are created; must be gitignored |
| `source_root` | rule or absolute path | `parent-of-consumer-root`, or a path |
| `source_exclude_repos` | list | source-root children never scanned (may be empty) |
| `source_exclude_paths` | list of paths | subtrees of the consumer repo that are pipeline output and must never be cited |
| `build_command` | string | the pinned site build; `<consumer root>` and `<scratch dir>` are substituted at run time |
| `build_toolchain` | string, optional | a version-manager shim to run the build through (`mise`, `asdf`, …) |
| `render_check` | selector | how the structure reviewer finds rendered tables |
| `preview_path` | URL path | the link reviewers are given to the output |
| `style_guide` | path | binding house style for authored pages |
| `unverified_marker`, `low_confidence_banner`, `report_banner` | string | the site's marker syntax for an inferred claim, a low-confidence page, and a run report |
| `contact_corroborators` | list of `{repo, path, field}` | tier-1 corroborators for contact points, repo-relative to the source root |

A key that is present but empty is treated as absent. A missing required key stops the run with
the key named.

## How the values are used

  * **doc-journeys** reads `settings.md` at Process step 0, then loads the two adapters at the
    points its load order names. Every path it prints, writes or reports is built from these
    keys; nothing is hard-coded.
  * **doc-run** reads `settings.md` at its step 0 and **pins every value it uses in every spawn
    prompt** — the four roots, `write_locations`, `build_command` with `<consumer root>`
    substituted, `preview_path`, `base_branch`. Agents therefore never need to re-read
    settings, and a reviewer's re-derive command lands on the same paths as the builder's.
  * **Agents** take the pinned values from their spawn prompt. The fallback, when a value is
    missing from the prompt, is to read `<consumer root>/.doc-settings/settings.md` — never to
    guess.

## Repo-specific examples in this skill

Where the reference files need a worked example they use **Foglight**, a fictional observability
product introduced in `${CLAUDE_SKILL_DIR}/references/examples.md`. Nothing about any real estate is encoded in the
skill; if you find something that is, it belongs in `.doc-settings/`.
