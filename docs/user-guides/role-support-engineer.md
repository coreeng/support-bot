# User guide: ROLE_SUPPORT_ENGINEER

Support engineers are the primary operators of the support bot. This role is assigned to members of the support Slack group (`team.support.group-ref` in config) and grants full ticket management capabilities plus access to all analytics.

This guide assumes you are also familiar with the [base user guide](./role-user.md).

## Working from Slack

Most of the ticket lifecycle happens directly in the support channel without needing to open the UI.

### Creating a ticket

When a tenant posts a question in the support channel, add the 👀 (`eyes`) reaction to their message. The bot will:

1. Create a ticket and record you as the assignee (if assignment is enabled — first reactor wins).
2. Add a 🎫 (`ticket`) reaction to confirm the ticket was created.
3. Post a form in the thread for you to fill in the ticket details (tags, impact, author's team).

Only support team members can trigger ticket creation — reactions from other users are silently ignored.

A few things to know:
- React to the **top-level message**, not a reply in the thread. Reactions on thread replies are ignored.
- Re-adding the reaction after removing it is safe — the handler is idempotent.
- If assignment is disabled on your deployment, the ticket is still created but not auto-assigned.

### Closing a ticket

When the issue is resolved, the bot posts a ✅ (`white_check_mark`) reaction on the original message automatically when the ticket is closed (via the UI or the bot's own close flow).

### Escalating a ticket

When a ticket is created, the bot posts a message in the thread with two buttons:

- **Full Summary** — opens a modal with the ticket summary
- **Escalate** — opens a modal to select the escalation team (only shown when the ticket is not yet closed)

Click **Escalate**, select the target team, and confirm. The bot will tag that team's Slack group in the thread, log the escalation against the ticket, and add a ⚠️ (`warning`) reaction to the original message. The escalation team must be in the support channel to see the tag.

### Stale tickets

If an open ticket has had no activity for the configured period (default: 3 days), the bot marks it stale and sends a reminder in the thread. It will repeat that reminder daily until action is taken. You can clear stale status by updating the ticket in the UI.

---

## Managing tickets

The tickets table is on the home page (`/tickets` still redirects there). Click any ticket row to open the edit modal. As a support engineer you can change:

| Field | Options |
|---|---|
| **Status** | `Opened`, `Closed`, `Stale` |
| **Support engineer** | Any member of the support team (if assignment is enabled) |
| **Author's team** | The tenant team who raised the issue — the bot suggests teams based on the thread context |
| **Tags** | One or more tags from the configured tag registry (at least one required) |
| **Impact** | One of the configured impact levels (required) |

All four required fields (status, author's team, tags, impact) must be set before you can save. If you try to close a ticket that has unresolved escalations, the modal warns you — closing the ticket also closes all its escalations.

The "Open in Slack" button in the modal footer takes you directly to the original thread if you need more context before editing.

## Metrics dashboard

You have access to all analytics pages:

- **Stats** — the main dashboard with first-response and resolution SLA charts, weekly ticket counts, and escalation breakdowns by team, tag, and impact
- **SLA** — SLA percentile and distribution views
- **Health** — service health indicators

These pages are also accessible to leadership.

## Support Summary

The **Support Summary** page (`/summary`) replaced the Knowledge Gaps page — old `/knowledge-gaps` links redirect to it. It shows what tenants raised in a date window, and why. It appears in the sidebar only when the feature is enabled on your deployment, and only for support engineers and leadership.

Pick a window with the dropdown at the top right: **Last Week**, **Last 2 Weeks** (the default), **Last Month**, or **Custom** with a from/to date. Windows are whole days; the presets end yesterday, and a custom range may span up to 366 days (the end date must not be before the start). The page then shows:

- A strip with the window and the number of **tickets raised** in it.
- **At a glance** — the LLM-written narrative for the window, followed by chips for the total raised, the top driver (with its share), top subject, top feature and top tenant team, and an **Awaiting classification** count if any tickets are not yet classified.
- Breakdown cards: **Top Support Areas** (by driver, with a stacked share bar), **Top categories**, **Top knowledge gaps** (categories of tickets whose driver is "Knowledge Gap"), **Top products** (only when product tags are configured), **Top Platform Features** and **Top Teams** (each team shows its top product). Every row has a count and share; click a row to expand its five most recent tickets, and click a ticket to open the usual edit modal.

The narrative has three states: **ready** (the text, with the model and generation time underneath), **generating** (a progress bar while threads are analysed, then "Writing the summary..."), or **unavailable** (an error message; the breakdowns still show).

There is no **Run Analysis** button any more. Opening the page classifies any closed-but-unclassified tickets in the window automatically, then writes the summary; the page keeps polling until it is ready. Results are cached per window and regenerated only when the window's tickets change. If a window's **Awaiting classification** count never clears, see the [Support Summary runbook](../runbooks/support-summary.md#re-running-classification-by-hand) — a support engineer can re-run the classification through the API.

**View Prompts** (top right) opens a dialog with a dropdown showing the two prompts the page uses: **Ticket classification** (how each thread is sorted into driver, category and feature) and **Summary generation** (how the narrative is written). Both are read-only.

## Escalations

You can view the full escalations list and its filters. To escalate a ticket, use the **Escalate** button on the bot's ticket message in the Slack thread — see [Escalating a ticket](#escalating-a-ticket) above.

## Tips for new support engineers

- Tag tickets accurately — tags feed the escalation trend charts and the products breakdown on the Support Summary, so they're more useful than they appear.
- Set "Author's team" before closing a ticket. The bot suggests likely teams based on the thread, but verify it — this field drives the per-team metrics.
- If a ticket has been sitting as "opened" for a while with no reply, mark it "stale" rather than leaving it open — it keeps the metrics honest.
- Check the Stats page weekly to spot patterns (recurring tags, teams with high escalation rates) that might warrant proactive docs or a retro.
