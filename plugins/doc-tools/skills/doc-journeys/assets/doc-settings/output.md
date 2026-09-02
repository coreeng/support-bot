---
name: Site adapter — output
description: Everything about this repository's documentation site that the plugin's generic references/output.md does not know — the output section and its furniture, frontmatter conventions, template tags, the Weight table, the build, and any navigation quirk that hides the output. Loaded by the skill alongside references/output.md.
---

# Output — site adapter

The skill's `references/output.md` specifies the directory structure, the `doc_journeys`
provenance schema, slugs and the report rules. This file supplies the facts about *this* site.
The values below describe a fictional site under `site/`; `EDIT` every section. A **Docsy
example** is given at the end for consumers on that theme.

## The site

`EDIT:` name the generator and theme with versions, the content directory, whether raw HTML
renders, and how the toolchain is pinned. State what `build_toolchain` in `settings.md` means
here (for example "run the build as `mise exec -- <command>` when `mise` is on the PATH; when it
is not, run the command directly and record the generator version the build used").

**Anything gitignored lives only at the consumer root**, so a run worktree has none of it. If the
build needs a dependency directory, `build_command` in `settings.md` must point at
`<consumer root>`'s copy — a published re-derive command naming `<repo root>`'s copy cannot
reproduce from a worktree.

## Output root and section furniture

`output_root` is `site/content/docs/generated/`. It is deliberately **inside** the content tree so
the output renders like any other section — it appears in the navigation and previews locally,
and reviewers read it as a site rather than as a diff.

`EDIT:` say what publishing the branch does. If the site deploys unconditionally from the default
branch, the generated section publishes with it; the section index states plainly that its pages
are generated and proposed.

Section furniture, created when first needed and then left alone:

| Path | Title | Weight |
| --- | --- | --- |
| `generated/_index.md` | `Generated Docs` | `EDIT` — sorts after the existing sections |
| `generated/products/_index.md` | `Products` | `1` |
| `generated/cross-product-journeys/_index.md` | `Cross-Product Journeys` | `2` |
| `generated/reports/_index.md` | `Reports` | `3` |

`proposals_root` — `doc-tools/proposals/` — is outside the content tree and never publishes.

## Navigation quirks

`EDIT:` record anything that hides the output from a reader who lands on the site — a landing-page
redirect, a hand-maintained navigation menu the new section is not in. Do not edit the quirk;
flag it in every report's Follow-ups and send reviewers to `preview_path` directly. Write "none
known" if there is none, so a run can tell an empty section from an unwritten one.

## Frontmatter conventions

`EDIT:` name the title and weight fields exactly as this site spells them (`title`/`weight`,
`Title`/`Weight`, …) and any other standard fields authored pages should carry. The skill's
`doc_journeys:` provenance block is lowercase and namespaced so it cannot collide with them.

State whether the theme renders the title field as the page heading (then pages carry **no body
`H1`**, and two pages in one directory must not share a title) and whether front matter must
start on line 1 (then the generation notice goes **below** the closing `---`).

## Template tags and markers

`EDIT:` list the template tags authored pages may use, and give the exact syntax for the three
markers in `settings.md`:

| Purpose | Syntax |
| --- | --- |
| unverified claim (`unverified_marker`) | `> **Unverified:** …` |
| low-confidence banner (`low_confidence_banner`) | `> **Low confidence — review before relying on this page.** …` |
| report banner (`report_banner`) | `> **Run diagnostic, not documentation.** …` |
| section listing | `EDIT` — the tag the rest of the site uses for child listings, ordered by weight |

**Quoting template-tag syntax requires escaping** on most generators, which expand tags before
markdown is rendered — a tag inside a backtick span is still executed, and an unclosed one fails
the build. `EDIT:` give the escaped form and the check command to run over `reports_dir` before
shipping any report, for example:

```bash
grep -rn '<opening delimiter>' <reports_dir> | grep -v '<escaped form>'
```

`EDIT:` say whether the site rewrites markdown links at render time. If it does, a relative form
that looks wrong in source can be correct; links must be verified against rendered HTML, never
from source syntax alone.

## Rendered-table check

`EDIT:` state the markup the theme emits for tables, and set `render_check` in `settings.md` to a
selector that matches it. A bare `<table>` regex finds zero tables on a page whose theme adds
classes to every one.

## Weight table

The theme orders navigation by weight within each section. The skill's rule is that Weight is
assigned deterministically so re-runs do not reshuffle the navigation; these are the values:

| Level | Weight rule |
| --- | --- |
| `generated/_index.md` | `EDIT` |
| `generated/products/_index.md` | `1` |
| `generated/cross-product-journeys/_index.md` | `2` |
| `generated/reports/_index.md` | `3` |
| `reports/batch.md` | `1` |
| other `reports/*.md` | alphabetical by slug, numbered from `2` |
| `cross-product-journeys/<journey>/_index.md` | the journey's 1-based position in the resolved cross-product journey list |
| `cross-product-journeys/<journey>/<spine>.md` | `1` |
| `<product>/_index.md` | products in alphabetical order by slug, numbered `1`, `2`, `3`, … |
| `<product>/tutorial/_index.md` | `10` |
| `<product>/how-to/_index.md` | `20` |
| `<product>/reference/_index.md` | `30` |
| `<product>/explanation/_index.md` | `40` |
| `<product>/<journey>/_index.md` | `100 + n`, `n` the journey's 1-based position in the product definition's journey list |
| pages inside a bucket | alphabetical by slug, numbered `1`, `2`, `3`, … |
| `<spine>.md` | `1` |
| `<variation>.md` | the variation's 1-based position in the journey's `variations` list |

The buckets are weighted in Diátaxis order — tutorial, how-to, reference, explanation. Journeys
are weighted `100 +` so every bucket sorts above them. Known latent churn: the alphabetical
product-numbering rule renumbers existing products when a new one lands ahead of them.

## House style

`style_guide` is `site/STYLE.md`. `EDIT:` summarise the rules that bite most — list markers and
indentation, terminal punctuation in list items, spelling variant — and say the guide is binding,
not advisory.

## Known template defects

`EDIT:` list defects the site's own templates reproduce on every page (a banner whose link points
at a redirecting landing page, say). A run that reproduces one is reporting it upstream, not
shipping a new defect; the structure reviewer grades it `latent`. Write "none known" if none.

