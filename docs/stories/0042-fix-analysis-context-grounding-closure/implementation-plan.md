# Story 0042 — Fix Analysis Context Grounding Closure — Implementation Plan

## Overview

Implement a deterministic bugfix in `AnalysisContextServiceImpl` so the base
`AnalysisContext` never exposes observations whose `supportingFactIds`
reference facts absent from `AnalysisContext.facts`.

The fix must preserve the current strict grounding contract and remain bounded.

This Story should correct the source analysis context itself, not weaken
selected-knowledge validation or rework Story 0041.

## Planned Changes

### 1. Enforce observation-to-fact closure inside `AnalysisContextServiceImpl`

Update:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`

Implementation intent:

* stop treating paged facts and paged observations as two independently safe
  snapshots;
* build the base context so every retained observation is backed by facts also
  retained in `AnalysisContext.facts`;
* preserve deterministic ordering for both facts and observations;
* keep observation support IDs truthful rather than trimming them to hide
  missing facts.

### 2. Keep the base context bounded with deterministic overflow behavior

Update:

* `AnalysisContextServiceImpl`

Implementation intent:

* keep `MAX_FACTS` as a hard upper bound for the base context;
* keep `MAX_OBSERVATIONS` as the initial observation candidate cap;
* load the top observation candidates first;
* derive the supporting fact IDs required by those observations;
* if required support facts exceed `MAX_FACTS`, reduce the selected
  observation set deterministically until the required support facts fit within
  the fact budget;
* once the observation set is closure-safe, fill any remaining fact capacity
  with the standard fact ordering from the analysis fact stream.

Rationale:

* this mirrors the truthfulness principle introduced in Story 0041;
* it preserves a coherent source context without making the context unbounded.

### 3. Preserve Story 0041 selected-knowledge closure and strict validators

Deliberately do not relax:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `ai-engine/app/services/insight_generation_service.py`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`

Implementation intent:

* keep Story 0041 intact as the selected-snapshot closure layer;
* let Story 0042 guarantee that the source `AnalysisContext` already respects
  the same invariant;
* preserve the rule that visible support IDs must resolve in the authorized
  fact set.

### 4. Add Java regression coverage for source-context closure

Update likely tests:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceTest.java`

Implementation intent:

* add a regression proving `AnalysisContext.observations[].supportingFactIds`
  is always contained in `AnalysisContext.facts.id`;
* add a budget-pressure regression proving the service reduces observations
  rather than emitting dangling support IDs when required facts would exceed
  `MAX_FACTS`;
* keep the existing tests for deterministic ordering and immutable snapshot
  behavior.

### 5. Add workflow-level regression coverage if required

Update likely tests:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceTest.java`
* or another focused workflow/service test only if the existing context-service
  regressions are insufficient

Implementation intent:

* prove the repaired base context is compatible with Story 0041 selected
  knowledge and downstream AI task creation;
* avoid broad end-to-end test churn unless a narrow regression is required to
  protect the behavior boundary.

### 6. Record the layered root-cause distinction

Create/update:

* `docs/stories/0042-fix-analysis-context-grounding-closure/implementation-report.md`
* `docs/stories/0042-fix-analysis-context-grounding-closure/code-review.md`
* `docs/stories/0042-fix-analysis-context-grounding-closure/engineering-report.md`

Implementation intent:

* document that Story 0041 fixed selected-snapshot closure;
* document that Story 0042 fixes source-context closure;
* record the chosen bounded overflow policy and any remaining limitations;
* document the live evidence that `knowledge-selection-v4` alone was
  insufficient.

## Validation Plan

1. Run targeted Java tests for:
   * source-context closure;
   * fact-budget overflow behavior;
   * deterministic bounded snapshot construction.
2. Run any additional targeted workflow regression only if needed.
3. Run the broader backend quality gate after implementation.
4. Reproduce the failing local refresh path on the rebuilt service if the
   environment allows it.
5. Run `git diff --check` before review.

## Risks And Controls

### Risk 1: Context closure and selected-knowledge closure diverge

If Story 0042 uses a different overflow policy from Story 0041, the two layers
may produce surprising behavior.

Control:

* apply the same principle at both layers:
  - preserve support closure;
  - preserve bounds;
  - reduce observations when closure does not fit.

### Risk 2: Required support facts inflate the source context unexpectedly

If support facts are always appended without control, the base context may
become larger than intended.

Control:

* keep `MAX_FACTS` hard;
* reduce selected observations deterministically when closure would overflow it.

### Risk 3: The service hides inconsistencies by trimming support IDs

If the implementation rewrites `supportingFactIds`, the context may become
superficially valid while losing semantic truth.

Control:

* reject trimming as the primary strategy;
* preserve truthful support by selecting a closure-safe base context.

### Risk 4: The real bug lies deeper in persistence rather than paging

If persisted observation-to-fact relations themselves are corrupt, changing
context paging alone will not fully solve the issue.

Control:

* add regression tests around current repository assumptions;
* verify the live failing examples against the repaired service;
* stop and reassess if the repaired bounded context still produces dangling
  support IDs.

## Expected Deliverables

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceTest.java`
* optional narrow workflow regression if required
* Story 0042 implementation artifacts:
  - `implementation-report.md`
  - `code-review.md`
  - `engineering-report.md`
