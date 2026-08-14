# Story 0060 — Contradiction and Supersession in Incremental Knowledge Evolution — Implementation Plan

## Overview

Implement the first contradiction-and-supersession vertical slice for
`ARCHITECTURE_REVIEW`, extending the incremental-evolution model introduced by
ADR-050 / Story 0037 without weakening the existing proposal / validation /
promotion boundaries.

The preferred design is:

1. formalize the architecture in `ADR-054`;
2. keep the slice architecture-only;
3. add a binary trust-state marker to trusted knowledge to distinguish current
   from historical truth;
4. add an explicit `SUPERSEDES` relation to record the transition;
5. extend the architecture AI contract so a supersession is expressed
   structurally;
6. preserve ADR-006 by keeping all trusted-knowledge lifecycle authority in the
   Java Core;
7. keep idempotence intact: no contradiction and no dominant new evidence still
   means no proposal and no trust-state change.

The first slice should prove the lifecycle end-to-end with the smallest possible
model:

* `NEW`
* `ENRICHES`
* `SUPERSEDES`
* no-significant-delta behavior

No `CONFIRMS`, `CONTRADICTS`, temporal-history engine, or full
`KnowledgeRelation` redesign will be attempted in this Story.

## Planned Changes

### 1. Create `ADR-054 — Contradiction and Supersession in Incremental Knowledge Evolution`

Add:

* `docs/decisions/ADR-054.md`

Implementation intent:

* use the existing ADR template and conventions exactly;
* align explicitly with:
  - ADR-006 for lifecycle authority;
  - ADR-050 for incremental evolution and historical truth;
  - ADR-051 for the duplicate policy;
* define the conceptual distinction between contradiction and supersession;
* require supersession to pass through the existing human validation lifecycle;
* require historical truth to be preserved, never rewritten;
* choose a binary trust-state marker over a full temporal engine;
* explicitly limit this Story's implementation slice to `SUPERSEDES` in addition
  to the already-supported `NEW` / `ENRICHES`.

Why first:

* the trust-state and lifecycle meaning are broader than a local implementation
  detail;
* the Story needs ADR-backed semantics before changing prompt and Core
  validation contracts.

### 2. Add a trust-state marker to the trusted-knowledge entity

Update likely components:

* `backend/src/main/resources/db/migration/V41__add_insight_trust_state.sql`
* `backend/src/main/java/com/hopeful117/devlogai/insight/entity/InsightTrustState.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/entity/Insight.java`

Implementation intent:

* add a `trust_state` column to `insights`, nullable, additive, defaulting to
  `ACTIVE` for existing rows;
* introduce `InsightTrustState` enum with `ACTIVE` and `SUPERSEDED`;
* add a `trustState` field to `Insight`, defaulting to `ACTIVE` on promotion;
* expose a targeted setter so promotion can mark a target superseded.

### 3. Add `SUPERSEDES` to the relation vocabulary

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/relation/entity/KnowledgeRelationType.java`

Implementation intent:

* add `SUPERSEDES` as a first-class directional relation type;
* keep `DERIVED_FROM` for enrichment so the two traceability paths stay
  distinct;
* the relation is successor → predecessor (`INSIGHT` → `INSIGHT`).

### 4. Extend the architecture delta contract to accept `SUPERSEDES`

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`

Implementation intent:

* add `SUPERSEDES` to `allowedDeltaTypes` for the architecture intent;
* extend `validateArchitectureDelta` to accept `SUPERSEDES` with a required
  `targetInsightId`;
* enforce that the target exists in the selected existing architecture
  knowledge;
* do not infer supersession intent from natural language.

### 5. Update the architecture prompt and schema for supersession

Update likely components:

* `ai-engine/app/schemas/insight.py`
* `ai-engine/app/prompts/insight.py`
* `ai-engine/app/services/insight_generation_service.py`

Implementation intent:

* add `SUPERSEDES` to `KnowledgeDeltaType`;
* require `targetInsightId` for `SUPERSEDES` (mirroring `ENRICHES`);
* validate that a `SUPERSEDES` target exists in the selected
  `existingArchitectureKnowledge`;
* instruct the model to emit `SUPERSEDES` only when new evidence contradicts or
  dominates a supplied trusted item, otherwise keep `NEW`/`ENRICHES` or return
  an empty proposals array.

### 6. Implement supersession promotion with history preservation

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPromotionService.java`

Implementation intent:

* `NEW` / `ENRICHES` promotion remains unchanged (`ACTIVE`);
* on accepted `SUPERSEDES`:
  - promote the successor Insight as `ACTIVE`;
  - mark the target Insight `SUPERSEDED`;
  - record a `SUPERSEDES` relation from the successor to the predecessor;
* reject a supersession whose target is missing, from another project, or not
  `ACTIVE`;
* keep the transition atomic within the existing promotion transaction;
* never delete, edit, or rewrite the superseded statement.

### 7. Expose `trustState` on the read API

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/mapper/InsightMapper.java`

