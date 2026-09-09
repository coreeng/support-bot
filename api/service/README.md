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

## 6. Stub LLM provider (optional)

The analysis run and the Support Summary page need an LLM. To exercise them on a laptop with no
credentials and no spend, select the **stub** provider: it returns canned, deterministic text with
no network call. Selecting it turns both features on. It is a two-step opt-in:

```yaml
llm:
  provider: stub
  stub:
    acknowledge-synthetic-data: true
```

Set these in a local override only (a local Spring profile, or the environment variables
`LLM_PROVIDER=stub` and `LLM_STUB_ACKNOWLEDGE_SYNTHETIC_DATA=true`). The acknowledgement is
deliberately absent from `application.yaml` and from the Helm chart.

What to know before switching it on:

- `llm.provider=stub` on its own fails startup: `acknowledge-synthetic-data=true` must be set as
  well, and the failure message spells out why.
- Its output is **synthetic data**. Classifications land in `analysis` and summaries in
  `summary_snapshot` exactly like real ones, and nothing in the schema marks them as fake (only
  `summary_snapshot.model`, which records `stub` in this mode). **Never point a stub-enabled
  instance at a shared database** — use a throwaway local one.
- The service logs a startup `WARN` whenever the stub is active.
- `LLM_MODEL_NAME` is ignored; summaries are stamped with the model name `stub`.

The rest of the LLM configuration (Vertex and proxy providers, model, delays) is described in
[configuration.md](docs/configuration.md#analysis-knowledge-gap-llm).

# CodeStyle and Linting

```bash
make lint
```

Ruleset can be found [here](pmd-ruleset.xml)

# PR lifecycle FSM

The PR-tracking state machine is defined declaratively in
[`PrLifecycle.TRANSITIONS`](src/main/java/com/coreeng/supportbot/prtracking/PrLifecycle.java)
— one ordered table that is the single source of truth for the runtime (`PrLifecycle.decide`). It can
also be rendered to a lifecycle diagram
([`docs/diagrams/pr-lifecycle.generated.md`](docs/diagrams/pr-lifecycle.generated.md)).

The diagram is an **optional, nice-to-have artefact**: it is generated (never hand-edited), but
**nothing enforces that it stays in sync** with the table. If you change the FSM and want the diagram
to match, regenerate it and commit the result:

```bash
make regen-fsm-diagram      # from api/, optional
```

If you skip it the diagram just goes stale — the build is unaffected.

# Support Bot API

## Support Analysis API

## Summary Data API

### Process

1. Export thread contents
2. Analyze thread contents with Knowledge Gap Analysis scripts that produce a JSONL file with a record per thread
3. Import analysis results JSONL file into the bot database

If you ran the analysis before you will have some content in the directory.
The export may overwrite some threads and create new ones.
The analysis will only process new threads that have not been analysed before.
If you want to process all threads again, remove the output directory created by the analysis

When importing the analysis results, the bot will merge new analysis records with existing ones, overwriting existing records.

### Export Thread Data

```bash
mkdir content || true
curl -s http://localhost:8080/summary-data/export?days=10 | bsdtar -xf - -C content
```

This will create a file for each thread in the `content` directory.
The file name is the thread timestamp.
This is the format expected by Knowledge Gap analysis scripts.

If the directory exists, existing files will be overwritten.
This will allow you to provide fresh analysis based on the latest thread content

### Import Analysis Data

```bash
curl -s -F "file=@../analysis-data/analysis.jsonl" http://localhost:8080/summary-data/import
```

This will merge analysis records with the records in the database by ticket ID

### Read analysis data as UI JSON

```bash
curl http://localhost:8080/summary-data/results
```

