# Support Bot - Datadog Metrics

PostgreSQL integration config for emitting support bot metrics to Datadog.

## Prerequisites

Install the Datadog Agent: https://docs.datadoghq.com/agent/

## Setup

1. Set password and run app to create datadog user (from `api/service/` directory):
```bash
export DATADOG_PASSWORD=your_password
make run
```

2. Copy config and set agent password:
```bash
cp src/main/resources/datadog/postgres-conf.yaml /opt/datadog-agent/etc/conf.d/postgres.d/conf.yaml
export DD_PG_PASSWORD=your_password  # same password as above
```

3. Start agent:
```bash
/opt/datadog-agent/bin/agent/agent run > /tmp/datadog-agent.log 2>&1 &

# Stop: pkill -f "datadog-agent"
# Status: datadog-agent status 2>&1 | grep -A 10 "postgres"
```

## Metrics

Metrics are collected every 60 seconds. See `postgres-conf.yaml` for full list. Key metrics:

- `supportbot.tickets{status,impact,escalated,rated}` - ticket counts
- `supportbot.escalations{status,team,impact}` - escalation counts
- `supportbot.response.p50_7d`, `p90_7d` - response time SLAs
- `supportbot.resolution.p50_7d`, `p90_7d` - resolution time SLAs
