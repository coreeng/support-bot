# ADR: Documentation Gap Analysis

**Status:** Proposed

## Context

Support engineers answer the same questions repeatedly because the platform's tenant-facing
documentation does not cover them, or covers them wrongly. The bot already classifies why each
closed ticket was raised ([ADR-002](adr-002-automated-analysis-workflow.md)), but nothing compares
a ticket with the documentation, so nobody can say which pages are missing or wrong.

Three things shape the decision:

- **The documentation is too large to hand to a model whole.** The repository used for sizing has
  about 480 pages, roughly 550–650k tokens, in about 20 product areas.
- **Where the documentation comes from is expected to change.** A GitHub repository is the first
  source. A later one may be an MCP server that searches rather than a set of files.
- **The work is costly and slow.** The existing classification sends only the thread. This sends
  tens of thousands of tokens of documentation with it, and a window takes hours rather than
  minutes.

The behaviour is specified in the OpenSpec change `add-documentation-gap-analysis`. This ADR
records the architectural decisions behind it.

## Decision

Classify each closed ticket against the documentation as `correct`, `missing`, `incorrect` or
`not_applicable`, with evidence a person can check, and show the results on a Documentation
Analysis page under Support Summary. Keep finding the documentation apart from classifying the
ticket, so the first can be replaced without touching the second.

### The analysis and the documentation source

- **Two parts, one boundary.** The analysis fetches a ticket and its thread, obtains the relevant
  documentation, classifies, checks the answer and saves it. A documentation source answers one
  thing: which documentation is relevant to this thread. The analysis does not know where
  documentation comes from, and a source does not know what is done with it.
- **Four operations.** A source is opened once per run, where it refreshes whatever it keeps or
  reports itself unavailable. It reports a version, recorded with each result. It finds the context
  for a thread. It gives a fingerprint that changes whenever it would answer the same thread
  differently.
- **Four answers.** Asked for context, a source returns pages, or that nothing covers the question,
  or that the thread asks no documentation question, or a failure with a reason code. The second
  saves `missing` and the third `not_applicable`, both without the classification call.
- **A source is given only the thread.** No model call comes before it. A source that reads the
  thread anyway may state the question and judge whether it is a documentation question. Neither is
  required: the classification call does both when the source has not.
- **The analysis sets a budget, the source stays inside it.** The budget is the input limit less
  the thread and the prompt. A source that returns more fails the ticket. No page is ever
  truncated.
- **One source at a time**, chosen by configuration. Each source has a package of its own, nothing
  outside refers to its types, and a test enforces it.

### The GitHub source

- **A copy on the pod's disk.** The source keeps the documentation files under `/tmp` and fetches
  again only when it holds no copy or the copy is more than 24 hours old. It asks for the branch's
  head commit first and downloads one archive only when that has changed. A copy is replaced only
  by a complete one, and an expired copy is used when a fetch fails.
- **A guarded fetch.** Only regular files under the configured path with a configured extension
  are kept. Links and paths that would fall outside the copy are skipped, and a fetch over the size
  or file limit fails.
- **Routing by product area.** A product area is a top-level directory of the documentation. One
  model call reads an index of every page (path, title, description) and the thread. It states the
  question, says whether it is a documentation question, and chooses up to two areas. The source
  returns every page of those areas, plus the pages at the root.
- **Over the budget.** When two areas are too much, the first is returned alone and the second is
  named as not supplied. When the first alone is too much, the ticket fails and is not retried.

### Results and evidence

- **A table of its own.** `doc_analysis` holds one row per ticket. A row is current while its
  digest matches the classification prompt, the input limit and the source's fingerprint. A change
  to the documentation alone does not make a result stale. Only current results are shown.
- **Every verdict carries evidence.** `correct` and `incorrect` cite one page and quote at most 200
  characters from it. The bot keeps the result only when that page was among those the source
  returned and contains the quote, compared without regard to whitespace or emphasis markers.
  `missing` names the closest pages.
- **Every outcome is defined.** An answer the bot rejects is a failed attempt with a reason code,
  never a guess. The model may answer `undetermined` when the pages it was given are the wrong
  ones; that is a failure, not `missing`.
- **`incorrect` is a conflict, not a verdict on the documentation.** It means the documentation
  contradicts the answer support gave, so it needs a message from support. The bot labels each
  thread message as tenant, support or bot from the support team's membership and sends the label
  in place of the author.
- **Inputs are data.** Thread and documentation text are sent inside fixed tags, with those tag
  names removed from the text and the instructions placed last.
- **Structured output.** The answer is one JSON record per ticket.
  [ADR-011](adr-011-support-summary-evidence.md) chose inline markup because one malformed answer
  would cost a whole summary; here it costs one ticket, which is tried again.
- **Stored text is sanitised.** The question, answer summary and rationale pass through the
  deployment's sanitisation patterns ([ADR-005](adr-005-configurable-thread-sanitisation.md))
  before they are saved.

### Running

- **The page is the trigger.** Opening the Documentation Analysis page classifies the window's
  tickets that have no current result, with the same roles and window limits as the Support
  Summary page. This was chosen because it is how the Support Summary page already works, so the
  two pages behave alike and no new way of starting work is introduced. There is no scheduled job
  yet. One can be added later without changing the run: it would call the same entry point the
  page does.
