# Implementation Report — Story 0023

## Outcome

Implemented the approved plan to bridge the architectural gap between `CommitDiffEvidenceCollector` (file-level `RepositoryEvidence`) and the grounding contract (which requires `FactSnapshot` UUIDs in `allowedSupportingFactIds`). A new `CommitScopedFactCollector` produces commit-scoped facts from Git history that the knowledge selection pipeline can rank and include in the grounding contract.

All automated quality controls pass. 486 backend tests, 0 failures. SonarQube Quality Gate OK (80.3% new-code coverage, 0 violations).

## Delivered

* Added `CollectorType.COMMIT_SCOPED` enum value.
* Added 5 new `FactType` values: `COMMIT_DIFF_SUMMARY`, `COMMIT_CHANGES_MODULE`, `COMMIT_ADDS_FEATURE`, `COMMIT_FIXES_BUG`, `COMMIT_REFACTORS_CODE`. No schema migration needed (stored as STRING).
* Added `CommitScopedFactCollector` — `@Component` implementing `KnowledgeCollector`, reads from `ProjectCommitRepository`, produces commit-scoped facts with SHA-256 fingerprint-based deduplication, max 20 facts per collection.
* Added knowledge selection scoring: `analyze-engineering-event` intent scores commit-scoped fact types at 100, all others at 10.
* Added `CommitScopedFactCollectorTest` — 7 unit tests covering summary, module grouping, refactoring detection, empty state, deduplication, evidence references, and negative detection.
* Added `KnowledgeSelectionServiceTest` — 1 new test verifying commit-scoped facts rank before non-commit facts for the `analyze-engineering-event` intent.

## Files Changed

| File | Change |
|---|---|
| `CollectorType.java` | Add `COMMIT_SCOPED` |
| `FactType.java` | Add 5 new commit-scoped types |
| `CommitScopedFactCollector.java` | New collector (refactored to reduce cognitive complexity) |
| `CommitScopedFactCollectorTest.java` | 7 tests |
| `KnowledgeSelectionServiceImpl.java` | +3 lines scoring for new types |
| `KnowledgeSelectionServiceTest.java` | +1 test for new scoring |

## Validation

* Backend: `./mvnw clean verify` — 486 tests, 0 failures, 1 pre-existing error (`contextLoads` — PostgreSQL unavailable).
* Frontend: no changes.
* AI Engine: no changes.
* SonarQube `devlog-ai`: Quality Gate OK — new-code coverage 80.3%, new duplication 0.0%, 0 new violations.

## Documentation Reconciliation

Main architecture docs (`knowledge-model.md`, `pipeline.md`, `architecture.md`) operate at a high abstraction level and do not enumerate specific collectors or fact types. No updates required — the commit-scoped collection is an implementation detail within the knowledge collection subsystem.
