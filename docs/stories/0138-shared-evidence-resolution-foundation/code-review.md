# Story 0138 - Refinement Review

## Status

`STORY_0138_ARCHITECTURE_ACCEPTED - FOLLOW_UP_SECURITY_STORY_REQUIRED`

## Review Scope

Reviewed the Slice 0 source-selection alignment, Slice 1 explicit resolution
seam, Slice 2 Git/document adapters, Slice 3 Fact adapter, Slice 4 snapshot
boundary, Slice 5 REST/MCP projections and Slice 6 persisted-family adapters
against the current repository, Story 0137, ADR-063 and ADR-068.

## Confirmed Findings

### Resolved: source ambiguity policy is now explicit

`AnalyzeStoryContextUseCase.resolveRevisionScope`,
`RepositoryStructureCollector` and MCP `ResourceSupport.requireActiveSourceId`
now distinguish zero and multiple active sources and never select by ordering.
Explicit `RepositoryRevisionScope` uses its exact source and revision.

The shared facade does not own repository or domain authority. Git and document
adapters delegate to existing repositories, workspace synchronization and
secure content reading.

The Fact adapter preserves Fact authority and requires explicit analysis scope;
it does not use project-wide identity as a substitute for analysis ownership.
The snapshot tests preserve `TaskSnapshotEvidenceResolver` as the sole
originating-task content path. The REST and MCP consumers delegate to the Core
facade and do not define identity or resolution semantics.

The six additional family adapters retain their repository/entity authority,
return family-owned payloads and reject task-snapshot mode before accessing
current state.

The REST and MCP projections are thin transport adapters, but this review did
not identify a complete application-user authorization boundary around the
REST endpoint. This is distinct from task-scoped `AiReferenceResolver`
authorization and must not be inferred from successful resolution.

## Architectural Risks

1. Reusing `AiReferenceResolver` as the shared resolver would incorrectly merge
   task-scoped authorization/mapping with project canonical identity.
2. Returning `RepositoryEvidence` as the universal result would leak bounded
   composition projections into authoritative family resolution.
3. Treating structure aggregates as expandable would violate Story 0137.
4. Mapping all missing or unauthorized references to `Optional.empty()` would
   erase deterministic failure semantics already present in AI and repository
   paths.
5. Letting MCP resolve canonical references would violate ADR-063's transport
   boundary and Human Context Supremacy direction.

## Positive Existing Foundations

- `DocumentReference` and `RepositoryRevisionScope` already model explicit
  source and revision semantics.
- `GitWorkspaceManager` can synchronize an explicit revision.
- `ProjectCommitRepository` provides source-scoped persisted commit history.
- `TaskSnapshotEvidenceResolver` proves that immutable task-snapshot resolution
  can remain separate from current-state resolution.
- `AiReferenceResolver` already exposes structured task-mapping failure codes.

## Acceptance Audit Position

| Area | Result | Evidence / remaining decision |
|---|---|---|
| Core dispatch and family ownership | PASS | Facade dispatches Git, document, Fact, Decision, Event, Story, Analysis, Artifact and Challenge families; family payloads remain specialized |
| Source/revision and no-fallback semantics | PASS | Slice 0 and Slice 2 tests plus source-scoped repository adapters |
| Current versus task snapshot | PASS | Current-state adapters reject `TASK_SNAPSHOT`; task snapshots remain owned by `TaskSnapshotEvidenceResolver` |
| REST/MCP transport boundary | PASS | Projections delegate to Core and preserve the canonical reference |
| Caller authorization | ACCEPTED LIMITATION | No application-wide boundary was identified; resolution is not authorization and untrusted exposure requires a follow-up security Story |
| Story 0139 legacy migration | DEFERRABLE | Legacy references remain intentionally supported; migration is useful but not required to accept the shared boundary |

## Review Recommendation

The implementation and Core architecture are accepted with the explicit
limitation that resolution does not imply authorization. Classify the required
security work as `FOLLOW_UP_SECURITY_STORY_REQUIRED`; keep legacy migration and
historical rewriting outside this slice.
