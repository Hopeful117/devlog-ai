# Story 0042 — Fix Analysis Context Grounding Closure — Engineering Report

## Status

Completed

## Story Recap

Story 0042 fixed the remaining grounding inconsistency that still caused
architecture-review / project-understanding refreshes to fail after Story
0041.

The bug was not in selected-knowledge closure anymore. It was in the base
`AnalysisContext`, where observations and facts were paged independently and
could therefore expose dangling `supportingFactIds`.

## Problem

Observed runtime failure:

* `INVALID_LLM_OUTPUT: supportingFactIds contains references absent from AnalysisContext`

Live failed analyses after Story 0041 already ran with:

* `selectionVersion = knowledge-selection-v4`

This proved Story 0041 was active but insufficient.

## Root Cause

`AnalysisContextServiceImpl` loaded:

* the top `MAX_FACTS` facts;
* the top `MAX_OBSERVATIONS` observations;

independently.

Because observations preserved their full persisted support metadata, the base
context could already be internally inconsistent before selected-knowledge
ranking began.

## Implemented Fix

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`

Key behavior:

* compute the support-fact closure required by retained observations;
* ensure those facts are loaded into `AnalysisContext.facts`;
* keep a hard fact budget;
* if closure would overflow the budget, reduce observations deterministically;
* preserve strict validators unchanged.

## Tests And Verification

Passed:

* `./mvnw -Dtest=AnalysisContextServiceTest test`
* `./mvnw verify`
* JaCoCo coverage checks
* `git diff --check`

Covered scenarios:

* required support fact outside the initial ranked fact page is retained;
* all visible observation support fact IDs resolve in the final context fact
  set;
* budget pressure reduces observations instead of emitting dangling
  references.

## Architectural Outcome

Story 0041 and Story 0042 now form a layered contract:

* Story 0042 guarantees closure in the base `AnalysisContext`;
* Story 0041 guarantees closure in the derived `SelectedKnowledge` snapshot.

Strict grounding validation remains unchanged in both Python and Java.

## Quality Gates

* Backend targeted tests: **PASS**
* Backend full verify: **PASS**
* JaCoCo: **PASS**
* Diff formatting: **PASS**

## Limitations

The remaining practical check is a live retest against the rebuilt running
local service for the previously failing refresh path.

The repository fix itself is complete and validated, but the runtime symptom
must still be confirmed against the restarted application process.

## Final Outcome

Completed in repository scope.

The codebase now guarantees that `AnalysisContext` cannot expose observation
support references absent from `AnalysisContext.facts`, which removes the
remaining source-context cause of the grounding failure addressed by Story
0042.
