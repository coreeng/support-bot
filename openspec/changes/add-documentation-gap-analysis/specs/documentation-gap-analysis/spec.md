## ADDED Requirements

### Requirement: Closed tickets are classified against the documentation

The bot SHALL classify each closed ticket as exactly one of:

- `correct`: the documentation answers the tenant's question, consistently with the answer
  support gave;
- `missing`: the question is one documentation should answer, and it does not;
- `incorrect`: the documentation addresses the question and contradicts the answer support gave;
- `not_applicable`: the ticket is not a documentation question, or is not a real query.

The bot SHALL identify the role of each thread message's author itself, as tenant, support or
bot, and SHALL give the model that role with each message, without the author's name or
identifier. The person who raised the ticket SHALL be treated as the tenant. A message SHALL
count as from support when its author is a member of the support team and did not raise the
ticket.

A ticket SHALL be classified `incorrect` only when its thread contains a message from support.
An `incorrect` result for a thread with no message from support SHALL be recorded as a failed
attempt. Open tickets SHALL NOT be classified. A window's tickets SHALL be those raised in it, as on the Support
Summary page.

A request to add a feature or change the platform SHALL be treated as not a documentation
question; a question whether something is supported SHALL be treated as one. When a thread
raises several questions, the first substantive one SHALL be the one classified.

Every result SHALL record the distilled question, up to five topic tags for it, a summary of
support's answer when there is one, the product areas consulted where the documentation has areas, a short rationale, the model
used, the documentation source, and the documentation version it was made against. A `correct` or `incorrect` result SHALL also record the one page
and the passage it relies on. The bot SHALL accept such a result only when that page was among
those given to the model and contains the passage, comparing the two without regard to
whitespace or markdown emphasis markers. A result that fails this check SHALL be recorded as a
failed attempt. A `missing` result SHALL record the closest pages found, when there are any.

A failed attempt SHALL be recorded with its time and a reason code, SHALL NOT count as a
classification, and SHALL be treated as a ticket that failed to classify.

The question, its tags, the summary of support's answer and the rationale SHALL be passed
through the deployment's configured thread sanitisation before they are saved.

A tag SHALL be lowercase words joined by hyphens. The bot SHALL drop a tag in any other form,
and a repeated one, and SHALL keep at most five. A result SHALL NOT be refused for having no
tags.

#### Scenario: Model reports that the documentation answers the question

- **WHEN** the model returns `correct` for a closed ticket, citing one page it was given and a
  passage that page contains
- **THEN** the ticket is saved as `correct` with that page and passage

#### Scenario: Model reports that the documentation does not cover the question

- **WHEN** the model returns `missing` for a closed ticket, naming closest pages that were among
  those it was given
- **THEN** the ticket is saved as `missing`, recording the product areas that were consulted and
  those pages

#### Scenario: Model reports a contradiction

- **WHEN** the model returns `incorrect` for a thread that contains a message from support, with
  a summary of support's answer, citing one page it was given and a passage that page contains
- **THEN** the ticket is saved as `incorrect` with that page, passage and summary

#### Scenario: Contradiction reported for a thread with no support message

- **WHEN** a verdict of `incorrect` is returned for a thread in which no message is from support
- **THEN** a failed attempt is recorded with that reason and the ticket is reported as failed

#### Scenario: Support engineer raises a ticket

- **WHEN** a member of the support team raises a ticket and nobody else from support replies
- **THEN** every message of theirs is given to the model as the tenant's, and the thread has no
  message from support

#### Scenario: Model reports that the ticket is not a documentation question

- **WHEN** the model returns `not_applicable` for a closed ticket
- **THEN** the ticket is saved as `not_applicable`, with no page or passage

#### Scenario: Tags are recorded

- **WHEN** a result comes with the tags `deploy-key`, `Rotate Credentials` and `deploy-key`
- **THEN** the result is saved with the single tag `deploy-key`

#### Scenario: Result without tags

- **WHEN** a result that passes its checks comes with no tags
- **THEN** the result is saved without tags

#### Scenario: Stored text is sanitised

- **WHEN** a deployment has a sanitisation pattern for email addresses and the model's rationale
  contains one
- **THEN** the rationale is saved without it

#### Scenario: Cited passage is not in the cited page

- **WHEN** a `correct` or `incorrect` verdict quotes a passage that the page it cites does not
  contain
- **THEN** a failed attempt is recorded with that reason, the ticket is reported as failed, and
  it is not attempted again until the retry delay has passed

