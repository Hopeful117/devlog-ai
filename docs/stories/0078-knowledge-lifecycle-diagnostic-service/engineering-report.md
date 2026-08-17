# Story 0078 — Knowledge Lifecycle Diagnostic Service — Engineering Report

## Status

Reported

## Story

| Field | Value |
|-------|-------|
| Number | 0078 |
| Title | Knowledge Lifecycle Diagnostic Service |
| Status | Done (implementation complete, awaiting commit) |
| Acceptance Criteria | 10/10 satisfied |

## Scope Delivered

### Implemented

* `lineage/dto/LineageStageStatus.java` — `PRESENT | PENDING | NOT_APPLICABLE | MISSING | INCONSISTENT` (ADR-058 §14).
* `lineage/dto/KnowledgeLifecycleStatus.java` — `COMPLETE | BROKEN | NOT_APPLICABLE`.
* `lineage/dto/KnowledgeLifecycleStageResponse.java` — stage, status, artifact id, detail.
* `lineage/dto/KnowledgeLifecycleDiagnosticResponse.java` — proposal key, type, proposal status, lifecycle status, stages, findings.
* `lineage/service/KnowledgeLifecycleDiagnosticService.java` (interface) + `KnowledgeLifecycleDiagnosticServiceImpl.java`.
* Tests: `KnowledgeLifecycleDiagnosticServiceImplTest` (14), `KnowledgeLifecycleDiagnosticPostgresIntegrationTest` (4).

### Explicitly not introduced (per ADR-058 / this slice)

* No migration (Flyway stays at **V43**), no entity change, no repository change.
* No generic lineage storage (`LineageNode`/`LineageEdge`), no `KnowledgeRelation` redesign, no graph database.
* No REST controller, no MCP tool, no frontend/UI.
* No Phase 2 context-projection lineage (ProjectContext / RepositoryEvidence / EngineeringContext / MCP), no "produced vs selected" distinction.
* No automatic repair / artifact creation.
* No AI / LLM-based diagnosis (fully deterministic).
* No cross-project lineage.

## Design Outcome

### Proposal-centric, reconstructible lineage

Story 0077 closed the last V1 NOT_RECONSTRUCTIBLE edge; Story 0078 consumes it. The service is
keyed by `proposalId` (ADR-058 §13) and resolves:

```text
ValidatableProposal
  ├── Validation            ValidationRepository.findByProposalId
  └── Promoted Knowledge    per type:
                              ENGINEERING_DECISION → DecisionRepository.findByProposalId
                              INSIGHT              → InsightRepository.findByProposalIdIn
                              ENGINEERING_EVENT    → EngineeringEventRepository.findByProposalIdIn
```

Every edge is DIRECTLY_STORED; no reconstruction gaps, no duplicated storage. The whole service
runs `@Transactional(readOnly = true)`, mirroring `ProposalReviewService`.

### Deterministic truth table (ADR-058 §14 semantics)

| ProposalStatus | Validation present | Promotion count | Overall |
|---|---|---|---|
| PROPOSED | no | 0 | COMPLETE (validation PENDING) |
| PROPOSED | yes | 0 | BROKEN (INCONSISTENT) |
| PROPOSED | yes / no | ≥1 | BROKEN (promoted INCONSISTENT — knowledge before acceptance) |
| REJECTED | no | 0 | BROKEN (validation MISSING) |
| REJECTED | REJECTED | 0 | COMPLETE (promotion NOT_APPLICABLE) |
| REJECTED | yes | ≥1 | BROKEN (INCONSISTENT) |
| ACCEPTED | no | any | BROKEN (validation MISSING) |
| ACCEPTED | ACCEPTED | 0 | BROKEN — **invariant violation** |
| ACCEPTED | ACCEPTED | 1 | COMPLETE |
| ACCEPTED | ACCEPTED | >1 | BROKEN (INCONSISTENT) |
| ACCEPTED | REJECTED | any | BROKEN (INCONSISTENT) |

PROPOSED is PENDING (not MISSING) per ADR-058 §14 — a not-yet-decided proposal is valid, not broken.
A `PROPOSED` proposal that already has promoted knowledge is, however, inconsistent
(promotion only occurs after an ACCEPTED validation) and reports BROKEN/INCONSISTENT,
mirroring the REJECTED+knowledge handling.
Unsupported types (`CHALLENGE`, `DOCUMENTATION`) → whole-lifecycle NOT_APPLICABLE, never BROKEN,
consistent with `ProposalPromotionService` having no promotion handler for them.

