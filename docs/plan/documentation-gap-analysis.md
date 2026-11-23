# Implementation plan: Documentation Gap Analysis

How the OpenSpec change `add-documentation-gap-analysis` is built, as eight pull requests. The
behaviour is in the change's `proposal.md`, `design.md` and specs; the decisions are in
[ADR-012](../adr/adr-012-documentation-gap-analysis.md). Task numbers below refer to the change's
`tasks.md`.

Every pull request merges behind `documentation-analysis.enabled: false`, so nothing is visible
until the page lands.

## Pull requests

| # | Pull request | Tasks | Size | Needs |
|---|---|---|---|---|
| 1 | Source groundwork: boundary, stub source, package test, input wrapping, config block with Helm and `configuration.md` | 2.1–2.3 | M | — |
| 2 | Analysis groundwork: `doc_analysis` migration, repository, erasure-compliance entries, role-labelled thread | 4.1, 4.2 | M | — |
| 3 | GitHub source fetch: client methods, beans and credential check, local copy with guards | 3.1, 3.2 | M | 1 |
| 4 | GitHub source routing: page index, product areas, `findContext`, routing prompt | 3.3, 3.4 | M | 3 |
| 5 | Per-ticket classifier | 4.3 | M | 1, 2 |
| 6 | Run: own lock and executor, resume, failure handling, daily cap | 4.4 | M–L | 5 |
| 7 | Endpoints and security rules | 4.5 | M | 6 |
| 8 | UI: hooks, sidebar entry, page, functional feature file, user guides and runbook | 5.1–5.3, 6.1, 6.2 | M–L | 7 |

Task 6.4 (formatting, build, lint and tests) applies to every pull request.

## Order

- **First:** 1 and 2. They do not depend on each other.
- **Then two tracks side by side:**
  - GitHub source: 3 → 4.
  - Analysis: 5 → 6 → 7 → 8.
- Pull request 5 tests against the stub source, so it does not wait for 3 and 4.
- The feature works end to end once 4 and 7 are both merged. Pull request 8 makes it visible.

## Notes on each pull request

1. **Source groundwork.** The boundary of design Decision 2 and the settings of Decision 6. The
   package test uses ArchUnit, already a test dependency. Startup fails when the feature is
   enabled with an unknown source.
2. **Analysis groundwork.** The classification prompt stored here is the draft in the change's
   `prompts/classification.md`. `getThreadAsText` stays unchanged, so the existing classifier's
   input does not move.
3. **GitHub source fetch.** `GHRepository.readTar` and `getBranch` exist in the GitHub client
   library already in use, so no new dependency is needed. The archive guards (links, `..` paths,
   size and file limits) are security-sensitive and are the reason this is not merged with 4.
4. **GitHub source routing.** Stores the draft in `prompts/github-source-routing.md` as a prompt
   type and adds a canned routing answer to `StubChatModel`.
5. **Per-ticket classifier.** Every row of the outcomes table in design Decision 8, with the quote
   check, tags and sanitisation of stored text.
6. **Run.** The largest. If it grows past a comfortable review, split failure handling (provider
   retries, the outage stop, retry delay, attempts and the daily cap) into a pull request of its
   own, leaving the lock, executor, due tickets and resume in the first.
7. **Endpoints.** Fixes the response shape the page reads.
8. **UI.** Includes the user guides and runbook, and the check of ADR-012 against what was built.

## Migrations

The latest migration on `main` is V44. Pull request 2 takes the next number and pull request 4 the
one after. Other open branches may claim them first, so expect to renumber at merge.

## Building before validation

Tasks 1.1–1.3 (validating the approach on labelled tickets) are not done yet, and building starts
without them. That has three consequences:

- **The prompts are drafts.** A prompt revised after validation is one more migration. The digest
  in `prompt_id` then makes earlier results stale without further work.
- **Rework risk sits in pull requests 4 and 5.** If validation misses its targets, routing and the
  classification outcomes are what change. The boundary, config, copy, table, role labels, run
  and page are unlikely to.
- **Release stays blocked.** Task 6.3 needs the labelled set from 1.1. The feature stays off in
  production until 1.3 and 6.3 pass.
