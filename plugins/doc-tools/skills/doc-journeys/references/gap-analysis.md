---
name: Gap analysis
description: How the skill computes coverage gaps — per-journey verdicts (covered/partial/missing) counted against each journey's declared spine, Diátaxis bucket coverage, the product brief, and suspected mis-filing. Load once per run, after audience tagging, before the placement map. Produces a coverage report for stakeholder discussion.
---

# Gap analysis

This file specifies how the skill computes documentation coverage gaps. It runs after audience tagging (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/audience-tagging.md`) and before the placement map is built.

The output is a coverage analysis written into REPORT.md. Gap analysis is per-journey and per-product; it does **not** modify per-page frontmatter or any other per-page output.

## Input and output

**Per-run inputs (already computed by earlier steps):**
- The resolved `journeys` list (each journey has name, optional users, feature, variations).
- For every scanned page: the Diátaxis verdict, the journey-relevance list, the audience block.

**Per-run outputs:**
- **Journey coverage** — one record per supplied journey with `verdict`, reasons, variation status, and counts.
- **Product-level coverage** — descriptive counts of builder/maintainer-audience pages by Diátaxis type. No flags; no assertion that any tier must be present.

## Procedure

### Part A — Journey coverage

Run only if `journeys` is non-empty. For each journey:

**Parts A and E are the parts that apply to cross-product journeys.** Parts B and C are product-scoped — Diátaxis buckets belong to a product, and a brief describes a product — so neither has a subject when the journey belongs to none. Part D never applies to them either. A cross-product run's report therefore carries Part A and Part E, plus undocumented surface area, topic coverage, and unverified claims, and omits Parts B, C and D rather than printing them empty. Every step below applies to both journey kinds unless it says otherwise.

Part E is cross-product-**only**, the mirror image of the others: it reports which products the journey was found to cross and with what confidence. Where the product list was derived rather than declared, Part E is the section that makes the rest of the report interpretable, and it is the first thing to read.

#### Step 1 — Collect matched pages

Collect every page whose journey-relevance list contains an entry for this journey. Bucket by Diátaxis type and confidence, **against the journey's declared `spine`** — `how-to` unless the definition says otherwise:

- `strong_spine` — page's Diátaxis type equals the journey's `spine`, AND the journey match for this journey has `confidence: strong`. For a `how-to` spine this means a PERFECT-how-to, REWRITE-how-to, or any how-to output of a SPLIT; read the equivalent for `tutorial` and `explanation`.
- `weak_spine` — same Diátaxis criterion, but `confidence: weak`.
- `non_spine` — matched pages whose Diátaxis type is anything other than the journey's spine (any confidence).

**Counting against the spine rather than against `how-to` is what makes a declared spine work at all.** These buckets drive the verdict, so a journey declared `tutorial` whose tutorial was written perfectly would otherwise land in `non_spine`, return `partial — no how-to`, and report as uncovered while being fully documented. The bucket name changed from `strong_how_to` for the same reason: a field named for one type cannot hold three.

For a journey with the default `how-to` spine, every rule below behaves exactly as it did before.

#### Step 2 — Variation coverage

If the journey has a non-empty `variations` list:

For each variation:
- A variation is **covered** if at least one matched page has a journey-match record with `variation` equal to this variation string (case-insensitive).
- Otherwise the variation is **missing**.

If the journey has no variations, skip Step 2; the journey is treated as a single unit.

#### Step 3 — End-to-end check

Run only when `strong_spine` is non-empty. Skip otherwise — a journey with no strong spine page is already partial or missing and the check would not change the verdict.

The agent is given:
- The journey's name, description, variations, and declared spine.
- The content sample of every page in `strong_spine`, concatenated and capped at ~3000 lines total. Prioritise pages in input order; if the cap is hit, prefer headings + opening paragraphs of each page over full bodies.

The agent answers:
- `end_to_end: yes` — the matched pages collectively take the reader from start to finish of the journey, covering every essential stage.
- `end_to_end: no` — one or more essential stages are missing. Provide one short sentence naming the missing stage(s).

**For a cross-product journey, "covers every stage" means every stage is named and routed, not documented in place.** A route that names six stages and links five of them, saying plainly that the sixth is undocumented, is `end_to_end: no` — the reader cannot finish. A route that names and links all six is `end_to_end: yes` even though it contains no procedure of its own. Judging a route by whether it *contains* the steps would fail every correctly-written cross-product page.

#### Step 4 — Determine verdict (counts-driven, no re-judgement)

**The verdict is determined SOLELY by the counts collected in Step 1.** This step counts; it does NOT re-evaluate whether a tagged match "really counts" or is "too tangential". The journey-matching procedure in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/journey-matching.md` is the only place where the line between strong, weak, and not-a-match is drawn; that decision is final.

If you find yourself reasoning "these matches are tangential, so I'll exclude them" or "the weak matches don't really cover the journey" — **STOP**. The page is in `weak_spine` because the journey-matching step decided it was a partial-coverage match; that is exactly what `weak` means. Re-judging here breaks consistency with the per-page tags emitted in REPORT.md Sections 8 and 9 (and would, for the same journey, produce a Section 3 row that contradicts the per-page tags shown elsewhere in the same report).

Apply the following rules in order. The first rule that matches wins.

1. `strong_spine` empty AND `weak_spine` empty AND `non_spine` empty → **missing**. Reason: `no matching pages`.
2. `strong_spine` empty AND `weak_spine` empty AND `non_spine` non-empty → **partial**. Reason: `no [spine]; only other types matched: [comma-separated type list]`.
3. `strong_spine` empty AND `weak_spine` non-empty → **partial**. Reason: `weak [spine] matches only — investigate`.
4. `strong_spine` non-empty:
   - Compute variation gaps from Step 2: a list of missing variations.
   - Read the end-to-end result from Step 3.
   - If variation gaps OR `end_to_end: no` → **partial**. Reasons: `missing variations: [list]` and/or `not end-to-end: [missing-step sentence]`.
   - Else → **covered**. No reasons.

#### Step 4a — Enumerated reasons (the ONLY allowed values)

The `reasons` field accepts ONLY the following strings (with bracketed placeholders filled in from the data). The skill MUST NOT emit any other reason text.

| Reason string | When |
| --- | --- |
| `no matching pages` | verdict `missing` (rule 1) |
| `no [spine]; only other types matched: [type list]` | verdict `partial` (rule 2) |
| `weak [spine] matches only — investigate` | verdict `partial` (rule 3) |
| `missing variations: [variation list]` | verdict `partial` (rule 4, variation gap) |
| `not end-to-end: [missing-step sentence from Step 3]` | verdict `partial` (rule 4, end-to-end check returned `no`) |
| `spine stage undocumented: [stage], owned by [product]` | verdict `partial`, cross-product journeys only (rule 4, a route named a stage it could not link) |
| `spine stage documented for a different audience: [stage], owned by [product]` | verdict `partial`, cross-product journeys only (rule 4, a route found prose for a stage but written for someone other than the journey's `users`) |

**The audience reason exists because "no documentation" and "documentation for the wrong reader" are different findings with different fixes.** The first needs someone to write a page; the second needs an existing page reframed, split, or supplemented — usually much less work, and it will never get done if the report calls it undocumented. It was added after a route reported a stage as having no documentation when in fact a thorough operator runbook covered it, and only the tenant's own question was unanswered.

Use it when prose for the stage exists and is usable as a source, but its intended reader is not among the journey's `users`. Do **not** use it to soften a genuine absence: if no prose covers the stage at all, the reason is `spine stage undocumented`.

`[spine]` is filled with the journey's declared spine — so a default journey still emits the exact strings `no how-to; only other types matched: …` and `weak how-to matches only — investigate`, and a wayfinding journey emits `no explanation; …`. It is a placeholder like `[type list]`, not a literal.

Editorial commentary in any other form is **prohibited**. Reasons like `"tangential matches"`, `"page only mentions the topic"`, `"doesn't cover this directly"`, `"only autoscaling docs touch it"` are all forbidden — those are judgements that belong in the journey-matching step, not here. If you cannot express the verdict's justification with one of the six strings above, the verdict logic produced an unexpected state — re-run the verdict procedure rather than inventing a new reason.

#### Step 4b — Mandatory self-consistency check (run before emitting Section 3)

For every journey row in Section 3 Subsection A, verify:

`row.strong_spine_count` MUST equal the count of pages in REPORT.md Sections 8 (Copied verbatim) and 9 (Rewritten) such that:
- the page's Diátaxis type equals this journey's declared `spine`, AND
- the page's `journeys` column lists this journey at confidence `strong`.

`row.weak_spine_count` MUST equal the same count restricted to confidence `weak`.

If the counts disagree, the per-page tags in Sections 8/9 are the **ground truth** — re-derive Section 3's counts from them. Do **not** "fix" the disagreement by changing tags in Sections 8/9. The journey-matching step is the only place where journey-relevance tags are decided, and it has already run for the day.

The check must pass for every row. If it cannot pass, the count was wrong, not the tags.

#### Step 4c — Worked example of the bug this prevents

Suppose journey "X" — default spine, so `how-to` — has two pages tagged `(X, weak)` in the journey-matching step, both with Diátaxis type `how-to`.

**Correct verdict**: `partial`, reason `weak how-to matches only — investigate` (rule 3 above).

**Incorrect verdict**: `missing`, with a freshly-invented reason like `"only tangential pages touch it"`.

The incorrect verdict is wrong on three counts:

1. The journey-matching step already labelled those pages as relevant — that's what `weak` means. The verdict step counts; it does not re-evaluate that label.
2. The reason string isn't in the enumerated list in Step 4a — it's editorial, which is prohibited.
3. The verdict contradicts the per-page tags REPORT.md Sections 8/9 will still emit, breaking cross-section consistency (Step 4b would fail).

If the agent is tempted to add custom reasoning, that's a signal the journey-matching step may have been too generous and the matches should be re-evaluated **there** — not filtered here.

#### Step 5 — Record

Per journey:

```yaml
name: "<journey name>"
kind: product | cross-product
spine: how-to | tutorial | explanation
verdict: covered | partial | missing
reasons: ["<reason 1>", "<reason 2>"]    # empty list when verdict is "covered"
strong_spine_count: N
weak_spine_count: N
non_spine_count: N
variations:                              # absent when journey has no variations
  - { name: "<variation>", covered: true | false }
end_to_end: yes | no | n/a               # "n/a" when the check was not run
products: ["<slug>", ...]                # cross-product journeys only
unrouted_stages:                         # cross-product journeys only; empty when fully routed
  - { stage: "<stage>", product: "<product>" }
```

### Part B — Diátaxis bucket coverage

Always runs, regardless of whether `journeys` is empty. Assesses all four buckets for the product — tutorial, how-to, reference, explanation — reporting each as filled or empty **with the reason it is empty**.

**This is an assessment, not a mandate.** The skill still does not assert that every product must carry all four. Whether an empty bucket matters depends on the product: a small CLI may legitimately have only how-tos, while a platform spanning several repositories usually needs all four. What changed is that an empty bucket is now *named and explained* rather than left as an unremarked zero, because "explanation: 0" tells a reader nothing about whether that is fine or a hole.

The distinction that carries the weight is **why** a bucket is empty:

| Reason | Meaning | Actionable by |
| --- | --- | --- |
| `no prose found` | Documentation for this type exists nowhere in the estate | A human writing docs |
| `prose found, not consolidated` | Material exists but was dropped at the shortlist cap or scored too low | Re-running with a better term set |
| `journey-shaped, no journey declared` | Material exists and is good, but the page it belongs on is a journey page, and no journey is declared to hold it | A human declaring the journey |
| `code-only` | The material exists but only as code, so strict provenance forbade writing it | A human documenting the code — see *undocumented surface area* |
| `not applicable` | The product genuinely has no material of this type | Nobody; this is fine |

`code-only` is the one to read first. It means the capability is real, the skill found it, and nothing may be written about it — the highest-value gap a run produces.

**Pick the reason that matches the facts, and check it against the rest of the report.** These strings are not interchangeable labels for "empty"; each names a different owner, which is the whole point of the vocabulary. Two failure modes to avoid, both observed:

  * **`no prose found` while another section of the same report describes the prose.** If *Journeys not covered* says material is "spread across four documents in three directories", the bucket cannot also claim it exists nowhere in the estate. Grep your own report before choosing.
  * **`prose found, not consolidated` when the cap was never reached.** Its definition is cap-or-score driven. If the report also states "dropped at the cap: 0", the string contradicts itself and the honest reason is a different one — most often `journey-shaped, no journey declared`.

`journey-shaped, no journey declared` exists because a product-only run has nowhere correct to put a product's central end-to-end path: writing it as a product-level how-to pre-empts the journey page and mis-tiers its audience, while calling the bucket empty for want of prose is false. It is actionable by declaring the journey and re-running, and it belongs in *suggested actions* naming the journey that would hold it. Do not stretch it to cover material that is **not** journey-shaped — a maintainer-tier task recipe tied to no journey belongs in the how-to bucket, and reporting the bucket empty because *one* candidate was journey-shaped is a misuse of this reason.

#### Step 1 — Count and classify

For each of the four buckets, record the page count and, where the count is zero, one reason from the table above:

```yaml
buckets:
  tutorial:    { count: 0, reason: no prose found }
  how_to:      { count: 3 }
  reference:   { count: 1 }
  explanation: { count: 2 }
```

Counts cover pages at the builder/maintainer tier plus product-level pages. **Journey spine pages are counted separately in Part A and never fill a product bucket**, whatever their type — a journey is an end-to-end path, not a product-level page, and conflating them hides that a product has no standalone material of that type. This applies to all three spines: a journey declared `tutorial` does not fill the product's `tutorial` bucket, exactly as a journey how-to has never filled its `how_to` bucket.

Cross-product journey pages never count toward any product's buckets either, for the stronger reason that they belong to no product.

Outliers do not count (they are not audience-tagged).

### Part C — Product brief

Always runs. One record per product:

```yaml
brief:
  present: true|false
  source: <the brief's `source` field, when present>
  captured: <ISO date>
  stale: true|false          # captured more than 180 days ago
  features_declared: true|false
```

`present: false` is a **product-level gap**, reported with this reason and no other wording:

> `no product brief — product page is navigation-only, and the term set for every journey under this product is degraded`

Both halves matter. The first is what a reader sees; the second is why the rest of the product's output is thinner than it looks, and omitting it invites the wrong conclusion — that the product is poorly documented, when in fact the skill was poorly equipped to look. See `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/product-definition.md`.

A brief that is present but `stale: true` is reported as a finding rather than a gap. An old product description is still a product description.

### Part D — Suspected mis-filing

Always runs for journeys under `products/`. Never runs for cross-product journeys — they are already declared as spanning products, so there is nothing to suspect.

A journey is declared under one product by a human. Discovery, meanwhile, searches every repository regardless of that declaration (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md`), so a run produces direct evidence about where a journey's documentation actually lives. Where the two disagree consistently, that is worth surfacing.

#### Step 1 — Compute the spread

For each journey under a product, take its evidence set and count evidence files by source repo, weighting each by relevance: `high` = 3, `medium` = 2, `low` = 1.

#### Step 2 — Flag

Flag the journey as **suspected mis-filed** when both hold:

  * the journey's own product contributed **less than half** the total weight, and
  * at least one other repo, or group of repos, contributed more

Report the flag with the weight distribution by repo, so a reader can see the shape rather than trust the threshold.

#### Step 3 — Report, never act

This is a **finding, not an instruction**. The skill does not move the journey, does not reassign its `products`, does not change its output path, and does not repeat the flag as a suggested action with an imperative verb. The next run behaves identically unless a human edits the definition.

Two reasons for the restraint, and the second matters more. First, classification is editorial: whether a journey is a Foglight journey or a cross-product one is a decision about what readers need, and evidence distribution is only one input to it. Second, the threshold is crude by design — a journey can legitimately draw most of its evidence from a config repo that belongs to nobody in particular, and a skill that acted on this would churn the site's URLs every time discovery shifted.

State it as an observation with its numbers:

> `Onboard to Foglight` — declared under `foglight`, which contributed 34% of evidence weight. Larger contributors: `foglight-config` (28%), `foglight-agent` (22%).

**Report by repo.** Where products declare `repos` (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/product-definition.md`), you may additionally group the repos under their owning products, since that grouping is then a declared fact rather than a guess. Report the repo-level numbers either way — they are what a human checks the grouping against, and a repo claimed by two products or by none is visible in the repo breakdown and invisible in the product one.

Do not guess which product owns a repo from its name. An undeclared repo stays undeclared in this report exactly as it stays unattributed in the attribution pass.

### Part E — Product inclusion

Runs **only** for cross-product journeys, and always for them — whether their `products` list was declared or derived. Never runs for journeys under `products/`; those have an owning product by declaration and Part D is their equivalent.

Part E reports the output of the product attribution pass in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md`. It computes nothing new. Its job is to make a derived product list inspectable, because a list the skill worked out is only trustworthy if a reader can see what it was worked out from.

#### Step 1 — Report the resolution

State whether the list was `declared` or `derived`, and for a derived list say that discovery produced it. A reader who assumes a human chose the products will misread every number that follows.

#### Step 2 — Report every product considered

One row per product with at least one attributed evidence file, **included and excluded together in one table**, sorted by descending evidence weight:

| Product | Included | Confidence | Weight | Share | High-relevance files | Attributed by | Why |
| --- | --- | --- | --- | --- | --- | --- | --- |

`Why` carries the rubric rows that fired, not prose — `3 high-relevance files, share ≥25%, routes_to match`. The rubric is the justification, and restating it as an opinion invites re-judgement, which is exactly what the derived-not-judged rule forbids.

**Excluded products belong in this table, not omitted from it.** A product that scored `low` and was left out is the most actionable row in the section: it is where a human either agrees, or declares `products` explicitly to overrule the derivation. A table showing only the winners cannot be checked.

#### Step 3 — Report attribution health

```yaml
unattributed: { weight: N, share: NN%, repos: [...] }
```

Report the unattributed share **beside the product scores, not beneath them**. Every share in Step 2 is a fraction of a total that includes unattributed weight, so a large unattributed share means every product's number is diluted by evidence nobody claimed. Above roughly a third, state plainly that the derivation is weakly grounded and that declaring `repos` on the products involved would change the result.

Name the unattributed repos. Each one is a concrete, one-line fix to a `product.md`.

#### Step 4 — Report disagreements

Only when `products` was declared. Two findings, both observations:

  * a declared product that attracted no evidence — the declaration may name a product that no longer participates
  * an undeclared product that outscored a declared one — the declaration may be missing a product

Neither is acted on. The declared list still governs what was published. Say which, so a reader does not have to infer it.

#### Step 5 — Report a failed derivation

When fewer than two products reached `high` or `medium`, no page was written. Report the journey with every product's score, the unattributed share, and the reason — its evidence landed inside one product. This is a gap, not an error, and the fix is a human's: either declare `products`, or move the journey under the one product its evidence points at, which only a human may do.

## What `partial` means

`partial` is a catch-all for "exists but not adequate." The reasons list makes the inadequacy specific. A journey may have multiple reasons concurrently (e.g. `missing variations: macos, windows` AND `not end-to-end: rollback step is not documented`). Reasons surface the actionable gap, not just the verdict.

## When `journeys` is empty

Part A is skipped entirely. Parts B and C always run. The report section still appears (see below); Subsection A's table is replaced with the line "No journeys were supplied for this run."

This is the normal shape of a **product-only run** — a pass that reorganises a product's existing documentation into the four buckets without authoring journey material. It is a supported mode, not a degraded one: `journeys = []` is valid whenever the run's purpose is product-level consolidation. See `SKILL.md`.

## Output — REPORT.md

A new section, "Coverage analysis", placed in the exec block of REPORT.md immediately after "Suggested actions" and before "Journey relevance summary". See `SKILL.md`'s Executive report format for the full section ordering. Two subsections:

### Subsection A — Journey coverage

A table with one row per supplied journey. Columns:

- **Journey** — name.
- **Spine** — the declared spine: `how-to`, `tutorial`, or `explanation`. Without it a reader cannot interpret the next column, since "0 strong" means something different depending on what was being counted.
- **Verdict** — `covered` / `partial` / `missing`, prefixed by a stoplight emoji for at-a-glance scanning: 🟢 `covered`, 🟡 `partial`, 🔴 `missing`. The emoji is always present; cell contents are e.g. `🔴 missing`, `🟡 partial`, `🟢 covered`.
- **Spine coverage** — `X strong, Y weak`.
- **Other types** — comma-separated counts where non-zero, e.g. `2 reference, 1 explanation`; `—` if none.
- **Variations** — for journeys with variations, a single cell with each variation marked `✓` or `✗`, e.g. `linux ✓ · macos ✗ · windows ✓`. `—` if no variations.
- **Reasons** — comma-separated reasons; empty for `covered`.

Sort: `missing` first, then `partial`, then `covered`. Within each verdict, preserve the order journeys appear in the input.

### Subsection B — Product-level coverage

A small block showing builder/maintainer audience page counts by Diátaxis type. This is **descriptive only** — the skill makes no assertion that any of these types must be present for a given product. The counts surface the composition so the reader can judge per product.

```
Builder/maintainer audience pages:
- Reference: 12
- Explanation: 5
- How-to: 8
- Tutorial: 2
```

No flags. No verdicts. No automatic suggested actions derive from this subsection.

## What this step does not do

- It does not detect duplication — that is the next step.
- It does not flag unclear, hollow, contradictory, or factually incorrect content — out of scope here.
- It does not suggest actions — that is the synthesis step.
- It does not modify per-page outputs or any earlier output.
- It does not classify or re-classify pages.

## Sources

The journey-coverage verdicts and variation-by-variation breakdown are original to this skill. The product-level Part B is purely descriptive: it reports counts by Diátaxis type at the builder/maintainer audience tier so stakeholders can see the composition without the skill asserting any particular tier must be present.
