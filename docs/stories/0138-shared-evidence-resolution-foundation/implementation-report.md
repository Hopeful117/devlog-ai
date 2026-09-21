# Story 0138 - Implementation Report

## Status

`STORY_0138_IMPLEMENTATION_COMPLETE - STORY_0138_TECHNICALLY_VALIDATED - STORY_0138_ARCHITECTURE_ACCEPTED - FOLLOW_UP_SECURITY_STORY_REQUIRED`

Slice 0 source-selection alignment, Slice 1's explicit resolution seam, Slice
2's Git/document adapters, Slice 3's analysis-scoped Fact adapter, Slice 4's
snapshot-boundary verification, Slice 5's REST/MCP consumer projections and
Slice 6's six persisted-family adapters were implemented.

## Repository State

- Branch: `main`
- Existing worktree changes: present and unrelated changes were not modified.
- Story 0138 changes in this phase: completed Slices 0 through 6; unrelated
  worktree changes were not modified.
- Commit created: `NO`
- Push performed: `NO`

## Governance

- Governing decisions: ADR-063, ADR-068 and Story 0137 baseline.
- Decisions resolved for this phase: deterministic multi-source failure,
  explicit facade boundary, and envelope plus family-owned payload contract.

## Files Added

- `repository-analysis.md`: current resolution architecture and audit findings.
- `implementation-plan.md`: implementation slices and validation plan.
- `engineering-report.md`: complete Story 0138 refinement report.
- `code-review.md`: architecture review and blocking finding.
- `final-report.md`: final refinement status and next decision.
- `implementation-report.md`: implementation and validation record.

## Production Changes

- Added `SourceSelectionException` with `SOURCE_UNAVAILABLE` and
  `AMBIGUOUS_SOURCE` reasons.
- Updated SCA revision-scope selection to enforce the `0 / 1 / N` rule.
- Updated repository structure collection to honor explicit
  `RepositoryRevisionScope` source/revision and to reject implicit ambiguity.
- Updated MCP active-source selection to reject multiple active sources without
  ordering-based fallback.
- Added backend error mappings and explicit MCP error messages.
- Added the Core resolution seam under `evidence.resolution`: canonical
  Git/document parsing, explicit current/task-snapshot mode, family dispatch,
  metadata envelope, family-owned payload marker and stable failure codes.
- Added focused parser and facade tests for canonical identity preservation,
  unsupported/unknown references, strict dispatch and duplicate registration.
- Added source-scoped `ProjectCommit` resolution with changed-file metadata.
- Added revision-pinned document resolution through `WorkspaceManager` and
  `SecureRepositoryContentReader`, including explicit rejection of revision
  fallback and snapshot-mode current-state lookup.
- Added `fact:{uuid}` resolution with explicit `analysisId` scope and Fact
  payload preservation. Cross-analysis and unscoped access are rejected.
- Added tests proving current-state adapters reject `TASK_SNAPSHOT` before
  repository access and that task snapshot resolution uses only the originating
  `AiTask` snapshot.
- Added the thin REST endpoint `/api/v1/evidence/resolve` over the Core facade.
- Added the thin MCP `evidence-resolution` resource, preserving the canonical
  reference as the only identity input.
- Added current-state resolvers and family-owned payloads for Decisions,
  Engineering Events, Stories, Analyses, Artifacts and Challenges.
- Added explicit repository entity graphs for resolver reads that require
  project/proposal ownership metadata.

## Tests and Commands

Focused Slice 6 tests: 22 passed.

Full backend verification: 1,385 passed; JaCoCo checks passed; Maven build
succeeded.

Full MCP verification: 54 passed; production compilation/package also
succeeded.

## Resolved Contradiction

`AnalyzeStoryContextUseCase.resolveRevisionScope`,
`RepositoryStructureCollector` and MCP `ResourceSupport.requireActiveSourceId`
now reject multiple active sources and distinguish zero sources. Explicit
repository revision scopes use their exact source and do not consult active
source ordering.

## Readiness

```text
SLICE_6_COMPLETE
SOURCE_POLICY_ALIGNED
EXPLICIT_FACADE_SEAM_IMPLEMENTED
GIT_DOCUMENT_ADAPTERS_IMPLEMENTED
FACT_ADAPTER_IMPLEMENTED
SNAPSHOT_BOUNDARY_VERIFIED
CONSUMER_PROJECTIONS_IMPLEMENTED
REPETITIVE_FAMILY_ADAPTERS_IMPLEMENTED
STORY_0138_IMPLEMENTATION_COMPLETE
STORY_0138_TECHNICALLY_VALIDATED
STORY_0138_ARCHITECTURE_ACCEPTED
KNOWN_APPLICATION_SECURITY_LIMITATION_RECORDED
FOLLOW_UP_SECURITY_STORY_REQUIRED
STORY_0139_DEFERRABLE
HUMAN_GIT_ACTION_REQUIRED
```

## Final Acceptance Limitation

Resolution does not imply authorization. The current deployment does not
establish a complete application-user authentication and authorization
boundary. Evidence Resolution is accepted within the current trusted
deployment boundary only; arbitrary untrusted exposure requires a dedicated
application-security Story.

The security classification is `FOLLOW_UP_SECURITY_STORY_REQUIRED`. No
authentication or authorization architecture is selected or implemented here.
