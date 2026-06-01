# User guide: ROLE_LEADERSHIP

Leadership role is for support leads who need visibility into metrics and trends but are not necessarily day-to-day ticket handlers. It is assigned to members of the leadership Slack group (`team.leadership.group-ref` in config).

This guide assumes you are also familiar with the [base user guide](./role-user.md).

## What leadership adds over the base role

`ROLE_LEADERSHIP` unlocks the metrics dashboards. Everything else (viewing tickets, viewing escalations, browsing the knowledge gaps summary) is the same as any authenticated user.

## Metrics dashboard

### Stats page

The main dashboard. Useful for weekly reviews and spotting trends:

- **First response SLA** — distribution and percentile charts showing how quickly tickets received a first reply
- **Resolution SLA** — how long tickets took to close, broken down by week and by tag
- **Weekly ticket counts** — volume over time, good for spotting demand spikes
- **Escalation breakdowns** — escalations by team, tag, and impact; trends over time; percentage escalated by tag
- **Top escalated tags this week** — quick view of which areas are generating the most escalation pressure

### SLA page

Percentile and bucket views for first-response and resolution times. Useful for reviewing SLA health against defined targets.

### Health page

Service health indicators.

## What leadership cannot do

- **Edit tickets** — requires `ROLE_SUPPORT_ENGINEER`
- **Run, export, or import analysis** — requires `ROLE_SUPPORT_ENGINEER`. The Knowledge Gaps page is read-only for leadership.
- **Resolve escalations from your team** — requires `ROLE_ESCALATION` (separate role, not implied by leadership)

## Common workflows

### Weekly metrics review

Open the Stats page and check:
1. Weekly ticket count — is volume trending up or down?
2. First-response SLA distribution — are most tickets getting a response within the target window?
3. Top escalated tags — are there recurring topics that could be addressed with runbooks or docs?
4. Escalations by team — are any tenant teams disproportionately escalating?

### Escalation trend investigation

The escalation charts on Stats let you filter by date range and drill into which tags and teams are driving escalations. If a tag is spiking, raise it with the support engineer running the next analysis cycle — they can pull the knowledge gaps for that tag to see what's missing.

### Sharing data

All dashboard pages render charts that can be screenshot for async reporting. The underlying data is not currently exportable from the UI directly — ask a support engineer to run an analysis export if you need raw data.
