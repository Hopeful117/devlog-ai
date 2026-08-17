# Story 0077 — Preserve Decision Promotion Provenance — Engineering Report

## Status

Reported

## Story

| Field | Value |
|-------|-------|
| Number | 0077 |
| Title | Preserve Decision Promotion Provenance |
| Status | Done (implementation complete, awaiting commit) |
| Acceptance Criteria | 8/8 satisfied |

## Scope Delivered

### Implemented

* `Decision.proposal` — `@OneToOne(LAZY)` `ValidatableProposal`; `@JoinColumn(name="proposal_id", updatable=false, unique=true)`, nullable.
* `V43__add_decision_proposal_provenance.sql` — `ALTER TABLE decisions ADD COLUMN proposal_id UUID` + `uk_decision_proposal_id UNIQUE (proposal_id)` + `fk_decision_proposal FOREIGN KEY (proposal_id) REFERENCES validatable_proposals(id)`. Nullable; **no backfill** (mirrors `V22__add_insight_provenance`).
* `ProposalPromotionService.promoteDecision(...)` — now sets `.proposal(proposal)`; the `validation` argument remains unused (not persisted, per Analysis Question B).
* `DecisionRepository.findByProposalId(...)` — `Optional<Decision>`; the lookup Story 0078 diagnostics will consume.

### Explicitly not introduced (per ADR-058 / this slice)

* No `KnowledgeLifecycleDiagnosticService` (Story 0078).
* No generic lineage storage (`LineageNode`/`LineageEdge`), no `KnowledgeRelation` redesign, no graph database.
* No `validation_id` / `analysis_id` / `ai_task_id` on `Decision` — all DERIVED from `Decision.proposal` (single authoritative path; avoids contradictory-state risk and duplication).
* No `DecisionResponse` / DTO proposal exposure, no ProjectContext / EngineeringContext / MCP changes.
* No heuristics, no backfill, no repair.

## Design Outcome

### Why only `proposal_id`?

Repository analysis confirmed every other V1 edge is already exact from persisted FKs:

```text
Decision --proposal_id--> ValidatableProposal
                               |-- analysis_id  (NOT NULL)      -> Analysis
                               |-- ai_task_id   (nullable)      -> AiTask
                               `-- Validation   (unique proposal_id) -> Validation
```

Insight and EngineeringEvent already carry unique `proposal_id` / `validation_id`; `Decision` was the sole dead-end. Storing only `Decision.proposal` closes the chain while preserving ADR-058 §15 (prefer DERIVED, avoid duplicating FKs) and §10 (stable domain identity).

### Governance & atomicity

`ValidationServiceImpl.validate(...)` is `@Transactional` and calls `promote(...)` synchronously after persisting the Validation and flipping proposal status — so the Decision (now carrying the proposal reference) commits or rolls back with the Validation acceptance (ADR-006 + ADR-058 §16). Verified by the existing `ValidationServiceTest` invocation assertion.

### Cardinality

`@OneToOne` + UNIQUE `proposal_id` enforces one-accepted-ENGINEERING_DECISION-proposal → exactly one Decision at the database, mirroring Insight (`Insight.java:42`) and EngineeringEvent. The unique constraint rejects double-promotion.

### Legacy data

Existing Decisions (including the reference `ae47a47d` and any manual-CRUD Decisions) keep `proposal_id = NULL`, remain valid and readable. They are treated later by Story 0078 as legacy/manual with promotion provenance NOT_APPLICABLE — never fabricated, never a standalone invariant violation.

## Implementation Summary

| File | Change |
|------|--------|
| `decision/entity/Decision.java` | +`@OneToOne proposal` field |
| `db/migration/V43__add_decision_proposal_provenance.sql` | `proposal_id UUID NULL` + UNIQUE + FK, no backfill |
| `validation/service/ProposalPromotionService.java` | `promoteDecision` sets `.proposal(proposal)` |
| `decision/repository/DecisionRepository.java` | `+findByProposalId(UUID)` |
| `test/.../validation/service/ProposalPromotionServiceTest.java` | 2 tests assert `proposal` on promoted Decision |
| `test/.../decision/DecisionPromotionProvenancePostgresIntegrationTest.java` | new: migration/uniqueness/legacy + `findByProposalId` (Testcontainers) |

## Current Dataset Outcome

Before: promoted Decisions dropped all provenance (project only). After: every promoted ENGINEERING_DECISION Decision carries its source proposal, from which validation, analysis and AI task are exactly derivable. Legacy/manual Decisions remain NULL-provenance records.

## Quality Gates

* backend `./mvnw test`: **PASS — 770 tests, 0 failures, 0 errors, BUILD SUCCESS** (baseline 768 + 2 new integration tests).

## Limitations

1. Legacy/manual Decisions have no `proposal_id`; provenance is NOT_APPLICABLE until/unless they are (separately) associated. No backfill was performed by design.
2. `Decision.response` does not yet expose `proposalId`; that is deferred to Story 0078 (not required to close the gap).
3. Story 0078 is required to evaluate the deterministic lifecycle invariants (PROPOSED/REJECTED/ACCEPTED ⇒ exactly one Decision) exposed by this data model.

## Next Architectural Questions

1. Story 0078: implement `KnowledgeLifecycleDiagnosticService` consuming `DecisionRepository.findByProposalId`, `ValidationRepository.findByProposalId`, and proposal status to evaluate lifecycle invariants for ENGINEERING_DECISION (then INSIGHT, ENGINEERING_EVENT).
2. Should `proposalId` be surfaced on `DecisionResponse` once diagnostics need it, or remain internal?
3. Whether to align the nullable `proposal` correctness gate with the accepted-proposal invariant in Story 0078.

## Documentation Outcome

This story folder is the canonical documentation: `story.md`, `repository-analysis.md`, `implementation-plan.md`, `implementation-report.md`, `engineering-report.md`.

---

## Next Steps

* Approve commit of the Story 0077 changes (entity, migration, promotion wiring, repository, tests, docs) on `story/0077-preserve-decision-promotion-provenance`.
* After merge: open Story 0078 — Knowledge Lifecycle Diagnostic Service.