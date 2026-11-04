# Repository Analysis - Story 0176

## Status

Repository Analysis completed. Human approval is required before producing the
Implementation Plan. No production implementation is authorized by this
artifact.

## Story Scope

Story 0176 addresses the remaining Story Context reliability and latency gap
after Story 0175. Story 0175 established the Core-owned completion allowance
for complex responses and the current live runtime now completes recent
`8192`-token executions. The remaining measured problem is the grounding
validation path and its corrective retry cost.

The intended outcome is not to weaken grounding or remove retries blindly. The
work must first make the failure taxonomy and retry economics observable, then
use deterministic and replayable evidence to evaluate prompt alignment and any
bounded retry-policy change.

## Repository and DevLog Context

- Repository: `devlog-ai`
- Branch: `main`
- HEAD at analysis: `30f49aead17d997f497023a060bf9a423b0c08cf`
- Story: `0176-story-context-grounding-retry-efficiency`
- Governing ADR: `ADR-079`
- Related Stories: `0173`, `0175`

The DevLog applicability check completed with a truncated projection at the
current repository revision. It retained only file-level summaries for
adjacent budget and tracing work and did not provide reliable implementation
details for Story 0176. The repository is therefore authoritative for the
current physical implementation. No Obsidian vault context was required.

## Measured Runtime Evidence

The live PostgreSQL trace sample for the previous 24 hours contained 78 Story
Context traces:

| Outcome | Count | Average duration | P95 duration |
|---|---:|---:|---:|
| `SUCCEEDED` | 37 | 13.2 s | 26.5 s |
| `GROUNDING_ERROR` | 39 | 28.5 s | 44.5 s |
| `PROVIDER_INCOMPLETE` | 2 | 25.3 s | 25.4 s |

The recent live executions after the Story 0175 policy change all completed
with `8192` authorized output tokens. Eight recent completed tasks ranged from
12.1 s to 21.6 s. This supports the conclusion that the current dominant
avoidable cost is the failed initial generation plus corrective retry, not the
previous `max_output_tokens` allowance alone.

The current traces show empty `validation_diagnostics` for the observed
`GROUNDING_ERROR` rows. This prevents reliable attribution to a specific
validator rule. Token usage is also frequently unavailable, so prompt-size and
provider-output hypotheses cannot yet be separated by exact provider usage.

## Current Execution Path

```text
MCP or REST Story Context request
  -> Java/Core projection and grounding contract
  -> Core budget preflight
  -> AI Engine prompt construction
  -> provider structured generation
  -> Pydantic output parsing
  -> Story Context grounding/semantic validation
  -> corrective retry for validation failures
  -> interaction traces and Core callback
```

MCP and REST delegate to the same Core Story Context capability. The MCP tool
polls the task every 2 seconds, but Core submission is fast and the provider
generation dominates observed duration. Polling can add up to roughly 2 seconds
of caller-visible tail latency, but it is not the primary failure or latency
cause established by this investigation.

## Current Implementation Findings

### Retry Boundary

`ai-engine/app/services/story_context_analysis_generation_service.py` catches
validation failures from `_generate_and_validate`, constructs a corrective
prompt, and invokes `_generate_and_validate` once more. A failed retry is sent
to Core as `INVALID_LLM_OUTPUT`.

`InteractionTraceCollector` records the initial attempt and retry as separate
traces. It sets `interactionType` to `INITIAL_GENERATION` or
`CORRECTIVE_RETRY`, records duration and failure category, and preserves the
retry reason on the retry trace.

The retry is currently broad: all `ValidationError`,
`StoryContextAnalysisOutputValidationError` and `ValueError` failures enter the
corrective path. The Story does not yet have evidence to determine which
grounding failures are recoverable and which are deterministic contract
failures that should fail without a second provider turn.

### Grounding Validation

The validator in
`ai-engine/app/services/story_context_analysis_generation_service.py` checks:

- the versioned typed grounding contract;
- repository evidence type and project-revision scope;
- equality of `ref`, `coreReference` and `taskReference`;
- requested-file grounding;
- causal evidence references, roles and defensible classifications;
- finding references against the authorized allow-list;
- finding classification and relation type.

The existing exception subclasses already distinguish at least
`GROUNDING_ERROR` and `SEMANTIC_SUPPORT_ERROR`, but the persisted trace only
has one broad `failureCategory` field and the normal persistence path does not
retain the detailed validation message.

### Interaction Trace Contract

The AI Engine schema and Java callback DTO already carry:

- `validationStatus`;
- `failureCategory`;
- `retryReason`;
- `validationDiagnostics`;
- `executionPhase`;
- provider status and incomplete reason;
- token measurements and measurement certainty.

`InteractionTraceCollector` creates `validationDiagnostics` from the bounded
failure message. However,
`AiInteractionTracePersistenceService` only persists `validationDiagnostics`
when the configured trace level is `DIAGNOSTIC`. This explains why normal
production traces have a failure category but no actionable grounding detail.

Any change must preserve the redaction and retention boundary. The likely
minimal direction is a bounded taxonomy/code in normal traces, while keeping
the potentially sensitive detailed message diagnostic-only.

