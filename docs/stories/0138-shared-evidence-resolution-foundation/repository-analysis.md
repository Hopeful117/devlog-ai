# Repository Analysis - Story 0138

## Status

`ANALYSIS COMPLETE - IMPLEMENTATION COMPLETE - ACCEPTANCE AUDIT`

## Story Boundary

Story 0137 is the baseline for canonical evidence identity. Story 0138 may
define a shared deterministic resolution capability, but it must not introduce
a universal `Evidence` aggregate or move ranking, trust, authorization,
composition or transport policy into that capability.

## Existing Resolution Capabilities

| Component | Current behavior | Scope and limitation |
|---|---|---|
| `RepositoryEvidenceResolverImpl` | Reconstructs repository paths from Insight proposal Fact/Observation lineage | Temporal-assessment-specific; does not resolve arbitrary canonical references |
| `TaskSnapshotEvidenceResolver` | Resolves typed repository evidence against an immutable `AiTask` snapshot and validates locators/digests | Task-scoped historical resolution; must not become a current-context resolver |
| `AiReferenceResolver` | Resolves `(type, ref, scope)` through an execution mapping snapshot | AI mapping and grounding capability authority; not project-wide evidence identity |
| `DocumentReference` | Parses and creates source/path/revision document references | Identity value object; no general expansion service |
| `RepositoryRevisionScope` | Carries project/source/revision/workspace scope for one SCA execution | Correct temporal input for collectors; not a resolution result |
| `GitWorkspaceManager` | Synchronizes explicit revisions and verifies source-scoped file presence | Repository infrastructure; no shared family dispatch |
| `ProjectCommitRepository` | Loads source-scoped commits and changed files | Persisted history authority; no canonical-reference facade |
| `DocumentBodyCollector` | Reads bounded related documents at one explicit revision | Collector/projection; no arbitrary document expansion endpoint |
| MCP resources | Resolve known IDs through REST and map payloads to MCP resources | Transport adapters; some contain lookup and active-source policy |

## Current Repository Context

`RepositoryEvidence` is a string-reference projection with summary, provenance,
related references, optional bounded content and symbols. Current collectors
produce:

- `git:{sourceId}:{sha}` for commits;
- `diff:{sourceId}:{sha}:{path}` for changed files;
- `file:{sourceId}:{path}@{revision}` for canonical repository files;
- `document:{sourceId}:{path}@{revision}` for revision-pinned documents;
- `fact:{uuid}`, `observation:{uuid}`, `insight:{uuid}`, `decision:{uuid}`,
  `event:{uuid}`, `story:{uuid}`, `analysis:{uuid}`, `artifact:{uuid}` and
  `challenge:{uuid}` for domain projections;
- bounded structure tokens such as `module:summary`, `module:{name}`,
  `source:directories`, `config:files` and `extensions:distribution`.

The last category is explicitly non-expandable. Its reference identifies a
bounded projection, not a repository object that a resolver may enumerate.

## Duplicated Resolution Behavior

1. `EngineeringContextContractMapper` derives MCP resource URIs from evidence
   kind, provenance identifiers and commit-reference parsing.
2. `ResourceSupport` performs project ownership checks, active Insight lookup
   and active-source selection.
3. `CommitContextResource` selects a source before calling the history endpoint.
4. `RepositoryContextAdapter` parses commit/diff references for commit-window
   filtering.
5. `TemporalAssessmentServiceImpl` reconstructs paths from Insight lineage and
   compares baseline/current source revisions.
6. `DocumentBodyCollector` creates revision-pinned references and reads files
   directly from a synchronized workspace.
7. `TaskSnapshotEvidenceResolver` scans the selected snapshot by reference and
   validates content against the task mapping.

These behaviors have different authority and temporal scopes. The future shared
capability should compose or adapt them, not replace them with a generic domain
lookup model.

## Historical Blocking Repository Contradiction

The baseline requires ambiguous source ownership to fail deterministically.
However:

- `AnalyzeStoryContextUseCase.resolveRevisionScope` uses `sources.getFirst()`;
- MCP `ResourceSupport.requireActiveSourceId` applies the same created-at/id
  ordering and returns the first active source.

This conflicted with Story 0137's no-first-source rule. Slice 0 removed the
first-active-source fallback. The affected paths now fail deterministically for
zero or multiple active sources, and explicit `RepositoryRevisionScope` values
use their exact source and revision.

## Accepted Current Audit Limitation

Story 0138 intentionally does not define the complete application
authorization architecture. The REST projection delegates to Core resolution,
but this audit did not identify a repository-wide caller authorization boundary
around the new endpoint. Resolution must not be interpreted as authorization.
The architecture is accepted within the current trusted deployment boundary;
arbitrary untrusted exposure requires a dedicated application-security Story.

Security classification:

```text
FOLLOW_UP_SECURITY_STORY_REQUIRED
```

## Governing Evidence

- ADR-063: shared retrieval/expansion semantics, consumer-owned composition and
  Human Context Supremacy.
- ADR-068: Core-owned typed AI references remain execution-scoped and separate
  from canonical project evidence identity.
- Story 0137: source-aware canonical identity, legacy no-inference behavior and
  bounded structure projections.
