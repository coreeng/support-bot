---
name: Output layout
description: Where authored pages are written, the directory and section structure, the frontmatter provenance schema, Weight determinism, the report rules, and re-run behaviour. Load once per run, before the first page is written, together with the consumer's site adapter. Replaces the base skill's "Output root resolution", "Naming and collisions", and "Frontmatter injection" rules.
---

# Output layout

Authored pages are written into the consumer's documentation site. This file is authoritative on the **shape** of the output — structure, invariants, provenance schema, report rules. The **site facts** — where the output root is, what the site's frontmatter fields are called, which template tags exist, the numeric Weight table, how the site builds — live in the consumer's **site adapter**, the file named by `output` in `.doc-settings/settings.md` (`${CLAUDE_SKILL_DIR}/references/settings.md`). Load both together; neither is complete alone.

It replaces three sections of `SKILL.md`: *Output root resolution*, *Naming and collisions*, and Step 5 *Frontmatter injection rules*.

## Output root

`output_root` in the consumer's settings — the only location under the site's content directory that this skill writes to. It holds three peer sections — `products/`, `cross-product-journeys/` and `reports/` — and nothing else.

The output root sits **inside** the content tree rather than beside it, so the output renders exactly like any other section — it appears in the navigation, it is browsable in a local preview, and reviewers can read it as a site rather than as a diff. That is the whole point: proposed documentation that cannot be previewed does not get reviewed.

The consequence is that this content **publishes when the branch reaches the deploying branch**, if the site deploys unconditionally — the site adapter says whether it does. That is accepted and known. The output root's `_index.md` therefore states plainly that the section is generated and proposed, so a reader who arrives from search is not misled about its status.

The section has three peers beneath it — the two things a reader can arrive looking for, plus the diagnostics about both:

  * `<output_root>/_index.md` — the section's own index. One short paragraph saying what the section is and that its pages are generated. The skill creates this file if it is absent and otherwise leaves it alone — it is section furniture, not per-run output.
  * `<output_root>/products/_index.md` — listing the products.
  * `<output_root>/cross-product-journeys/_index.md` — listing the cross-product journeys with a one-line description each. Created only when at least one cross-product journey produced pages; an empty section is a stub and is never written.
  * `<output_root>/reports/_index.md` — see *The reports section* below.

Their titles and Weights are in the site adapter. Products sort first because a reader orienting themselves asks what exists before asking how to combine it. Reports sort last because they are about the other two rather than about the platform. All are section furniture: created when first needed, then left alone.

Run reports go **inside** the content directory, to `reports_dir` (under the output root). See *The reports section* below — it renders them as pages, deliberately, so they can be read alongside the output they describe. This reverses an earlier rule that kept them outside the content directory; the reasoning for the reversal, and the constraints it brings, are in that section.

Sidecar proposals are the exception and stay **outside** the content tree, at `proposals_root`. A proposal is draft page content rather than a diagnostic, so publishing one would put a second version of a real page on the site with nothing to distinguish it. See `${CLAUDE_SKILL_DIR}/references/refresh.md`.

Nothing outside `write_locations` is created, edited, moved, or deleted — including everything under `prior_art_roots`, which the skill reads for prior art (per `${CLAUDE_SKILL_DIR}/references/source-discovery.md`) but never touches.

### Site constraints to be aware of

The site adapter records the site's theme and version, its template tags, its content directory and renderer settings, and any **navigation quirk that hides the output** — a landing-page redirect, say, that leaves the output root reachable only from the sidebar. Where one exists, flag it in every report as a follow-up for a human — never edit the redirect — and send reviewers to `preview_path` directly. It matters more than it looks: a reviewer sent a bare link to the site will never find the proposed docs.

## Directory structure

