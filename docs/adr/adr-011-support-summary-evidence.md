# ADR: Evidence for the Support Summary's claims

**Status:** Proposed

## Context

The Support Summary's prose draws conclusions such as "documentation on X is needed", but gives the
reader no way to see what they are based on. The provenance was lost before the model was called:
the window report gave each ticket's `Reason` and date but not its ticket, and the model's prose
was stored as one text value. Matching sentences back to tickets afterwards (by keyword or
embedding similarity) would be guesswork presented as evidence.

## Decision

Ask the model to cite its evidence as it writes, check the citations before storing them, and
resolve them to tickets when the page is read. This needs summary prompt version 2 (V44).

- **Each reason line names its ticket and classification**, labelled as the breakdowns label them:
  `#123 | 2026-03-12 | Knowledge Gap | Build & CI | pipelines | team-a — <reason>`. Without the
  labels, a claim about a breakdown value could only be cited by guessing from the reason text.
- **Inline markup, not structured output.** The model wraps the phrase of each specific claim as
  `[[phrase|123,456]]`. A claim about a breakdown value cites every ticket it covers, up to five,
  with any count kept outside the phrase. A malformed tag costs one citation; malformed JSON would
  cost the whole summary.
- **Checked at generation.** `SummaryCitations.sanitise` keeps only tickets that were in the report,
  at most five per claim, and reduces any other tag to its phrase, so a stored snapshot never names
  a ticket outside its scope.
- **Resolved at read.** The annotated prose is stored in `summary_snapshot.content`. On read it is
  parsed into segments, and the cited tickets are looked up under the page's scope. A ticket that no
  longer resolves is dropped; a claim left with none reads as plain text.
- **Additive wire shape.** `summary.content` stays plain prose. `summary.segments` is added only when
  the prose cites something; each cited segment carries its tickets' reason, date, channel and
  thread timestamp.
- **The page.** Cited phrases are underlined. Hovering opens a popover (click, tap or Enter pin it)
  listing each ticket's reason and date, with links to its Slack thread and to the ticket.
- **Two rules ride on the same version**, so they cost no second regeneration:
  - A team group's summary describes only that group, never the platform as a whole. This settles
    [ADR-010](adr-010-team-groups-for-support-summary.md)'s scope-aware follow-up.
  - Past `summary-area.page.max-reasons` (1000 by default), the reason lines are spread evenly across
    the window rather than the newest only, and the report says they are a sample. A wide window's
    prose still covers its early weeks.

## Consequences

### Positive

- ✅ Each claim links straight to the threads behind it.
- ✅ No schema change, and nothing about tickets is copied into snapshots.
- ✅ Fails safe: prose with no surviving citations renders exactly as before.
- ✅ No re-classification. Each cached summary is regenerated once, from existing classifications,
  on its window's next visit.
- ✅ Slack links are built from channel and timestamp, so the page makes no Slack calls.

### Negative

- ⚠️ **Citations are the model's attribution, not proof.** The checks guarantee a cited ticket was in
  the report, not that it supports the claim. Showing each ticket's reason makes a poor citation
  quick to spot.
- ⚠️ **Coverage depends on the model.** Some claims may go uncited.
- ⚠️ **Rollback needs a flag flip.** A release from before V44 would show the markup as raw text, so
  summary prompt version 1 must be put back in use first (see `docs/runbooks/support-summary.md`).
- ⚠️ **The report grows.** Each reason line gains its id and four labels, roughly 15–30 tokens on top
  of a 25–40 token reason, so a window at `summary-area.page.max-reasons` (1000 by default) sends a
  report around 1.5–2× its previous size. That makes the call slower and costlier, and more likely
  to hit a provider timeout (`AI_PROXY_TIMEOUT`, 20s by default). Tune `SUMMARY_MAX_REASONS` or the
  provider timeout if it fails there.
