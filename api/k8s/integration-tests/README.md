# Support Bot Integration Tests Helm Chart

This Helm chart deploys a Kubernetes Job that runs integration tests for the Support Bot application.

## Overview

The integration tests chart creates a Kubernetes Job that:
- Runs integration tests against a deployed Support Bot service
- Uses a dedicated service account with appropriate RBAC permissions
- Configures test parameters through a ConfigMap
