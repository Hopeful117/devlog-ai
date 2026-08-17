# Story 0078 — Knowledge Lifecycle Diagnostic Service — Implementation Report

## Summary

* Delivered a **proposal-centric, deterministic, read-only** application diagnostic
  (`KnowledgeLifecycleDiagnosticService`) that answers whether a `ValidatableProposal`'s
  governed knowledge lifecycle is complete, consuming the lineage made exact by Story 0077.
* Minimal production surface: 4 DTOs + 1 interface + 1 implementation — **no migration,
  no entity change, no REST/MCP/UI, no repair**.
* All lifecycle stages are reconstructed from already-persisted domain relationships
  (`DecisionRepository.findByProposalId`, `Insight/EngineeringEventRepository.findByProposalIdIn`,
  `ValidationRepository.findByProposalId`); no new storage or repository methods.
* Deterministic truth-table engine (ADR-058 §14), PROPOSED→PENDING (never MISSING),
  unsupported types → NOT_APPLICABLE (never BROKEN).
* Full backend suite green: **789 tests, 0 failures** (baseline 770 + 18 + code-review unit test).

## Delivered Artifacts

* `lineage/dto/LineageStageStatus.java` — `PRESENT | PENDING | NOT_APPLICABLE | MISSING | INCONSISTENT`.
* `lineage/dto/KnowledgeLifecycleStatus.java` — `COMPLETE | BROKEN | NOT_APPLICABLE`.
* `lineage/dto/KnowledgeLifecycleStageResponse.java` — stage name, status, artifact id, detail.
* `lineage/dto/KnowledgeLifecycleDiagnosticResponse.java` — proposal id, type, proposal status,
  lifecycle status, stages, findings.
* `lineage/service/KnowledgeLifecycleDiagnosticService.java` — `diagnose(UUID)`.
* `lineage/service/KnowledgeLifecycleDiagnosticServiceImpl.java` — read-only resolution
  + truth-table evaluation.
* Tests: `KnowledgeLifecycleDiagnosticServiceImplTest` (15 Mockito unit tests),
  `KnowledgeLifecycleDiagnosticPostgresIntegrationTest` (4 Testcontainers integration tests).
  One unit test was added during code review to close the PROPOSED+promoted-knowledge
  inconsistency (promoted stage INCONSISTENT / BROKEN).
* Docs: `story.md`, `repository-analysis.md`, `implementation-plan.md` (already authored);
  this report + `engineering-report.md`.

## Validation

### Backend

```
Tests run: 789, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

* Unit (15): full truth table (PROPOSED/REJECTED/ACCEPTED × validation/promotion outcomes),
  including PROPOSED+promoted-knowledge → BROKEN/INCONSISTENT, per-type dispatch
  (Decision/Insight/Event), unsupported type NOT_APPLICABLE, missing proposal
  → `EntityNotFoundException`, read-only (no `save` ever invoked).
* Integration (4, Testcontainers):
  * promote an ACCEPTED `ENGINEERING_DECISION` via `ValidationService` → diagnostic COMPLETE,
    both stages PRESENT, no findings;
  * ACCEPTED proposal with no promoted Decision → BROKEN + invariant-violation finding;
  * PROPOSED proposal (no validation) → COMPLETE, validation PENDING, no findings;
  * REJECTED + REJECTED Validation → COMPLETE, no findings.

## Acceptance Criteria Verification

| # | Criterion | Status |
|----|---|---|
| 1 | ACCEPTED + artifact present → COMPLETE, both stages PRESENT | ✅ unit + integration |
| 2 | ACCEPTED + no artifact → BROKEN + invariant finding | ✅ unit + integration |
| 3 | PROPOSED → COMPLETE, validation PENDING (never MISSING) | ✅ unit + integration |
| 4 | REJECTED + REJECTED Validation → COMPLETE; missing Validation / promoted artifact → BROKEN | ✅ unit + integration |
| 5 | Correct per-type dispatch (Decision/Insight/Event) | ✅ unit |
| 6 | Unsupported types → NOT_APPLICABLE, never BROKEN | ✅ unit |
| 7 | Deterministic and read-only (no writes) | ✅ unit + integration |
| 8 | Full suite green (788) | ✅ |
| 9 | No migration added (Flyway stays at V43) | ✅ verified |
| 10 | No generic lineage table / MCP / REST / UI / repair | ✅ verified |

## Final Assessment

All 10 acceptance criteria satisfied. The implementation is small, deterministic and reads
only from already-persisted ADR-058 V1 lineage; it introduces no infrastructure and modifies
no existing production code. It is the first application-level consumer of Story 0077's
provenance and a prerequisite for Phase 2 context-projection lineage and future MCP exposure.