- **Its own lock and its own worker.** The run takes its own `async_job` row and its own
  single-thread, no-queue executor. It can be active alongside one Support Summary refresh or
  manual analysis run. Calls within each run stay sequential. This amends ADR-002, which kept all
  LLM work on one lock and one worker.
- **Failures are handled by kind.** A provider error is retried within the run. Five tickets in a
  row failing that way stop the run without marking them failed. Any other failed ticket is tried
  again after 24 hours, three times at most, then only when the prompt, input limit or source
  changes.
- **Bounded cost.** The feature is off by default and has its own `enabled` switch. At most a
  configured number of tickets, 500 by default, are attempted in any 24 hours.
- **Checked against people.** Verdicts are compared with support engineers' labels on at least 150
  real tickets, against stated targets, once by script before building and once from the built
  feature before release.

### Not decided here

- A second documentation source. Only the boundary is built.
- A scheduled run. The run is built so that one can start it the way the page does.
- A button that starts a run.
- Suggesting or raising documentation fixes.
- Re-classifying when the documentation changes.
- GitHub credentials of the feature's own.

## Consequences

### Positive

- ✅ A source can be replaced or added by writing a source. The run, the checks, the table and the
  page do not change.
- ✅ Every `correct` and `incorrect` verdict points at a passage that is really in the page, and
  every result links to its ticket.
- ✅ A mistake in finding the documentation shows up as a failed ticket, not as a page someone is
  sent to write.
- ✅ A long documentation run does not hold up the Support Summary page.
- ✅ No new infrastructure: no index to build or store, no queue, no second process.
- ✅ An outage does not park a window's tickets, and spend is capped per day.

### Negative

- ⚠️ **Two LLM jobs can now call the provider at once.** The documentation calls are large, so the
  provider's tokens-per-minute limit is the one most likely to be hit.
- ⚠️ **Cost.** With the GitHub source, in the order of 5 to 19 million input tokens per 100 tickets
  on the repository used for sizing. This is an estimate until validation measures it.
- ⚠️ **Verdicts are the model's judgement.** The checks prove a quote exists, not that it answers
  the question, and `missing` and `not_applicable` carry no quote. Validation sets how far they are
  trusted.
- ⚠️ **The boundary was drawn with one source built.** A second may need it adjusted.
- ⚠️ **A source that does not judge the thread costs a classification call** on tickets that turn
  out not to be documentation questions.
- ⚠️ **Results lag the documentation.** A ticket stays `missing` after its page is written, until
  the prompt, input limit or source changes.
- ⚠️ **After such a change the page shows fewer results** until the window is classified again,
  which the daily cap can stretch over days.
- ⚠️ **Role labels use today's support team.** A message from someone who has since left it counts
  as a tenant's, so some conflicts in old threads are missed.
- ⚠️ **The copy is lost when the pod is replaced** and fetched again on the next run. A run resumed
  after that may continue against a newer version; each result records the one it used.
- ⚠️ **Documentation content is sent to the LLM provider**, which the bot has not done before.

## Alternatives considered

- **A triage call ahead of the source**, to state the question and decide whether it is a
  documentation question, so that every source receives the same input. Rejected: it adds a prompt
  and a model call to every ticket, and the GitHub source's routing call reads the thread anyway.
- **Routing inside the analysis**, with a source that only supplies files. Rejected: routing by page
  index suits documentation held as files. A source that searches has no use for it, so adding one
  would mean changing the run.
- **The whole documentation in every prompt.** No routing mistakes, but it needs a 1M-token model
  and is slow and costly per ticket.
- **Embedding or keyword retrieval.** Rejected: a retrieval miss looks exactly like a `missing`
  verdict, and it needs an index to build and store.
- **An agent with file tools.** Works at any size, but adds a tool loop the bot does not have and
  makes cost per ticket unpredictable.
- **Results on `ticket` or on `analysis`.** Rejected: the first puts LLM output on the core ticket
  record, and on the second the two features would invalidate each other's rows.
- **The copy in PostgreSQL.** It would survive pod replacement, but a re-fetch is cheap and the
  database would hold megabytes of documentation.
- **One shared lock, with the runs taking turns.** It needs no amendment to ADR-002 and cannot
  raise the request rate. Rejected: a Support Summary refresh would wait behind a documentation run
  for as long as it takes.
- **A button that starts the run.** It makes the spend a deliberate act, but departs from how the
  Support Summary page works. The `enabled` switch and the daily cap bound the cost without it.
- **A cap per run.** Rejected: any reader's visit starts a run and the page polls, so it would
  bound nothing.
- **Classifying in product-area order**, so consecutive calls share a documentation prefix the
  provider may cache. It needs a second state per ticket and a two-phase resume. Deferred until
  the cost is measured.
- **A random delimiter for each request** in place of removing tag names. The same guarantee, but
  every call would open with different text.
- **Pinning a resumed run to the commit it began on.** Rejected: each result already records its
  version, and it would need a second way of fetching.
- **Keeping old results visible, marked as old, after a prompt change.** Rejected: the counts would
  mix two versions of the prompt.
- **Skipping tickets the existing classification marks as task requests.** Rejected for now: it
  would tie this feature to the other classifier having run and being right.
