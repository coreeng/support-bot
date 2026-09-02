---
name: doc-journeys
description: Use when consolidating, generating or refreshing product and journey documentation in a static documentation site from documentation scattered across many source repositories. Reads the consumer repository's `.doc-settings/` and product definition, discovers relevant material across the source repos, and rewrites it into Diátaxis-typed pages under the configured output root, never authoring from scratch — every claim traces to prose that already existed, code and manifests corroborate but are never written from, and material that exists only as code is reported as a gap. Journeys spanning several products are published to their own section, routing readers to the product documentation rather than restating it. Every page carries a computed confidence score. Also refreshes docs it wrote previously — detecting whether sources actually changed, protecting human-edited pages, regenerating minimally — plus a legacy `audit` mode that classifies existing documentation in a single repo without writing.
license: Apache-2.0
---

# doc-journeys

## What this skill does

Given a product and its user journeys, this skill **consolidates documentation that already exists** — scattered across many repositories — into a coherent, Diátaxis-typed set of pages in the consumer's documentation site.

The skill is **consumer-agnostic**. Everything about a particular repository, site and estate — where output goes, which repositories are sources, how the site builds, what has been authorised — lives in that repository's `.doc-settings/` directory and is read per `${CLAUDE_SKILL_DIR}/references/settings.md`. Nothing in this skill names a real repository, site or team.

It is a reorganiser, not an author. Prose, structure and framing are written fresh; **claims are never invented and never derived from code**. Where a capability is documented only in source, the skill writes nothing and reports the gap.

The flow is:

1. Resolve the products, their journeys, and any cross-product journeys from a product definition.
2. For each journey, discover relevant source material across all source repos — code, manifests, charts, schemas, and READMEs. Discovery is never narrowed to the declaring product's repositories.
3. Author a Diátaxis-typed page set for each product, each of its journeys, and each cross-product journey, grounded in that evidence.
4. Write it into the site with correct frontmatter, section indexes, and navigation ordering.
5. Report what was written, what was not, and how well-grounded each page is.

**The core constraint:** every fact on an authored page traces to a cited source file, or is explicitly marked unverified. Prose and structure are authored; facts are not invented. This is the whole basis on which the output can be trusted, and `${CLAUDE_SKILL_DIR}/references/authoring.md` specifies it in detail.

Source repositories are **read-only**. The skill writes documentation to exactly one directory — see "Output" below — plus sidecar proposals and, under the narrow conditions in the Guardrails, a confirmed declaration under `product-definition/`.

## Documentation model

The skill operates on a small, explicit model of what good documentation is. Every rule maps to one of these principles.

1. **User-focused** — every page targets an intended user. Audience is assigned per `${CLAUDE_SKILL_DIR}/references/audience-tagging.md`.

2. **Two audience tiers**:
   - **Product-level documentation** (reference, explanation) aimed at the product's builders and maintainers — anyone who extends, operates, or repairs it.
   - **Journey-level documentation** (one end-to-end page per journey) aimed at end-users — the people who consume the product through its supported journeys.

3. **Journey-level coverage** — every journey should have at least one end-to-end page. In the base audit skill a missing how-to was the only high-severity finding; in this skill, writing that page is the primary deliverable.

   Its Diátaxis type is the journey's declared **spine** — `how-to` by default, `tutorial` where the journey is a newcomer's guided first pass, `explanation` where the reader's problem is not knowing where to start. Coverage is judged against the declared spine, not against how-to. See *Choosing a spine* in `${CLAUDE_SKILL_DIR}/references/authoring.md`.

4. **Single-type focus** — each page is exactly one of the four Diátaxis types. Authored pages are single-type by construction; if a draft carries two intents, it becomes two pages.

5. **Two journey scopes.** A journey either belongs to exactly one product or crosses several. A journey's *scope* is declared, never inferred; *which* products a cross-product journey crosses may be derived by discovery and is reported with a per-product inclusion confidence. Cross-product journeys are published to their own top-level section, where their job is to **route** — name each stage, say which product owns it, and link to where it is documented. They do not restate what the product journeys already cover. The two journey lists overlap by design; the split is what keeps the overlap from becoming competing documentation.

**What the skill does NOT assert:**

- That every product must carry all of reference + explanation + how-to. Product-level sections are authored only where evidence supports them.
- Factual correctness. Grounding a fact in a source file means the source said it, not that it is true.
- That any command on an authored page has been executed. Nothing is run.

## Reference materials

Reference files under `${CLAUDE_SKILL_DIR}/references/` hold the load-bearing logic. You MUST consult them rather than reinvent their rules.

**Load order for `author` mode:**

