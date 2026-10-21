# `.doc-settings/` starter

Copy this directory to the root of the repository you want documented — the **consumer** — as
`.doc-settings/`, then edit every value marked `EDIT` in the files below. The skills stop if the
directory is missing or a required key is empty, so nothing here is optional unless the skill's
`references/settings.md` says so.

```bash
cp -r <tools root>/doc-journeys/assets/doc-settings <consumer root>/.doc-settings
```

| File | Role | What to change |
| --- | --- | --- |
| `settings.md` | every scalar the pipeline pins — paths, branch, build, marker syntax | almost everything; the frontmatter is annotated |
| `source-discovery.md` | the **estate adapter**: which repositories are sources, where prior art lives, term-expansion vocabulary, contact-point traps | the repo scope, the always-add files, the traps |
| `output.md` | the **site adapter**: output section and furniture, frontmatter conventions, template tags, the Weight table, how the site builds | the site facts; a labelled Docsy example is included |
| `authorisations.md` | what an unattended run may do in this repository, granted by whom, when | the grants and the grantor |

The examples throughout use **Foglight**, the fictional observability product from the skill's
`references/examples.md`, with a fictional documentation site under `site/`. Replace them; do not
document Foglight.

## Before your first run

Every item below has stopped or spoiled a real run. Check them from the **consumer root** — the
main checkout — because that is where a run resolves them.

  * **`.doc-settings/settings.md` has every required key** listed in the skill's
    `references/settings.md`, with a value. A key that is present but empty counts as absent,
    and the run stops with the key named.
  * **The three adapter files named in `settings.md` exist** beside it and no longer carry
    `EDIT` markers or Foglight values.
  * **`product-definition/` exists at the consumer root** and validates against the skill's
    `references/product-definition.md` — every `product.md` has a `name`, journey names are
    unique across the whole definition. Recommended, not required: each product you intend
    to run has a brief or at least `features` — without them the skill still runs, but discovery
    vocabulary is thinner and the report says so.
  * **The site conventions match the site.** If the site does not use `_index.md` section
    indexes, `.md` pages, a weight field for ordering and plain markdown, then `section_index`,
    `page_extension`, `nav_ordering`, `nav_file` and `content_format` are set in `settings.md`.
    The site adapter's *Navigation file* and *MDX* sections are then filled in, or deleted
    where they do not apply.
  * **The site's frontmatter schema accepts a `doc_journeys:` key.** Every page a run writes
    carries one. To check, add `doc_journeys: {}` to one existing page, run `build_command`, then
    revert the page.
  * **Every source is a git repository directly under the source root.** To list them, run
    `find <source root> -mindepth 2 -maxdepth 2 -name .git`. A folder missing from that list is
    not scanned. Loose files, such as exported wiki pages, go inside one of the listed
    repositories: see *Sources that are not git repositories* in the skills README.
  * **If the output root already has pages without a `doc_journeys:` block**, follow *Taking
    over an existing site* in the skills README. Without the `adopt` switch those pages are
    protected as human-edited.
  * **`base_branch` exists** (`git rev-parse --verify <base_branch>`). A run worktree branches
    from it and the finished branch merges back into it.
  * **The directory named by `worktree_dir` is gitignored** (`git check-ignore <worktree_dir>`
    prints the path). Run worktrees are created inside it; an unignored one would appear as
    untracked content in every run's `git status` cross-check.
  * **The site's build dependencies exist at the consumer root** — a `node_modules` directory or
    whatever the site generator needs — and **`build_command` points at the consumer root's
    copy**, via the `<consumer root>` placeholder. A run worktree has nothing gitignored, so a
    build that only finds its dependencies from the main checkout fails the structural gate.
  * **`build_command` has been run once by hand from a worktree and is green**, with the
    `<consumer root>` and `<scratch dir>` placeholders substituted the way a run substitutes
    them.
  * **If `build_toolchain` is set, the version-manager shim it names is installed** on the
    machine that runs the pipeline (`command -v <shim>`). Otherwise leave it empty and the build
    runs unwrapped.
  * **The search tools your command templates assume are installed.** The skills' own templates
    use `rg`; if your adapters or reports pin `rg` or `grep` commands, check the named binary is
    on the PATH and behaves as the template expects (a shell alias or wrapper is not the same
    thing).
  * **The skills sit where your agent looks, and the session started after they landed.** Most
    agents read `.agents/skills/` (`gh skill install --agent universal`); Claude Code reads
    `.claude/skills/`, and a symlink from there to `.agents/skills` works. Discovery happens when
    a session starts, so restart an open session after installing or updating.

Then run the skill in plan mode before anything writes:

```
/doc-journeys plan mode for <a product you have declared>
```
