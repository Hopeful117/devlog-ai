# Story 0041 — Fix Selected Knowledge Grounding Consistency — Implementation Report

## Status

Implemented

## Summary

Implemented a deterministic selected-knowledge bugfix for the insight-generation
/ project-understanding refresh path.

The selected snapshot can no longer expose observations whose
`supportingFactIds` reference facts absent from `selectedFacts`.

The chosen fix preserves the existing strict grounding contract:

* validators in Python and Java remain unchanged;
* the selected snapshot is repaired upstream in Java;
* fact budget remains bounded;
* budget pressure is resolved by reducing selected observations
  deterministically rather than emitting dangling fact references.

## Changes

### 1. Enforced observation-to-fact closure in knowledge selection

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`

Changes:

* `KnowledgeSelectionServiceImpl.VERSION` now advances to
  `knowledge-selection-v4`;
* observation ranking and fact ranking remain deterministic;
* selected observations and selected facts are no longer finalized
  independently;
* the service now:
  - ranks observations;
  - computes the supporting fact IDs required by the selected observations;
  - ensures those required facts are retained in `selectedFacts`;
  - fills any remaining fact budget with discretionary ranked facts.

Outcome:

* a selected observation cannot point to a fact ID omitted from
  `selectedFacts`;
* the AI Engine no longer receives a self-contradictory selected snapshot for
  this failure mode.

### 2. Preserved bounded selection under fact-budget pressure

Updated:

* `KnowledgeSelectionServiceImpl`

Changes:

* `BUDGET.maximumFacts()` remains a hard upper bound;
* when the top-ranked selected observations would require more supporting
  facts than the fact budget allows, the service removes the lowest-priority
  selected observations until closure fits within the fact budget;
* required support facts are kept as real facts, not rewritten observation
  metadata.

Outcome:

* the fix preserves AC-1 and AC-2 together;
* the service prefers a smaller truthful snapshot over a larger inconsistent
  one.

### 3. Kept grounding validation strict

Deliberately unchanged:

* `ai-engine/app/services/insight_generation_service.py`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`
* `ai-engine/app/prompts/insight.py`

Decision:

* `supportingFactIds` must still be a subset of `selectedFacts.id`;
* no validator or prompt-side workaround was introduced;
* the bugfix remains aligned with the earlier grounding lesson that strict
  validation was correct and the selected deterministic context was wrong.

### 4. Added backend regressions for closure and bounded behavior

Updated:

* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceAdditionalTest.java`

Covered scenarios:

* a required low-ranked supporting fact is still retained when referenced by a
  selected observation;
* every selected observation’s `supportingFactIds` is fully contained in the
  final `selectedFacts`;
* under fact-budget pressure, the service reduces selected observations rather
  than producing dangling references;
* selection metadata now records the new rule:
  `OBSERVATION_FACT_CLOSURE`.

### 5. Added AI-engine regression coverage for the visible-ID path

Updated:

* `ai-engine/tests/test_insight_generation_service.py`

Covered scenario:

* when a selected observation visibly carries `supportingFactIds` and the same
  fact is present in `selectedFacts`, the AI Engine accepts a proposal that
  copies that fact ID;
* the existing negative test for truly absent fact IDs remains unchanged and
  continues to protect the strict contract.

## Behavioral Outcome

### Now prevented

* selected observations exposing support fact IDs that are missing from
  `selectedFacts`
* deterministic `INVALID_LLM_OUTPUT` failures caused by the model copying a
  fact ID that DevLog itself exposed in selected observations but did not
  authorize in `selectedFacts`

### Preserved

* strict grounding validation
* deterministic ranking order
* duplicate-fact elimination for discretionary fact selection
* hard fact budget

### Explicitly deferred

* any redesign of the grounding contract
* validator relaxation
* broad prompt changes
* unrelated repository-context ranking changes

## Documentation Outcome

Documentation update: Required.

Updated or added:

* `docs/stories/0041-fix-selected-knowledge-grounding-consistency/story.md`
* `docs/stories/0041-fix-selected-knowledge-grounding-consistency/repository-analysis.md`
* `docs/stories/0041-fix-selected-knowledge-grounding-consistency/implementation-plan.md`
* `docs/stories/0041-fix-selected-knowledge-grounding-consistency/implementation-report.md`

Reason:

* the Story required explicit documentation of the root cause, chosen fix
  policy, and verification evidence.

## Vault Outcome

* Vault consulted during Repository Analysis: No
* Outcome: no vault action
* Rationale: this Story is a repository-local bugfix and does not introduce a
  new transverse engineering pattern by itself.

## Validation

Performed:

* targeted backend tests for selected-knowledge closure and budget behavior
* targeted AI-engine pytest for the copied-supporting-fact path
* full backend `./mvnw verify`
* repository diff formatting check

Results:

* `./mvnw -Dtest=KnowledgeSelectionServiceTest,KnowledgeSelectionServiceAdditionalTest test`: pass
* `./.venv/bin/python -m pytest tests/test_insight_generation_service.py -q`: pass
* `./mvnw verify`: pass
* JaCoCo coverage checks: pass
* `git diff --check`: pass

Initial local issue encountered:

* `pytest ai-engine/tests/test_insight_generation_service.py -q` failed under
  the global Python environment because `openai` was not installed there
* resolved by using the repository-local virtual environment already present in
  `ai-engine/.venv`

