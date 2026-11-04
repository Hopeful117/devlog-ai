# Implementation Plan - Story 0176

## Status

Implementation Plan completed. Human approval is required before implementation.
No production implementation is authorized by this artifact.

## Objective

Reduce avoidable Story Context grounding failures and corrective-retry latency
while preserving Core authority, strict grounding validation and terminal
provider-incomplete behavior.

The implementation will first make the failure distribution measurable, then
apply only bounded prompt or retry changes supported by deterministic and
replayable evidence. It will not increase production output or context budgets.

## Governing Constraints

- Java/Core remains authoritative for scope, selection, grounding, budget policy
  and provider-call authorization.
- Python performs provider invocation and defensive output validation only.
- MCP and REST use the same Core capability and cannot receive transport-specific
  grounding or retry behavior.
- Incomplete provider output remains terminal and is never parsed or retried as
  valid output.
- AI output remains untrusted.
- Normal diagnostics must not contain credentials, authorization headers, full
  prompts, repository payloads or raw provider output.
- Existing Story 0175 completion-policy propagation remains unchanged.
- Existing validator correctness must not be weakened to improve the success
  rate.
- The pre-existing uncommitted prompt/scenario/test changes must be reconciled
  explicitly and must not be silently included as Story 0176 implementation.

## Proposed Implementation Sequence

### 1. Establish a bounded grounding-failure taxonomy

Introduce stable bounded failure codes at the validation boundary instead of
deriving categories from free-form exception text.

Proposed categories:

```text
GROUNDING_UNKNOWN_REFERENCE
GROUNDING_REQUESTED_FILE_SCOPE
GROUNDING_RELATION_TYPE
GROUNDING_CAUSAL_SUPPORT
GROUNDING_CONTRACT_INVALID
GROUNDING_MAPPING_INVALID
GROUNDING_OTHER
SEMANTIC_SUPPORT_ERROR
```

The exact names may be adjusted to repository conventions, but each validation
path must map to one primary category. The existing broad `failureCategory`
field should remain the compatibility-level category, while the bounded code
should be propagated only if the current additive contract can carry it safely.

Do not include reference values, titles, file contents or model-generated
messages in normal traces.

Likely areas:

- `ai-engine/app/services/story_context_analysis_generation_service.py`
- `ai-engine/app/schemas/interaction_trace.py`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiInteractionTraceRequest.java`
- callback contract validation and trace persistence tests

### 2. Preserve actionable but safe diagnostics

Keep a bounded machine-readable category in normal traces and retain detailed
validation text only under the existing diagnostic retention policy.

Verify the complete path:

```text
validator exception
  -> InteractionTraceCollector
  -> AiInteractionTraceRequest
  -> Core callback validation
  -> AiInteractionTracePersistenceService
  -> query response
