# 1. Persistence

Date: 2025-01-07

## Status

Accepted

## Context

The project is basically a very simple ticketing system,
so we need a persistence layer to store the information about created tickets.

We also need is to be able to expose different kinds of metrics
to ensure quality of service.

Metrics are going to be fetched and presented via Grafana.

### Options

- Slack as state storage.
  It's possible to use slack API to derive the state of the application.
  Slack provides an API to store arbitrary data withing a message,
  so we can use it to store metadata required for the application to work.
  - Pros: No need to deploy anything else together with the bot.
  - Cons: Reliance on third party API for storing our data.
    API might not be flexible enough to easily support required cases.
    Metrics extraction is complicated.
- Slack as a state storage and prometheus as metrics storage.
  - Pros: easier to collect and store metrics
  - Cons: The need to deploy and manage Prometheus.
    By the nature of prometheus metrics are not absolutely accurate
    (since they are stored in memory and polled).
    All other cons caused by storing business data in slack.
- Separately deployed database.
  Relational DB such as Postgres should work well,
  since SQL is well suited for writing analytic queries
  and in general is well suited for application development.
  With document based DB (such as MongoDB)
  it would be a bit simpler to develop the core logic,
  but complicate the metric extraction.
  - Pros: Responsibility for data storage in on our side,
    so we can form the data as we need.
    We can extract metrics directly from the persisted business data.
    Data is closer to the app, no need to derive the state from the Slack,
    hence the app should work faster.
  - Cons: The need to deploy and manage DB.
    The need to ensure that Slack and DB are in sync in case of failures.
    No need to do it right from the start, since the load is not expected
    to be too high (50-100) tickets per week +
    in general it should be straightforward to fix the failure within the Slack UI.
    If Slack will prove itself as not stable enough,
    we could improve reliability later.

## Decision

Use Postgres DB together with the bot.