#### Scenario: Quoted passage differs only in formatting

- **WHEN** a `correct` verdict quotes a passage that matches the cited page except for line
  breaks, spacing or emphasis markers
- **THEN** the result is saved

#### Scenario: Cited page was not given to the model

- **WHEN** a verdict cites a page that was not among those given to the model
- **THEN** a failed attempt is recorded with that reason

### Requirement: Context and classification outcomes are all defined

For each ticket the bot SHALL ask the documentation source for the documentation relevant to
its thread.

- When the source reports that the thread does not ask a documentation question, the ticket
  SHALL be classified `not_applicable`, and no documentation SHALL be sent to the model.
- When the source reports that nothing in the documentation covers the question, the ticket
  SHALL be classified `missing`, recording that, and no documentation SHALL be sent to the model.
- When the source fails, the attempt SHALL be recorded as failed with the source's reason.
- When the source returns pages, the ticket SHALL be classified against those pages and no
  others.

A source SHALL NOT be required to judge whether a thread asks a documentation question, nor to
state the question or its tags. What the source leaves out SHALL be settled when the ticket is
classified.

When the pages given to the model are not enough to judge the question, because it belongs to
a part of the documentation that was not given or the thread is too unclear, the attempt SHALL
be recorded as failed with that reason. It SHALL NOT be classified `missing`.

A model answer that cannot be read, names a classification outside the four, cites more than
one page for a `correct` or `incorrect` verdict, or gives an `incorrect` verdict without a
summary of support's answer SHALL be recorded as a failed attempt.

The bot SHALL NOT send the model more documentation than a configured input limit. It SHALL
tell the documentation source how much it may return, and SHALL record a failed attempt when
the source returns more. A page SHALL NOT be truncated.

#### Scenario: Not a documentation question

- **WHEN** the documentation source reports that a thread does not ask a documentation question
- **THEN** the ticket is classified `not_applicable` and no documentation pages are sent to the
  model

#### Scenario: Source does not judge the thread

- **WHEN** the documentation source returns pages for a thread without saying whether it asks a
  documentation question, and the model, given those pages, returns `not_applicable`
- **THEN** the ticket is saved as `not_applicable`

#### Scenario: Nothing covers the question

- **WHEN** the documentation source reports that nothing in the documentation covers a
  documentation question
- **THEN** the ticket is classified `missing`, with no product areas recorded and the source's
  reason as its rationale, and no documentation pages are sent to the model

#### Scenario: Source fails for one ticket

- **WHEN** the documentation source fails to find the documentation for one ticket
- **THEN** a failed attempt is recorded with the source's reason and the run continues

#### Scenario: Source returns more than the input limit

- **WHEN** the documentation source returns more documentation than it was told it may
- **THEN** nothing is sent to the model and a failed attempt is recorded with that reason

#### Scenario: Unreadable answer

- **WHEN** the model's answer is not the expected structure, or names a classification that is
  not one of the four
- **THEN** a failed attempt is recorded with that reason and the run continues

#### Scenario: Question belongs to documentation that was not given

- **WHEN** the model reports that the pages it was given cannot settle the question because it
  belongs to a part of the documentation it was not given
- **THEN** a failed attempt is recorded with that reason, and the ticket is not shown as
  `missing`

### Requirement: Documentation comes from a replaceable source

The bot SHALL obtain documentation only through a documentation source. One source SHALL be
active at a time, chosen by configuration. Asked about a thread, a source SHALL answer with the
pages relevant to it, or that nothing covers its question, or that it asks no documentation
question, or a failure, and SHALL report the version of the documentation it answered from.

How tickets are found, classified, checked, stored, retried and shown SHALL NOT depend on which
source is active.

At the start of a run the bot SHALL ask the source to make itself ready. When the source cannot,
the run SHALL fail without classifying any ticket and the page SHALL report the analysis as
unavailable and why.

Every result of an uninterrupted run SHALL be made against the same documentation version. A
run resumed after a restart MAY continue against a newer one. Each result SHALL record the
version it was made against.

#### Scenario: Source cannot be made ready

- **WHEN** a run starts and the documentation source reports that it is unavailable
- **THEN** no ticket is classified and the page reports the analysis as unavailable

#### Scenario: Another source is configured

- **WHEN** a deployment is changed to a different documentation source, and a user opens a
  window classified with the first
- **THEN** a run re-classifies that window's closed tickets with documentation from the new
  source, and the results are checked and shown as before

