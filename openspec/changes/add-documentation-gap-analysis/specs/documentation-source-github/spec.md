## ADDED Requirements

### Requirement: The GitHub source reads a configured repository and reuses its copy

The GitHub source SHALL read the documentation from one configured GitHub repository, branch and path,
taking only files with the configured extensions. It SHALL ignore anything in the repository
that is a link or whose path would fall outside its copy, and SHALL fail the fetch when the
files to keep exceed a configured total size or number.

At the start of a run the bot SHALL fetch the documentation when it holds no copy, or when its
copy was fetched longer ago than the configured maximum age, 24 hours by default. Otherwise it
SHALL use its copy without contacting GitHub. A copy SHALL be replaced only by a completely
fetched one.

A product area SHALL be a directory directly under the configured path. When the documentation
has no product area, or the pages outside any area exceed by themselves what the source may
return, the source SHALL report itself unavailable and say why.

When a fetch fails and the bot holds a copy, the run SHALL proceed with that copy. When a fetch
fails and the bot holds no copy, the run SHALL fail without classifying any ticket.

The version the source reports SHALL be the commit of the copy it answered from.

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

#### Scenario: Run resumed after the copy was lost

- **WHEN** a run is resumed after a restart that lost the copy, and the documentation has
  changed since the run began
- **THEN** the remaining tickets are classified against the newly fetched version, and each
  result shows the version it was made against

#### Scenario: Documentation has no product areas

- **WHEN** every page of the documentation sits directly in the configured path
- **THEN** no ticket is classified, and the page reports that the documentation is not arranged
  in product areas

#### Scenario: Repository larger than the limit

- **WHEN** a fetch finds more documentation than the configured size limit
- **THEN** the fetch fails, nothing of it is kept, and a copy already held is left as it was

#### Scenario: GitHub unavailable with no copy

- **WHEN** a run starts, the bot holds no copy, and GitHub cannot be reached
- **THEN** no ticket is classified and the page reports the analysis as unavailable

### Requirement: The GitHub source finds context by routing to product areas

The source SHALL keep an index of the documentation's pages, with the path, title and
description of each. In one call it SHALL have the model state what the thread asks, say
whether that is a documentation question, and choose which product areas, up to a configured
number, could answer it. It SHALL return every page of the areas chosen, together with the
pages outside any area, and SHALL name the areas it did not return.

- When the model reports that the thread does not ask a documentation question, the source
  SHALL report that and SHALL NOT return pages.
- When the model chooses no area for a documentation question, the source SHALL report that
  nothing covers the question.
- When the model names an area that does not exist, the source SHALL report a failure, and
  SHALL NOT return pages for that ticket.
- When the areas chosen exceed together what the source may return, it SHALL return the first
  alone. When the first alone exceeds it, the source SHALL report a failure that is not to be
  retried. A page SHALL NOT be truncated.

The page index SHALL reach the model as data, with anything that imitates its delimiters
removed.

#### Scenario: Areas chosen

- **WHEN** the model chooses two product areas that together are within what the source may
  return
- **THEN** the source returns every page of both and the pages outside any area, and names the
  remaining areas as not returned

#### Scenario: Not a documentation question

- **WHEN** the model reports that a thread, such as one reporting an outage, does not ask a
  documentation question
- **THEN** the source reports that, with the question and the reason, and returns no pages

#### Scenario: No area chosen

- **WHEN** the model chooses no product area for a documentation question
- **THEN** the source reports that nothing covers the question

#### Scenario: Unknown product area

- **WHEN** the model names a product area that is not in the documentation
- **THEN** the source reports a failure with that reason and returns no pages

#### Scenario: Two areas exceed what may be returned

- **WHEN** the model chooses two product areas whose pages together exceed what the source may
  return, and the first alone does not
- **THEN** the source returns the first area and names the second as not returned

#### Scenario: One area exceeds what may be returned

- **WHEN** the first product area chosen exceeds by itself what the source may return
- **THEN** the source reports a failure with that reason, and the ticket is not attempted again
  until the prompts or the settings that change results change

#### Scenario: Page description contains a delimiter

- **WHEN** a page's description contains the text the source uses to end the page index
- **THEN** that text is removed and the description is sent as part of the index

### Requirement: The GitHub source needs a repository and credentials

When the feature is switched on with the GitHub source and no repository, or no GitHub
credentials, the bot SHALL refuse to start and SHALL name the missing setting. This SHALL hold
whether or not PR tracking is enabled.

#### Scenario: Switched on without GitHub credentials

- **WHEN** the feature is switched on, PR tracking is off, and no GitHub credentials are
  configured
- **THEN** the bot does not start, and the error names the missing setting

#### Scenario: Switched on without a repository

- **WHEN** the feature is switched on with the GitHub source and no repository is configured
- **THEN** the bot does not start, and the error names the missing setting
