# Engineering Report - Story 0137

## Story

| Field | Value |
|---|---|
| ID | 0137 |
| Title | Source-Aware Canonical Evidence Reference Semantics |
| Status | Implementation slice complete; human review required |
| Commit | None created |
| Push | Not performed |

## Architecture Preserved

1. Java Core remains the deterministic authority for evidence identity and
   provenance.
2. Evidence families retain their own domain identities.
3. `AiReference` remains task-scoped and is not changed.
4. MCP remains a projection layer.
5. Ranking and composition remain consumer concerns.
6. Trust and authorization are not implied by reference validity.
7. Historical references remain readable and are not rewritten.

## Current Reference Rules

```text
Git commit       git:{sourceId}:{sha}
Changed file     diff:{sourceId}:{sha}:{path}
Repository file  file:{sourceId}:{path}@{revision}
Document         document:{sourceId}:{path}@{revision}
Fact             fact:{uuid}
Observation      observation:{uuid}
Domain entity    {kind}:{uuid}
```

Structure aggregates remain bounded projections with source and revision
metadata. They are not declared resolvable objects.

## Test Results

The focused combined backend run covered:

- `CommitDiffEvidenceCollectorTest`: 35;
- `RepositoryStructureCollectorTest`: 16;
- `DeterministicKnowledgeContextCollectorTest`: 2;
- `DocumentReferenceTest`: 9;
- `ProjectKnowledgeContextCollectorTest`: 16;
- `BudgetedDiverseEvidenceSelectorTest`: 10;
- `EngineeringContextContractMapperTest`: 15.

Total focused: 103 passing tests.

Full backend verification:

```text
1351 tests executed, 1351 passed
JaCoCo coverage checks: passed
Maven reactor build: SUCCESS
```

## Remaining Work

- Human review of the source-aware contract.
- Story 0138 authorization before shared resolver implementation.
- Future explicit treatment of legacy resolution behavior.
