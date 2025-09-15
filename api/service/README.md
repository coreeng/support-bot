# Support bot

Slack Support Bot that handles tickets and escalations.
Exposes metrics about processed tickets and escalations.

# Running bot locally

## 1. Slack bot credentials
You have to have Slack credentials for the bot to authorize itself.
To achieve this, it is recommended that you create your own Slack application instance and get unique credentials.

Once you have it, create `.envrc` using `.envrc.example`.

You'll also need a Slack channel for that the bot to operate in.
You can create one for yourself in your slack workspace, and specify it in `.envrc`.

## 2. Codegen

You need to run the below make task, which generates the necessary code based on our database schema

```bash
make codegen
```

## 3. Run DB.
This line of code will start a Postgres instance,
expose port 5432 and mount data to `./db-data` folder, so it will persist between runs.
```bash
make db-run
```

## 4. Connect to cluster
Configure connection to cloud and kubernetes cluster.

For [Core-Platform](https://coreplatform.io/):
```bash
corectl env connect gcp-dev -b -f
```

For GCP:
```bash
gcloud auth application-default login
```

## 5. Start the bot
```bash
make run
```

# CodeStyle and Linting

```bash
make lint
```

Ruleset can be found [here](pmd-ruleset.xml)
