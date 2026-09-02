---
name: Multi-repo source discovery
description: How the skill finds, ranks, and scores source material for a product/journey across many repositories. Load at the start of every authoring run, immediately after the product definition is resolved. Replaces the single-repo "Source scope" behaviour.
---

# Source discovery

This skill authors documentation from source repositories it does not own. Those repositories are large (thousands of files each) and were not written to be documentation inputs. This file specifies how to get from "many large repositories" to "a ranked, scored shortlist of evidence for one journey" without reading everything.

It replaces the single-repo `Source scope` section of `SKILL.md`. Load it once per run, after `${CLAUDE_SKILL_DIR}/references/product-definition.md` has resolved `product_name` and `journeys` — and **immediately afterwards load the consumer's estate adapter**, the file named by `source_discovery` in `.doc-settings/settings.md` (`${CLAUDE_SKILL_DIR}/references/settings.md`). This file is the method; the adapter is the estate: source-root derivation, repo scope, prior-art location, always-add candidates, expansion vocabulary and known contact-point traps. Nothing estate-specific lives here.

## Terminology

- **Source root** — a directory containing repositories to draw from. Resolved per `source_root` in the consumer's `settings.md`: either an absolute path, or the rule `parent-of-consumer-root`, meaning the parent directory of the **main** checkout of the consumer repository — never the parent of the working directory, because orchestrated runs execute in a git **worktree** whose parent is inside the repo. When a source root is supplied in your spawn prompt, use it verbatim. Otherwise derive the consumer root first (`${CLAUDE_SKILL_DIR}/references/settings.md`):

  ```bash
  CONSUMER_ROOT=$(dirname "$(git -C <repo root> rev-parse --path-format=absolute --git-common-dir)")
  SRC_ROOT=$(dirname "$CONSUMER_ROOT")
  ```

  In the main checkout this collapses to the plain parent directory, so it is correct in both cases.

  Do **not** hardcode a literal path, and do not rely on `~` expanding to the directory that holds the checkout — `$HOME` is not guaranteed to be the parent of the source tree, and a `~/`-relative path fails with "No such file or directory" where it is not. Derive the absolute path once at the start of the run and use it verbatim in every subsequent command.
- **Consumer repo** — the repository being documented, which holds `.doc-settings/`, `product-definition/` and the output root. It is normally one of the source repos too; the estate adapter says whether it is.
- **Source repo** — a direct child of the source root that is a git repository.
- **Candidate** — a file that survived the path or content pass and may be read.
- **Evidence** — a file that was actually read and contributed facts to an authored page.
- **Relevance** — how strongly a single evidence file relates to a journey. Scored `high` / `medium` / `low`.
- **Confidence** — how well-grounded an authored page is. Scored `high` / `medium` / `low`. Derived from its evidence set, not judged freehand.

`strong` and `weak` are reserved for journey-match confidence in `${CLAUDE_SKILL_DIR}/references/journey-matching.md`. Do not reuse those words here.

## Repo scope

1. Enumerate direct children of the source root. A child is a source repo if it contains a `.git` entry. Drop any named in `source_exclude_repos`.
2. **The consumer repo is a source repo like any other** when it sits under the source root — including its existing documentation, which is exactly the prose the output consolidates. Its **own output trees are excluded as sources**, because they are this skill's output and would let a run cite itself: every path in `source_exclude_paths` (the output root, the proposals and plan directory, the worktree directory). When the enumerated consumer repo is the main checkout and the run writes into a worktree of it, scan the main checkout as the source — the content is identical at the branch point, and the worktree's output root is in-flight output.
3. Do not exclude anything else by default. The user may pass an exclude list at invocation; honour it and record it in the report.
4. Print the resolved repo list and count back to the user as part of the run summary, before discovery begins.

If the source root does not exist or contains no git repositories, stop and tell the user. Do not fall back to scanning the current working directory.

### Path exclusions (all repos)

