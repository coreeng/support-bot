---
name: Grounded authoring
description: Rules for writing new documentation pages from source-repository evidence. Defines the page set produced per product and per journey, the grounding and citation contract, and what to do when evidence is missing. Load once per run, after source discovery completes and before any page is written.
---

# Grounded authoring

This skill **writes new pages out of documentation that already exists**. It composes, restructures and rewrites; it does not originate. Two rules govern that, and they are not the same rule:

> **Provenance** — every claim on a page must already exist in prose somewhere in the estate. Code is never an origin. See *Strict provenance* below.
>
> **Grounding** — every claim must trace to a cited source file, or be explicitly marked unverified.

Provenance decides *what may be said at all*; grounding decides *what must be shown for it*. A claim can be perfectly grounded in a Go type and still be forbidden, because the Go type is not documentation.

Prose, structure, ordering, headings, and explanatory framing are yours to write. Claims are not.

Load this file once per run, after `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` has produced an evidence set, and before writing any page.

## What gets written

For each product, each journey under it, and each cross-product journey, produce the page set below. Paths, frontmatter, and site specifics are in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/output.md` and the consumer's site adapter; this section defines the *content contract* for each page.

### Product level

| Page | Diátaxis type | Required | Contract |
| --- | --- | --- | --- |
| `<product>/_index.md` | explanation | Yes | What the product is, who it is for, why it exists, what it groups together, who owns it, and an index of its journeys. See *The product page is the point* below. Navigation-only when the product has no brief. |
| `<product>/tutorial/*.md` | tutorial | When prose supports it | A newcomer's first guided pass at the product, end to end, one linear path, no branches. |
| `<product>/how-to/*.md` | how-to | When prose supports it | Task recipes that are not tied to one journey. A task that belongs to a journey goes in the journey directory instead. |
| `<product>/reference/*.md` | reference | When prose supports it | Configuration keys, CRD fields, CLI flags, API surfaces, defaults. Tables and field lists, no narrative. **Every field must already be described in prose somewhere** — a schema or CRD may confirm it but may never be the origin of it. A field the code defines and no documentation mentions is reported as undocumented surface area, not written. |
| `<product>/explanation/*.md` | explanation | When prose supports it | Architecture, key concepts, design decisions, boundaries with adjacent systems. One concept per page. |

These are the **four Diátaxis buckets**. Every one is assessed for every product in the report, whether or not pages landed in it — an empty bucket is a reported gap, never a stub. Do not manufacture a page to fill a bucket; `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/quality-flags.md` will flag it `hollow`, and a hollow page is a defect the skill deletes rather than ships.

Product-level pages other than `_index.md` serve the **builder/maintainer** audience tier.

### The product page is the point

`<product>/_index.md` is the most important page the skill writes, and it is the one most easily mistaken for a formality.

A product here is an umbrella over several services and repositories. Nothing inside those repositories says what the product *is* — a repository explains its own component, never the concept binding it to four others. For most readers the product is a new concept, and they arrive asking "what is this, who is it for, and why does it exist?" That question is answered nowhere else in the estate, which makes this page the skill's primary deliverable rather than a landing pad on the way to the how-tos.

Write it to answer, in this order:

  1. What it is, in one sentence someone outside the team would understand
  2. What problem it solves and why it was built
  3. Who it is for — the audiences, in their own terms
  4. What it deliberately is **not**, and where that work belongs instead
  5. What it groups together — the services and capabilities under the umbrella
  6. Who owns it, and where to reach them
  7. The journey index

Points 1–5 come almost entirely from the brief. That is why an unbriefed product produces a navigation-only page: the material simply does not exist anywhere else, and inventing it is precisely what the grounding contract forbids.

### Navigation-only product pages

When a product has no brief, `<product>/_index.md` carries its title, a one-line statement that no product description is available, and the journey index. Nothing else — no "what it is" assembled from READMEs, no feature table inferred from repository names, no audience list deduced from journey `users`.

The temptation is to reach for the highest-ranked repository README and treat it as a product description. Do not. A README describes a component; using one as a product description produces a page describing whichever repo happened to rank first, which is worse than an acknowledged gap because it looks like an answer. Report the missing brief per `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/gap-analysis.md` and move on.

### Journey level

| Page | Diátaxis type | Required | Contract |
| --- | --- | --- | --- |
| `<product>/<journey>/_index.md` | explanation | Yes | What the journey achieves, prerequisites, who performs it, its variations, and an index of the pages beneath it. Short — it orients, it does not instruct. |
| `<product>/<journey>/<variation>.md` | the journey's `spine` | Yes, one per variation | The end-to-end page. If the journey declares no `variations`, write exactly one page named for the spine — `how-to.md`, `tutorial.md`, or `explanation.md`. |

Journey-level pages serve the **end-user** audience tier. They are the point of the whole run: per the documentation model in `SKILL.md`, a journey with no end-to-end page is the only high-severity gap the skill recognises, and this skill exists to close it.

### Choosing a spine

The **spine** is the Diátaxis type of a journey's end-to-end page. It is declared in the journey definition (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/product-definition.md`) and defaults to `how-to`. The skill does not choose it, but it must recognise when the declared value is wrong for the material it found, and say so in the report rather than quietly writing the wrong shape.

| Spine | When it fits | Reader's position |
| --- | --- | --- |
| `how-to` | The default. A competent reader with a specific outcome in mind. | "I know what I need; give me the steps." |
| `tutorial` | The journey body explicitly describes a *learning path* — a newcomer's guided first pass, safe to run, with a guaranteed outcome. The word "first" in a journey name is usually the tell. | "I have never done this; teach me by doing." |
| `explanation` | The reader has a goal but does not yet know which products or steps serve it. Wayfinding. This is the normal spine for a **cross-product journey**. | "I don't know where to start." |

**The old rule was a prohibition; this replaces it, and the reasoning it protected still holds.** Tutorials teach by walking a newcomer through a safe worked example; a how-to gets a competent user to an outcome. Conflating them remains the most common failure mode here — a "tutorial" that is really a how-to teaches nothing and a "how-to" that is really a tutorial wastes an expert's time. Declaring the spine does not make the distinction less sharp; it moves the decision to a human who knows the audience, and makes the answer inspectable in frontmatter.

Where the declared spine and the evidence disagree — a journey declared `tutorial` whose only material is a terse runbook — write the page to the **declared** spine, note the mismatch in the report, and do not invent the missing register. A tutorial written out of runbook fragments is a tutorial in name only.

### Cross-product journey level

| Page | Diátaxis type | Required | Contract |
| --- | --- | --- | --- |
| `cross-product-journeys/<journey>/_index.md` | explanation | Yes | What the reader is trying to achieve, which products it crosses and why each is involved, prerequisites, and a link to the spine page. |
| `cross-product-journeys/<journey>/<spine>.md` | the journey's `spine` | Yes | **The route.** What the reader needs, in what order, which product owns each part, and a link to where each part is documented. |

These pages **orient and link. They do not restate.**

That is the whole design, and it is easy to lose while writing. A cross-product journey exists because its ground is already covered — in product journeys, in the wiki, in repository docs — and the reader's problem is not that the steps are missing but that they cannot tell which steps apply to them or in what order. Re-documenting the steps solves nothing and creates a second copy that drifts.

So the spine page:

  * **names each stage and the product that owns it**, in the order the reader meets them
  * **links to the page that documents that stage** — a product journey via `routes_to`, a wiki page, a repository doc
  * **states what carries between stages** — the output of one stage that the next needs. This is the part that genuinely exists nowhere else, because no single product's documentation can see across the boundary
  * **names the decision points** that determine which path the reader takes

And it does **not**:

  * reproduce commands, manifests, or field lists that the linked page already carries. Copy nothing you are linking to
  * describe a stage in enough detail that a reader could complete it without following the link. If they can, you have written a competing document
  * fill a stage that has no documentation with your own account of it. An undocumented stage is named as a gap, with the product that owns it, and reported — the same rule as anywhere else

The last one is the sharp edge. A route with a hole in it is genuinely less useful than a complete one, and the pressure to close the hole with a paragraph of plausible prose is real. Do not. Write "this stage is not documented — it belongs to *&lt;product&gt;*" and let the report carry it as a gap. A named hole is a task for the team that owns it; a filled one is a fabrication with a link either side lending it credibility.

#### What to write instead

The paragraph above says what not to do. This says what to do, and it is deliberately short because the output should be short.

**When a stage has no documentation at all:**

> **Stage N — &lt;name&gt;.** Owned by *&lt;product&gt;*. Not documented.
>
> &lt;One sentence on what the stage achieves, if a source supports it.&gt; Ask the owning team.

**When documentation exists but is written for a different reader** — an operator runbook, an internal design note, a page aimed at a different tier:

> **Stage N — &lt;name&gt;.** Owned by *&lt;product&gt;*. Documented for &lt;that reader&gt;, not for &lt;the journey's `users`&gt;.
>
> &lt;What that material establishes, cited as normal.&gt; &lt;Where it lives.&gt;
>
> &lt;One sentence naming what the reader's own question still lacks.&gt; Ask the owning team.

That is the whole form. Two to four sentences.

**The reasoning goes in the report, never on the page.** Why material was rejected, which sources were weighed, what a human would need to write — all of it belongs in *undocumented surface area*. A reader trying to finish a task has no stake in how the page was assembled.

#### The opposite failure

Over-writing a gap is as much a defect as filling one, and it is easier to miss because it looks like diligence. The tells:

  * stating the same absence three or four times
  * a banner about the page's own construction — "Gap, not a step" — rather than about the reader's task
  * justifying an editorial decision: "it is not linked here because…"
  * explaining at length what the available sources do and do not cover

Both failures put the writer where the reader should be. The filled hole does it with invented content, the over-written hole with self-justification. The test is the same for each: can the reader act on this paragraph? If not, it belongs in the report.

#### Saying why each product is involved

Both pages must name the products the journey crosses and say why each is there — that is the question a reader arrives with. Where the product list was **derived** rather than declared (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md`), one rule constrains how you write it:

> **An attribution score is never a reason on a page.** "Foglight Agent is involved because it contributed 41% of the evidence weight" is a run diagnostic wearing the clothes of an explanation. It tells the reader nothing about their task and it launders a computed number into a factual claim about the estate.

The reason a product is involved must be grounded in prose like any other claim: a stage of the journey is documented in that product's material, so the reader is sent there for it. Write that — name the stage and the product, from the evidence. If no prose supports a sentence about why a product participates, name the product and the stage it owns and stop; do not reach for the score to fill the sentence out.

The scores belong in the report, where Part E carries them with the rubric that produced them.

**Where a cross-product journey and a product journey cover the same ground**, the product journey is canonical and the cross-product page links to it. That relationship is expected — the two journey lists overlap by design — and it only becomes a defect when the cross-product page restates rather than routes. `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/duplication.md` checks for exactly that.

### Single-type discipline

Every authored page is exactly one Diátaxis type. Use `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/types.md` for the signals and voice of each type, and `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/compass.md` if you are unsure which type a piece of material belongs in. If a page you are drafting starts to carry two intents, split it into two pages — do not compromise.

The classification machinery in the base skill (PERFECT / REWRITE / SPLIT / OUTLIER) does **not** apply to authored pages. Those verdicts describe pages you found; these are pages you wrote, and you write them single-type by construction.

## Strict provenance — what may be written from

**Nothing is written from scratch.** Every page is a reorganisation and rewriting of documentation that already exists somewhere in the estate. The skill's job is to find scattered prose, consolidate it, and give it a Diátaxis shape — not to compose fresh documentation from source code.

This is stricter than "cite your facts", and it is deliberate. A page composed from Go types is genuinely new documentation, written by a model, that no human ever wrote or reviewed. It reads authoritatively and nothing behind it was ever intended as documentation.

### Sources — the only things a page may be written from

Prose written to be read by a human:

  * `README.md` at any level
  * anything under `docs/`
  * `CONTRIBUTING.md`, handbooks, runbooks, architecture notes, ADRs
  * the existing documentation under the consumer's `prior_art_roots`
  * a product **brief** (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/product-definition.md`), which is the most authoritative source for product-level claims and among the weakest for technical ones

Rewriting is expected — matching word for word is not required and usually wrong, since the whole point is to restructure. What must hold is that **the claim already existed in prose somewhere**.

### Corroborators — never sources

Code, Kubernetes manifests, CRDs, OpenAPI and JSON schemas, Helm values, Terraform, CI definitions and tests are **not** sources under this rule. Nothing may be written from them.

They serve two other purposes, both valuable:

  1. **Confirming a prose claim.** A README stating a default that the chart confirms is far stronger than one nothing confirms. Recorded in `doc_journeys.corroborated_by`, and worth `+2` on the confidence rubric.
  2. **Detecting stale prose.** Where code and prose disagree, the code wins as a matter of fact but the prose wins as a matter of *what may be written* — so the correct action is to write neither and report the conflict. A page must not quietly state what the code says.

### When only code documents something

This is the case the rule exists for, and it has exactly one correct outcome: **write nothing and report the gap.**

Report it as **undocumented surface area** — the code defines a capability that no documentation anywhere describes. This is among the most valuable things a run produces, because it names precisely what a human needs to go and write, and it is invisible without a tool that reads both.

Granularity is the **claim**, not the page. A reference page whose fields are documented in prose except for one is written, with that one field omitted and reported. Do not discard a whole page because a corner of it is unbacked, and do not include the corner because the rest is fine.

### Two carve-outs

  * **Structure, ordering, headings, transitions and framing** are yours to write, exactly as before. Strict provenance constrains *claims*, not prose. Consolidating six wiki pages into one how-to means writing connective tissue that appears nowhere in the sources; that is the job, not a violation.
  * **The product definition** (`product.md`) is a permitted source. It is authored by a human stating what they know, not by a model filling a gap, so the failure mode this rule guards against is absent.

## The grounding contract

### Copy, never paraphrase, these

Reproduce **character-for-character** from the evidence file. Paraphrasing any of these is a defect, not a style choice:

- commands and their arguments
- flag and option names
- file and directory paths
- environment variable names
- configuration keys and their default values
- resource, chart, namespace, and cluster names
- API paths, ports, URLs
- version numbers and image tags
- error message text you are telling the reader to look for

If you cannot copy it from a source, you do not have it. Write the page without it.

### Never invent these

Do not produce a plausible-looking value for anything in the list above. A command that does not exist is worse than an acknowledged gap: the reader will run it. Specifically, do not:

- complete a partial command with likely-looking flags
- normalise a path to what it "should" be
- assume a standard port, default, or naming convention holds
- convert an example value into a placeholder and back
- carry a fact from one repo's component to a similarly-named component in another repo

### Contact points and other live external entities

Slack channels, user group handles, email distribution lists, ticket queues, dashboards, and on-call rotas name things that live **outside** the repositories. No source file can prove one still exists. A channel is renamed or archived while every document mentioning it stays exactly as it was, so the ordinary grounding contract — *the fact was copied from a cited source* — does not protect the reader here. It is the one class of fact where a correctly cited value is routinely wrong.

Getting this wrong is worse than an omission. A reader sent to a dead channel waits for an answer that cannot arrive, and blames the platform rather than the page.

These have their own authority order, which is **not** the general one above:

1. **Machine-validated configuration.** A value some schema or admission webhook enforces — a tenant-registration field validated against an allowed chat host, say, where a malformed value fails a pipeline, so a well-formed one has been checked by something other than a human's memory. The consumer's `contact_corroborators` setting names the repo, path and field for each such corroborator in the estate.
2. **A repository's own `CONTRIBUTING.md` or support documentation**, where the channel is named *for the purpose of contacting that team* and carries a permalink.
3. **Prose naming a channel with no link.** Weakest. Enough to corroborate something already found at tier 1 or 2, never enough on its own.

Rules:

  * **Corroborate before emitting.** A contact entity whose only source is a product page, wiki page, or other prose is not written onto the page. Search the source repos for the literal string first; no corroborating hit means it does not appear in the output. The consumer repository never counts toward this corroboration, even though it is a content source — its own site prose is exactly the stale-mention risk this rule exists for.
  * **Search every form the entity has, not just the one you found.** A Slack channel has a *name* (`#foglight-support`) and an *archive ID* (`C0FOGL1GHT`), and an estate uses them in different places — prose links by name, deployed config and validated fields by ID. Searching one form and reporting "no corroboration" is the single most repeated error in this skill's history: three entities in one run were proposed for deletion or recorded as uncorroborated because only one form was searched, and every one of them turned out to be corroborated under the other. **Record which form yielded the hits**, because the count is what a deletion decision rests on.

    Two traps that follow from this — the consumer's estate adapter lists the known instances under *Known contact-point traps*:

      * **Former names stay in the estate.** A renamed channel is still called by its old name in repos nobody updated. A reader who greps concludes there are two channels. Where a former name is in live use, say so on the page.
      * **One name can be a prefix of another.** `#foglight-alerts` matches inside `#foglight-alerts-dev`, so a fixed-string count for the shorter name is inflated. Anchor the boundary — `grep -rlE '#foglight-alerts([^-]|$)'`.

    When excluding the output repository from a corroboration count, **anchor the exclusion to the repository root** (`grep -v '^<consumer repo dir>/'`, or `--exclude-dir` at the tool level). An unanchored filter also drops paths where the repo name appears as a subdirectory elsewhere in the estate, which silently understates the count.
  * **Prefer the permalink to the name.** Write `[#foglight-support](https://<workspace>.slack.com/archives/C0FOGL1GHT)` rather than a bare `#foglight-support`. Names are renamed; archive IDs are not. Having only the name is itself the signal that you are at tier 3.
  * **An identifier withheld means the bare name is withheld too.** Publishing the name while withholding its uncorroborated ID ships the *weaker* form of the same claim — a bare name silently outlives the thing it named, which is exactly what the ID exists to detect. Either the entity is corroborated well enough to publish name+ID, or neither goes on the page.
  * **Never infer one from a naming convention.** `#foglight-<product>` looking plausible beside a real `#foglight-support` is exactly the failure this rule exists to prevent.
  * **Two sources naming different channels is a conflict**, resolved per *Conflicts between sources* above — not a list of alternatives to hand the reader.
  * **Attribute the contact, do not generalise it.** A channel registered by one tenant is that tenant's channel. It becomes the way to reach a *product's* team only where a source says so for that purpose.

This rule exists because a product hub page stated two channels, only one of which was real, and the invented one reached a published page correctly cited. Citation was never the weak link.

### Version and staleness

Where a version, image tag, or pinned dependency appears in evidence, cite the file it came from **in the same sentence or table cell**. These go stale fastest and a reader needs to know where to re-check. Prefer pointing at the file that pins it over restating the value when the value changes often.

### Conflicts between sources

When two evidence files disagree, apply the authority order in `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/source-discovery.md` — deployed configuration beats interface definitions beats code beats tests beats prose. Then:

1. Write the fact from the higher-authority source.
2. Add a quality flag on the page recording the conflict, both paths, and both values.
3. Apply the `−2` confidence penalty from the discovery rubric.

Do not silently pick one. Do not present both as if the reader can choose.

### A human override covers the rule it was given for, and no other

A human may override a rule in this file at the plan gate — most often *Corroborate before
emitting*, to publish a contact point the estate cannot corroborate. **That override reaches
exactly the rule it was asked about.** It does not extend to a neighbouring rule merely because
the same fact is in scope of both.

The case this exists for: an override of *Corroborate before emitting* was generalised into an
override of *Conflicts between sources*, and two rival Slack channels were published side by side
with "check with the team which is current" — the shape the section above forbids in two separate
sentences. The `−2` was never applied. Both pages were a confidence band too high for a full round
as a result, and a support engineer reading them mid-query had to guess.

So, when writing under an override:

  * **Name the rule the override covers** in the run report, and state that no other rule is
    overridden. "The user overrode corroboration" is the record; "the user approved publishing
    this" is not, because it does not say what was approved.
  * **Every other rule still applies to the overridden fact.** An uncorroborated channel that also
    conflicts with another source is still a conflict: one channel is written, the flag is added,
    and the `−2` is applied. The override bought it a place on the page, not an exemption from
    everything else.
  * **If a second rule seems to need overriding too, that is a question for the gate**, not an
    inference to make while authoring. Return it as an open question.

This defect class does not fail re-derivation, because nothing is miscounted — the report shows
its working and cites the override, so it reads as diligence. It is caught only by asking which
rule the override actually named.

## Marking what you could not verify

A journey usually has steps that no single file states outright. You may write such a step when it is required for the journey to make sense, but it must be visibly marked. Use the consumer's `unverified_marker` (settings; the site adapter shows the exact form), inline at the point of the claim, wrapping this text:

```
This step is inferred from <what you reasoned from> and was not confirmed by any source file. Verify before relying on it.
```

Rules:

- One marker per inferred claim. Do not batch several inferences under one banner.
- Each marker costs the page `−2` on the confidence rubric. A page with three inferred steps is `low` confidence almost by construction — which is correct and should not be worked around.
- Never mark a *command* as unverified and print it anyway. If you do not have the command, describe the outcome the reader needs to achieve and say the command was not found. An unverified command is a trap; an acknowledged gap is a task.

If a required page cannot be written at all because no evidence exists, **do not write a stub**. Emit no page, and record the journey as `missing` in the report with the term set that failed to find anything. A hollow page is worse than an absent one — it looks like coverage.

## Low-confidence pages

Any page computing to `low` confidence carries the consumer's `low_confidence_banner` immediately after its frontmatter, before the first heading, wrapping this text:

```
**Low confidence — needs review.** This page was generated from limited source evidence. Check every command and value against the sources listed at the bottom before relying on it.
```

Pages at `medium` and `high` do not carry a banner; their confidence is recorded in frontmatter and the report only.

## Citing sources

Every authored page ends with a `## Sources` section — the last section on the page, after all content.

```markdown
## Sources

This page was generated from the following files. Paths are relative to each repository root.

  * `foglight-agent` — `docs/forwarding.md` — the forwarder configuration keys and restart step
  * `foglight-agent` — `README.md` — the agent version prerequisite
  * `foglight-platform` — `docs/retention.md` — how long forwarded records are kept
```

Rules:

- One bullet per evidence file that contributed a fact. Files read but not used are excluded.
- Order by relevance: `high` first, then `medium`, then `low`.
- The trailing clause states what the file contributed, not what the file is.
- The same list, in the same order, goes in the page's `sources:` frontmatter — see `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/output.md`.

Do not link source paths. These repositories are private and the rendered site has no resolvable target; a broken link is worse than plain text.

## Voice and style

Authored pages must read like the rest of the site, not like generated output.

- Follow the consumer's `style_guide`. It is binding, not advisory; the site adapter summarises its rules on list formatting, punctuation and spelling.
- Use the voice of the page's Diátaxis type per `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/types.md` — imperative for how-to, declarative for reference, discursive for explanation.
- Match the existing site's variety of English.
- Do not open a page by restating its own title, and do not write "This document describes…".
- Do not include a changelog, a "last updated" line, or an author byline. Provenance lives in frontmatter.

### The generation notice

Every authored page carries this HTML comment as the **first line of the body — immediately after the closing `---` of the frontmatter**, never before the opening one:

```
<!-- Generated by doc-journeys from source repositories. Review before relying on it; see the Sources section. -->
```

Static-site generators only recognise front matter when it begins on the very first line of the file. A comment placed above the opening `---` means the page has no front matter at all: the title, the weight, and the whole `doc_journeys` block are silently discarded, the delimiters render as horizontal rules, and the page appears untitled in the sidebar. Because the notice sits in the body it is covered by `content_hash`, which is correct — it is a constant, so it does not make the hash unstable.

This differs from the base skill's notice, which tells the reader to edit the source file instead. Here there is no source document to edit — the sources are code. Edits to these pages are legitimate and expected.

## What this skill still does not do

- **It does not assert correctness.** Grounding a fact in a source file means the source said it, not that it is true. The source may itself be wrong or stale.
- **It does not run anything.** No command on an authored page has been executed. Cited does not mean tested.
- **It does not read private context.** Anything known only to the team and not written down anywhere in the repositories will be absent, and the skill cannot tell the difference between "not documented" and "does not exist".

Say so plainly in the report rather than letting a confident-looking page imply otherwise.
