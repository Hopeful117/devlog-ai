# Story 0137 - Implementation Report

## Status

`IMPLEMENTATION SLICE COMPLETE - READY FOR HUMAN REVIEW`

## Implementation Summary

Implemented the first source-aware evidence identity slice without changing
the `RepositoryEvidence` record shape or introducing a universal Evidence
entity. Changed-file evidence is now source-aware at grouping and reference
creation time. Repository structure collection rejects ambiguous active-source
selection, file evidence carries source and revision identity, and aggregate
structure summaries are explicitly non-expandable projections.

Facts and Observations now retain domain identity independently from their
supporting repository references. Ranking and trust tests prove that reference
identity does not imply ranking, composition or trust.

## Files Changed

| File | Change |
|---|---|
| `CommitDiffEvidenceCollector.java` | Group and reference changed files by source and path |
| `RepositoryStructureCollector.java` | Reject source ambiguity; add file/projection semantics |
| `DeterministicKnowledgeContextCollector.java` | Separate Fact identity from supporting references |
| `CommitDiffEvidenceCollectorTest.java` | Cross-source identity regression coverage |
| `RepositoryStructureCollectorTest.java` | Ambiguity and structure-reference coverage |
| `DeterministicKnowledgeContextCollectorTest.java` | Fact/Observation identity coverage |
| `BudgetedDiverseEvidenceSelectorTest.java` | Ranking identity preservation coverage |
| `EngineeringContextContractMapperTest.java` | Trust separation coverage |
| `docs/architecture.md` | Complete collector/reference matrix and legacy compatibility rules |
| `story.md` | Status updated after authorized implementation slice |

## Acceptance Criteria Evidence

- Repository source ownership is unambiguous for changed files and structure
  file evidence.
- Multiple active repository sources fail explicitly.
- Document source/path/revision semantics remain unchanged.
- Ranking changes preserve evidence identity.
- Reference syntax does not promote technical evidence to trusted knowledge.
- Historical references are not rewritten.
- No universal Evidence entity was introduced.
- No transport-specific identity became canonical.

## Validation

```text
103 tests executed, 103 passed
Full backend verification: 1351 tests executed, 1351 passed
JaCoCo coverage checks: passed
Maven reactor build: SUCCESS
git diff --check: passed
```

## Not Executed

- Story 0138 shared resolver implementation.
- Historical data migration.
- Commit, push, merge or Story acceptance.

## Readiness

`READY_FOR_HUMAN_REVIEW`
