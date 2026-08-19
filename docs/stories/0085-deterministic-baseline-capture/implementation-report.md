# Implementation Report — Story 0085

## Branch

`story/0085-deterministic-baseline-capture`

## Story

0085 — Deterministic Baseline Capture for New Source-Scoped Analyses

## Status

READY_FOR_COMMIT_APPROVAL

## Production Changes

### Modified Files

1. `backend/src/main/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingClaimService.java`
   - Line 64: `.targetRevision(prepared.targetRevision())` → `.targetRevision(prepared.resolvedRevision())`

2. `backend/src/test/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingClaimServiceTest.java`
   - Added: 5 new tests exercising the real claim service persistence.

### Change Description

The claim service now persists the exact immutable revision actually observed during
synchronization (`SynchronizedWorkspace.resolvedRevision()`), carried on
`PreparedProjectUnderstanding.resolvedRevision()`), instead of the caller's revision
expression (`prepared.targetRevision()`, which may be null).

This establishes ADR-061's Repository Observation Baseline for new source-scoped Project
Understanding Analyses:

    persisted (selectedSource, targetRevision) == (synchronized Source, observed revision)

### What Is NOT Changed

- `ProjectUnderstandingPreparationService` — still computes both requested and resolved
  revisions; unchanged.
- `GitWorkspaceManager` — commit-identity equivalence already guaranteed; unchanged.
- `ProjectUnderstandingExecutionKey` — REQUEST_IDENTITY semantics preserved; unchanged.
- Engineering Event — already compliant reference; unchanged.
- Analysis entity, AnalysisMapper, standard/multi-source Analysis flow — unchanged.
- `KnowledgeCollectionService` — unchanged; downstream collection already pins to
  `analysis.getTargetRevision()` (now the resolved immutable hash).
- Story 0083 temporal services, RepositoryStatePort, ProjectCommitRepository — unchanged.
- Context Engine, AI Engine, API/MCP contracts, persistence schema, migrations — unchanged.
- Legacy Analyses — unchanged.

## Test Results

### Focused Tests (ProjectUnderstandingClaimServiceTest)

```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Neighbor Tests (Project Understanding + KnowledgeCollection + TemporalAssessment)

```
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Full Backend Suite

```
Tests run: 835, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Note: one first full-suite run reported a single failure in
`KnowledgeSelectionServiceTest.shouldDeterministicallyRankDeduplicateBudgetAndDigestSelection`
(`expected: <1> but was: <0>`). The test passes in isolation and in a full rerun
(835/835 green). It is a pre-existing flaky ordering-sensitive test using random UUIDs in
its fixtures (line 107/112) and is unrelated to this change (different package/domain).

## Test Categories Covered

| Case | Test Method | Status |
|---|---|---|
| NO_REVISION_REQUESTED | `persistsResolvedRevisionWhenNoRevisionRequested` | PASS |
| FULL_SHA_REQUESTED | `persistsResolvedRevisionWhenFullShaRequested` | PASS |
| SYMBOLIC_REVISION_REQUESTED (`main`) | `persistsResolvedRevisionWhenSymbolicRevisionRequested` | PASS |
| SHORT_SHA_REQUESTED | `persistsResolvedRevisionWhenShortShaRequested` | PASS |
| REQUESTED_DIFFERS_FROM_OBSERVED | `persistsResolvedRevisionWhenRequestedDiffersFromObserved` | PASS |
| DOWNSTREAM_COLLECTION_PINNED | Existing `KnowledgeCollectionServiceTest` (sync uses persisted targetRevision) | PASS |
| MULTI_SOURCE_UNCHANGED | By construction (diff isolated in source-scoped claim path) | PASS |
| STORY_0083_BASELINE_PRECONDITION | Existing `TemporalAssessmentServiceImplTest` (non-null baseline → CURRENT/SUSPECTED_STALE) | PASS |

## Invariant Verification

- [x] Baseline identity = Source + immutable observed revision
- [x] Persisted targetRevision == actually observed repository state
- [x] Caller revision expression is never persisted as provenance
- [x] No raw requested-vs-resolved guard introduced
- [x] `executionKey` = REQUEST_IDENTITY unchanged
- [x] Analysis baseline = OBSERVATION_IDENTITY
- [x] Multi-source path unchanged by construction
- [x] `targetRevision` immutability preserved (`updatable=false`)
- [x] Engineering Event production path unchanged
- [x] Story 0083 temporal services unchanged
- [x] No AI inference, no ProjectCommit substitution, no legacy repair
- [x] Fail-closed: sync failure / invalid revision prevents Analysis creation

## ADR Assessment

**Result: NO NEW ADR**

Compliant with ADR-061 (PROPOSED). ADR-061's status (PROPOSED) remains a separate human
decision; this Story does not silently change it.

## Git Hygiene

- Branch: `story/0085-deterministic-baseline-capture`
- Only expected files modified
- No .env, IDE files, temporary files, or benchmark artifacts
- No unrelated changes
- `git diff --cached`: empty (nothing staged)