```
<output_root>/
  _index.md                              # section furniture
  products/
    _index.md                            # products landing, lists all products
    <product-slug>/
      _index.md                          # product overview + journey index
      tutorial/                          # the four Diátaxis buckets
        _index.md
        <lesson-slug>.md
      how-to/
        _index.md
        <task-slug>.md
      reference/
        _index.md
        <subject-slug>.md
      explanation/
        _index.md
        <concept-slug>.md
      <journey-slug>/
        _index.md                        # journey overview + prerequisites + page index
        <spine>.md                       # when the journey declares no variations
        <variation-slug>.md              # one per declared variation, instead of <spine>.md
  cross-product-journeys/
    _index.md                            # cross-product landing, lists the journeys
    <journey-slug>/
      _index.md                          # what the journey is and which products it crosses
      <spine>.md                         # the route — orients and links, never restates
  reports/
    _index.md                            # reports landing, says these are diagnostics
    batch.md                             # the batch summary (multi-product batch runs only)
    <product-slug>.md                    # one per whole-product run
    <product-slug>-<journey-slug>.md     # one per journey-scoped run
    cross-product-journeys.md            # the cross-product phase
```

`<spine>.md` is named for the journey's declared `spine`: `how-to.md`, `tutorial.md`, or `explanation.md`. It defaults to `how-to.md`, which is what every journey produced before the field existed, so existing output is unaffected. Naming the file after its type means a reader — and a later run — can see what a journey's end-to-end page is without opening it.

The four type sections are the **Diátaxis buckets**: every page a product carries belongs to exactly one of them, and the report assesses all four whether or not pages landed in each. Journey directories sit alongside rather than inside the buckets, because a journey is an end-to-end path rather than a category — its pages carry a Diátaxis type like any other, but they are organised by the journey a reader is on rather than by that type.

Rules:

  * Every directory that contains pages **must** have an `_index.md`. A section without one renders with an empty title and breaks the navigation.
  * **A bucket is created only when there are pages to put in it.** Never create an empty section with a placeholder index. An unfilled bucket is reported as a gap per `${CLAUDE_SKILL_DIR}/references/gap-analysis.md`, and that is the correct outcome — a stub would look like coverage and would be flagged as `hollow` by the skill's own quality check.
  * Journey directories are always created — every journey in the product definition gets one, even if only `_index.md` and one how-to land in it.
  * If a journey produced no pages at all because no evidence was found (per `${CLAUDE_SKILL_DIR}/references/authoring.md`), create **no directory** for it. Record it as `missing` in the report instead.
  * A product with no brief still gets `<product>/_index.md`, navigation-only. See `${CLAUDE_SKILL_DIR}/references/authoring.md`.
  * **`cross-product-journeys/` is created only when at least one cross-product journey produced pages.** A definition with no such journeys, or whose journeys all found too little evidence to write, gets no section at all — not an empty one. The same no-stub rule that governs buckets governs the whole section.
  * A cross-product journey directory contains exactly two files: `_index.md` and its spine page. It carries **no buckets and no variations**. Buckets are a product-level organising device, and a journey whose job is to route readers to other pages has nothing to put in them. If a cross-product journey seems to need a variation, it is two journeys or it is a product journey.

## The reports section

Run reports are published as pages under `reports_dir`, so they render alongside the output they describe.

**One run, one report file.** The report path follows the run's scope: a whole-product run writes `reports/<product-slug>.md`, a journey-scoped run writes `reports/<product-slug>-<journey-slug>.md`, the cross-product phase writes `reports/cross-product-journeys.md`, and only a multi-product batch run writes `reports/batch.md`. A run never amends a report a different scope owns — parallel journey runs sharing one report file inherited each other's stale prose and collide at merge. A stale claim noticed in another run's report is a suggested action for a human, not an edit.

**Why they render.** The same reasoning that put the output root inside the content tree applies to the diagnostics about it: a report that cannot be previewed does not get read. These reports are mostly tables and cross-references, and reading them rendered — with working links between them and to the pages they discuss — is materially easier than reading raw markdown in a diff. Reviewers get the report and the pages in one place.

**What it costs, stated plainly.** The section publishes exactly as the pages do, wherever the site deploys unconditionally. Run diagnostics therefore become live pages on the documentation site, discoverable by site search. They name documentation gaps, carry confidence scores, and describe what the skill could not write. That is accepted, and it is the reason for the labelling rules below rather than a reason to reconsider.

