# Story 0114 - Engineering Report

## Status

**IMPLEMENTED_AND_MERGED - NOT READY FOR ACCEPTANCE**

## Delivered Architecture

```text
EngineeringContext.metadata.freshness
    -> AiTask.contextSnapshot["contextFreshness"]
    -> StoryContextAnalysis.contextFreshness JSONB
    -> StoryContextAnalysisResponse envelope
    -> REST and MCP consumers
```

The repository now captures the authoritative `EngineeringContextFreshness` snapshot at context construction, carries it through the `AiTask` lifecycle, persists it in a new `context_freshness` JSONB column, and exposes it through a response envelope.

## Contract Model

`StoryContextAnalysisResponse` wraps the AI analysis result with the deterministic freshness snapshot. Freshness fields: status (`CURRENT`, `STALE`, `PARTIALLY_FRESH`, `NO_BASELINE`, `UNKNOWN`), repository revision, context revision, per-source breakdown (observed revision, context revision, status, guidance, checkedAt), and warnings (currently omitted — see code review).

`StoryContextAnalysisResult` (AI-owned) remains unchanged.

## Authority Assessment

Target authority model:

```text
JAVA_CORE = context + trust + grounding + validation + durability + FRESHNESS
PYTHON = generation + defensive validation only (NO freshness)
REST_AND_MCP = thin adapters over Core pipeline
```

Freshness remains deterministic and Java/Core-owned. AI receives and produces no freshness data. Freshness remains attached to a non-trusted analysis artifact. REST and MCP remain adapters over the Core pipeline.

## Execution And Durability Assessment

The migration `V47__add_context_freshness_to_story_context_analysis.sql`, entity, repository, and callback persistence path exist. However:

- The `Map.copyOf()` retrieval bug can cause legitimate snapshots to fail on read.
- Warnings from `EngineeringContextMetadata` are not captured.
- AC1/AC2 lack direct end-to-end tests exercising the capture → task → callback → persistence → query path.

Therefore the complete Story 0114 acceptance contract is not currently achieved end to end.

## Quality Evidence

- Backend: 1,109 tests, 0 failures/errors/skips.
- JaCoCo: 82.9% line coverage; all configured checks met.
- Frontend: 260 tests across 49 files passed; production build passed.
- Frontend E2E: 1 smoke test passed.
- PR checks: Maven/JaCoCo, frontend unit/build, frontend E2E, and aggregate quality gate all succeeded.
- `git diff --check 0a1c719..d35383b`: passed during review.
- No PR check demonstrates the requested full MCP suite.
- No PR check demonstrates `python -m pytest`; no AI files changed.

## Required Corrective Work

1. Make `NO_BASELINE` and `UNKNOWN` freshness snapshots retrievable (fix `Map.copyOf`).
2. Include aggregate warnings in the captured snapshot.
3. Add `AnalyzeStoryContextUseCase` integration test for capture and persistence.
4. Add controller test for response envelope serialization and compatibility.
5. Expand deterministic tests for `NO_BASELINE` and `UNKNOWN` statuses.
6. Perform the required real Story qualitative evaluation.

## Final Assessment

```text
STRUCTURAL_VERTICAL_SLICE = PRESENT
PRIMARY_END_TO_END_FLOW = PARTIAL (retrieval bug, missing warnings, missing direct tests)
TRUST_AND_GROUNDING_AUTHORITY = PRESERVED (freshness remains Core-owned)
MERGE_CI = PASSED
STORY_ACCEPTANCE_GATE = NOT_PASSED
NEXT_STATE = CORRECTIVE_IMPLEMENTATION_REQUIRED
```