- `${CLAUDE_SKILL_DIR}/references/settings.md` — load **first, before anything else**. Locates the consumer's `.doc-settings/settings.md`, defines the four path roles (plugin root, consumer root, repo root, source root), lists the required keys, and **stops the run if settings are absent**. Every path this skill reads or writes comes from here. Read the `authorisations` file it names at the same time: it decides what the Guardrails' bounded `product-definition/` writes are permitted to do in this consumer, and the run report cites it wherever a declaration is created or a `product.md` amended.
- `${CLAUDE_SKILL_DIR}/references/product-definition.md` — load **next**. Product and journey ingestion, the multi-product catalogue schema, cross-product journeys, and the paste fallback. Resolves the blocking input step.
- `${CLAUDE_SKILL_DIR}/references/source-discovery.md` — load **immediately after the product definition resolves**, followed by the consumer's **estate adapter** (the `source_discovery` file in settings). Multi-repo scope, the five-pass discovery funnel, relevance scoring, the confidence rubric, the prior-art pass over `prior_art_roots`, and the **product attribution pass** that derives or corroborates a cross-product journey's product list.
- `${CLAUDE_SKILL_DIR}/references/authoring.md` — load **after discovery completes, before writing any page**. The page set per product, per journey and per cross-product journey, how to choose a spine, the grounding and citation contract, unverified-claim markers, and voice.
- `${CLAUDE_SKILL_DIR}/references/refresh.md` — load **before writing any page, whenever the target product already has pages under its output directory** — in `refresh` mode always, and in `author` mode whenever prior output exists. Human-edit detection, evidence-change detection, the per-page decision table, minimal-diff regeneration, and sidecar proposals. Not loading it when prior output exists risks destroying reviewed content.
- `${CLAUDE_SKILL_DIR}/references/output.md` — load **alongside `authoring.md`**, followed by the consumer's **site adapter** (the `output` file in settings). Directory structure, slugs, Weight determinism, the `doc_journeys` provenance schema, section indexes, report rules, and re-run behaviour; the adapter supplies the site's own paths, field names, template tags, Weight table and build.
- `${CLAUDE_SKILL_DIR}/references/topic-coverage.md` — load **after discovery, before authoring**. Its Pass 1 extracts a canonical topic list from the journey's description and body prose. In this skill that list is used twice: as the **outline** for the journey's spine page before writing, and as a **completeness check** after writing, judging each topic present or missing on the authored page. A topic the evidence could not support is reported as a gap, not padded with prose. For a cross-product journey a topic counts as present when it is named and routed, not when it is documented in place.
- `${CLAUDE_SKILL_DIR}/references/compass.md` and `${CLAUDE_SKILL_DIR}/references/types.md` — load **once per run, before authoring**. Signals, anti-signals, and voice for each Diátaxis type. Required to write each page in the right register and to keep it single-type.
- `${CLAUDE_SKILL_DIR}/references/decision-rubric.md` — load **on demand**, when material does not obviously belong to one type or a draft page is carrying two intents.
- `${CLAUDE_SKILL_DIR}/references/audience-tagging.md` — load **once per run**, to assign `audience_tier` and detailed labels to each authored page.
- `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` — load **after authoring completes**. Part A produces per-journey coverage verdicts counted against each journey's declared spine, Part B assesses all four Diátaxis buckets with a reason for each empty one, Part C records the product brief and its absence, Part D flags suspected mis-filing, Part E reports product inclusion for cross-product journeys. Verdicts describe pages the skill wrote, not pages it found. In a product-only run Part A is skipped and Parts B and C carry the whole report; in the cross-product phase Parts A and E run and the rest do not.
- `${CLAUDE_SKILL_DIR}/references/quality-flags.md` — load **after authoring completes**. The `hollow` and `stale-marker` checks run against authored output as a self-check.
- `${CLAUDE_SKILL_DIR}/references/duplication.md` — load **after authoring completes**, to detect authored pages that overlap each other or overlap prior art (Rule 1), and cross-product pages that restate a page they route to (Rule 2).
- `${CLAUDE_SKILL_DIR}/references/suggested-actions.md` — load **last**, to synthesise the report's prioritised action list.
- `${CLAUDE_SKILL_DIR}/references/weightings.md` — load **only if** `weightings.md` was resolved in the product definition. Compares authored content volume per journey against the declared ideal.
- `${CLAUDE_SKILL_DIR}/references/journey-matching.md` — load **only** for the prior-art pass, to decide which existing pages under `prior_art_roots` cover which journey. Not used to match source repo files; `${CLAUDE_SKILL_DIR}/references/source-discovery.md` governs that.
- `${CLAUDE_SKILL_DIR}/references/examples.md` — load **on demand**, for a worked exemplar. Optional.

Do not invent disambiguation logic. If you reach a case the rubric does not cover, escalate to the user using the scoring format `${CLAUDE_SKILL_DIR}/references/decision-rubric.md` specifies.

## Required input (blocking step)

Before any discovery, authoring, or writing, resolve:

- `product_name` — a single string identifying the product.
- `journeys` — a list of journey records representing what users do with the product.
- `mode` — see "Execution mode" below.

And one non-blocking input:

- `cross_product_journeys` — journeys belonging to no single product, resolved once per run rather than per product. An absent or empty set is normal and skips the cross-product phase silently.

All are resolved per `${CLAUDE_SKILL_DIR}/references/product-definition.md`; load it at the very start of every run.

`journeys = []` is valid and produces a **product-only run**: the product's existing documentation is consolidated into the four Diátaxis buckets, no journey pages are authored, and Part A of `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` is skipped. This is a supported mode rather than a degraded one — reorganising a product's scattered docs is useful on its own, and is often the right first pass before journeys are defined.

Confirm the intent when journeys are absent, since it is equally likely to mean the definition is incomplete. Ask once, proceed on confirmation, and record in the report that no journeys were supplied.

The product definition lives at `<repo root>/product-definition/` — in the consumer repository, beside `.doc-settings/`, not at the root of a repository being scanned, because in this skill there is no single repository being scanned. See `${CLAUDE_SKILL_DIR}/references/product-definition.md` for the folder layout and the multi-product catalogue format.

### Execution gating

You MUST NOT proceed to discovery, authoring, or writing until `product_name`, `journeys`, and `mode` are all resolved and the resolved values have been confirmed with the user.

## Execution mode

