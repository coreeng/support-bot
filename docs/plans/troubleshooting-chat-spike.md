# Troubleshooting chat spike: reviewed plan

Prepares for [ADR-012](../adr/adr-012-ai-troubleshooting-sessions.md). Stacked on PR #13 (`feat/anthropic-llm-provider`). Not meant to merge.

How to read the references:

- `JAVA/` is the service's main package directory, `api/service/src/main/java/**/supportbot/`.
- `RES/` is `api/service/src/main/resources/`. `UI/` is `ui/src/`.
- Line numbers are from `feat/anthropic-llm-provider` at commit `f53b4f23`. `application.yaml` lines are from the committed file, not from local edits.
- Nothing was built or run for this review. The last section lists what is not verified.

## 1. Goal and success criteria

**Goal.** In about two days, find out whether assistant-ui's `ExternalStoreRuntime` can render a shared chat for a ticket, where the server owns the state and a router on the shared Claude model decides the next action.

**Success is one of two answers, with the reason written down:**

- **Yes.** assistant-ui renders server-owned events and our custom cards without workarounds.
- **No.** Build the timeline from our own components. The backend, the endpoints and the cards stay the same.

**"Without fighting us" means all of these hold** (checked on day 1, see 4.6):

1. Events held in react-query render in order, and a reload shows the same history.
2. Items next to each other stay separate. A router message, a proposal and a Slack message are not merged into one bubble.
3. Our cards render as message parts, built from `UI/components/ui`, and their buttons work.
4. Two engineers and the Slack author can be told apart.
5. An engineer can type while the router is busy, or the limit is acceptable.
6. Updates that arrive from the stream do not duplicate messages or make the view jump.
7. It needs `@assistant-ui/react` only, with design tokens only (no raw colour classes).

One failed point that costs more than about an hour to work around is a "no".

## 2. Findings from the code

### 2.1 Backend conventions

- **Config records must live in `config/`.** The scan covers only that package (`JAVA/SupportBotApplication.java:16`). A `TroubleshootingProps` placed in a `troubleshooting` package would not bind.
- **Feature flags use `@ConditionalOnProperty`** (`JAVA/prtracking/PrTrackingConfig.java:12`, props at `JAVA/config/PrTrackingProps.java:17`). LLM features use `@ConditionalOnLlmEnabled` (`JAVA/config/ConditionalOnLlmEnabled.java`). The spike needs both on every bean. With `llm.provider=none` there is no `ChatModel` bean (`JAVA/analysis/llm/LlmConfig.java:21-22`), so a bean that injects it would stop the service from starting.
- **The UI asks an always-registered `/enabled` endpoint** whether to show a feature (`JAVA/summary/rest/SummaryEnabledController.java:29-31`). The spike should do the same.
- **Layout per feature:** domain classes and `Jdbc*Repository` in the package, REST in a `rest/` sub-package with `*UI` records, a mapper and a `@RestControllerAdvice` that returns `ProblemDetail` with a `code` (`JAVA/summary/rest/SummaryController.java:24-28`, `JAVA/summary/rest/SummaryExceptionHandler.java:11-22`).
- **Controllers may not use repositories directly.** `ArchitectureTest` fails the build (`api/service/src/test/java/**/supportbot/ArchitectureTest.java:21`).
- **Repositories are jOOQ classes with `@Transactional` methods** (`JAVA/summary/JdbcSummarySnapshotRepository.java:12-19`). JSONB is already used: `RES/db/migration/V34__elevate_connection.sql:3`, written with `JSONB.valueOf` (`JAVA/elevate/ElevateRepository.java:792-794`). `JAVA/util/JsonMapper.java:38,46` converts to and from JSON.
- **Latest migration is V44** on this branch, on `origin/main` and on every remote branch checked. V45 is free.
- **Every new column must be listed in `erasure-compliance.yaml`** or `ErasureComplianceTest` fails (`api/AGENTS.md:32`).
- **The build is strict:** Error Prone with `-Werror`, NullAway, Checkstyle (enum constants in `UPPER_SNAKE_CASE`), Spotless (`api/AGENTS.md`). Spike code must pass these to compile. Allow time for it.
- **No controller reads the caller's identity today.** The principal is a `UserPrincipal(email, name, teams, roles)` (`JAVA/security/UserPrincipal.java:6`, `JAVA/security/JwtAuthenticationToken.java:29`). The spike is the first to need an actor.

### 2.2 Background work

- **The LLM executor cannot be reused.** `analysisTaskExecutor` has one thread and no queue (`JAVA/config/AsyncConfig.java:33-45`). The summary refresh and the analysis run share it (`JAVA/summary/SummaryRefreshService.java:172-181`). A router turn would be rejected while either runs.
- **A virtual-thread executor already exists** for Slack handlers (`JAVA/config/ExecutorConfig.java:15-18`). The spike should add its own in the same style.
- **`async_job` is the lock ADR-012 points to.** `tryStartJob` inserts a row and returns false on a duplicate id (`JAVA/asyncjob/JdbcAsyncJobRepository.java:23-36`). The startup resumer only looks at the analysis job id (`JAVA/analysis/AnalysisJobResumer.java:42`), so a crashed router turn would leave a row nobody clears.
- **The API runs as one replica.** It is fixed in the chart (`helm-chart/templates/deployment.yaml:8`). An in-memory list of open streams works for the spike and for today's deployment. It will not work with two replicas.

### 2.3 Tickets, queries and Slack threads

