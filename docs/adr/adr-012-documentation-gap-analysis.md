# ADR: Documentation Gap Analysis

**Status:** Proposed

## Context

Support engineers answer the same questions repeatedly because the platform's tenant-facing
documentation does not cover them, or covers them wrongly. The bot already classifies why each
closed ticket was raised ([ADR-002](adr-002-automated-analysis-workflow.md)), but nothing compares
a ticket with the documentation, so nobody can say which pages are missing or wrong.

To compare the two, a model has to be shown the documentation that matters for a ticket. How it
gets that documentation is the main decision, and two things shape it:

- **The documentation is too large to send whole.** The repository used for sizing has about 480
  pages, roughly 550–650k tokens, in about 20 product areas.
- **Where the documentation comes from is expected to change.** A GitHub repository is the first
  source. A later one may be an MCP server that searches rather than a set of files.

This ADR records the strategy only. Prompts, limits, checks, storage, retry rules, and how and
when a run starts are specified in the OpenSpec change `add-documentation-gap-analysis` and can
change without amending this ADR.

## Decision

Narrow the documentation to the part a ticket is about, then classify the ticket against that part.

- **Narrow first, then classify.** One model call reads the thread and an index of the
  documentation's pages, and chooses the product areas the question belongs to. A second call is
  given the pages of those areas and classifies the ticket as `correct`, `missing`, `incorrect` or
  `not_applicable`.
- **Whole areas, not fragments.** A product area is a top-level section of the documentation. The
  second call sees every page of the chosen areas, so a `missing` verdict is made against the
  whole area and not against a handful of search hits.
- **A local copy.** The bot keeps a copy of the documentation repository on the pod and refreshes
  it periodically. The index is built from that copy.
- **Finding documentation is kept apart from classifying.** The part that finds documentation
  answers one question: which documentation is relevant to this thread. The rest of the analysis
  does not know where documentation comes from, so another source can replace the first without
  changing it.

## Consequences

### Positive

- ✅ No new infrastructure: no search index to build or store, no queue, no tool loop.
- ✅ Cost per ticket is predictable: two calls, the second bounded by the size of the chosen areas.
- ✅ `missing` means no page in the area answers the question, not that a search found nothing.
- ✅ The way documentation is found can be replaced without changing how tickets are classified.

### Negative

- ⚠️ **The first call can choose the wrong area.** The page that answers the question is then not
  among those the second call sees. Validation on real tickets measures how often this happens.
- ⚠️ **It depends on how the documentation is organised.** Areas are top-level sections, so a
  repository with no such sections, or with one very large section, does not fit.
- ⚠️ **Cost.** Every ticket sends tens of thousands of tokens of documentation, far more than the
  existing classification, which sends only the thread.
- ⚠️ **Documentation content is sent to the LLM provider**, which the bot has not done before.
- ⚠️ **The boundary was drawn with one source built.** A second may need it adjusted.

## Alternatives considered

- **The whole documentation in every prompt.** No wrong choice of area, but it needs a model with
  a context of a million tokens and is slow and costly per ticket.
- **An agent with tools over the local copy**, such as a search the bot runs on the model's
  behalf. It works at any size and reads only what it needs. Rejected for now: it adds a tool loop
  the bot does not have, makes cost per ticket unpredictable, and running commands a model chose
  on the pod is a risk to contain. It could be built later as another source.
- **Embedding or keyword retrieval.** Rejected: a retrieval miss looks exactly like a `missing`
  verdict, and it needs an index to build and store.
