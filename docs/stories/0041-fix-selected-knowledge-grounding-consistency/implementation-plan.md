# Story 0041 — Fix Selected Knowledge Grounding Consistency — Implementation Plan

## Overview

Implement a narrow deterministic bugfix in selected-knowledge construction so
the AI Engine never receives selected observations whose `supportingFactIds`
reference facts absent from `selectedFacts`.

The fix must preserve the current strict grounding contract.

This Story should correct the upstream selected snapshot, not weaken the
downstream validators in Python or Java.

## Planned Changes

### 1. Enforce observation-to-fact closure inside knowledge selection

Update:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`

Implementation intent:

* keep the current deterministic ranking for observations and facts;
* keep the current duplicate-fact elimination rule;
* stop selecting facts independently from selected observations;
* after ranking candidate observations, derive the set of required fact IDs
  referenced by their `supportingFactIds`;
* construct `selectedFacts` so every selected observation is grounded by facts
  that are also present in the final fact selection;
* preserve stable ordering of selected facts with deterministic
  tie-breakers.

### 2. Make the final selection bounded without introducing dangling references

Update:

* `KnowledgeSelectionServiceImpl`

Implementation intent:

* keep `BUDGET.maximumFacts()` as a hard upper bound;
* if the first `maximumObservations` candidates require more supporting facts
  than the fact budget allows, reduce the selected observation set
  deterministically until its required supporting facts fit within the fact
  budget;
* once the selected observation set is closure-safe, fill remaining fact
  capacity with the existing ranked discretionary facts;
* avoid rewriting observation `supportingFactIds` to hide omitted facts;
* prefer truthful closure over silently mutating observation semantics.

Rationale:

* this preserves AC-1 and AC-2 together;
* it keeps the selected snapshot internally coherent even under tight budgets.

### 3. Keep the strict grounding validators unchanged

Deliberately do not relax:

* `ai-engine/app/services/insight_generation_service.py`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`
* `ai-engine/app/prompts/insight.py`

Implementation intent:

* preserve the rule that `supportingFactIds` must be a subset of
  `selectedFacts.id`;
* preserve the current corrective-retry behavior when the model returns IDs
  outside the selected snapshot;
* treat the selected-knowledge snapshot as the single source of truth.

### 4. Add Java regression coverage for closure and budget behavior

Update likely tests:

* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceAdditionalTest.java`

Implementation intent:

* add a regression proving a selected observation cannot reference a missing
  selected fact;
* add a budget-pressure regression proving the service reduces observations
  rather than emitting dangling references when required supporting facts would
  overflow the fact budget;
* keep coverage for deterministic ordering and duplicate-fact elimination;
* verify the final selected snapshot remains bounded.

### 5. Add AI-engine regression coverage for the impacted failure mode

Update likely tests:

* `ai-engine/tests/test_insight_generation_service.py`
* `ai-engine/tests/intent_fixtures.py` only if a fixture extension is needed

Implementation intent:

* add a regression covering the previously failing shape from the consumer
  side:
  - a selected observation references a fact ID;
  - that fact ID is present in `selectedFacts`;
  - the model returns the same fact ID;
  - validation succeeds without `INVALID_LLM_OUTPUT`;
* keep the existing negative test proving truly out-of-context fact IDs are
  still rejected.

### 6. Record the bugfix behavior and any remaining limitation

Create/update:

* `docs/stories/0041-fix-selected-knowledge-grounding-consistency/implementation-report.md`
* `docs/stories/0041-fix-selected-knowledge-grounding-consistency/code-review.md`
* `docs/stories/0041-fix-selected-knowledge-grounding-consistency/engineering-report.md`

Implementation intent:

* document the exact root cause in the selected-knowledge pipeline;
* document the chosen policy under fact-budget pressure;
* record that validators remained intentionally strict;
* note any residual limitation if a high-support observation can displace lower
  priority discretionary facts.

## Validation Plan

1. Run targeted Java knowledge-selection tests covering:
   * closure without dangling fact references;
   * budget-pressure behavior;
   * deterministic bounded selection.
2. Run targeted AI-engine tests covering:
   * successful validation when copied fact IDs are present in `selectedFacts`;
   * continued rejection of truly absent fact IDs.
3. Run the relevant broader backend quality gate after implementation.
4. Run `git diff --check` before review.

## Risks And Controls

### Risk 1: Closure fix breaks bounded selection

If required support facts are always forced in without constraint, the fact
budget may drift.

Control:

* treat fact budget as hard;
* reduce selected observations deterministically when required closure would
  overflow that budget.

### Risk 2: Closure fix changes ranking unpredictably

If the algorithm mixes closure and discretionary facts carelessly, selections
may become unstable.

Control:

* preserve existing ranking comparators;
* apply closure with explicit stable ordering and deterministic tie-breakers.

### Risk 3: Hidden semantics loss by trimming observation support lists

If the implementation simply removes missing `supportingFactIds`, the snapshot
becomes superficially valid but semantically weaker.

Control:

* reject trimming as the primary strategy;
* preserve truthful observation support by selecting a closure-safe set
  instead.

### Risk 4: Accidental validator relaxation

A quick workaround in the Python or Java validators would mask the real bug
and repeat the earlier grounding mistake.

Control:

* keep validators unchanged and prove the fix through selected-knowledge
  construction.

## Expected Deliverables

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceAdditionalTest.java`
* `ai-engine/tests/test_insight_generation_service.py`
* Story 0041 implementation artifacts:
  - `implementation-report.md`
  - `code-review.md`
  - `engineering-report.md`
