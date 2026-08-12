# Story 0041 — Fix Selected Knowledge Grounding Consistency — Engineering Report

## Status

Reported

## Story

### Number

0041

### Title

Fix Selected Knowledge Grounding Consistency

### Status

Implemented

### Acceptance Criteria

Met.

## Problem

Project-understanding / insight-generation refreshes could fail with:

`INVALID_LLM_OUTPUT: supportingFactIds contains references absent from AnalysisContext`

The failure was deterministic.

The selected snapshot sent to the AI Engine could contain:

* a selected observation exposing `supportingFactIds`;
* a selected fact set built independently under its own budget;
* a visible fact UUID inside the observation payload that was absent from
  `selectedFacts`.

The model could then legitimately copy that UUID, and strict validation would
reject it.

## Previous Behavior

`KnowledgeSelectionServiceImpl` selected:

* observations via one ranking/budget path;
* facts via another ranking/budget path.

That meant the selected snapshot could become internally inconsistent even
though:

* `AnalysisContextServiceImpl` had built coherent observation support data;
* Python validation was correct;
* Java validation was correct.

So repeated failures were caused by a selected-knowledge contract mismatch, not
by a weak prompt or an overly strict validator.

## Scope Delivered

Implemented:

* deterministic observation-to-fact closure in selected knowledge
* hard bounded fact selection preserved
* deterministic observation reduction under fact-budget pressure
* backend regressions for closure and bounded behavior
* AI-engine regression for the copied visible fact-ID path

Deferred:

* grounding-contract redesign
* validator relaxation
* broad prompt changes
* unrelated ranking-policy changes

## Design Outcome

### Boundary retained

`KnowledgeSelectionServiceImpl`

* now owns closure-safe selected snapshot construction

`InsightGenerationService` and `AiProposalContractValidator`

* unchanged as strict validators

### Why this matters

This preserves the intended architecture:

* deterministic Java selection must provide a coherent snapshot;
* AI may only use what that snapshot authorizes;
* validators remain the enforcement boundary, not the repair mechanism.

## Implementation Summary

### Updated

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceAdditionalTest.java`
* `ai-engine/tests/test_insight_generation_service.py`

### Key behavior

* selection version advanced to `knowledge-selection-v4`
* selected observations now imply required supporting fact retention
* required support facts are preserved by identity
* discretionary facts still use duplicate-content elimination
* when required closure would overflow fact budget, lower-priority
  observations are dropped until the snapshot fits

## Behavioral Outcome

### Now prevented

* selected observations referencing facts absent from `selectedFacts`
* deterministic invalid-output failures caused by DevLog exposing a fact ID in
  selected observations but not authorizing it in `selectedFacts`

### Preserved

* strict subset validation for `supportingFactIds`
* deterministic ranking and tie-breaking
* bounded fact budget
* discretionary duplicate-fact elimination

## Quality Gates

* `./mvnw -Dtest=KnowledgeSelectionServiceTest,KnowledgeSelectionServiceAdditionalTest test`: **PASS**
* `./.venv/bin/python -m pytest tests/test_insight_generation_service.py -q`: **PASS**
* backend `./mvnw verify`: **PASS**
* JaCoCo coverage check: **PASS**
* `git diff --check`: **PASS**

## Documentation Outcome

Story artifacts added:

* `story.md`
* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`

No additional canonical ADR or repository-wide documentation update was
required for this bounded bugfix.

## Vault Outcome

* curated vault context materially informed the work: no
* vault action: none
* outcome remained proposal-only: not applicable

## Limitations

1. The fix assumes the base `AnalysisContext` remains coherent, which matched
   Repository Analysis for this bug.
2. Under tight fact budgets, some lower-priority observations may now be
   dropped to preserve truthful grounding closure.
3. This Story fixes the selected snapshot contract mismatch only; it does not
   redesign the broader grounding model.

## Next Architectural Questions

1. Should `AnalysisContextServiceImpl` eventually expose an explicit invariant
   check for observation support coherence before selection?
2. Should selected-knowledge diagnostics eventually surface how many
   observations were dropped due to fact-budget closure pressure?
3. Should future selection policies consider support-cost-aware ranking rather
   than applying closure after observation ranking?
