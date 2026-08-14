# Story 0060 — Contradiction and Supersession in Incremental Knowledge Evolution

## Status

Approved

## Priority

High

## Objective

Implement the first vertical slice of **ADR-054 (Contradiction and Supersession in Incremental Knowledge Evolution)**: allow `ARCHITECTURE_REVIEW` analysis to propose that an existing trusted architecture Insight should be superseded, and — once the proposal passes the existing human validation lifecycle — record that supersession without rewriting or deleting the historical statement.

The governing principle, inherited verbatim from ADR-054:

> **DevLog supersedes by recording a decided transition, not by rewriting the past.**

Story 0037 implemented ADR-050 for `NEW` and `ENRICHES` only. `CONFIRMS`, `CONTRADICTS`, `SUPERSEDES`/`INVALIDATES` and temporal modeling were explicitly deferred. This story closes the highest-value gap: the ability to represent that a project no longer uses a technology while preserving that it once did (ADR-050 §7).

## Motivation

DevLog's trusted knowledge model currently has no way to distinguish:

> this knowledge is historically true

from:

> this knowledge is currently true.

Once an `Insight` is promoted it never changes trust representation. When new evidence invalidates or dominates a prior statement, the system today cannot represent the transition without either keeping both statements as equally active (violating ADR-050) or editing/rewriting the prior trusted knowledge (violating ADR-006).

Humans and machines both need this: Project State and freshness projections must eventually show *current* architecture understanding without discarding the *historical* record. This story proves the lifecycle end-to-end for `ARCHITECTURE_REVIEW`.

## Scope

### In Scope

1. **Backend — schema (migration)**
   - Add a trust-state marker to `insights`:
     - `trust_state` `VARCHAR(50)` nullable (values `ACTIVE`, `SUPERSEDED`), defaulting to `ACTIVE` for existing rows.

2. **Backend — entity**
   - Add `InsightTrustState` enum (`ACTIVE`, `SUPERSEDED`).
   - Add `trustState` field to `Insight`, defaulting to `ACTIVE` on promotion.

3. **Backend — relation vocabulary**
   - Add `SUPERSEDES` to `KnowledgeRelationType`.

4. **Backend — delta contract**
   - Extend the architecture Insight delta contract to accept `SUPERSEDES` with a required `targetInsightId`:
     - `IntentCatalog.outputContract` `allowedDeltaTypes` → `NEW, ENRICHES, SUPERSEDES`;
     - `AiProposalContractValidator.validateArchitectureDelta` → accept `SUPERSEDES`; `targetInsightId` required; target must exist, be `ACTIVE`, same project, and be present in `existingArchitectureKnowledge`.

5. **Backend — promotion**
   - `InsightPromotionService.promote`:
     - `NEW`/`ENRICHES` unchanged (`ACTIVE`);
     - on accepted `SUPERSEDES`: promote the successor Insight as `ACTIVE`, mark the target Insight `SUPERSEDED`, and record a `KnowledgeRelation`:
       - source = successor Insight, target = superseded Insight, relation type = `SUPERSEDES`.
   - Atomic within the existing promotion transaction.

6. **Backend — read API**
   - Expose `trustState` on `InsightResponse` via `InsightMapper`.

7. **Backend — tests**
   - `AiProposalContractValidatorTest`: valid/invalid supersession target.
   - `InsightPromotionServiceTest`: accepted supersession marks target `SUPERSEDED` and creates `SUPERSEDES` relation; rejected supersession leaves target unchanged.
   - `InsightMapperTest`: `trustState` mapped.

8. **AI engine — schema and prompt**
   - `ai-engine/app/schemas/insight.py`: allow `SUPERSEDES` in `KnowledgeDeltaType`; require `targetInsightId` for it.
   - `ai-engine/app/prompts/insight.py` + generation service: instruct the model that on strong contradicting/dominating evidence it may emit `SUPERSEDES` targeting a trusted architecture insight, otherwise keep `NEW`/`ENRICHES` or return empty proposals.

### Out of Scope

- **`CONFIRMS`** (ADR-050 / ADR-054 §8) — deferred.
- **`CONTRADICTS`** as a separate first-class outcome — the first slice surfaces contradiction through the supersession contract only.
- **Deterministic contradiction detector** (ADR-054 §8) — deferred.
- **Full temporal truth engine** (ADR-054 non-goal) — we add a binary trust-state marker, not a temporal model.
- **`KnowledgeRelation` redesign / Knowledge Graph** (ADR-049 §15) — one relation type added only.
- **Dedicated persisted `KnowledgeDelta` entity** — the delta remains payload metadata + trust-state marker, consistent with Story 0037.
- **Historical backfill** — existing insights default to `ACTIVE`; audit of their current-correctness is a separate maintenance concern (Story 0040 / ADR-051).
- **Frontend redesign** — `trustState` exposed on the API only; no UI changes in this slice.
- No new ADR beyond ADR-054.

## Constraints

- **Historical truth preserved**: a superseded insight is never deleted, edited, or rewritten; only its trust-state changes to `SUPERSEDED`.
- **No automatic acceptance**: supersession only takes effect through the existing human validation lifecycle.
- **No duplicate bypass**: supersession must not be used to relabel an equivalent `ACTIVE` statement; the duplicate policy (ADR-051 / Story 0038) remains authoritative.
- **Core-owned transition**: the Java Core performs the trust-state change and relation creation atomically (ADR-006).
- **Structurally validated**: `SUPERSEDES` is decided from contract fields, never by parsing free text.

## Impact

