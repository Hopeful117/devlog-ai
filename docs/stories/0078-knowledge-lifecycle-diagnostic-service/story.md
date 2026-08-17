# Engineering Story 0078 — Knowledge Lifecycle Diagnostic Service

## Status

Prepared

## Priority

High

## Context

ADR-058 (Engineering Knowledge Data Lineage, **Proposed**) defines V1 governed-lineage
as:

```text
Analysis → AI Task → ValidatableProposal → Validation → Promoted Knowledge
```

where Promoted Knowledge initially includes `Decision`, `Insight` and
`EngineeringEvent`. ADR-058 §13 requires an application-level diagnostic:

> diagnose_knowledge_lifecycle(lifecycleType, reference)

and §12 requires it be deterministic (no LLM).

Story 0077 closed the only V1 provenance gap: an accepted `ENGINEERING_DECISION`
proposal now maps to exactly one trusted `Decision` via a persisted unique
`Decision.proposal_id` (migration V43) and `DecisionRepository.findByProposalId`.

Story 0078 is the **consumer** of that now-complete lineage: a
**proposal-centric diagnostic** that answers, given a `ValidatableProposal`,
whether its governed knowledge lifecycle is complete, or exactly where it is
broken.

## Problem Statement

DevLog can store and process governing knowledge, but it cannot systematically
explain, for a given proposal, whether it was validated and whether it was
promoted into its trusted artifact — and where that lifecycle is broken if it
was not (ADR-058 context). This Story delivers that capability deterministically
from already-persisted relationships, without generic lineage infrastructure.

## Repository Analysis

See `repository-analysis.md`. Key facts (source-verified):

- All candidate edges are already persisted:
  - Proposal → Validation: `validations.proposal_id` UNIQUE
  - Proposal → Decision: `decisions.proposal_id` UNIQUE NULLABLE (Story 0077/V43)
  - Proposal → Insight: `insights.proposal_id` UNIQUE
  - Proposal → EngineeringEvent: `engineering_events.proposal_id` UNIQUE
- Promotion is dispatched per type in `ProposalPromotionService.promote(...)`;
  only `INSIGHT`, `ENGINEERING_EVENT`, `ENGINEERING_DECISION` have handlers.
- Highest Flyway = **V43**; no V44 required by this Story.
- No existing lifecycle-diagnostic symbol exists; new, non-duplicative.
- The diagnostic is read-only: `@Transactional(readOnly = true)`, matching
  `ProposalReviewService`.

## Proposed Responsibility

A new application-level diagnostic capability, proposal-keyed by `UUID proposalId`,
that resolves, for one proposal:

1. **Validation stage** — `ValidationRepository.findByProposalId(proposalId)`.
2. **Promoted-knowledge stage** — per `ProposalType`:
   - `ENGINEERING_DECISION` → `DecisionRepository.findByProposalId`
   - `INSIGHT` → `InsightRepository.findByProposalIdIn`
   - `ENGINEERING_EVENT` → `EngineeringEventRepository.findByProposalIdIn`
3. Per-stage status + overall lifecycle status + deterministic findings for any
   BROKEN case (including the ADR-058 reference invariant violation).

## Proposed Package

New top-level package `com.hopeful117.devlogai.lineage` (`service` + `dto`),
isolated from any single domain, mirroring ADR-058 vocabulary and leaving the
existing `analysis/diagnostics` package untouched. Rationale in
repository-analysis Question C.

## Lifecycle Semantics (deterministic)

Per-stage status: `PRESENT | PENDING | NOT_APPLICABLE | MISSING | INCONSISTENT`.
Implements ADR-058 §14 (missing is not always an error; PROPOSED is PENDING, not
MISSING).

Reference invariant (ADR-058 §4) extended to all three supported types:

> An ACCEPTED {ENGINEERING_DECISION|INSIGHT|ENGINEERING_EVENT} proposal
> MUST produce exactly one trusted {Decision|Insight|EngineeringEvent}.

Truth table and overall `LifecycleStatus` (`COMPLETE` / `BROKEN` /
`NOT_APPLICABLE`) are defined in repository-analysis Question F. Unsupported
types (`CHALLENGE`, `DOCUMENTATION`) → `NOT_APPLICABLE`, never broken.

## Architectural Constraints

- ADR-058 remains **Proposed** — not modified by this Story.
- ADR-006 governance preserved: the diagnostic only **observes**; it never
  mutates validation or promotion state, and never bypasses the governed flow.
