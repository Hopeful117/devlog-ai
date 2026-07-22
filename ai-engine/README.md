# DevLog AI Engine

Insight generation is intent-driven. Every accepted submission contains a
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
callback contract, and the ADR-022 structured `INSIGHT_GENERATION` workflow.
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

## LLM configuration

The default provider is deterministic and requires no API key:

```bash
LLM_PROVIDER=mock uvicorn app.main:app --reload
```

OpenAI can be enabled explicitly:

```bash
LLM_PROVIDER=openai \
LLM_MODEL=gpt-4.1-mini \
LLM_API_KEY=... \
uvicorn app.main:app --reload
```

Additional settings are `LLM_TIMEOUT_SECONDS`, `LLM_MAX_OUTPUT_TOKENS`,
`CORE_BASE_URL`, and `CORE_CALLBACK_TIMEOUT_SECONDS`.

The HTTP acknowledgement remains `202 Accepted`. V1 processing uses FastAPI
in-process background tasks and then sends the ADR-020 callback. This executor
is not a durable queue and can lose an in-flight task if the process stops.
