# Story 0126 - Repository Analysis

## Status

**IMPLEMENTED - READY FOR HUMAN REVIEW**

## Baseline

- Branch: `story/0126-ai-interaction-traceability`
- Baseline HEAD: `c2ab4c7` (`Merge pull request #111 from Hopeful117/story/fix-unauthorized-observation-grounding-ids`)
- Implementation is currently uncommitted on the feature branch.
- Governing constraints: ADR-006, ADR-063, ADR-066, ADR-067.

## Existing Boundaries Reused

- `AiTask` remains the functional AI work aggregate and callback correlation point.
- `AiTaskResultRequest` remains the Python-to-Java result contract.
- Existing `PromptExecutionMetadata` remains the task-level prompt identity record.
- Python providers remain responsible for LLM execution only.
- Java Core remains responsible for durable persistence and authoritative task handling.
- Existing Flyway, Spring Data JPA, and REST query conventions are reused.

## Implemented Topology

```text
Python generation service
        ↓
InteractionTraceCollector
        ↓
AiTaskResultRequest.interactionTraces
        ↓
Java AiTaskResultServiceImpl
        ↓
AiInteractionTracePersistenceService (REQUIRES_NEW)
        ↓
ai_interaction_traces
        ↓
AiInteractionTraceQueryService
        ↓
REST queries by task or analysis
```

## Repository Findings

1. A reusable Python collector now records one trace per concrete provider attempt, including corrective retries, parsing failures, validation failures, and provider failures.
2. Provider metadata is exposed through `ProviderGenerationResult`, including best-effort raw output, timing, and token usage.
3. The callback contract carries structured `interactionTraces` without changing existing proposal or analysis result semantics.
4. Java persists traces in a dedicated aggregate/table linked to both `AiTask` and `Analysis`.
5. Core applies NORMAL/DIAGNOSTIC persistence filtering, diagnostic expiry, and idempotency by trace UUID.
6. Query access is available by AI task ID and analysis ID, ordered by attempt and trace ID.
7. Optional `traceId` and `spanId` are nullable and are not populated by the current providers.

## Deliberate Boundary

The standalone `/api/v1/deliverables/generate` endpoint is not integrated with this callback trace path. It does not create an `AiTask`, does not return `AiTaskResultRequest`, and has no analysis correlation required by Story 0126.

## Test Coverage Assessment

- Dedicated Java persistence tests cover NORMAL mode, DIAGNOSTIC mode, expiry, and duplicate trace IDs.
- Existing callback tests cover trace persistence integration and failure isolation.
- Python generation tests cover successful attempts, corrective retries, failures, and credential redaction.
- Full backend verification passed: 1,298 tests, 0 failures/errors/skips.
- Full AI Engine verification passed: 177 tests.

## Conclusion

The repository contains the intended vertical slice for durable AI interaction traceability. Java/Core remains the persistence and lifecycle authority; traces remain operational metadata and cannot authorize grounding or promote trusted knowledge.
