## ADDED Requirements

### Requirement: Closed tickets are classified against the documentation

The bot SHALL classify each closed ticket as exactly one of:

- `correct`: the documentation answers the tenant's question, consistently with the answer
  support gave;
- `missing`: the question is one documentation should answer, and it does not;
- `incorrect`: the documentation addresses the question and contradicts the answer support gave;
- `not_applicable`: the ticket is not a documentation question, or is not a real query.

A ticket SHALL be classified `incorrect` only when its thread contains an answer from support.
Open tickets SHALL NOT be classified. A window's tickets SHALL be those raised in it, as on the Support
Summary page.

Every result SHALL record the distilled question, a summary of support's answer when there is
one, the product areas consulted, a short rationale, the model used, and the documentation
version it was made against. A `correct` or `incorrect` result SHALL also record the page and
the passage it relies on. A result whose passage does not appear in the documentation version
recorded SHALL NOT be saved, and the ticket SHALL remain unclassified.

#### Scenario: Documentation answers the question

- **WHEN** a closed ticket asks how to do something that a documentation page explains, and
  support's answer agrees with the page
- **THEN** the ticket is classified `correct`, citing that page and the passage

#### Scenario: Documentation does not cover the question

- **WHEN** a closed ticket asks a question about platform usage that no page covers
- **THEN** the ticket is classified `missing`, recording the product areas that were consulted

#### Scenario: Documentation contradicts support's answer

- **WHEN** a closed ticket's thread contains support's answer, and a documentation page says
  otherwise
- **THEN** the ticket is classified `incorrect`, citing the page and the contradicting passage

#### Scenario: Ticket is not a documentation question

- **WHEN** a closed ticket asks support to perform a task, such as granting access
- **THEN** the ticket is classified `not_applicable`

#### Scenario: Cited passage is not in the documentation

- **WHEN** a `correct` or `incorrect` verdict quotes a passage that is not in the documentation
- **THEN** no result is saved and the ticket remains unclassified

### Requirement: Opening the page classifies the window's unclassified tickets

When the Documentation Analysis results for a window are requested and the window contains
closed tickets without a current result, the bot SHALL start classifying them in the background
and SHALL report that a run is in progress, with the number classified so far and the total.
There SHALL be no scheduled run. An interrupted run SHALL lose at most the ticket in flight, and
a run interrupted by a restart SHALL resume.

A result SHALL stop being current when the prompts used for documentation analysis change. A
change to the documentation alone SHALL NOT make a result stale.

At most one documentation analysis run SHALL be active at a time. A request made while a run is
active SHALL report that run's progress and SHALL NOT start another. A documentation analysis
run SHALL NOT prevent a Support Summary refresh from starting, nor the reverse.

A ticket that fails to classify SHALL NOT stop the run. It SHALL remain unclassified and SHALL
NOT be attempted again until a configured retry delay, 24 hours by default, has passed since the
failure, or the prompts change. Until then it SHALL NOT cause a run to start.

The window SHALL default to the same period as the Support Summary page and SHALL be refused
when it ends before it starts or exceeds 92 days.

#### Scenario: First visit to a window

- **WHEN** a user opens the page for a window with 40 closed tickets, none classified
- **THEN** a run starts, the page shows progress out of 40, and results appear as they are saved

#### Scenario: Everything already classified

- **WHEN** a user opens the page for a window whose closed tickets all have current results
- **THEN** the results are shown and no run starts

#### Scenario: Prompt changed

- **WHEN** a documentation analysis prompt is replaced by a new version, and a user opens a
  window classified with the old one
- **THEN** a run re-classifies that window's closed tickets

#### Scenario: Run already active

- **WHEN** a second user opens the page while a run is active
- **THEN** the page shows the active run's progress and no second run starts

#### Scenario: Restart during a run

- **WHEN** the bot restarts while a run is active
- **THEN** the run resumes, and tickets classified before the restart are not classified again

#### Scenario: One ticket fails

- **WHEN** one ticket of a run cannot be classified
- **THEN** the run classifies the remaining tickets, and the failed ticket is reported as failed

#### Scenario: Visit soon after a failure

- **WHEN** a user opens a window whose only unclassified ticket failed an hour ago
- **THEN** the results are shown, the ticket is reported as failed, and no run starts

#### Scenario: Visit after the retry delay

- **WHEN** a user opens a window containing a ticket that failed more than the retry delay ago
- **THEN** a run starts and attempts that ticket again

#### Scenario: Window too long

- **WHEN** a user requests a window of more than 92 days
- **THEN** the request is refused and no run starts

### Requirement: The page shows results with their evidence

For the chosen window the page SHALL show:

- the number of closed tickets in each classification, the number not yet classified, and the
  number whose last attempt failed;
- the same counts broken down by product area;
- each classified ticket with its classification, distilled question, rationale, and, where
  recorded, the documentation page and passage, with a link to the ticket;
- the documentation version the results were made against and when the documentation was last
  fetched.

When no result exists and no run is active because the last run failed, the page SHALL say that
the analysis is unavailable and why, without exposing credentials or raw provider errors.

#### Scenario: Reviewing a missing-documentation verdict

- **WHEN** a user opens a ticket classified `missing`
- **THEN** they see the distilled question, the rationale and the product areas consulted, and
  can follow a link to the ticket

#### Scenario: Partly classified window

- **WHEN** a run has classified 10 of a window's 40 closed tickets
- **THEN** the page shows the 10 results and reports 30 tickets as not yet classified

### Requirement: The documentation is read from a configured repository and reused

The bot SHALL read the documentation from one configured GitHub repository, branch and path,
taking only files with the configured extensions.

At the start of a run the bot SHALL fetch the documentation when it holds no copy, or when its
copy was fetched longer ago than the configured maximum age, 24 hours by default. Otherwise it
SHALL use its copy without contacting GitHub. A copy SHALL be replaced only by a completely
fetched one.

When a fetch fails and the bot holds a copy, the run SHALL proceed with that copy. When a fetch
fails and the bot holds no copy, the run SHALL fail without classifying any ticket.

Every result of one run SHALL be made against the same documentation version.

#### Scenario: Recent copy held

- **WHEN** a run starts and the bot's copy was fetched 3 hours ago
- **THEN** the run uses that copy and GitHub is not contacted

#### Scenario: No copy, or copy older than the maximum age

- **WHEN** a run starts and the bot holds no copy, or its copy was fetched 30 hours ago
- **THEN** the bot fetches the documentation before classifying any ticket

#### Scenario: GitHub unavailable with a copy held

- **WHEN** a run starts, the copy has expired, and GitHub cannot be reached
- **THEN** the run classifies against the expired copy, and the page shows that copy's version
  and fetch time

#### Scenario: GitHub unavailable with no copy

- **WHEN** a run starts, the bot holds no copy, and GitHub cannot be reached
- **THEN** no ticket is classified and the page reports the analysis as unavailable

### Requirement: The feature is available only when configured, to the Support Summary's readers

The Documentation Analysis page, its menu entry and its endpoints SHALL be available only when
an LLM provider and a documentation repository are both configured. Otherwise the menu entry
SHALL be hidden and the endpoints SHALL NOT start a run or contact GitHub.

The results and the run they trigger SHALL be available only to the roles that can read the
Support Summary: leadership and support engineers.

#### Scenario: No documentation repository configured

- **WHEN** an LLM provider is configured and no documentation repository is
- **THEN** the menu entry is hidden and the Support Summary page works as before

#### Scenario: User without access

- **WHEN** a user with neither the leadership nor the support engineer role requests the results
- **THEN** the request is refused and no run starts