- No generic lineage table (`LineageNode`/`LineageEdge`); no migration (V44).
- Deterministic: pure repository reads, no LLM / ranking / selection.
- Read-only transactional service.
- No REST controller, no MCP tool, no UI (MCP is ADR-058 Phase 2; the
  application capability is exposed so a future adapter can wrap it).
- Project-scoped by construction (proposal carries `project_id`).
- No automatic repair.

## Acceptance Criteria

1. Given an ACCEPTED supported proposal with its promoted artifact present, the
   diagnostic reports `COMPLETE` (both stages PRESENT).
2. Given an ACCEPTED supported proposal with **no** promoted artifact, the
   diagnostic reports `BROKEN` with an invariant-violation finding.
3. Given a PROPOSED proposal, the diagnostic reports `COMPLETE` (pending) with
   validation PENDING — never MISSING.
4. Given a REJECTED proposal, the diagnostic reports `COMPLETE` (no promotion
   expected) when a REJECTED Validation exists; `BROKEN` when the Validation is
   missing or a promoted artifact exists (INCONSISTENT).
5. The diagnostic dispatches correctly per type (Decision/Insight/Event).
6. Unsupported types return `NOT_APPLICABLE`/`COMPLETE` (nothing expected), never
   BROKEN.
7. The result is deterministic and read-only (no persistence writes occur).
8. Full existing backend test suite remains green (currently 770 passed).
9. No migration is added (highest Flyway stays V43).
10. No generic lineage table, no MCP/REST/UI, no repair.

## Test Strategy

- Service unit tests covering the full truth table (Question F), per-type
  dispatch, and unsupported-type handling.
- Repository lookup tests for `findByProposalId` / `findByProposalIdIn`.
- Postgres integration test: promote an ACCEPTED ENGINEERING_DECISION via
  `ValidationService`; assert the diagnostic reports COMPLETE; assert a
  deliberately missing Decision reports BROKEN + invariant finding (mirrors
  Story 0077's `DecisionPromotionProvenancePostgresIntegrationTest`).
- Backend suite gate at 770+ passed / 0 failed.

## Risks

- Over-reporting valid intermediate states → handled by PROPOSED=PENDING rule.
- Package placement → isolated `lineage` package (Question C).
- Multiplicity beyond 1 → guarded in service despite DB unique indexes.
- ADR-058 status drift → explicitly not modified.

## Dependencies

- Story 0077 (`Decision.proposal` + `findByProposalId`) — present on this branch.
- `ValidatableProposalRepository`, `ValidationRepository`, `DecisionRepository`,
  `InsightRepository`, `EngineeringEventRepository`.
- Enums: `ProposalStatus`, `ProposalType`, `ValidationDecision`, `InsightStatus`.

## Explicitly Out of Scope

- Generic lineage table / migration (V44).
- Modification of ADR-058 or ADR-006.
- MCP tool, REST controller, frontend/UI.
- Phase 2 context-projection lineage (ProjectContext / RepositoryEvidence /
  EngineeringContext / MCP).
- Ranking / selection lineage ("decision exists but not selected").
- Also-distinction across projects.
- Automatic lifecycle repair / artifact creation.
- AI / LLM-based diagnosis.
- Detailed diagnostic fidelity for CHALLENGE / DOCUMENTATION beyond NOT_APPLICABLE.

## Implementation Plan — DRAFT ONLY

> **NOT APPROVED — DO NOT IMPLEMENT**

The following is a draft for scope assessment only:

1. Verify Story 0077 provenance is reachable from this branch (already done).
2. Add `com.hopeful117.devlogai.lineage.dto` lifecycle status + response records
   (per-stage status enum, overall `LifecycleStatus`, findings list).
3. Add `KnowledgeLifecycleDiagnosticService` resolving Validation + promoted
   knowledge per type and evaluating the invariant truth table.
4. Add focused unit + repository + Postgres integration tests (Test Strategy).
5. Run the full backend suite; confirm 770+ green.
6. This Story does not add MCP/REST/UI or any migration.

## Verification Plan

At Phase 6 gates: after accepting a supported proposal via `ValidationService`, the
diagnostic reports COMPLETE; with a removed/missing promoted artifact it reports
BROKEN with the invariant finding; PROPOSED/rejected/unsupported states report
per the truth table. Full suite remains green at the pre-existing baseline.

## Future Follow-up

- Phase 2: context-projection lineage (above, out of scope) distinguishing source
  missing vs candidate-not-selected.
- MCP exposure of the diagnostic once the application capability is stable.
- Evaluate whether a shared governed-lifecycle abstraction emerges across the
  three supported types (ADR-058 §15).