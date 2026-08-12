# Story 0042 — Fix Analysis Context Grounding Closure — Implementation Report

## Status

Implemented

## Summary

Implemented a deterministic source-context bugfix for architecture-review /
project-understanding refreshes that were still failing after Story 0041.

The base `AnalysisContext` can no longer expose observations whose
`supportingFactIds` reference facts absent from `AnalysisContext.facts`.

The chosen fix preserves the current strict grounding contract:

* validators in Python and Java remain unchanged;
* Story 0041 selected-knowledge closure remains unchanged;
* the source `AnalysisContext` is now repaired earlier in the pipeline;
* fact budget remains bounded;
* budget pressure is resolved by reducing observations deterministically rather
  than emitting dangling source-context references.

## Changes

### 1. Enforced observation-to-fact closure in `AnalysisContextServiceImpl`

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`

Changes:

* observations are still loaded from the paged `MAX_OBSERVATIONS` candidate
  set;
* the service now computes the support fact IDs required by the retained
  observations;
* required support facts are loaded into the base context even when they are
  absent from the initial top `MAX_FACTS` page;
* the final `AnalysisContext.facts` list is assembled from:
  - required support facts first;
  - then remaining ranked facts while capacity remains.

Outcome:

* `AnalysisContext.observations[].supportingFactIds` is now closed over
  `AnalysisContext.facts.id`.

### 2. Preserved a hard fact budget with deterministic overflow behavior

Updated:

* `AnalysisContextServiceImpl`

Changes:

* `MAX_FACTS` remains a hard upper bound;
* when the initial observation candidate set would require more than
  `MAX_FACTS` supporting facts, the service removes the lowest-priority
  retained observations until the required fact closure fits within the fact
  budget;
* observation support metadata is not trimmed or rewritten.

Outcome:

* the base context remains bounded and truthful;
* the service prefers a smaller coherent context over a larger contradictory
  one.

### 3. Preserved Story 0041 and strict validation unchanged

Deliberately unchanged:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `ai-engine/app/services/insight_generation_service.py`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`

Decision:

* Story 0041 remains the selected-snapshot closure layer;
* Story 0042 fixes the base-context closure layer beneath it;
* strict validators continue to enforce the same contract without relaxation.

### 4. Added regression coverage for source-context closure

Updated:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceTest.java`

Covered scenarios:

* a required support fact absent from the initial ranked fact page is still
  retained in the final `AnalysisContext.facts`;
* every observation support fact ID resolves in the final context fact set;
* under fact-budget pressure, the service reduces retained observations rather
  than exposing dangling support fact IDs;
* existing bounded and immutable snapshot behavior remains preserved.

## Behavioral Outcome

### Now prevented

* source `AnalysisContext` snapshots where observations reference facts absent
  from `AnalysisContext.facts`
* downstream selected-knowledge incoherence caused by independently paged
  source context inputs

### Preserved

* strict grounding validation
* Story 0041 selected-snapshot closure
* deterministic ordering
* hard fact budget at the source-context layer

### Explicitly deferred

* any redesign of the grounding contract
* validator relaxation
* broad prompt changes
* unrelated repository-context changes

## Layered Root Cause Outcome

### Story 0041 fixed

* selected-snapshot closure after `AnalysisContext` construction

### Story 0042 fixes

* source-context closure during `AnalysisContext` construction itself

This distinction is important because live failed analyses after Story 0041
already showed:

* `selectionVersion = knowledge-selection-v4`
* but still `INVALID_LLM_OUTPUT` failures

That live evidence proved Story 0041 was active but insufficient until the base
context was also made coherent.

## Documentation Outcome

Documentation update: Required.

Updated or added:

* `docs/stories/0042-fix-analysis-context-grounding-closure/story.md`
* `docs/stories/0042-fix-analysis-context-grounding-closure/repository-analysis.md`
* `docs/stories/0042-fix-analysis-context-grounding-closure/implementation-plan.md`
* `docs/stories/0042-fix-analysis-context-grounding-closure/implementation-report.md`

Reason:

* the Story required explicit documentation of the remaining root cause after
  Story 0041 and the fix layer chosen for Story 0042.

## Vault Outcome

* Vault consulted during Repository Analysis: No
* Outcome: no vault action
* Rationale: this Story is a repository-local bugfix and does not add a new
  cross-project engineering pattern by itself.

## Validation

Performed:

* targeted backend tests for source-context closure and budget behavior
* full backend `./mvnw verify`
* repository diff formatting check

Results:

* `./mvnw -Dtest=AnalysisContextServiceTest test`: pass
* `./mvnw verify`: pass
* JaCoCo coverage checks: pass
* `git diff --check`: pass

Not yet completed in this implementation stage:

* live end-to-end refresh retest against the running local backend process

Reason:

* the repository build is green, but the currently running local service was
  not hot-restarted as part of this implementation step.
