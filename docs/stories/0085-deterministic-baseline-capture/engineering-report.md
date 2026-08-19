# Engineering Report — Story 0085

## Branch

`story/0085-deterministic-baseline-capture`

## Story

0085 — Deterministic Baseline Capture for New Source-Scoped Analyses

## Engineering Assessment

### Problem Classification

**SMALL_DETERMINISTIC_CORRECTION (A)**

A one-line persistence correction: persist the observed revision instead of the caller's
revision expression. No new architectural concept, schema, API, or lifecycle state is
introduced.

### Architecture Ownership

- **Fact owner:** `GitWorkspaceManager.synchronize() → SynchronizedWorkspace.resolvedRevision()`
- **Policy owner:** `ProjectUnderstandingPreparationService.prepare()` (normalizes +
  synchronizes; the resolved revision is computed from the same operation).
- **Persistence owner:** `ProjectUnderstandingClaimService.claim()` (constructs the
  Analysis from already-prepared deterministic facts).
- **Execution owner:** `ProjectUnderstandingService.execute()` (orchestrates
  prepare → claim → workflow; the same prepared result drives claim and downstream
  collection).

### Explicit Revision Semantics

`GitWorkspaceManager.synchronize(source, requestedExpr)` resolves the requested Git
revision expression (`null`, `main`, `feature/foo`, short SHA, full SHA) to the canonical
immutable commit hash, checks it out in detached mode, resets to it, and returns that
same hash as `resolvedRevision`. This equivalence is guaranteed by construction, so:

- **EXPLICIT_REVISION_EQUIVALENCE_ALREADY_GUARANTEED**
- No requested-vs-resolved guard is required or added (a raw string comparison would
  incorrectly reject symbolic/short references).
- Engineering Event's stricter guard is justified only by its narrower request contract
  (full object ID required via `GitCommitIdentity.normalize`), which Project Understanding
  does not impose.

## Solution

Single-line change in `ProjectUnderstandingClaimService.claim()`:

```java
.targetRevision(prepared.resolvedRevision())
```

This persists the exact observed immutable revision while leaving the request expression
out of the persisted baseline. The `executionKey` (REQUEST_IDENTITY) and the persisted
baseline (OBSERVATION_IDENTITY) remain intentionally distinct.

## Risk Assessment

- **Risk: LOW** — 1-line change in a source-scoped service; no schema/API change.
- **Rollback:** Revert line 64 to `prepared.targetRevision()`.
- **Scope:** 1 production file + 1 new focused test file.
- **Failure semantics:** Sync failures / invalid revisions still throw during preparation
  before claim, so no fabricated provenance can be persisted.

## Behavioral Changes

**Before:** Project Understanding Analyses persisted `targetRevision = null` when no
revision was requested, causing Story 0083 to return UNKNOWN (baseline unavailable).

**After:** Project Understanding Analyses persist `targetRevision = <resolved immutable
commit>` regardless of the caller's revision expression, satisfying Story 0083's baseline
precondition (`selectedSource != null` AND `targetRevision != null`).

## Test Coverage

| Case | Test | Status |
|---|---|---|
| No requested revision | `persistsResolvedRevisionWhenNoRevisionRequested` | PASS |
| Full SHA requested | `persistsResolvedRevisionWhenFullShaRequested` | PASS |
| Symbolic revision (`main`) | `persistsResolvedRevisionWhenSymbolicRevisionRequested` | PASS |
| Short SHA requested | `persistsResolvedRevisionWhenShortShaRequested` | PASS |
| Requested differs from observed | `persistsResolvedRevisionWhenRequestedDiffersFromObserved` | PASS |
| Downstream collection pinned | Existing `KnowledgeCollectionServiceTest` | PASS |
| Story 0083 baseline precondition | Existing `TemporalAssessmentServiceImplTest` | PASS |
| Multi-source unchanged | By construction | PASS |

## Documentation Reconciliation

- `story.md`: Matches implementation (defect, resolution, scope, invariants).
- `repository-analysis.md`: Matches implementation (lifecycle trace, equivalence proof,
  multi-source isolation, REQUEST_IDENTITY).
- `implementation-plan.md`: Updated to reflect the final single-file change and final test
  names.
- `implementation-report.md`: Created with accurate change description and results.
- `engineering-report.md`: This document.

## ADR Assessment

**NO NEW ADR REQUIRED; COMPLIANT WITH ADR-061 (PROPOSED).**

ADR-061's status change (PROPOSED → ACCEPTED) is a separate human decision and is not
carried out by this Story.

## Git Hygiene

- Branch: `story/0085-deterministic-baseline-capture`
- Files modified: 1 production + 1 test
- Untracked: `docs/stories/0085-deterministic-baseline-capture/` (story artifacts)
- No .env, IDE files, temporary files, or benchmark artifacts
- No unrelated changes

## Post-Merge Expectations

- New source-scoped Project Understanding Analyses will carry a valid Repository
  Observation Baseline (`selectedSource` + concrete immutable `targetRevision`).
- Story 0083 temporal assessment can produce CURRENT / SUSPECTED_STALE instead of UNKNOWN
  for such analyses (given non-empty evidenceReferences and available currentKnownRevision).
- Legacy analyses and multi-source analyses remain unchanged.