Apply these to every pass. They are not configurable — they exclude machine-generated and vendored content that produces false positives at high volume:

```
.git/  .worktrees/  node_modules/  vendor/  .terraform/  .venv/  venv/  __pycache__/
build/  dist/  target/  out/  bin/  .gradle/  .idea/  .mypy_cache/
testdata/  fixtures/  *.min.js  *.lock  *.sum  go.sum  package-lock.json
```

In the consumer repo only, additionally exclude the skill's own output — every path in `source_exclude_paths` (see Repo scope).

`test/` and `tests/` are **not** excluded — test names and fixtures are often the clearest statement of intended behaviour.

### Prior-art pass (existing documentation)

The consumer repo is a source repo, so its existing documentation reaches the funnel like any other prose — but the pages under `prior_art_roots` get one extra treatment, because they occupy the ground the authored pages will also occupy.

Before authoring, additionally scan every `prior_art_roots` entry — **always excluding the output root** — for pages matching the journey's term set. For each hit, record path and title.

**Prior art is citable evidence.** An existing page is a real file with real content, and for many journeys it holds facts that exist nowhere else in the estate: install commands, portal URLs, application IDs, request wording. Consolidating it into the output is in scope — the estate adapter says whether the output is intended to eventually replace the prior art, in which case lifting its content forward (duplicating it, since the original may later be removed) is the job, not a hazard.

  * It ranks at **authority tier 5 (prose)** — the lowest. When a prior-art page and a manifest disagree, the manifest wins and the discrepancy is recorded as a quality flag.
  * It appears in the page's `## Sources` section and in `doc_journeys.sources`, with `repo:` set to the consumer repo's directory name under the source root.
  * It **also** appears in `doc_journeys.prior_art`, which records overlap rather than contribution. A page can legitimately appear in both.
  * It counts toward the confidence rubric exactly as a prose source does — no bonus, no penalty. Prior art alone will not lift a page to `high`, because it cannot satisfy the authoritative-tier condition.

**Authoring in full is the default, and duplication is accepted.** Do not reduce a page to a stub of links because existing content covers the ground. Author the complete page — self-contained, so it survives the original's removal.

  * Cross-link to the covering pages from the body, so a reader can reach the canonical detail while it still exists.
  * Record every overlap in the report's Prior art section with an `overlap` of `full` or `partial`, whether or not you linked to it.
  * `${CLAUDE_SKILL_DIR}/references/duplication.md` will flag the overlap. Against prior art that flag is **reportable, not a defect** — it does not cause the page to be deleted or shortened. Overlap between two pages this skill authored in the same run is still a defect.

The cost of this choice is drift: two descriptions of the same procedure that can diverge until the original is retired. That is the reason the overlap is reported page by page rather than noted once — a human needs to be able to see exactly which pairs exist, and retire or reconcile the prior-art copy when the time comes.

**Contact points still need corroboration from outside the consumer repo.** Making its documentation a source does not weaken the contact-point rule in `${CLAUDE_SKILL_DIR}/references/authoring.md`: a Slack channel, group handle, or DL mentioned only in prior art is still uncorroborated, because the output repository's own prose is exactly the stale-mention risk that rule exists for. Anchor the exclusion to the repo root (`grep -v '^<consumer repo dir>/'`, or `--exclude-dir` at the tool level) when counting corroborations.

Never edit, move, or delete anything under a prior-art root, including content an authored page supersedes. Retiring a prior-art page is a human decision; the report may recommend it, the skill never performs it. The `write_locations` in the consumer's settings are the only things this skill writes.

## The funnel

Five passes, cheapest first. Each pass narrows the set the next pass operates on. Do not skip passes; do not reorder them.

### Pass 0 — Build the term set

From the resolved product definition, build a term set for the run and a per-journey term set for each journey.

