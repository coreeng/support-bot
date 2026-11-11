# ADR: Evidence for the Support Summary's claims

**Status:** Proposed

## Context

The Support Summary's prose draws conclusions such as "documentation on X is needed", but gives the
reader no way to see what they are based on. The provenance was lost before the model was called:
the report the model reads did not say which ticket each reason came from, and the prose was stored
as one block of text. Matching sentences back to tickets afterwards (by keyword or embedding
similarity) would be guesswork presented as evidence.

## Decision

Ask the model to cite its evidence as it writes, check the citations before storing them, and
resolve them to tickets when the page is read. This is a new version of the summary prompt.

- **The report names each reason's ticket and classification**, labelled as the breakdowns label
  them, so a claim about a breakdown value can be cited without guessing from the reason text.
- **Inline markup, not structured output.** The model marks the phrase of each specific claim and
  names the few tickets behind it. A malformed tag costs one citation; malformed structured output
  would cost the whole summary.
- **Checked at generation.** Only tickets that were in the model's report are kept, and anything
  else is reduced to plain text, so a stored summary never names a ticket outside its scope.
- **Resolved at read.** Cited tickets are looked up under the page's scope each time the summary is
  read. A ticket that no longer resolves is dropped; a claim left with none reads as plain text.
- **Additive API.** The plain prose is unchanged; the cited claims and their tickets are added
  alongside it only when there are any.
- **The page.** Cited phrases are marked. Opening one shows each ticket's reason and date, with links
  to its Slack thread and to the ticket.
- **Two rules ride on the same prompt version**, so they cost no second regeneration:
  - A team group's summary describes only that group, never the platform as a whole. This settles
    [ADR-010](adr-010-team-groups-for-support-summary.md)'s scope-aware follow-up.
  - When a window has more reasons than the report can hold, they are sampled evenly across the
    window rather than taken from its newest end, and the report says they are a sample. A wide
    window's prose still covers its early weeks.

## Consequences

### Positive

- ✅ Each claim links straight to the threads behind it.
- ✅ No schema change, and nothing about tickets is copied into stored summaries.
- ✅ Fails safe: prose with no surviving citations renders exactly as before.
- ✅ No re-classification. Each cached summary is regenerated once, from existing classifications,
  on its window's next visit.
- ✅ Slack links are built from stored thread references, so the page makes no Slack calls.

### Negative

- ⚠️ **Citations are the model's attribution, not proof.** The checks guarantee a cited ticket was in
  the report, not that it supports the claim. Showing each ticket's reason makes a poor citation
  quick to spot.
- ⚠️ **Coverage depends on the model.** Some claims may go uncited.
- ⚠️ **Rollback needs the previous prompt version put back in use first**, or an older release would
  show the markup as raw text (see `docs/runbooks/support-summary.md`).
- ⚠️ **The report grows.** Each reason line carries its ticket and classification, so the model call
  is larger, slower and costlier, and more likely to hit the provider timeout on a wide window. The
  number of reasons sent and the timeout are both configurable.
