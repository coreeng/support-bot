# Diataxis — framework reference

Distilled summary of the Diataxis documentation framework, used by the `diataxis-scoring` skill. Source of truth: <https://diataxis.fr/>. This file is offline cache for the skill — refer to the live site for the authoritative version and current examples.

## The central claim

Diataxis identifies **four distinct kinds of documentation**. Each kind serves a different reader need. A single page should serve **one kind**. Pages that try to serve two or more kinds at once leave every reader half-satisfied.

The four kinds are not arbitrary categories — they fall out of two orthogonal axes:

|                         | **Practical** (steps, action) | **Theoretical** (concepts, understanding) |
|-------------------------|-------------------------------|--------------------------------------------|
| **Study** (learning)    | Tutorial                      | Explanation                                |
| **Work** (doing the job)| How-to guide                  | Reference                                  |

- *Study* vs. *Work*: is the reader learning, or doing their job?
- *Practical* vs. *Theoretical*: are they handling concrete steps, or absorbing concepts?

## The four modes in depth

### Tutorial — learning-oriented

**Reader's question**: "I'm new — how do I get started?"
**Author's job**: take the reader by the hand through a concrete, complete lesson that ends with a working result. Build *confidence*, not comprehensive coverage.

Hallmarks:
- Narrative voice — "you'll learn", "we'll build"
- Every step is concrete, every result visible
- No optionality, no branching — one path through
- Doesn't try to be comprehensive (that's reference's job)
- Doesn't dwell on why (that's explanation's job)

Anti-pattern: a "tutorial" that's actually a how-to (assumes the reader has a specific problem already in mind) or a reference (lists features without doing anything with them).

### How-to guide — task-oriented

**Reader's question**: "I have a specific problem — how do I solve it?"
**Author's job**: hand the reader a recipe that gets the job done. The reader is competent; the author respects their time.

Hallmarks:
- Title is a task ("Deploy the app with TLS")
- Imperative voice — "run", "configure", "verify"
- Numbered or bulleted steps
- Assumes the reader knows what they want
- Skips background — points elsewhere for it

Anti-pattern: a how-to that drifts into explaining *why* the steps work. The reader didn't ask why; they asked how. Explanation belongs in a separate page.

### Reference — information-oriented

**Reader's question**: "What is X? What does X accept? What does X return?"
**Author's job**: provide accurate, complete, dry technical descriptions. Reference is consulted, not read.

Hallmarks:
- Structured, predictable layout — tables, lists, type signatures
- Neutral, descriptive tone — no narrative, no opinion
- Comprehensive — every field, every flag, every option
- No procedural steps (those are how-to)
- No conceptual explanation (that's explanation)

Anti-pattern: reference docs that editorialise ("you should usually set this to 5") or include extended examples. Trim the editorialising; move examples elsewhere.

### Explanation — understanding-oriented

**Reader's question**: "Why does X work this way? What were the trade-offs?"
**Author's job**: deepen the reader's mental model. Cover history, alternatives, constraints, design decisions.

Hallmarks:
- Discursive prose — paragraphs, not steps
- "Why" / "because" / "we chose X because Y" framing
- Trade-offs, alternatives, design rationale
- No commands, no field lists
- Often the natural home for ADR-style content

Anti-pattern: explanation docs that drift into how-to ("now here's how to configure it…"). The reader is trying to understand, not execute.

## Common confusion patterns

| Confusion | Resolution |
|-----------|------------|
| Tutorial vs. how-to | Tutorial = a complete lesson for someone new. How-to = a recipe for someone with a problem. Both have steps; their reader is different. |
| Reference vs. explanation | Reference = "what". Explanation = "why". Reference is consulted, explanation is read. |
| How-to vs. reference | How-to = procedure. Reference = data. A page describing "how to use the X API" is usually a how-to *or* a reference, not both — choose one. |
| Explanation vs. tutorial | Tutorial uses learning to motivate concepts. Explanation discusses concepts directly. Tutorial does, explanation reflects. |

## Why this matters for scoring

The `diataxis-scoring` skill measures **mode purity**. The hypothesis: a docs page that holds one mode cleanly serves its reader well; a page that mixes modes serves every reader badly.

A page that scores 100/100 isn't necessarily *good* — it could be poorly written within its mode — but a page that scores low is *guaranteed* to be confusing, because it's asking the reader to context-switch between reader stances within a single page.

## Further reading

- <https://diataxis.fr/> — the framework site, with examples and rationale
- <https://diataxis.fr/start-here/> — a short orientation
- Daniele Procida, *What nobody tells you about documentation* (talk and essay) — the origin of the framework