**Product term set** — from `product.md` and the brief:
- The product `name`, plus each whitespace-separated token of it longer than 2 characters.
- Every entry in `features`, plus each token.
- From the **brief**, where one exists: capability and feature names, the systems and propositions it names, and the vocabulary it uses for its audiences. A brief is the richest term source available at product level, and mining it costs one read.

**When a product has neither a brief nor `features`, say so before proceeding.** The term set collapses to the product name and whatever each journey supplies, and discovery gets measurably worse for every journey under that product — not because the material is absent from the repos, but because there is nothing to search for it with. Record this in the run's discovery record and surface it in the report as the cause, so a thin result is not mistaken for a thinly documented product.

**Per-journey term set** — from the journey record, unioned with the product term set:
- `name` and its tokens.
- Content words from `description` (drop articles, prepositions, auxiliaries).
- Every entry in `variations`.
- Every entry in `users`.
- Every noun phrase from the journey body prose that names a system, tool, resource, or artefact.

**For a cross-product journey**, there is no single product term set to union with. Build it from **every product named in `products`** — each one's `name`, `features`, and brief — then union that with the journey's own terms. A journey crossing three products draws on three products' vocabulary, which is correct: it covers more ground than any of them.

#### When the product list is derived, the term set is built in two stages

A cross-product journey may omit `products` and have it derived (`${CLAUDE_SKILL_DIR}/references/product-definition.md`). That creates a circularity: the term set is built from the products, the products are attributed from the evidence, and the evidence is found using the term set. Resolve it by running discovery twice rather than by guessing an entry point.

**Stage 1 — journey-only term set.** Build the set from the journey's own material alone: `name`, `description`, `variations`, `users`, and every system, tool, resource and artefact named in its body prose. Add the expansion step as normal. Do not include any product's `features` or brief. Run passes 1–4 with this set, then run the attribution pass below to derive the product list.

This works because of an asymmetry the schema already relies on: a cross-product journey's body prose is the *richest* journey prose in the definition, since it has to name the stages it routes between, and those stage names are precisely the high-value discovery terms. A journey too thinly written to bootstrap its own discovery is too thinly written to route, and the right outcome is the thin-evidence path below rather than a fabricated product list.

**Stage 2 — enriched term set.** Union the stage 1 set with the `name`, `features` and brief vocabulary of every product attribution included. Re-run passes 1–4 with the enriched set. Evidence found in stage 1 is retained; stage 2 adds to it rather than replacing it.

Stage 2 is not optional and not a refinement. A product's feature vocabulary is what finds the material a journey never thought to name — the journey says "archive" and the product's features say `forward-logs`, `retention`, `cold-storage`. Skipping it produces exactly the thin route the two-stage design exists to prevent.

Two rules keep the second stage honest:

  * **Attribution is not recomputed after stage 2.** The enriched set is built from the products stage 1 selected, so scoring inclusion against evidence that set went looking for would confirm the choice by construction — every included product would find more of its own vocabulary and score higher. Derive once, from stage 1, and report those numbers.
  * **Record both term sets in the report.** A reader needs to see what the journey found on its own and what its products' vocabulary added, because a stage 1 set that found almost nothing is a signal about the journey's prose, not about the estate.

When `products` is declared, both stages collapse into the single-pass behaviour above: the products are known before discovery starts, so there is nothing to bootstrap. Attribution still runs on the resulting evidence, to corroborate the declaration.

Two consequences worth expecting rather than discovering:

  * The 40-term cap bites much harder here. Three products' features can exhaust it before the journey's own terms are added, and the journey's terms are the specific ones. **Add the journey's own terms first, then fill the remainder from the products**, dropping product-wide terms before journey-specific ones.
  * An unbriefed product in the list degrades the set **partially**, not fatally — the other products still contribute. Report degradation per product rather than as a single flag for the journey, so a reader can see it was two products out of three and which one is missing.

**Ownership never narrows the scan.** A journey declared under one product is still searched for across every source repo, exactly as a cross-product journey is. What a product declaration changes is the vocabulary the search uses, not its reach — which is why a single-product journey can, and often does, find most of its evidence outside its own product's repositories. `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part D reports when it does.