- **`author` (default)** — discovers existing documentation across the repos and consolidates it into new pages in the site. The subject of this file. When the target product already has generated pages, `author` automatically applies the refresh rules rather than overwriting — see below.
- **`refresh`** — updates documentation a previous run authored. Re-runs discovery, determines per page whether the evidence actually changed and whether a human has edited it, and regenerates only what needs it, minimally. Governed by `${CLAUDE_SKILL_DIR}/references/refresh.md`.
- **`plan`** — runs discovery and page-set planning, then stops. Writes nothing. Reports what it would author, from what evidence, at what confidence. Use this to sanity-check a product's discovery results before committing to a write. Also valid ahead of a refresh, where it reports the per-page dispositions it would apply.
- **`audit`** — the base skill's original behaviour: classify markdown that already exists in a single repo and report on it, writing no new content. Retained because the classification and reporting machinery is shared. In this mode the base skill's own `full` / `report-only` / `coverage-only` sub-modes apply, output goes to that repo, and `${CLAUDE_SKILL_DIR}/references/settings.md`, `${CLAUDE_SKILL_DIR}/references/source-discovery.md`, `${CLAUDE_SKILL_DIR}/references/authoring.md`, and `${CLAUDE_SKILL_DIR}/references/output.md` are **not** loaded.

### Mode resolution (blocking)

1. If the invocation contains a case-insensitive match of `plan`, `dry run`, `dry-run`, or `what would you write` → `mode = plan`.
2. If the invocation contains `refresh`, `update the docs`, `re-run`, `rerun`, or `bring up to date` → `mode = refresh`.
3. If the invocation contains `audit`, `categorise`, `categorize`, or `classify existing` → `mode = audit`.
4. If the invocation contains `author`, `generate`, `write docs`, or `generate documentation` → `mode = author`.
5. Otherwise → `mode = author`. This differs from the base skill, which asked. Authoring is the purpose of this skill, and the `plan` mode plus the pre-write confirmation below already give the user a stopping point.

Two orthogonal switches ride alongside the mode, both off unless the invocation sets them:

- **`force`** — set by `regenerate anyway`, `redo`, `regardless of evidence`, or an explicit `force`. Feeds the Force column of `${CLAUDE_SKILL_DIR}/references/refresh.md` Step 3: skill-owned pages are regenerated even where the evidence has not moved, minimal-diff as always. Use when the skill or model version changed. It never touches a human-edited page, in any combination, and there is no invocation that makes it.
- **`only-missing`** — set by `extend`, `fill the gaps`, `only what is missing`. Restricts writing to pages with disposition `created`; every existing page resolves to `current` or `human-owned, current` and is left alone. It narrows what gets written, never what gets discovered.

Both are per-run, both are printed with the resolved values at step 1, and neither is inferred from the presence of existing pages — a run that already has output is the normal case, not a signal.

**`author` and `refresh` converge.** Mode selects the intent; what actually happens per page depends on what is already on disk. An `author` run targeting a product with existing generated pages MUST apply `${CLAUDE_SKILL_DIR}/references/refresh.md` — it must never overwrite existing pages just because the user said "generate" rather than "refresh". Equally, a `refresh` run authors any journey that has no pages yet. The distinction is emphasis, not a different code path, and getting this wrong destroys reviewed content.

**Where prior output exists, the mode keyword is not enough to know what was wanted.** "Document Foglight Alerting" is what a person says whether they mean *write it*, *bring it up to date*, or *I did not realise it already exists*. The convergence rule above makes every reading safe — nothing is overwritten either way — but safe is not the same as intended, and a refresh delivered to someone expecting a rebuild wastes a run. So when the target already has pages, state at the step 1 confirmation what exists and which of these the run will do: refresh where evidence moved, regenerate skill-owned pages regardless (the force flag in `${CLAUDE_SKILL_DIR}/references/refresh.md` Step 3, which still never touches a human-edited page), author only what is missing, or plan and write nothing. Rebuilding from a blank page is not on that list: it requires deleting the pages first, which is a human's explicit act and never the skill's.

The mode is per-run and is not persisted.

## Source scope

Governed entirely by `${CLAUDE_SKILL_DIR}/references/source-discovery.md` plus the consumer's estate adapter. In summary:

- Source root: per `source_root` in settings — by default the parent directory of the **main** consumer checkout, resolved at run time, never a hardcoded path and never the parent of a run worktree. Every direct child that is a git repository is a source repo, minus `source_exclude_repos`.
- The consumer repository is normally **included** as a source — its existing documentation is consolidated (and where needed duplicated) into the output. Only the skill's own output is excluded as a source: the paths in `source_exclude_paths`. Pages under `prior_art_roots` additionally get the prior-art pass, which records overlap.
- Discovery is a five-pass funnel: build a term set, match paths, match content, rank and shortlist, then read. The shortlist is capped at 40 files per journey and anything dropped at the cap is reported.
- Every evidence file gets a relevance score; every authored page gets a confidence score computed from its evidence set.
- For cross-product journeys a further **product attribution pass** maps evidence to products via each product's declared `repos`, and scores how strongly each product belongs in the journey. It derives the product list when the journey omitted one and corroborates it otherwise. Repo ownership is never guessed from a repository's name.

Do not scan all repositories exhaustively. There are thousands of markdown files and far more source files across the source root; the funnel exists because reading them all is neither possible nor useful.

## Output

Governed entirely by `${CLAUDE_SKILL_DIR}/references/output.md` plus the consumer's site adapter. In summary:

