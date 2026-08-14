# Story 0060 — Contradiction and Supersession in Incremental Knowledge Evolution — Repository Analysis

## Purpose

Read-only reconnaissance of the incremental-knowledge lifecycle to determine the
smallest safe vertical slice for contradiction and supersession, extending the
baseline established by ADR-050 / Story 0037.

This analysis was performed before any ADR draft or implementation changes.

## Relevant Components

### `ADR-050` / Story 0037
- Story 0037 implemented ADR-050 for `ARCHITECTURE_REVIEW`: bounded existing
  trusted architecture knowledge, structured delta contract, `NEW`/`ENRICHES`,
  no-significant-delta via zero proposals.
- It explicitly deferred `CONFIRMS`, `CONTRADICTS`, `SUPERSEDES`, and temporal
  modeling.

### `ADR-006`
- Governing lifecycle ADR.
- Impractical for supersession unless the Core retains ownership: the AI may
  propose, but only the Java Core changes trust state and only after validation.

### `Insight` entity
- `backend/.../insight/entity/Insight.java`
- Fields: `id, project, analysis, proposal, validation, type, severity, title,
  content, rationale, confidence, evidenceReferences, sourceType, createdAt,
  updatedAt`.
- **Finding**: no trust-state field exists. A promoted statement can never change
  its trust representation, so historical-vs-current truth is not modeled.

### `InsightPromotionService`
- `promote(...)` builds an `Insight` from payload and saves it.
- For accepted `ENRICHES`, creates a `DERIVED_FROM` relation (successor → target).
- **Finding**: this is the promotion location where trust-state changes and
  supersession relations would naturally be added.

### `KnowledgeRelationType`
- `backend/.../knowledge/relation/entity/KnowledgeRelationType.java`
- Values: `RESOLVES, CAUSED_BY, RELATES_TO, DERIVED_FROM, ADDRESSES, INFORMED_BY`.
- **Finding**: no `SUPERSEDES` type exists. Enrichment uses `DERIVED_FROM`;
  supersession needs its own explicit relation type to stay distinct.

### `KnowledgeRelation`
- `backend/.../knowledge/relation/entity/KnowledgeRelation.java`
- Supports directional links with `source/target EntityType` and `EntityType.INSIGHT`.
- **Finding**: an `INSIGHT → INSIGHT` relation with `SUPERSEDES` is structurally
  supported today.

### `AiProposalContractValidator`
- `validateArchitectureDelta(task, payload)`.
- Currently accepts only `NEW` and `ENRICHES`; `ENRICHES` requires
  `targetInsightId` in selected architecture knowledge.
- **Finding**: extending this to accept `SUPERSEDES` (same target rules) is where
  Core-side supersession validation belongs.

### `IntentCatalog`
- `architecture-overview` output contract declares `allowedDeltaTypes = [NEW,
  ENRICHES]`.
- **Finding**: `SUPERSEDES` must be added to the allowed delta types.

### AI Engine
- `ai-engine/app/schemas/insight.py`: `KnowledgeDeltaType(NEW, ENRICHES)` with
  `target_insight_id` required for `ENRICHES`.
- `ai-engine/app/services/insight_generation_service.py`: validates that an
  `ENRICHES` target is in `existingArchitectureKnowledge`.
- `ai-engine/app/prompts/insight.py`: architecture prompt frames existing
  knowledge as comparison input; instructs the model to return empty proposals
  when nothing material is new.
- **Finding**: schema, generation, and prompt must learn `SUPERSEDES`.

## Architecture Pipeline Today (supersession)

Current lifecycle supports only `NEW` and `ENRICHES`:

Repository Evidence
* Existing Trusted Architecture Knowledge
  → Architecture Analysis
  → Delta Proposal (NEW | ENRICHES | empty)
  → Human Validation
  → Trusted Knowledge Evolution

A promoted statement never changes trust representation. There is no way to
record that a previously valid statement is no longer current while preserving
it as historical truth.

## Idempotence / Consistence Findings

### 1. Stability of analysis
- Deterministic selection, ranking, deduplication, digests. Re-running similar
  inputs yields similar context and outputs. Unchanged (this slice only adds a
  trust-state transition on acceptance).

### 2. Deduplication
- `TrustedKnowledgeDuplicateGuard` compares normalized type + sourceType + title
  + content + rationale to reject equivalent `ACTIVE` statements at acceptance.
- **Finding**: this guard must continue to apply to superseding statements so a
  supersession is not used to bypass duplicate policy.

### 3. Idempotence
- A scan with no contradiction and no dominant new evidence still returns zero
  proposals and changes no trust state (ADR-050 preserved).

### 4. Incremental enrichment
- `ENRICHES` and now `SUPERSEDES` are explicit delta outcomes decided through the
  existing lifecycle, never automatic.

## Smallest Safe Vertical Slice

1. limit the change to `ARCHITECTURE_REVIEW`;
2. add a binary trust-state marker to the trusted-knowledge entity
   (`ACTIVE`/`SUPERSEDED`);
3. add `SUPERSEDES` to the relation vocabulary;
4. extend the architecture delta contract (`IntentCatalog`, validator) to accept
   `SUPERSEDES` with a target;
5. on accepted `SUPERSEDES`: promote the successor as `ACTIVE`, mark the target
   `SUPERSEDED`, and record a `SUPERSEDES` relation (successor → predecessor);
6. extend the AI schema/prompt to emit `SUPERSEDES`;
7. leave `CONFIRMS`, `CONTRADICTS`, and temporal modeling out of scope.

## Recommended Architectural Direction

### Preferred model

Existing Trusted Architecture Knowledge (ACTIVE)

* New Repository Evidence
* Architecture Analysis Intent
  → Analysis
  → Delta Proposal (NEW | ENRICHES | SUPERSEDES | none)
  → Human Validation
  → Trusted Knowledge Evolution
    → target marked SUPERSEDED + SUPERSEDES relation (history preserved)

### Historical truth

A superseded statement remains stored and queryable as historically true; only
its trust-state changes. It is never deleted, edited, or rewritten.

## Risks

1. **Automatic contradiction resolution** — supersession must require human
   validation, not auto-apply.
2. **Duplicate-policy bypass** — the successor must itself satisfy the duplicate
   guard.
3. **Overlapping `ACTIVE` statements** — structural validation keeps the target
   `ACTIVE`-unique per supersession.
4. **Scope creep into a temporal engine / graph** — deferred.
5. **Historical migration** — existing insights default `ACTIVE`; audit is a
   maintenance concern, out of scope.

## Recommendation

Proceed with:

* `ADR-054 — Contradiction and Supersession in Incremental Knowledge Evolution`
* Story 0060 as the first architecture-only slice.

No blocking architectural contradiction was found during reconnaissance. The
existing delta contract, selected-knowledge mechanism, validation lifecycle,
and promotion point already provide the structural basis; the missing pieces
are a trust-state marker, a `SUPERSEDES` relation type, and the extended
contract.