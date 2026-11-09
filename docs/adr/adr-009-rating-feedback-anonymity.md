# ADR: Rating Follow-up Feedback and Anonymity

**Date:** 2026-10-09
**Status:** Proposed

---

## Context

When a ticket closes, the thread author gets an ephemeral 1–5 star rating prompt. Ratings are stored in `ratings`
without any ticket, user, channel or thread reference, so they are anonymous by construction.

We want richer feedback than a number:

- **1–3 stars:** "What went wrong with your experience?"
- **4–5 stars:** "What made your experience worthwhile?"

Submitters must be able to give this feedback anonymously or linked to their support thread.

Requirement: **no names or Slack IDs are stored in the database**, neither the submitter's nor anyone else's.

## Decision

### Flow

1. A star click records the rating exactly as before.
2. The bot then opens a Slack modal with the follow-up questions.
   - The modal's `private_metadata` carries `{ratingId, ticketId, rating}`. This lives in Slack, not in our database.
3. The modal is optional. "Skip" leaves only the anonymous rating.

### Storage

The feedback is attached to the existing `ratings` row (migration `V41__rating_feedback.sql`):

| Column / table | Content | When set |
|---|---|---|
| `ratings.feedback` | Free-text answer | Always, on submit |
| `ratings.ticket_id` | Link to the ticket, and therefore the thread | **Only when the submitter ticks "Link my feedback to this support thread"** |

- **Anonymous** (default, unticked): `ticket_id` stays `NULL`, and the row is as unlinked as a rating is today.
- **Named** (linked to the thread): only the ticket ID is stored. The submitter's name or Slack ID is never stored,
  and the API returns only the ticket ID too. Viewers see who submitted it by following the ticket to its Slack
  thread.
- There is no feedback timestamp column, so feedback adds no timing signal beyond the rating's existing
  `submitted_ts`.

### Read access

Feedback is served from `GET /rating/feedback?dateFrom&dateTo`. It is restricted to the `LEADERSHIP` and
`SUPPORT_ENGINEER` roles, like the other dashboards.

It is deliberately **not** part of `/stats`, which is open to any authenticated user. Free-text feedback and named
ticket links are more sensitive than aggregate ratings.

Each item exposes only the submission **week**, not the exact timestamp, and this endpoint never returns anything
finer:

- `dateFrom` and `dateTo` are widened to whole UTC weeks (Monday to Sunday), so a single-day query returns the
  whole week.
- Items are ordered by week (latest first), then by rating and feedback text, never by submission time.
- **Anonymous feedback only from completed weeks.** Anonymous items are held back until their UTC week has ended,
  whatever `dateTo` is. The running week would otherwise show items as they arrive. Early in the week it covers
  only a day or two (on a Monday, just today), and anyone refreshing the panel could see which items are new since
  they last looked. Named items appear right away, as the submitter chose to be identifiable.

### Logging

The feedback path logs the rating ID and whether the feedback was anonymous or named. It never logs the ticket ID
alongside the rating ID, and never logs feedback text.

## Consequences

### Positive

- Actionable qualitative feedback, without weakening the anonymity of existing ratings.
- Submitters explicitly opt in to being identifiable, and nothing identifying is stored for anonymous feedback.

### Negative

- **Named feedback de-anonymises its rating.** Linking `ticket_id` also identifies that row's star rating. This is
  the intended effect of opting in.
- **Quiet weeks can still narrow anonymous feedback down.** Anonymous items keep their stars. If a week has only
  one rating with that many stars, the ratings stats (which can be queried for a single day) and the tickets
  table's Rated column can still point to its day and ticket.
- **Anonymous feedback is delayed.** It appears up to a week after it is submitted, at the next Monday 00:00 UTC.
  The dashboard's linked share is worked out over completed weeks only, as the running week has no anonymous
  items yet.
- **The hold-back goes by the rating's time.** There is no feedback timestamp, so feedback whose modal was still
  open at Monday 00:00 UTC belongs to a week that has already ended and appears as soon as it is submitted.
- **Free text can identify its writer.** No filtering protects against details the submitter writes themselves.