- Output root: `output_root` in settings, and nothing else under the site's content directory.
- Everything the skill writes lives under that one section, inside the content tree so it renders and can be previewed and reviewed as a site. Where the site deploys unconditionally it publishes with the branch; the section index says plainly that its pages are generated and proposed.
- Three peer sections beneath it: **`products/`**, **`cross-product-journeys/`** and **`reports/`**, in that order. Products sort first because a reader orienting themselves asks what exists before asking how to combine it; reports sort last because they describe the other two rather than the platform.
- Structure: `products/<product>/` for product-level pages, `products/<product>/<journey>/` for journey pages, with `tutorial/`, `how-to/`, `reference/` and `explanation/` bucket sections under the product where prose supports them. `cross-product-journeys/<journey>/` holds an index and a single spine page — no buckets, no variations.
- Every directory carries an `_index.md`; Weight ordering is assigned deterministically so re-runs do not reshuffle navigation.
- Frontmatter uses the site's own standard fields (named in the site adapter) plus a namespaced `doc_journeys:` provenance block carrying product, journey, type, audience, confidence, and the cited source list.
- Re-runs never blindly overwrite. Per-page disposition is decided by `${CLAUDE_SKILL_DIR}/references/refresh.md`: pages whose evidence is unchanged are left untouched, and pages a human has edited — detected by comparing the on-disk body against `content_hash`, not by the presence of `generated: true` — are never overwritten in any mode.

## Authoring

Governed entirely by `${CLAUDE_SKILL_DIR}/references/authoring.md`. The rules that matter most:

- **Nothing is written from scratch.** Every claim must already exist in prose somewhere — a README, `docs/`, a runbook, the existing site, or a product brief. Rewriting and restructuring is the job; matching word for word is not required.
- **Code, manifests, CRDs, schemas and tests are corroborators, never sources.** They confirm a prose claim (worth confidence) and detect stale prose (worth a reported conflict), but no page may be written from them. Where prose and code disagree, write neither and report it.
- **Material that exists only in code is reported as undocumented surface area**, at claim granularity — one unbacked field is dropped from an otherwise well-sourced page, not the whole page.
- Commands, flags, paths, config keys, resource names, ports, and versions are **copied character-for-character** from evidence, never paraphrased and never completed from plausibility.
- A step required by narrative logic but unsupported by evidence is written with the site's explicit `unverified_marker` and costs the page confidence. An unverified **command** is never printed at all.
- **Contact points are not ordinary facts.** Slack channels, group handles, and distribution lists name things outside the repositories, so citing a source does not establish that they still exist. One never goes on a page unless a source repo corroborates it, and it is written with its permalink where one exists. See `${CLAUDE_SKILL_DIR}/references/authoring.md`.
- **A cross-product journey page routes; it does not restate.** It names each stage, says which product owns it, and links to where it is documented. Reproducing a command or field list that the linked page already carries is a defect, caught by `${CLAUDE_SKILL_DIR}/references/duplication.md` Rule 2. A stage with no documentation is named as a gap with its owning product — never filled with plausible prose because a hole in a route looks worse than a hole in a list.
- A page that cannot be grounded is not written. No stubs — a hollow page looks like coverage and is worse than an acknowledged gap.
- Every page ends with a `## Sources` section listing each contributing file and what it contributed.
- The consumer's `style_guide` is binding on list formatting, punctuation, and house style.

## Process

0. **Resolve settings** per `${CLAUDE_SKILL_DIR}/references/settings.md`: derive the consumer root from the repo root, read `<consumer root>/.doc-settings/settings.md`, and stop if it is absent or missing a required key. Print the resolved output root, source root and adapter filenames. Every later step names settings keys and expects them resolved here.
1. Resolve `product_name`, `journeys`, `brief`, `cross_product_journeys`, and `mode` per `${CLAUDE_SKILL_DIR}/references/product-definition.md`. Blocking. Print the resolved values — including whether a brief was found and its capture date, and the cross-product block — and confirm with the user.

1b. **Where the request names a product or journey the definition does not declare, draft the missing declaration** per *Declaring from a run* in `${CLAUDE_SKILL_DIR}/references/product-definition.md` — do not stop, and do not fall back to the paste flow, which resolves the run in memory and leaves the definition no better than it found it. The draft is provisional here: discovery has not run, so `description`, `spine` and a product's `features` are your best reading of the request. Print it, say it will be confirmed at step 6 rather than now, and carry it forward. Where the target already has pages on disk, print that too and say which of refresh / regenerate-anyway / author-what-is-missing / plan-only this run will do, per *Mode resolution*.
2. Resolve the source repo list per `${CLAUDE_SKILL_DIR}/references/source-discovery.md`. Print the repo list and count.
3. Run the prior-art pass over `prior_art_roots` for each journey. Record what already exists.
4. For each journey, run the five-pass discovery funnel. Produce a scored, ranked evidence shortlist and a per-journey discovery record. Extract the journey's canonical topic list per `${CLAUDE_SKILL_DIR}/references/topic-coverage.md` Pass 1 — it becomes the outline for the journey's spine page. Discovery searches every source repo regardless of which product the journey is declared under; record the evidence spread per repo, which Part D of `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` consumes.
4b. **Separate sources from corroborators** across the whole evidence set, per the strict provenance rule in `${CLAUDE_SKILL_DIR}/references/authoring.md`. Prose becomes the source set; code, manifests, CRDs, schemas and tests become the corroborator set. Record every claim the corroborators establish that no prose covers — this becomes the report's *undocumented surface area* section, and it must be collected here rather than reconstructed later.
4c. **Attribute evidence to products** — cross-product journeys only, per the product attribution pass in `${CLAUDE_SKILL_DIR}/references/source-discovery.md`. Derive the product list where the journey omitted `products`, corroborate it where it declared one, and record every product's inclusion confidence with the rubric rows that produced it, the unattributed share, and the repos behind it. Where the list was derived, re-run step 4 with the enriched term set before continuing, and keep the attribution scores from the first pass. A journey resolving to fewer than two products produces no page and is reported.
5. **Determine per-page disposition.** If the product already has pages under its output directory, apply `${CLAUDE_SKILL_DIR}/references/refresh.md` Steps 1–3: detect human edits via `content_hash`, detect evidence change via recorded `repo_head` plus newly discovered candidates, and resolve each page to `current`, `regenerated`, `proposal written`, `human-owned, current`, `created`, or `orphaned`. If the product has no existing pages, every page is `created` and this step is trivial.
6. **Confirm before writing.** Print the planned page set: every path, its disposition from step 5, its Diátaxis type, its evidence count, and its computed confidence. Call out pages that will be left untouched, pages needing a sidecar proposal, and orphans. Include journeys with no viable evidence and say they will be skipped. For every cross-product journey with a derived product list, print the derivation from step 4c — included and excluded products with their confidence and share — because the page set for that journey is a consequence of it, and this is the last point at which a human can overrule it before pages are written. In `plan` mode, stop here.

    **Print any declaration drafted at step 1b in full** — path, frontmatter, body, exactly as it will be written — with each frontmatter value marked *found* (and cited) or *proposed* (and justified), plus, for a new product, the `catalogue.md` line and where it goes. Confirming it is a separate act from confirming the page set: the file outlives this run and frames every later one, and burying it in a page table gets it waved through. If the human's edits change a value that feeds discovery, return to step 4 with the corrected vocabulary and come back here — do not carry on against a plan built from superseded inputs.