### Prompt Contract

`ai-engine/app/prompts/story_context_analysis.py` builds a Core-authorized
projection prompt containing the grounding contract, selected knowledge,
output schema, truncation rules and grounding instructions.

The current working tree also contains an uncommitted `REQUESTED FILE SCOPE`
prompt section and its test/scenario updates. Those changes predate this
Repository Analysis and are not treated as approved Story 0176 implementation.
They are relevant evaluation input only. The Story implementation must decide,
through the approved plan, whether that behavior is retained, revised or
excluded.

The prompt already instructs the model to use exact authorized references and
to abstain when evidence is insufficient. The remaining question is whether
the model receives a sufficiently direct mapping between requested files and
authorized grounding references, and whether the large projection causes the
model to cite broad but unauthorized-for-scope evidence.

## Affected Components

### Primary

- `ai-engine/app/services/story_context_analysis_generation_service.py`
  - validation categories, retry eligibility and bounded failure reporting.
- `ai-engine/app/services/interaction_trace.py`
  - attempt accounting, retry outcome and diagnostics.
- `ai-engine/app/schemas/interaction_trace.py`
  - additive trace fields if the existing contract is insufficient.
- `ai-engine/app/prompts/story_context_analysis.py`
  - grounding instructions and requested-file scope representation.
- `ai-engine/tests/test_story_context_analysis_prompt.py`
  - prompt and scope behavior.
- Existing Story Context and provider tests under `ai-engine/tests/`.

### Secondary

- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiInteractionTraceRequest.java`
  - callback contract compatibility.
- `backend/src/main/java/com/hopeful117/devlogai/ai/interactiontrace/service/AiInteractionTracePersistenceService.java`
  - bounded normal-trace persistence behavior.
- `backend/src/main/java/com/hopeful117/devlogai/ai/interactiontrace/entity/AiInteractionTrace.java`
  - persistence mapping if new additive fields are required.
- `backend/src/main/resources/db/migration/`
  - only if existing columns cannot represent the bounded taxonomy.
- Story Context callback, persistence and transport-parity tests.
- Canonical operator documentation if diagnostic semantics change.

No MCP transport change is currently justified. The 2-second polling interval
is a secondary optimization and must not become a workaround for provider or
grounding failures.

## Architectural Constraints

- Java/Core remains the sole authority for project scope, selection, grounding,
  trust and provider-call authorization.
- Python may classify and validate provider output defensively but must not
  reconstruct context or authorize broader grounding.
- MCP and REST remain thin adapters over the same Core capability.
- Incomplete provider output remains terminal and is never parsed or retried as
  valid output.
- AI output remains untrusted and is not promoted directly to trusted
  knowledge.
- Prompt/context reduction must preserve projection identity, grounding
  references and Core authorization.
- No production budget increase is part of this Story.

## Required Implementation Decisions

The Implementation Plan must resolve these questions using evidence rather than
assumption:

1. Which bounded grounding categories are persisted in normal traces, and which
   details remain diagnostic-only?
2. Which validation failures are deterministic and should fail without a
   corrective retry?
3. Which failures remain eligible for one corrective retry, and how is retry
   success measured?
4. Does the requested-file scope prompt change reduce grounding failures
   without reducing valid evidence coverage?
5. Which prompt/context measurements are exact, estimated or unavailable for
   each provider?
6. Can the current additive callback contract carry the required fields, or is
   a versioned contract change required?

These decisions must not weaken the existing validators merely to improve the
success rate.

## Validation Plan Inputs

The later Implementation Plan should include:

- deterministic fixtures for each grounding taxonomy category;
- first-attempt success, retry success and retry failure cases;
- requested-file targeted, broad and reduced projection cases;
- unknown-reference and relation-type rejection cases;
- causal grounding and semantic-support cases;
- provider-incomplete regression coverage from Story 0175;
- normal-trace redaction and bounded-size assertions;
- callback and persistence compatibility tests;
- REST/MCP transport parity;
- replayable measurements of latency, provider calls, token usage and quality;
- full backend and AI Engine validation when dependencies are available.

## Risks and Unknowns

- The detailed distribution of grounding failure rules is currently unknown
  because normal traces discard the detailed message.
- Provider token usage is unavailable for many current traces.
- Prompt token estimates may not match provider tokenization.
- A narrower prompt may reduce errors by omitting useful evidence rather than
  improving model grounding.
- Eliminating retries may reduce latency while lowering recoverable success.
- The existing uncommitted prompt changes may overlap this Story and must be
  reconciled before implementation planning.

## Analysis Result

Story 0176 is technically well-scoped and can proceed to an Implementation Plan
after human approval of this Repository Analysis. The highest-value first step
is bounded failure taxonomy and retry accounting, because current data cannot
yet prove which prompt change or retry-policy change will improve correctness.

No implementation files were modified for this analysis. Pre-existing
uncommitted files were not reverted or included.

## Next Gate

`WAITING_FOR_ANALYSIS_APPROVAL`

Human approval of this Repository Analysis is required before producing the
Implementation Plan.
