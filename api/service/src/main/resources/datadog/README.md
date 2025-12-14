# Support Bot - Datadog Metrics

PostgreSQL integration config for emitting support bot metrics to Datadog.

## Prerequisites

Install the Datadog Agent: https://docs.datadoghq.com/agent/

## Setup

1. Set password environment variable:
```bash
export DD_PG_PASSWORD=your_password
```

2. Run Flyway migration to create datadog user (from `api/` directory):
```bash
./gradlew :service:flywayMigrate -Dflyway.placeholders.DATADOG_PASSWORD=$DD_PG_PASSWORD
```

3. Copy config:
```bash
cp src/main/resources/datadog/postgres-conf.yaml /opt/datadog-agent/etc/conf.d/postgres.d/conf.yaml
```

4. Start agent:
```bash
/opt/datadog-agent/bin/agent/agent run > /tmp/datadog-agent.log 2>&1 &

# Stop: pkill -f "datadog-agent"
# Status: datadog-agent status 2>&1 | grep -A 10 "postgres"
```

## Metrics

See `postgres-conf.yaml` for full list. Key metrics:

- `supportbot.tickets{status,impact,escalated,rated}` - ticket counts
- `supportbot.escalations{status,team,impact}` - escalation counts
- `supportbot.response.p50_7d`, `p90_7d` - response time SLAs
- `supportbot.resolution.p50_7d`, `p90_7d` - resolution time SLAs
