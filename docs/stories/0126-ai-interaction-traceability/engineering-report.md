# Story 0126 - Engineering Report

## Status

**IMPLEMENTED - UNCOMMITTED ON FEATURE BRANCH, AWAITING HUMAN REVIEW**

## Delivered Architecture

```text
AiTask submission
        ↓
Python provider attempt
        ↓
InteractionTraceCollector
        ├── initial attempt
        ├── corrective retry
        └── failure/validation outcome
        ↓
AiTaskResultRequest.interactionTraces
        ↓
Java callback handler
        ↓
REQUIRES_NEW trace persistence
        ↓
ai_interaction_traces
        ↓
query by task ID or analysis ID
```

## Authority Assessment

```text
JAVA_CORE = durable persistence + level filtering + expiry + query access
PYTHON = provider execution metadata + defensive redaction
TRACE_DATA = operational metadata, never trusted knowledge
```

Trace fields include provider/model identity, prompt and grounding fingerprints, selected-knowledge counts, timing, validation status, retry reason, optional token usage, and nullable future correlation IDs. Raw prompts, model responses, parsed output, and diagnostics are persisted only in DIAGNOSTIC mode and are redacted before transport.

## Retry And Failure Semantics

1. The collector creates a distinct trace ID for every provider attempt.
2. A corrective retry is recorded as attempt 2 and carries the previous failure reason.
3. Provider, parsing, and validation failures retain a trace before the failed callback is sent.
4. Java persists traces in an independent transaction so trace persistence does not change task result behavior.
5. Duplicate trace IDs are ignored for idempotent callback handling.

## Durability And Queryability

- `ai_interaction_traces` is linked to both `ai_tasks` and `analyses` with cascade deletion.
- Composite indexes support ordered retrieval by task/attempt and analysis/attempt.
- Core applies diagnostic retention metadata but intentionally does not run cleanup automation.
- REST endpoints expose ordered trace records by AI task ID and analysis ID.

## Quality Evidence

- Backend: 1,298 tests passed.
- AI Engine: 177 tests passed.
- Flyway migrations through v48 applied successfully.
- `git diff --check`: passed.

## Final Assessment

```text
ATTEMPT_LEVEL_TRACEABILITY = PRESENT
RETRY_AND_FAILURE_CAPTURE = PRESENT
NORMAL_DIAGNOSTIC_FILTERING = PRESENT
JAVA_CORE_PERSISTENCE_AUTHORITY = PRESERVED
TRUST_BOUNDARY = PRESERVED
QUALITY_GATES = PASSED
STORY_ACCEPTANCE_GATE = AWAITING_HUMAN_REVIEW
```
