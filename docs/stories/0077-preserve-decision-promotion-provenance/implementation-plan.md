# Story 0077 — Preserve Decision Promotion Provenance — Implementation Plan

> **NOT APPROVED — DO NOT IMPLEMENT**
>
> The Repository Analysis is approved. This plan is submitted to the
> Implementation Plan approval gate (Phase 6). No production code, migration or
> test is written until that approval.

## Objective

Close the only NOT_RECONSTRUCTIBLE V1 lineage edge by persisting
`Decision.proposal_id` (UNIQUE, nullable) and linking it during DECISION
promotion, deriving validation/analysis/AI-task provenance from the single
proposal reference. No generic lineage infrastructure.

## Phase 1 — Data model

### 1.1 `Decision` entity (`backend/.../decision/entity/Decision.java`)
- Add `@OneToOne(fetch = LAZY, optional = true)` field `ValidatableProposal proposal`
  with `@JoinColumn(name = "proposal_id", updatable = false, unique = true)`.
- Keep `project_id` and existing content fields unchanged.
- No new status/type enum, no validation/analysis/aiTask fields (per Q B).

## Phase 2 — Migration

### 2.1 New migration `backend/src/main/resources/db/migration/V43__add_decision_proposal_provenance.sql`
- `ALTER TABLE decisions ADD COLUMN proposal_id UUID NULL;`
- `ADD CONSTRAINT uk_decision_proposal_id UNIQUE (proposal_id);`
- `ADD CONSTRAINT fk_decision_proposal FOREIGN KEY (proposal_id)
   REFERENCES validatable_proposals(id);`
- **No backfill.** Legacy rows keep `proposal_id = NULL`.
- Migration must be idempotent-safe in intent and align exactly with the JPA
  `@OneToOne unique` mapping.

## Phase 3 — Promotion wiring

### 3.1 `ProposalPromotionService.promoteDecision(...)`
(`backend/.../validation/service/ProposalPromotionService.java`)
- Set `.proposal(proposal)` on the built `Decision` before save.
- Do not persist validation/analysis/aiTask on Decision (derived).
- INSIGHT / ENGINEERING_EVENT / other branches unchanged.

### 3.2 `DecisionRepository` (`backend/.../decision/repository/DecisionRepository.java`)
- Add `Optional<Decision> findByProposalId(UUID proposalId)`.
- Existing findByProjectId methods unchanged.

## Phase 4 — Tests

### 4.1 `ProposalPromotionServiceTest`
- Accepted ENGINEERING_DECISION -> saved Decision has `proposal == proposal`.
- INSIGHT promotion: no Decision created / unchanged.
- ENGINEERING_EVENT promotion: unchanged.
- Non-promotable type (CHALLENGE/DOCUMENTATION): still throws, unchanged.

### 4.2 `ValidationServiceTest`
- Accepting an ENGINEERING_DECISION proposal creates a Decision whose
  `proposal_id == proposal.id`, inside the same validation transaction.
- Rollback (force failure after acceptance) removes the Decision (atomicity).

### 4.3 `DecisionRepository` test
- `findByProposalId(linkedProposalId)` returns exactly one Decision.
- `findByProposalId(unlinkedProposalId)` returns empty.

### 4.4 Migration test
- `V43` applies on a schema containing legacy Decisions; legacy rows stay NULL;
  unique index present.
- Double-promotion of the same accepted ENGINEERING_DECISION proposal fails
  (unique violation / guard).

### 4.5 Legacy / compatibility
- Legacy Decision (NULL proposal) remains readable and passes existing
  `DecisionServiceTest` / `DecisionControllerWebMvcTest`.
- Manual `DecisionServiceImpl.create` (no proposal) still allowed.

## Validation

- `./mvnw test` in `backend/` — full green (baseline 768 tests + new tests).
- Manual confirmation: accepting an ENGINEERING_DECISION proposal yields a
  Decision with a non-null `proposal_id`; legacy Decisions remain NULL.
- The ENGINEERING_DECISION E2E path (Analysis → Task → Proposal → Validation →
  Decision → RepositoryEvidence → MCP) remains functional.

## Explicitly out of scope (this plan)

- `KnowledgeLifecycleDiagnosticService` (Story 0078).
- Generic lineage storage, `KnowledgeRelation` redesign, graph DB.
- `DecisionResponse` proposalId exposure / ProjectContext / EngineeringContext / MCP.
- Any backfill or repair.

## Deliverables

1. `Decision.java` (+1 field)
2. `V43__add_decision_proposal_provenance.sql`
3. `ProposalPromotionService.promoteDecision` (+1 line)
4. `DecisionRepository.findByProposalId`
5. Tests above
6. Implementation report + engineering report (post-approval)