### Report page rules

  1. **Every report carries frontmatter.** The site's title, summary and weight fields (named in the site adapter), and a tags field carrying `doc-journeys` and `run-report`. Without frontmatter a report renders untitled and breaks the navigation, the same as any other page.
  2. **Title for a reader, not for a filename.** `Foglight run report`, not `foglight.md`. The summary says what the report is *for*, since it is what search shows.
  3. **No body `H1`.** The site renders the title field as the page heading, so a leading `# ...` duplicates it. Reports begin at `## `.
  4. **Every report opens with the `report_banner`** saying it is a run diagnostic and not documentation. This is not decoration. A report reached from site search arrives with no context, and its section index is not there to explain it — the banner is the only thing standing between a reader and mistaking a gap analysis for guidance.
  5. **Weight:** `batch.md` is `1`; every other report is numbered from `2` in alphabetical slug order. The batch summary sorts first because it is the index to the rest.
  6. **Links between reports use rendered URLs**, not file paths. `[Foglight report](./foglight/)`, never `](./foglight.md)` — the `.md` form does not resolve once the site has built the page.
  7. **Every figure lives in exactly one place.** A count, share, weight or attribution appears once per report — in a table, or as the derived headline of the one list it counts. Prose never restates a number a table carries; `## Post-run corrections` is a table, not narrative; superseded claims are rewritten out, never left in present tense below a correction. See *Report format* in `SKILL.md` — this is the pipeline's largest recorded defect class, and every prose restatement is a drift seed even while it is still correct.

### Derived figures — state once, refer to by name

A report is dense with counts, shares, weights and ordinals, and every one of them is derived
from something on disk. The recurring failure is not computing them wrongly. It is computing one
correctly, restating it in three other sentences, and then updating only one of them in a later
round — leaving the report contradicting itself about a number it derived itself.

Four rules, all of which exist because a run breached them:

  1. **A figure that can change is stated once, in one place, with the command that re-derives it
     beside it.** Everywhere else refers to it by name — "the confidence distribution", "the
     evidence weight" — never by repeating the value. A number restated in prose is a number that
     will go stale, and prose is where these hide from re-derivation.
  2. **Never point at a list position.** "The eighth suggested action" breaks the moment the list
     grows, and these lists grow every round. Name the action instead.
  3. **A superseded figure is marked past-tense in place, never silently overwritten.** Write
     "the first three drafts gave the consumer repo a 100% share; that was true when written and is
     not now", with a pointer to the current value. Silent overwriting destroys the record of what
     a reviewer was shown, and a reader meeting an uncorrected earlier section takes a retracted
     figure as live.
  4. **Every published command must return the figure printed beside it — run it before shipping,
     verbatim, from the directory it is written to run from.** A command is the report's evidence,
     so one that does not reproduce is a broken claim, not a cosmetic slip. Two ways this has
     failed: a path filter that is inert against the local tool's output format
     (`grep -v '^\./<repo>/'` matches nothing where GNU grep emits no `./` prefix — exclude
     at the tool level with `--exclude-dir` instead), and a command whose scope does not match its
     figure (`find … -type f | wc -l` offered as an area count when it counts files).

The same applies across reports: a figure restated in `batch.md` is subject to all three rules,
and `batch.md` is the more widely read of the two. When a fix round changes a figure, the sweep
covers both files.

**When correcting derived figures, sweep rather than patch.** If more than two or three findings
in a round are stale figures in one document, re-derive every figure in it from disk and reconcile
every prose restatement, rather than fixing the reported lines. Patching the reported instances
regenerates the class — in one run it did so three rounds running, and a single sweep converged.

**Two edits are sweep triggers however small they look**, because both change something other
figures are derived from:

  * **Adding or removing a source on any page.** It changes the evidence base, and with it that
    page's evidence count, the run's unique-file count, the total weight, and every repo share. One
    added corroborating source silently invalidated five figures across four sections and
    `batch.md`. Re-derive them from the pages' `sources:` blocks — by script, not by hand — rather
    than adjusting the ones you remember.
  * **Adding or removing a cross-link.** Any table asserting where a page is linked from is now
    stale. A link added by a fix round left a prior-art row still naming one inbound page when
    there were two.

