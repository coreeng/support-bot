# Support bot

Slack Support Bot that handles tickets and escalations.
Exposes metrics about processed tickets and escalations.

# Structure
```
.
├── Dockerfile # Service dockerfile
├── functional/ # Functional tests for the service
├── integration-tests/ # Integration tests for the service
├── k8s/ # Helm chart for deploying tests and the service
│   ├── functional-tests/
│   ├── integration-tests/
│   └── service/
├── scripts/ # Deployment scripts
└── service/ # The support bot service
```

## Authentication

### Allow-List

Access to the Support UI can be restricted to specific email addresses and/or email domains.

**Environment Variables** (for local development):

| Variable | Description | Example |
|----------|-------------|---------|
| `ALLOWED_EMAILS` | Comma-separated list of allowed email addresses | `alice@example.com,bob@corp.io` |
| `ALLOWED_DOMAINS` | Comma-separated list of allowed email domains | `example.com,corp.io` |

**Behavior:**
- When **both lists are empty or unset**, all SSO-authenticated users are allowed (default, current behavior).
- When **either list is configured**, only users whose email matches an entry in `ALLOWED_EMAILS` or whose email domain matches an entry in `ALLOWED_DOMAINS` can log in.
- Users not in the allow-list see an "Access Restricted" page after SSO authentication.
- Email and domain matching is case-insensitive.

For Kubernetes deployments, see the Helm chart's `auth.allowedEmails` and `auth.allowedDomains` values.