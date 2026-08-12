# Story 0037 — Incremental Architecture Knowledge Evolution — Implementation Report

## Status

Implemented

## Summary

Implemented the first vertical slice of **ADR-050 — Incremental Knowledge
Evolution** for `ARCHITECTURE_REVIEW`.

The slice keeps ADR-006 intact while changing the architecture-analysis model
from:

Repository Evidence
→ Analysis
→ Proposal
→ Validation
→ Trusted Knowledge

to a first incremental form:

Relevant Existing Trusted Architecture Knowledge
* New Repository Evidence
  → Architecture Analysis
  → Structured Delta Proposal
  → Existing Validation Lifecycle
  → Trusted Knowledge Evolution

The implementation supports:

* `NEW`
* `ENRICHES`
* no-significant-delta behavior through an empty `proposals` array

It does **not** yet implement contradiction, supersession, or temporal
knowledge lifecycle.

## Changes

### 1. ADR

Added:

* `docs/decisions/ADR-050.md`

The ADR formalizes:

* cumulative knowledge;
* semantic idempotence distinct from technical idempotence;
* knowledge delta categories;
* bounded trusted-knowledge reuse;
* Java Core ownership of lifecycle;
* preservation of historical truth.

### 2. Dedicated existing architecture knowledge input

Updated:

* `SelectedKnowledge`
* `KnowledgeSelectionServiceImpl`

Added a dedicated section:

* `existingArchitectureKnowledge`

This section is:

* project-scoped;
* architecture-scoped;
* deterministic;
* bounded to 5 entries in V1.

Selection currently reuses trusted `Insight` persistence and includes
architecture-relevant knowledge identified via:

* `sourceType` in architecture categories when available;
* fallback to normalized `InsightType.ARCHITECTURAL` /
  `InsightType.TECHNOLOGY` for older knowledge with no `sourceType`.

This is intentionally narrower than the generic `selectedInsights` list, which
is still preserved for backward-compatible historical context.

### 3. Architecture delta contract

Updated:

* `IntentCatalog`
* `InsightProposalPayloadResponse`
* `ValidatableProposalMapper`

For architecture analysis, Insight payloads now carry explicit delta metadata:

* `deltaType`
* `targetInsightId` (required for `ENRICHES`, forbidden for `NEW`)

This lets the Java Core validate lifecycle semantics structurally rather than
inferring them from free text.

### 4. Core-side validation

Updated:

* `AiProposalContractValidator`

Added strict validation for architecture Insight proposals:

* `insightType` must be valid and allowed by the Intent;
* `deltaType` must be `NEW` or `ENRICHES` for
  `architecture-overview-v1`;
* `targetInsightId` is required only for `ENRICHES`;
* enrichment target must exist in `selectedKnowledge.existingArchitectureKnowledge`.

This preserves scope safety:

* no foreign project target;
* no arbitrary trusted-knowledge mutation target;
* no natural-language lifecycle parsing.

### 5. AI-engine prompt and schema

Updated:

* `ai-engine/app/schemas/insight.py`
* `ai-engine/app/prompts/insight.py`
* `ai-engine/app/services/insight_generation_service.py`

Changes:

* insight schema now supports `deltaType` and `targetInsightId`;
* architecture prompts now include a dedicated
  `EXISTING TRUSTED ARCHITECTURE KNOWLEDGE` section;
* prompt instructions explicitly ask the model to:
  * compare new evidence with trusted architecture knowledge;
  * emit `NEW` or `ENRICHES` only when meaningful;
  * return an empty proposals array when nothing materially new is learned.

For non-architecture intents, no new delta metadata is injected into payloads.

### 6. Lifecycle behavior

Updated:

* `InsightPromotionService`

Behavior:

* `NEW` proposals are promoted as ordinary accepted Insights.
* `ENRICHES` proposals are also promoted as new accepted Insights, preserving
  immutable proposal history.
* On accepted `ENRICHES`, the new Insight is linked to the target trusted
  Insight through a `KnowledgeRelation`:
  * source = newly accepted Insight
  * target = enriched trusted Insight
  * relation type = `DERIVED_FROM`

This gives the first traceable trusted-knowledge evolution path without
rewriting prior trusted knowledge and without introducing a dedicated persisted
`KnowledgeDelta` entity.

### 7. No-significant-delta behavior

No new persistence model was added for this V1.

Instead:

* an architecture analysis may return `proposals: []`;
* the task completes successfully;
* no `ValidatableProposal` is created;
* no human validation is triggered;
* no trusted knowledge is mutated.