**Expansion.** For each term set, add plausible synonyms, abbreviations, and the concrete technology names the journey implies. This is a judgement step and is expected to be non-deterministic. The shape of the reasoning: a journey mentioning "deploy a workload" expands to `helm`, `chart`, `deployment.yaml`, `argocd`, `kustomize`, `pipeline`; one mentioning "ingress" expands to the estate's ingress controller and proxy names as well as `gateway` and `route`. The estate adapter carries the estate-specific vocabulary — component names a term like "ingress" should expand to here — and is the place to record expansions that proved productive.

Cap the expanded per-journey term set at 40 terms. Beyond that, precision collapses and every pass returns noise. If you have more than 40 candidate terms, keep the ones most specific to this journey and drop the generic ones (`config`, `service`, `platform`, `app` are almost always worth dropping).

Record the final per-journey term set. It goes in the report so a reader can see why a file was or was not found.

### Pass 1 — Path signal

Match the per-journey term set against **file and directory paths only**. No file contents are read.

```bash
# per source repo
rg --files <repo> | rg -i -e '<term1>' -e '<term2>' ...
```

Additionally, always add to the candidate set regardless of term match:

- `<repo>/README.md`, `<repo>/CONTRIBUTING.md`, `<repo>/DEVELOPMENT.md`
- everything under `<repo>/docs/` and `<repo>/documentation/`
- every **component identity file** the estate adapter names — a service-catalogue descriptor that names the owning team and the component's purpose

These are cheap, high-yield, and orient you in a repo you have not seen.

A path hit is a strong signal in these repos: a directory named after a component usually *is* that component. Weight path hits above content hits when ranking.

### Pass 2 — Content signal

Grep the per-journey term set across the candidate repos, counting **distinct terms matched per file**, not total matches. A file matching six different journey terms once each is far more relevant than one matching `platform` sixty times.

```bash
rg -i -c -e '<term1>' -e '<term2>' ... <repo> --glob '!{node_modules,vendor,.terraform,build,dist}/**'
```

Restrict to text-bearing extensions: `.md .txt .yaml .yml .json .tf .go .py .java .kt .sh .ts .js .tpl .gotmpl` and extensionless files in `bin/`-like locations only if the path pass already flagged them.

### Pass 3 — Rank and shortlist

Score each candidate:

| Signal | Points |
| --- | --- |
| Path contains a journey-specific term (not a product-wide one) | 5 each, max 15 |
| File is a repo `README.md` / `docs/**` page in a repo with ≥1 path hit | 4 |
| Distinct journey terms matched in content | 2 each, max 20 |
| File is a component identity file for a component with a path hit | 3 |
| File declares user-facing interface (see "Authoritative file types" below) | 4 |
| File is under `test/` or `tests/` | −2 |
| File is itself generated (`Generated by`, `DO NOT EDIT` in first 5 lines) | −10 |

#### Collapse repeated instances of the same data shape

Before ranking, collapse **instance directories** — directories holding many near-identical files that are per-tenant, per-namespace, per-cluster, or per-environment instances of one schema. Configuration repositories are full of them, and they will swamp the shortlist with files that all say the same thing.

The scale is real: measured on one estate, a single journey's path pass returned nearly nine thousand hits in one config repo, more than four fifths of them per-namespace quota manifests under one directory (the estate adapter records the figures). Shortlisting 40 of those teaches you nothing that the first one did not.

Detect an instance directory when a single directory subtree contributes **more than 20 candidates** whose filenames or immediate parent directories vary but whose structure does not. Then, instead of the individual files, add to the candidate set:

  * the `README.md` or documentation that **describes** the shape — this is the source, and it is what the page may be written from
  * the schema, chart, CRD or validating webhook that **defines** the shape — tier 1 or 2, so this corroborates the README and never replaces it. Where the definition documents a field the README does not, that field is undocumented surface area, not a gap to fill from the schema
  * **one or two representative instances**, preferring the most recently modified, as illustration
  * the **count** of collapsed files, and their common path prefix