#### Scenario: Source without product areas

- **WHEN** the active source does not divide its documentation into product areas
- **THEN** results are saved with no product areas, and the page counts them under no area

### Requirement: Opening the page classifies the window's unclassified tickets

When the Documentation Analysis results for a window are requested and the window contains
closed tickets without a current result, the bot SHALL start classifying them in the background
and SHALL report that a run is in progress, with the number classified so far and the total.
There SHALL be no scheduled run. An interrupted run SHALL lose at most the ticket in flight, and
a run interrupted by a restart SHALL resume.

A result SHALL stop being current when the prompts used for documentation analysis change, when
the input limit changes, or when the documentation source or a setting of it that changes what
it returns does. A change to the documentation alone SHALL NOT make a result stale.

Only current results SHALL be shown or counted. A ticket whose result is no longer current
SHALL count as not yet classified, and its next attempt SHALL replace the old result whether it
succeeds or fails.

At most one documentation analysis run SHALL be active at a time. A request made while a run is
active SHALL report that run's progress and SHALL NOT start another. A documentation analysis
run SHALL NOT prevent a Support Summary refresh from starting, nor the reverse.

A ticket that fails to classify SHALL NOT stop the run. It SHALL remain unclassified and SHALL
NOT be attempted again until a configured retry delay, 24 hours by default, has passed since the
failure. After a configured number of failed attempts, three by default, it SHALL NOT be
attempted again at all. A ticket that is not due SHALL NOT cause a run to start. All of this
SHALL be reset when a result stops being current.

A model call that fails because of the provider (a rate limit, a timeout, a server or network
error) SHALL be retried within the run, up to three times and with increasing waits, before the
ticket is recorded as failed. When five tickets in a row fail for such a reason, the run SHALL
stop, those failures SHALL NOT be kept against the tickets, the page SHALL report the analysis
as unavailable, and no run SHALL start until a configured delay, 15 minutes by default, has
passed.

The bot SHALL attempt at most a configured number of tickets in any 24 hours, 500 by default.
On reaching it the run SHALL stop, the page SHALL say that the daily limit was reached, and no
run SHALL start while the limit is still reached.

A request that names no window SHALL get the 14 days ending yesterday, as a Support Summary
request does. The page SHALL open on the window the Support Summary page opens on. A window
SHALL be refused when it ends before it starts or exceeds 92 days.

#### Scenario: First visit to a window

- **WHEN** a user opens the page for a window with 40 closed tickets, none classified
- **THEN** a run starts, the page shows progress out of 40, and results appear as they are saved

#### Scenario: Everything already classified

- **WHEN** a user opens the page for a window whose closed tickets all have current results
- **THEN** the results are shown and no run starts

#### Scenario: Prompt changed

- **WHEN** a documentation analysis prompt is replaced by a new version, and a user opens a
  window classified with the old one
- **THEN** a run re-classifies that window's closed tickets, and until a ticket is re-classified
  the page counts it as not yet classified and does not show its old result

#### Scenario: Run already active

- **WHEN** a second user opens the page while a run is active
- **THEN** the page shows the active run's progress and no second run starts

#### Scenario: Support Summary refresh during a documentation run

- **WHEN** a user opens the Support Summary page for a window that needs a refresh while a
  documentation analysis run is active
- **THEN** the refresh starts and both runs proceed

#### Scenario: Documentation run during a Support Summary refresh

- **WHEN** a user opens the Documentation Analysis page for an unclassified window while a
  Support Summary refresh is active
- **THEN** the documentation analysis run starts and both runs proceed

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

#### Scenario: Ticket out of attempts

- **WHEN** a user opens a window whose only unclassified ticket has failed three times, the
  last more than the retry delay ago
- **THEN** the ticket is reported as failed and no run starts

#### Scenario: Setting that changes results is changed

- **WHEN** the input limit is changed, and a user opens a window classified before the change
- **THEN** a run re-classifies that window's closed tickets, including those that were out of
  attempts

#### Scenario: Passing provider error

- **WHEN** a model call is rate-limited and succeeds when retried
- **THEN** the ticket is classified and no failure is recorded

#### Scenario: Provider outage

- **WHEN** five tickets in a row fail because the provider cannot be reached
- **THEN** the run stops, none of the five is recorded as failed, the page reports the analysis
  as unavailable, and a visit a minute later does not start a run

#### Scenario: Daily limit reached

