# AI Task Domain

## Overview

The AI Task domain represents an AI processing request created and owned by the
Java Core for one `Analysis`.

Each task persists the exact `AnalysisContext` sent to the AI Engine and uses a
Core-generated correlation identifier. The Python AI Engine may execute the
request, but it does not own the business lifecycle stored by the Core.

## Lifecycle

```text
CREATED -> SUBMITTED -> PROCESSING -> COMPLETED
                                \-> FAILED
```

Transitions are explicit service operations. Arbitrary status updates are not
exposed.

## Responsibilities

The domain is responsible for:

- associating one or more AI tasks with an analysis;
- generating stable correlation identifiers;
- preserving immutable context snapshots;
- tracking submission and processing timestamps;
- recording external job identifiers, attempts, and technical failures;
- enforcing the V1 lifecycle.

The domain is not responsible for:

- communicating with the Python AI Engine;
- retrying tasks automatically;
- changing the lifecycle of the originating analysis.

ADR-020 callbacks are handled by the application-level AI Engine callback
service. That service coordinates proposal persistence and the task terminal
transition atomically while this domain remains the owner of task state.
