# ADR: AI Gateway LLM Provider

**Date:** 2026-07-27
**Status:** Proposed

---

## Context

Thread analysis is hard-wired to hosted Vertex AI: `LlmConfig` builds a `VertexAiGeminiChatModel` from project, location, and model name, authenticating through Application Default Credentials via workload identity (ADR-002).

Some deployments must route LLM traffic through a central AI gateway instead — an organisational governance and routing boundary that fronts Gemini at

```
{gateway-root}/platform/google-vertex/proxy/v1beta/models/{model}:generateContent
```

and authenticates each request with a Basic `Authorization` header rather than cloud credentials. EL-131 requires the provider to be swappable through configuration: endpoint, model, auth, and timeouts must all be configurable, with no credentials in code or images.

## Decision

### Provider selector

`analysis.llm.provider` (`ANALYSIS_LLM_PROVIDER`) selects `vertex` (default) or `gateway`. Selection is static: changing provider requires a restart. There is no runtime switching and no silent fallback between providers — a misconfigured or failing provider fails loudly.

Provider-neutral settings moved out of the vertex block to common config: `analysis.llm.model-name` and `analysis.llm.request-delay`. Provider-specific settings live under `analysis.llm.vertex.*` (project-id, location) and `analysis.llm.gateway.*` (base-url, basic-auth-token, timeout). The pre-existing `VERTEX_*` environment variables are preserved; new settings use `AI_GATEWAY_BASE_URL`, `AI_GATEWAY_BASIC_AUTH_TOKEN`, and `AI_GATEWAY_TIMEOUT`.

`AnalysisProps` validates fail-fast at startup, and only the selected provider's settings are required: gateway mode needs no GCP project or location and performs no cloud credential discovery; vertex mode needs no gateway secret. The Basic credential is Base64 `username:password`, sourced from secret-backed configuration, redacted from `toString()`, and never logged.

### Gateway client

The gateway exposes the native Gemini REST contract, not the project/location-qualified Vertex API, so gateway mode uses LangChain4j's stable `langchain4j-google-ai-gemini` module (`GoogleAiGeminiChatModel`) — not the Vertex module, whose builder offers neither custom headers nor a timeout.

- `baseUrl` is the full gateway URL **including the `/v1beta` segment**; the client appends `/models/{model}:generateContent`.
- Auth is a single custom `Authorization: Basic <token>` header. No `apiKey` is set, so the client sends no `x-goog-api-key` header.
- `timeout` is configurable (gateway mode only; the Vertex builder has no timeout knob).

### Wiring

`LlmConfig` declares one `@Bean` method per provider with mutually exclusive `@ConditionalOnProperty` conditions on the selector (`matchIfMissing = true` keeps vertex the default when the property is absent), so exactly one `ChatModel` bean ever exists and adding a provider means adding a method, not branching inside one. `LlmAnalysisService` keeps depending on the `ChatModel` abstraction and is provider-agnostic. An unrecognised provider value fails enum binding at startup.

WireMock contract tests pin the gateway wire contract: exact request path, JSON content type, native Gemini `contents`/`parts` body, exactly one Basic Authorization header, and no API-key header.

## Alternatives considered

- **Vertex module with endpoint override** — the beta `langchain4j-vertex-ai-gemini` module cannot set per-request headers or a timeout, speaks the project/location-qualified protocol the gateway does not expose, and drags gRPC plus ADC credential discovery into gateway deployments. Rejected.
- **Runtime provider switching** — confirmed unnecessary on EL-131; restart-to-change keeps wiring simple. Rejected.

## Consequences

- Existing deployments need no configuration change and retain hosted Vertex behaviour.
- Gateway deployments configure provider, base URL, model, secret, and timeout only; the Helm chart's existing env/envFrom mechanism suffices for the new variables.
- Property paths moved from `analysis.vertex.*` to `analysis.llm.*`; nothing outside the service module referenced the old paths, and the environment-variable interface is backward-compatible.
- The functional-test profile continues to construct the vertex bean with a dummy project id, shadowed by the `@Primary` fake model; gateway mode is exercised by contract tests instead.
