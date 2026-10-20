# ADR: Personal Data Erasure Requests

**Date:** 2026-10-15
**Status:** Accepted

---

## Context

ISO/IEC 27001:2022 control A.8.10 requires company-specified data to be deleted when it is no longer required or may no longer be kept, and requires that deletion to be evidenced. An **Erasure Request** meets this: one instruction to delete or anonymise one person's data across every system that holds it. An **erasure orchestrator**, a system outside Support Bot, issues each Erasure Request to every participating system and tracks it until every participant reports that the work is done.

Support Bot is one of those systems. It holds people's data in its own PostgreSQL database and in the systems it holds credentials for (Slack, GitHub, GitLab). Only Support Bot knows where that data sits, so only Support Bot can fulfil an Erasure Request for its own stores. This ADR decides how Support Bot accepts and reports Erasure Requests, using the same transport and contract as the orchestrator's other participants.

An Erasure Request names the person by their current email address and carries the replacement email address that a participant rewrites it to. It carries no user ids.

---

## Affected Personas

- **Compliance owners**: get per-system evidence that a leaver's data was erased from Support Bot.
- **Support Bot deployers**: run an additional endpoint pair that the erasure orchestrator calls; see [ADR-010](adr-010-service-caller-on-an-internal-port.md) for how it is exposed.
- **Support engineers and tenants**: no change in behaviour.

---

## Decision Drivers

- Support Bot must confirm the work was done through some output.
- Support Bot owns its schema and the external artefacts it created; no central component can correctly enumerate its tables.
- External deletions (Slack, GitHub) take seconds to minutes and fail independently of the database work, so partial completion is a normal state to observe, not an error to hide.
- The mechanism must be idempotent: crashes, retries and re-drives converge on one erasure.
- The orchestrator already speaks one protocol to every participant; Support Bot should not need a bespoke one.

---

## Options Considered

### Option 1 — Asynchronous REST erasure endpoints (initiate and poll)

Support Bot exposes an endpoint that accepts an Erasure Request, returns 202 Accepted at once and does the work in the background; a status endpoint reports progress until the work completes. The orchestrator submits the request, polls the status endpoint and records the result as evidence.

**Pros.** Plain REST, which Support Bot already serves. Slow external deletions are unremarkable — a Slack or GitHub call taking minutes leaves the status in progress. Re-issuing the same request id makes crash recovery a re-POST. The status endpoint carries the outcome the evidence record needs anyway.

**Cons.** Support Bot gains an endpoint pair and its first caller that is a service rather than a person. Completion is eventual, so overdue requests must be noticed by the orchestrator rather than by the caller of a single call.

### Option 2 — Synchronous REST erasure endpoint (request held open)

A single endpoint, held open until Support Bot finishes its deletion or anonymisation work, whose response reports the outcome.

**Pros.** No polling and no status endpoint.

**Cons.** Deletions that touch external systems can outlast the held connection. The orchestrator still needs persisted state to survive its own crash, since a dropped held-open call says nothing about what completed.

### Option 3 — Erasure commands over a message broker

An Erasure Request is published once; Support Bot subscribes, does the work and reports the outcome.

**Pros.** Durable delivery and redelivery come from the broker rather than bespoke code.

**Cons.** Neither the orchestrator nor Support Bot runs a broker, so this buys a new class of infrastructure for every deployment. Delivery is not completion: the reporting half of Option 1 is still needed.

---

## Decision

Option 1.

- `POST /api/erasure-requests` accepts `{requestId, currentUserEmail, newUserEmail}` and answers 202 Accepted with the request's status.
- `GET /api/erasure-requests/{requestId}` reports `{requestId, status, requestedAt}`, or 404 for an id Support Bot has not accepted.
- The path has an `/api` prefix even though Support Bot's other backend paths have none, because the orchestrator calls every participant at the same path.

The following contract holds, as it does for the orchestrator's other participants. Like them, Support Bot reports one status per request rather than an outcome per store:

- **Idempotency per request.** The request id is the idempotency key. Resubmitting an id returns the request first accepted under it, unchanged, whatever the new payload says.
- **Report only work done.** A request is reported complete only once Support Bot has erased the person's data. The orchestrator treats its completion status as final.
- **Anonymisation satisfies an Erasure Request.** Support Bot may anonymise rows instead of deleting them where removal would break aggregates or history.
- **Emails are not disclosed.** The email addresses in a request never appear in a response, an error body or a log line; logs carry the request id only.
- **Backups are handled by window expiry.** Erased data stays restorable from database backups for the deployer's retention window. Any restore must replay the Erasure Requests completed since the backup point.

Support Bot first ships intake only: it accepts and reports requests, and every accepted request stays in progress. Processing — finding and erasing the person's data — follows in a later change, and until it ships Support Bot must not be registered as a participant with the orchestrator, which would otherwise hold every new Erasure Request open.

---

## Consequences

### Positive

- Support Bot joins the orchestrator's erasures with the same protocol as its other participants; the orchestrator needs only Support Bot's base URL.
- Slow or failing external deletions show up as an in-progress status rather than a timeout.
- Replays after a crash on either side converge on one request.

### Negative / Trade-offs

- Support Bot gains endpoints called by a service rather than a person, and therefore a second way to be trusted; [ADR-010](adr-010-service-caller-on-an-internal-port.md) decides it.
- Support Bot stores the email addresses of the person being erased until processing completes.
- Until processing ships, accepting a request does nothing beyond recording it.
- The orchestrator sends every Erasure Request, whichever of its tenants raised it, to every registered participant. Support Bot cannot tell which tenant is its own organisation, so once registered it accepts and stores the emails of leavers from every tenant the orchestrator serves, including people it holds no data about, until processing completes those requests. This is accepted: a request for someone Support Bot holds nothing about erases nothing.

### Neutral

- Support Bot serves one organisation per deployment, so a request is not scoped to one of the orchestrator's tenants.