6b. **Write the confirmed declaration**, before any page. Then re-read the definition from disk and proceed against that, so the run resolves the file exactly as every later run will.
7. Assign audience tiers per `${CLAUDE_SKILL_DIR}/references/audience-tagging.md`.
8. Author or regenerate the page set per `${CLAUDE_SKILL_DIR}/references/authoring.md`, using `${CLAUDE_SKILL_DIR}/references/compass.md` and `${CLAUDE_SKILL_DIR}/references/types.md` for type and voice, escalating to `${CLAUDE_SKILL_DIR}/references/decision-rubric.md` when material does not fit one type cleanly. For pages dispositioned `regenerated`, apply `${CLAUDE_SKILL_DIR}/references/refresh.md` Step 4 — start from the existing page and change only what the evidence requires; do not reword what did not change.
9. Write pages into the layout per `${CLAUDE_SKILL_DIR}/references/output.md` and the site adapter, including every `_index.md`, Weight value, and the full provenance block (`content_hash`, `generated_at`, `generated_by`, `skill_version`, per-source `repo_head`). Pages dispositioned `current` or `human-owned, current` are **not** rewritten at all. Sidecar proposals go to `proposals_root`, never into the content tree.
10. Compute coverage verdicts per `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` against the full page set now on disk — not just pages written this run. Part A gives journey coverage **counted against each journey's declared spine**, Part B assesses all four Diátaxis buckets with a reason for each empty one, Part C records the product brief, Part D flags suspected mis-filing. Run `${CLAUDE_SKILL_DIR}/references/topic-coverage.md` Pass 2 over each journey's spine page against its topic list. Topics judged missing are reported as gaps, never backfilled with unsourced prose.
11. Run `${CLAUDE_SKILL_DIR}/references/quality-flags.md` as a self-check over pages written this run. A `hollow` flag on a page this skill just wrote is a defect: delete the page and record the journey as uncovered rather than shipping a stub.
12. Run `${CLAUDE_SKILL_DIR}/references/duplication.md` over the product's pages plus prior-art hits, to catch pages that overlap each other or restate existing site content. Rule 2 additionally runs over any cross-product page authored this run against every page it routes to — a route that restated its own destination is a defect to fix before writing, not an observation to file.
13. If a weightings file was resolved, compute the comparison per `${CLAUDE_SKILL_DIR}/references/weightings.md`.
14. Synthesise the action list per `${CLAUDE_SKILL_DIR}/references/suggested-actions.md`.
15. Write the run report to `<reports_dir>/<product-slug>.md`, per the report page rules in `${CLAUDE_SKILL_DIR}/references/output.md` — frontmatter, no body `H1`, the `report_banner`, and every quoted template tag escaped.
16. Print a one-screen summary in chat: dispositions by count, journeys covered, confidence distribution and any confidence changes, pages needing re-review, and a pointer to the report.

## Batch runs

The product definition may declare several products, and separately any number of cross-product journeys (see `${CLAUDE_SKILL_DIR}/references/product-definition.md`). A run has three phases:

**Phase 1 — resolve, once.** Steps 1 and 2 run once for the whole batch: the product definition, every product's journeys, the cross-product journey set, and the source repo list.

**Phase 2 — per product.** Steps 3–15 run once per product, in the order declared.

- Confirm the **whole batch plan** with the user at step 6 — every product's page set and dispositions, plus the cross-product page set, at once — not once per product. A batch that stops to ask fifteen times is unusable.
- If one product fails, continue with the rest and report the failure. Do not abort the batch.
- Write one report per product to `<reports_dir>/<product-slug>.md`.

**Phase 3 — cross-product, once, last.** Steps 3–14 run once over the whole cross-product journey set, writing to `cross-product-journeys/` and reporting to `<reports_dir>/cross-product-journeys.md`.

It runs **after** every product phase, and the ordering is load-bearing rather than cosmetic. A cross-product page routes to product journey pages, so it needs those pages to exist in order to link to them and in order for `${CLAUDE_SKILL_DIR}/references/duplication.md` Rule 2 to compare against them. Running it first produces routes pointing at paths that do not yet exist, and no restating check at all.

Skip Phase 3 entirely when the cross-product set is empty. Do not write an empty section.

