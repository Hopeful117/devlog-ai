# Engineering Story 0077 — Preserve Decision Promotion Provenance

## Status

Prepared

## Priority

High

## Context

ADR-058 (Engineering Knowledge Data Lineage, Proposed) introduces a minimal,
deterministic lineage capability for the governed knowledge lifecycle:

```text
Analysis → AI Task → ValidatableProposal → Validation → Promoted Knowledge
```

where Promoted Knowledge initially includes `Decision`, `Insight` and
`EngineeringEvent`. ADR-058 explicitly prefers reconstructing lineage from
existing domain relationships and rejects generic graph / lineage-dedicated
infrastructure unless a relationship is genuinely lost.

The validated `ENGINEERING_DECISION` end-to-end lifecycle produced:

```text
Analysis → AI Task → ENGINEERING_DECISION proposal → Validation → Decision
→ RepositoryEvidence → EngineeringContext → MCP
```

During the Lineage V1 investigation, every upstream edge was found exactly
reconstructible from persisted foreign keys **except** one:

```text
ValidatableProposal → Decision    NOT_RECONSTRUCTIBLE
```

`Decision` currently drops proposal / validation / analysis / AI-task
provenance during promotion. This is the exact, narrow gap this Story closes.

## Problem Statement

Decision promotion currently destroys the exact Proposal → Decision provenance
edge.

`ProposalPromotionService.promoteDecision(...)` builds a `Decision` carrying
only `projectId` plus content fields and never records the source
`ValidatableProposal` (or `Validation`). The `decisions` table has `project_id`
as its sole foreign key. As a result, given a Decision ID, the source proposal,
validation, analysis and AI task cannot be reconstructed deterministically;
given an accepted ENGINEERING_DECISION proposal, its promoted Decision cannot be
located.

`Insight` and `EngineeringEvent` already persist `proposal_id` and
`validation_id` (both unique). `Decision` is the only promoted knowledge type
with broken provenance.

## Repository Analysis

### Current domain relationships (authoritative)

| Edge | Persisted reference | Classification |
|---|---|---|
| Analysis → AI Task | `ai_tasks.analysis_id` NOT NULL | RECONSTRUCTIBLE_EXACTLY |
| AI Task → Proposal | `validatable_proposals.ai_task_id` (nullable, set in `AiTaskResultServiceImpl.toProposals`) | RECONSTRUCTIBLE_EXACTLY (generated) |
| Proposal → Validation | `validations.proposal_id` UNIQUE (`uk_validation_proposal_id`) | DIRECTLY_STORED |
| Proposal → Insight | `insights.proposal_id` UNIQUE | DIRECTLY_STORED |
| Proposal → EngineeringEvent | `engineering_events.proposal_id` UNIQUE | DIRECTLY_STORED |
| Proposal → Decision | **none** | **NOT_RECONSTRUCTIBLE** |

### Decision model and promotion behavior

- `Decision` entity (`decision/entity/Decision.java`): `id`, `project_id` (only
  FK), content fields, `created_at`, `updated_at`. No lifecycle/status enum.
- `decisions` table (`db/migration/V3__create_decisions_table.sql`): single FK
  `project_id`.
- `DecisionRepository`: `findByProjectIdOrderByCreatedAtDesc[IdDesc]` only; no
  proposal-based lookup.
- `ProposalPromotionService.promoteDecision(...)` (line 52): builds Decision from
  `project` + payload; the `validation` argument is unused; the source proposal
  is not referenced.
- `DecisionServiceImpl` / `DecisionMapper`: pure manual CRUD; `DecisionResponse`
  exposes `id, projectId` + content only.

### Atomicity

`ValidationServiceImpl.validate(...)` is `@Transactional` (class and method) and
calls `promotionService.promote(...)` synchronously after saving the `Validation`
and updating the proposal to ACCEPTED/REJECTED. Any `Decision` persistence added
inside `promoteDecision` therefore shares the same transaction as validation
acceptance (ADR-006 + ADR-058 §16). No separate commit boundary is introduced.

### Comparison with Insight / EngineeringEvent promotion

- `InsightPromotionService.promote` sets `project, analysis, proposal, validation`
  with `proposal_id` and `validation_id` as `@OneToOne` UNIQUE (nullable=false,
  updatable=false).
