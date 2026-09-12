# Story 0119 - Implementation Report

## Status

**IMPLEMENTED - DELEGATED SUBTASKS ON MAIN, AWAITING HUMAN REVIEW**

## Repository State

- Branch: `main`
- HEAD: `e730c87` (Story 0118 merge)
- Worktree: Uncommitted changes (delegated subtasks not yet committed)
- Commit created by this task: NO (awaiting human authorization)
- Push performed by this task: NO

## Governance

- Governing ADRs: ADR-006, ADR-063, ADR-067
- Authorized Story: Story 0119 — Consistent Scoped Evidence Through Story Context Analysis
- Scope: DELEGATE subtasks only (Subtasks 2, 4, 7)
- Trust target: trust-tier classification fix, deterministic evidence timestamps
- Java/Core authority target: trust-tier mapping, evidence identity determinism

## Implementation Summary

Implemented three delegated subtasks from Story 0119. Subtask 2 adds `STORY_DOCUMENT`, `ADR_DOCUMENT`, and `ROADMAP_DOCUMENT` to the `HUMAN_AUTHORED` trust tier in `EngineeringContextContractMapper.classifyTrustTier()`, fixing invariant I-4 where repository document evidence was silently excluded (classified as `null`). Subtask 4 replaces `Instant.now()` with `Source.lastSynchronizedAt` (the existing persisted synchronization timestamp) as `occurredAt` for repository document evidence, falling back to `Instant.EPOCH` when `lastSynchronizedAt` is null. This removes execution-time nondeterminism while using the existing domain timestamp. Subtask 7 adds trust-tier classification tests and determinism tests covering both non-null and null `lastSynchronizedAt` cases.

## Production Components

### Backend (Java)
- `backend/.../engineeringcontext/mapper/EngineeringContextContractMapper.java`: Added 3 document kinds to HUMAN_AUTHORED block (line 220)
- `backend/.../repositorycontext/collector/DocumentBodyCollector.java`: Added `UNAVAILABLE_SYNC_TIMESTAMP` fallback constant; `resolveWorkspace()` returns `ResolvedWorkspace` carrying `occurredAt` from `Source.lastSynchronizedAt`; threaded `occurredAt` through all collect methods replacing `Instant.now()`

### Tests
- `backend/.../EngineeringContextContractMapperTest.java`: 3 new trust-tier classification tests (13 total)
- `backend/.../collector/DocumentBodyCollectorTest.java`: 3 new determinism tests (6 total)
- `backend/.../collector/DocumentBodyCollectorIntegrationTest.java`: 6 existing tests (unchanged, all pass)

## Documentation

- `docs/stories/0119-consistent-scoped-evidence-through-sca/story.md`: canonical Story (REFINEMENT state)

## Recorded Verification

```text
TARGETED_TESTS = 25 passed (13 mapper + 6 collector + 6 integration)
FULL_BACKEND_TESTS = 1182 passed
JACOCO_CHECKS = MET
GIT_DIFF_CHECK = PASS (backend/ files only)
STAGED_FILES = 4 source/test files (uncommitted)
```

## Architectural Decisions Preserved

- Java/Core deterministic authority: trust-tier classification remains in `EngineeringContextContractMapper`
- Evidence identity determinism: `Source.lastSynchronizedAt` (or `Instant.EPOCH` fallback) ensures reproducible digests without introducing `Instant.now()` or new time sources
- Scope discipline: only DELEGATE subtasks implemented; LEARN/PAIR subtasks untouched
- No changes to LEARN/PAIR code paths: `RepositoryContextEngine.build(6-param)`, `AnalyzeStoryContextUseCase.buildGroundingContract()`, `DocumentBodyCollector.collect()` budget enforcement

## Known Limitations

- `Source.lastSynchronizedAt` is a source-level persisted synchronization timestamp, not an immutable timestamp intrinsically bound to a specific Git revision. It represents the most recent prior collection timestamp, not the current SCA execution time
- When `lastSynchronizedAt` is null (source never collected), `Instant.EPOCH` is used as fallback
- Trust-tier fix means document evidence now appears in `EngineeringContext` but grounding contract still derives from `EngineeringContext` (Subtask 5 not yet implemented)
- Cross-document budget enforcement not yet implemented (Subtask 3)

## Git Diff Summary

Uncommitted changes: 4 backend files, +167/-3 lines.

| File | Insertions | Deletions |
|------|-----------|-----------|
| `EngineeringContextContractMapper.java` | +3 | -1 |
| `DocumentBodyCollector.java` | +30 | -6 |
| `EngineeringContextContractMapperTest.java` | +58 | 0 |
| `DocumentBodyCollectorTest.java` | +143 | 0 |

## Readiness

```text
IMPLEMENTATION_PRESENT = YES
COMMITTED_ON_MAIN = NO (uncommitted changes)
MERGED = NO
PUSH_PERFORMED = NO
HUMAN_ACCEPTANCE = PENDING
READY_FOR_HUMAN_REVIEW = YES
```