Finally, write a `<reports_dir>/batch.md` summary listing each product, its page count, its coverage, and its lowest page confidence — plus a row for the cross-product phase.

### What Phase 3 does differently

Phase 3 is the per-product process with six changes, and everything not listed here is identical:

  * **The term set unions every product's** `features` and brief, per `${CLAUDE_SKILL_DIR}/references/source-discovery.md`. There is no single product term set.
  * **The product attribution pass runs**, deriving the product list where the journey omitted `products` and corroborating it where the journey declared one. A journey with a derived list runs discovery **twice** — a journey-only term set first to bootstrap attribution, then an enriched set once the products are known. Attribution is scored on the first pass only, so an included product cannot confirm itself with vocabulary the second pass went looking for.
  * **A journey whose derivation yields fewer than two products produces no page.** Report it with every product's score. Do not reduce it to a single-product journey; that is a scope change and scope is declared.
  * **Parts B, C and D of gap analysis do not run** — Diátaxis buckets and the brief are product-scoped, and a cross-product journey has no product; Part D has nothing to suspect about a journey already declared as spanning products. **Part E does run**, and is cross-product-only. The report carries Parts A and E plus undocumented surface area, topic coverage and unverified claims.
  * **Pages route rather than restate**, per `${CLAUDE_SKILL_DIR}/references/authoring.md`. `${CLAUDE_SKILL_DIR}/references/duplication.md` Rule 2 enforces it against the pages Phase 2 just wrote.
  * **No buckets and no variations** are created. Each journey directory holds an `_index.md` and one spine page.

## Report format

Reports are written **inside the site's content directory**, as pages, to `reports_dir`:

```
<reports_dir>/<product-slug>.md
<reports_dir>/<product-slug>-<journey-slug>.md   # journey-scoped runs
<reports_dir>/cross-product-journeys.md
<reports_dir>/batch.md
```

**One run, one report file — reports are never shared across runs.** A run scoped to a single journey writes `reports/<product-slug>-<journey-slug>.md` and touches **no other report**: not the product-wide report, not `batch.md` (a single-journey run is not a batch), not another journey's report. The product-wide `reports/<product-slug>.md` is written only by a run scoped to the whole product, and `batch.md` only by a multi-product batch run. This rule exists because parallel journey runs amending one shared report inherited each other's stale sections — present-tense Follow-ups from another run, twin figures with different underlying facts — and it is where two structural-gate failures lived. Where a run's scope has no report file yet, it creates one; it never amends a report a different scope owns, and a stale figure it notices in another run's report is a suggested action, not an edit.

They render as a `Reports` section beside `products/` and `cross-product-journeys/`, so a reviewer reads the diagnostics next to the output they describe, with working links between them. `${CLAUDE_SKILL_DIR}/references/output.md` is authoritative on the section, and four of its rules are load-bearing rather than cosmetic:

  * **Every report carries frontmatter, no body `H1`, and an opening `report_banner`** saying it is a run diagnostic and not documentation. A report reached from site search has no other context.
  * **Template tag syntax quoted in a report must be escaped**, in the form the site adapter gives. Static-site generators expand template tags inside backtick spans, and the *Unverified claims* section quotes an *unclosed* marker by construction, which fails the build. This will bite every report that has that section.
  * **Sidecar proposals stay outside the content tree**, at `proposals_root`. A proposal is draft page content, not a diagnostic; publishing it would put an unmarked second copy of a real page on the site.
  * **Every figure lives in exactly one place.** A count, share, weight, or attribution appears once per report — in a table, or as the derived headline of the one list it counts — and prose never restates a number any table carries; write "see the table" or a qualitative phrase instead. Restated figures are the single largest defect class in this pipeline's history: the table gets edited, the sentence does not, and every fix round that rewrote a prose figure minted a new stale one (one run leaked nine fresh figure defects out of fixing five). The same rule makes `## Post-run corrections` a **table** (finding, what was wrong, what changed) with no narrative paragraphs, and forbids superseded claims left in present tense — rewrite the section, never append a correction below a live copy of the old claim.

This reverses the earlier rule that kept reports out of the content directory. The consequence that rule protected against is real and now accepted: reports publish to the live site with the branch, and they name documentation gaps. The banner and the section index carry the labelling that keeps them from being read as guidance.

Sections, in order:

