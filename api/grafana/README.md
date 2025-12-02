# Support Bot Grafana Dashboard

A Grafana dashboard for monitoring the Support Bot service.

## Overview

This dashboard provides real-time visibility into all aspects of the Support Bot service, including:

- **Application Health**: Error rates, WebSocket connectivity, resource saturation
- **JVM Performance**: Memory usage, garbage collection, thread states
- **Slack Integration**: API call latencies, notification processing, WebSocket health
- **Database**: HikariCP connection pool metrics
- **HTTP Server**: Jetty thread pool and connection metrics
- **Caching**: Caffeine cache hit rates and evictions

## Quick Start

### Prerequisites

- Grafana 10.x or later
- Prometheus data source configured and scraping metrics

### Import Dashboard

1. In Grafana, navigate to **Dashboards** → **Import**
2. Copy-Paste `grafana-dashboard.json` to the input box.
3. Click **Import**
4. Update variables to match your desired datasource and kubernetes namespace.