- **Backend**:
  - migration (ALTER TABLE `insights`, additive nullable `trust_state`);
  - `Insight` entity + `InsightTrustState` enum, `InsightResponse`, `InsightMapper`;
  - `KnowledgeRelationType` (+`SUPERSEDES`);
  - `IntentCatalog`, `AiProposalContractValidator`, `InsightPromotionService`;
  - test updates as listed.
- **AI engine**:
  - `insight.py`, `insight.py` prompts, `insight_generation_service.py`.
- **CI**: no change.
- **Tests**: backend +several, AI engine +several.

## Acceptance Criteria

- AC-1: Migration adds `trust_state` to `insights` (nullable, additive); existing rows default to `ACTIVE`.
- AC-2: `InsightTrustState` enum has `ACTIVE` and `SUPERSEDED`; `Insight` exposes `trustState` defaulting to `ACTIVE` on promotion.
- AC-3: `KnowledgeRelationType` includes `SUPERSEDES`.
- AC-4: The architecture delta contract accepts `SUPERSEDES`; `targetInsightId` is required for it.
- AC-5: Core-side validation rejects a supersession target that is missing, not `ACTIVE`, from another project, or not present in selected architecture knowledge.
- AC-6: Accepting a `SUPERSEDES` proposal promotes the successor as `ACTIVE`, marks the target `SUPERSEDED`, and creates a `SUPERSEDES` relation (successor → predecessor).
- AC-7: Rejecting a `SUPERSEDES` proposal leaves the target `ACTIVE` and creates no relation.
- AC-8: The promotion transition is atomic — target mark and relation are created or not together.
- AC-9: `InsightResponse` exposes `trustState` via `InsightMapper`.
- AC-10: Existing gates: backend `verify` (tests + JaCoCo), AI engine pytest, frontend lint/build/test, SonarQube gate — no thresholds lowered.
- AC-11: Idempotence preserved — an `ARCHITECTURE_REVIEW` with no contradiction and no dominant new evidence still returns empty proposals with no trust-state change.

## Technical Context

Verified source structures:

- `Insight` entity (`backend/.../insight/entity/Insight.java`): fields `id, project, analysis, proposal, validation, type, severity, title, content, rationale, confidence, evidenceReferences, sourceType, createdAt, updatedAt`. No trust-state field. Promoted via `InsightPromotionService.promote` (builds from payload, `toDomainType` normalization).
- `KnowledgeRelationType` enum: `RESOLVES, CAUSED_BY, RELATES_TO, DERIVED_FROM, ADDRESSES, INFORMED_BY`. No `SUPERSEDES`.
- `KnowledgeRelation` entity: `id, project, sourceEntityType, sourceEntityId, targetEntityType, targetEntityId, relationType, description, createdAt`.
- `InsightPromotionService.createEnrichmentRelationIfNeeded`: creates a `DERIVED_FROM` relation for accepted `ENRICHES` (source = new Insight, target = enriched Insight). This is the pattern to mirror for `SUPERSEDES`.
- `AiProposalContractValidator.validateArchitectureDelta`: currently accepts `NEW`/`ENRICHES`; `ENRICHES` requires `targetInsightId` in selected architecture knowledge.
- `IntentCatalog.outputContract`: `allowedDeltaTypes = [NEW, ENRICHES]` for insight intents.
- `KnowledgeSelectionServiceImpl.selectExistingArchitectureKnowledge`: bounded (max 5), project-scoped, `existingArchitectureKnowledge` snapshots with `insightId`.
- AI engine `ai-engine/app/schemas/insight.py`: `KnowledgeDeltaType(NEW, ENRICHES)` with `target_insight_id` required for `ENRICHES`.
- Latest migration: currently at the migration referenced by Story 0059; new migration number to be determined during planning.

## Dependencies

- ADR-054 (Accepted) — contradiction and supersession lifecycle.
- ADR-050 (Accepted) — incremental knowledge evolution (base).
- ADR-006 (Accepted) — Core owns proposal/trust lifecycle.
- ADR-051 / Story 0038 — duplicate policy (supersession must not bypass it).
- Story 0037 — existing delta contract, selection, and promotion to extend.

## Risks

1. **Automatic contradiction resolution** — supersession could be treated as self-resolving; mitigated by requiring the existing human validation lifecycle (ADR-054 §3).
2. **Duplicate-policy bypass** — an equivalent statement relabeled as a "successor"; mitigated by reusing `TrustedKnowledgeDuplicateGuard` on the promoted successor and enforcing structural target validation.
3. **Overlapping `ACTIVE` statements** — a supersession could coexist with an equivalent active statement; mitigated by contract validation + duplicate guard.
4. **Scope creep into temporal truth engine / graph** — mitigated by explicit non-goals inherited from ADR-054.
5. **Historical migration** — existing insights default `ACTIVE`; audit of current-correctness stays out of scope (maintenance concern).

## Decisions for validation (resolved)

An ADR is **not** proposed here: this story implements the already-Accepted ADR-054.

1. **Trust-state mechanism**: a binary `trustState` (`ACTIVE`/`SUPERSEDED`) on `Insight` rather than a temporal model. ✅ Approved
2. **Relation vocabulary**: add `SUPERSEDES` (successor → predecessor) rather than reusing `DERIVED_FROM`. ✅ Approved
3. **Contradiction surfaced via supersession contract** only; no deterministic contradiction engine. ✅ Approved
4. **No historical backfill**; existing insights default `ACTIVE`. ✅ Approved

## Artifacts

- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`