```

The implementation must fix the current observability gap where normal traces
show `GROUNDING_ERROR` but discard the actionable diagnostic. The preferred
minimal approach is to persist the bounded category in existing fields and not
add a migration unless a new field is demonstrably required.

Add redaction and maximum-size tests before enabling any new diagnostic field.

### 3. Make retry eligibility explicit

Retain at most one corrective retry, but classify failures before entering the
retry path.

Initial proposed policy:

| Failure | Retry | Rationale |
|---|---:|---|
| Model output references an unauthorized/unknown evidence item | Yes | The model can remove or correct the reference. |
| Requested-file scope mismatch | Yes | The model can omit unsupported findings or use scoped evidence. |
| Missing/invalid `relationType` | Yes | The model can repair the structured field. |
| Causal support/classification mismatch | Yes | The model can downgrade to `NOT_ESTABLISHED`. |
| Invalid Core grounding contract | No | Provider output cannot repair Core input. |
| Invalid Core reference mapping | No | Deterministic Core contract failure. |
| Missing typed allow-list or forbidden legacy contract | No | Provider retry cannot establish authorization. |
| Provider incomplete response | No | Terminal provider failure per ADR-079 and Story 0175. |

This table is a plan proposal and must be confirmed by deterministic tests and
the implementation report. A retry must record its eligibility reason and
outcome without changing the accepted validation rules.

Likely areas:

- `ai-engine/app/services/story_context_analysis_generation_service.py`
- `ai-engine/app/prompts/story_context_analysis.py`
- `ai-engine/app/services/interaction_trace.py`

### 4. Measure attempt and retry economics

Extend existing trace assertions and, only if required by the current contract,
add bounded accounting for:

- initial versus corrective attempt;
- retry eligibility category;
- total provider calls per execution;
- first-attempt duration;
- retry duration;
- total execution duration;
- input/output/total token usage and certainty.

Prefer deriving total provider calls and retry outcome from existing attempt
traces rather than adding redundant persisted counters. If a caller-facing
summary is needed, it must be additive and version-compatible.

The implementation must make it possible to compare:

```text
first-attempt success rate
retry success rate
failed retry rate
average/p95 latency by outcome
provider calls by outcome
token usage by outcome when available
```

### 5. Evaluate and minimally align the prompt

Use deterministic fixtures for:

- no requested file scope;
- one requested file with matching references;
- requested file with only unrelated references;
- broad projection;
- reduced/truncated projection;
- causal and non-causal outputs.

Compare the current prompt against the proposed requested-file scope behavior.
Measure prompt size or input estimate, selected/discarded counts, output
grounding, retry outcome and latency. Keep only changes that improve grounding
or retry cost without reducing valid evidence coverage.

The prompt must continue to:

- use only Core-authorized grounding references;
- abstain when evidence is insufficient;
- preserve truncation safety;
- distinguish recommendations from factual/interpretive findings;
- preserve causal contract rules.

Do not duplicate canonical repository content in a new prompt section.

### 6. Preserve contract compatibility

Before changing the callback or trace payload, verify the compatibility matrix:

| Core | AI Engine | Required behavior |
|---|---|---|
| old | old | Existing traces and callbacks remain valid. |
| new | new | Grounding categories and accounting are exchanged. |
| new | old | Unsupported new fields fail closed or are safely omitted according to the accepted contract. |
| old | new | AI Engine does not invent Core authorization or require new fields. |

Existing strict `extra="forbid"` behavior must be respected. Any new field must
be additive and versioned, or the plan must reuse existing fields.

### 7. Add deterministic validation and replay evidence

Add or update tests for:

#### AI Engine

- each grounding taxonomy category;
- retry eligibility and one-retry limit;
- successful first attempt;
- successful corrective retry;
- failed corrective retry;
- deterministic Core contract failure without provider retry;
- provider incomplete response remains terminal;
- requested-file prompt mapping;
- redaction and bounded diagnostics;
- trace token measurement certainty.

#### Backend

- callback DTO compatibility;
- persistence of bounded normal-trace categories;
- diagnostic-only retention of detailed messages;
- trace query mapping;
- existing Story 0173 and Story 0175 regression tests.

#### Evaluation

- broad, targeted and reduced projections;
- grounding success and failure outcomes;
- retry/no-retry policy outcomes;
- prompt/input estimate and selected/discarded accounting;
- latency, provider-call and token measurements;
- quality/regression checks for accepted findings.

### 8. Documentation reconciliation

Update only canonical operator documentation required by the final behavior:

- grounding failure categories;
- retry eligibility and terminal provider failures;
- trace fields and measurement certainty;
- safe recovery guidance;
- diagnostic retention and redaction boundaries.

Do not document an output-budget increase or a polling change as part of this
Story.

## Success Criteria and Evidence

The Implementation Report must provide a before/after comparison using the
current baseline:

- 39 `GROUNDING_ERROR` traces in the measured 24-hour sample;
- 28.5 s average grounding-failure duration;
- 44.5 s grounding-failure P95;
- 37 successful first-or-final traces;
- 13.2 s successful-trace average.

The Story is ready for review only if the evidence shows that the implemented
change does not increase unauthorized or ungrounded accepted output and
demonstrates an improvement in at least one of grounding failure rate, failed
retry rate, provider calls or latency without a material quality regression.
No absolute production target is assumed before the representative evaluation
baseline is captured.

## Validation Commands

Focused validation should run first:

```bash
cd ai-engine && python -m pytest tests/test_story_context_analysis_prompt.py tests/test_story_context_analysis_wire_contract.py -v
./backend/mvnw -pl backend test -Dtest=AiInteractionTracePersistenceServiceTest,StoryContextAgentCallbackFacadeTest -B
```

Then run the applicable broader suites:

```bash
cd ai-engine && python -m pytest -q
./backend/mvnw -pl backend -am clean verify -B
./backend/mvnw -pl mcp-server test -B
```

If `pytest` remains unavailable, the Implementation Report must mark Python
behavioral validation blocked and must not claim the AI Engine suite passed.

## Risks and Mitigations

- **Diagnostic leakage:** use stable bounded codes in normal traces and retain
  detailed messages only under existing diagnostic retention.
- **False latency improvement:** compare accepted evidence quality and not only
  duration.
- **Over-aggressive no-retry policy:** preserve retry for model-correctable
  output failures and measure recovered success.
- **Prompt narrowing hides evidence:** include broad, targeted and reduced
  evaluation cases.
- **Contract drift:** test old/new combinations and avoid unnecessary schema
  changes.
- **Unrelated worktree changes:** inspect and reconcile the pre-existing prompt,
  scenario and Playwright files separately; do not revert or silently include
  them.

## Implementation Boundary

This plan authorizes no implementation until explicitly approved. It does not
authorize:

- production budget changes;
- automatic retrieval or context expansion;
- MCP authentication or credential changes;
- removal of fail-closed validation;
- commit, push, deployment or Story acceptance.

## Next Gate

`WAITING_FOR_PLAN_APPROVAL`

Human approval of this Implementation Plan is required before implementation.