- **A ticket points to one query, and a query is one Slack message:** `query(ts, channel_id)` is unique, `ticket.query_id` is unique (`RES/db/migration/V1__init.sql:18-25,32-44`). The thread is `ticket.channelId()` plus `ticket.queryTs()` (`JAVA/ticket/Ticket.java:19-20,53-55`).
- **`ThreadService` returns text only.** `getThreadAsText` gives one sanitised string with no author and no time per message (`JAVA/summarydata/ThreadService.java:142-146,165-175`). It cannot feed "Slack message" cards.
- **`ThreadService` is slow by design.** It sleeps 500 ms after every fetch and up to 10 s on a rate limit (`JAVA/summarydata/ThreadService.java:96-110,136`). Calling it on every router turn adds that to every reply.
- **`ThreadService` throws when the thread is missing.** The analysis caller catches everything and returns null (`JAVA/analysis/llm/LlmAnalysisService.java:64-67`). The router must catch it too, or seeded tickets break.
- **Seeded tickets have no query text either.** `GET /ticket/{id}` reads it from Slack and returns null on failure (`JAVA/ticket/TicketQueryService.java:82-94`). For those tickets the router sees status, team, impact and tags only.
- **Nothing puts Slack replies into a session today.** Thread replies only touch the ticket (`JAVA/ticket/TicketProcessingService.java:105-121`).
- **A second handler for Slack messages is easy.** Handlers are grouped by event class and each runs on the executor (`JAVA/config/SlackAppInit.java:79-86`). A new `SlackEventHandler<MessageEvent>` receives thread replies without touching ticket code.

### 2.4 How the LLM is called

- **One shared `ChatModel` bean per provider** (`JAVA/analysis/llm/LlmConfig.java:89-116`). For Anthropic: 60 s timeout and 16,000 max tokens (`JAVA/config/LlmProps.java:137-141`).
- **Both callers send one string and get one string back** (`JAVA/analysis/llm/LlmAnalysisService.java:60`, `JAVA/summary/LlmSummaryService.java:63`). There is no system message, no JSON mode and no token count.
- **Output is parsed by line prefix** (`JAVA/analysis/llm/LlmAnalysisService.java:105-130`). There is no JSON parsing of model output to copy.
- **Untrusted text is wrapped in delimiters** (`JAVA/analysis/llm/LlmAnalysisService.java:77-87`). The router should keep that habit.
- **The provider logs nothing per call.** The builder sets no request logging and no listener (`JAVA/analysis/llm/LlmConfig.java:104-115`). "Check Anthropic calls in the API log" will show nothing unless the router logs it.
- **LangChain4j 1.18.0 can do more than the callers use** (`api/service/build.gradle.kts:125`). `ChatModel.chat(ChatRequest)` returns a `ChatResponse`. The Anthropic builder has `responseFormat`, and `AnthropicTokenUsage` carries input, output and cache token counts. Seen in the jar with `javap`, not run.
- **The request sends no `thinking` field** (`api/service/src/test/java/**/supportbot/analysis/llm/AnthropicChatModelContractTest.java:73`). On `claude-sonnet-5-5` that means thinking is on at the default effort. Each turn pays for thinking tokens and waits for them.
- **`llm.provider` switches on every LLM feature** (`JAVA/config/LlmProvider.java`, `JAVA/config/LlmProps.java:59`). With `LLM_PROVIDER=anthropic` the Support Summary page also calls Claude when opened.
- **The stub provider only knows two callers** (`JAVA/analysis/llm/StubChatModel.java:28-38`). With `LLM_PROVIDER=stub` the router would get a classification answer, not JSON.

### 2.5 Security

- **New routes need one new rule.** Rules are path matchers in one chain (`JAVA/security/SecurityConfig.java:49-101`). Support-engineer-only routes use `hasAnyRole("SUPPORT_ENGINEER")` (`:94-98`). Anything not listed is open to every signed-in user (`:100-101`), so forgetting the rule exposes sessions to all users.
- **The rule must not be limited to GET.** The file explains why: HEAD is served by `@GetMapping` (`JAVA/security/SecurityConfig.java:76-79`).
- **`/enabled` checks sit before the role rules** so every user can ask (`JAVA/security/SecurityConfig.java:66-72`).
- **The backend has no CSRF and no session** (`JAVA/security/SecurityConfig.java:46,48`). It trusts the bearer token (`JAVA/security/JwtAuthenticationFilter.java:24-34`). CSRF is checked in the Next.js layer (`UI/app/api/_lib/csrf.ts:8-25`).
- **CSRF is checked on routes that cost money or change prompts**, including a GET (`UI/app/api/summary/route.ts:6-10`). It is not checked everywhere (`UI/app/api/tickets/[id]/route.ts:19-35`). Troubleshooting POSTs start paid LLM calls, so they should check it.
- **SSE works with the bearer token.** The browser sends the session cookie to the Next.js route, and the route adds the token (`UI/app/api/_lib/backend-fetch.ts:10-20`). `EventSource` cannot set headers, and it does not need to. A GET stream needs no CSRF token.
- **SSE will trip the security filter when a stream ends.** `SseEmitter` finishes with an ASYNC dispatch. The JWT filter is a `OncePerRequestFilter`, which skips ASYNC dispatches as it skips ERROR ones. The chain already has a rule for ERROR for that reason (`JAVA/security/SecurityConfig.java:50-55`). There is none for ASYNC. Expect "response is already committed" errors on every stream close until one is added.
- **A second identity is available for tests.** With `security.test-bypass.enabled=true`, the headers `X-Test-User` and `X-Test-Role: support` sign a request in as a support engineer (`JAVA/security/TestAuthBypassFilter.java:19-20,27,44`). It is off by default (`RES/application.yaml:33-34`).

### 2.6 Server and streaming

