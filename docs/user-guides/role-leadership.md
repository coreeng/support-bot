# User guide: ROLE_LEADERSHIP

Leadership role is for support leads who need visibility into metrics and trends but are not necessarily day-to-day ticket handlers. It is assigned to members of the leadership Slack group (`team.leadership.group-ref` in config).

This guide assumes you are also familiar with the [base user guide](./role-user.md).

## What leadership adds over the base role

`ROLE_LEADERSHIP` unlocks the metrics dashboards and the Support Summary page. Everything else (viewing tickets on the home page, viewing escalations) is the same as any authenticated user.

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

## Support Summary

The **Support Summary** page (`/summary`) replaced the Knowledge Gaps page and is the best starting point for a periodic review. It is shown in the sidebar when the feature is enabled on your deployment. Pick a window with the dropdown at the top right. **Last Week**, **Last 2 Weeks** and **Last Month** are the whole business period before the current one: Monday to Friday of last week, the two weeks before this one, or the previous calendar month. **Custom** takes a from/to range of up to 92 days — about a quarter. The page then shows:

- How many tickets were raised in the window.
- **At a glance** — a short LLM-written narrative of what tenants asked about and why, plus the top driver, subject, feature and tenant team.
- Breakdowns of **Top Support Areas** (drivers), **Top categories**, **Top knowledge gaps**, **Top products** (when product tags are configured), **Top Platform Features** and **Top Teams** (with each team's top product). Expand a row to see its five most recent tickets; click one to open the read-only ticket detail.

Opening the page classifies any closed-but-unclassified tickets in the window automatically — there is nothing to run. While that happens the narrative shows a progress bar; the page keeps polling until it is ready. Results are cached, so a window whose tickets have not changed loads instantly. If the narrative shows "unavailable", the breakdowns are still correct and the page retries later.

**View Prompts** shows the two prompts behind the page (ticket classification and summary generation), read-only.

### Focusing on one team group

If your deployment has team groups set up, a selector next to the window dropdown narrows the whole page — narrative, chips and every breakdown — to one group of tenant teams. A group is called a **Team group** by default, but your deployment may use another name, such as Domain. The strip above the cards shows which group is on screen (hover it to see its teams), and **Top Teams** ranks the teams within it. Clear the selector to go back to **All teams**.

The group is part of the page's address, so you can share or bookmark a link that opens straight on, say, last month for one group. Its first visit may take a moment while the narrative is written for that group; after that it loads instantly like any other window.

A group's totals are at most the whole window's: tickets with no team, or not raised by a tenant, belong to no group.

## What leadership cannot do

- **Edit tickets** — requires `ROLE_SUPPORT_ENGINEER`
- **Re-run ticket classification by hand** — the Support Summary classifies tickets on its own when you open it; if a window stays stuck on **Awaiting classification**, ask a support engineer, who can trigger it through the API.
- **Resolve escalations in the UI** — requires `ROLE_SUPPORT_ENGINEER`; closing a ticket closes its escalations. `ROLE_ESCALATION` only grants visibility into escalations assigned to your team, not the ability to resolve them.

## Common workflows

### Weekly metrics review

Open the Stats page and check:
1. Weekly ticket count — is volume trending up or down?
2. First-response SLA distribution — are most tickets getting a response within the target window?
3. Top escalated tags — are there recurring topics that could be addressed with runbooks or docs?
4. Escalations by team — are any tenant teams disproportionately escalating?

### Escalation trend investigation

The escalation charts on Stats let you filter by date range and drill into which tags and teams are driving escalations. If a tag is spiking, open the Support Summary for the same window — the **Top knowledge gaps** and **Top categories** cards, and the narrative, show what tenants were actually asking about.

### Sharing data

All dashboard pages render charts that can be screenshot for async reporting. The underlying data is not currently exportable from the UI directly — ask a support engineer if you need raw data (they can export it through the API).