Implementation intent:

* add `trustState` to `InsightResponse`;
* rely on MapStruct field mapping for `Insight → InsightResponse`.

### 8. Add focused automated coverage across the vertical slice

Update likely tests:

* `InsightPromotionServiceTest` — accepted supersession marks target
  `SUPERSEDED` and creates a `SUPERSEDES` relation; rejected / non-active target
  leaves the target unchanged;
* `AiProposalContractValidatorTest` — valid `SUPERSEDES` accepted; invalid target
  rejected;
* `InsightMapperTest` — `trustState` mapped;
* `InsightControllerWebMvcTest` — DTO shape updated;
* AI-engine schema tests — `SUPERSEDES` allowed and target required.

Minimum scenarios to cover:

* existing knowledge + identical evidence
  - no equivalent trusted-knowledge result, no trust-state change
* existing knowledge + dominant / contradicting additional evidence
  - supersession proposal possible
* no relevant existing architecture knowledge
  - new proposal possible
* different project
  - no context leakage
* non-active / foreign / missing supersession target
  - rejected, no trusted mutation
* rejected proposal
  - no trusted mutation
* accepted proposal
  - trusted-knowledge evolution through the existing lifecycle

### 9. Run the full quality gates unchanged

Expected validation:

* backend `./mvnw verify`
* AI-engine test suite for the modified prompt/schema contract
* any repository-standard static checks already required by current stories
* quality gates introduced by recent stories must remain intact

No threshold reduction, bypass, or rule softening is permitted.

## Files to Modify

Expected primary modifications:

* `docs/decisions/ADR-054.md`
* `docs/stories/0060-contradiction-and-supersession/*`
* `backend/src/main/resources/db/migration/V41__add_insight_trust_state.sql`
* `backend/src/main/java/com/hopeful117/devlogai/insight/entity/InsightTrustState.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/entity/Insight.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/mapper/InsightMapper.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/relation/entity/KnowledgeRelationType.java`
* `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPromotionService.java`
* relevant backend tests
* `ai-engine/app/schemas/insight.py`
* `ai-engine/app/prompts/insight.py`
* `ai-engine/app/services/insight_generation_service.py`

## Files Not Expected to Change

Unless implementation reveals hidden coupling, the following should remain
unchanged:

* frontend code
* repository-context ranking / selection engine
* engineering-story integration outside normal DevLog behavior
* `CONFIRMS` / `CONTRADICTS` / temporal-history subsystems
* project-state UI / timeline UI
* artifact-generation features

## Sequencing

1. Write `ADR-054`.
2. Add the trust-state marker and `InsightTrustState` enum + migration.
3. Add `SUPERSEDES` to the relation vocabulary.
4. Extend the architecture output contract and AI prompt/schema.
5. Extend Core-side contract validation.
6. Implement supersession promotion with history preservation.
7. Expose `trustState` on the read API.
8. Add backend and AI-engine regression coverage.
9. Run quality gates.
10. Produce implementation, review, and engineering reports.

## Validation

Automated validation should include at minimum:

* targeted backend unit tests for contract validation and promotion;
* AI-engine tests for schema enforcement and prompt construction;
* full backend `verify`.

The final report should explicitly show:

* how supersession is expressed in the contract;
* how the Core validates it;
* how historical truth is preserved;
* what trust-state behaviors are supported now;
* what remains intentionally deferred.

## Risks and Controls

### Risk: supersession becomes automatic contradiction resolution

Control:

* require the existing human validation lifecycle for every supersession.

### Risk: supersession is used to bypass duplicate policy

Control:

* the promoted successor still passes `TrustedKnowledgeDuplicateGuard`;
* the target must be `ACTIVE` and in the selected architecture knowledge.

### Risk: overlapping `ACTIVE` statements

Control:

* structural validation plus the duplicate guard keep supersession and duplicate
  semantics distinct.

### Risk: scope explodes into a temporal engine or graph

Control:

* keep the slice architecture-only with a binary trust-state marker and one new
  relation type.

### Risk: historical data migration

Control:

* existing insights default to `ACTIVE`; no historical backfill is performed in
  this Story.

## Completion Criteria

The Story is complete when:

* ADR-054 is added and coherent with existing ADRs;
* trusted knowledge carries a trust-state marker;
* the AI contract can express `SUPERSEDES`;
* accepted supersession marks the target `SUPERSEDED` and records a
  `SUPERSEDES` relation without rewriting the past;
* rejected supersession leaves trusted knowledge unchanged;
* repeated identical architecture analysis still produces no redundant outcome
  and no trust-state change;
* relevant automated validation and quality gates pass unchanged.