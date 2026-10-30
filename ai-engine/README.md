# DevLog AI Engine

Insight and Engineering Event generation are intent-driven. Every accepted submission contains a
provider-independent `PromptRequest` with a versioned catalog Intent and an immutable `AnalysisContext`; arbitrary user
prompts are not accepted. The prompt builder only supports registered template
versions and validates generated Insight categories against the Intent before
returning proposals for human validation.

Submissions may include structured User Guidance (`focus`, `audience`,
`levelOfDetail`, `writingStyle`, `outputContext`, and ordered `priorities`). It is
validated as a closed schema and inserted into the prompt at the lowest priority:
Intent and AnalysisContext constraints always win.

The Prompt Builder converts this contract into an immutable, provider-agnostic
`Prompt` containing separate system and user messages, the expected output
schema, a generation policy, traceability metadata, and a deterministic SHA-256
content digest. Providers only adapt that Prompt to their external API. Prompt
versions, digests, provider names, and model identifiers are returned to the
Core for persistence; complete rendered prompts are never written to standard
logs.

Prompt Version and Prompt Digest have distinct roles. The human-readable,
semantic version governs the template contract; the SHA-256 digest identifies
the normalized system message, user message, and expected schema actually sent
to the provider. Historical AI Tasks persist both values.

FastAPI service responsible for the AI-processing boundary of DevLog AI.

The service implements health reporting, ADR-019 task acceptance, the ADR-020
callback contract, the ADR-022 structured `INSIGHT_GENERATION` workflow, and the dedicated
`EVENT_PROPOSAL_GENERATION` workflow. Event prompts require Core's bounded first-parent evolution
context and allow zero output when the selected evidence is insufficient.
It interprets only the immutable `AnalysisContext` supplied by the Java Core.

## Local development

```bash
python -m pip install -e ".[dev]"
uvicorn app.main:app --reload
pytest
```

## Endpoints

- `GET /health`
- `POST /api/v1/ai/tasks`
- `POST /api/v1/deliverables/generate`
- `GET /api/v1/auth/chatgpt/start` and `/callback`
- `GET /api/v1/auth/chatgpt/status` and `POST /disconnect`

The Deliverable endpoint implements ADR-034. Its closed request schema accepts only validated
Insight snapshots plus communication parameters. It does not accept repository data, Facts,
Observations, AnalysisContext, or pending proposals. The versioned
`deliverable-generation-prompt-v1` template returns a single structured title/content result and
records provider/model and prompt digest metadata for Core persistence.

## LLM configuration

The default provider is deterministic and requires no API key:

```bash
LLM_PROVIDER=mock uvicorn app.main:app --reload
```

OpenAI can be enabled explicitly:

```bash
LLM_PROVIDER=openai \
LLM_MODEL=gpt-5.6-luna \
LLM_API_KEY=... \
uvicorn app.main:app --reload
```

Additional settings are `LLM_TIMEOUT_SECONDS`, `LLM_MAX_OUTPUT_TOKENS`,
`CORE_BASE_URL`, and `CORE_CALLBACK_TIMEOUT_SECONDS`.

Sign in with ChatGPT is opt-in with `LLM_PROVIDER=openai_chatgpt` and requires
the existing OAuth encryption key, redirect URI, issuer/JWKS settings, and
`CHATGPT_OAUTH_ENABLED=true` plus `DEVLOG_SERVICE_AUTH_SECRET`. OAuth access, refresh, and ID tokens remain
encrypted in the AI Engine. Core receives only an opaque credential handle,
the non-secret subject, granted scopes, and a bounded runtime state. The
catalog product id `openai_chatgpt` is explicitly mapped to the internal
credential id `chatgpt`; there is no provider or credential fallback.
Core starts the flow with a short-lived, single-use principal-bound intent. The
AI Engine validates the HMAC-signed opaque intent before creating its OAuth
transaction. After the provider callback, the AI Engine returns a one-time
completion code; an authenticated Core principal must submit that code to
complete the association. Core then redeems and acknowledges the completion
using replay-protected service authentication. OAuth tokens and authorization
 codes never enter the URL or callback.

Anthropic API-key enrollment is a separate flow at `POST /api/v1/auth/api-key/enroll`.
Core creates a short-lived HMAC-signed intent from
`POST /api/v1/ai/configuration/api-key/anthropic/start`; the client posts the key directly to
the AI Engine with that intent and then sends only the returned opaque handle to Core's
completion endpoint. The AI Engine performs one bounded Anthropic validation call, stores the
key as an encrypted `API_KEY` credential, and never returns or logs the key. A handle is
unusable without a Core-issued task binding. Replacing or revoking an association invalidates
the old handle and future bindings; there is no fallback to OAuth or `ANTHROPIC_API_KEY` for a
configured user association. Set `DEVLOG_CREDENTIAL_ENCRYPTION_KEY` and
`DEVLOG_SERVICE_AUTH_SECRET`; credentials are stored in the SQLite file configured by
`DEVLOG_CREDENTIAL_STORE_PATH`.

Set `ANTHROPIC_USER_CREDENTIALS=true` for task execution that must resolve the
user-configured Anthropic credential through the Core-issued binding. This mode fails closed
when the binding or association is absent and does not use the process-level API key.

The HTTP acknowledgement remains `202 Accepted`. V1 processing uses FastAPI
in-process background tasks and then sends the ADR-020 callback. This executor
is not a durable queue and can lose an in-flight task if the process stops.
