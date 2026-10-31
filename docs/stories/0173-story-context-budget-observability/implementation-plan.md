# Implementation Plan

## Overview

Implement bounded budget accounting and provider-failure diagnostics for Story
Context executions without changing the Java/Core authority boundary or raising
production limits. Core will create the authoritative execution accounting and
perform a provider-call authorization preflight. The AI Engine will normalize provider outcomes
and return bounded diagnostics through a versioned or explicitly additive Story
Context contract.

Unknown model capacity will reject the execution before provider invocation with
`BUDGET_CAPACITY_UNKNOWN`. Provider-specific incomplete responses remain terminal
provider failures and are never parsed or persisted as successful output.

The immutable Core execution snapshot remains an audit/grounding artifact. It is
not treated as normal diagnostic logging; normal and short-lived diagnostic
traces retain only their approved bounded fields.

## Governing Constraints

- Core remains authoritative for context construction, selection, trust,
  grounding, identity and authoritative validation.
- Python receives authorized context and performs probabilistic generation plus
  defensive validation only.
- No Python-side repository access, retrieval, RAG, autonomous retry or context
  expansion is introduced.
- `truncated=true` is distinct from provider failure.
- `response.incomplete` is terminal for the current execution.
- No credentials, authorization headers, full prompts, repository payloads or raw
  provider output are added to normal diagnostics.
- No production context/output limit is increased by this Story.
- Every provider call has one Core-owned preflight outcome: `AUTHORIZED`,
  `PROJECTION_REDUCED` or `REJECTED`.
- `PROJECTION_REDUCED` may only reduce the already authorized canonical context;
  it must not trigger retrieval, a second semantic selection or Python-side
  context recovery.

## Planned Changes

### 1. Define the bounded accounting and diagnostic contract

Components: `devlog-contracts`, backend Story Context protocol, AI Engine schemas.

- Define the normalized execution phase, failure category, budget layer,
  measurement unit and certainty status (`EXACT`, `ESTIMATED`, `UNKNOWN`).
- Represent unavailable provider-specific values as `UNKNOWN`; never infer usage
  from `max_output_tokens`.
- Add a versioned Story Context envelope, or an additive envelope accepted by
  both sides, rather than adding fields silently to strict shared contracts.
- Define capability negotiation/downgrade behavior for old/new combinations:
  old/old remains unchanged, new/new exchanges diagnostics, new/old fails closed
  with a version diagnostic, and new AI Engine downgrades when communicating
  with old Core.
- Keep provider-specific response details inside AI Engine adapters; Core receives
  only the bounded normalized projection.

Likely areas:

- `devlog-contracts/src/main/java/.../storycontextagent/`
- `backend/src/main/java/.../storycontextanalysis/controller/`
- `backend/src/main/java/.../storycontextanalysis/usecase/`
- `ai-engine/app/schemas/`

### 2. Add Core selection accounting and immutable snapshot fields

Components: Story Context preparation/submission and persisted task snapshot.

- Carry candidate, selected, discarded, selection budget, effective usage,
  warning and `truncated` values from the deterministic selection result.
- Measure serialized projection/prompt inputs only where the measurement method
  is reliable, recording unit and certainty status.
- Preserve context, projection, grounding and submission digests as authoritative
  identities.
- Store the accounting needed for audit/grounding in the existing immutable
  execution snapshot without moving raw prompts or provider output into normal
  diagnostics.
- Add preflight before provider invocation. Reject missing/unsafe model capacity
  with `BUDGET_CAPACITY_UNKNOWN` and an actionable non-sensitive caller error.
- Reject an insufficient effective output allowance before provider invocation
  with a bounded diagnostic rather than relying on provider truncation.
- Do not silently increase any configured selection or output limit.

Likely files:

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextPreparationService.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/PreparedStoryContext.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextAgentProjectionV1.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/entity/StoryContextAnalysis.java`

### 3. Extend bounded Core interaction traces

Components: Core trace request, persistence and query projection.

- Add only normalized, bounded fields required by the approved contract:
  execution phase, failure category, provider status/incomplete reason, effective
  output limit and budget accounting references.
- Preserve strict validation and maximum lengths for all diagnostic strings.
- Keep existing prompt fields subject to their current diagnostic retention policy.
- Ensure the immutable execution snapshot and interaction trace have separate
  lifecycle/retention semantics in persistence and documentation.
- Make trace persistence idempotent and correlation-safe for preflight failures,
  provider failures and successful executions.

Likely files:

- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiInteractionTraceRequest.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/interactiontrace/entity/AiInteractionTrace.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/interactiontrace/service/AiInteractionTracePersistenceService.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/interactiontrace/dto/AiInteractionTraceResponse.java`
- related backend interaction-trace tests

### 4. Normalize AI Engine provider outcomes

Components: provider base result, provider adapters, task processing and trace
service.

- Extend the internal provider result/error boundary so adapters can expose
  bounded status, incomplete reason and usage metadata without raw response
  content.
- Normalize mock, OpenAI-compatible, Anthropic, Gemini, OpenRouter and ChatGPT
  behavior where those adapters are supported by the repository.
- Preserve `UNKNOWN` for fields unavailable from a provider.
- Map ChatGPT `response.incomplete` and reason `max_output_tokens` to a terminal
  normalized provider failure; do not parse, retry or persist its partial output.
- Keep credentials and authorization material outside diagnostics.
- Emit the versioned/additive callback payload accepted by Core, with explicit
  downgrade behavior when the peer does not support the new envelope.

