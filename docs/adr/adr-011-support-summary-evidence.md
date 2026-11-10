# ADR: Evidence for the Support Summary's claims

**Status:** Proposed

## Context

The Support Summary's prose draws conclusions such as "documentation on X is needed" or "recurring
problem Y is worth fixing", but gives the reader no way to see what they are based on. Checking one
means searching the breakdowns or Slack by hand.

The provenance was lost before the model was called. The window report gave the model each
ticket's one-line `Reason` with its date but not its ticket, and the model returned free prose
stored as one text value. The breakdown rows' "most recent tickets" are the newest five per bucket,
not evidence for any particular sentence, and matching sentences back to tickets afterwards (by
keyword or embedding similarity) would be guesswork presented as evidence.

## Decision

Ask the model to cite its evidence as it writes, check the citations before storing them, and
resolve them to tickets when the page is read.

- **The report names each ticket and how it was classified.** Every reason line starts with its
  ticket id, date, driver, category, feature and team, labelled exactly as the breakdowns label
  them: `#123 | 2026-03-12 | Knowledge Gap | Build & CI | pipelines | team-a — <reason>`. Without
  the labels, a claim about a breakdown value ("knowledge gaps (3 tickets)") could only be cited by
  guessing from the reason text, and the model's guesses did not match the counts.
- **A claim about a breakdown value cites every ticket it covers**, up to five, with any count kept
  outside the cited phrase, so the popover agrees with the number beside it.
- **Inline markup, not structured output.** Summary prompt version 2 (V44) asks the model to wrap
  the phrase of each specific claim as `[[phrase|123,456]]`, citing 1 to 5 tickets whose reasons
  support it, and to leave a claim uncited rather than stretch a citation. A malformed tag costs one
  citation, whereas malformed JSON would cost the whole summary.
- **Checked at generation.** `SummaryCitations.sanitise` keeps only tickets whose reasons were in
  the report, at most five per claim, and reduces any other tag to its phrase. A stored snapshot can
  therefore never name a ticket outside its scope, whatever the model wrote.
- **Resolved at read.** The annotated prose is stored in the existing `summary_snapshot.content`.
  On read it is parsed into segments, and the cited tickets are looked up under the same scope as
  every other figure on the page (window, monitored channels, team group, current classification
  prompt). A ticket that no longer resolves is dropped; a claim left with none reads as plain text.
- **Additive wire shape.** `summary.content` stays plain prose. `summary.segments` is added, present
  only when the prose cites something, and each cited segment carries its tickets' reason, date,
  channel and thread timestamp.
- **The page.** Cited phrases are underlined in the prose. Hovering opens a popover (click, tap or
  Enter also open and pin it) listing each ticket's reason and date, with links to its Slack thread
  and to the ticket. The popover says the citations are the model's own.

## Consequences

### Positive

- ✅ Each claim links straight to the threads behind it.
- ✅ No schema change: the citations live in the existing content column and the ticket details are
  read live, so nothing about tickets is duplicated into snapshots.
- ✅ Fails safe: no citations, or none that survive the checks, renders the prose exactly as before.
- ✅ No re-classification. Only the summary prompt changes; each cached summary is regenerated once,
  from existing classifications, on its window's next visit. That one-off regeneration is the cost
  [ADR-010](adr-010-team-groups-for-support-summary.md) avoided by leaving the summary prompt
  unversioned; this decision accepts it.
- ✅ Slack links are built from channel and timestamp, so rendering the page makes no Slack calls.

### Negative

- ⚠️ **Citations are the model's attribution, not proof.** The checks guarantee a cited ticket was in
  the report and is in scope, not that it supports the claim. Showing each ticket's reason beside
  its link makes a poor citation quick to spot.
- ⚠️ **Coverage depends on the model.** Some claims may go uncited.
- ⚠️ **Rollback needs a flag flip.** A release from before V44 would show version 2's markup as raw
  text, so summary prompt version 1 must be put back in use first (see
  `docs/runbooks/support-summary.md`).
- ⚠️ **The report grows.** Each reason line gains its id and four labels, roughly 15–30 tokens on top
  of a 25–40 token reason, so a window at `summary-area.page.max-reasons` (1000 by default) sends a
  report around 1.5–2× its previous size. That makes the call slower and costlier, and more likely
  to hit a provider timeout (`AI_PROXY_TIMEOUT`, 20s by default). Tune `SUMMARY_MAX_REASONS` or the provider timeout if it fails there.
