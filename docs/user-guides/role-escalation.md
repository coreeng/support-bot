# User guide: ROLE_ESCALATION

`ROLE_ESCALATION` is for teams that *receive* escalations from the support team — typically product or platform teams outside the core support team. You don't need this role to respond to escalations in Slack; the bot tags your Slack group directly in the thread. This role is for people who also log into the UI and want to track and resolve escalations there.

This guide assumes you are also familiar with the [base user guide](./role-user.md).

## How escalations reach you

When a support engineer escalates a ticket to your team:

1. The bot posts a message in the Slack support thread tagging your team's Slack group.
2. If you're in the support channel, you'll see the tag and can respond in the thread directly — no UI login required.
3. If you log into the UI, the Escalations page shows a dedicated widget for your team with a breakdown of what's been sent your way.

## The "Escalated to My Team" widget

When you're logged in and have selected your escalation team from the team filter, the Escalations page shows a dedicated section at the top:

- **Total Received / Active / Resolved** — summary tiles showing overall counts
- **Escalations by Status** — pie chart of active vs resolved
- **Escalations by Impact** — pie chart showing the severity breakdown of what's been sent to your team

Below the widget is the standard escalations table, filtered to your team's escalations, where you can see each escalation's ticket, when it was raised, and how long it has been open.

## Resolving an escalation

Escalations can be resolved in the UI by updating the parent ticket's status to "closed" — but that action requires `ROLE_SUPPORT_ENGINEER`. If your team has handled the escalated issue and wants to mark it resolved, the workflow is:

1. Reply in the Slack thread confirming the resolution (this is the primary channel for communication with the support team).
2. Ask a support engineer to close the escalation in the UI, or resolve it yourself if you also hold `ROLE_SUPPORT_ENGINEER`.

> This is a known limitation: `ROLE_ESCALATION` does not currently grant the ability to mark individual escalations resolved without also holding the support engineer role. See the ADR at `docs/adr/adr-003-rework-view-permissions.md` for background.

## Getting access

`ROLE_ESCALATION` membership is resolved differently from other roles — it goes through platform identity sources (Azure, GCP, or Kubernetes) rather than a direct Slack group lookup. Contact whoever manages your deployment to confirm which identity source is used and how to add members.

See the [configuration docs](../../api/service/docs/configuration.md#role_escalation) for details on how membership resolution works and its current limitations.