Likely files:

- `ai-engine/app/providers/base.py`
- `ai-engine/app/providers/chatgpt.py`
- `ai-engine/app/providers/openai.py`
- `ai-engine/app/providers/anthropic.py`
- `ai-engine/app/providers/gemini.py`
- `ai-engine/app/providers/openrouter.py`
- `ai-engine/app/providers/mock.py`
- `ai-engine/app/schemas/interaction_trace.py`
- `ai-engine/app/services/interaction_trace.py`
- `ai-engine/app/services/task_processing_service.py`

### 5. Add bounded metrics and caller-facing diagnostics

Components: Core Story Context metrics/callback facade and AI Engine task result
mapping.

- Add fixed or bounded labels for selection truncation, preflight rejection,
  provider incomplete, provider transport failure, parsing failure and
  validation failure.
- Ensure metrics do not include project identifiers, prompts, provider response
  text or unbounded model/provider labels.
- Preserve the existing task, correlation, context digest and projection digest
  linkage for operator diagnosis.
- Expose a stable, non-sensitive caller error that identifies the terminal phase
  and remediation category without leaking provider content.

Likely files:

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/service/StoryContextAgentMetrics.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/service/StoryContextAgentCallbackFacade.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextCallbackService.java`
- corresponding AI task result/error schemas and mappers

## Test Plan

### Backend tests

- Selection accounting exposes candidate/selected/discarded/budget/usage/warning
  and keeps Core truncation distinct from provider failure.
- Unknown model capacity rejects before `AIEngineClient.submit` and records
  `BUDGET_CAPACITY_UNKNOWN`.
- Exact, estimated and unknown measurements preserve unit and certainty status.
- Snapshot fields remain available for grounding/audit while normal diagnostics
  remain redacted and bounded.
- Trace persistence covers preflight, provider incomplete, parsing, validation
  and transport terminal phases.
- Contract tests cover old/old, new/new, new/old and old/new compatibility.
- Metrics labels remain fixed/bounded and caller errors contain no sensitive
  content.

Likely existing test areas:

- `StoryContextSubmissionServiceTest`
- `StoryContextAgentCallbackFacadeTest`
- `StoryContextAnalysisQueryServiceTest`
- `StoryContextAgentProtocolV1Test`
- interaction-trace persistence tests

### AI Engine tests

- Deterministic fixture for ChatGPT-style `response.incomplete` with
  `max_output_tokens` produces a terminal normalized failure and no successful
  parsed result.
- Provider adapters expose common diagnostic fields and `UNKNOWN` fallback
  behavior without network access.
- Mock fixtures cover complete, truncated-context, unknown-capacity and provider
  incomplete paths.
- Strict schema validation accepts only the negotiated contract and does not
  silently discard unsupported fields.
- Redaction tests prove tokens, authorization headers, prompts, raw provider
  output and repository content do not enter normal diagnostics.

Likely test areas:

- `ai-engine/tests/test_llm_providers.py`
- `ai-engine/tests/test_chatgpt_provider.py`
- interaction-trace and task-processing tests

### Optional live validation

Document a separately marked procedure for reproducing Core truncation and a
provider `max_output_tokens` interruption with correlation identities. It must
not require live credentials in CI and must not be the only acceptance evidence.

## Validation Commands

Focused first:

```bash
cd ai-engine && python -m pytest tests/test_llm_providers.py tests/test_chatgpt_provider.py -q
./backend/mvnw -pl backend test -Dtest=StoryContextSubmissionServiceTest,StoryContextAgentCallbackFacadeTest,StoryContextAgentProtocolV1Test
```

Broader validation:

```bash
cd ai-engine && python -m pytest -q
./backend/mvnw -pl backend -am clean verify -B
git diff --check
```

If the contract is implemented in `devlog-contracts`, run its focused module
tests before the backend suite. Do not use provider credentials or modify OAuth,
MCP transport or delegation configuration as part of default validation.

## Documentation Reconciliation

After implementation, update only the canonical operator/runtime documentation
needed to describe the final diagnostic fields, failure categories, safe
reproduction procedure and retention rules. Do not document speculative fields
before the runtime contract exists.

## Risks and Mitigations

- **Strict contract breakage:** introduce the negotiated/versioned envelope and
  execute the four compatibility matrix tests before rollout.
- **Unsafe preflight estimates:** record certainty and reject unknown capacity;
  estimates authorize calls only when policy proves them safe.
- **Sensitive diagnostic persistence:** keep raw provider details at adapter
  boundaries, use allow-listed normalized fields and test redaction.
- **Provider divergence:** normalize common fields and retain `UNKNOWN` rather
  than fabricating provider semantics.
- **Over-rejection:** keep budget tuning out of this Story and measure rejected
  executions separately before any later policy change.
- **Snapshot/trace confusion:** preserve separate retention and access semantics
  and test both paths explicitly.

## Files Summary

Expected affected surfaces are the existing Story Context/Core protocol,
interaction-trace persistence, AI Engine provider base/adapters, schemas,
task-processing services, tests and canonical runtime documentation. No database
migration, new retrieval engine, OAuth/MCP transport change or trusted-knowledge
promotion is planned unless implementation discovers an existing contract
requires a narrowly scoped additive migration; such a discovery must stop for
human guidance rather than be silently expanded.

## Readiness

The plan is complete for human review. Implementation is not authorized until
this Implementation Plan receives explicit approval.
