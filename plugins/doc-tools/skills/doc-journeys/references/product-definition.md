---
name: Product definition input schema
description: How the skill ingests product and journey context — folder layout, file schemas, product briefs, cross-product journeys, paste fallback, parsing rules, and execution gating. Load at the start of every run, before any scanning or classification.
---

# Product definition

This file specifies how the skill resolves the two values it cannot proceed without:

- `product_name` — a single string identifying the product the documentation belongs to.
- `journeys` — a list of journey records (possibly empty) representing the things a user does with the product.

It also resolves two optional inputs that are not blocking but strongly shape the output:

- `brief` — the product owner's description of what the product is. See *Product briefs*. Its absence is valid, is never an error, and is always reported.
- `cross_product_journeys` — journeys belonging to no single product, resolved once per run rather than per product. See *Cross-product journeys*. An empty or absent set is valid.

There are two routes to resolve these values:

1. A `product-definition/` folder at the root of the repository being scanned (preferred — durable, version-controlled, reusable).
2. An interactive paste fallback at run time (used when the folder is missing or invalid).

This file defines the input data shape. Downstream steps consume the resolved values: journey matching uses `name`, `description`, and `variations`; audience tagging uses `users`; gap analysis uses `variations`; suggested-actions synthesis uses the journey list and verdicts. `feature` is currently informational (preserved in the input but not consumed by any downstream rule).

## Location

**In `author` and `plan` modes**, the product definition lives at:

```
<repo root>/product-definition/
```

