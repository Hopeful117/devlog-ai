# Story 0085 — Deterministic Baseline Capture for New Source-Scoped Analyses

## Status

**READY_FOR_COMMIT_APPROVAL**

## Objective

Ensure that NEW source-scoped Project Understanding Analyses persist the repository
revision ACTUALLY OBSERVED during synchronization, so that the Repository Observation
Baseline is accurate:

    Source + immutable observed revision

For Project Understanding, the authoritative revision already exists as:

    SynchronizedWorkspace.resolvedRevision()

and is already propagated through:

    PreparedProjectUnderstanding.resolvedRevision()

## Problem

`ProjectUnderstandingClaimService` persisted:

    prepared.targetRevision()

which represents caller intent and may be null.

The caller may request a Git revision expression (`null`, a symbolic name such as
`main`, a short SHA, or a full SHA). `GitWorkspaceManager.synchronize()` resolves that
expression to the canonical immutable commit actually checked out and observed.

The defect: when the caller requested no revision, the Analysis persisted `targetRevision
= null` even though the repository was successfully observed at a known immutable
revision. This defeated Story 0083 temporal assessment, which returns UNKNOWN when
`Analysis.targetRevision` is null.

## Resolution

`ProjectUnderstandingClaimService` now persists:

    prepared.resolvedRevision()

the exact immutable revision actually observed for the synchronized Source. Caller
syntax is NOT provenance; the observed immutable revision is.

`GitWorkspaceManager.synchronize()` already guarantees commit-identity equivalence:
it resolves the requested Git revision expression to the canonical immutable commit,
checks out that exact commit in detached mode, resets to it, and returns that same
resolved revision. No additional requested-vs-resolved guard is required or added.

The `executionKey` (REQUEST_IDENTITY) remains unchanged and intentionally distinct from
the persisted baseline (OBSERVATION_IDENTITY).

## Scope

### IN SCOPE

- New source-scoped Project Understanding Analyses
- Persist the exact observed revision as `Analysis.targetRevision`
- Deterministic tests covering request/revision-expression forms
- Story 0083 compatibility verification (baseline precondition now satisfiable)

### OUT OF SCOPE

- Legacy repair (existing `targetRevision = NULL` Analyses stay unchanged)
- Multi-source baseline persistence
- Story 0084 eligibility
- Evidence-reference redesign
- AI provenance inference
- Temporal assessment redesign
- Context Engine changes
- API/MCP changes
- Persistence migration
- Engineering Event production path (unchanged reference implementation)

## Non-Negotiable Invariants

1. Repository provenance is deterministic.
2. AI never chooses repository provenance.
3. A repository baseline is identified by Source + immutable revision.
4. The persisted revision must be the revision actually observed.
5. Caller intent is not equivalent to observed repository state.
6. Latest ProjectCommit is not automatically historical provenance.
7. Single-source and multi-source observations must not be conflated.
8. Multi-source provenance must never be collapsed into an arbitrary targetRevision.
9. Historical provenance is immutable (`Analysis.targetRevision` remains `updatable=false`).
10. Missing provenance remains explicit.
11. Legacy provenance is never fabricated.
12. Repository-state verification failure never becomes false certainty.
13. Story 0083 semantics remain unchanged.

## References

- ADR-006 — AI proposals / trusted knowledge boundary
- ADR-059 — Temporal Knowledge
- ADR-060 — Deterministic / Probabilistic Responsibility
- ADR-061 — Deterministic Repository Observation Baselines (PROPOSED; status is a
  separate human decision)
- Story 0083 — Deterministic Insight Temporal Assessment Foundation
