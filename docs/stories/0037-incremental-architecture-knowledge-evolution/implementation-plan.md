# Story 0037 — Incremental Architecture Knowledge Evolution — Implementation Plan

## Overview

Implement the first incremental-knowledge vertical slice for `ARCHITECTURE_REVIEW`
without weakening the existing proposal / validation / promotion boundaries.

The preferred design is:

1. formalize the architecture in `ADR-050`;
2. keep the slice architecture-only;
3. introduce a dedicated bounded existing-knowledge context instead of overloading generic
   historical `selectedInsights`;
4. evolve the architecture AI contract so the result can express structured delta semantics;
5. preserve ADR-006 by keeping all trusted-knowledge lifecycle authority in the Java Core;
6. treat “no significant delta” as an explicit no-new-proposal outcome rather than an empty
   synthetic knowledge record.

The first slice should prove useful incremental behavior with the smallest possible model:

* `NEW`
* `ENRICHES`
* no-significant-delta behavior

No contradiction, supersession, or temporal-history redesign will be attempted in this Story.

## Planned Changes

### 1. Create `ADR-050 — Incremental Knowledge Evolution`

Add:

* `docs/decisions/ADR-050.md`

Implementation intent:

* use the existing ADR template and conventions exactly;
* align explicitly with:
  - ADR-006 for lifecycle authority;
  - ADR-049 for semantic preservation;
  - recent Knowledge ADRs when relevant;
* define the conceptual model:
  - existing trusted knowledge as possible input;
  - analysis producing a knowledge delta rather than always reconstructing standalone knowledge;
* define semantic categories at ADR level:
  - `NEW`
  - `ENRICHES`
  - `CONFIRMS`
  - `CONTRADICTS`
  - `SUPERSEDES` / `INVALIDATES`
* explicitly limit this Story’s implementation slice to:
  - `NEW`
  - `ENRICHES`
  - no-significant-delta behavior.

Why first:

* the contract and lifecycle meaning are broader than a local implementation detail;
* the Story needs ADR-backed semantics before changing prompt and Core validation contracts.

### 2. Add a dedicated architecture existing-knowledge selection model

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* possibly a dedicated helper or selector class in the same package

Implementation intent:

* keep current `selectedInsights` behavior for backward-compatible historical context unless the
  change can be made safely in one step;
* add a new dedicated structure for relevant existing trusted architecture knowledge, for example:
  - project-scoped;
  - architecture-scoped;
  - bounded;
  - deterministic ordering;
* derive the new section from trusted persisted knowledge already available in the project;
* prefer reusing existing `Insight` persistence first instead of introducing a new stored model;
* include enough structured data to support:
  - semantic comparison target selection;
  - enrichment targeting;
  - provenance back to the trusted record.

Selection rules should be:

* project-scoped only;
* limited to architecture-relevant trusted knowledge;
* deterministic;
* bounded to a small fixed limit;
* narrow enough to avoid prompt noise.

### 3. Keep selection architecture-specific and minimally invasive

Implementation intent:

* activate the new existing-knowledge section only for `architecture-overview-v1`;
* do not generalize prematurely to all intents;
* avoid redesigning the full repository-context engine;
* reuse current `KnowledgeSelectionServiceImpl` orchestration unless inspection during coding
  proves that a dedicated selector class materially improves clarity.

Preferred direction:

* a dedicated architecture-existing-knowledge snapshot model attached to `SelectedKnowledge`,
  rather than changing generic repository evidence semantics.

### 4. Evolve the architecture AI output contract to represent delta semantics

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiProposalResult.java`
* response payload DTOs / payload parsing utilities if needed
* AI-engine schema files for insight output

Implementation intent:

* keep output structured;
* do not require Java Core to infer delta semantics from natural language;
* add minimal fields enabling the Core to understand whether a result is:
  - `NEW`
  - `ENRICHES`
* represent enrichment target identity explicitly when applicable;
* support no-significant-delta behavior without fabricating a fake proposal.

Preferred shape:

* architecture insight proposal payload gains explicit delta metadata;
* or a tightly scoped architecture-only proposal contract version is introduced if that keeps the
  contract cleaner than mutating the generic one.

Constraint:

* any contract evolution must remain fully validated on both Core and AI-engine sides.

### 5. Update the architecture prompt to frame trusted knowledge as comparison context

Update likely components:

* `ai-engine/app/prompts/insight.py`
* related AI-engine models / fixtures / tests

Implementation intent:

* keep existing trust-boundary instructions intact;
* continue treating repository evidence and guidance as untrusted input;
* add a dedicated prompt section for relevant existing trusted architecture knowledge;
* explicitly ask the AI to evaluate new evidence relative to what DevLog already knows;
* instruct the AI not to restate semantically equivalent trusted knowledge as a new proposal;
* allow zero proposals when nothing materially new is learned.

This should move the prompt from:

* “describe architecture”

toward:

* “given existing trusted architecture knowledge and current evidence, propose only meaningful
  architecture knowledge delta”.

### 6. Extend Core-side contract validation for incremental architecture results

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`
* related test classes

Implementation intent:

* validate the new architecture delta fields explicitly;
* ensure enrichment targets, when present, refer only to allowed existing-knowledge identities
  supplied in the selected input;
* reject malformed or cross-project / foreign-target payloads;
* keep evidence-grounding validation intact.

The validator should remain strict:

* type-safe;
* scope-safe;
* deterministic;
* no natural-language interpretation required.

### 7. Reuse the current proposal / validation / promotion lifecycle

Update likely components:

* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java`
* validation / promotion services only if required
* potentially proposal payload mapping helpers

Implementation intent:

* `NEW` results continue to become ordinary `ValidatableProposal` records;
* `ENRICHES` results also become `ValidatableProposal` records, carrying enough payload metadata
  for the accepted lifecycle to evolve trusted knowledge correctly;
* no-significant-delta behavior produces no proposal, no forced validation task, and no fake
  trusted record.

Important constraint:

* do not let the AI directly mutate trusted knowledge;
* any trusted evolution still happens only after explicit acceptance.

If the current promotion model lacks the minimum hook needed for enrichment:

* prefer the smallest targeted extension of the existing model;
* avoid introducing a full persisted `KnowledgeDelta` entity in this Story.

### 8. Implement the minimal accepted-enrichment behavior

Likely updates depend on the chosen contract, but the preferred direction is:

* preserve the accepted proposal history as its own immutable record;
* evolve trusted architecture knowledge in a traceable way using existing domain concepts where
  possible;
* preserve historical truth instead of overwriting the past invisibly.

Practical expectation for this Story:

* accepted enrichment must produce a demonstrable trusted-knowledge evolution outcome;
* that outcome must remain traceable to:
  - proposal;
  - validation;
  - evidence;
  - prior trusted knowledge target when applicable.

If implementation reveals that proper enrichment requires an additional architectural decision not
covered by ADR-050, stop and surface it before widening scope.

### 9. Add focused automated coverage across the vertical slice

Update likely tests:

* `KnowledgeSelectionService...` tests
* `AnalysisContext...` tests if needed
* `AiProposalContractValidatorTest`
* `AiTaskResultServiceTest`
* promotion / validation tests for accepted enrichments
* AI-engine prompt and schema tests
* possibly end-to-end integration tests around the architecture-analysis lifecycle

Minimum scenarios to cover:

* existing knowledge + identical evidence
  - no additional semantically equivalent trusted-knowledge result
* existing knowledge + meaningful additional evidence
  - enrichment proposal possible
* no relevant existing architecture knowledge
  - new proposal possible
* different project
  - no context leakage
* non-architecture trusted knowledge
  - no architecture-context pollution
* rejected proposal
  - no trusted mutation
* accepted proposal
  - trusted-knowledge evolution through the existing lifecycle

### 10. Run the full quality gates unchanged

Expected validation:

* backend `./mvnw verify`
* AI-engine test suite for modified prompt/schema contract
* any repository-standard static checks already required by current stories
* quality gates introduced by recent stories must remain intact

No threshold reduction, bypass, or rule softening is permitted.

## Files to Modify

Expected primary modifications:

* `docs/decisions/ADR-050.md`
* `docs/stories/0037-incremental-architecture-knowledge-evolution/*`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java`
* relevant backend tests
* `ai-engine/app/prompts/insight.py`
* relevant AI-engine schema/model/test files

Potentially affected depending on the cleanest implementation:

* payload DTOs / mappers for insight proposals
* promotion / validation services
* integration-test fixtures

## Files Not Expected to Change

Unless implementation reveals hidden coupling, the following should remain unchanged:

* frontend code
* repository-context ranking engine beyond narrow selection wiring
* engineering-story integration outside normal DevLog behavior
* contradiction / supersession / temporal-history subsystems
* project-state UI / timeline UI
* artifact-generation features

## Sequencing

1. Write `ADR-050`.
2. Add the dedicated existing-knowledge selection model for architecture analysis.
3. Evolve the architecture output contract and AI prompt/schema.
4. Extend Core-side contract validation.
5. Wire no-significant-delta behavior and incremental proposal persistence.
6. Implement minimal accepted-enrichment behavior.
7. Add backend and AI-engine regression coverage.
8. Run quality gates.
9. Produce implementation, review, and engineering reports.

## Validation

Automated validation should include at minimum:

* targeted backend unit/integration tests for selection, contract validation, task result
  handling, and promotion/validation flow
* AI-engine tests for prompt construction and schema enforcement
* full backend `verify`

The final report should explicitly show:

* why repeated architecture analysis previously repeated itself;
* where trusted knowledge now comes from;
* how it is selected and bounded;
* what delta behaviors are supported now;
* what remains intentionally deferred.

## Risks and Controls

### Risk: semantic scope explodes into a full knowledge-graph redesign

Control:

* keep the Story limited to architecture-only incremental evolution and the minimum delta model.

### Risk: no-significant-delta becomes an implicit or ambiguous behavior

Control:

* make the no-new-proposal path explicit in design, tests, and implementation report.

### Risk: enrichment targets become unsafe or ambiguous

Control:

* require explicit target identity and validate it against selected existing architecture
  knowledge only.

### Risk: existing trusted knowledge becomes prompt noise

Control:

* add a dedicated bounded selection rather than dumping all trusted knowledge into the prompt.

### Risk: lifecycle boundaries get blurred

Control:

* keep ADR-006 as the hard boundary: AI proposes, Core validates, human accepts, Core promotes.

## Completion Criteria

The Story is complete when:

* ADR-050 is added and coherent with existing ADRs;
* architecture analysis receives dedicated bounded existing trusted architecture knowledge;
* the AI contract can express `NEW`, `ENRICHES`, and no-significant-delta behavior;
* repeated identical architecture analysis no longer creates a redundant equivalent
  trusted-knowledge outcome;
* accepted incremental proposals still flow through the existing validation lifecycle;
* rejected proposals leave trusted knowledge unchanged;
* relevant automated validation and quality gates pass unchanged.
