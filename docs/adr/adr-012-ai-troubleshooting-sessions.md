# ADR: AI Troubleshooting Sessions for Support Queries

**Date:** 2026-11-12
**Status:** Proposed

---

## Context

Support engineers investigate and troubleshoot queries outside the bot, each with their own tools
and methods, so how a query is investigated differs from person to person.

That work is also invisible to the bot. An investigation stays with the engineer who ran it: nobody
else can continue it, and the ticket records none of it.

We want the bot to troubleshoot a support query itself, using an AI agent that runs diagnostic
tools and suggests a resolution:

- **Manually**, with an engineer steering, or **automatically**, with the bot investigating and
  replying to the query's author by itself.
- The mode can be changed during an investigation.
- Another engineer can pick up an investigation someone else left, and see the query's whole
  history.
- **Read-only.** The bot diagnoses and suggests a resolution. It never changes the systems it looks
  at.

---

## Decision Drivers

- Troubleshooting quality should match that of a mature agent harness, and keep improving without
  us maintaining one.
- The harness must be replaceable. Installations approve different harnesses, and local development
  may use a different one from production.
- An investigation belongs to the ticket, not to an engineer or a process.
- Cost should follow activity. A query's author can take minutes, an hour or a day to reply, and
  nothing should run while waiting.
- Read-only must hold even if the agent is wrong or is misled by what it reads.
- Messages that need no investigation (a thank-you, a request to close) should not start one.

---

## Options Considered

### Where the agent loop runs

**Option 1 - Inside the service.** The existing LangChain4j client runs the loop, with tools written
in Java or exposed as MCP servers.

- No new deployable, works with any LLM provider, and keeps to ADR-002's "no separate worker".
- We would be building a harness: context management, tool handling, documentation lookup. It would
  fall behind dedicated ones, and an installation could not bring a harness it has already
  approved.

**Option 2 - A separate worker running an existing harness headless (chosen).** The service decides
who talks to the harness and where its output goes; the harness does the troubleshooting.

- An installation picks a harness it has approved and supplies its own documentation and tools.
  Harness improvements arrive with its releases.
- A new deployable, a shell that must be confined, and a dependency on harness CLIs we do not
  control.

### How long a worker lives

- **For the ticket's lifetime.** Rejected: it would sit idle for most of it.
- **Until it has been idle for a while, then started again on demand (chosen).**

### How an investigation continues on a new worker

- **Restore the harness's own session.** Rejected: each harness keeps session state in its own
  format and place, not all document it or support resuming when headless, and it would need
  storage shared between workers. The design would be tied to one harness's internals.
- **Start a fresh harness session seeded from what the service stored (chosen).** Workers hold no
  state, the same path serves every run, and it works with any harness.

### What a message reaches first

- **The worker.** Rejected: a worker would start for messages that need none, such as an author's
  thank-you or an engineer's question the session already answers.
- **A model in the service that routes it, in automatic mode only.** Rejected: manual and automatic
  mode would be two separate paths, and an engineer's every message would start a run.
- **A model in the service that routes it, in both modes (chosen).** The modes then differ only in
  whether it asks an engineer first.

### What manual mode means

- **The engineer drives.** The router waits for instructions, and a new message in the thread
  starts nothing. Rejected: the engineer starts from a blank page each time, and the two modes
  behave differently.
- **The router proposes and the engineer approves (chosen).** It follows the thread and works out
  the next action exactly as in automatic mode, then asks before carrying it out.

### Where engineers steer

- **Slack.** Rejected: the query's thread is where the author is, so steering there would show them
  the working-out, and Slack shows tool output and a long history poorly.
- **The web UI (chosen).** It already restricts pages by role, and can show the whole session
  beside the conversation.

### What enforces read-only

- **The harness's permission settings.** Rejected as the guard: they differ between harnesses, an
  allowlist that matches command names allows `kubectl delete` along with `kubectl get`, and
  automatic command review is best-effort.
- **Credentials and network rules (chosen)**, with the harness's settings on top as a convenience.

---

## Decision

Run investigations in short-lived workers that wrap an agent harness running headless, with the
service owning the session, the mode and the history. The design names no harness: each one is an
implementation behind the same interface.

