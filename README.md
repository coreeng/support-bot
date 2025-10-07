# Support-Bot

![Build Status](https://img.shields.io/github/actions/workflow/status/coreeng/support-bot/support-bot-fast-feedback.yaml?branch=main)
![License](https://img.shields.io/github/license/coreeng/support-bot)
![Version](https://img.shields.io/github/v/tag/coreeng/support-bot)

___
## 🚀 Introduction 
The Support-Bot is a Slack integration designed to **simplify** and **automate** support workflows directly
within your workspace.

You can:

🧾 Create tickets from Slack messages in dedicated channels.

👀 View and track ticket statuses.

⚙️ Update and manage ticket states (open, closed).

🚨 Mark tickets as escalated when requiring specialist attention.

⭐ Collect instant feedback on support threads — upon ticket closure, the thread creator can rate their support experience directly in Slack.

📊 Store support metrics in a database for dashboards and analytics.

📋 Browse and filter tickets from the in-Slack Bot Homepage — view a dashboard of tickets and quickly filter by status, impact, or assigned team, all without leaving Slack.

___

## 📦 Installation

## Running locally

To run the Support-Bot locally, please refer to [the service README](https://github.com/coreeng/support-bot/blob/main/api/service/README.md) for instructions.

## Running within a Kubernetes Cluster

Follow these steps to deploy the Support-Bot in a Kubernetes environment:

### 1️⃣ Dockerfile

Create a `Dockerfile` that uses our base image. You can reference the image below:

```
ghcr.io/coreeng/support-bot:<latestVersionFromRegistry>
```
 
* Replace `<latestVersionFromRegistry>` with the latest version
* For a full list of available versions, see the [package registry](https://github.com/coreeng/support-bot/pkgs/container/support-bot)

```Dockerfile
# Dockerfile example
FROM ghcr.io/coreeng/support-bot:0.0.48
# Optional: add custom config/commands
```
### 2️⃣ Database

A `Postgres` database is required for the application to function. Ensure your cluster has a running
Postgres instance and that the Support-Bot can connect to it. The Support-Bot will run database migrations on startup.

### 3️⃣ Application Configuration

We recommend mounting your custom configuration as a Kubernetes `ConfigMap`.

* The default config can be found [here](https://github.com/coreeng/support-bot/blob/main/api/service/src/main/resources/application.yaml)
* An example of custom config definition can be found [here](https://github.com/coreeng/support-bot/blob/main/api/k8s/service/values.yaml#L118)
* An example of how the config is mounted can be found [here](https://github.com/coreeng/support-bot/blob/main/api/k8s/service/templates/deployment.yaml#L58)

For example, to override the enums configuration, replace the enums block in your custom config.

For a more detailed explanation of all configuration options see the [Configuration Documentation](https://github.com/coreeng/support-bot/blob/main/api/service/docs/configuration.md)

### 4️⃣ Environment Variables

For the application to function correctly, you will need to set the following environment variables:

| Variable | Description |
|----------|-------------|
| `SLACK_TOKEN` | Slack bot token |
| `SLACK_SOCKET_TOKEN` | Websocket token for Slack events |
| `SLACK_SIGNING_SECRET` | Slack signing secret |
| `SLACK_TICKET_CHANNEL_ID` | Slack channel ID where queries arrive |

### 5️⃣ Identity Provider

The Support-Bot integrates with an **Identity Provider** to fetch member details.  
An Identity Provider is a prerequisite for the bot to function.  
More details under `platform-integration` in the [configuration docs](https://github.com/coreeng/support-bot/blob/main/api/service/docs/configuration.md).

## 6️⃣ Feedback & Support

If you have questions, need assistance, or want to provide feedback, feel free to reach out at `info@cecg.io`

--- 

## 📄 License

This project is licensed under the **Apache 2.0 License**. See [LICENSE](LICENCE) for details.

---

Developer & Maintained by [CECG](https://cecg.io/about-us)