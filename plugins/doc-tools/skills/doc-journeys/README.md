# doc-journeys

Consolidates product and journey documentation into a static documentation site, drawing on documentation scattered across the source repositories that sit beside the repository being documented.

The skill is **consumer-agnostic**: everything about a particular repository, site and estate lives in that repository's `.doc-settings/` directory (see *Consumer setup*), and the skill stops if it cannot find it.

It is a **reorganiser, not an author**. Given a product definition — products, their user journeys, and journeys that cross several products — it discovers relevant material across many repositories and rewrites it into a coherent, Diátaxis-typed set of pages. Prose, structure and framing are written fresh; **facts are never invented and never derived from code**. Every claim on a page traces to prose that already existed somewhere in the estate, or is explicitly marked unverified. Where a capability is documented only in source code, the skill writes nothing and reports the gap.

---

## Quick start

The skill ships in the `doc-tools` Claude Code plugin (see *Installing* in the plugin README), so the
slash form is `/doc-tools:doc-journeys <request>`. In prose:

```
Use doc-journeys in plan mode for Foglight.
```

Plan mode runs discovery and prints the page set it would write — paths, types, evidence counts, confidence — without writing anything. Start here for any product you have not run before.

When the plan looks right:

```
Use doc-journeys to generate documentation for Foglight.
```

The skill confirms the resolved product, journeys, source repos, and the full page plan before writing.

Other modes: `refresh` updates pages a previous run wrote, touching only what its evidence requires; `audit` is a legacy mode that classifies existing markdown in a single repo without writing anything.

---

## Consumer setup

