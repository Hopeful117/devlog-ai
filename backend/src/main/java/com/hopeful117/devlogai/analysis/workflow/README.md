# Analysis Workflow

## Overview

The analysis workflow is the application-level coordinator defined by ADR-018.
It prepares one pending analysis for future AI processing without moving
cross-domain workflow logic into a REST controller or a domain service.

## Preparation sequence

```text
PENDING Analysis
      |
      v
Start Analysis (IN_PROGRESS)
      |
      v
Run deterministic analysis boundary
      |
      v
Build immutable AnalysisContext
      |
      v
Create AiTask (CREATED)
      |
      v
Submit to AI Engine (SUBMITTED)
```

Each state-changing specialized service owns its own short transaction. The
workflow itself does not open one long transaction.

If preparation fails after the analysis has started, the workflow asks
`AnalysisService` to transition it to `FAILED`. Repeated starts are rejected by
the locked `PENDING -> IN_PROGRESS` transition and do not create another task.

## Deterministic V1 boundary

ADR-018 defines orchestration but does not define extraction rules for Facts or
Observations. The V1 deterministic service therefore reports results already
persisted for the current analysis. It is an explicit extension point for
future deterministic extractors; no speculative extraction rule is introduced
by this increment.

## REST endpoint

```text
POST /api/v1/analyses/{analysisId}/workflow
```

The request selects one `AiTaskType`. The response contains the analysis state,
deterministic result counts, AI task identifier and correlation identifier.

The AI Engine call only expects a `202 Accepted` acknowledgement. Callback,
retry, cancellation, proposal creation and knowledge promotion remain outside
this increment.
