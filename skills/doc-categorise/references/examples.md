---
name: Diátaxis examples (using a fictional product, Foglight)
description: Four exemplar pages, one drift-rewrite, and one multi-intent split. All examples document the same fictional observability product so the same material is visible in four shapes.
---

# Examples

These exemplars document the same fictional product — *Foglight*, an observability platform with a CLI agent and a web UI — across all four Diátaxis types. Foglight is invented for these examples and is not a real product; any resemblance to existing tools is coincidence.

Use the exemplars by **shape**, not topic: when classifying a real page, ask which of the four it most resembles in structure, voice, and reader intent. The before/after pairs at the end demonstrate the two most common transformations the skill performs — drift-rewrite (single-intent, restructured) and split (multi-intent, separated).

> **Note on frontmatter:** the exemplars below show only `diataxis_type` in frontmatter for brevity. Real outputs MUST include all three required fields (`product`, `diataxis_type`, `source_path`) per Step 5 of `SKILL.md`, plus the provenance HTML comment from the Rewrite rules. The exemplars demonstrate **shape and voice**, not the full output format.

> **Note on output paths:** the paths below (e.g. `docs/how-to/install.md`) use the **flat layout** for brevity — the layout the skill uses when `journeys = []`. In the default journey-scoped layout (when journeys are supplied), the category folder is nested one level deeper under a journey folder: each file lands at `<output_root>/<journey-slug>/<category>/…` for **every** journey the source matched (the page is duplicated across journey folders), or at `<output_root>/no-journey/<category>/…` if it matched none. So `docs/how-to/install.md` below would be `docs/<journey-slug>/how-to/install.md` in the journey-scoped layout. See "Output root resolution" and "Placement, naming, and collisions" in `SKILL.md`. The classification **shape** these examples teach is identical in both layouts.

---

## 1. Tutorial exemplar

````markdown
---
diataxis_type: tutorial
---

# Your first dashboard with Foglight

In this tutorial we'll install Foglight, send some sample data from your laptop, and build a dashboard that shows it. By the end you'll see your own request data on a live chart — and you'll have everything you need to do this again for a real service.

## Step 1 — Install the CLI

Run:

```
brew install foglight
```

You should see `foglight 2.1.0` printed. If you see `command not found`, open a new terminal — your shell may not have picked up the install yet.

## Step 2 — Start the agent

Run:

```
foglight agent start --demo
```

You'll see a line that reads `agent ready (demo mode)`. The agent is now generating sample request data and sending it to Foglight under your account.

## Step 3 — Open the UI

Open `https://app.foglight.example/dashboards` in your browser. Click **New dashboard**, name it "My first dashboard", and click **Create**.

Notice the empty dashboard with a single tile that says "Add data". This is where we'll put the chart.

## Step 4 — Add a request-rate chart

Click **Add data**, choose **Requests per second**, and click **Save**. You should see a line chart appear with a steady trickle of requests.

Let's check that the chart is showing your data: hover over the line and you'll see a tooltip with values around 5–10 requests/second, which matches what the demo agent is sending.

## Step 5 — Stop the agent

Back in your terminal:

```
foglight agent stop
```

The chart in the UI will go flat within a minute or two. That's expected — there's no data being sent any more.

## What you've built

You now have a Foglight account with one dashboard and one chart, fed by a local agent in demo mode. The same flow — install agent, send data, build dashboard — is how you'd onboard a real service. When you're ready to send real data instead of demo data, see the how-to "How to forward Foglight logs to S3" and the reference "foglight agent".
````

**Why this is a tutorial:** beginner reader; single linear path; concrete throughout (this command, this output); each step verifies; pedagogical voice ("we'll", "let's check", "you should see"); celebration at the end; explanation is minimal and deferred.

---

## 2. How-to exemplar

````markdown
---
diataxis_type: how-to
---

# How to forward Foglight logs to S3

This guide configures the Foglight agent to forward logs to an Amazon S3 bucket. Use it when you need long-term archival or when your compliance team requires logs outside Foglight's own storage.

## Prerequisites

- Foglight agent 2.0 or later (`foglight agent version`).
- An S3 bucket in the region you want to write to.
- Either an IAM role attached to the host, or a long-lived AWS access key.

## Step 1 — Configure the destination

Edit `/etc/foglight/agent.yaml` and add:

```
forwarders:
  - kind: s3
    bucket: <your-bucket>
    region: <your-region>
    prefix: foglight/
```

## Step 2 — Configure authentication

Pick one path.

**If the host has an IAM role with `s3:PutObject` on the bucket**, no further configuration is needed. Skip to step 3.

**If you are using a long-lived access key**, add to `agent.yaml`:

```
forwarders:
  - kind: s3
    # ...
    auth:
      access_key_id: <key>
      secret_access_key: <secret>
```

