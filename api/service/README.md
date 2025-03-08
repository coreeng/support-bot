# Support bot

Slack Support Bot that handles tickets and escalations.
Exposes metrics about processed tickets and escalations.

# Running bot locally

## 1. Slack bot credentials
You have to have Slack credentials for the bot to authorize itself.
To achieve this, either create your own instance of the bot or ask for credentials a colleague

Once you have it, create `.envrc` using `.envrc.example`.

You'll also need a channel for that the bot will work with.
You can either create a private one for yourself or ask a colleague to add you to an existing one.
Use specify it in `.envrc`.

## 2. Run DB.
This line of code will start a Postgres instance,
expose port 5432 and mount data to `./db-data` folder, so it will persist between runs.
```bash
make db-run
```

## 3. Connect to cluster
Configure connection to cloud and kubernetes cluster.

In Core Platform case:
```bash
corectl env connect gcp-dev -b -f
```

In GCP case:
```bash
gcloud auth application-default login
```

## 4. Start the bot
```bash
make run
```