The feature is off unless enabled, and needs an LLM provider configured (`llm.provider`) for the
router.

Two terms are used below. A **session** is the service's record of a ticket's investigation. A
**run** is one invocation of the harness; a session has many.

### 1. Sessions

- **One session per ticket, stored in PostgreSQL.** It holds the mode, the case note (see 7) and an
  append-only log: the engineers' conversation with the router, every run's prompts, agent messages,
  tool calls and their output, the router's proposals and what the engineer did with them, posted
  replies, and mode changes with who made them.
- **The log is copied from the harness's event stream**, translated into the service's own event
  types (see 6), so the history reads the same whichever harness produced it and never depends on a
  worker still existing.
- **Nobody owns a session.** Any support engineer can view it and continue it. A database lock
  allows one run per session at a time, as the analysis pipeline does for its batches.

### 2. Modes

In both modes the router follows the query's thread and works out what should happen next (see 3).
The modes differ only in whether it asks first.

- **Manual.** The router proposes its next action to the engineer in the web UI (see 4) and waits.
  The engineer approves it, changes it or turns it down. Nothing is investigated, posted or closed
  without that approval. Only discussing with the engineer needs none.
- **Automatic.** The router carries out its action without asking.
- **The mode is a field on the session, read before each router turn** and changed from the web UI.
  Both modes go through the same router and session, so a change takes effect on the next turn and
  loses nothing.

### 3. Router

A model in the service that every message reaches first, in both modes. It is the only part that
talks to people; workers report to it.

A turn starts with a new message in the thread, a message from the engineer, or the end of a run.
Each turn it reads the thread and the session, and picks one action:

- **Investigate.** It writes the prompt for a run. When the run ends the findings come back to it,
  and it picks the next action.
- **Reply to the author**: an answer, a question for them or a closing message, posted to the
  thread.
- **Close the ticket**, with the tags and impact a closed ticket needs.
- **Discuss.** It talks to the engineer in the web UI: answers a question from what the session
  already holds, or explains what it proposes and why.

In manual mode every action except Discuss is a proposal (see 2). A newer turn replaces a proposal
that is still waiting.

Two things keep it small:

- It uses the service's existing LLM client (`llm.*`), not the harness.
- **It has no tools and gathers no evidence.** Whatever the session does not already answer goes to
  a worker, and when unsure it investigates.

### 4. Web UI

- **Engineers steer and read sessions in the web UI**, on the ticket. Slack stays where the author
  is.
- **The session view** shows the query's thread, the case note, every run with its tool output, the
  conversation with the router, and each change of mode.
- From it an engineer talks to the router, changes the mode, and approves, changes or turns down
  what the router proposes: what an investigation should look into, the text of a reply, closing
  the ticket.
- Sessions are limited to the `SUPPORT_ENGINEER` role.

### 5. Worker

- A container image with a harness, the installation's documentation and the read-only tools, plus
  a small wrapper. The wrapper takes prompts from the service, runs the harness and streams the
  events back.
- **Started on demand.** The service creates a worker for a session when a run is needed and none is
  running, using its Kubernetes client.
- **Stops itself** after an idle period (configurable, 15 minutes by default). A worker that is
  still running serves the session's next run, which only saves start-up time.

### 6. Harness

- **One interface, several implementations.** An implementation knows how to start its harness with
  a prompt and how to translate its output. Nothing else in the service or the wrapper knows which
  harness is in use.
- **Chosen by configuration**, per installation. Local development can run one harness and
  production another.
- **What a harness must offer** to be supported:
  - a headless mode that takes a prompt and exits when the answer is complete;
  - a structured stream of its messages, tool calls and tool output;
  - a way to refuse commands without waiting for a human;
  - authentication that needs no interactive login.
- **Nothing harness-specific is stored.** Sessions, the event log and the case note use the
  service's own formats, so an installation can change harness without losing history.

### 7. Continuing an investigation

Every run is a fresh harness session, seeded in three layers:

- **The case note and the query's thread**, always.
- **The last one or two runs in full**, including tool output, so a quick follow-up can refer to
  what was just seen.