Restrict the file's permissions: `chmod 600 /etc/foglight/agent.yaml`.

## Step 3 — Restart the agent

```
sudo systemctl restart foglight-agent
```

## Step 4 — Verify

After 60 seconds, list objects in the bucket:

```
aws s3 ls s3://<your-bucket>/foglight/
```

You should see `.jsonl.gz` files appearing. If you see no objects, check the agent logs at `/var/log/foglight/agent.log` for `s3 forward error` lines.

## See also

- [foglight agent reference](#) — full configuration schema for the agent.
- [About Foglight's sampling model](#) — why some log records may be downsampled before they reach S3.
````

**Why this is a how-to:** competent reader; goal-shaped title; prerequisites; imperative steps; branches by environment (IAM vs static key); links out to reference and explanation rather than enumerating.

---

## 3. Reference exemplar

````markdown
---
diataxis_type: reference
---

# foglight agent

Background process that collects telemetry and forwards it to Foglight.

## Synopsis

```
foglight agent <command> [options]
```

## Commands

| Command   | Description                                                  |
| --------- | ------------------------------------------------------------ |
| `start`   | Start the agent.                                             |
| `stop`    | Stop the running agent.                                      |
| `status`  | Print the agent's running state and last-error, if any.      |
| `version` | Print the agent version and exit.                            |

## Options

| Option                | Type    | Default                  | Description                                                       |
| --------------------- | ------- | ------------------------ | ----------------------------------------------------------------- |
| `--config <path>`     | string  | `/etc/foglight/agent.yaml` | Path to the agent configuration file.                            |
| `--demo`              | bool    | `false`                  | Run in demo mode; sends synthetic data instead of real telemetry. |
| `--log-level <level>` | string  | `info`                   | One of `debug`, `info`, `warn`, `error`.                          |
| `--pidfile <path>`    | string  | `/var/run/foglight.pid`  | Path to write the PID file.                                       |

## Exit codes

| Code | Meaning                                            |
| ---- | -------------------------------------------------- |
| 0    | Success.                                           |
| 1    | Invalid arguments.                                 |
| 2    | Configuration file not found or could not be read. |
| 3    | Network failure contacting the Foglight backend.   |
| 4    | Already running (PID file present and live).       |

## Files

| Path                          | Purpose                       |
| ----------------------------- | ----------------------------- |
| `/etc/foglight/agent.yaml`    | Default configuration file.   |
| `/var/log/foglight/agent.log` | Default log destination.      |
| `/var/run/foglight.pid`       | PID file for the running agent. |

## Examples

```
foglight agent start
foglight agent start --demo --log-level debug
foglight agent status
```
````

**Why this is reference:** neutral declarative voice; no first-person; identical sub-headings across commands; tables of options/exit codes/files; usage snippets, not narrative; reader looks up one entry, leaves.

---

## 4. Explanation exemplar

````markdown
---
diataxis_type: explanation
---

# About Foglight's sampling model

Foglight samples telemetry before it leaves the agent. This page explains why we sample, why we chose head-based sampling, and what trade-offs that implies for the data you see in dashboards.

## Why sample at all

A busy service can emit millions of telemetry records per minute. Sending every record over the network and storing them indefinitely would be expensive in bandwidth and storage, and most of the records would be redundant — successful requests look much like other successful requests. Sampling discards records that are unlikely to be informative, so the records that do survive are the ones worth keeping.

## Head-based vs tail-based sampling

There are two well-known approaches.

**Head-based sampling** decides at the start of a request whether to keep the telemetry it produces. The decision is cheap: a single random draw against a configured rate. It is also coarse: the agent does not know yet whether the request will fail or be slow.

**Tail-based sampling** waits until the request completes, then decides whether to keep its telemetry based on the outcome — for instance, always keeping failed or slow requests, sampling the rest. The decision is informed but expensive: the agent must buffer in-progress telemetry, and a buffer that fills up under load can cause back-pressure on the host.

## Why Foglight chose head-based

Foglight is head-based by default. Two reasons led to that choice.

First, the agent runs *in the host's hot path*. Anything that adds memory or CPU pressure to the agent risks affecting the host's own workload. Head-based sampling adds a constant per-request cost (one random draw); tail-based sampling adds a variable cost that grows with request duration and concurrency.

Second, Foglight's storage tier already deduplicates and compresses telemetry aggressively. The marginal cost of keeping a few more "uninteresting" requests is small, so the informational gain from tail-based sampling is not as large for Foglight as it would be for a system that stored records verbatim.

We do support tail-based sampling for users who need it — for example, security or audit teams who want every failed request preserved. See the agent reference for the `--sampling-strategy` flag.

## What this means in dashboards

Head-based sampling means that, at low traffic, dashboards show every request, and at high traffic they show a representative subset. A spike from zero to ten thousand requests per second will appear faithfully in the rate chart; the *content* of those requests will be down-sampled to a configured fraction. If you need to investigate a specific failing request, head-based sampling means you may not have it — search for similar failures and infer.
````

**Why this is explanation:** discursive prose throughout; "why" framing; explicit comparison of alternatives; opinionated language ("we chose"); no imperative steps; no tables; reader is reflecting, not doing.

---

## Before / after — single-intent drift, rewritten

The original page below opens as a tutorial, then mid-flow becomes a flag enumeration and a digression on transport choice. The dominant intent is tutorial; the drift is explanation and reference content mixed in. Verdict: REWRITE as a single-intent tutorial. The displaced material goes into the corresponding reference and explanation pages, not this one.

### Before — `docs/onboarding/first-dashboard.md`

````markdown
# Your first dashboard

To set up a dashboard, run `foglight agent start`. The agent accepts the
following flags:

| Flag           | Description                                |
| -------------- | ------------------------------------------ |
| `--config`     | Path to the configuration file.            |
| `--demo`       | Run in demo mode.                          |
| `--log-level`  | Log level (debug, info, warn, error).      |
| `--pidfile`    | Path to write the PID file.                |

A note on transport: Foglight uses OTLP over HTTP/2 because we originally
considered gRPC but found that HTTP/2 was simpler to operate at the edge
where some customers have proxies that don't pass gRPC cleanly. There were
trade-offs in latency and message size that we accepted in exchange…

Once the agent is running, open the UI and click "New dashboard"…
````

**What's wrong:** the page opens with the imperative step but then enumerates flags (reference content) and digresses on transport rationale (explanation content) before resuming the tutorial. A beginner trying to learn will lose the thread; a competent user looking up a flag will not find it here.

### After — three files

**`docs/tutorials/first-dashboard.md`** (the cleaned-up tutorial):

````markdown
# Your first dashboard with Foglight

In this tutorial we'll install Foglight, send some sample data, and build a dashboard.

## Step 1 — Start the agent

Run `foglight agent start --demo`. You should see `agent ready (demo mode)`.

## Step 2 — Open the UI

…

## See also
- [foglight agent reference](../reference/foglight-agent.md) — every flag.
- [About Foglight's transport choices](../explanation/transport.md) — why OTLP over HTTP/2.
````

**`docs/reference/foglight-agent.md`** receives the flag table (merged with the existing reference for the command).

**`docs/explanation/transport.md`** receives the OTLP/HTTP/2 digression (or it's added as a new section in an existing explanation page).

The tutorial is now single-intent; nothing has been deleted; the displaced material has been placed where readers will find it for its own purpose.

---

## Before / after — multi-intent split

The original page below contains an actionable how-to (configuring rate limits) intertwined with a substantial explanation (the design of the rate-limit algorithm and its trade-offs). The two intents are roughly equally weighted, and neither can be reduced to a one-paragraph aside. Verdict: SPLIT.

### Before — `docs/rate-limiting.md`

````markdown
# Rate limiting

Foglight rate-limits incoming telemetry per agent. The default is 10,000
records per second per agent, dropping the excess.

The algorithm is a token bucket with a refill rate equal to the rate limit
and a burst capacity of 2x the rate limit. We chose a token bucket rather
than a leaky bucket because operators often see legitimate bursts (e.g. at
the start of a CI run) that should not be dropped — a token bucket
absorbs those bursts up to the burst capacity, then degrades to the
configured rate.

To configure the rate limit, edit `/etc/foglight/agent.yaml`:

    rate_limit:
      records_per_second: 5000
      burst_multiplier: 3

Then restart the agent:

    sudo systemctl restart foglight-agent

The choice of burst multiplier is a trade-off: higher values let you
absorb larger bursts but mean a slower-recovering memory footprint if a
burst happens repeatedly. We recommend 2–4 for most workloads…
````

**What's wrong:** the page interleaves "how do I change the rate limit?" (a how-to) with "why is the algorithm a token bucket?" (an explanation). Each is substantive — neither is a one-paragraph aside. A reader looking up "how do I change the rate limit" wastes time on the algorithm discussion; a reader trying to understand the algorithm wades through configuration syntax.

### After — two files

**`docs/how-to/configure-rate-limit.md`:**

````markdown
# How to configure the Foglight agent's rate limit

Use this to raise, lower, or change the burst behaviour of the per-agent rate limit.

## Step 1 — Edit the configuration

Edit `/etc/foglight/agent.yaml` and add:

    rate_limit:
      records_per_second: 5000
      burst_multiplier: 3

## Step 2 — Restart the agent

    sudo systemctl restart foglight-agent

## Recommended values

- `records_per_second` — set to your steady-state telemetry volume plus headroom.
- `burst_multiplier` — 2–4 for most workloads.

## See also
- [About Foglight's rate-limit algorithm](../explanation/rate-limit-algorithm.md)
````

**`docs/explanation/rate-limit-algorithm.md`:**

````markdown
# About Foglight's rate-limit algorithm

Foglight rate-limits incoming telemetry per agent. The algorithm is a token bucket. This page explains the choice and the trade-offs.

## Why a token bucket

…

## Trade-offs in burst multiplier

…

## See also
- [How to configure the rate limit](../how-to/configure-rate-limit.md)
````

Every fact in the original survives in one of the two outputs. Each output is single-intent, properly voiced, and cross-linked. The `REPORT.md` entry for this split would read:

> `docs/rate-limiting.md` → `docs/how-to/configure-rate-limit.md` + `docs/explanation/rate-limit-algorithm.md`. Reason: how-to (~45%) and explanation (~50%) roughly comparable; trade-off discussion is substantive and cannot be reduced to a one-paragraph aside.

---

## Before / after — N-output split (sprawling README)

The original page below is a Foglight `README.md` that has accumulated ten different topics over time: three install variants, three independent configuration tasks, troubleshooting, two CLI command references, and one essay on why Foglight exists. There is no single dominant intent; each section is independent. Verdict: SPLIT into many outputs.

### Before — `docs/README.md` (table of contents only)

````markdown
# Foglight

## Install on Linux
…
## Install on macOS
…
## Install on Windows
…
## Configure the agent
…
## Forward logs to S3
…
## Scrape metrics from a Prometheus endpoint
…
## Troubleshooting common issues
…
## CLI reference: `foglight agent`
…
## CLI reference: `foglight dashboard`
…
## Why we built Foglight
…
````

### Unit analysis

| Source section                                    | Type        | Grouping decision                                                          | Output                                              |
| ------------------------------------------------- | ----------- | -------------------------------------------------------------------------- | --------------------------------------------------- |
| Install on Linux / macOS / Windows                | how-to      | Three **variants of one goal** → group into one file with sub-sections     | `how-to/install.md`                                 |
| Configure the agent                               | how-to      | Independent goal                                                           | `how-to/configure-agent.md`                         |
| Forward logs to S3                                | how-to      | Independent goal                                                           | `how-to/forward-logs-to-s3.md`                      |
| Scrape metrics from a Prometheus endpoint         | how-to      | Independent goal                                                           | `how-to/scrape-prometheus-metrics.md`               |
| Troubleshooting common issues                     | how-to      | Independent goal                                                           | `how-to/troubleshoot-common-issues.md`              |
| CLI reference: `foglight agent`                   | reference   | Independent subject                                                        | `reference/foglight-agent.md`                       |
| CLI reference: `foglight dashboard`               | reference   | Independent subject                                                        | `reference/foglight-dashboard.md`                   |
| Why we built Foglight                             | explanation | Independent topic                                                          | `explanation/why-foglight.md`                       |

Ten source sections produce eight output files: three install variants group into a single how-to (because they share one goal), and everything else stays separate.

### After — eight files in three category folders

```
docs/how-to/install.md
docs/how-to/configure-agent.md
docs/how-to/forward-logs-to-s3.md
docs/how-to/scrape-prometheus-metrics.md
docs/how-to/troubleshoot-common-issues.md
docs/reference/foglight-agent.md
docs/reference/foglight-dashboard.md
docs/explanation/why-foglight.md
```

Each output is strictly single-type. The grouped how-to (`how-to/install.md`) carries the three install variants as sub-sections under one shared goal:

````markdown
# How to install Foglight

In this guide you'll install the Foglight agent. Pick the section for your platform.

## On Linux

…

## On macOS

…

## On Windows

…
````

Every output carries a "See also" footer linking to its siblings. Because eight siblings is a lot, the See-also block in each file groups links by type:

````markdown
## See also

**How-tos**
- [How to configure the agent](../how-to/configure-agent.md)
- [How to forward logs to S3](../how-to/forward-logs-to-s3.md)
- [How to scrape metrics from a Prometheus endpoint](../how-to/scrape-prometheus-metrics.md)
- [How to troubleshoot common issues](../how-to/troubleshoot-common-issues.md)

**References**
- [foglight agent](../reference/foglight-agent.md)
- [foglight dashboard](../reference/foglight-dashboard.md)

**Explanation**
- [About Foglight](../explanation/why-foglight.md)
````

The `REPORT.md` entry for this split would read:

> `docs/README.md` → 8 outputs across 3 types. Reason: 10 independent source sections; three install variants grouped as one how-to (variants of a single goal); four independent how-tos kept separate; two reference entries kept separate (different commands); one explanation. No facts dropped.
