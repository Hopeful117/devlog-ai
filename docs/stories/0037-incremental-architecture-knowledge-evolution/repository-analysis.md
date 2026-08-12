# Story 0037 — Incremental Architecture Knowledge Evolution — Repository Analysis

## Purpose

Read-only reconnaissance of the current architecture-analysis lifecycle to determine the
smallest safe vertical slice for incremental knowledge evolution.

This analysis was performed before any ADR draft or implementation changes.

## Relevant Components

### `docs/stories/0035-richer-validated-knowledge`
- Story 0035 implements ADR-049 by preserving semantic richness when accepted proposals are
  promoted into trusted `Insight` records.
- It improves the quality of trusted knowledge persistence, but it does **not** add a model for
  repeated analyses to compare new evidence against existing trusted knowledge.

### `ADR-006`
- Governing lifecycle ADR.
- Confirms that:
  - AI output becomes `ValidatableProposal`;
  - accepted and rejected proposals are immutable;
  - new analysis with a different interpretation must create a **new proposal**;
  - Java Core owns validation and promotion into trusted knowledge.
- This remains fully compatible with incremental evolution as long as the AI proposes deltas and
  the Core owns their lifecycle.

### `ADR-049`
- Governs semantic preservation during promotion.
- Important precondition for this Story because richer trusted knowledge improves future reuse as
  analysis context.
- Does not define cumulative evolution semantics.

### `AnalysisContextServiceImpl`
- Builds `AnalysisContext` from:
  - current analysis facts and observations;
  - project-level historical context;
  - `projectContext.validatedProposals()`.
- For `ARCHITECTURE_REVIEW`, also injects:
  - related analyses;
  - architecture artifacts;
  - decisions.
- **Finding**: trusted/validated proposal history is already available when a new architecture
  analysis is prepared.

### `ProjectContextProviderImpl`
- Loads accepted proposals with:
  - `proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(projectId, ACCEPTED, ...)`
- Caps `validatedProposals` at 20.
- **Finding**: accepted proposal history is already project-scoped and bounded at snapshot time.

### `KnowledgeSelectionServiceImpl`
- Selects:
  - ranked facts;
  - ranked observations;
  - recent trusted `Insight` entities (`selectedInsights`);
  - repository context;
  - engineering events when relevant.
- `selectedInsights` are currently loaded through:
  - `insightRepository.findByProjectIdOrderByCreatedAtDesc(context.project().id())`
  - then truncated to `maximumInsights = 10`.
- **Finding**: trusted knowledge is already present in analysis input, but only as a generic list
  of recent insights. There is no architecture-specific incremental selection, no semantic
  equivalence model, and no delta framing.

### `SelectedKnowledge`
- Contains:
  - `selectedInsights`
  - `repositoryContext`
  - deterministic `selectionDigest`
- No dedicated field exists for:
  - relevant existing architecture knowledge;
  - knowledge-delta candidates;
  - equivalence anchors;
  - no-significant-delta semantics.

### `IntentCatalog`
- `architecture-overview-v1`:
  - output proposal type: `INSIGHT`
  - supported insight types:
    - `ARCHITECTURE_DESCRIPTION`
    - `TECHNOLOGY_DESCRIPTION`
    - `INFRASTRUCTURE_DESCRIPTION`
    - `API_DESCRIPTION`
  - output contract currently requires:
    - `insightType`
    - `title`
    - `summary`
    - `rationale`
    - `confidence`
    - `supportingFactIds`
    - `supportingObservationIds`
    - `evidenceReferences`
- **Finding**: the current contract only models “produce an Insight proposal”. It has no explicit
  field for delta semantics such as `NEW`, `ENRICHES`, or no-change.

### AI Engine `InsightPromptBuilder`
- Requires and serializes:
  - `selectedFacts`
  - `selectedObservations`
  - `selectedInsights`
  - `selectionMetadata`
  - `selectionDigest`
- For architecture prompt:
  - objective is “Describe demonstrable architectural characteristics without quality
    judgements.”
- **Finding**: the AI already sees prior trusted insights, but only inside a flat prompt context.
  The prompt does not ask:
  - what existing trusted architecture knowledge already says;
  - whether new evidence confirms or enriches it;
  - whether there is no significant delta.

### `AiProposalContractValidator`
- Validates:
  - output proposal type matches intent;
  - evidence references belong to selected knowledge;
  - engineering-event payload rules.
- No dedicated validation currently exists for incremental-insight delta semantics.

### `AiTaskResultServiceImpl`
- Technical idempotence:
  - duplicate callbacks for terminal tasks are acknowledged safely;
  - valid proposals are persisted once per task result;
  - prompt metadata and context digest are persisted for traceability.
- Semantic behavior:
  - every returned valid proposal becomes a new `ValidatableProposal`;
  - there is no semantic equivalence or enrichment-aware handling against existing trusted
    knowledge.

### `InsightPromotionService` + validation flow
- Accepted proposals are promoted atomically into trusted knowledge.
- Story 0035 / ADR-049 improved semantic preservation at promotion time.
- **Finding**: promotion is richer now, but still promotes accepted proposals independently. There
  is no existing “evolve prior knowledge” abstraction.

## Architecture Pipeline Today

Current repeated architecture-analysis lifecycle is effectively:

