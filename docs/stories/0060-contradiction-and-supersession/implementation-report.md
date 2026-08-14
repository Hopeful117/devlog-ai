# Story 0060 — Contradiction and Supersession in Incremental Knowledge Evolution — Implementation Report

## Status

Implemented

## Summary

Implemented the first contradiction-and-supersession vertical slice of
**ADR-054** for `ARCHITECTURE_REVIEW`, extending the incremental-evolution model
introduced by ADR-050 / Story 0037.

The slice keeps ADR-006 intact while adding a **supersession** trust transition:

```text
Repository Evidence
* Existing Trusted Architecture Knowledge (ACTIVE)
  → Architecture Analysis
  → Delta Proposal (NEW | ENRICHES | SUPERSEDES | none)
  → Existing Human Validation
  → Trusted Knowledge Evolution
    → target marked SUPERSEDED + SUPERSEDES relation (history preserved)
```

The implementation supports:

* `NEW`
* `ENRICHES`
* `SUPERSEDES`
* no-significant-delta behavior through an empty `proposals` array

It does **not** implement `CONFIRMS`, `CONTRADICTS`, a temporal truth engine, or
a deterministic contradiction detector.

## Changes

### 1. ADR

Added:

* `docs/decisions/ADR-054.md`

The ADR formalizes:

* contradiction vs. supersession as distinct concepts;
* supersession as a decided, validated trust transition;
* historical truth preservation (never rewrite the past);
* AI proposes supersession; Java Core owns lifecycle and mutation;
* structural Core-side validation.

### 2. Trust-state marker on trusted knowledge

Added:

* `backend/src/main/resources/db/migration/V41__add_insight_trust_state.sql`
* `backend/src/main/java/com/hopeful117/devlogai/insight/entity/InsightTrustState.java`

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/insight/entity/Insight.java`

The `insights` table gains a `trust_state` column (nullable, additive,
defaulting to `ACTIVE`). `Insight` exposes a `trustState` field
(`ACTIVE`/`SUPERSEDED`) defaulting to `ACTIVE`, with a targeted setter so
promotion can mark a target superseded.

### 3. Relation vocabulary

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/relation/entity/KnowledgeRelationType.java`

Added `SUPERSEDES`, keeping it distinct from `DERIVED_FROM` (enrichment).

### 4. Architecture delta contract

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`

`allowedDeltaTypes` now includes `SUPERSEDES`. `validateArchitectureDelta`
accepts `SUPERSEDES` with a required `targetInsightId` that must exist in the
selected existing architecture knowledge.

### 5. AI-engine schema and prompt

Updated:

* `ai-engine/app/schemas/insight.py`
* `ai-engine/app/prompts/insight.py`
* `ai-engine/app/services/insight_generation_service.py`

`KnowledgeDeltaType` now includes `SUPERSEDES`; `targetInsightId` is required for
both `ENRICHES` and `SUPERSEDES`. Generation validates that a supersession
target exists in the selected `existingArchitectureKnowledge`. The architecture
prompt instructs the model to emit `SUPERSEDES` only when new evidence
contradicts or dominates a supplied trusted item.

### 6. Supersession promotion with history preservation

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPromotionService.java`

Behavior:

* `NEW` / `ENRICHES` promotion is unchanged (`ACTIVE`).
* On accepted `SUPERSEDES`:
  - the successor Insight is promoted as `ACTIVE`;
  - the target Insight is marked `SUPERSEDED`;
  - a `SUPERSEDES` relation is recorded (successor → predecessor).
* Supersession is rejected when the target is missing, from another project, or
  not `ACTIVE`.
* The transition is atomic within the existing promotion transaction.
* The superseded statement is never deleted, edited, or rewritten.