The repository being documented — the **consumer** — owns two directories at its root:

  * **`.doc-settings/`** — everything repo-, site- and estate-specific. `settings.md` carries every scalar the skill pins (output root, reports and proposals directories, write locations, base branch, source root rule, build command, marker syntax, …) and names three adapter files: an **estate adapter** (source-root derivation, repo scope, prior art, known contact-point traps), a **site adapter** (section furniture, frontmatter conventions, template tags, Weight table, build notes) and an **authorisations** file (what an unattended run may do, granted by whom, when). The required keys are listed in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/settings.md`.
  * **`product-definition/`** — the products and journeys to document, below.

Prerequisites the skill checks or assumes: the site's dependency directory (`node_modules` or equivalent) is present at the consumer root; the `worktree_dir` is gitignored; `base_branch` exists.

## Setting up the product definition

The skill reads `<consumer root>/product-definition/`. A human owns this folder; the skill only ever adds to it — and only a declaration the request names, confirmed with you before it is written.

```
product-definition/
  catalogue.md                 # optional: batch order and exclusions
  products/
    foglight/
      product.md               # required — the declaration
      brief.md                 # optional — the product owner's description
      journeys/*.md
      weightings.md            # optional
    foglight-agent/
      ...
  cross-product-journeys/      # journeys owned by no single product
    observe-a-service-end-to-end.md
```

`product.md` frontmatter — `name` (required); `owners`, `features`, `repos`, `brief` (optional). `features` feeds the discovery term set and `repos` maps repositories to products, so both are worth declaring even though they are optional.

`journeys/<slug>.md` frontmatter — `name` (required); `description`, `users`, `feature`, `spine`, `variations` (optional). The `spine` is the Diátaxis type of the journey's end-to-end page: `how-to` (the default), `tutorial`, or `explanation`.

Full schemas are in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/product-definition.md`.

### The brief is the highest-value input

A product here is an umbrella over several repositories, and no repository says what the product *is* — each explains its own component. A `brief.md`, written by the product owner, is the only source that can answer "what is this and is it for me?". Without one the product page is navigation-only, because inventing a description is exactly what the grounding contract forbids.

### Journey prose is load-bearing

The search terms used to find evidence across the source repositories are derived largely from a journey's `description` and body prose. A journey defined by name alone will find little and produce low-confidence pages or none. Journeys worth writing from name the concrete outcome, list their `variations`, and describe the systems, tools, and resources involved.

### Cross-product journeys

A journey a reader completes by using several products together lives in `cross-product-journeys/`, owned by none of them. Its page **routes rather than restates**: it names each stage, says which product owns it, and links to where that stage is documented. It may declare which products it crosses, or omit the list and have discovery derive it from where the evidence actually lives — the report then shows each product's inclusion confidence so you can overrule the derivation.

---

## How discovery works

There are thousands of markdown files and far more source files across the estate. The skill does not read them all. It runs a five-pass funnel per journey:

  1. **Term set** — build search terms from the product and journey definitions plus the brief, expanded with the technologies they imply. Capped at 40 terms.
  2. **Path signal** — match terms against file and directory paths. In these monorepos a directory named after a component usually is that component, so path hits rank above content hits.
  3. **Content signal** — grep for terms, counting *distinct* terms matched per file rather than total matches.
  4. **Rank and shortlist** — score candidates, take the top 25 (hard cap 40), and report anything dropped at the cap.
  5. **Read** — read the shortlist in full, following one hop outward to more authoritative sources.

Every git repository under the source root (by default, the parent of the consumer checkout) is in scope, and so is the consumer repository itself — its existing documentation is consolidated into the output. Only the skill's own output trees are excluded, so a run can never cite itself. Existing pages under the configured prior-art roots additionally get a **prior-art pass** that records overlap between them and the pages being written.

### Prose is the only permitted origin

Evidence splits into two roles:

  * **Sources** — prose written for humans: READMEs, `docs/`, runbooks, the existing site, product briefs. Pages may only be written from these.
  * **Corroborators** — code, manifests, CRDs, schemas, Helm values, tests. They confirm a prose claim (raising confidence) and detect stale prose (raising a conflict), but no page is ever written from them.

When prose and code disagree, the skill writes neither and reports the conflict. Material that exists only in code is reported as **undocumented surface area** — a concrete list of what a human needs to go and write.

Details in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` and `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/authoring.md`.

---

## Confidence scoring

Every authored page carries a confidence score. It is **computed from the evidence set, not judged** — number and relevance of evidence files, whether any claim was corroborated by deployed config or a schema, whether sources agreed, and whether every claim traces to a source. The rubric is in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md`.

  * **high** — well-evidenced, multiple relevant sources, at least one machine-corroborated claim
  * **medium** — grounded but thin
  * **low** — minimal evidence, or containing inferred steps

Low-confidence pages are still written, but carry a visible review banner. **A journey's confidence is the lowest of its pages, not the average.**

Pages that cannot be grounded at all are **not written**. There are no stubs — a hollow page looks like coverage and is worse than a reported gap.

---

## What you get

Everything lands under a single output section of the site (`output_root` in settings), which renders and previews like any other section:

```
<output_root>/
  products/
    foglight/
      _index.md                      product overview + journey index
      tutorial/  how-to/  reference/  explanation/    the four Diátaxis buckets, when evidenced
      archive-telemetry/
        _index.md                    journey overview + prerequisites
        s3.md                        one page per variation, or one spine page
        syslog.md
  cross-product-journeys/
    observe-a-service-end-to-end/
      _index.md                      which products it crosses and why
      explanation.md                 the route — links out, never restates
  reports/
    foglight.md                      one run diagnostic per run
```

Reports render beside the output they describe, with working links, and open with a banner saying they are run diagnostics rather than documentation.

The two most useful report sections are **Undocumented surface area** (capabilities only the code describes — each entry is a writing task) and **Journeys not covered** (what found no evidence, and the terms that failed). Both tell you where to point the skill, or a human, next.

---

## Keeping documentation up to date

Sources move on. Run the skill again:

```
Use doc-journeys to refresh where sources changed.
```

Refresh is **not** "generate again and overwrite". Two things make that harmful, and the design works around both:

  * **Generation is non-deterministic.** Regenerating an unchanged page produces differently-worded prose saying the same thing. Across a product that yields a huge diff with a few real changes buried in it, which nobody can review.
  * **Human review is the most valuable input the docs get.** A page someone corrected is better than anything the skill produces.

So the rule is: **change a page only when its evidence changed, and change only what the evidence requires.**

### How it decides

Every generated page records a `content_hash` of its body and the HEAD SHA of each source repo it cited. On refresh:

  * **Did a human edit it?** Hash the page on disk and compare. This is the only reliable signal — `generated: true` says who wrote it originally, not who has touched it since.
  * **Did the evidence change?** Ask git what changed in each cited repo since the recorded SHA. Discovery also re-runs in full, because the interesting change a month later is usually *new* material that did not exist before, not edits to files already cited.

| Human edited | Evidence changed | Result |
| --- | --- | --- |
| No | No | Left untouched |
| No | Yes | Regenerated minimally, in place |
| Yes | No | Left untouched |
| Yes | Yes | Page untouched; new version written as a **proposal** for you to merge |

**No invocation overwrites a human-edited page** — not `refresh`, not `force`. If you want a page regenerated from scratch, delete it and re-run. That is deliberately an explicit act.

Proposals land in `proposals_root` (settings), outside the content tree and unpublished — a proposal is a draft of a real page, and publishing it would leave two copies on the site with nothing marking which is current. The report explains what changed in the evidence and what the proposal would change on the page.

### Minimal-diff regeneration

When a skill-owned page does need regenerating, the skill starts from the existing page and changes only what the new evidence requires — keeping existing wording wherever the facts are unchanged. A diff that touches every paragraph because one default value changed is treated as a failure, not thoroughness.

### Review tracking

Add a `reviewed:` block to a page's frontmatter when you sign it off:

```yaml
  reviewed:
    by: a.reviewer
    at: 2026-08-04
    at_content_hash: sha256:3f9a1c8e...
```

The skill never writes this — you do. If the page is later regenerated, `at_content_hash` stops matching and the refresh report lists the page under **needs re-review**, so approval never silently carries over to content nobody has read.

### New model, same sources

For the case where nothing changed but you want the output redone by a better model:

```
Use doc-journeys to refresh Foglight, force.
```

Force regenerates skill-owned pages even where evidence is unchanged. It still never touches human-edited pages. Each page records `generated_by` and `skill_version`, so you can see what produced what.

### A refresh that changes nothing

...reports one line and writes nothing. That is a successful run.

---

## Safety properties

  * Source repositories are never written to.
  * Only the configured `output_root` (pages and reports) and `proposals_root` are written. Everything under `prior_art_roots` is read-only, and originals are never modified even when their content is consolidated forward.
  * The one bounded exception: a declaration the request names may be added under `product-definition/`, after its exact contents are confirmed with you. Nothing there is ever deleted, renamed, or rewritten.
  * Human-edited pages are never overwritten, in any mode, with any flag. Detected by content hash, not by a marker a human edit would leave in place.
  * Pages whose evidence has not changed are never rewritten, so diffs stay reviewable.
  * Nothing is ever deleted. Orphaned directories from a previous run are reported for a human to remove.
  * Unverified commands are never printed. If the command was not found, the page says so rather than guessing.
  * Contact points (Slack channels, group handles, DLs) are published only when corroborated in a source repository — a citation alone cannot prove something outside the repositories still exists.

---

## Known limitations

  * **Grounded is not correct.** A cited fact means the source said it, not that it is true. Sources may themselves be stale.
  * **Nothing is executed.** No command on a generated page has been run.
  * **Undocumented knowledge is invisible.** Anything known only to a team and not written down anywhere in the repositories will be absent, and the skill cannot distinguish "not documented" from "does not exist".
  * **The site may hide the output section.** A landing-page redirect can leave the output reachable only from the sidebar; the site adapter records whether this site has one. The skill flags it rather than editing the redirect; link reviewers to the configured `preview_path` directly.

---

## Reference files

| File | Role |
| --- | --- |
| `settings.md` | Locating the consumer's `.doc-settings/`, the four path roles, required keys, stop-if-absent |
| `product-definition.md` | Product, journey and cross-product-journey input schemas, briefs, the batch catalogue |
| `source-discovery.md` | Multi-repo scope, the five-pass funnel, relevance, confidence, and product attribution |
| `authoring.md` | Page set per product and journey, strict provenance, the grounding contract, citation format, voice |
| `output.md` | Output structure, slugs, Weight determinism, the provenance schema, the reports section (site specifics come from the consumer's site adapter) |
| `refresh.md` | Human-edit and evidence-change detection, the per-page decision table, minimal-diff regeneration, proposals |
| `compass.md`, `types.md`, `decision-rubric.md` | Diátaxis type selection and voice |
| `topic-coverage.md` | Journey topic extraction — the spine page's outline, and the completeness check |
| `audience-tagging.md` | Audience tier and labels |
| `gap-analysis.md` | Per-journey coverage verdicts, bucket assessment, product inclusion |
| `quality-flags.md` | `hollow` and `stale-marker` self-checks on authored output |
| `duplication.md` | Overlap between authored pages, against prior art, and routes that restate their destinations |
| `weightings.md` | Optional ideal-vs-actual content weighting |
| `journey-matching.md` | Matching existing site pages to journeys in the prior-art pass |
| `examples.md` | Worked exemplars (fictional product, *Foglight*) |
