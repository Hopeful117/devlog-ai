# Implementation Plan — Story 0085

## Summary

Persist the exact observed repository revision for new source-scoped Project
Understanding Analyses.

Change `ProjectUnderstandingClaimService` to persist `prepared.resolvedRevision()`
instead of `prepared.targetRevision()`.

No explicit requested-vs-resolved guard is added: `GitWorkspaceManager.synchronize()`
already guarantees commit-identity equivalence by resolving the requested Git revision
expression to the canonical immutable commit actually checked out and observed.

## Production Changes

### File: `backend/src/main/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingClaimService.java`

**Location:** Line 64 (inside `claim()`, `Analysis.builder()`).

**Previous code:**
```java
.targetRevision(prepared.targetRevision())
```

**New code:**
```java
.targetRevision(prepared.resolvedRevision())
```

**Rationale:** `targetRevision` is immutable historical provenance. It MUST contain the
exact immutable revision actually observed for the synchronized Source
(`SynchronizedWorkspace.resolvedRevision()`), never the caller's revision expression.

### Unchanged By Design

- `ProjectUnderstandingPreparationService` — still computes both `targetRevision`
  (request) and `resolvedRevision` (observation); no change needed.
- `GitWorkspaceManager` — equivalence already guaranteed internally.
- `ProjectUnderstandingExecutionKey` — REQUEST_IDENTITY semantics preserved.
- Engineering Event — already compliant reference; untouched.
- Analysis entity — `targetRevision` remains `updatable=false`.
- AnalysisMapper — must NOT own baseline capture; untouched.
- Standard/multi-source Analysis flow — untouched.
- `KnowledgeCollectionService` — production code untouched.
- Story 0083 temporal services, RepositoryStatePort, ProjectCommitRepository — untouched.
- API/MCP contracts, persistence schema, migrations — untouched.
- Legacy Analyses — untouched.

## Test Changes

### File: `backend/src/test/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingClaimServiceTest.java`

New focused unit test for the real `ProjectUnderstandingClaimService` (package-private,
tested in the same package; the existing `ProjectUnderstandingServiceTest` mocks the
claim service, so it does not exercise persistence).

5 tests, all asserting `persistedAnalysis().getTargetRevision() == resolvedRevision`:

1. `persistsResolvedRevisionWhenNoRevisionRequested` — `null` requested + resolved R
   → persisted `R`; also asserts `selectedSource == source`.
2. `persistsResolvedRevisionWhenFullShaRequested` — full SHA requested + resolved R
   → persisted `R`.
3. `persistsResolvedRevisionWhenSymbolicRevisionRequested` — `"main"` requested + resolved
   R → persisted `R`; MUST NOT fail because `"main" != R`.
4. `persistsResolvedRevisionWhenShortShaRequested` — short SHA requested + resolved R
   → persisted `R`.
5. `persistsResolvedRevisionWhenRequestedDiffersFromObserved` — `refs/heads/main`
   requested + resolved R → persisted `R`.

### Existing coverage reused (no duplication)

- `KnowledgeCollectionServiceTest` — already proves `collect()` calls
  `synchronize(source, analysis.getTargetRevision())`; post-fix that value is the
  resolved immutable hash R (downstream pinning).
- `TemporalAssessmentServiceImplTest` — already proves CURRENT / SUSPECTED_STALE given a
  non-null baseline + mocked `RepositoryStatePort` (Story 0083 precondition).
- Multi-source regression — not needed: the diff is strictly isolated inside
  `ProjectUnderstandingClaimService` and cannot affect `AnalysisMapper` /
  `AnalysisServiceImpl` / `resolveSources()`.

## Behavior Change

**Before:** A Project Understanding Analysis persisted the caller's revision expression,
or `null` when none was requested.

**After:** A Project Understanding Analysis persists the exact immutable revision actually
observed during synchronization.

## Rollback/Safety

Reverting line 64 to `prepared.targetRevision()` restores the previous behavior. No
schema, API, migration, or contract change. No new lifecycle states.

## Non-Goals

- Adding a raw `requested.equals(resolved)` guard.
- Tightening the Project Understanding request API to full-hash-only.
- Changing `executionKey` semantics.
- Repairing legacy `targetRevision = NULL` Analyses.
- Fabricating a baseline for the multi-source path.
- Changing ADR-061 status (separate human decision).
