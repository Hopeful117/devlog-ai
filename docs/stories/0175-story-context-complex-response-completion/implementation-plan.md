# Implementation Plan - Story 0175

## Status

Implementation Plan completed. Human approval is required before implementation.
No production implementation is authorized by this artifact.

## Objective

Add a Core-owned, measured completion policy for complex Story Context
responses. The policy must ensure that the output allowance authorized by Core
is exactly the allowance sent by the AI Engine, reject requests that cannot be
shown to have a supported completion path, and preserve provider-incomplete
responses as terminal failures when only the provider can report them.

The implementation must not begin by increasing the global
`LLM_MAX_OUTPUT_TOKENS` default.

## Governing Constraints

- Java/Core remains authoritative for context, scope, grounding, policy and
  provider-call authorization.
- MCP and REST remain thin adapters over the same Core capability.
- Python performs provider invocation and defensive validation only.
- Incomplete output is never parsed, retried as valid, persisted as successful
  analysis or promoted to trusted knowledge.
- `truncated=true`, policy rejection and provider interruption remain distinct.
- No credentials, prompts, repository payloads or raw provider output enter
  normal diagnostics.
- Any production budget change requires measured completion, latency, cost and
  quality evidence and separate human review.
- New strict-contract fields must use an additive/versioned compatibility
  mechanism.

## Proposed Implementation Sequence

### 1. Establish the effective allowance contract

Inspect and test the complete value path:

```text
Core provider configuration
  -> AiTask authorization binding
  -> PromptRequest.allowedParameters
  -> AI Engine Prompt.generation_parameters
  -> provider request body
```

Add a bounded, explicit policy identity and effective output allowance to the
existing Story Context accounting only if the current additive contract can
carry them safely. Otherwise introduce the smallest versioned envelope needed
for Story Context executions.

Requirements:

- exactly one effective `maxOutputTokens` value per execution;
- no fallback to the AI Engine default when Core supplied an allowance;
- missing, altered, unsupported or out-of-range values fail closed;
- the value is traceable through task, context and projection identities;
- provider adapters represent unavailable values as `UNKNOWN`.

Likely areas:

- `backend/src/main/java/.../aicatalog/`
- `backend/src/main/java/.../aiuserconfiguration/`
- `backend/src/main/java/.../storycontextanalysis/usecase/`
- `backend/src/main/java/.../ai/engine/dto/PromptRequest.java`
- `ai-engine/app/schemas/ai_task.py`
- `ai-engine/app/prompts/story_context_analysis.py`
- `ai-engine/app/providers/`

### 2. Define the bounded completion policy

Implement a deterministic Core policy that classifies the request using only
approved bounded inputs, such as:

- Story Context intent;
- response schema version and bounded schema complexity;
- guidance flags and bounded requested scope;
- canonical projection accounting and truncation state;
- provider/model capability catalog;
- effective output allowance and its certainty.

The policy must produce a bounded outcome and policy version. It must not infer
semantic answer quality or perform a second retrieval. It must reject when a
required policy input or provider capability is unknown.

The Implementation must resolve the precise policy formula in code and tests;
this plan does not authorize a global output-limit change.

Likely areas:

- `StoryContextBudgetPreflight.java` or a narrowly scoped completion-policy
  collaborator;
- provider/model catalog records;
- Story Context snapshot and diagnostic accounting;
- relevant backend configuration validation.

### 3. Apply policy consistently to all Story Context submissions

Use one Core policy path for:

- MCP Story Agent requests;
- REST Story Context requests;
- broad requests;
- targeted requests;
- deliberately reduced projections;
- follow-up executions, if they submit Story Context provider calls.

The policy must run before `AIEngineClient.submit`. A rejected task must be
recorded with a bounded failure code and must not invoke the provider.

Likely areas:

- `StoryContextSubmissionService.java`;
- `StoryAgentFollowUpService.java`;
- `AnalyzeStoryContextUseCase.java`;
- REST and MCP controller/adapter tests;
- callback and task failure mapping.

### 4. Enforce exact propagation in AI Engine adapters

Add or strengthen tests and minimal implementation guards so that the AI Engine
uses the Core-authorized `maxOutputTokens` value for supported Story Context
requests. It must not substitute the configured provider default when an
authorized value is present.

For ChatGPT:

- preserve bounded handling of `response.incomplete`;
- preserve `max_output_tokens` reason normalization;
- do not parse accumulated partial output;
- retain terminal status and bounded reason in the existing trace contract.

