# Story 0138 - Final Refinement Report

## Final Status

```text
SLICE_0_COMPLETE
SOURCE_POLICY_ALIGNED
SLICE_1_COMPLETE
EXPLICIT_FACADE_SEAM_IMPLEMENTED
SLICE_2_COMPLETE
GIT_DOCUMENT_ADAPTERS_IMPLEMENTED
SLICE_3_COMPLETE
FACT_ADAPTER_IMPLEMENTED
SLICE_4_COMPLETE
SNAPSHOT_BOUNDARY_VERIFIED
SLICE_5_COMPLETE
CONSUMER_PROJECTIONS_IMPLEMENTED
SLICE_6_COMPLETE
REPETITIVE_FAMILY_ADAPTERS_IMPLEMENTED
STORY_0138_IMPLEMENTATION_COMPLETE
STORY_0138_TECHNICALLY_VALIDATED
STORY_0138_ARCHITECTURE_ACCEPTED
KNOWN_APPLICATION_SECURITY_LIMITATION_RECORDED
FOLLOW_UP_SECURITY_STORY_REQUIRED
STORY_0139_DEFERRABLE
HUMAN_GIT_ACTION_REQUIRED
```

Human acceptance is recorded for the implementation and Core architecture with
the bounded application-security limitation below. No commit, push or merge
was performed. Only the authorized Slice 0 through Slice 6 changes were
implemented.

## Completed

- Reconstructed current repository, domain, snapshot, Git, document and MCP
  resolution behavior.
- Identified duplicated/ad-hoc resolution logic and preserved family ownership.
- Built the reference-family resolution matrix.
- Separated identity, resolution, authorization, trust, relevance, ranking,
  composition and transport responsibilities.
- Reconciled proposed failure classes with existing exceptions and result
  behavior.
- Classified temporal behavior for Git, files, documents, domain entities and
  task snapshots.
- Compared shared facade, registry/dispatch and existing-capability composition
  options without selecting one autonomously.
- Defined result-envelope alternatives and incremental implementation slices.
- Added `LEARN`, `PAIR` and `DELEGATE` recommendations.
- Enforced explicit source scope and deterministic `0 / 1 / N` source selection
  in the affected SCA, repository-structure and MCP paths.
- Added an explicit Core resolution facade with canonical Git/document parsing,
  strict dispatch, metadata envelope, family-owned payload marker and stable
  failure codes.
- Added source-scoped Git commit and revision-pinned document adapters with
  no-fallback revision checks.
- Added the first persisted domain adapter for `fact:{uuid}`, with explicit
  analysis scope and cross-analysis rejection.
- Verified that current-state adapters reject `TASK_SNAPSHOT` before repository
  access and that `TaskSnapshotEvidenceResolver` reads only the originating
  task snapshot.
- Added a thin REST projection at `/api/v1/evidence/resolve`.
- Added a thin MCP projection at `devlog://evidence/{reference}` that forwards
  the canonical reference without project-based reinterpretation.
- Added current-state adapters for Decisions, Engineering Events, Stories,
  Analyses, Artifacts and Challenges with family-owned payloads.
- Preserved repository authority and explicit `TASK_SNAPSHOT` rejection for all
  current-state family adapters.

## Resolved Finding

The first-active-source fallback was removed from:

- `AnalyzeStoryContextUseCase.resolveRevisionScope`;
- `RepositoryStructureCollector` when no explicit revision scope is supplied;
- MCP `ResourceSupport.requireActiveSourceId`.

Explicit `RepositoryRevisionScope` now resolves its exact source and revision.

## Final Security Decision

```text
FOLLOW_UP_SECURITY_STORY_REQUIRED
```

Resolution does not imply authorization. The current deployment does not
establish a complete application-user authentication and authorization boundary.
Evidence Resolution is accepted as operating inside the current trusted
deployment boundary only. It is not approved for arbitrary untrusted exposure.

Repository evidence established:

- no Spring Security application boundary was identified;
- no caller identity reaches `EvidenceResolutionFacade`;
- canonical resolution does not establish caller ownership;
- REST and MCP do not independently provide project authorization for this
  capability;
- equivalent backend capabilities also lack an established application-wide
  authentication/authorization boundary;
- Story 0138 therefore does not bypass a previously enforced application
  security guarantee.

The follow-up security Story must cover caller identity, authentication,
project/analysis ownership, endpoint authorization, REST/MCP propagation,
direct backend exposure and trusted versus untrusted deployment boundaries.
Technology and enforcement choices remain open for that Story.

`identity != resolution != authorization` remains an architectural invariant.

## Follow-Up Classification

Story 0139 is `USEFUL_BUT_DEFERRABLE`. No evidence currently shows that legacy
reference migration blocks the next DevLog capability.

## Acceptance Matrix

| Criterion area | Result | Evidence / note |
|---|---|---|
| Core dispatch, family ownership and metadata | PASS | Shared facade and family-owned payloads across implemented adapters |
| Ambiguous source, missing revision and no fallback | PASS | Deterministic source policy and explicit revision checks |
| Current versus originating-task snapshot | PASS | Separate current-state adapters and `TaskSnapshotEvidenceResolver` |
| REST/MCP projection boundary | PASS | Transport delegates to Core and preserves canonical identity |
| Failure semantics in scope | PASS | Focused parser, facade, adapter and projection tests |
| Application caller authorization | ACCEPTED LIMITATION | Explicitly outside Story 0138; resolution is not authorization and untrusted exposure requires a follow-up security Story |
| Story 0139 legacy migration | USEFUL_BUT_DEFERRABLE | Legacy forms remain supported; no evidence this blocks Story 0138 acceptance |

## Validation

```text
Focused Slice 6 tests: 22 passed
Full backend verification: 1385 passed
JaCoCo checks: passed
Backend Maven build: SUCCESS
MCP production package: SUCCESS
MCP test suite: 54 passed
git diff --check: passed
```


## Missing Work Intentionally Deferred

- Story 0139 legacy-reference migration.
- Historical reference rewriting.
- Full authorization architecture.
- Universal evidence model.
- MCP redesign and transport migration.
- RAG, vector retrieval and ContextPack.
