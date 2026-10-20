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

## Elevate Erasure Requests

Elevate's erasure orchestrator submits and polls Erasure Requests on an internal port that is off by default ([ADR-009](../docs/adr/adr-009-personal-data-erasure-requests.md), [ADR-010](../docs/adr/adr-010-service-caller-on-an-internal-port.md)).

| Variable | Description | Default |
|----------|-------------|---------|
| `INTERNAL_PORT_ENABLED` | Serve the erasure endpoints on the internal port | `false` |
| `INTERNAL_PORT` | Internal port | `8082` |

- `POST /api/erasure-requests` with `{requestId, currentUserEmail, newUserEmail}` stores the request and answers `202` with `{requestId, status, requestedAt}`. Resubmitting an id returns the first request unchanged.
- `GET /api/erasure-requests/{requestId}` reports the request, or `404` for an unknown id.

Every request on the internal port is trusted as Elevate's service caller and may reach only these endpoints; the public port refuses them to everyone. The `X-Elevate-Tenant-Id` header the orchestrator sends is ignored. Nothing processes a request yet, so every request stays `IN_PROGRESS`. In Kubernetes, enable it through the chart's `internalPort` values and add a NetworkPolicy that admits the port only from the orchestrator's pods.