For other supported providers, preserve the common contract and explicit
`UNKNOWN` fallback where provider semantics differ.

Likely areas:

- `ai-engine/app/providers/chatgpt.py`;
- `ai-engine/app/providers/openai.py`;
- `ai-engine/app/providers/anthropic.py`;
- `ai-engine/app/providers/gemini.py`;
- `ai-engine/app/providers/openrouter.py`;
- `ai-engine/app/services/interaction_trace.py`;
- provider and schema tests.

### 5. Add completion and rejection evidence

Add deterministic fixtures for:

- policy authorization with an effective allowance;
- policy rejection before provider invocation;
- complete representative complex structured output;
- ChatGPT provider incomplete response;
- altered/missing allowance;
- unknown capacity or policy input;
- broad, targeted and reduced projections.

Add a separately marked live or replayable procedure using one task ID,
context digest and projection digest. It must record completion, latency, cost
and quality evidence without exposing credentials or raw content.

### 6. Documentation reconciliation

Update only canonical operator/runtime documentation required by the final
implementation, including:

- policy rejection versus provider interruption;
- bounded diagnostic fields;
- safe reproduction and correlation procedure;
- configuration ownership and retention boundaries.

Do not document a budget increase unless the implementation produces the
required measurements and the change receives separate human review.

## Contract Compatibility

Before implementation changes strict contracts, test and document:

| Core | AI Engine | Required behavior |
|---|---|---|
| old | old | Existing Story Context behavior remains valid |
| new | new | Completion-policy accounting is exchanged |
| new | old | Unsupported execution fails closed with a version diagnostic |
| old | new | AI Engine does not invent Core policy or send unsupported fields |

The implementation must not silently discard new fields under Pydantic
`extra="forbid"` or equivalent Core validation.

## Test Plan

### Backend

- focused completion-policy unit tests;
- exact effective-allowance propagation tests;
- pre-provider rejection tests proving `AIEngineClient.submit` is not called;
- broad, targeted and reduced projection policy tests;
- MCP/REST transport-parity tests;
- follow-up execution coverage;
- task snapshot and bounded diagnostic assertions;
- mixed-version contract tests;
- full relevant backend module suite.

### AI Engine

- PromptRequest allowed-parameter validation;
- exact provider request-body propagation;
- missing/altered allowance failure behavior;
- complete structured complex fixture;
- ChatGPT `response.incomplete(max_output_tokens)` terminal behavior;
- no partial-result parsing or callback success;
- supported-provider common diagnostic behavior;
- full relevant pytest suite when the dependency is available.

### Runtime and evaluation

- deterministic credential-free policy fixtures;
- replayable or explicitly environment-dependent complex-request procedure;
- correlation of task, context and projection identities;
- redaction and bounded-size checks;
- `git diff --check`.

## Validation Commands

Focused first:

```bash
./backend/mvnw -pl backend test -Dtest=StoryContextBudgetPreflightTest,StoryContextSubmissionServiceTest,AnalyzeStoryContextUseCaseTest -B
cd ai-engine && python -m pytest tests/test_chatgpt_provider.py tests/test_v3_protocol.py -q
```

Broader validation:

```bash
./backend/mvnw -pl backend -am clean verify -B
./backend/mvnw -pl mcp-server test -B
cd ai-engine && python -m pytest -q
git diff --check
```

If `pytest` is unavailable, report the blocked Python checks explicitly and run
the available syntax and deterministic checks; do not claim behavioral coverage
without execution.

## Rollout and Configuration Safety

- Do not change `LLM_MAX_OUTPUT_TOKENS` globally as part of instrumentation.
- Treat any new per-intent or per-model allowance as a separately reviewable
  policy configuration.
- Preserve fail-closed behavior for unknown capacity or unsupported policy.
- Keep rollout additive and observable before considering any production policy
  change.

## Open Decisions for Implementation

1. Select the deterministic complexity inputs and exact completion-policy
   formula.
2. Decide whether the first supported complex policy uses an existing allowance
   or a separately configured allowance.
3. Select policy identity and measurement-certainty fields.
4. Define the bounded failure codes and caller-facing messages.
5. Define the replay/evaluation fixture and quality measurement method.

These decisions are implementation details within Story scope. They must not
alter the Core/Python authority boundary or trust model.

## Readiness

The Implementation Plan is complete and ready for human review. Implementation
is not authorized until this plan receives explicit human approval.