### 7. Read API

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/mapper/InsightMapper.java`

`InsightResponse` exposes `trustState`, mapped by MapStruct from `Insight`.

## Previous Behavior

Repeated architecture analyses reproduced the same conclusions because trusted
knowledge was only a comparison input for `NEW`/`ENRICHES`. A promoted
statement could never change its trust representation, so DevLog could not
represent that a project replaced a technology without keeping both statements
as equally active or rewriting historical truth — both forbidden by ADR-050 and
ADR-006.

## Knowledge Context

### Source

Trusted architecture knowledge comes from persisted trusted `Insight` records
already associated with the current project, selected through the existing
`existingArchitectureKnowledge` mechanism.

### Selection

Project-scoped, architecture-scoped, deterministic selection in
`KnowledgeSelectionServiceImpl`. Reused unchanged from Story 0037.

### Limit

* `maximumArchitectureKnowledge = 5`

### Structure sent to analysis

Each selected item contains trusted insight identity, normalized type, severity,
`sourceType`, title, content, rationale, evidence references, and createdAt. The
supersession target must be drawn from this selected set.

## Knowledge Delta

### Model retained

Payload-level delta metadata plus a trust-state marker:

* `deltaType`
* `targetInsightId`
* `InsightTrustState` (`ACTIVE` / `SUPERSEDED`)

### Supported behaviors

* `NEW`
* `ENRICHES`
* `SUPERSEDES`
* no-significant-delta via zero proposals

### Explicitly deferred

* `CONFIRMS`
* `CONTRADICTS`
* temporal truth engine
* deterministic contradiction detector

## AI Contract

### Changes

Architecture Insight output can now carry `deltaType` `SUPERSEDES` with a
required `targetInsightId`.

The architecture prompt now frames trusted architecture knowledge as comparison
input and permits a supersession when evidence contradicts or dominates a
supplied trusted item.

### Java Core validation

The Core validates structurally:

* `SUPERSEDES` target is required;
* target exists in selected existing architecture knowledge;
* target belongs to the same project;
* target is `ACTIVE`;
* a rejected or invalid supersession never changes trust state.

## Lifecycle

Current lifecycle after implementation:

```text
Repository Evidence
* Existing Trusted Architecture Knowledge
  → Architecture Analysis
  → Delta Proposal (NEW | ENRICHES | SUPERSEDES | none)
  → Existing Human Validation
  → Trusted Knowledge Evolution
  → SUPERSEDED target + SUPERSEDES relation on acceptance
```

## Idempotence

Existing guarantees are preserved:

* deterministic knowledge selection;
* deterministic prompt construction;
* duplicate callback handling;
* evidence deduplication.

New semantic behavior:

* a scan with no contradiction and no dominant new evidence returns zero
  proposals and changes no trust state;
* a supersession is only ever decided through validation, never automatic.

## Tests

Added or updated coverage includes:

* `InsightPromotionServiceTest`
  - accepted supersession marks target `SUPERSEDED` and creates a `SUPERSEDES`
    relation;
  - rejection of a non-active supersession target;
* `AiProposalContractValidatorTest`
  - valid `SUPERSEDES` accepted;
  - invalid target rejected;
* `InsightMapperTest`
  - `trustState` mapped;
* `InsightControllerWebMvcTest`
  - updated `InsightResponse` shape;
* AI-engine schema tests
  - `SUPERSEDES` allowed and `targetInsightId` required.

## Verification

### Backend

* `./mvnw verify`
  - **BUILD SUCCESS**
  - **647 tests**
  - JaCoCo check passed

### AI Engine

* `./.venv/bin/python -m pytest -q`
  - **52 tests passed**

## Documentation Update

Required.

Updated documentation:

* `docs/decisions/ADR-054.md`
* Story 0060 artifacts

No frontend, setup, or operational documentation required updates in this slice.

## Vault Outcome

No vault action.

Rationale:

* the Story evolves DevLog's internal knowledge lifecycle but does not yet
  establish a new cross-project standard mature enough for curated transverse
  capture;
* ADR-054 and the Story-local reports are the correct canonical artifacts for
  this stage.

## Limitations

Real limitations of V1:

1. supersession is only supported for `ARCHITECTURE_REVIEW`;
2. the trust-state marker is binary (`ACTIVE`/`SUPERSEDED`), not a full temporal
   model;
3. contradiction is surfaced through the supersession contract only; no
   deterministic contradiction detector exists yet;
4. legacy trusted insights default to `ACTIVE`; no historical audit was
   performed for current-correctness.