That is the **consumer repository** — the one that holds `.doc-settings/` and the output root (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/settings.md`) — not the root of a repository being scanned. This skill draws from many repositories and writes to one, so the definition belongs with the output repository, version-controlled alongside the documentation it produces.

**In legacy `audit` mode**, the original behaviour applies: the skill looks for `product-definition/` at the root of the single repository being scanned.

The folder is created and maintained by the human running the skill, and the skill **never moves, renames or deletes anything inside it**, in any mode. It may do exactly three things, each only with confirmation of the exact contents or diff at the pre-write gate: create a declaration the request needs and the folder does not supply (expanding a product's `journeys/` set this way is expected where the consumer's `authorisations` file says so), amend a **`product.md`** (e.g. adding `features` a new journey needs, adding `repos`, correcting a value), and append a newly declared product's slug to `catalogue.md`. All are specified under *Declaring from a run* below. Briefs and existing journey and cross-product-journey declarations are read-only.

If the folder is absent the skill falls back to the paste flow described below; it does **not** scaffold the folder from pasted input.

## Folder layout

**The multi-product layout is the expected one.** A consumer repository typically documents many products, and they do not arrive together or in the same state — some have a brief, some do not, and products are added one at a time. The single-product layout is retained only for a repository documenting exactly one thing.

Both may not be present at once — if they are, the multi-product layout wins and the single-product files are ignored, with a warning.

### Multiple products (expected)

```
<repo root>/
  product-definition/
    catalogue.md                 # optional — declares batch order and exclusions
    products/
      <product-slug>/
        product.md               # required — the declaration
        brief.md                 # optional — see "Product briefs" below
        journeys/
          <journey-slug>.md      # journeys owned by exactly one product
          ...
        weightings.md            # optional, per product
      <product-slug>/
        ...
    cross-product-journeys/      # optional — see "Cross-product journeys" below
      <journey-slug>.md
      ...
```

`cross-product-journeys/` is a **peer of `products/`, not a child of one**. A journey that spans several products belongs to none of them, and filing it under the first-listed product is precisely the mistake this folder exists to prevent.

### Single product

```
<repo root>/
  product-definition/
    product.md
    brief.md               # optional
    journeys/
      <journey-slug>.md
      ...
    weightings.md          # optional
```

Each `products/<product-slug>/` directory is exactly the single-product layout. Everything specified below about `product.md`, `brief.md`, `journeys/`, and `weightings.md` applies unchanged within it.

The directory slug is a filesystem convenience only. A product is identified by the `name` field in its `product.md`, exactly as a journey is identified by the `name` in its frontmatter.

Batch order is the order given in `catalogue.md` when present, otherwise alphabetical by directory slug.

- `product.md` — one file per run. Holds product-level metadata.
- `journeys/` — a folder of one-file-per-journey markdown files. Slugs are arbitrary; the skill identifies a journey by the `name` field in its frontmatter, not by filename.
- `weightings.md` — optional. Declares the ideal per-journey content weighting and per-Diátaxis-type mix used by the weighting analysis step. Schema, parsing rules, and behaviour when absent are defined in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/weightings.md`. The skill does not error if this file is missing.

The skill MUST exclude `product-definition/**` from the documentation scan, regardless of include globs supplied by the user. Journey files and the weightings file are inputs to the skill, not documentation to classify.

## `product.md` schema

Markdown document with YAML frontmatter. The body is free-text and is not parsed.

`product.md` is **the declaration, not the description.** It says which product this is; its journeys are the files under `journeys/`, not a field here — the schema below is the complete list of fields, and any `journeys:` key found in a `product.md` is inert and should be reported rather than read. Everything descriptive belongs in `brief.md`. Keeping them apart is what allows a product with no brief to still be declared and documented.

| Field      | Required | Type             | Notes                                                                                                                       |
| ---------- | -------- | ---------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `name`     | Yes      | non-empty string | The product name. Resolves to `product_name` for the rest of the skill.                                                     |
| `owners`   | No       | list of strings  | The product's owners — team names, individual names, or both. Optional because a product without a brief often has no recorded owner. Absent means the product page says nothing about ownership, never that it is unowned. |
| `features` | No       | list of strings  | The product's features. Free-text labels. Verb-shaped names ("run-workload") are preferred over noun-shaped ("compute"). |
| `repos`    | No       | list of strings  | Source repo directory names this product owns. The **repo-to-product mapping**, used to attribute discovered evidence to products. See *Declaring which repos a product owns* below. |
| `brief`    | No       | string           | Relative path to the brief, when it is not the default `brief.md`. |

**`features` is load-bearing, not decorative.** `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` Pass 0 builds the journey term set from every entry plus each of its tokens. Absent `features` produces a thinner term set, weaker discovery, and lower-confidence pages across **every journey under that product** — see *When a product has no brief* below. Where a brief exists, copy its feature list here in slug form rather than leaving the field empty; the brief's prose is not a substitute, because the term set needs stable tokens rather than sentences.

Example:

```yaml
---
name: Foglight
owners: [Foglight Platform Team]
features: [collect-telemetry, build-dashboards, forward-logs, alerting]
repos: [foglight-platform, foglight-config]
brief: brief.md
---
```

## Declaring which repos a product owns

`repos` is the only repo-to-product mapping in the definition, and nothing else can supply it. A product is an umbrella over several repositories; which repositories is a fact a human knows and no file in the estate states. `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/gap-analysis.md` Part D has always refused to infer it — "do not guess which product owns a repo from its name" — and that refusal stands. `repos` does not replace the refusal; it removes the need for it by letting a human declare the answer.

It is consumed by the **product attribution pass** in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md`, which decides which products a cross-product journey actually crosses and with what confidence. Two consequences follow, and the second is the one that surprises people:

  * A repo named in no product's `repos` list is **unattributed**, not unassigned to the nearest plausible product. Its evidence still counts toward a page's confidence — it is real evidence — but it contributes to no product's inclusion score, and the run reports it. A large unattributed share is the signal that `repos` is incomplete.
  * Two products may legitimately claim the same repo. A shared config repo genuinely serves several products, and forcing an exclusive assignment would misrepresent it. Evidence from a repo claimed by *n* products contributes to each of them at `1/n` weight, so a shared repo cannot single-handedly pull a product into a journey.

`repos` holds **directory names as they appear under the source root**, not URLs and not display names — `foglight-agent`, not `example-org/foglight-agent`. A name matching no directory under the source root is a warning: either the repo is not checked out, in which case attribution silently loses that product's evidence, or the entry is a typo. Both are worth seeing, and neither is an error.

Absent `repos` is valid and degrades gracefully. Attribution falls back to matching evidence against the product's `features` and brief vocabulary, which is weaker because it infers ownership from what a file talks about rather than from where it lives, and it caps that product's inclusion confidence at `medium`. Declaring `repos` is the single cheapest way to improve a cross-product run.

## Product briefs

A **brief** is a product-owner-authored description of what a product is: what it does, what it deliberately does not do, who it is for, why it was built, and what state its capabilities are in. It is optional, and it is the single highest-value input this skill receives.

It matters more here than the word "optional" suggests. A product in this estate is an umbrella over several services and repositories that are otherwise discoverable only as separate repos. Nothing in those repos states what the product *is* — a repository explains its own component, never the concept that groups it with four others. For many readers the product is a new concept entirely, and the question they arrive with is "what is this and is it for me?", not "what fields does this resource have". The brief is the only source that answers it. Without one, the skill can document how to *use* a product while remaining unable to say what it *is*.

### Location and precedence

Default path `brief.md`, beside `product.md`, or the path given by `product.md`'s `brief` field. Absent is valid and triggers the behaviour in *When a product has no brief*.

### Frontmatter

A brief is normally a **snapshot of a page that lives elsewhere**, so it carries its own provenance. These fields are to a brief what `repo_head` is to a source file — the handle that makes staleness detectable:

| Field | Required | Notes |
| --- | --- | --- |
| `source` | Yes | Human-readable name of where it came from, e.g. `Product Hub — Foglight`. |
| `source_url` | No | The original URL, where one is known and stable. |
| `source_updated` | No | The **source page's own** last-updated date, when it publishes one. Not the capture date. |
| `source_updated_by` | No | Who last updated the source page, when published. |
| `captured` | Yes | ISO date the extract was taken. Drives the staleness flag. |
| `captured_from` | No | How, e.g. `mhtml export`, `manual transcription`. |

A brief whose `captured` date is more than **180 days** old is flagged in the report as possibly stale. Flagged, not rejected — an old brief is still far better than none.

### The extraction procedure

The skill cannot reach the wiki or product hub these come from. Extraction is therefore **human-triggered input preparation, not part of a run**: it always begins with a person supplying a file.

1. A human exports the page — MHTML preserves a page that requires authentication, which is the usual case.
2. Decode it: MHTML parts are quoted-printable or base64, so raw text search over the file finds nothing and reading it undecoded is worthless. Walk the `text/html` and `text/plain` parts, decode each, strip `script`/`style`/`nav`/`footer`, remove tags, unescape HTML entities, and collapse the whitespace.
3. Write the result as markdown with the frontmatter above, preserving the page's own structure — headings, feature tables with their status values, personas, roadmap, team.
4. Commit the extract. Leave the export itself gitignored: it is mostly base64 images, and only the text is worth versioning.

**Extract faithfully, including errors.** A brief records what the source said. Do not correct, improve, or omit a claim because it looks wrong — a wrong claim in a brief is evidence about the source, and the authoring rules downstream decide what actually reaches a page. Where a known-bad claim is preserved, mark it with an HTML comment that says it is not part of the source.

### A brief's authority depends on the claim

Unusually, one source sits at two different heights depending on what is being asserted:

  * **For product-level claims** — what the product is and is not, who it serves, why it exists, which capabilities are GA — the brief is the **most authoritative source available**, above code and manifests. The product owner wrote it, and no manifest encodes intent.
  * **For technical claims** — field names, commands, defaults, versions — it is **weak**, near the bottom with other prose. It is promotional in register and nothing validates it.

So a brief may be cited for "Foglight does not store raw request bodies" and must not be cited for a resource's field list, even when it mentions one.

### When a product has no brief

The product is still documented. Three things change:

  1. **The product page is navigation-only** — `<product>/_index.md` carries its title and journey index and nothing else. No "what it is", no feature table, no audiences. This follows the existing rule that `_index.md` files are navigation rather than content; the alternative is inventing a description, which the grounding contract forbids.
  2. **The report records a product-level gap**, per `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/gap-analysis.md`.
  3. **Discovery degrades for every journey under that product**, because a product without a brief usually has no `features` either, and the term set is built from them. This is worth stating plainly in the report: a missing brief is not a cosmetic gap confined to one page, it measurably weakens every page produced for that product.

Do not substitute a repository README for a brief. A README describes a component; a brief describes a product. Using one as the other produces a product page that describes whichever repo happened to rank highest, which is worse than an acknowledged gap because it looks like an answer.

## `journeys/<slug>.md` schema

Each file holds one journey. Markdown document with YAML frontmatter. The body is free-text describing the journey (expected outcome, prerequisites, edge cases); the skill reads the body but does not parse it. Richer prose helps downstream steps match documentation to the journey.

| Field         | Required | Type             | Notes                                                                                                                                                              |
| ------------- | -------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `name`        | Yes      | non-empty string | The journey name. Should be evergreen — chosen at an abstraction level that survives implementation churn.                                                         |
| `description` | No       | string           | One- or two-sentence description of the journey.                                                                                                                   |
| `users`       | No       | list of strings  | The user(s) who perform this journey. Plain labels — e.g. `[end-user]`, `[platform-engineer, sre]`.                                                                |
| `feature`     | No       | string           | The product feature this journey belongs to. **Must** match an entry in `product.md`'s `features` list; a mismatch is a warning, not an error — see below. |
| `spine`       | No       | string           | The Diátaxis type of the journey's end-to-end page: `how-to` (default), `tutorial`, or `explanation`. See *Choosing a spine* in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/authoring.md`. |
| `variations`  | No       | list of strings  | Distinct paths through the same journey — e.g. `[dev, staging, prod]`, `[stateless, stateful, cron-job]`, `[linux, macos, windows]`. Variations are paths through one journey, not separate journeys. |

**`feature` mismatches are now warned about.** They were previously ignored. Two things changed: `features` became load-bearing for the term set, and the product/feature split became something a human maintains in a source of record outside this repository. A journey tagged `feature: egress` under a product whose `features` omits `egress` is the visible symptom of a stale `product.md`, and it costs every journey under that product a weaker term set. Warn, record it in the report, and proceed — never reject the journey, and never silently add the feature to the product.

Example:

```yaml
---
name: Deploy a workload
description: Get an application built and running on the platform, reachable by its consumers.
users: [end-user, application-developer]
feature: run-workload
variations: [stateless, stateful, cron-job]
---

# Deploy a workload

To deploy a workload the user needs a built image, a target environment, and credentials...
```

## Cross-product journeys

A **cross-product journey** is one a reader completes by using several products together. It lives in `product-definition/cross-product-journeys/`, is owned by no product, and is published to its own top-level section — see `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/output.md`.

It uses the journey schema above, with three differences:

| Field | Required | Type | Notes |
| ----- | -------- | ---- | ----- |
| `products` | No | list of strings | Product slugs the journey passes through, **two or more**, in the order the reader meets them. **Omit it to have discovery derive it** — see *Deriving the product list* below. When given, it is authoritative and discovery corroborates it rather than replacing it. |
| `routes_to` | No | list of strings | Names of the product journeys this one hands off to. Each must resolve to a journey `name` somewhere under `products/`. Unresolvable entries are a warning, not an error — the target journey may not be defined yet. |
| `feature` | — | — | Not used. A cross-product journey belongs to no single product and therefore to no single product's feature. |

### Scope is declared; the product list may be derived

These are two different decisions and only one of them is a human's to make.

**Scope is declared, never inferred.** A journey is cross-product because a human put it in `cross-product-journeys/`. The skill never promotes a product journey to cross-product, never demotes a cross-product journey, and never moves either between sections. Whether a reader is best served by a route or by a procedure is an editorial judgement about audience, and evidence distribution is a poor proxy for it. This guardrail is unchanged.

**Which products a cross-product journey crosses may be derived.** That is a question of fact — where does the documentation for this journey's stages actually live — and discovery answers it directly and better than a human writing a list from memory. A hand-written `products` list goes stale silently as repositories move; a derived one is recomputed every run and carries its evidence.

The distinction is worth holding onto, because collapsing it in either direction breaks something. Deriving scope would churn the site's URLs whenever discovery shifted. Requiring a hand-written product list makes a journey undeclarable until someone has already worked out the answer the skill is capable of computing.

### Deriving the product list

When `products` is absent, `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` runs its **product attribution pass** and derives the list. The journey is published with the derived products, and the report carries a per-product inclusion confidence with the evidence behind it.

  * A product reaching inclusion confidence `high` or `medium` is **included**.
  * A product at `low` is **reported but excluded**, with its evidence, so a human can see what was considered and overrule it by declaring `products` explicitly.
  * Deriving **fewer than two** products means the evidence does not support a cross-product journey. Do not publish the page and do not silently reduce it to a single-product journey — that would be reclassifying scope, which is forbidden above. Report it: the journey stays declared, this run produced no page for it, and the reason is that its evidence landed inside one product.

When `products` **is** declared, the derivation still runs, purely to check it. Disagreements are reported and never acted on:

  * a declared product that attracted no evidence — the list may name a product that no longer participates
  * an undeclared product that attracted more evidence than a declared one — the list may be missing a product

Both are findings for a human. The declared list still determines what is published, what `doc_journeys.products` records, and what the route's stages are attributed to.

The skill still reports the evidence for a *scope* change — see *suspected mis-filing* in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/gap-analysis.md` — and still acts on it never.

### These journeys orient; they do not restate

A cross-product journey exists because a reader has a goal and does not know which products serve it. Its page answers "what do you need, in what order, and where is each part documented" — and then links. It does not re-document the steps that the product journeys already cover.

This matters because the two journey lists overlap by design. "Expose a service to users" and "Expose an application to consumers outside the Kubernetes cluster" are the same ground at two levels of abstraction: one is wayfinding, one is the procedure. Written as two end-to-end procedures they compete, drift, and neither ends up authoritative. Written as a route plus a procedure they compose.

The page contract is in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/authoring.md`; `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/duplication.md` checks that the restating failure mode did not happen.

### Discovery is not narrowed by ownership

Declaring a journey under one product does **not** restrict discovery to that product's repositories. The funnel in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` runs across every source repo for every journey regardless of ownership, and always has. What ownership affects is the *term set* — which is why a cross-product journey unions the features and briefs of all its declared products.

Example:

```yaml
---
name: Expose a service to users
description: Get an application running inside the cluster reachable by consumers outside it, with a name they can resolve and a certificate they trust.
users: [end-user, application-developer]
# products omitted — discovery derives it and reports the inclusion confidence
# for each product it considered. Declare it to override the derivation.
routes_to:
  - Expose an application to consumers outside Kubernetes Cluster
  - Create or use a custom DNS name
spine: explanation
---

# Expose a service to users

A reader arriving here knows they have a service and needs it reachable. They do
not yet know whether they need ingress, DNS, a certificate, an egress rule, or
all four...
```

## Declaring from a run

A request may name a journey no file declares, a journey that belongs to no one product, or a product with no directory at all. **This is not a defect in the request.** Journeys are frequently discovered by documenting them: the run is how anyone finds out that the GCP path is undocumented or that the requester's side is a separate journey. Refusing until a human hand-writes the declaration first asks them to answer, cold, the questions the run exists to answer.

So the skill may **create** the missing declaration — and only create. The rules:

1. **Only what the request names.** One journey requested, one journey file. Not the sibling journeys discovery turned up, not a `repos` line a neighbouring product is missing, not a `features` list you would have written differently. Those go in the report.
2. **The human confirms the exact contents**, at the same gate that already confirms the plan, seeing the frontmatter and body as they will be written. This is the whole of the authorisation; a confirmation of "the plan" that never displayed the file does not carry it.
3. **Never modify an existing file** under `product-definition/`, with one bounded exception: appending a newly declared product's slug to `catalogue.md`'s `products` list, per *Keeping the catalogue current* below. In particular, not the `product.md` of the product a new journey sits under — nothing there needs to change, because journeys resolve by scanning `journeys/*.md` for the `name` in each file's frontmatter, and `journeys` is not a field in the `product.md` schema at all.
4. **`feature` must match** an entry in the product's existing `features`, or be **omitted**. It is an optional field, and omitting it is honest. Inventing a value that fires the mismatch warning in order to look complete is the worst of the three options: it manufactures the exact symptom that warning exists to report.
5. **Say which values were proposed rather than found.** `users`, `spine` and a new product's `features` are judgement calls that frame everything the run then writes, and a reader six months later cannot tell a researched value from a guessed one unless the file says. Record it in the run report, and where the file's own body explains the choice, in the body.
6. **Declare narrowly on one-sided evidence.** Where every source describes one audience — the operator's runbooks, say, and nothing from the requester's side — `users` names that audience and the report names the other path as an undeclared journey. Widening `users` to cover an audience no evidence describes produces pages that address a reader they were never written for.

**Ordering.** The declaration is proposed at Process step 1, refined by discovery, confirmed at the step 6 gate with the rest of the plan, and **written first in step 7 — before any page.** It cannot be written before discovery, because discovery is what tells you the `description`, the `spine` and whether the evidence is one-sided; and it must not be written after the pages, because the declaration is an input to the run rather than a record of it, and writing it last makes the run unreproducible from the definition and inverts the dependency in the history.

If the human's edits at the gate change a value that feeds discovery — a new product's `features`, most of all — the term set that produced the confirmed page set is no longer the one the definition implies. Do not write and proceed. Re-run discovery with the corrected vocabulary and return to the gate with the revised plan. A page set confirmed against superseded inputs is not a confirmed page set.

Two nearby rules are unchanged and are not this. A cross-product journey whose `products` names a slug with no directory is still reported and skipped — the definition contradicting itself is a different thing from the request naming something new, and the fix is a human's. And a `feature` mismatch in an existing journey is still warned about and never repaired by adding the feature to `product.md`.

## `catalogue.md` schema (multi-product only)

Optional. Declares which products are in the batch and in what order. Markdown document with YAML frontmatter; the body is free-text and is not parsed.

| Field       | Required | Type            | Notes                                                                                                                    |
| ----------- | -------- | --------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `products`  | Yes      | list of strings | Product directory slugs under `products/`, in the order the batch should run. A slug with no matching directory is an error. |
| `exclude`   | No       | list of strings | Product slugs present on disk but to be skipped this run. Reported as skipped, not silently ignored.                     |

Example:

```yaml
---
products:
  - foglight
  - foglight-agent
  - foglight-alerting
exclude:
  - foglight-legacy
---
```

If `catalogue.md` is absent, every directory under `products/` is in the batch, in alphabetical slug order.

### The catalogue composes batches; it does not gate requests

`catalogue.md` answers one question: *when someone asks for a batch run, which products does that mean and in what order.* It is authoritative for that and nothing else.

  * **Batch run** — no product named, the request is "run the definition". The catalogue is authoritative: a product directory listed neither in `products` nor in `exclude` is **skipped with a warning**, because silently including an unlisted product would make batch runs non-reproducible.
  * **A request naming a product** — "document Foglight Agent", "refresh Foglight Alerting". The catalogue is **not consulted for admission at all.** The human naming the product is a stronger signal of intent than a list they may simply not have updated, and refusing them a run because of a bookkeeping file is the catalogue acting as a gate it was never meant to be. Run it. If the slug is absent from both lists, say so in the report as a bookkeeping note; if it is in `exclude`, run it and say plainly that the exclusion was overridden by an explicit request, since that one *is* a human's stated intent and deserves to be visible when contradicted.

A product with no directory under `products/` at all is a different case — nothing to run, rather than something withheld — and is resolved by declaring it, per *Declaring from a run* above.

### Keeping the catalogue current

A run that declares a new product may **append its slug to `products`** — one list entry, in the position the confirmed plan states, changing nothing else in the file. It exists because the alternative is worse: a definition where a declared product is invisible to every batch run until someone notices. Present the appended line at the same gate that confirms the declaration. (The other permitted modification under `product-definition/` is a `product.md` amendment, bounded as stated at the top of this file — same gate, exact diff shown.)

Nothing else in `catalogue.md` is ever machine-edited: not `exclude`, not the ordering of entries that are already there, and not a word of the prose, which is a human's commentary on their own decisions.

## Parsing rules

0. Determine the layout. If `product-definition/products/` exists and contains at least one directory with a `product.md`, use the multi-product layout and parse `catalogue.md` if present. Otherwise use the single-product layout. Then apply rules 1–6 per product.
0b. **Resolve cross-product journeys — once per run, not per product.** If `product-definition/cross-product-journeys/` exists, read every `*.md` directly under it (non-recursive) and parse each per rules 3–5. Absent folder means no cross-product journeys, which is valid and unremarkable. Resolve `routes_to` entries against the journey names collected in rule 2 across **all** products, and warn on any that do not resolve.

    Then resolve each journey's `products`:

      * **Absent** — mark the journey `products: derived` and leave the list unresolved until the attribution pass in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` runs. This is not a defect and is not queried; it is the documented way to declare a cross-product journey without pre-computing its product list.
      * **Declared with two or more entries, all resolving to a directory under `products/`** — accept it as authoritative. Attribution still runs, to corroborate.
      * **Declared with fewer than two entries** — an input defect in the declaration itself, since the author asserted a list and asserted a non-crossing one. Report and skip the journey. Do not fall back to derivation: a one-element list is more likely a mistake than an invitation, and deriving past it would hide the error.
      * **Declared naming a slug with no directory under `products/`** — report and skip the journey. The undeclared product is the actual defect; the run cannot route to a product it knows nothing about, and guessing which checked-out repo was meant is exactly what `repos` exists to avoid.
1. Read `product.md`. If the file is missing, unreadable, has no frontmatter, or has a frontmatter without a non-empty `name` field, fall through to the paste fallback for **both** product and journeys — do not partially populate. In a multi-product batch, an invalid `product.md` skips that product with a recorded error rather than triggering the paste fallback for the whole batch.
1b. Resolve the brief. Look for `product.md`'s `brief` path if set, otherwise `brief.md` beside it. Absent is valid and is **not** an error — record the product as unbriefed and apply *When a product has no brief*. Present but lacking `source` or `captured` frontmatter: still use it, and record the missing provenance as a finding, because an extract with no capture date cannot be assessed for staleness.

2. Read every `*.md` file under `journeys/` (non-recursive — files directly under `journeys/`, not in subfolders).
3. For each journey file, parse the frontmatter. If the file has no frontmatter, or its frontmatter has no non-empty `name`, skip the file and record it in the run summary as a skipped input. Do not error out.
4. Trim whitespace on every scalar value. Empty strings after trimming are treated as absent.
5. For list fields, accept both YAML list syntax (`[a, b, c]` or `- a\n- b\n- c`) and a single string (which is normalised to a one-element list).
6. Reject duplicates. **Journey names are unique across the entire definition**, not merely within a product — every journey under every product, plus every cross-product journey, share one namespace. Two resolving to the same `name`, or to the same slug per `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/output.md`, is an input defect: stop and report it. Do not disambiguate silently. The same applies to two products resolving to the same `name`.

   Uniqueness is global rather than per-product because the two output sections are read together. Two pages titled "Onboard to Foglight" in different sections is the worst available outcome — a reader cannot tell which is authoritative, and neither page can say.

   **This check is necessary and nowhere near sufficient.** It compares names, so it catches only exact collisions. The overlaps that actually matter are semantic and pass it cleanly: "Archive telemetry" against "Forward logs to long-term storage", or "Make the service production-ready" against "Configure the application for production". Those are caught after authoring by `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/duplication.md`, not here. Do not read a clean uniqueness check as evidence that the journey set is non-overlapping.
7. After parsing, print the resolved product (name, owners, features, **brief present or absent with its capture date**) and the journey list (names, counts, and any skipped files) back to the user. In a multi-product batch, print one block per product plus a batch header giving the product count, run order, and **how many products are unbriefed** — that count is the single most useful number in the confirmation, because it predicts where the output will be weakest before any work is done.

   Print each product's `repos` declaration too, or `repos: not declared`. It is the input that most directly determines whether product attribution will work, and the cheapest moment to notice it is missing is before discovery rather than after.

   Then print a **cross-product block**: the count of cross-product journeys, each one's name, its `spine`, any `routes_to` entry that failed to resolve, and its `products` shown as either the declared list or the literal `derived (pending attribution)`. Print it even when the count is zero, as one line — its absence in a definition that has products with overlapping journeys is itself worth seeing.

   A journey whose products are derived cannot have its page set confirmed here, because which products it crosses is not yet known. Say that, rather than printing a provisional list that will be replaced — the plan for that journey is confirmed at step 6, once attribution has run.

   The user confirms before discovery begins.

## Paste fallback

If `product-definition/product.md` is missing or invalid (per rule 1 above):

### Step A — Ask for the product name

Ask the user verbatim:

> "What product is this documentation for?"

The user may type any value. Do not show suggestions. Apply the validation rules in *Validation* below.

If the user responds with `not applicable` (case-insensitive), set `product_name = "not provided"`.

### Step B — Ask for the journey list

Ask the user verbatim:

> "Paste your list of journeys (one per line, or markdown bullets). Type `not applicable` if there are no journeys."

If the user responds with a single line `not applicable` (case-insensitive), set `journeys = []`.

Otherwise, parse the paste line-by-line:

- Trim whitespace on each line.
- Strip a leading markdown bullet marker if present (`- `, `* `, or `1. ` / `2. ` / ...).
- Skip blank lines after trimming.
- Each remaining line becomes a journey record with `name` set to the line content and all other fields absent.

Pasted input is held in memory for the duration of the run only. The skill does **not** write the paste to disk and does **not** scaffold a `product-definition/` folder from it.

## Validation

The product name must be:
- non-empty after trimming,
- single-line,
- free of markdown formatting characters (`#`, `*`, `_`, `` ` ``, `[`, `]`).

Reject and re-ask if any rule is violated.

For pasted journeys, each non-blank line is accepted as a journey name; per-line validation is not applied (richer-prose journey descriptions belong in the folder route).

## Execution gating

The skill MUST NOT proceed to:

- repo scanning
- classification
- rewriting
- file generation
- reporting

until both `product_name` and `journeys` are resolved.

A journey the request names but no file declares is resolved by *declaring* it, per *Declaring from a run* above — the gate is satisfied by the confirmed declaration, not routed around. Discovery does not start before that file is on disk.

**Empty journey lists behave differently per mode:**

- In legacy `audit` mode, `journeys = []` is a valid resolution and the skill MUST proceed — it simply has no journey context for downstream steps.
- In `author` and `plan` modes, `journeys = []` is valid and produces a **product-only run**: the product's existing documentation is consolidated into the four Diátaxis buckets and no journey pages are authored. Because an empty journey list is equally likely to mean the definition is incomplete, confirm the intent once before proceeding, then record in the report that no journeys were supplied.

`cross_product_journeys` never gates execution. An empty set skips the cross-product phase entirely and is not queried — unlike an empty product journey list, an absent cross-product folder is the normal state of a definition that has not needed one yet.

### What richer journey prose buys you

In the base audit skill, journey `description` and body prose only affected matching quality. In `author` mode they are load-bearing: the journey term set built in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` Pass 0 is derived largely from them, and a journey defined by name alone will produce a thin term set, find little evidence, and yield low-confidence pages or none at all.

Journeys destined for `author` mode should carry:

- a `description` naming the concrete outcome, not the activity in the abstract
- `variations`, where distinct paths exist — each becomes its own how-to page
- `users`, which drives audience labelling
- body prose naming the systems, tools, resources, and artefacts involved — these become the highest-value discovery terms

A one-line journey name is accepted, but expect the report's "Journeys not covered" section to explain why it found nothing.

## Run summary

Before any scanning, the skill prints a confirmation block to the user with:

- The resolved `product_name`.
- The resolved owners and features (if from the folder route), and whether `features` is empty.
- Whether a brief was resolved, its `captured` date, and whether it is over 180 days old.
- The count and names of resolved journeys.
- The count and paths of skipped journey files (if any).
- The route used: `product-definition/` folder, paste, or a mix.

The user confirms before the skill proceeds.