Record the collapse in the per-journey discovery record: path prefix, count collapsed, and what was read in their place. A reader must be able to see that thousands of files were deliberately represented by two, not accidentally missed.

Never write a per-instance value from a collapsed directory onto a page as though it were a general default. One tenant's quota is that tenant's quota. Defaults come from the schema or chart, not from an instance.

Shortlist the **top 25 candidates per journey**, hard cap 40. If more than 40 score above zero, keep the top 40 and record the number dropped in the report — never silently truncate.

If fewer than 5 candidates score above zero, the journey has thin evidence. Do not pad the shortlist with low scorers. Proceed, and expect the confidence rubric below to return `low`.

### Pass 4 — Read and extract

Read every shortlisted file in full. While reading, follow one hop outward when a file points at something more authoritative: a README naming a chart directory, a Taskfile target naming a script, a Go file naming a config struct. One hop only — do not walk the graph.

**Authoritative file types.** This ordering ranks how close a file sits to the running system:

1. Deployed configuration and manifests — Helm `values.yaml` and templates, Kubernetes manifests, Terraform, Crossplane compositions.
2. Interface definitions — CRDs, OpenAPI/JSON schemas, protobufs, CLI flag definitions, `Makefile` / `Taskfile.yaml` targets.
3. Source code implementing the behaviour.
4. Tests asserting the behaviour.
5. Prose in READMEs, `docs/`, the existing site, and product briefs.

**Read what this ordering is and is not for.** Under the strict provenance rule in `${CLAUDE_SKILL_DIR}/references/authoring.md`, tiers 1–4 are **corroborators**: nothing may be written from them. Tier 5 is the only tier a page may be written *from*. So this list does not rank what to write — it ranks what confirms what you wrote, and how much weight a confirmation carries.

The effect is that the ordering runs backwards from how it reads. Prose sits last because it is the most likely to be stale, yet it is the only permitted origin. That tension is the point: prose is where documentation lives, and code is how you find out the documentation has rotted.

When prose and a manifest disagree, **write neither**. The manifest wins on the question of fact but loses on the question of what may be published, so the correct output is no claim plus a reported conflict, naming both values and both paths. Silently writing the manifest's value would be authoring from code through the back door.

Where a disagreement **admits a scoping reading** — the two values could both be true under different scopes, so it might not be a conflict at all — take the conservative reading: treat it as a conflict, apply the conflict's confidence penalty, and record the scoping alternative in the report. Do not stop to ask which reading to take, and never pick the reading that flatters the page's confidence. A run once put exactly this choice to its gate ("I would rather you chose than have me pick the flattering one"); this default is the answer.

Two related standing rulings, so runs stop re-deriving them: a **generated file whose content blocks are human-written prose** (e.g. assembled from tenant-authored comments) is a permitted tier-5 source — the generation is transport, not authorship — cited as the generated artifact; and the `-10` generated-file rank penalty does not apply to such files.

One exception sits above this ordering: for **product-level claims** — what a product is, who it serves, why it exists, what is GA — a product brief outranks everything, including tier 1. No manifest encodes intent, and the product owner wrote the brief. The same brief is near-worthless for a field list. See `${CLAUDE_SKILL_DIR}/references/product-definition.md`.

**One class of fact escapes this ordering entirely.** Slack channels, group handles, distribution lists, and other live external entities are not described by any tier of file — they are merely *mentioned* by them, and a mention survives the thing it names being renamed or deleted. Even a tier 1 manifest only proves the value was well-formed when it was written. These require the separate corroboration rule in `${CLAUDE_SKILL_DIR}/references/authoring.md`; do not treat a single prose mention as sufficient evidence for one just because prose was the only place it appeared.

