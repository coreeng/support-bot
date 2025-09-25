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