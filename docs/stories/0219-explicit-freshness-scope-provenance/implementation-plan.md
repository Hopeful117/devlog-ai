# Story 0219 - Implementation Plan

## Status

IMPLEMENTATION PLAN COMPLETE - AWAITING HUMAN APPROVAL

This plan is based on the approved Repository Analysis. It does not authorize
implementation, production contract changes, commits, or pushes.

## Implementation Objective

Complete the deterministic Core-owned metadata path for Story Context Analysis
so that freshness, execution scope, and evidence provenance remain explicit
from context construction through persistence and REST/MCP projection.

The implementation must prefer existing `EngineeringContext` freshness,
`RepositoryRevisionScope`, `RepositoryEvidence`, `AiTask.contextSnapshot`, and
`StoryContextAnalysis` boundaries. No parallel metadata model or retrieval path
should be introduced.

## Affected Modules

- `backend`: authoritative capture, persistence, validation, and use-case flow.
- `devlog-contracts`: only if an existing shared response contract requires an
  additive metadata type or field.
- `mcp-server`: adapter projection and serialization tests only.
- Repository documentation: Story-local lifecycle artifacts only; historical
  0114/0118 status text is not rewritten by this Story.

The Python AI Engine is not expected to change. If an existing callback contract
requires a compatibility adjustment, it must remain transport-only and must not
make Python authoritative for context metadata.

## Planned Contract

Use one additive, Core-produced analysis metadata projection containing the
following independent dimensions:

```text
executionScope:
  projectId
  sourceId
  resolvedRevision
  revisionSource

freshness:
  status
  repositoryRevision
  contextRevision
  sources[]
  warnings[]

provenance:
  evidence references and bounded source/path/revision metadata

limitations:
  explicit unknown/unavailable state and targeted verification guidance
```

The exact Java record/map shape must reuse the current response and contract
conventions. The implementation must not replace an existing response payload
when an additive envelope or field can preserve compatibility.

## Implementation Sequence

### 1. Verify the current baseline

- Confirm the 0114 freshness column, entity mapping, task snapshot capture and
  response projection already present on `main`.
- Confirm the 0118 `RepositoryRevisionScope` and document collector are on the
  active SCA path.
- Identify the current response compatibility boundary before changing any
  shared contract.

### 2. Complete freshness capture and persistence

- Preserve the complete authoritative freshness snapshot, including warnings,
  rather than only the status/revision subset.
- Ensure `NO_BASELINE` and `UNKNOWN` values with absent revisions remain valid
  through JSONB mapping and response serialization.
- Add or adjust the smallest persistence mapping required; do not add a
  migration if the existing schema already contains the required fields.
- Keep the captured snapshot immutable for the analysis execution.

### 3. Verify and connect execution scope

- Resolve `RepositoryRevisionScope` once per SCA execution.
- Carry the resolved source and revision alongside the freshness snapshot.
- Reject or explicitly mark unresolved scope; never silently fall back to a
  different revision after scope resolution.
- Ensure document evidence and freshness metadata cannot claim incompatible
  revisions without an explicit limitation.

### 4. Preserve provenance and grounding

- Keep the canonical document reference format:
  `document:{sourceId}:{normalizedPath}@{revision}`.
- Preserve source type, repository path, revision and evidence classification
  through selection and projection.
- Reuse existing grounding allow-list validation.
- Do not allow metadata fields or adapters to create new evidence relationships.

### 5. Update read projections

- Expose the additive metadata consistently through the existing REST response
  and MCP tool projection.
- Preserve existing response fields and error semantics where compatibility is
  required.
- Keep full repository content and prompts out of normal diagnostics; expose
  bounded metadata only.

### 6. Add focused tests

- Freshness snapshot capture in `AnalyzeStoryContextUseCase`.
- Callback persistence from `AiTask.contextSnapshot`.
- Nullable `NO_BASELINE` and `UNKNOWN` serialization.
- Warning preservation.
- Single-scope propagation through the repository context pipeline.
- Revision-aware document reference and provenance preservation.
- REST and MCP additive projection behavior.
- Grounding subset regression proving metadata does not authorize evidence.

## Estimated Files and Components

These are implementation targets, not an authorization to modify all of them:

- `backend/.../story/.../AnalyzeStoryContextUseCase*`
- `backend/.../ai/.../AiTask*` and callback persistence services
- `backend/.../story/.../StoryContextAnalysis*`
- `backend/.../story/.../StoryContextAnalysisQueryService*`
- existing freshness mapper/contract classes under
  `backend/.../engineeringcontext/...`
- existing repository scope/document collector classes under
  `backend/.../repositorycontext/...`
- existing REST controller and response DTO/package for Story Context Analysis
- existing MCP client/tool and their tests
- focused tests adjacent to each changed component

The exact paths must be confirmed against the current `main` checkout before
editing. The three unrelated dirty MCP files listed in the Repository Analysis
must not be included.

## Persistence and API Impact

### Persistence

Expected to reuse the existing 0114 `context_freshness` JSONB persistence. A
new migration is permitted only if the current schema cannot preserve the
approved metadata contract after direct inspection. Any migration must be
backward-compatible with existing nullable rows.

### API and MCP

The preferred approach is an additive projection. Replacing the existing raw
analysis payload is not allowed without explicit compatibility evidence and
human approval. REST and MCP must expose equivalent metadata semantics while
remaining thin adapters over Core.

### AI Engine

No prompt, model, retrieval, or AI schema change is planned. Core-owned metadata
may be carried as execution context, but Python must not generate, recalculate,
or authorize freshness, scope, provenance, or grounding.

## Validation Plan

Run focused verification first:

```bash
./backend/mvnw -pl backend test -Dtest=<changed Story Context tests>
```

Then run the applicable broader checks:

```bash
./backend/mvnw -pl backend -am clean verify -B
cd ai-engine && python -m pytest -q
git diff --check
```

The exact test selector must be finalized after inspecting the current test
class names. If the AI Engine is unchanged, its full regression remains a
compatibility check rather than evidence of an AI implementation change.

## Risks and Controls

- **Null serialization failure:** use explicit nullable DTO handling and tests
  for every freshness status with absent revisions.
- **Warning loss:** test the complete metadata capture rather than only query
  reconstruction from hand-built maps.
- **Mixed revisions:** compare scope and evidence revision at the Core boundary
  and emit a bounded limitation when they differ.
- **Breaking response change:** preserve existing fields and test JSON shape
  compatibility before changing adapters.
- **Grounding expansion:** retain existing allow-list subset checks and add a
  regression test for metadata-only references.
- **Scope creep into loss ledger:** defer truncation/loss accounting to the
  next epic Story.
- **Unrelated worktree contamination:** inspect and stage only Story 0219 files
  if implementation is later authorized.

## Explicitly Unchanged

- Java/Core authority boundaries.
- Python probabilistic execution boundary.
- Retrieval, ranking, and knowledge-selection policy.
- Budget limits and provider retry policy.
- AI prompts and response semantics.
- Existing trust and human-validation workflow.
- Historical Story 0114 and 0118 lifecycle artifacts.
- The unrelated dirty MCP files.

## Readiness

The plan is implementation-ready subject to human approval of this exact
artifact version. Approval of this plan is a separate gate and is required
before any production file is modified.