## Relevance scoring (per evidence file)

Assign each file that contributed facts:

- **high** — the file is about this journey. It names the journey's subject in its path or title, and its content is substantially about performing or configuring it.
- **medium** — the file covers a component the journey depends on, or one step of the journey, but is not about the journey as a whole.
- **low** — the file mentioned journey terms and was read, but yielded at most incidental context.

Record relevance per file. Files scored `low` that contributed no facts are dropped from the evidence list entirely — they are noise, not evidence.

## Product attribution (cross-product journeys)

This pass answers **which products a cross-product journey actually crosses**, and how strongly each belongs. It runs after relevance scoring, on the stage 1 evidence set, for every cross-product journey — whether its `products` list was declared or omitted. When the list was omitted the result *is* the list; when it was declared the result corroborates it and disagreements are reported. See `${CLAUDE_SKILL_DIR}/references/product-definition.md`.

It does not run for journeys under `products/`, which have an owning product by declaration. The related-looking check on those is *suspected mis-filing* (`${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part D), which reports by repo and changes nothing.

### Step 1 — Attribute each evidence file to products

For each file in the evidence set, decide which products it belongs to. Two mechanisms, in order:

1. **By repo — the strong mechanism.** The file's source repo appears in a product's `repos` list. This is a declared fact about ownership, so attribution by repo is as reliable as the declaration.
2. **By vocabulary — the fallback.** Used only for a product that declares no `repos`, and for evidence whose repo no product claims. The file is attributed to a product when its content matches that product's `features` or brief vocabulary at three or more distinct terms. Weaker by construction: it infers ownership from what a file *talks about* rather than from where it *lives*, and a file can discuss a product at length without belonging to it.

A file may be attributed to several products, to one, or to none.

**Weighting.** A file's weight is its relevance — `high` = 3, `medium` = 2, `low` = 1. Where a file is attributed to *n* products, each receives `weight / n`. A config repo claimed by four products contributes a quarter of its weight to each, so no shared repo can pull a product into a journey on its own.

**Never attribute from a repository's name.** A repo called `foglight-agent` looking like it ought to belong to the Foglight product is not evidence that it does. If no `repos` list claims it and its content matches no product's vocabulary, it is unattributed. This is the same refusal `${CLAUDE_SKILL_DIR}/references/gap-analysis.md` Part D makes, for the same reason — an inferred mapping is wrong exactly when it matters, on the shared and oddly-named repositories.

**Prior art is attributed by vocabulary only.** Evidence from the consumer repo carries its directory name as `repo`, which belongs to no product and must never be added to a product's `repos`. For cross-product journeys the existing documentation is often the largest single contributor, so leaving it unattributed would strand most of the evidence — run the vocabulary mechanism over it and record that this is how those files were attributed.

### Step 2 — Compute inclusion confidence per product

**Derived, not judged**, on the same pattern as page confidence. For each product with at least one attributed file, start at zero and add:

| Condition | Points |
| --- | --- |
| ≥3 attributed files at relevance `high` | 3 |
| 1–2 attributed files at relevance `high` | 2 |
| No `high`, ≥3 attributed at `medium` | 1 |
| Product's share of total attributed weight ≥ 25% | 2 |
| Product's share of total attributed weight 10–24% | 1 |
| The journey's `routes_to` resolves to a journey declared under this product | 2 |
| The product declares a `feature` matching a journey term set entry | 1 |
| Every attributed file came from a repo shared with another product | −1 |
| Attribution rested entirely on the vocabulary fallback | cap the result at `medium` |

Map the total: **≥6 → high**, **3–5 → medium**, **≤2 → low**.

The `routes_to` row is worth two points because it is the only input a human wrote *about this journey specifically*. Evidence weight says a product owns material the journey touches; `routes_to` says the author intended the reader to be sent there. That is a stronger statement about crossing than any amount of term overlap.

The vocabulary cap is a ceiling, not a penalty — a product attributed only by vocabulary can still be `medium` and still be included. It cannot reach `high`, because nothing has confirmed that the files discussing it are files it owns.

### Step 3 — Resolve the list

  * Products at `high` or `medium` are **included**, ordered by descending evidence weight. Where `products` was declared, the declared order wins — it is the order the reader meets them, which is editorial and not derivable from weight.
  * Products at `low` are **excluded and reported**, with their score and evidence. This list is the useful half of the output: it shows what was considered and rejected, which is what a human needs in order to overrule the derivation by declaring `products` explicitly.
  * **Fewer than two products included** means the evidence does not support a cross-product journey. Write no page. Report the journey with every product's score, including the excluded ones, and the reason: its evidence landed inside one product. Do not reduce it to a single-product journey — that is a scope change, and scope is declared.

### Step 4 — Record

Per cross-product journey:

```yaml
products_resolution: declared | derived
products_declared: ["<slug>", ...]        # present when declared
products_included:
  - slug: <slug>
    confidence: high | medium | low
    evidence_weight: N
    share: NN%
    high_relevance_files: N
    attributed_by: repos | vocabulary | both
    reasons: ["<rubric row>", ...]
products_excluded:                         # same shape; empty list is meaningful, print it
  - slug: <slug>
    confidence: low
    ...
unattributed:
  weight: N
  share: NN%
  repos: ["<repo>", ...]
disagreements:                             # only when products was declared
  - declared_but_no_evidence: ["<slug>", ...]
  - undeclared_outscored_declared: ["<slug>", ...]
```

`unattributed.share` is the health metric for the whole pass. A high share means `repos` declarations are incomplete, and every product's score is computed against a total that is partly noise — so report it next to the scores rather than in a footnote. Above roughly a third, say plainly that the derivation is weakly grounded and that declaring `repos` would change the answer.

## Confidence scoring (per authored page)

Confidence is **derived, not judged**. Compute it from the evidence set:

Start at zero and add:

| Condition | Points |
| --- | --- |
| ≥3 evidence files at relevance `high` | 3 |
| 1–2 evidence files at relevance `high` | 2 |
| No `high`, ≥3 at `medium` | 1 |
| ≥1 claim on the page corroborated by tier 1 or 2 evidence | 2 |
| Evidence spans ≥2 source repos and they agree | 1 |
| Every step or field on the page traces to a cited source | 2 |
| Page required an inferred step with no direct source | −2 |
| Evidence sources contradict each other and it was not resolvable | −2 |
| A claim was dropped as undocumented surface area | −1 |

Map the total: **≥6 → high**, **3–5 → medium**, **≤2 → low**.

The corroboration row replaces an earlier condition that awarded points for *citing* a tier 1–2 file. Under strict provenance no page ever cites one, so that condition could never fire and every page would have been capped a band too low. The intent is unchanged — a page anchored to something machine-checkable is worth more than one resting on prose alone — but it is now earned by confirming prose against a manifest rather than by writing from the manifest.

The penalty row is deliberately small. A page that had to drop one undocumented field is still a good page; it should sting slightly and be visible in the report, not be pushed to `low`.

Every authored page carries its computed confidence in frontmatter and in the report. A `low`-confidence page is still written — it is a starting point for a human — but it must additionally carry the reviewer banner specified in `${CLAUDE_SKILL_DIR}/references/authoring.md`.

Per-journey confidence is the **lowest** confidence among its authored pages, not the average. A journey is only as trustworthy as its weakest page.

## Recording

Discovery output feeds two consumers. Produce both:

1. **Per-page evidence list** — for frontmatter and the page's Sources section: repo, path, relevance, and one line on what it contributed.
2. **Per-journey discovery record** — for the report: term set used, candidates found per pass, shortlist size, number dropped at the cap, repos that yielded nothing.

A repo that yielded nothing for a journey is a reportable finding, not an omission. It tells the reader where the skill did not look successfully.