---

## Docsy example (labelled — delete if your site is not Hugo + Docsy)

For a Hugo site on the Docsy theme, the sections above typically resolve to:

  * **The site** — Hugo with Docsy as a Hugo module; `contentDir` `content/en`;
    `markup.goldmark.renderer.unsafe: true` so raw HTML renders. Docsy's PostCSS step resolves
    plugins from Hugo's module cache and needs the Node module search path pointed at the consumer
    root's `node_modules`, so `build_command` sets that variable to `<consumer root>/node_modules`
    before `hugo --destination <scratch dir>`; the failure otherwise misleadingly reports
    `autoprefixer` missing.
  * **Frontmatter** — Docsy renders `title` as the page heading and builds the sidebar from it;
    Hugo only parses front matter that starts on line 1.
  * **Markers** — `unverified_marker`: `{{% alert title="Unverified" color="warning" %}} … {{% /alert %}}`;
    `low_confidence_banner`: `{{% pageinfo color="warning" %}} … {{% /pageinfo %}}`;
    `report_banner`: `{{% pageinfo %}} … {{% /pageinfo %}}`. Section listings use whichever
    child-listing shortcode the rest of the site uses.
  * **Escaping** — Hugo expands shortcodes inside backtick spans; quote as
    `{{%/* alert title="Unverified" */%}}`, and check reports with
    `grep -rn '{{[%<]' <reports_dir> | grep -v '/\*'`.
  * **Rendered tables** — Docsy emits `<table class="table table-striped">`; `render_check` is
    `table.table`.
