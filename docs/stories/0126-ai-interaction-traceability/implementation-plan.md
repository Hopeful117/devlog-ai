# Story 0126 - Implementation Plan

## Status

**IMPLEMENTED - UNCOMMITTED, READY FOR HUMAN REVIEW**

This document records the implementation sequence. It is not a new implementation authorization.

## Planned Vertical Slice

1. Define the Python trace contract and collector.
2. Capture provider timing, raw output, and best-effort token usage.
3. Instrument insight, engineering-event, decision, and Story Context Analysis generation paths.
4. Extend the Python-to-Java callback with `interactionTraces`.
5. Add the Java trace entity, repository, DTO, Flyway migration, and persistence service.
6. Persist traces before task failure handling and isolate trace persistence errors.
7. Apply NORMAL/DIAGNOSTIC filtering and diagnostic expiry in Java Core.
8. Add query services and endpoints by AI task and analysis.
9. Add focused tests for retries, failures, redaction, filtering, expiry, and idempotency.
10. Run focused and full verification, then review scope and diff integrity.

## Actual Implementation Sequence

| Area | Result |
|---|---|
| Python schemas and collector | Implemented in `interaction_trace.py` and `AiTaskResultRequest` |
| Provider metadata | Implemented in base, mock, and OpenAI providers |
| Generation services | Integrated for insight, event, decision, and Story Context Analysis |
| Java persistence | Implemented with `AiInteractionTrace`, repository, and persistence service |
| Database | Added `V48__create_ai_interaction_traces.sql` |
| Query API | Added task and analysis query endpoints |
| Verification | Backend 1,298 passed; AI Engine 177 passed |

## Explicitly Unchanged

- No OpenTelemetry, Jaeger, Tempo, Grafana, or distributed tracing infrastructure.
- No frontend dashboard.
- No prompt redesign or grounding-ID redesign.
- No change to validator authority or trusted-knowledge promotion.
- No automatic diagnostic cleanup job.
- No automatic trace injection into future prompts.
- No changes to the standalone deliverable generation endpoint.

## Verification Commands

```bash
./backend/mvnw -pl backend -am test -B
cd ai-engine && python3 -m pytest -q
git diff --check
```
