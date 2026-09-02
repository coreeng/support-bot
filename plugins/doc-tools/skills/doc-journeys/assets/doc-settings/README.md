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

## Before the first run

  * `product-definition/` exists at the consumer root (schema in the skill's
    `references/product-definition.md`)
  * `worktree_dir` is gitignored
  * `base_branch` exists
  * the site's dependency directory (`node_modules` or equivalent) is present at the consumer
    root — a run worktree does not have it
  * `build_command` has been run once by hand from a worktree and is green

Then run the skill in plan mode before anything writes:

```
/doc-journeys plan mode for <a product you have declared>
```