This preserves idempotence while avoiding synthetic records.

## Previous Behavior

Repeated architecture analyses often reproduced the same conclusions because:

1. evidence selection was already deterministic and bounded;
2. prompt construction was already stable;
3. trusted insights were visible only as generic historical context;
4. the output contract still modeled “propose architecture insights”, not
   “propose knowledge delta”;
5. every valid proposal was persisted independently of semantic equivalence to
   existing trusted knowledge.

The repetition was therefore caused by the absence of an incremental evolution
contract, not by instability in evidence selection.

## Knowledge Context

### Source

Trusted architecture knowledge now comes from persisted trusted `Insight`
records already associated with the current project.

### Selection

Selection is:

* project-scoped;
* architecture-focused;
* deterministic;
* bounded.

### Limit

* `maximumArchitectureKnowledge = 5`

### Structure sent to analysis

Each selected item contains:

* `insightId`
* `proposalId`
* normalized type
* severity
* `sourceType`
* title
* content
* rationale
* evidence references
* createdAt

## Knowledge Delta

### Model retained

Minimal payload-based delta metadata:

* `deltaType`
* `targetInsightId`

### Supported behaviors

* `NEW`
* `ENRICHES`
* no-significant-delta via zero proposals

### Explicitly deferred

* `CONFIRMS`
* `CONTRADICTS`
* `SUPERSEDES` / `INVALIDATES`
* temporal truth engine
* dedicated persisted delta entity

## AI Contract

### Changes

Architecture Insight output can now carry:

* `deltaType`
* `targetInsightId`

Prompt semantics now explicitly frame trusted architecture knowledge as
comparison input rather than passive historical noise.

### Java Core validation

The Core now validates:

* Insight type compatibility with the Intent;
* valid architecture delta semantics;
* enrichment target identity restricted to selected trusted architecture
  knowledge.

## Lifecycle

Current lifecycle after implementation:

Repository Evidence
* Existing Trusted Architecture Knowledge
  → Architecture Analysis
  → Delta Proposal
  → Validation
  → Trusted Knowledge
  → KnowledgeRelation trace when enrichment is accepted

## Idempotence

Existing guarantees are preserved:

* deterministic knowledge selection;
* deterministic prompt construction;
* duplicate callback handling;
* evidence deduplication.

New semantic behavior:

* repeated architecture analysis with no material delta can complete with zero
  proposals;
* no equivalent trusted-knowledge duplicate needs to be created merely because
  the scan succeeded.

## Tests

Added or updated coverage includes:

* `KnowledgeSelectionServiceAdditionalTest`
  - bounded `existingArchitectureKnowledge`
* `KnowledgeSelectionServiceTest`
  - no unexpected architecture-knowledge section for generic context
* `AiProposalContractValidatorTest`
  - valid enrichment target
  - invalid foreign / missing enrichment target
* `InsightPromotionServiceTest`
  - accepted enrichment creates a `KnowledgeRelation`
* `SmallClassCoverageTest`
  - updated typed proposal payload DTO
* `RestAIEngineClientTest`
  - updated `KnowledgeBudget` shape
* AI-engine prompt tests
  - architecture prompt includes trusted architecture knowledge section
* AI-engine generation tests
  - enrichment payload carries delta metadata
  - empty proposals accepted for no-significant-delta

## Verification

### Backend

* `./mvnw verify`
  - **BUILD SUCCESS**
  - **566 tests**
  - JaCoCo check passed

### AI Engine

* `./.venv/bin/python -m pytest -q`
  - **48 tests passed**

## Documentation Update

Required.

Updated documentation:

* `docs/decisions/ADR-050.md`
* Story 0037 artifacts

No frontend, setup, or operational documentation required updates in this
slice.

## Vault Outcome

No vault action.

Rationale:

* the Story evolves DevLog’s internal architecture and knowledge lifecycle, but
  does not yet establish a new cross-project standard mature enough for curated
  transverse capture;
* ADR-050 and the Story-local reports are the correct canonical artifacts for
  this stage.

## Limitations

Real limitations of V1:

1. legacy trusted insights without `sourceType` can only be approximated via
   normalized Insight types.
2. `ENRICHES` uses `DERIVED_FROM` as the existing relation vocabulary’s closest
   traceability fit; no dedicated `ENRICHES` relation type exists yet.
3. no contradiction or supersession lifecycle is implemented yet.
