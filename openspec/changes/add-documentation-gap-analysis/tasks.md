## 1. Validate the approach on real tickets

- [ ] 1.1 Draft the routing and classification prompts and run them by hand against a local clone of a documentation repository and at least one month of closed tickets. Have a support engineer label a sample of them. Record the agreement rate per classification and the false `missing` rate.
- [ ] 1.2 From 1.1, confirm the routing approach or revise `design.md` and the spec before building. Keep on record the false `missing` rate and the share of tickets whose existing primary driver is Task Request or Product Temporary Issue, for later decisions on a second check and a pre-filter.

## 2. Documentation copy

- [ ] 2.1 Add a method to `GitHubClient` that resolves a branch to its head commit and one that downloads the repository archive at a commit, implemented in `Hub4jGitHubClient`. Make the `GitHub` and `GitHubClient` beans available when this feature is configured as well as when PR tracking uses a GitHub repository. Verify with unit tests, and that a deployment with PR tracking off and this feature on starts.
- [ ] 2.2 Add configuration properties under a new top-level `documentation-analysis` block for the repository, branch, documentation path, file extensions, copy location (default under `/tmp`), maximum copy age (default 24h), maximum areas per ticket and the failed-ticket retry delay (default 24h), with validation. Add them to `application.yaml`, the Helm values and schema, and `api/service/docs/configuration.md`.
- [ ] 2.3 Add the documentation copy service: fetch when absent or expired, skip the download when the head commit is unchanged, replace the copy only when complete, fall back to an expired copy when a fetch fails, and expose the commit and fetch time. Verify each scenario of the spec's repository requirement with unit tests.
- [ ] 2.4 Build the page index (path, title, description; path and first heading when frontmatter is absent) and the product-area grouping when a copy is loaded. Verify with unit tests, including pages at the root of the documentation path.

## 3. Classification run

- [ ] 3.1 Add a migration for `doc_analysis` and for two new `analysis_prompt` types holding the routing and classification prompts from 1.1. Add the new table's columns to `service/src/test/resources/erasure-compliance.yaml`. Verify `ErasureComplianceTest` passes.
- [ ] 3.2 Add the per-ticket classifier: fetch the thread text as the existing classifier does, including its sanitisation (ADR-005), route, classify against the selected areas plus root pages, parse the structured result, reject a result whose quoted passage is not in the copy, and fail the ticket with a clear error when the selected areas exceed the model's context. Extend `StubChatModel` with canned answers for both prompts. Verify with unit tests for each classification, the rejected-quote case and the oversized-area case.
- [ ] 3.3 Add the run: find the window's closed tickets with no row for the current prompt digest, classify them one at a time with `llm.request-delay` between calls, save each result as it completes, record a failed ticket with its time and reason and continue, skip tickets that failed within the retry delay, and hold its own `async_job` row. Resume it on startup. Verify with tests for resume, for a failed ticket, for a visit inside and after the retry delay, and that a Support Summary refresh can start while it runs.
- [ ] 3.4 Add `GET /documentation-analysis` (results, counts, progress, documentation commit and fetch time) and `GET /documentation-analysis/enabled`, with the window rules of `/summary`. Restrict the results endpoint to leadership and support engineers in `SecurityConfig`. Verify with controller and security tests.

## 4. Page

- [ ] 4.1 Add the API routes and hooks in `ui`, and a feature flag hook alongside `useSummaryEnabled`. Add the Documentation Analysis entry under Support Summary in `app-sidebar.tsx`, hidden when the feature is off or the user lacks the capability. Verify with Jest tests.
- [ ] 4.2 Add the page: window picker, counts per classification and per product area, the failed count, the ticket list with evidence and ticket links, the progress state with polling, the unavailable state, and the documentation commit and fetch time. Verify with Jest tests for each state.
- [ ] 4.3 Add `documentation-analysis.feature` to `ui/p2p/tests/functional/tests/features/`, modelled on `summary.feature`, covering a first visit that reaches results and a user without access.

## 5. Documentation and checks

- [ ] 5.1 Update the support engineer and leadership user guides and add a runbook entry covering the GitHub permission needed, the minimum model context size, the proxy timeout, and what a failed fetch looks like.
- [ ] 5.2 Verify `./gradlew spotlessApply` and `./gradlew build` pass in `api`, and the lint, type check and tests pass in `ui`.
