# Support bot service

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

> *Note*: you can specify spring profile when running the service. Example:
> ```
> SPRING_PROFILES_ACTIVE=functionaltests
> ```

# CodeStyle and Linting

```bash
make lint
```

Ruleset can be found [here](pmd-ruleset.xml)

# Support Bot API

## Support Analysis API

## Summary Data API

### Process

1. Export thread contents
2. Analyze thread contents with Knowledge Gap Analysis scripts that produce a TSV file with a record per thread
3. Import analysis results TSV file into the bot database

If you ran the analysis before you will have some content in the directory.
The export may will overwrite some threads and create new ones.
The analysis will only process new threads that have not been analysed before.
If you want to process all threads again, remove the output directory created by the analysis

When importing the analysis results, the bot will merge new analysis records with existing ones, overwriting existing records.

### Export Thread Data

```bash
mkdir content || truue
curl http://localhost:8080/summary-data/export?days=10 | bsdtar -xf - -C content
```

This will create a file for each thread in the `content` directory.
The file name is the thread timestamp.
This is the format expected by Knowledge Gap analysis scripts.

If the directory exists, existing files will be overwritten.
This will allow you to provide fresh analysis based on the latest thread content

### Import Analysis Data

```bash
curl -F "file=@../analysis-data/analysis.tsv" http://localhost:8080/summary-data/import
```

This will merge analysis records with the records in the database by ticket ID

### Read analysis data as UI JSON

```bash
curl http://localhost:8080/analysis
```