- **Spring MVC on embedded Jetty 12** (`api/service/build.gradle.kts:21,71`, `api/build.gradle.kts:45-48`). `SseEmitter` is supported there. There is no async MVC code in the service yet, so this is the first.
- **No async timeout is set** (`RES/application.yaml:1-4`). A plain `new SseEmitter()` then uses the container default, which is short. The emitter needs an explicit timeout.
- **Nothing in the service buffers responses.** No compression is configured, and the only filters are the two auth filters (`JAVA/security/SecurityConfig.java:115-116`).
- **Graceful shutdown waits up to 30 s** for open requests (`RES/application.yaml:3,40-41`). Open streams will slow restarts unless they are closed on shutdown.
- **Ingress is off by default with no annotations** (`helm-chart/values.yaml:43-46,180-183`). A proxy in front may buffer. That is an installation concern, not a spike one.

### 2.7 UI: proxy routes

- **`backendFetch` cannot be used for the stream.** It always sets `Accept: application/json` (`UI/app/api/_lib/backend-fetch.ts:64-67`). An endpoint that produces `text/event-stream` would answer 406.
- **`backendAccessToken` is exported** (`UI/app/api/_lib/backend-fetch.ts:10-20`). The stream route can use it with a plain `fetch`.
- **Route handlers may return a stream.** The bundled Next.js 16.4.1 docs say so and name Server-Sent Events (`ui/node_modules/next/dist/docs/01-app/02-guides/streaming.md:482-484`).
- **Buffering risks are documented:** proxies and compression (`ui/node_modules/next/dist/docs/01-app/02-guides/streaming.md:689-723`). `next.config.ts` does not turn compression off (`ui/next.config.ts`).
- **API routes are never redirected to login** (`UI/proxy.ts:43-47`). The stream route must return 401 itself.
- **A 401 signs the user out** (`UI/lib/hooks/index.ts:41-59`). `EventSource` cannot see a status code, so a dropped stream needs a normal request to find out why.

### 2.8 UI: pages and data

- **There is no tickets page.** `/tickets` redirects to `/` (`UI/app/(dashboard)/tickets/page.tsx:9-19`). The table is on the home page (`UI/app/(dashboard)/page.tsx:17-18`) and opens `EditTicketModal` (`UI/components/tickets/tickets.tsx:600`).
- **The natural place for the button is the modal footer**, next to "Open in Slack" (`UI/components/tickets/EditTicketModal.tsx:522-531`). The modal already knows `isSupportEngineer` (`:33`).
- **A page at `UI/app/(dashboard)/tickets/[id]/session/page.tsx` fits the routing.** Pages are thin client components that wrap a component from `UI/components/**` (`UI/app/(dashboard)/summary/page.tsx:1-12`).
- **All data fetching is react-query through `UI/lib/hooks/index.ts`** (`apiGet` at `:81-98`, `apiFetch` with CSRF at `:108-124`). `swr` is installed but not used anywhere (`ui/package.json:42`). Use react-query only.
- **Queries are fresh for 5 minutes** (`UI/components/providers/Providers.tsx:15-24`). There is no `setQueryData` use yet; the spike adds the first.
- **The existing "live" pattern is polling** (`UI/lib/hooks/index.ts:657-670`). It is the fallback if streaming through Next.js fails.
- **There is no markdown renderer.** Model output is rendered as React text on purpose (`UI/components/summary/summary-prose.tsx:19`). Slack text has its own renderer for code blocks (`UI/components/ui/SlackMessageRenderer.tsx:14`).
- **The design rules are strict:** tokens only, shadcn primitives only, `yarn` only, `yarn format` after every change (`ui/AGENTS.md:3,7,33,71`).
- **There is no `textarea` component** in `UI/components/ui`. `button`, `dialog`, `tooltip`, `avatar`, `input`, `skeleton`, `badge`, `scroll-area` exist.
- **Versions:** Next.js 16.4.1, React 19.2, Tailwind 4.2.4, `radix-ui` locked at 1.4.3 (`ui/package.json:32,36,37,64`, `ui/yarn.lock:5683-5684`).

### 2.9 assistant-ui (from its docs, read 2026-11-18)

