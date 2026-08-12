# Story 0042 — Fix Analysis Context Grounding Closure — Repository Analysis

## Purpose

Understand why the grounding failure still occurs after Story 0041 and
determine the minimum deterministic fix that restores coherence at the
`AnalysisContext` layer without weakening validation.

## Story Understanding

Story 0041 fixed `SelectedKnowledge` closure.

It did **not** fix the source `AnalysisContext` contract.

The current bug is therefore a second upstream defect:

* selected-knowledge closure now works only with facts actually provided by the
  base context;
* if the base context already omits support facts required by its own
  observations, the selected snapshot still cannot become fully coherent.

## Live Evidence After Story 0041

Recent failed architecture reviews on the local DevLog instance prove the bug
is still real after the merged Story 0041 code:

### Failed analysis `29260ddb-7d70-4f56-9bf2-9dfe2b3442f9`

Associated AI task:

* `8ad5983f-ab53-461b-a3cd-4b0b8f22b31e`

Observed task properties:

* `failureCode = INVALID_LLM_OUTPUT`
* `failureMessage = supportingFactIds contains references absent from AnalysisContext: ['4eec97fb-57c4-411d-816a-401fd8da24b1']`
* `selectedKnowledgeSnapshot.selectionMetadata.selectionVersion = knowledge-selection-v4`

Important conclusion:

* the Story 0041 fix is active in the running service;
* the remaining failure is not caused by running stale `knowledge-selection-v3`
  code.

### Failed analysis `1db24252-d9cc-433d-b531-fd521972ffaf`

Associated AI task:

* `6eabc6e4-f4e5-4a3b-bbd9-721df563dcbb`

Observed task properties:

* `failureCode = INVALID_LLM_OUTPUT`
* `selectedKnowledgeSnapshot.selectionMetadata.selectionVersion = knowledge-selection-v4`

This confirms the issue is reproducible across more than one recent failed
analysis.

## Key Observation

For failed Story-0041-era AI tasks, the persisted `selectedKnowledgeSnapshot`
still contains many observation `supportingFactIds` absent from
`selectedFacts`.

That can happen only if one of the following is true:

1. Story 0041 code is not running.
2. The base `AnalysisContext` already lacks required facts.

The live evidence above eliminates option 1.

Therefore the remaining bug is option 2.

## Relevant Components

### `AnalysisContextServiceImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`

Current behavior:

* loads facts with:
  - `MAX_FACTS = 100`
  - `factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(...)`
* loads observations independently with:
  - `MAX_OBSERVATIONS = 50`
  - `observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(...)`
* maps each observation to its full persisted `supportingFactIds`
* does not enforce closure between:
  - the paged fact set;
  - the paged observation set;
  - the fact IDs referenced by those observations.

This is the most likely remaining bug source.

### `AnalysisContext`

File:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContext.java`

Current behavior:

* the contract shape implies that `facts` and `observations` describe one
  coherent immutable analysis snapshot;
* observations expose exact `supportingFactIds`.

Expected invariant:

* every `supportingFactId` visible in `observations` should resolve inside
  `facts`.

That invariant is currently not guaranteed by the builder service.

### `KnowledgeSelectionServiceImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`

Current behavior after Story 0041:

* enforces observation-to-fact closure **within the facts provided by the base
  context**
* reduces observations when required supporting facts would overflow the fact
  budget

Interpretation:

* this fix is still correct;
* it cannot recover support facts that never reached `AnalysisContext.facts`.

### `InsightGenerationService` and `AiProposalContractValidator`

Files:

* `ai-engine/app/services/insight_generation_service.py`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`

Current behavior:

* both remain strict and correct;
* they reject fact IDs absent from the selected snapshot / analysis context.

These validators are not the bug source.

## Root Cause

The base `AnalysisContext` can already be internally inconsistent.

Specifically:

1. facts are truncated independently to the top `MAX_FACTS` rows.
2. observations are truncated independently to the top `MAX_OBSERVATIONS`
   rows.
3. each selected observation still carries its full persisted
   `supportingFactIds`.
4. some of those fact IDs are absent from the paged fact set.
5. Story 0041 selection can keep only the facts present in the base context.
6. the AI Engine still sees observations whose support set is incomplete
   relative to the original analysis.
7. strict grounding validation rejects copied support fact IDs.

So the remaining failure is:

* a source-context closure bug caused by independent pagination.

## Why Story 0041 Did Not Fully Solve It

Story 0041 fixed:

* `SelectedKnowledge` inconsistency introduced after `AnalysisContext`
  construction.

Story 0042 must fix:

* `AnalysisContext` inconsistency introduced during context construction
  itself.

These are distinct but related layers.

## Candidate Fix Directions

### Option A — Trim observation support IDs to the paged fact set

Approach:

* keep current paging;
* rewrite `ObservationSnapshot.supportingFactIds` so only fact IDs present in
  `AnalysisContext.facts` remain.

Pros:

* very small implementation footprint;
* preserves current fact page size.

Cons:

* hides the real support set;
* weakens observation truthfulness at the source context level;
* repeats the rejected strategy from Story 0041 at an earlier layer.

Verdict:

* not preferred.

### Option B — Enforce closure in `AnalysisContextServiceImpl`

Approach:

* page or rank observations first;
* compute the support fact IDs required by those observations;
* ensure `AnalysisContext.facts` contains those required facts;
* if required support cannot fit a hard fact budget, reduce observations
  deterministically until closure fits;
* then fill remaining fact capacity with the standard paged / ranked facts.

Pros:

* keeps the base context truthful;
* aligns the base context with Story 0041 selected-snapshot behavior;
* preserves strict validation.

Cons:

* requires carefully redefining the current `MAX_FACTS` / `MAX_OBSERVATIONS`
  interaction.

Verdict:

* recommended.

## Recommended Direction

Implement closure directly in `AnalysisContextServiceImpl`.

The target invariant is:

* if an observation is present in `AnalysisContext.observations`, every
  `supportingFactId` it exposes must resolve in `AnalysisContext.facts`.

The fix should remain deterministic and bounded, and should prefer reducing
observations over emitting a self-contradictory base context.

## Risks

### Risk 1 — Context size drift

If required support facts are added without a hard bound, the context may grow
unbounded.

Control:

* keep a hard fact budget and resolve overflow by deterministically reducing
  observations.

### Risk 2 — Double policy drift between context and selection layers

If Story 0042 invents a radically different closure policy from Story 0041, the
layers may diverge.

Control:

* mirror the same truthfulness principle:
  - preserve support closure;
  - preserve bounds;
  - reduce observations when closure cannot fit.

### Risk 3 — Hiding the bug behind looser validation

Any validator relaxation would only mask the broken source context.

Control:

* keep validators unchanged.

## Gate Recommendation

Proceed to Implementation Planning only after approval of this Repository
Analysis.
