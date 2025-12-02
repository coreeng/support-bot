# Support Bot Metrics & Monitoring Guide

This document describes the metrics exposed by the Support Bot for monitoring, alerting, and operational observability.

## Overview

The Support Bot exposes metrics in **Prometheus format** via Spring Boot Actuator. These metrics enable you to:

- Monitor the health of Slack WebSocket connections
- Track notification processing throughput and latency
- Detect errors and failures in Slack API calls
- Set up alerts for degraded performance or outages

## Metrics Endpoint

Metrics are exposed on a separate management port for security:

| Endpoint           | URL                             |
|--------------------|---------------------------------|
| Prometheus metrics | `http://<host>:8081/prometheus` |
| Health check       | `http://<host>:8081/health`     |

> **Note**: The management port (default: 8081) is separate from the application port (default: 8080).
> Ensure your Prometheus scrape configuration targets the correct port.

## Metrics Categories

### 1. Spring Boot Default Metrics

The application automatically exposes standard Spring Boot Actuator metrics:

| Metric Prefix            | Description                             |
|--------------------------|-----------------------------------------|
| `jvm_*`                  | JVM memory, garbage collection, threads |
| `process_*`              | Process CPU, uptime, file descriptors   |
| `http_server_requests_*` | HTTP request latency and counts         |
| `logback_events_total`   | Log events by level                     |
| `hikaricp_*`             | Database connection pool                |
| `cache_*`                | Caffeine caches                         |

These are useful for general application health monitoring.

---

### 2. Slack WebSocket Connection Metrics

These metrics track the health of the WebSocket connection to Slack (Socket Mode).

| Metric                           | Type    | Description                    |
|----------------------------------|---------|--------------------------------|
| `slack_socket_disconnects_total` | Counter | Total WebSocket disconnections |
| `slack_socket_errors_total`      | Counter | Total WebSocket errors         |

**What to Monitor:**

- **Frequent disconnects**: A high rate of disconnects may indicate network instability or Slack service issues
- **Connection errors**: Spikes in errors warrant investigation

---

### 3. Slack Notification Processing Metrics

These metrics track how the bot processes incoming Slack notifications.

| Metric                                 | Type    | Description                          |
|----------------------------------------|---------|--------------------------------------|
| `slack_notifications_received_total`   | Counter | Notifications received from Slack    |
| `slack_notifications_processed_total`  | Counter | Notifications successfully processed |
| `slack_notifications_errors_total`     | Counter | Notification processing failures     |
| `slack_notifications_duration_seconds` | Timer   | Processing duration (histogram)      |

**Labels:**

| Label        | Values                                             | Description                     |
|--------------|----------------------------------------------------|---------------------------------|
| `type`       | `event`, `action`, `suggestion`, `view_submission` | Notification type               |
| `handler`    | Slack callback ID (see below)                      | Identifies the specific handler |
| `error_type` | Exception class name                               | (errors only) Type of exception |

**Handler Label Values by Type:**

| Type              | Source                    | Example Values                                                     |
|-------------------|---------------------------|--------------------------------------------------------------------|
| `event`           | Event type from payload   | `message`, `reaction_added`, `app_mention`                         |
| `action`          | Action ID(s) from payload | `ticket-create`, `escalation-resolve`, `rating-submit-[1,2,3,4,5]` |
| `suggestion`      | Action ID from payload    | `ticket-change-team`                                               |
| `view_submission` | View callback ID          | `ticket-summary`, `ticket-confirm`, `homepage-filter`              |

> **Note**: For block actions, if multiple actions are received in a single payload, their IDs are joined with `|`.
> As far as we know Slack doesn't send multiple actions in a single payload, but we will find it out from the metric in case it does. 

---

### 4. Slack API Call Metrics

These metrics track outgoing API calls to Slack (sending messages, reactions, etc.).

| Metric                             | Type    | Description                |
|------------------------------------|---------|----------------------------|
| `slack_api_calls_success_total`    | Counter | Successful Slack API calls |
| `slack_api_calls_errors_total`     | Counter | Failed Slack API calls     |
| `slack_api_calls_duration_seconds` | Timer   | API call latency           |

**Labels:**

| Label        | Values                        | Description                  |
|--------------|-------------------------------|------------------------------|
| `method`     | Slack API method name         | Which API was called         |
| `error_code` | Slack error code or exception | (errors only) Failure reason |

**Common API Methods:**

| Method               | Description                      |
|----------------------|----------------------------------|
| `reactions.add`      | Add emoji reaction to message    |
| `reactions.remove`   | Remove emoji reaction            |
| `chat.postMessage`   | Send message to channel/thread   |
| `chat.postEphemeral` | Send ephemeral (private) message |
| `chat.update`        | Edit existing message            |
| `views.open`         | Open modal dialog                |
| `views.publish`      | Update App Home tab              |

**Common Error Codes:**

| Error Code          | Description             |
|---------------------|-------------------------|
| `already_reacted`   | Reaction already exists |
| `no_reaction`       | No reaction to remove   |
| `channel_not_found` | Invalid channel ID      |
| `rate_limited`      | Slack rate limit hit    |
| `invalid_auth`      | Token expired/invalid   |

---

### 5. Cache Metrics

The bot caches the results of some API calls to reduce a load on Slack.

| Metric                  | Type    | Description          |
|-------------------------|---------|----------------------|
| `cache_gets_total`      | Counter | Cache get operations |
| `cache_puts_total`      | Counter | Cache put operations |
| `cache_evictions_total` | Counter | Cache evictions      |
| `cache_size`            | Gauge   | Current cache size   |

**Labels:**

- `cache`: Cache name
- `result`: `hit` or `miss` (for `cache_gets_total`)