Pages read: [ExternalStoreRuntime](https://www.assistant-ui.com/docs/runtimes/custom/external-store), [manual installation](https://www.assistant-ui.com/docs/manual-installation), [installation](https://www.assistant-ui.com/docs/installation), [message conversion](https://www.assistant-ui.com/docs/api-reference/external-store/message-conversion), [MessagePrimitive reference](https://www.assistant-ui.com/docs/api-reference/primitives/message), [Message guide](https://www.assistant-ui.com/docs/primitives/message), [Thread guide](https://www.assistant-ui.com/docs/primitives/thread), [tool rendering](https://www.assistant-ui.com/docs/api-reference/tools/rendering), [React compatibility](https://www.assistant-ui.com/docs/migrations/react-compatibility), and the [npm metadata](https://registry.npmjs.org/@assistant-ui/react/latest).

- **Version and React.** `@assistant-ui/react` is 0.15.36. Peer dependency is React 18 or 19. React 19 needs no changes.
- **State held elsewhere is the documented use.** `useExternalStoreRuntime({ messages, onNew, isRunning, convertMessage })` with `AssistantRuntimeProvider`. Only `onNew` is required. The page has a TanStack Query example.
- **Leaving handlers out turns features off.** No `onEdit` means no edit button, no `onReload` no regenerate, no `setMessages` no branch switching. That suits a server-owned log.
- **Messages have three roles only:** `user`, `assistant`, `system`. There is no notion of several human authors.
- **Custom content is a "data part".** A part of type `data-<name>` with a `data` field becomes a `DataMessagePart`. It is rendered through `MessagePrimitive.Parts`, either with `data.by_name` or with a children function that receives `{ part }`.
- **Messages are picked per role with a children function** on `ThreadPrimitive.Messages`. A thread can be rendered with `Root`, `Viewport` and `Messages` only.
- **`isRunning` shows an optimistic assistant message.**
- **Adjacent assistant messages can be merged.** `useExternalMessageConverter` has a `joinStrategy`, and its default joins them. `none` keeps them apart.
- **Messages should carry an `id`.** Without one they are renumbered and remounted.
- **The prebuilt Thread is a larger install.** It needs `@assistant-ui/react-markdown`, `remark-gfm`, `@base-ui/react`, `tw-shimmer`, `zustand`, and the shadcn components `button`, `skeleton`, `dialog`, `tooltip`, `avatar`, `textarea`, `input`. It copies about 20 files into `components/assistant-ui/`. The primitives need none of that.
- **The recommended installer is interactive** (`npx assistant-ui@latest setup` opens a browser). Use the manual route.
- **Tailwind 4.** `tw-shimmer` is described as a Tailwind v4 plugin. No page states a Tailwind or Next.js requirement.
- **The API changes often.** Deprecated in the pages read: `MessagePrimitive.If`, `ThreadPrimitive.Empty`, the `components` prop on `Messages`, `makeAssistantToolUI`, `useAssistantToolUI`. Several external-store helpers are marked experimental or unstable.
- **It depends on `radix-ui ^1.8.1`.** The repo locks 1.4.3, so yarn would install a second copy.

## 3. Review of the plan

Severity: **blocker** stops the spike or makes its answer wrong. **Should fix** costs hours or hides a problem. **Nice to have** is cheap polish.

| # | Issue | Severity | Change |
|---|-------|----------|--------|
| 1 | The streaming proxy route cannot be built "as today". `backendFetch` forces `Accept: application/json` (2.7). | Blocker | Write the stream route with `backendAccessToken` and a plain `fetch`. Pass `request.signal`, forward `Last-Event-ID`, return the upstream body. |
| 2 | Props in a `troubleshooting` package will not bind, and `troubleshooting.enabled=true` with `llm.provider=none` stops startup (2.1). | Blocker | `TroubleshootingProps` goes in `config/`. Every bean gets both conditions. |
| 3 | The plan does not protect the new routes. Unlisted paths are open to every signed-in user (2.5). | Blocker | Add `/troubleshooting/enabled` to the authenticated list and `/troubleshooting/**` with `hasAnyRole("SUPPORT_ENGINEER")`. |
| 4 | "Sequence number" has no rule for who assigns it. Two engineers sending at once can get the same number or commit out of order, and a stream reading "after N" then misses an event. | Blocker | Keep `last_seq` on the session row. Append with `UPDATE ... SET last_seq = last_seq + 1 ... RETURNING` and the insert in one transaction. Unique `(session_id, seq)`. |
| 5 | The "Slack message" event type has no source. `ThreadService` returns one text blob without authors or times (2.3). | Should fix | At session start, read the thread once with `SlackClient.getThreadPage` and store one `SLACK_MESSAGE` event per message. The router and the cards then read the same events. |
| 6 | Calling `ThreadService` every turn adds 0.5 s or more, and it throws for seeded tickets (2.3). | Should fix | Covered by 5. Catch the Slack error and record "thread not available". |
| 7 | ADR-012 says a newer turn replaces a waiting proposal. The plan has no such state. An engineer could approve a proposal that is out of date. | Should fix | When a turn produces output, mark the waiting proposal `SUPERSEDED` with an event. A decision on a proposal that is not waiting returns 409. |
| 8 | Two engineers can decide the same proposal. | Should fix | Check "still waiting" inside the same row lock as the append. First decision wins, the second gets 409 and the card updates from the stream. |
| 9 | Two engineers can send while a turn runs. The plan starts a router call per message. Calls overlap and answer out of order. | Should fix | One turn per session at a time. A message that arrives during a turn is stored and triggers one more turn when the current one ends. In-process is enough for the spike (one replica, 2.2). |
| 10 | The executors used by summary and analysis cannot take router turns (2.2). | Should fix | Add a `troubleshootingExecutor` on virtual threads. |
| 11 | "Must answer in JSON" has no parsing rule and no failure path. The repo has no JSON parsing of model output (2.4). | Should fix | Ask for a JSON schema through `ChatRequest` and `ResponseFormat`. Parse with Jackson into a `RouterDecision`. On bad JSON, an unknown action, empty text, a timeout or a refusal: append a `ROUTER_STATUS` event with state `FAILED` and stop. Never act on output that did not parse. |
| 12 | Router prompt injection is not mentioned. Slack text is written by the query's author and reaches the router. | Should fix | Rules go in a system message. Thread and engineer text go in the user message inside delimiters, labelled as data. The router has no tools. Every action needs approval. The proposal card shows the exact text that would be posted. Cap the thread length. |
| 13 | Cost and latency per turn cannot be read from the logs (2.4). Thinking is on by default. The whole log is resent every turn. | Should fix | Log and store per turn: model, input tokens, output tokens, duration. Show them on the router's card. Record the median over the test. |
| 14 | `GET .../session` creates the session. A GET with side effects goes against the CSRF habit (2.5), and two windows opening together race. | Should fix | `GET` only reads and returns 404 when there is none. `POST .../session` creates it, is safe to repeat (unique `ticket_id`, `ON CONFLICT DO NOTHING`), and starts the first turn. |
| 15 | `SseEmitter` defaults: short timeout, errors on stream close, no keep-alive (2.5, 2.6). | Should fix | Explicit timeout of 30 minutes. Allow the ASYNC dispatcher type next to the ERROR rule. Send a comment line every 15 s. Close streams on shutdown. |
| 16 | A gap between "load history" and "open stream" can lose events. | Should fix | The stream replays everything after the given sequence number from the database, then follows. The client drops events it already has. |
| 17 | The UI has no way to show "router is thinking" in both windows. `isRunning` held in one browser is wrong for a shared session. | Should fix | The server appends `ROUTER_STATUS` events (`THINKING`, `IDLE`, `FAILED`). `isRunning` is derived from the last one. |
| 18 | assistant-ui has three roles and can merge adjacent assistant messages (2.9). The plan assumes several authors and separate cards. | Should fix | Make these two points the first things the stub tests. Carry the author in the message, give every message an `id`, and turn joining off if it happens. |
| 19 | "Its thread components" pulls in a markdown stack, about 20 copied files and shadcn components that may overwrite ours (2.9). It also breaks the "model output is plain text" habit (2.8). | Should fix | Use the primitives from `@assistant-ui/react` only. Render text as plain text or with `SlackMessageRenderer`. Do not add the prebuilt Thread in the spike. |
| 20 | "A button on the tickets page": there is no tickets page (2.8). | Should fix | Put a "Troubleshoot" link in the `EditTicketModal` footer. Show it when the feature is enabled and the user is a support engineer. |
| 21 | Testing with two windows of the same user does not test two engineers. Actor names would be identical. | Should fix | Send the second engineer's messages with `curl` and the test-bypass headers (2.5), or sign in as a second local user if one exists. |
| 22 | An approved investigation ends with canned events and nothing else. In ADR-012 the end of a run starts a router turn. | Should fix | After the canned run, start a router turn with the canned findings. It costs one LLM call and shows the full loop in the UI. |
| 23 | The stream is only tested in `next dev`. Compression and buffering differ in a production build (2.7). | Should fix | Test the stub stream once with `yarn build && yarn start`. Send `Cache-Control: no-cache, no-transform` and `X-Accel-Buffering: no`. |
| 24 | New tables fail `ErasureComplianceTest`, and the build is strict (2.1). | Should fix | Add both tables to `erasure-compliance.yaml`. Run `./gradlew spotlessApply` before building. |
| 25 | `close` needs tags and impact in ADR-012. The plan treats it like the others. | Nice to have | Keep `close` as a text-only proposal. Record "would close". No tags or impact in the spike. |
| 26 | Backend paths use `/tickets/`. The ticket API is singular (`JAVA/ticket/rest/TicketController.java:17`). | Nice to have | Use `/troubleshooting/ticket/{ticketId}/...` in the service and `/api/troubleshooting/tickets/[id]/...` in the UI, as tickets do today. |
| 27 | `LLM_PROVIDER=anthropic` also turns on the Support Summary and the analysis run (2.4). | Nice to have | Do not open the Summary page during the test, or accept the cost. |
| 28 | A second copy of `radix-ui` (2.9). | Nice to have | Bump `radix-ui` to `^1.8.1` in the spike branch if two copies cause trouble. |
| 29 | The spike migration is V45. The next real migration on `main` will also be V45. | Nice to have | Keep the spike on its own database copy, as planned. Never point another branch at it. |

**Over-scoped for two days.** Cut these first if time runs short, in this order:

1. Live Slack replies into the session (4.2, step 9). The snapshot at session start is enough.
2. Edit-and-approve for `investigate` and `close`. Keep it for `reply` only.
3. `Last-Event-ID` resume. A reconnect can reload the whole session.
4. The `close` action.

**Is the proposed order right?** Mostly. UI first is right, because it is the question. Two changes:

- The stub must contain the hard cases: two engineers, a Slack message, a proposal with buttons, run updates, a busy state, and events arriving over a real stream. A stub that returns one JSON array answers nothing.
- Make the stub a Next.js route handler that streams. That tests the streaming proxy (issue 1 and 23) in the same hours, with no Java.

## 4. The revised plan

### 4.1 Setup (30 minutes)

- Create the worktree by hand: `git worktree add .worktrees/spike-troubleshooting-chat -b spike/troubleshooting-chat feat/anthropic-llm-provider`. The worktree helper skill branches from `main`, which is the wrong base here.
- Copy the local-only `application.yaml` edits and the `.env.local` files into it. Do not commit them. Stage spike changes to `application.yaml` with `git add -p`.
- Use a separate copy of the seeded database, selected with `DB_URL` or `DB_SCHEMA` (`RES/application.yaml:87,98-100`).
- Set `LLM_PROVIDER=anthropic`, `LLM_MODEL_NAME=claude-sonnet-5-5`, `TROUBLESHOOTING_ENABLED=true`. For the two-engineer test also set `security.test-bypass.enabled=true` locally.
- Stop other local instances first. Ports 8080, 8081, 3000 and 5432 are shared between worktrees.
- Run `make codegen-local` in `api/` after adding the migration, so jOOQ knows the new tables.

### 4.2 Backend

All beans carry `@ConditionalOnProperty(name = "troubleshooting.enabled", havingValue = "true")` and `@ConditionalOnLlmEnabled`, except the enabled check.

**1. Migration** `RES/db/migration/V45__troubleshooting_session.sql`

```sql
CREATE TABLE troubleshooting_session (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES ticket (id),
    mode       TEXT        NOT NULL DEFAULT 'MANUAL',
    last_seq   BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT troubleshooting_session_ticket_unique UNIQUE (ticket_id)
);

CREATE TABLE troubleshooting_event (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES troubleshooting_session (id),
    seq        BIGINT      NOT NULL,
    type       TEXT        NOT NULL,
    actor      TEXT        NOT NULL,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT troubleshooting_event_seq_unique UNIQUE (session_id, seq)
);
```

- `actor` is the engineer's email, `router`, `slack:<user id>` or `system`.
- Add both tables to `api/service/src/test/resources/erasure-compliance.yaml`. `actor` and `payload` hold personal data.

**2. Config**

- `JAVA/config/TroubleshootingProps.java`: `record TroubleshootingProps(boolean enabled, int maxThreadChars)` with prefix `troubleshooting`.
- `RES/application.yaml`: `troubleshooting.enabled: ${TROUBLESHOOTING_ENABLED:false}`.
- `JAVA/troubleshooting/TroubleshootingConfig.java`: the `troubleshootingExecutor` bean (virtual threads).

**3. Domain and storage** in `JAVA/troubleshooting/`

- `TroubleshootingSession`, `TroubleshootingEvent` (records), `TroubleshootingMode` (`MANUAL`, `AUTOMATIC`).
- `TroubleshootingEventType`: `SLACK_MESSAGE`, `ENGINEER_MESSAGE`, `ROUTER_MESSAGE`, `PROPOSAL`, `DECISION`, `RUN_UPDATE`, `ROUTER_STATUS`.
- `TroubleshootingRepository` and `JdbcTroubleshootingRepository`: `findByTicketId`, `createIfAbsent`, `append` (takes the row lock, assigns `seq`), `findEventsAfter(sessionId, seq)`.
- `TroubleshootingSessionService`: start a session, append, decide a proposal, list events. It tells the broadcaster after the transaction commits.
- Payloads:
  - `PROPOSAL`: `{action, text, reason}`.
  - `DECISION`: `{proposalSeq, decision, text}` with decision `APPROVED`, `REJECTED` or `SUPERSEDED`. Edit-and-approve is `APPROVED` with a changed `text`.
  - `ROUTER_STATUS`: `{state, error}` with state `THINKING`, `IDLE` or `FAILED`.
  - `ROUTER_MESSAGE` and `PROPOSAL` also carry `{model, inputTokens, outputTokens, durationMs}`.

**4. Stream fan-out** `JAVA/troubleshooting/TroubleshootingEventBroadcaster.java`

- Keeps the open `SseEmitter`s per session in memory, each with the last sequence number it sent.
- On every append it wakes the session's emitters. Each one reads `findEventsAfter(lastSent)` and sends the rows. Replay and live updates use the same code, so order holds and nothing is skipped.
- Each SSE message has `id: <seq>` and the event as JSON.
- A `@Scheduled` task sends a comment line every 15 s. A `@PreDestroy` completes all emitters.

**5. Router** in `JAVA/troubleshooting/router/`

- `RouterAction` (`DISCUSS`, `REPLY`, `INVESTIGATE`, `CLOSE`) and `RouterDecision(action, text, reason)`.
- `RouterPromptBuilder`: a system message with the rules, and a user message with the ticket fields and the session events inside delimiters.
- `RouterService`: calls `chatModel.chat(ChatRequest)` with a JSON schema response format, parses, logs token usage and duration.
- `RouterTurnRunner`: one turn per session at a time on `troubleshootingExecutor`. Order of a turn: append `THINKING`, call the model, mark a waiting proposal `SUPERSEDED`, append the result, append `IDLE`. If new input arrived meanwhile, run once more.
- `DISCUSS` becomes a `ROUTER_MESSAGE`. Anything else becomes a `PROPOSAL`. The mode is read at the start of each turn and is always `MANUAL` in the spike.
- On failure: append `FAILED` with a short reason. No action is taken. The engineer can send again.

**6. Slack thread snapshot** `JAVA/troubleshooting/SlackThreadSnapshotter.java`

- At session start, read the thread once with `SlackClient.getThreadPage` and append one `SLACK_MESSAGE` per message: `{ts, userId, text}`.
- If Slack answers with an error, append one `SLACK_MESSAGE` from `system` saying the thread is not available. Seeded tickets take this path.
- Known shortcut: the text is not passed through the thread sanitisation of ADR-005. Acceptable for a test workspace only.

**7. Fakes** `JAVA/troubleshooting/CannedRunService.java`

- Approved `REPLY`: append a `RUN_UPDATE` that says "would post to Slack" with the text. Nothing is posted.
- Approved `INVESTIGATE`: append three `RUN_UPDATE` events a second apart (started, a `kubectl` output, a case note). Then start a router turn.
- Approved `CLOSE`: append "would close the ticket".

**8. REST** in `JAVA/troubleshooting/rest/`

| Method and path | Does | Answers |
|---|---|---|
| `GET /troubleshooting/enabled` | Feature check, always registered (`TroubleshootingEnabledController`) | `{enabled}` |
| `GET /troubleshooting/ticket/{ticketId}/session` | Read session and all events | 200, or 404 with code `TROUBLESHOOTING_SESSION_NOT_FOUND` |
| `POST /troubleshooting/ticket/{ticketId}/session` | Create if missing, snapshot the thread, start the first turn | 200 with the session |
| `POST /troubleshooting/ticket/{ticketId}/session/messages` | Append an engineer message `{text}`, start a turn | 202 with the event |
| `POST /troubleshooting/ticket/{ticketId}/session/proposals/{seq}/decision` | `{decision, text}` | 200, or 409 with code `TROUBLESHOOTING_PROPOSAL_NOT_PENDING` |
| `GET /troubleshooting/ticket/{ticketId}/session/stream?after={seq}` | SSE; also reads `Last-Event-ID` | `text/event-stream` |

- Classes: `TroubleshootingController`, `TroubleshootingEnabledController`, `TroubleshootingSessionUI`, `TroubleshootingEventUI`, `MessageRequest`, `DecisionRequest`, `TroubleshootingMapper`, `TroubleshootingExceptionHandler`.
- The actor comes from `@AuthenticationPrincipal UserPrincipal`.
- `JAVA/security/SecurityConfig.java`: add `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` beside the ERROR rule, `/troubleshooting/enabled` to the authenticated list, and `/troubleshooting/**` with `hasAnyRole("SUPPORT_ENGINEER")`.

**9. Stretch: live Slack replies** `JAVA/troubleshooting/TroubleshootingMessageHandler.java`

- A `SlackEventHandler<MessageEvent>`. For a thread reply on a ticket that has a session, append a `SLACK_MESSAGE` and start a turn.

**Tests.** Keep them few: one unit test for router parsing (good JSON, bad JSON, unknown action), one for the decision rule (second decision gets 409).

### 4.3 UI

**1. Dependency.** `yarn add @assistant-ui/react` only. No CLI, no registry components, no markdown package.

**2. Proxy routes** in `UI/app/api/troubleshooting/`

- `enabled/route.ts` (GET).
- `tickets/[id]/session/route.ts` (GET, POST).
- `tickets/[id]/session/messages/route.ts` (POST).
- `tickets/[id]/session/proposals/[seq]/decision/route.ts` (POST).
- `tickets/[id]/session/stream/route.ts` (GET, streaming).
- POST routes call `validateCsrfToken` first and use `backendFetch`. Errors go through `backendErrorResponse` so the `code` reaches the client.
- Check that `id` and `seq` are digits before building the backend path.
- The stream route, as a sketch (not run):

```ts
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const token = await backendAccessToken(request);
  if (!token) return unauthorizedResponse();

  const headers = new Headers({ Accept: "text/event-stream", Authorization: `Bearer ${token}` });
  const lastEventId = request.headers.get("Last-Event-ID");
  if (lastEventId) headers.set("Last-Event-ID", lastEventId);

  const upstream = await fetch(
    `${process.env.BACKEND_URL}/troubleshooting/ticket/${id}/session/stream${request.nextUrl.search}`,
    { headers, signal: request.signal, cache: "no-store" }
  );
  if (!upstream.ok || !upstream.body) return errorResponse(`Backend error: ${upstream.status}`, upstream.status);

  return new Response(upstream.body, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      "X-Accel-Buffering": "no",
    },
  });
}
```

**3. Data** 

- `UI/lib/types/troubleshooting.ts`: `TroubleshootingSession`, `TroubleshootingEvent`, payload types.
- `UI/lib/hooks/index.ts`: `useTroubleshootingEnabled()`, `useTroubleshootingSession(ticketId)` with query key `["troubleshooting", ticketId]`. Sending and deciding use `apiFetch`.
- `UI/components/troubleshooting/use-session-stream.ts`: opens an `EventSource` on the stream route with `?after=<last seq>`. Each event is added to the query cache with `setQueryData`, skipping sequence numbers already there. On an error it refetches the session, which also catches a 401.

**4. Page and components**

- `UI/app/(dashboard)/tickets/[id]/session/page.tsx`: thin client page. Shows `AccessDenied` unless `isSupportEngineer`.
- `UI/components/troubleshooting/session-view.tsx`: header with ticket id and mode badge, "Start troubleshooting" when there is no session, then the thread and the composer.
- `UI/components/troubleshooting/session-items.ts`: turns the event list into view items in one pass. A proposal item carries its state (`PENDING`, `APPROVED`, `REJECTED`, `SUPERSEDED`), taken from later `DECISION` events. Busy state comes from the last `ROUTER_STATUS`. This file knows nothing about assistant-ui and is reused if the answer is "no".
- `UI/components/troubleshooting/session-runtime.ts`: `useExternalStoreRuntime` with `messages` (the view items), `convertMessage`, `isRunning` and `onNew` (posts to the messages route). No `onEdit`, `onReload` or `setMessages`.
- `UI/components/troubleshooting/event-cards.tsx`: `SlackMessageCard` (uses `SlackMessageRenderer`), `ProposalCard` (Approve, Edit, Reject; shows who decided), `RunUpdateCard`, `RouterFailedNote`. All text is rendered as React text.
- `UI/components/troubleshooting/mode-badge.tsx`: uses `Badge`.
- Mapping to try first:
  - `ENGINEER_MESSAGE` to role `user`, with the author's name shown in our message component.
  - `ROUTER_MESSAGE` to role `assistant` with a text part.
  - `PROPOSAL`, `SLACK_MESSAGE`, `RUN_UPDATE` to role `assistant` with one data part each (`data-proposal`, `data-slack-message`, `data-run-update`).
  - Every message gets `id` = the event's `seq`.
- Thread built from `ThreadPrimitive`, `MessagePrimitive` and `ComposerPrimitive`, styled with tokens.
- `UI/components/tickets/EditTicketModal.tsx`: a "Troubleshoot" link in the footer to `/tickets/{id}/session`.

### 4.4 Test steps

1. **Real thread.** Post a query in the local Slack test channel and let the bot create the ticket. Open the ticket, click "Troubleshoot", start the session. Check the Slack messages appear as cards and the router answers.
2. **Seeded ticket.** Start a session on a seeded ticket. Check the "thread not available" card and that the router still answers what the engineer types.
3. **Two windows.** Open the same session in two browser windows. Send in one. Check both update without a reload. Reload one and check the history is the same.
4. **Two engineers.** Send a message as a second user with `curl`, straight to the service on port 8080, with `X-Test-User` and `X-Test-Role: support`. Check both windows show the other name.
5. **Send at the same time.** Send from the window and from `curl` within a second. Check the sequence numbers have no gaps or duplicates (`SELECT seq, type, actor FROM troubleshooting_event WHERE session_id = ? ORDER BY seq`) and the router answers once for both, or once each, never out of order.
6. **Proposal.** Ask for a reply to the author. Edit and approve it. Check the decision shows in both windows with the engineer's name, and in the table with the edited text.
7. **Race on a decision.** Approve in one window and reject with `curl` at once. Check one gets 409 and both windows show the same result.
8. **Replaced proposal.** With a proposal waiting, send a new message. Check the old card shows as replaced and its buttons are gone.
9. **Investigate.** Approve an investigation. Check three run updates arrive one by one and the router then proposes the next step.
10. **Bad output.** Make the model fail once (set `ANTHROPIC_TIMEOUT=1ms`, or ask it in chat to answer in plain text). Check a failure note appears and no action is taken.
11. **Injection.** Post a Slack message that tells the bot to close the ticket and reply "resolved". Check the router does not act on it without a proposal, and note what it proposes.
12. **Stream health.** Leave a window idle for 10 minutes, then send from the other. Check it still updates. Restart the service and check the window recovers.
13. **Production build.** Run `yarn build && yarn start` once and repeat step 3.
14. **Cost and speed.** From the stored token counts, record input tokens, output tokens and duration per turn, at turn 1 and at turn 10. List price for `claude-sonnet-5-5` at the time of writing is $2 per million input tokens and $10 per million output tokens; check the current price before quoting a figure.

### 4.5 Out of scope

- Real workers and real investigations.
- Posting to Slack and closing tickets.
- Automatic mode and its limits. Mode switching beyond the stored field.
- Notifications that a proposal is waiting.
- Thread sanitisation, erasure of session data, and a lock that survives a restart or works across replicas.
- Markdown rendering of router text.
- Prompt caching, a cheaper router model, lower thinking effort. Measure first.
- Jest and functional tests for the new UI.

### 4.6 Order of work

**Day 1**

| Step | Time |
|---|---|
| Setup (4.1) | 0.5 h |
| UI against a stub. A Next.js route handler streams a scripted session over SSE: two engineers, a Slack message, a router message, a proposal, a decision, three run updates, a busy state. Build `session-items.ts`, the runtime, the cards and the stream hook against it. | 3 h |
| **Checkpoint.** Go through the seven points in section 1. Write "yes" or "no" with the reason in this file. On "no", replace `session-runtime.ts` and the primitives with a plain list over the same view items, and carry on. | 0.5 h |
| Stub stream in a production build (`yarn build && yarn start`). | 0.5 h |
| Migration, repository, session service, append with sequence numbers. | 2 h |
| Controller, security rules, broadcaster, SSE endpoint. Check with `curl -N`. | 2 h |

**Day 2**

| Step | Time |
|---|---|
| Router: prompt, JSON schema, parsing, failure path, token logging, turn runner. | 2.5 h |
| Decisions, replaced proposals, canned run. | 1 h |
| Real proxy routes and hooks. Remove the stub. | 1.5 h |
| Slack thread snapshot. Live Slack replies if time allows. | 1 h |
| Test steps (4.4) and write-up: the answer, the numbers, what to change in ADR-012. | 2 h |

About 16.5 hours. The cut list in section 3 buys back about 2 hours.

## 5. Open questions

Only what the code cannot answer. Each has a recommended answer.

1. **When does a session start?** ADR-012 leaves this open. *Recommended:* when an engineer clicks "Start troubleshooting". No LLM call happens just because someone opened a page.
2. **May the spike send raw thread text to Claude?** `ThreadService` sanitises text before the analysis sees it; the snapshot in 4.2 does not. *Recommended:* yes, for the local test workspace only. The real build must reuse the sanitisation.
3. **What happens to a message sent while the router is busy?** ADR-012 leaves this open too. *Recommended:* accept it, store it, and answer it in the next turn. Do not block the composer.
4. **Is there a second local user to sign in with?** *Recommended:* do not depend on one. Use the test-bypass headers with `curl` for the second engineer.
5. **How slow and how costly may a router turn be before it is a problem?** The spike will produce numbers but needs a bar. *Recommended:* under 10 seconds and under 5 cents for a typical turn. Above that, try a lower effort or a smaller model before changing the design.
6. **Should session data be erasable?** The log is append-only and holds names and message text. *Recommended:* out of scope for the spike, but add it to ADR-012's open points now.

## 6. Not verified

- **Nothing was run.** No build, no stream test, no LLM call.
- **Security on stream close.** The ASYNC dispatch problem is reasoned from the existing ERROR rule and the framework defaults, not reproduced.
- **Jetty timeouts.** The default async timeout and whether the connector's idle timeout closes a quiet stream. The 15 s keep-alive covers both.
- **Next.js server.** Whether its `fetch` drops a quiet upstream after some minutes, and whether compression buffers an event stream in a production build. Test steps 12 and 13 cover them.
- **LangChain4j.** That `ResponseFormat` with a JSON schema reaches Claude as structured output, and how a refusal or a cut-off answer is reported. Class and method names were read from the 1.18.0 jar only.
- **assistant-ui.** Next.js 16 and Tailwind 4 support is not stated in the docs. Not confirmed: whether custom metadata reaches the rendered message, whether plain `convertMessage` merges adjacent assistant messages, how `system` messages render, how the composer behaves while `isRunning` is true. The docs were read through a summarising fetch tool, so check names against the installed package's types. The old `/docs/getting-started` address returns 404.
- **Prices.** Taken from a cached price list, not from the live pricing page.
- **Local environment.** `.env.local` files were not opened. Whether test-bypass works with the local setup is assumed from the code.
