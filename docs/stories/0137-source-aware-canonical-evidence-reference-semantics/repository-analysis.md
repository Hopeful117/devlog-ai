# Repository Analysis - Story 0137

## Story Summary

Story 0137 establishes source-aware evidence identity semantics without
introducing a universal `Evidence` entity. The implementation boundary is the
existing `RepositoryEvidence.reference` string plus explicit provenance and
metadata rules. The first slice covers changed-file evidence, repository
structure evidence, Facts and Observations, and the identity/trust/ranking
invariants.

## Relevant Implementation

| Area | Current implementation | Story 0137 finding |
|---|---|---|
| Evidence record | `RepositoryEvidence.reference` is a string | Keep the existing shape for this slice |
| Provenance | `EvidenceProvenance(sourceType, repositoryLocation, originatingFile, identifier)` | Provenance is not canonical identity |
| Source | `Source.id` identifies a repository source | Required for repository-scoped identity |
| Revision | `RepositoryRevisionScope.resolvedRevision` | Intrinsic for files/documents, not every domain entity |
| Git commits | `git:{sourceId}:{sha}` | Already source-aware |
| Changed files | Previously grouped by path and emitted `diff:{sha}:{path}` | Now grouped by `(sourceId, path)` and emits `diff:{sourceId}:{sha}:{path}` |
| Repository files | Previously emitted path-only references | Now emits `file:{sourceId}:{path}@{revision}` |
| Structure aggregates | Short references such as `module:summary` | Explicitly bounded non-expandable projections |
| Facts | Could reuse source/evidence reference as primary reference | Now always uses `fact:{uuid}` |
| Observations | Already used `observation:{uuid}` | Supporting Facts remain related references |
| Documents | `DocumentReference` value object | Existing source/path/revision semantics preserved |

## Important Existing Constraints

- `BudgetedDiverseEvidenceSelector` deduplicates by `reference`.
- `EngineeringContextContractMapper` maps trust from evidence kind and
  provenance, not from reference syntax.
- MCP resource URIs are projections and are not canonical identity.
- Existing persisted and historical strings must not be rewritten.
- Shared resolution belongs to Story 0138 and remains out of scope.

## Identified Problems

1. `CommitDiffEvidenceCollector` grouped files by path, which could merge two
   sources containing the same path.
2. Its former reference used project identity or omitted source identity rather
   than preserving the owning `Source.id`.
3. `RepositoryStructureCollector` silently selected `sources.getFirst()` when
   multiple active sources existed.
4. Structure file references lacked source/revision identity.
5. Structure aggregate references are summaries, not individually expandable
   repository objects.
6. Fact projection could use a source path or external evidence reference as
   the Fact's primary identity.

## Historical Compatibility Classification

The following are legacy/display forms, not rewritten by this Story:

```text
diff:{sha}:{path}
file:{path}
```

They remain readable as historical values. New code must not infer a source or
revision from them.

## Governing Decisions

- ADR-038: deterministic collector boundary.
- ADR-063: canonical evidence semantics, source identity, temporal semantics,
  and shared resolution boundary.
- ADR-068: typed AI-facing references remain task-scoped and separate.