### Correction entries

Corrections are recorded as a **table** — finding, where, was, now, re-derive command — not as
narrative prose. Three consecutive runs put a defect inside the prose written to record a
correction, and none put one in a table.

Two rules specific to correction rows:

  * **Scope every command in a correction row to a section.** An unscoped search for text you just
    removed will always match the correction row that quotes it in its "was" column, so re-running
    it appears to disprove the fix it documents. This has recurred four times in a single run,
    including in the row written to record the previous instance. Write
    `awk '/^## 16\./,/^## 17\./' REPORT.md | grep -c '<removed text>'` → 0, not
    `grep -c '<removed text>' REPORT.md`.
  * **An absence claim in a correction row is still an absence claim.** Check it with a command
    before writing it. Absence claims fail adversarial review far more often than positive ones,
    and a report asserting "no such file exists" is one `ls` from being checked by whoever reads it.

**Before delivering a report, grep it against itself.** The run verifies pages mechanically and has
historically verified reports only by reading them, which is why every defect in some runs lands in
the report rather than on a page. At minimum: re-run every published command, re-derive every count
against its own enumeration, and check that each ordinal and section cross-reference still resolves
after any renumbering.

### Escaping template syntax — required

A report that quotes the site's template tag or template syntax **must escape it**, in whatever form the site adapter prescribes. Static-site generators typically expand template tags before markdown is rendered, so a template tag inside a backtick span is still executed, and an unclosed one fails the build outright.

This is not hypothetical. The *Unverified claims* section exists precisely to report which `unverified_marker` instances were emitted, so it names the marker by construction, and it names it **unclosed** because it is quoting an opening tag. Every report that has an unverified-claims section is a build failure waiting to happen unless the escape is applied.

