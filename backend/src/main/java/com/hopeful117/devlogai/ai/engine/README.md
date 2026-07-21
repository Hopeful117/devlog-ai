# AI Engine REST Client

## Purpose

This package implements the outbound submission boundary from ADR-019. Core
application services depend on `AIEngineClient`; only `RestAIEngineClient`
contains HTTP-specific behavior.

The Core submits an immutable request to:

```text
POST /api/v1/ai/tasks
```

The payload contains the Core correlation identifier, AI task type, analysis
identifier and the complete deterministic `AnalysisContext`. The AI Engine is
not expected to retrieve additional project knowledge.

Only a valid `202 Accepted` acknowledgement with the same correlation
identifier advances the Core task from `CREATED` to `SUBMITTED`. Rejection,
invalid acknowledgement or transport failure advances it to `FAILED` through
the AI task service.

## Configuration

```properties
ai-engine.base-url=${AI_ENGINE_BASE_URL:http://localhost:8000}
ai-engine.connect-timeout=${AI_ENGINE_CONNECT_TIMEOUT:2s}
ai-engine.read-timeout=${AI_ENGINE_READ_TIMEOUT:5s}
```

This increment does not implement result callbacks, retries, cancellation or
AI result conversion.