- **Older tool output as files** in the worker's workspace, named in the case note. The agent reads
  them only if it needs to.

Each run ends with the agent rewriting the **case note** in a fixed structure: findings, evidence,
what was ruled out, and next steps. An engineer picking up the ticket reads the same note.

### 8. Read-only

- **Credentials.** The worker's cluster and metrics credentials can only read, and cannot read
  secrets or open a shell in a pod.
- **Network.** The worker can reach the service, the harness's backend and the systems it
  diagnoses, and nothing else.
- **Harness settings.** A command allowlist or automatic review, where the harness has one, keeps
  the agent from stalling on harmless commands. No implementation runs its harness in a mode that
  approves everything.
- Read-only describes the diagnostic tools. The bot still replies in threads and closes tickets.

### Not decided here

- **Which clusters a worker can reach**, and how it gets read-only credentials for each.
- **When a session starts and in which mode**: for every new ticket, or when an engineer opens one.
- **Limits in automatic mode**: how long a run may take, how many a session may have, and when the
  router hands over to an engineer.
- **What automatic mode may post unreviewed.** Whether replies pass a redaction step, or automatic
  mode is limited to some kinds of query.
- **Messages during a run**: whether they wait for it to end, and whether an engineer can stop it.
- **Closing and escalating**: how the tags and impact are chosen when the router closes a ticket,
  and whether it may escalate one.
- **How engineers learn that a proposal is waiting**, and what happens to one when the mode changes.
- **How the web UI follows a run in progress**: polling or a stream.
- **How a worker is started in local development**, where there may be no cluster.
- **How the worker authenticates to the service.**
- The schema, API and configuration keys.

### To confirm before building

For each harness an installation uses:

- Its licence terms allow unattended, shared use.
- Whether its command sandbox, if it has one, works inside a pod. These often need user namespaces
  or a recent kernel.
- What it does with a refused command when headless: reject it, or wait for an approval that never
  comes.

---

## Consequences

### Positive

- Investigations run on a mature harness, with each installation's own documentation and tools.
- Queries are investigated the same way whoever is on support.
- An investigation is part of the ticket: any engineer can continue it, and its history outlives
  every worker.
- Workers hold no state and run only while a query is active.
- Trivial messages are answered without starting a worker.
- Changing mode is cheap, because the modes differ only in whether the router asks first.
- In manual mode the engineer starts from a proposal, not a blank page, and can question it without
  starting a run.

### Negative / Trade-offs

- **A second deployable.** A worker image to build and release, and the service gains permission to
  create pods. This departs from ADR-002, which avoided a separate worker.
- **A shell with cluster credentials.** Read-only is only as good as the credentials and network
  rules each installation sets up.
- **Untrusted input.** Thread messages can try to steer the agent, and in automatic mode an answer
  can repeat sensitive log content to the author.
- **Data leaves the installation.** Thread content, cluster output and logs go to the harness's
  model provider, and thread content to the router's.
- **Context is rebuilt every run.** Seeding costs tokens each time, and what is not in the case note
  or the recent runs is only seen if the agent opens the files. A poor case note makes a poor
  investigation.
- **Manual mode waits for an engineer.** A proposal nobody answers leaves the author without a
  reply.
- **The router is more than a classifier.** It holds a conversation and writes replies, so its model
  and prompt matter to quality, and it is the part most likely to grow into a harness. Having no
  tools is what stops it.
- **Findings are relayed.** The router sits between the worker and whoever reads the answer, and can
  drop or change detail such as an exact command or error message.
- **Engineers steer away from Slack**, where the query itself is.
- **Start-up delay** on the first message after a worker has stopped.
- **Tied to harness CLIs.** A change to a harness's flags or event format breaks its implementation
  until it is updated, and each supported harness is one more to maintain.
- **Harnesses behave differently.** An investigation that works with the harness used in local
  development is not proof it works with the one in production.
- **Two models to configure and pay for**: the router's and the harness's.
- **A misrouted message** can skip an investigation that was needed. Investigating when unsure
  limits this, at the price of some unnecessary runs.