Repository Evidence
→ Deterministic Selection
→ AI Insight Proposal(s)
→ Human Validation
→ Trusted Knowledge

On the next analysis, the system does include some historical trusted context, but the lifecycle
still behaves conceptually as:

Repository Evidence
→ Deterministic Selection
→ AI Insight Proposal(s)
→ Human Validation
→ Trusted Knowledge

again.

This means prior trusted knowledge influences prompt context, but does not participate as a
first-class comparison target in the proposal contract.

## Idempotence Findings

The repository currently implements several different concepts that must not be conflated.

### 1. Stability of analysis
- `KnowledgeSelectionServiceImpl` produces deterministic ranking, truncation, deduplication, and
  `selectionDigest`.
- `InsightPromptBuilder` generates canonical prompt content and deterministic content digests.
- Re-running the same analysis with materially similar inputs tends to produce similar prompt
  inputs and therefore similar AI outputs.

### 2. Deduplication
- Repository evidence selection deduplicates duplicate evidence candidates.
- Fact selection deduplicates duplicate fact content.
- Engineering-event contract validation rejects duplicate event proposals within one result set.
- This deduplication applies to input evidence or same-callback payloads, not to trusted
  knowledge evolution across analyses.

### 3. Idempotence
- AI callback handling is idempotent at the task-result boundary:
  - repeated terminal callbacks are acknowledged as duplicates instead of reprocessing.
- Request / digest traceability preserves reproducibility and duplicate detection for execution
  artifacts.
- There is **no semantic idempotence rule** stating:
  “if a proposal is equivalent to existing trusted architecture knowledge, do not create another
  knowledge record.”

### 4. Enrichment cumulatif
- Not currently modeled.
- Existing trusted knowledge can be seen by the AI as context, but the contract does not
  distinguish:
  - restating known architecture;
  - strengthening known architecture;
  - enriching known architecture;
  - or finding nothing materially new.

## Why Repeated Architecture Analysis Repeats Itself

Repeated architecture analyses currently tend to reproduce the same conclusions because:

1. evidence selection is intentionally stable and deterministic;
2. architecture prompts are bounded and structured;
3. prior trusted insights are supplied only as generic historical context;
4. the output contract still asks for architecture insight proposals, not knowledge deltas;
5. the Core persists any valid new proposal independently of semantic equivalence to already
   trusted architecture knowledge.

So the repetition is not a bug in selection stability.

It is the absence of a first-class incremental-evolution model.

## Trusted Knowledge Availability at Analysis Time

Yes, trusted knowledge is available when a new architecture analysis is prepared.

It exists in two relevant forms:

1. `AnalysisContext.validatedProposals`
   - accepted proposal history
   - project-scoped
   - bounded by `MAX_VALIDATED_PROPOSALS = 20`

2. `SelectedKnowledge.selectedInsights`
   - trusted insight entities
   - project-scoped
   - bounded by `maximumInsights = 10`

However, neither structure is currently selected and framed as:

* relevant existing architecture knowledge;
* delta comparison anchors;
* or enrichment targets.

## Smallest Safe Vertical Slice

The smallest slice that addresses the observed problem without over-design is:

1. limit the change to `ARCHITECTURE_REVIEW`;
2. add a deterministic, bounded selection of relevant existing trusted architecture knowledge;
3. inject that selection as a dedicated structured section in `SelectedKnowledge`;
4. evolve the architecture AI contract so the result can express:
   - `NEW`
   - `ENRICHES`
   - no-significant-delta behavior
5. reuse the current proposal / validation / promotion lifecycle for cases that still produce a
   proposal;
6. avoid introducing contradiction, supersession, or full temporal modeling in this slice.

## Recommended Architectural Direction

### Preferred model

Current Trusted Architecture Knowledge

* New Repository Evidence
* Architecture Analysis Intent
  → Analysis
  → Proposed Knowledge Delta
  → Validation
  → Trusted Knowledge Evolution

### Minimum contract expectations

The Java Core should receive structured delta intent, not infer it from free text.

Minimum conceptual outcomes:

* `NEW`
* `ENRICHES`
* no-significant-delta behavior

Likely implication:

* `NEW` / `ENRICHES` => proposal persisted and validated through existing lifecycle
* no-significant-delta => zero proposal result, or another equally explicit non-proposal
  behavior that does not trigger human validation

## Risks

1. **Trying to solve contradiction, supersession, and temporal truth immediately**
   - Too large for the first slice.

2. **Keeping generic `selectedInsights` as the only existing-knowledge channel**
   - Risks preserving the current ambiguity and prompt noise problem.

3. **Adding a complex persisted `KnowledgeDelta` model too early**
   - Not justified by the first vertical slice if the current lifecycle can be evolved.

4. **Weak Core-side validation of delta semantics**
   - The current Java validator is strong for task/result integrity but not yet for
     incremental-insight semantics.

## Recommendation

Proceed with:

* `ADR-050 — Incremental Knowledge Evolution`
* Story 0037 as the first architecture-only vertical slice

No blocking architectural contradiction was found during steps 1 to 6.

The current model already provides:

* access to trusted knowledge at analysis time;
* deterministic bounded selection infrastructure;
* structured prompt contracts;
* controlled proposal validation and promotion.

The missing piece is the explicit modeling of existing trusted architecture knowledge as
comparison context and the structured expression of incremental knowledge delta.