- `ProposalPromotionService.promoteEvent` sets `project, analysis, proposal,
  validation, source` with `proposal_id` and `validation_id` UNIQUE.
- `Decision` is the anomaly: it persists none of these.

### Migration state

- Highest applied Flyway version: `V42`. Next available: **`V43`**.
- The ENGINEERING_DECISION lifecycle work added no `decisions` migration.

### Provenance reconstruction analysis (Verifies the central hypothesis)

With **only** `Decision.proposal_id` persisted, the full chain is derivable:

```text
Decision ──proposal_id──▶ ValidatableProposal
                            ├── analysis_id  (NOT NULL)          → Analysis
                            ├── ai_task_id   (nullable)          → AiTask
                            └── Validation    (unique proposal_id) → Validation
```

Every downstream relationship is derived from the single authoritative
`Decision.proposal` reference. No ambiguity, no extra storage.

### MCP context assessment

- **DIRECTLY_RELEVANT**: ADR-006 governance notes; reference Decision
  `ae47a47d-65fa-4a30-810c-f114b37755bd`.
- **USEFUL_BACKGROUND**: ADR documentation pattern insights; Engineering Story
  conventions.
- **STALE_OR_CONFLICTING**: none.
- **NOISE**: the majority of returned evidence (maintenance, deduplication,
  projection-refresh commits/stories 0068–0076) is unrelated to this slice.
- **MISSING**: ADR-058/ADR-006 content, Decision model, promotion services,
  `ValidatableProposal`/`Validation` details. Repository is the authoritative
  source and was used for all claims above.

## Architectural Constraints

- ADR-006: promoted trusted knowledge must only be created from an accepted
  `ValidatableProposal` through the governed validation+promotion workflow.
- ADR-058: prefer DERIVED lineage; persist only relationships that would
  otherwise be lost; do not duplicate existing FKs without justification.
- No generic lineage table (`LineageNode` / `LineageEdge`).
- No graph database.
- No RAG / Retrieval dependency.
- No MCP-specific implementation (MCP exposure is Phase 2).
- Promotion remains transactional with Validation acceptance.

## Proposed Responsibility

- `ProposalPromotionService.promoteDecision(...)` — sets the proposal reference
  on the created `Decision`.
- `Decision` entity + `V43` migration — persist the unique nullable proposal FK.
- `DecisionRepository` — add proposal-based lookup for future diagnostics.

No new generic lineage service is introduced in this slice; the existing
proposal-promotion and validation components own the change.

## Proposed Minimal Domain Change

Repository analysis confirms the minimal mapping:

```text
Decision
  → proposal  UNIQUE, nullable FK (updatable = false, @OneToOne)
```

Validation, analysis and AI task are DERIVED from `proposal` and are not stored
again (see Analysis Question B). Legacy Decisions keep `proposal_id = NULL`.

## Legacy Data Strategy

- Proposed: `proposal_id` is **nullable**; no heuristic backfill.
- Existing Decisions (including the reference Decision `ae47a47d` produced by
  the validated E2E, and any manually-created Decisions) remain valid records
  with `proposal_id = NULL`.
- Diagnostics (Story 0078) will treat a NULL proposal reference as a valid
  legacy/manual Decision whose promotion provenance is NOT_APPLICABLE — never a
  fabricated association and never an invariant violation on its own.
- The accepted-proposal → exactly-one-Decision invariant applies only to
  Decisions that carry a proposal reference, and only to ACCEPTED
  ENGINEERING_DECISION proposals.

## Acceptance Criteria

1. A newly promoted ENGINEERING_DECISION `Decision` references its source
   `ValidatableProposal` (`decision.proposal_id == proposal.id`).
2. `Decision.proposal_id` is unique (no two Decisions reference the same
   proposal).
3. Promotion remains atomic with Validation acceptance (single transaction).
4. Legacy Decisions remain readable with `proposal_id = NULL`; no heuristic
   backfill is applied.
5. No generic lineage table is introduced.
6. No duplicated `analysis_id` / `validation_id` / `ai_task_id` provenance on
   Decision unless justified (analysis shows none is justified).