### Reference invariant

> An ACCEPTED {proposal type} proposal MUST produce exactly one trusted {artifact}.

Emitting both `An ACCEPTED ... MUST produce exactly one trusted ...` findings via any MISSING
promoted-knowledge stage; unique DB indexes make multiplicity >1 unreachable, but the engine
reports INCONSISTENT defensively.

### Governance & atomicity

The diagnostic only **observes**; it never performs write, promotion or validation. ADR-006
governance is preserved (no shortcut AI→trusted knowledge) and ADR-058 §16 (transactional
semantics) is moot for a read-only capability. ADR-058 remains **Proposed** and is untouched.

### Legacy Decisions (Story 0077 NULL provenance)

A `Decision` with `proposal_id = NULL` is never returned by `findByProposalId(proposalId)`, so it
cannot affect any proposal-keyed result. It is invisible to this diagnostic (as designed in Story
0077's NOT_APPLICABLE handling); it is never fabricated and never flagged via a proposal lookup.

## Implementation Summary

| File | Change |
|------|--------|
| `lineage/dto/LineageStageStatus.java` | new enum (5 states) |
| `lineage/dto/KnowledgeLifecycleStatus.java` | new enum (3 states) |
| `lineage/dto/KnowledgeLifecycleStageResponse.java` | new record |
| `lineage/dto/KnowledgeLifecycleDiagnosticResponse.java` | new record |
| `lineage/service/KnowledgeLifecycleDiagnosticService.java` | new interface |
| Test | Status | Acceptance |
|-------|--------|--------|
| `lineage/service/KnowledgeLifecycleDiagnosticServiceImpl.java` | new read-only service + truth-table engine (incl. PROPOSED+promoted → BROKEN/INCONSISTENT) |
| `test/.../lineage/service/KnowledgeLifecycleDiagnosticServiceImplTest.java` | new: 15 unit tests |
| `test/.../lineage/service/KnowledgeLifecycleDiagnosticPostgresIntegrationTest.java` | new: 4 Testcontainers tests |

No existing production or test file was modified.

## Current Dataset Outcome

Given any ENGINEERING_DECISION / INSIGHT / ENGINEERING_EVENT proposal, DevLog can now
deterministically report whether the proposal was validated (or should be), whether it was
promoted into exactly one trusted artifact, and — when broken — a precise, human-readable
finding describing the gap. The ADR-058 reference invariant is now observable, not just
storable.

## Quality Gates

* backend `./mvnw test`: **PASS — 789 tests, 0 failures, 0 errors, BUILD SUCCESS** (baseline 770
  + 18 + 1 code-review unit test).

## Limitations

1. Read-only diagnosis only; it does not repair or auto-create missing artifacts (by ADR-058 §21).
2. Not exposed via REST/MCP/UI; it is an application capability awaiting a Phase 2 adapter.
3. `CHALLENGE`/`DOCUMENTATION` proposals are intentionally NOT_APPLICABLE (no promotion handler
   exists); fidelity beyond that is out of scope.
4. Diagnostic looks up by proposal only; a decision-keyed (reverse) diagnostic ("what proposal
   produced this Decision?") and full lineage trace are Phase 2.

## Next Architectural Questions

1. Surface the diagnostic via MCP / a read-only REST endpoint once the Phase 2 context lineage is
   defined (ADR-058 §20, §13).
2. Phase 2: context-projection lineage distinguishing "source missing" from "candidate not
   selected" (ProjectContext → RepositoryEvidence → EngineeringContext → MCP).
3. Whether a shared governed-knowledge lifecycle abstraction emerges across the three supported
   types (ADR-058 §15) for future invariants (e.g. generation-intent prerequisites).

## Documentation Outcome

This story folder is the canonical documentation: `story.md`, `repository-analysis.md`,
`implementation-plan.md`, `implementation-report.md`, `engineering-report.md`.

---

## Next Steps

* Approve commit of the Story 0078 changes (lineage DTOs, service, tests, docs) on
  `story/0078-knowledge-lifecycle-diagnostic-service`.
* Note: this branch is based on `story/0077` (`425af79`), which is not yet merged to `main`; the
  chain must be merged in order (0077 → 0078) before main contains the full lineage feature set.