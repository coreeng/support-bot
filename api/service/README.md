# Support bot

Slack Support Bot that handles tickets and escalations.
Exposes metrics about processed tickets and escalations.

# Running bot locally

## 1. Slack bot credentials
You have to have Slack credentials for the bot to authorize itself.
To achieve this, it is recommended that you create your own Slack application instance and get unique credentials. Documentation
can be found [here](https://docs.slack.dev/quickstart/). Click on `Create an app`, and then create a new app `From a Manifest`.
You can use [this manifest](docs/configuration.md#slack), change the `name`, `description` and `background_color` as desired.

After the Slack Application is set up, you can install it in your organisation Slack workspace.

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

## 4. Identity Provider Integrations
Identity Provider integrations are disabled by default for local runs. What you can do instead, is look at the [app config](src/main/resources/application.yaml)
and ensure the `platform-integration.static-user` is set to true, while `platform-integration.gcp.enabled` and `platform-integration.azure.enabled` are set to `false`. 
You can set your desired `platform-integration.static-user.users` entries.

## 5. Start the bot
```bash
make run
```

# CodeStyle and Linting

```bash
make lint
```

Ruleset can be found [here](pmd-ruleset.xml)