1. **Header** — product, journeys, source repos scanned, run date, mode.
2. **TL;DR** — pages written, journeys covered out of total, confidence distribution (`high X · medium Y · low Z`), and the count of pages needing review.
3. **Product brief** — present or absent, its source, capture date, and staleness. When absent, state the gap in full: the product page is navigation-only *and* every journey's term set is degraded. Per `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part C.
4. **Diátaxis bucket coverage** — all four buckets with page counts, and for each empty bucket the reason (`no prose found`, `prose found, not consolidated`, `code-only`, `not applicable`). Per `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part B.
5. **Undocumented surface area** — capabilities the code defines that no documentation anywhere describes, and which strict provenance therefore prevented writing. Table: what it is, where the code defines it, what a human would need to write. **This is the most actionable section in the report** — it names work that is invisible without reading code and prose together, and every entry is a concrete task.
6. **Pages written** — table: path, Diátaxis type, audience tier, confidence, evidence file count.
7. **Journeys not covered** — table: journey, its declared spine, why (no evidence found / evidence too thin / all candidates dropped), and the term set that failed. It tells a human exactly where to point the skill next.
8. **Suspected mis-filing** — journeys whose evidence came mostly from repositories outside their declaring product, with the weight distribution by repo. Per `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part D. An observation for a human, never an action the next run takes. Omitted in the cross-product report.
8b. **Product inclusion** — cross-product report only, per `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part E. States whether the product list was `declared` or `derived`, then one table covering **every product considered, included and excluded together**, with its inclusion confidence, evidence weight, share, high-relevance file count, how it was attributed, and which rubric rows fired. Beside it, the unattributed evidence share and the repos that produced it. Where the list was derived this is the section that makes the rest of the report interpretable — a reader who assumes a human picked the products will misread every other number. The excluded rows are the actionable ones: each is a place to either agree with the derivation or overrule it by declaring `products`.
9. **Topic coverage** — per journey: the canonical topic list from `${CLAUDE_SKILL_DIR}/references/topic-coverage.md`, each marked present or missing on the authored pages. They name what a journey's documentation still lacks even where a page was written.
10. **Evidence summary** — per journey: repos that yielded evidence, repos that yielded nothing, shortlist size, and count dropped at the cap.
11. **Prior art** — existing pages under `prior_art_roots` covering the same ground, with overlap and whether the authored page linked to them.
12. **Duplication and restated routes** — Rule 1 clusters and Rule 2 flags per `${CLAUDE_SKILL_DIR}/references/duplication.md`. Rule 2 fires only in the cross-product phase; state explicitly when it found nothing, because an absent subsection is indistinguishable from a check that never ran.
13. **Unverified claims** — every `unverified_marker` emitted, with page and claim. A reviewer works through this list.
14. **Source conflicts** — where prose disagreed with prose, or prose with code. Both values, both paths, and the resolution. A prose-versus-code conflict resolves to *no claim written*, not to the code's value.
15. **Skipped pages** — target paths not written because a human-edited page already occupied them.
16. **Suggested actions** — per `${CLAUDE_SKILL_DIR}/references/suggested-actions.md`. **This list is authoritative on section ordering**; that file specifies the action vocabulary and derivation rules only. Where the two have disagreed about placement, this ordering won and the reference was corrected.
17. **Follow-ups** — including any navigation quirk the site adapter records (a landing-page redirect that hides the output section, say), because a reviewer given a bare site link will otherwise never find the output; point them at `preview_path`.

### The cross-product report

Phase 3 writes to `<reports_dir>/cross-product-journeys.md`, using the same section order with four differences:

  * **Sections 3, 4 and 8 are omitted** — the product brief, Diátaxis bucket coverage and suspected mis-filing are all product-scoped and have no subject here. Omit them rather than printing them empty.
  * **Section 8b appears only here.** Product inclusion has no meaning for a journey with one declared owner, and it is the only section that reports on the derivation.
  * **The header names the products crossed** rather than a single product, and section 1 lists each journey with its `spine` and its `products`, marked `declared` or `derived`. Never print a derived list without that marker — a reader who mistakes it for a human's editorial choice will not think to check it.
  * **Two sections are read first, in this order.** Section 8b, where the product list was derived, because everything downstream is conditional on the derivation being right. Then section 12: in a product report duplication is a survey of the estate, but here Rule 2 is checking whether the skill just wrote a route that competes with its own destination, which is a defect in this run's output.

The report is not written in `plan` mode; the step 6 plan is printed to chat instead.

## Guardrails

- **Write to exactly two places.** In `author` mode the only writable locations are `output_root` (pages **and** run reports, the latter under `reports_dir`) and `proposals_root` (sidecar proposals only). Never write to a source repository. Never write elsewhere in the consumer repository — everything under `prior_art_roots` is read-only prior art. **Originals are never modified**: consolidating an existing page into a proposed page leaves the existing page exactly as it was.
  * **One exception: bounded writes under `product-definition/`.** Where the request names a journey or product the definition does not declare, a **new** file may be created under `product-definition/` — after its exact contents were confirmed at the pre-write gate, the same confirmation this skill already requires before writing anything. Expanding a product's journey set this way is expected where the consumer's `authorisations` file says so. Two modifications are also permitted, same confirmation required for the exact diff: amending a **`product.md`** (e.g. adding `features` a new journey needs), and appending a newly declared product's slug to `catalogue.md`'s `products` list — one entry and nothing else. Briefs, existing journey and cross-product-journey declarations are never modified, and nothing is ever deleted or renamed there. See `${CLAUDE_SKILL_DIR}/references/product-definition.md`, *Declaring from a run*.
- **Never reclassify a journey's scope.** Whether a journey belongs to one product or crosses several is declared by a human, by which folder it sits in. The skill reports the evidence for a different answer (`${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part D) and acts on it never — not by moving the journey between sections, not by demoting a cross-product journey whose evidence landed in one product, and not by changing its output path. A run must produce the same paths as the last one unless the definition changed — by a human editing it, or by this run creating a declaration a human confirmed, which is the same thing arriving by a different route.
- **Deriving a cross-product journey's product list is not reclassification.** Scope is editorial and declared; *which* products a cross-product journey crosses is a question of fact, and discovery answers it per `${CLAUDE_SKILL_DIR}/references/source-discovery.md`. A journey may omit `products` and have the list derived, with a per-product inclusion confidence reported in Part E. Where the list is declared it is authoritative and the derivation only corroborates it. Do not collapse these two rules into one in either direction — the first prevents URL churn, the second prevents a journey being undeclarable until someone hand-computes an answer the skill can compute.
- **Never let a route restate its destination.** A cross-product page that reproduces a command or field list from a page it links to has created a competitor to that page, with a link lending the copy credibility. Trim it before writing.
- **Never invent a missing stage in a route.** An undocumented stage is named, attributed to its owning product, and reported. A route with an acknowledged hole is a task list; one with a plausible paragraph over the hole is a fabrication.
- **Never explain a gap on the page.** Name it in a line, say who owns it, and stop. Why the material was rejected belongs in the report. A gap notice that argues its case is the same defect as an invented stage, wearing the opposite costume. See *What to write instead* in `${CLAUDE_SKILL_DIR}/references/authoring.md`.
- **Never write the cross-product section before the products.** Phase 3 runs last so its links resolve and Rule 2 has something to compare against.
- **Never author from code.** No claim on any page may originate in source, manifests, CRDs, schemas or tests. They corroborate; they are never written from. Material documented only in code is reported, never published.
- **Never substitute a README for a product brief.** An unbriefed product gets a navigation-only page and a reported gap.
- **Never create an empty bucket.** A Diátaxis section with no pages is a reported gap, not a placeholder index.
- **Never write an unescaped template tag into a report.** Reports live in the content directory and are built by the site generator, which expands template tags even inside backtick spans. An unclosed one — and the *Unverified claims* section quotes one by construction — fails the whole site build. Escape in the form the site adapter gives and run its check command before finishing.
- **Never publish a sidecar proposal.** Proposals are draft page content and stay outside the content tree. A published proposal is an unmarked second copy of a real page.
- **Never write a report without its `report_banner`.** A report is a diagnostic that now renders as a live page; without the banner a reader arriving from search cannot tell it apart from documentation.
- **Never modify source repositories** in any way, including files that look like scratch or draft content.
- **Never overwrite human-edited pages.** A page whose body no longer matches its recorded `content_hash` has been edited by a human and is protected — regardless of mode, and regardless of any force flag. There is no invocation that discards a human's edits. See `${CLAUDE_SKILL_DIR}/references/refresh.md`. A page with no recorded `content_hash` is treated as human-edited.
- **Never rewrite a page whose evidence did not change.** Regeneration is non-deterministic, so rewriting unchanged pages produces churn that buries the real changes and makes the diff unreviewable.
- **Never delete.** Orphaned journey directories from a previous run are reported, not removed.
- **Never print an unverified command.** See `${CLAUDE_SKILL_DIR}/references/authoring.md`.
- **Never print an uncorroborated contact point.** A Slack channel, group handle, or distribution list found only in prose does not go on a page, however authoritative the page it came from looked.
- **Never write a stub.** No evidence means no page and a reported gap.
- **Confirm the plan before writing** at step 6, always, in every mode that writes.
- **Report the cap.** When discovery drops candidates at the 40-file shortlist cap, say how many. Silent truncation reads as complete coverage.