7. Existing Decision CRUD behavior remains compatible (manual Decisions without
   a proposal still allowed).
8. Existing ENGINEERING_DECISION E2E path remains functional.

## Test Strategy

- `ProposalPromotionServiceTest`: promoted ENGINEERING_DECISION sets `proposal`;
  INSIGHT/EVENT promotion unaffected; non-promotable types unchanged.
- `ValidationServiceTest`: accepted ENGINEERING_DECISION produces a Decision
  linked to the proposal within the validation transaction; rollback removes the
  Decision.
- Decision persistence/repository: `findByProposalId` returns exactly one for a
  linked Decision; returns empty for a proposal with no Decision.
- Migration: `V43` applies on a DB containing legacy Decisions; legacy rows keep
  NULL; unique index enforced.
- Uniqueness: attempting two promotions of the same accepted ENGINEERING_DECISION
  proposal fails (unique constraint / guard).
- Legacy null provenance: legacy `Decision` (NULL proposal) is readable and not
  flagged.
- Existing `DecisionServiceTest` / `DecisionControllerWebMvcTest` continue to
  pass.

## Risks

- Migration risk limited to adding a nullable column + unique index in `V43`.
- Accidental duplicate provenance if the full Insight/Event field set were
  copied — avoided by persisting only `proposal_id`.
- JPA `@OneToOne` uniqueness/cardinality mismatch with the schema unique index.
- API compatibility: `DecisionResponse` unchanged (proposal exposure deferred).
- Transaction behavior: Decision creation shares the validation transaction; a
  failure rolls back the Decision, Validation and proposal status together.

## Dependencies

- `Decision` entity (backend).
- `db/migration/V43` (next available Flyway version).
- `ProposalPromotionService`.
- `ValidationServiceImpl` transaction boundary (existing, already `@Transactional`).

## Explicitly Out of Scope

- `KnowledgeLifecycleDiagnosticService` (Story 0078).
- Generic `LineageNode` / `LineageEdge`.
- `KnowledgeRelation` redesign.
- Graph database.
- Context projection lineage.
- `RepositoryEvidence` lineage.
- Ranking / selection lineage.
- MCP lineage.
- Retrieval Layer.
- RAG.
- Automatic lifecycle repair.
- Heuristic legacy backfill.
- Exposure of `proposalId` in `DecisionResponse` / ProjectContext / EngineeringContext.

## Implementation Plan — DRAFT ONLY

> **NOT APPROVED — DO NOT IMPLEMENT**

The following is a draft for scope assessment only:

1. Add `@OneToOne` `proposal` reference to `Decision` (unique, nullable,
   `updatable=false`).
2. Add `V43__add_decision_proposal_provenance.sql`: `ALTER TABLE decisions ADD
   COLUMN proposal_id UUID NULL` + unique index/constraint on `proposal_id` +
   FK to `validatable_proposals`. No backfill.
3. Update `ProposalPromotionService.promoteDecision(...)` to set
   `.proposal(proposal)` (and pass `validation` if a reference is later needed;
   not persisted per analysis).
4. Add `DecisionRepository.findByProposalId(...)` returning an Optional.
5. Update/confirm `ProposalPromotionServiceTest`, `ValidationServiceTest`, add
   repository and migration tests.
6. Run backend test suite.
7. This Story stops before the diagnostic service (Story 0078).

## Verification Plan

Prove the exact provenance gap is closed by asserting, after accepting an
ENGINEERING_DECISION proposal via `ValidationService.validate(...)`:

- a `Decision` row exists with `proposal_id = proposal.id`;
- `DecisionRepository.findByProposalId(proposal.id)` returns exactly that one
  Decision;
- the proposal's `analysisId` / `aiTaskId` / `Validation` are derivable from
  `Decision.proposal` without additional provenance storage;
- rollback of the validation transaction removes the Decision.

## Future Follow-up

Next Story (0078):

**Knowledge Lifecycle Diagnostic Service** — consumes the now-persisted /
derived lineage to answer, given an ENGINEERING_DECISION proposal:

```text
find Proposal → find Validation → find promoted Decision → evaluate invariant → deterministic diagnostic
```

No MCP or frontend exposure.