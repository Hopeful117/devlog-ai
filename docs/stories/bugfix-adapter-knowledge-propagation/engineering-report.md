# Engineering Report — Adapter Knowledge Propagation Bugfix

## Story

Bugfix: `RepositoryContextAdapter.synthesizeAnalysisContext()` drops `validatedEngineeringEvents`, `openChallenges`, and `knowledgeRelations` from `ProjectContextSnapshot` when synthesizing the `AnalysisContext` for the Engineering Story Context path.

## Problem

Three fields present in `ProjectContextSnapshot` were not propagated into the synthesized `AnalysisContext`:

| Field | Root cause |
|---|---|
| `validatedEngineeringEvents` | Adapter called 11-arg `AnalysisContext` constructor that hardcoded `List.of()` |
| `openChallenges` | Field did not exist in `AnalysisContext` record |
| `knowledgeRelations` | Field did not exist in `AnalysisContext` record |

## Solution

1. Added `openChallenges` and `knowledgeRelations` fields to `AnalysisContext` record (matching existing snapshot inner-record pattern with fully-qualified types to avoid circular dependency).
2. Updated `RepositoryContextAdapter.synthesizeAnalysisContext()` to pass all three snapshot fields via the canonical constructor.
3. Updated `AnalysisContextServiceImpl` to pass the two new fields in the normal analysis path for consistency.
4. Added 3 regression tests proving data survival through the adapter.

## Verification

- **Compilation**: OK
- **Tests**: 518 pass, 0 failures
- **New tests**: 3 (propagation of all 3 fields)
- **SonarQube**: Not run (bugfix scope — can be verified separately)

## Key Design Decisions

- **Fully-qualified inline types** for `ChallengeSnapshot` and `KnowledgeRelationSnapshot` — avoids circular dependency between `analysis.context` and `projectcontext` packages, consistent with existing `EngineeringEventSnapshot` pattern.
- **Convenience constructors preserve backward compatibility** — all existing callers (including ~15 test files) continue to work without changes.
- **No downstream behavior change** — no RepositoryContext collector currently reads these fields. The fix repairs the propagation contract for future consumers.

## Artifacts

- `repository-analysis.md` — root cause analysis
- `implementation-plan.md` — 5-step plan
- `implementation-report.md` — execution record
- `code-review.md` — independent verification
- `engineering-report.md` — this file
