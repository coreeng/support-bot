# support-bot Grafana dashboard chart

This directory contains the Helm chart that provisions the support-bot service Grafana dashboard via the `GrafanaDashboard` custom resource.

## Requirements

- The Grafana Operator (or a compatible controller) and the `GrafanaDashboard` CRD must already be installed in the target cluster.
- The monitoring stack (Prometheus + Grafana) should be deployed separately.

