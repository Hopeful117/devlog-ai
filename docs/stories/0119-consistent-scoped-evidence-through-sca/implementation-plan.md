# Story 0119 - Implementation Plan

## Status

**IMPLEMENTED - DELEGATED SUBTASKS ON MAIN, AWAITING HUMAN REVIEW**

This plan records the implementation sequence for the delegated subtasks only.

## Planned Vertical Slice

Per governing design (Story 0119 refinement, DELEGATE subtasks):

1. **Subtask 2 — Trust-tier classification**: Add `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT` to `HUMAN_AUTHORED` block in `classifyTrustTier()`
2. **Subtask 4 — Deterministic evidence timestamp**: Replace `Instant.now()` with `Source.lastSynchronizedAt` (fallback `Instant.EPOCH`) in `DocumentBodyCollector`
3. **Subtask 7 — Conformance tests**: Trust-tier classification tests + determinism tests

## Actual Implementation Sequence

| Step | Change | Files |
|------|--------|-------|
| 1 | Add 3 document kinds to HUMAN_AUTHORED block | `EngineeringContextContractMapper.java` |
| 2 | Add `UNAVAILABLE_SYNC_TIMESTAMP` fallback; use `Source.lastSynchronizedAt` as `occurredAt`; thread through collect methods | `DocumentBodyCollector.java` |
| 3 | Add 3 trust-tier classification tests + helper | `EngineeringContextContractMapperTest.java` |
| 4 | Add `ArgumentCaptor` import + 3 determinism tests (non-null, null fallback, ADR path) | `DocumentBodyCollectorTest.java` |

## Verification Planned

- Focused `EngineeringContextContractMapperTest` (trust-tier classification)
- Focused `DocumentBodyCollectorTest` (determinism)
- Focused `DocumentBodyCollectorIntegrationTest` (regression)
- Full backend Maven verification with coverage
- `git diff --check`

## Explicitly Unchanged

- `RepositoryContextEngine.build(6-param)` scope propagation (Subtask 1 — LEARN)
- `DocumentBodyCollector.collect()` budget enforcement (Subtask 3 — PAIR)
- `AnalyzeStoryContextUseCase.buildGroundingContract()` (Subtask 5 — LEARN)
- `KnowledgeSelectionServiceRepositoryContextPropagationTest` (Subtask 6 — PAIR)
- Multi-source disambiguation policy
- RAG/vector/diff extraction
- ContextPack redesign
- Any LEARN/PAIR code paths
- No API/MCP contract changes
- No new persistence entities
