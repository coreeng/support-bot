# User guides

Role-based guides for new team members. Start with the base user guide, then read the guide for your role.

| Guide | Role | Who it's for |
|---|---|---|
| [Base user](./role-user.md) | `ROLE_USER` | Everyone — assigned automatically on login |
| [Support engineer](./role-support-engineer.md) | `ROLE_SUPPORT_ENGINEER` | Support team members who manage and close tickets |
| [Leadership](./role-leadership.md) | `ROLE_LEADERSHIP` | Support leads who review metrics and trends |
| [Escalation team](./role-escalation.md) | `ROLE_ESCALATION` | Teams that receive escalations from the support team |

Roles are additive — you can hold more than one. For example, a support lead who is also on the support team would hold both `ROLE_SUPPORT_ENGINEER` and `ROLE_LEADERSHIP`.

For how roles are configured and assigned, see the [configuration docs](../../api/service/docs/configuration.md#roles).