Check before writing, with the command the site adapter gives (typically a search for the tag's opening delimiter, filtered for the escaped form). The same applies to any section-listing, banner or cross-reference template tag a report quotes rather than uses.

### What stays outside

**Sidecar proposals** (`${CLAUDE_SKILL_DIR}/references/refresh.md`) are written to `proposals_root`, outside the content tree, and are **not** published. A proposal is a draft of a real page, not a diagnostic about one. Publishing it would put a second copy of a page on the site with nothing marking which is current, which is the duplication failure the skill exists to avoid. The report links to a proposal by repository path, not by URL.

## Slugs

Directory and file names are slugged: lowercase, ASCII, dash-separated, punctuation stripped other than dashes.

  * Product slug — from `product.md`'s `name`. `Foglight Agent` → `foglight-agent`.
  * Journey slug — from the journey's `name`, with leading verbs kept. `Deploy a workload` → `deploy-a-workload`.
  * Variation slug — from the variation string verbatim. `cron-job` → `cron-job.md`.
  * Cross-product journey slug — the same rule as a journey slug. `Expose a service to users` → `expose-a-service-to-users`.

Collisions cannot occur, because `${CLAUDE_SKILL_DIR}/references/product-definition.md` rule 6 rejects duplicate journey names **across the whole definition** — every product's journeys and every cross-product journey share one namespace. If two journeys slug to the same value, that is an input defect: stop and report it rather than disambiguating silently.

Note that this guarantees only that no two journeys share a *name*. It says nothing about two journeys covering the same *ground*, which is expected between the two sections and is handled after authoring by `${CLAUDE_SKILL_DIR}/references/duplication.md`.

## Weight ordering

The site orders its navigation by a weight field within each section. **Assign it deterministically so re-runs do not reshuffle the navigation** — the principle is the skill's; the numeric table is the site adapter's. The principle has four parts:

  * Section furniture and bucket indexes take fixed values from the adapter's table, in Diátaxis order — tutorial, how-to, reference, explanation — which also happens to run from most to least hand-holding.
  * Journeys are weighted above every bucket, so a reader lands on the product, sees what it is, then how to learn it, use it, look things up in it and understand it, and only then the end-to-end paths through it.
  * **Journey order follows the product definition**, not alphabetical order — the author's ordering is meaningful and preserving it is worth more than sorted output. The same holds for variations within a journey and for the resolved cross-product journey list.
  * Products, pages inside a bucket, and reports other than the batch summary are numbered alphabetically by slug. Known latent churn: alphabetical numbering renumbers existing siblings when a new one lands ahead of them, and the structure reviewer reports the resulting Weight-only diffs.

## Frontmatter

Two blocks, with the generation notice as the first line of the body **below** them.

The notice must never precede the opening `---`. Most generators only parse front matter that starts on line 1, so a comment above it discards the title, the weight, and the entire `doc_journeys` block. See `${CLAUDE_SKILL_DIR}/references/authoring.md`.

The site's standard fields — title, summary, tags, weight, aliases — use **the site's own names and casing**, which the site adapter records (the examples below use one common convention, capitalised `Title`/`Summary`/`Tags`/`Weight`/`Aliases`; use whatever the adapter says). Skill-specific provenance is namespaced under a lowercase `doc_journeys` key so it cannot collide with a site or theme field now or later.

```markdown
---
Title: Forward logs to S3
Summary: End-to-end steps to get the Foglight agent forwarding logs to an S3 bucket for long-term archival.
Tags:
  - agent
  - logs
  - s3
Weight: 1
doc_journeys:
  product: Foglight
  journey: Archive telemetry
  variation: s3
  spine: how-to
  diataxis_type: how-to
  audience_tier: end-user
  confidence: high
  generated: true
  generated_at: 2026-07-28
  generated_by: claude-opus-5
  skill_version: 5
  content_hash: sha256:3f9a1c8e...
  sources:
    - repo: foglight-agent
      path: docs/forwarding.md
      repo_head: 9d4b71e2...
      relevance: high
      contributed: the forwarder configuration keys and the restart step
    - repo: foglight-agent
      path: README.md
      repo_head: 9d4b71e2...
      relevance: medium
      contributed: the agent version prerequisite
  prior_art:
    - path: docs/how-to/archiving-logs.md
      overlap: partial
---
<!-- Generated by doc-journeys from source repositories. Review before relying on it; see the Sources section. -->

## Before you start
...
```

### Field rules

| Field | Required | Notes |
| --- | --- | --- |
| title | Yes | Sentence case. Never repeat the product name in a page nested under that product, and **never repeat the journey name on a page nested under that journey** — see *Titles must be unique within a directory* below. |
| summary | Yes | One sentence, no trailing full stop omitted — write it as a sentence. Appears in section listings and search results. |
| tags | Yes | 3–8 lowercase terms drawn from the journey term set. These feed site search; generic terms (`platform`, `config`) dilute it and should be left out. |
| weight | Yes | Per the site adapter's table and the principle above. |
| aliases | No | Only when this page supersedes a known URL. Do not invent aliases. |
| `doc_journeys.product` | Pages under `products/` | The resolved `product_name`, identical across every page of a product's run. **Omitted entirely on cross-product journey pages**, which carry `products` instead. |
| `doc_journeys.products` | Cross-product journey pages | The products the journey crosses — in declared order where declared, in descending evidence weight where derived. Never emitted alongside `product` — a page has one or the other, so a consumer can tell the two kinds apart without parsing its path. |
| `doc_journeys.products_resolution` | Cross-product journey pages | `declared` or `derived`, per `${CLAUDE_SKILL_DIR}/references/source-discovery.md`. Without it a later run cannot tell whether the list above is a human's editorial decision — which it must preserve — or a computed result it should recompute, and the two demand opposite treatment. |
| `doc_journeys.products_inclusion` | Cross-product journey pages, when the list was derived | One entry per **included** product carrying `slug`, `confidence`, `share` and `attributed_by`. Excluded products live in the report, not in frontmatter: the page records what it is, and the report records what was considered. |
| `doc_journeys.cross_product` | Cross-product journey pages | Always `true` where present; omitted rather than set to `false` on product pages. Redundant with `products` by construction, and worth carrying anyway: it is the field a query filters on, and it survives someone later allowing a single-product entry in the list. |
| `doc_journeys.journey` | Journey pages only | The journey `name`. Present on both product journey and cross-product journey pages; omitted on product-level pages. |
| `doc_journeys.spine` | Journey pages only | The journey's resolved `spine` — `how-to`, `tutorial`, or `explanation`. Always equal to `diataxis_type` on the spine page itself; recorded separately because `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` counts coverage against the *declared* spine, and a page whose two values disagree is a defect worth being able to see. |
| `doc_journeys.routes_to` | Cross-product journey pages, when declared | The product journeys this one hands off to. Each entry carries `journey` and the `path` it resolved to, so a later run can detect a target that has since been renamed or removed. |
| `doc_journeys.variation` | Variation how-tos only | Omitted when the journey has no variations. |
| `doc_journeys.diataxis_type` | Yes | One of `tutorial`, `how-to`, `reference`, `explanation`. |
| `doc_journeys.audience_tier` | Yes | `end-user` for journey pages, `builder/maintainer` for product-level pages, per `${CLAUDE_SKILL_DIR}/references/audience-tagging.md`. |
| `doc_journeys.confidence` | Yes | `high` / `medium` / `low`, computed by the rubric in `${CLAUDE_SKILL_DIR}/references/source-discovery.md`. Never assigned by feel. |
| `doc_journeys.generated` | Yes | Always `true`. Records that the skill authored this page originally. It does **not** indicate whether a human has since edited it — `content_hash` is what answers that. |
| `doc_journeys.generated_at` | Yes | ISO date of the run that last wrote the page. |
| `doc_journeys.generated_by` | Yes | The model that authored it. Lets a later run force-refresh output produced by an older model. |
| `doc_journeys.skill_version` | Yes | Integer. Bump when the authoring rules change materially enough to warrant regenerating existing output. Currently `7` — `3` added the contact-point corroboration rule; `4` added strict provenance (prose-only sources, code as corroborator), the four Diátaxis buckets, product briefs, and the move to a dedicated output section; `5` added cross-product journeys, the declared `spine`, and global journey-name uniqueness; `6` added product attribution — `repos` on `product.md`, derivable `products`, and the inclusion-confidence rubric; `7` moved every site- and estate-specific rule into the consumer's `.doc-settings/` (no page content changed, so `7` does not by itself warrant a refresh). Bumping does not itself regenerate anything; it records which rules a page was written under, and lets a human force a refresh of output older than a given version. |
| `doc_journeys.content_hash` | Yes | `sha256:` of the page body as written, excluding frontmatter, after normalising trailing whitespace and ensuring a single trailing newline. **This is the human-edit detector** — the whole refresh flow depends on it. A page missing it is treated as human-edited and is never overwritten. |

Every row above applies to **content pages** — the pages authored from sources. Section furniture carries none of it: the top-level indexes under *Output root*, and the per-product bucket indexes under *Bucket sections* below, carry title, summary and weight alone. A bucket index with no `doc_journeys:` block is correct output, not a page that lost its provenance.
| `doc_journeys.sources` | Yes | Same files, same order, as the page's `## Sources` section. Never empty — a page with no sources should not have been written. Each entry carries `repo_head`, the source repo's HEAD SHA at generation time, so a later run can ask git exactly what changed. |
| `doc_journeys.corroborated_by` | When corroboration exists | Code, manifests, CRDs, schemas, Helm values and tests that **confirm** what a prose source claimed. These are not sources — nothing on the page may be written from them — but they are what lifts a page's confidence and what detects a prose source having gone stale. Same `repo`/`path`/`repo_head` shape as `sources`, with `confirms` naming the claim rather than `contributed`. See the strict provenance rule in `${CLAUDE_SKILL_DIR}/references/authoring.md`. |
| `doc_journeys.brief` | Product-level pages, when a brief exists | The brief's `source` and `captured` date, so a reader can see how old the product description is. Omitted entirely on an unbriefed product's navigation-only page. |
| `doc_journeys.prior_art` | When hits exist | Existing pages under `prior_art_roots` covering the same ground, with `overlap` of `full` or `partial`. |
| `doc_journeys.reviewed` | When a human signs off | `by`, `at`, and `at_content_hash`. Added by a human, never by the skill. When `at_content_hash` no longer matches `content_hash`, the page has been regenerated since review and is reported as needing re-review. |

The base skill's flat `product:` / `diataxis_type:` / `source_path:` / `journeys:` / `audience:` frontmatter is **superseded** by the block above and must not be emitted. `source_path` in particular has no meaning here — there is no single source document.

### Titles must be unique within a directory

A journey directory holds an `_index.md` titled with the journey name, plus its spine page. **The
spine page must not also be titled with the journey name.** The site renders its navigation from the
title field, so two pages sharing one produces a parent and child with the same label — the reader
cannot tell which is which, and neither page can tell them.

This is the same rule as the global journey-name uniqueness check in
`${CLAUDE_SKILL_DIR}/references/product-definition.md` rule 6, applied one level down. That check compares journeys
against each other and passes cleanly here, because there is only one journey; the collision is
between two pages *of* it.

The case bites hardest where it is least avoidable:

  * **A journey with `variations`** has no problem. Each variation page is titled for its variation
    — `Stateless`, `New team` — which is naturally distinct
  * **A journey with no variations** writes a single `<spine>.md`, and the obvious title for it is
    the journey name. This is the collision
  * **Every cross-product journey** is in the second category, because they carry no variations by
    construction. So this is not an edge case there, it is the default path

Title the spine page for **what it is within the journey**, not for the journey:

| Spine | Journey index title | Spine page title |
| --- | --- | --- |
| `explanation` (a route) | `Expose a service to users` | `The route` |
| `how-to` | `Roll back a release` | `The procedure`, or the concrete outcome — `Roll back to a previous version` |
| `tutorial` | `Your first deployment` | `The walkthrough` |

Keep the summary fully self-describing when you do this. A title like `The route` is unambiguous in the
navigation, where its parent is directly above it, and meaningless in a search result, where it is not —
so the summary has to carry the context the title has given up.

### A cross-product journey page

The same schema with `product` swapped for `products`, plus the routing block. Note the title is
the spine page's own, **not** the journey name, per the rule above:

```yaml
---
Title: The route
Summary: The stages of getting a service's telemetry from a running workload into a Foglight dashboard, in order, with the product that owns each one and a link to where it is documented.
Tags:
  - telemetry
  - agent
  - dashboard
Weight: 1
doc_journeys:
  products:
    - Foglight
    - Foglight Agent
  products_resolution: derived
  products_inclusion:
    - slug: foglight-agent
      confidence: high
      share: 41%
      attributed_by: repos
    - slug: foglight
      confidence: high
      share: 33%
      attributed_by: repos
  cross_product: true
  journey: Observe a service end to end
  spine: explanation
  diataxis_type: explanation
  audience_tier: end-user
  confidence: medium
  routes_to:
    - journey: Install the agent
      path: <preview_path>products/foglight-agent/install-the-agent/
    - journey: Archive telemetry
      path: <preview_path>cross-product-journeys/archive-telemetry/
  generated: true
  generated_at: 2026-07-29
  generated_by: claude-opus-5
  skill_version: 5
  content_hash: sha256:8b21d0f4...
  sources:
    - repo: <consumer repo>
      path: <prior_art_root>/how-to/observe-a-service.md
      repo_head: 74de7623...
      relevance: high
      contributed: the ordering of the install, forward, and dashboard steps
---
```

Note that `routes_to` may point at another **cross-product** journey, as the archive entry above does. Routing is between journeys, not between sections.

`products` carries product **names**, matching `product` on a single-product page; `products_inclusion[].slug` carries **slugs**, matching the directory names under `products/`. The two coexist deliberately — the names are what the page's prose and its index refer to, and the slugs are what a later run resolves paths and `repos` declarations against.

## Section index pages

`_index.md` files are navigation, not content. Keep them short and use the site's section-listing template tag (named in the site adapter) for listings, matching how the rest of the site does it.

**Product index** (`<product>/_index.md`) — an explanation-type page. What the product is, the problem it solves, owners from `product.md`, its features, then the journey list. The journey list is written explicitly rather than via the template tag, because each entry carries a one-line description the template tag cannot render:

```markdown
## Journeys

  * [Install the agent](./install-the-agent/) — get the Foglight agent running on a host and reporting in
  * [Archive telemetry](./archive-telemetry/) — forward logs to long-term storage outside Foglight
```

**Journey index** (`<product>/<journey>/_index.md`) — what the journey achieves, who performs it, prerequisites, and links to the pages beneath it. When the journey has variations, name what distinguishes them so the reader can pick:

```markdown
## Variations

  * [S3](./s3/) — forward to an S3 bucket; the common case
  * [Syslog](./syslog/) — forward to an existing syslog collector
```

**Cross-product section index** (`cross-product-journeys/_index.md`) — one paragraph saying what the section is for: goals that no single product satisfies, each page naming the products involved and linking to where each step is documented. Then the journey list, written explicitly like the product journey list so each entry can carry its one-line description:

```markdown
## Journeys

  * [Observe a service end to end](./observe-a-service-end-to-end/) — get a running service's telemetry onto a dashboard
  * [Archive telemetry](./archive-telemetry/) — get logs out of Foglight and into long-term storage
```

**Cross-product journey index** (`cross-product-journeys/<journey>/_index.md`) — what the reader is trying to achieve, which products it crosses and why each is involved, prerequisites, and a link to the spine page. It must name the products in prose, not only in frontmatter: a reader arriving here is asking *which products do I need*, and that is the question this page exists to answer.

**Bucket sections** (`tutorial/_index.md`, `how-to/_index.md`, `reference/_index.md`, `explanation/_index.md`) — a single orienting sentence, then the site's section-listing template tag, in the form the site adapter gives, **ordered by weight** — these sections have meaningful Weight ordering and the listing should honour it.

Bucket sections are **section furniture**, like the top-level indexes above: created when a bucket first has pages, then left alone. They carry title, summary and weight and **no `doc_journeys:` block** — no `content_hash`, no `sources`, no `confidence`. That is deliberate, not an omission. A bucket index has no evidence behind it: its body is one orienting sentence and a template tag, its child list is computed by the site at render time, and there is nothing a refresh could recompute from changed sources. Emitting the block would mean inventing a `sources` list for a page whose own rule is that sources are never empty.

The consequence is that `${CLAUDE_SKILL_DIR}/references/refresh.md`'s missing-`content_hash` rule — absent hash means treat as human-edited and never overwrite — reads as freezing these pages. It is not a defect and needs no repair. Their content is fixed by this template rather than by any source, so a refresh has no reason to touch them. The one case that does need a human is a change to the template itself: if the orienting sentence or the template tag call changes here, existing bucket indexes will not pick it up on any refresh and must be updated deliberately. Say so in the run report when it happens rather than working around the rule.

## Re-runs

Re-running for the same product does **not** blindly overwrite its pages. Which pages change, and how, is decided per page by `${CLAUDE_SKILL_DIR}/references/refresh.md` — load it whenever the target already has pages under `products/<product-slug>/` or under `cross-product-journeys/<journey-slug>/`, in any mode. Cross-product pages are ordinary generated pages: they carry a `content_hash`, they are protected from overwrite once a human edits them, and they are left untouched when their evidence has not changed.

The short version:

  * A page whose evidence has not changed is **left untouched**. Not rewritten, not reformatted, not re-dated. Regeneration is non-deterministic, so rewriting unchanged pages produces churn that hides the real changes.
  * A page a human has edited — detected by comparing the on-disk body against `content_hash`, *not* by the presence of `generated: true` — is **never overwritten**, by any mode or flag. If its evidence changed, the new version goes to a sidecar proposal under `proposals_root` for a human to merge.
  * A page whose evidence changed and which no human has touched is **regenerated minimally**: start from the existing page, change only what the evidence requires.
  * Journey directories no longer backed by the product definition are **orphaned**, reported, and never deleted.

Getting this wrong destroys reviewed content, which is the most valuable material on the site. When uncertain whether a page is human-owned, treat it as human-owned.