## Invocation examples

Slash form: `/doc-journeys <request>` — `/doc-tools:doc-journeys` when installed as the plugin;
the natural-language forms below resolve to the same thing.

```
Use doc-journeys to generate documentation for Foglight.
Use doc-journeys for all products in the product definition.
Use doc-journeys for Foglight, journey "Archive telemetry" only.
Use doc-journeys in plan mode for Foglight Agent.

Use doc-journeys for the cross-product journeys only.      # Phase 3 alone; requires the
                                                           # product pages to already exist
Use doc-journeys in plan mode for the cross-product journeys.

Use doc-journeys to refresh Foglight.
Use doc-journeys to refresh where sources changed.        # cheapest useful refresh
Use doc-journeys to refresh everything, force.            # regenerate skill-owned pages
                                                          # even where sources are unchanged
                                                          # (never touches human-edited pages)
Use doc-journeys in plan mode to refresh Foglight.        # show dispositions, write nothing

Use doc-journeys to audit foglight-agent.                 # legacy audit mode
```

## Command templates

```bash
# Locate the consumer root and its settings — the MAIN checkout, never the worktree, never $HOME.
CONSUMER_ROOT=$(dirname "$(git -C <repo root> rev-parse --path-format=absolute --git-common-dir)")
test -f "$CONSUMER_ROOT/.doc-settings/settings.md" || { echo "no .doc-settings — stop"; }

# Resolve the source root per settings (`parent-of-consumer-root` shown); see
# ${CLAUDE_SKILL_DIR}/references/settings.md and the estate adapter.
SRC_ROOT=$(dirname "$CONSUMER_ROOT")

# Enumerate source repos — the consumer repo included; its own output trees are excluded per
# source_exclude_paths
find "$SRC_ROOT" -maxdepth 2 -name .git | sed 's|/.git$||'

# Pass 1 — path signal for one journey's term set
rg --files <repo> | rg -i -e '<term1>' -e '<term2>'

# Pass 2 — content signal, counting distinct terms per file
rg -i -c -e '<term1>' -e '<term2>' <repo> \
  --glob '!{node_modules,vendor,.terraform,build,dist,.git}/**'

# Corroborate a contact point before writing it onto a page.
# No hit outside the source it came from means it does not go on the page.
rg -n --fixed-strings '#foglight-support' "$SRC_ROOT" --glob '!{node_modules,vendor,.git}/**'

# Contact points at their most authoritative — the schema-validated fields the estate adapter
# names under contact_corroborators ({repo, path, field})
rg -h '<field>' "$SRC_ROOT/<repo>/<path>"

# Detect a generated file before ranking it
head -5 <file> | rg -i 'generated|do not edit'

# Refresh — what changed in a source repo since a page was generated
git -C "$SRC_ROOT/<repo>" rev-parse HEAD
git -C "$SRC_ROOT/<repo>" diff --name-only <recorded_repo_head>..HEAD -- <cited paths>

# Refresh — hash a page body (excluding frontmatter) to detect human edits
awk 'BEGIN{c=0} /^---$/{c++; next} c>=2' <page> | sha256sum
```
