# Engineering Report — Story 0023: Commit-Scoped Evidence for Grounding

## Summary

Story 0023 bridges the architectural gap between `CommitDiffEvidenceCollector` (which produces file-level `RepositoryEvidence`) and the grounding contract (which expects `FactSnapshot` UUIDs in `allowedSupportingFactIds`). A new `CommitScopedFactCollector` produces commit-scoped facts from Git history that the knowledge selection pipeline can rank and include in the grounding contract, enabling the AI Engine to generate proposals with valid `supportingFactIds`.

## Acceptance Criteria Status

| AC | Description | Status |
|---|---|---|
| AC-1 | Commit-diff fact types produced by the collector | ✅ Complete |
| AC-2 | Evidence collection integration with existing file-level types | ✅ Complete |
| AC-3 | Grounding contract coverage via knowledge selection scoring | ✅ Complete |
| AC-4 | Live validation (end-to-end pipeline) | ⚠️ Deferred — code complete, not live-tested |
| AC-5 | Backward compatibility | ✅ Complete |

## Architecture Delivered

### New Collector
- `CommitScopedFactCollector` — implements `KnowledgeCollector`, reads from `ProjectCommitRepository`, produces `COMMIT_DIFF_SUMMARY`, `COMMIT_CHANGES_MODULE`, `COMMIT_ADDS_FEATURE`, `COMMIT_FIXES_BUG`, and `COMMIT_REFACTORS_CODE` facts as `FactSnapshot` items with fingerprint-based deduplication

### Extended Enums
- `CollectorType.COMMIT_SCOPED` — new collector type identifier
- 5 new `FactType` values — no schema migration needed (stored as STRING)

### Knowledge Selection Scoring
- `KnowledgeSelectionServiceImpl.factScore()` — `analyze-engineering-event` intent scores commit-scoped facts at 100, all others at 10

## Files Changed

| File | Change |
|---|---|
| `CollectorType.java` | Add `COMMIT_SCOPED` |
| `FactType.java` | Add 5 new commit-scoped types |
| `CommitScopedFactCollector.java` | New collector |
| `CommitScopedFactCollectorTest.java` | 7 tests |
| `KnowledgeSelectionServiceImpl.java` | Scoring for new types |

## Test Results

- Backend: 485 tests, 0 failures (1 pre-existing `contextLoads` error — PostgreSQL unavailable)
- `CommitScopedFactCollectorTest`: 7 tests passing
- Frontend: no changes
- AI Engine: no changes

## Documentation Reconciliation

Main architecture docs (`knowledge-model.md`, `pipeline.md`, `architecture.md`) operate at a high abstraction level and do not enumerate specific collectors or fact types. No updates required — the commit-scoped collection is an implementation detail within the knowledge collection subsystem.

## Verification

* Backend: `./mvnw clean verify` — 486 tests, 0 failures, 1 pre-existing error (`contextLoads` — PostgreSQL unavailable).
* Frontend: no changes.
* AI Engine: no changes.
* SonarQube `devlog-ai`: Quality Gate **OK** — new-code coverage 80.3%, new duplication 0.0%, 0 new violations.
* `CommitScopedFactCollectorTest`: 7 tests passing.
* `KnowledgeSelectionServiceTest`: 2 tests passing (including new scoring test for `analyze-engineering-event`).

## Risks

- **Low**: New collector depends on `ProjectCommitRepository` data availability. If history import hasn't occurred, the collector produces zero facts (graceful degradation).
- **Low**: Commit message parsing is heuristic (prefix detection). Non-conventional commits won't produce feature/bug/refactor facts, but will still produce `COMMIT_DIFF_SUMMARY`.

## What's Next

AC-4 (live validation) requires running the full engineering event pipeline against a real project to verify that commit-scoped facts appear in the grounding contract and that the AI Engine generates proposals with valid `supportingFactIds`. This is deferred to a follow-up validation step.