- **WHEN** a run reaches the daily limit with tickets of the window still unclassified
- **THEN** the run stops, the page shows the results so far and says the daily limit was
  reached, and further visits do not start a run until the limit is no longer reached

#### Scenario: Window too long

- **WHEN** a user requests a window of more than 92 days
- **THEN** the request is refused and no run starts

### Requirement: The page shows results with their evidence

For the chosen window the page SHALL show:

- the number of closed tickets in each classification, the number not yet classified, and the
  number whose last attempt failed;
- the same counts broken down by product area, with each ticket counted once: under the area of
  the page it cites when it cites one, otherwise under the first area consulted for it, and
  under no area when none was;
- each classified ticket with its classification, distilled question, rationale, and, where
  recorded, the documentation page and passage, with a link to the ticket;
- the tickets listed with `incorrect` and `missing` first, and a way to show one classification
  only;
- `correct` and `incorrect` results grouped by the page they cite, and `missing` results by
  product area, each group with its number of tickets;
- for each result, the documentation version it was made against;
- the documentation source's current version and when it was last refreshed.

The page SHALL present `incorrect` as "Conflicts with support's answer" and SHALL show the
quoted passage together with the summary of support's answer, without stating which of the two
is wrong.

When no result exists and no run is active because the last run failed, the page SHALL say that
the analysis is unavailable and why, without exposing credentials or raw provider errors.

#### Scenario: Reviewing a missing-documentation verdict

- **WHEN** a user opens a ticket classified `missing`
- **THEN** they see the distilled question, the rationale and the product areas consulted, and
  can follow a link to the ticket

#### Scenario: Reviewing a conflict

- **WHEN** a user opens a ticket classified `incorrect`
- **THEN** it is labelled "Conflicts with support's answer", and they see the quoted passage
  next to the summary of support's answer

#### Scenario: Several tickets cite the same page

- **WHEN** three tickets of the window are classified `incorrect` citing the same page
- **THEN** the page shows that documentation page once, with a count of three and its tickets

#### Scenario: Ticket with two areas consulted

- **WHEN** a ticket for which two product areas were consulted is classified `correct`, citing a
  page of the second
- **THEN** it is counted once, under the second area

#### Scenario: Results from two documentation versions

- **WHEN** a window holds results made against two versions of the documentation
- **THEN** each result shows its own version, and the page shows the source's current version

#### Scenario: Showing one classification

- **WHEN** a user chooses to see only `missing` results
- **THEN** only those tickets are listed, grouped by product area

#### Scenario: Partly classified window

- **WHEN** a run has classified 10 of a window's 40 closed tickets
- **THEN** the page shows the 10 results and reports 30 tickets as not yet classified

### Requirement: The feature is available only when configured, to the Support Summary's readers

The feature SHALL be off unless it is switched on in configuration. The Documentation Analysis
page, its menu entry and its endpoints SHALL be available only when it is switched on and an
LLM provider is configured. Otherwise the menu entry SHALL be hidden and the endpoints SHALL
NOT start a run or ask the documentation source for anything.

When the feature is switched on and the chosen documentation source is unknown or lacks a
setting it needs, the bot SHALL refuse to start and SHALL name the setting.

The results and the run they trigger SHALL be available only to the roles that can read the
Support Summary: leadership and support engineers.

#### Scenario: Feature not switched on

- **WHEN** an LLM provider is configured and the feature is not switched on
- **THEN** the menu entry is hidden and the Support Summary page works as before

#### Scenario: Switched on with an unknown source

- **WHEN** the feature is switched on and its configuration names a documentation source that
  does not exist
- **THEN** the bot does not start, and the error names the setting

#### Scenario: User without access

- **WHEN** a user with neither the leadership nor the support engineer role requests the results
- **THEN** the request is refused and no run starts

### Requirement: Thread and documentation text reach the model as data

The bot SHALL send the thread, the question and the documentation pages to the model inside
delimiters, placed before its instructions. Anything a documentation source sends to the model
SHALL be sent the same way. It SHALL remove from that text anything that
imitates the delimiters, so that text from a thread or a page cannot end its own section or
begin another.

#### Scenario: Thread contains a delimiter

- **WHEN** a thread message contains the text the bot uses to close the thread section,
  followed by an instruction to classify the ticket `not_applicable`
- **THEN** the delimiter text is removed and the whole message is sent as part of the thread

#### Scenario: Documentation page contains a delimiter

- **WHEN** a documentation page contains the text the bot uses to begin a page
- **THEN** the delimiter text is removed and the page is sent as one page
