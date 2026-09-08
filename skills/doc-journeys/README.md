# doc-journeys

Consolidates product and journey documentation that already exists — scattered across many
source repositories — into a coherent, Diátaxis-typed set of pages in a static documentation
site.

It is a **reorganiser, not an author**. Given a product definition (products, their user
journeys, and journeys that cross several products) it discovers relevant material across the
source repositories and rewrites it into pages. Prose, structure and framing are written fresh;
**facts are never invented and never derived from code**. Every claim on a page traces to prose
that already existed somewhere in the source repositories, or is explicitly marked unverified.
Where a capability is documented only in source code, the skill writes nothing and reports the
gap.

The skill is **consumer-agnostic**. It knows how to discover evidence, author pages, score
confidence and refresh safely. It does not know where your site is, which repositories are
sources, how the site builds or what an unattended run may do — all of that lives in the
repository being documented, under `.doc-settings/`, and the skill stops if it cannot find it.
See [The consumer contract](#the-consumer-contract).

Install per the [skills README](../README.md); `doc-run` (the unattended, reviewed wrapper) is
documented in [`../doc-run/README.md`](../doc-run/README.md). Every example below uses
**Foglight**, the fictional observability product from
[`references/examples.md`](references/examples.md).

---

## Quick start

The slash form is `/doc-journeys <request>`; in prose:

```
Use doc-journeys in plan mode for Foglight.
```

Plan mode runs discovery and prints the page set it would write — paths, types, evidence counts,
confidence — without writing anything. Start here for any product you have not run before.

When the plan looks right:

```
Use doc-journeys to generate documentation for Foglight.
```

The skill confirms the resolved product, journeys, source repositories and the full page plan
before writing. Later:

```
Use doc-journeys to refresh where sources changed.
```

Modes: **`author`** (the default) writes pages; **`refresh`** updates pages a previous run wrote,
touching only what its evidence requires; **`plan`** writes nothing; **`audit`** is a legacy mode
that classifies existing markdown in a single repository without writing, and is the only mode
that reads no settings. `author` and `refresh` converge — an `author` run over a product that
already has pages applies the refresh rules rather than overwriting.

---

## The consumer contract

The repository being documented — the **consumer** — owns two directories at its root. Nothing
about it is hard-coded in the skill.

### `.doc-settings/`

`settings.md` carries, in its frontmatter, every scalar the pipeline pins: the output root, the
reports and proposals directories, the plan file, the prior-art roots, the write locations, the
base branch and worktree directory, the source-root rule and exclusions, the build command, the
preview path, the style guide and the site's marker syntax. It also names three adapter files
that sit beside it:

| Adapter | Named by | What it carries |
| --- | --- | --- |
| **estate adapter** | `source_discovery` | how the source root is derived, which repositories are in scope, where prior art lives, files every repository carries, term-expansion vocabulary, known contact-point traps |
| **site adapter** | `output` | the output section and its furniture, frontmatter field names, template tags and marker syntax, the Weight table, how the site builds, navigation quirks that hide the output |
| **authorisations** | `authorisations` | what an unattended run may do in this repository — granted by whom, when |

The required keys are listed in [`references/settings.md`](references/settings.md). A key that
is present but empty counts as absent, and a missing key stops the run with the key named.
[`assets/doc-settings/`](assets/doc-settings/) is a documented starter: copy it to
`<consumer root>/.doc-settings/` and edit every value marked `EDIT`.

### `product-definition/`

The products and journeys to document. A human owns this directory; the skill only ever adds to
it, and only a declaration the request names, confirmed before it is written.

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

`product.md` frontmatter — `name` (required); `owners`, `features`, `repos`, `brief` (optional).
`features` feeds the discovery term set and `repos` maps repositories to products, so both are
worth declaring. `journeys/<slug>.md` frontmatter — `name` (required); `description`, `users`,
`feature`, `spine`, `variations` (optional). The `spine` is the Diátaxis type of the journey's
end-to-end page: `how-to` (the default), `tutorial` or `explanation`. Full schemas in
[`references/product-definition.md`](references/product-definition.md).

Three things about the definition matter more than the schema:

  * **The brief is the highest-value input.** A product is an umbrella over several
    repositories, and no repository says what the product *is* — each explains its own
    component. A `brief.md`, written by the product owner, is the only source that can answer
    "what is this and is it for me?". Without one the product page is navigation-only, because
    inventing a description is exactly what the grounding contract forbids.
  * **Journey prose is load-bearing.** Discovery terms come largely from a journey's
    `description` and body. A journey defined by name alone finds little and produces
    low-confidence pages or none. Name the outcome, list the `variations`, describe the systems
    and tools involved.
  * **Cross-product journeys route rather than restate.** A journey completed by using several
    products together lives in `cross-product-journeys/`, owned by none of them. Its page names
    each stage, says which product owns it and links to where that stage is documented. It may
    declare which products it crosses, or omit the list and have discovery derive it — the
    report then shows each product's inclusion confidence so a human can overrule the
    derivation.

### The four path roles

Every reference file and every agent prompt uses the same four roots:

| Role | Meaning | Holds |
| --- | --- | --- |
| **tools root** | the directory holding the `doc-journeys` and `doc-run` skill directories | `SKILL.md`, `references/`, the agent prompt files |
| **consumer root** | the **main** checkout of the consumer repository | `.doc-settings/` and anything gitignored — the site's dependency directory, most importantly |
| **repo root** | the checkout content is read from and written to — a run worktree, in an orchestrated run | `product-definition/`, every path in `settings.md` |
| **source root** | per `source_root` in `settings.md` | the repositories discovery scans |

In a plain checkout the consumer root and repo root are the same directory. In a `doc-run`
worktree they differ, which is why the build command may need to name `<consumer root>` and why
the source-root rule `parent-of-consumer-root` is resolved from the main checkout, never from
the working directory.

### Before your first run

Work through the checklist in the starter's
[`assets/doc-settings/README.md`](assets/doc-settings/README.md#before-your-first-run), then run
plan mode.

---

## How discovery works

Source repositories are large and were not written to be documentation inputs. The skill does
not read them all. Per journey it runs a five-pass funnel:

  1. **Term set** — from the product and journey definitions plus the brief, expanded with the
     technologies they imply and the estate adapter's vocabulary. Capped at 40 terms.
  2. **Path signal** — match terms against file and directory paths. A directory named after a
     component usually is that component, so path hits rank above content hits.
  3. **Content signal** — search for terms, counting *distinct* terms matched per file rather than
     total matches.
  4. **Rank and shortlist** — score candidates, take the top 25 (hard cap 40), collapse
     directories of near-identical per-instance files, and report anything dropped at the cap.
  5. **Read** — read the shortlist in full, following one hop outward to more authoritative
     sources.

Every git repository directly under the source root is in scope, minus `source_exclude_repos`.
The consumer repository is normally one of them — its existing documentation is what the output
consolidates — with only the pipeline's own output trees (`source_exclude_paths`) excluded, so a
run can never cite itself. Pages under `prior_art_roots` additionally get a **prior-art pass**
that records overlap between them and the pages being written; they are cited, never edited.

### Prose is the only permitted origin

Evidence splits into two roles:

  * **Sources** — prose written for humans: READMEs, `docs/`, runbooks, the existing site,
    product briefs. Pages may only be written from these.
  * **Corroborators** — code, manifests, schemas, charts, tests. They confirm a prose claim
    (raising confidence) and detect stale prose (raising a conflict), but no page is ever
    written from them.

When prose and code disagree, the skill writes neither and reports the conflict. Material that
exists only in code is reported as **undocumented surface area** — a concrete list of what a
human needs to go and write. Details in
[`references/source-discovery.md`](references/source-discovery.md) and
[`references/authoring.md`](references/authoring.md).

### Confidence is computed, not judged

Every page carries a confidence score derived from its evidence set — how many relevant sources,
whether any claim was corroborated by a manifest or schema, whether sources agreed, whether every
claim traces. **high** is well-evidenced with at least one machine-corroborated claim; **medium**
is grounded but thin; **low** is minimal evidence or an inferred step, and the page carries a
visible review banner. A journey's confidence is the lowest of its pages, not the average. Pages
that cannot be grounded at all are not written — there are no stubs, because a hollow page looks
like coverage and is worse than a reported gap.

---

## What it writes, and where

Everything lands under the one section of the site named by `output_root`, inside the content
tree so it renders and previews like any other section:

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

Each page carries the site's own frontmatter fields (named in the site adapter) plus a
namespaced `doc_journeys:` block: product, journey, type, audience, confidence, the model and
skill version that wrote it, a `content_hash` of the body, and every source with the repository
HEAD it was read at. Weights are assigned deterministically so re-runs do not reshuffle the
navigation.

**Reports** render beside the pages, open with the site's `report_banner` saying they are run
diagnostics rather than documentation, and publish wherever the pages do. The two most useful
sections are *Undocumented surface area* (capabilities only the code describes — each entry is a
writing task) and *Journeys not covered* (what found no evidence, and the terms that failed).

**Sidecar proposals** go to `proposals_root`, outside the content tree and unpublished — a
proposal is a draft of a real page a human has edited, and publishing it would leave two copies
on the site with nothing marking which is current.

**Declarations** are the one write outside `output_root` and `proposals_root`: where the
request names a product or journey the definition does not declare, the skill drafts it, prints
it in full at the confirmation gate, and writes it under `product-definition/` before any page.
Existing declarations and briefs are never modified. The consumer's authorisations file
records that an unattended run may expand the definition this way; strike that grant there
if you want every declaration written by a human.

---

## Keeping documentation up to date

Refresh is **not** "generate again and overwrite". Generation is non-deterministic, so
regenerating an unchanged page yields differently-worded prose saying the same thing — a huge
diff with the real changes buried in it. And a page a human corrected is better than anything
the skill produces. So the rule is: **change a page only when its evidence changed, and change
only what the evidence requires.**

Every page records a `content_hash` of its body and the HEAD of each repository it cited. On
refresh the skill hashes the page on disk to detect a human edit (this is the only reliable
signal — `generated: true` says who wrote it originally, not who has touched it since), asks git
what changed in each cited repository, and re-runs discovery in full to catch material that did
not exist before.

| Human edited | Evidence changed | Result |
| --- | --- | --- |
| No | No | Left untouched |
| No | Yes | Regenerated minimally, in place |
| Yes | No | Left untouched |
| Yes | Yes | Page untouched; new version written as a **proposal** for a human to merge |

**No invocation overwrites a human-edited page** — not `refresh`, not `force`. To regenerate a
page from scratch, delete it and re-run; that is deliberately an explicit human act. A refresh
that changes nothing reports one line and writes nothing, and that is a successful run.

Two refinements: `force` (`Use doc-journeys to refresh Foglight, force.`) regenerates
skill-owned pages even where evidence is unchanged — for a better model, say — and still never
touches a human-edited page. A `reviewed:` block a human adds to a page's frontmatter
(`by`, `at`, `at_content_hash`) is never written by the skill; when the hash stops matching, the
report lists the page under *needs re-review*, so sign-off never silently carries over to
content nobody has read. Details in [`references/refresh.md`](references/refresh.md).

---

## What it never does

  * **Author from scratch.** No claim originates in the skill. No stubs, no placeholder pages,
    no unverified commands — an unverified command is never printed at all.
  * **Write from code.** Code, manifests, schemas and tests corroborate; nothing is written from
    them. Material documented only in code is reported.
  * **Edit a source repository**, including the consumer repository's existing documentation
    under `prior_art_roots`. Originals are never modified even when their content is
    consolidated forward.
  * **Write outside `write_locations`** — `output_root` (pages and reports), `proposals_root`,
    and the bounded declaration writes under `product-definition/` described above.
  * **Overwrite a human-edited page**, in any mode, with any flag.
  * **Rewrite a page whose evidence did not change**, so diffs stay reviewable.
  * **Delete anything.** Orphaned directories from a previous run are reported for a human to
    remove.
  * **Publish an uncorroborated contact point.** Chat channels, group handles and distribution
    lists name things outside the repositories; a citation alone cannot prove one still exists.
  * **Push or open a pull request.** Output lands in the working tree; what happens next is the
    consumer's decision.

## Known limitations

  * **Grounded is not correct.** A cited fact means the source said it, not that it is true.
  * **Nothing is executed.** No command on a generated page has been run.
  * **Undocumented knowledge is invisible.** Anything known only to a team and not written down
    is absent, and the skill cannot distinguish "not documented" from "does not exist".
  * **The site may hide the output section.** A landing-page redirect can leave it reachable only
    from the sidebar; the site adapter records whether this site has one, and reviewers are sent
    to `preview_path` directly rather than to the landing page.

---

## Reference files

| File | Role |
| --- | --- |
| [`settings.md`](references/settings.md) | Locating `.doc-settings/`, the four path roles, required keys, stop-if-absent |
| [`product-definition.md`](references/product-definition.md) | Product, journey and cross-product-journey schemas, briefs, the batch catalogue, declaring from a run |
| [`source-discovery.md`](references/source-discovery.md) | Multi-repo scope, the five-pass funnel, relevance, confidence, product attribution |
| [`authoring.md`](references/authoring.md) | Page set per product and journey, strict provenance, the grounding contract, citations, voice |
| [`output.md`](references/output.md) | Output structure, slugs, Weight determinism, the provenance schema, the reports section |
| [`refresh.md`](references/refresh.md) | Human-edit and evidence-change detection, the decision table, minimal-diff regeneration, proposals |
| [`compass.md`](references/compass.md), [`types.md`](references/types.md), [`decision-rubric.md`](references/decision-rubric.md) | Diátaxis type selection and voice |
| [`topic-coverage.md`](references/topic-coverage.md) | Journey topic extraction — the spine page's outline, and the completeness check |
| [`audience-tagging.md`](references/audience-tagging.md) | Audience tier and labels |
| [`gap-analysis.md`](references/gap-analysis.md) | Per-journey coverage verdicts, bucket assessment, product inclusion |
| [`quality-flags.md`](references/quality-flags.md) | `hollow` and `stale-marker` self-checks on authored output |
| [`duplication.md`](references/duplication.md) | Overlap between authored pages, against prior art, and routes that restate their destinations |
| [`suggested-actions.md`](references/suggested-actions.md) | The report's prioritised action list |
| [`weightings.md`](references/weightings.md) | Optional ideal-vs-actual content weighting |
| [`journey-matching.md`](references/journey-matching.md) | Matching existing site pages to journeys in the prior-art pass |
| [`examples.md`](references/examples.md) | Worked exemplars (fictional product, *Foglight*) |
| [`assets/doc-settings/`](assets/doc-settings/) | Starter `.doc-settings/` for a new consumer, with the pre-run checklist |
