# Story 0045 — Fix Understanding Refresh LLM Timeout — Engineering Report

## Status

Completed

## Story Recap

Story 0045 fixed the understanding-refresh timeout that remained after the
grounding-coherence bugfixes from Stories 0041 and 0042.

At that point, refreshes no longer failed because of invalid grounding
references, but they still failed because the AI-facing prompt payload was too
large for the provider timeout budget.

## Problem

Observed runtime failure:

* `LLM_PROVIDER_ERROR`
* `Request timed out.`

The key signal from live failed tasks was that the issue was now provider-side,
not transport-side:

* backend submission to the AI engine succeeded quickly;
* the AI engine accepted the task with `202`;
* the failure arrived later from the LLM provider path.

## Root Cause

The dominant size contributor in `selectedKnowledge` was the AI-facing
`repositoryContext` payload.

It still carried large volumes of backend-only metadata such as:

* selection decisions
* ranking explanations
* provenance details
* extraction metadata
* diagnostics and token-accounting fields

Those fields were useful for backend selection and debugging, but they were not
required by the model to generate grounded proposals.

## Implemented Fix

Added:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/PromptRequest.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/task/service/AiTaskServiceImpl.java`

Key behavior:

* build a dedicated compact map for prompt submission;
* keep grounding-relevant repository evidence fields;
* remove backend-only ranking/allocation/debug metadata from the AI-facing
  `repositoryContext`;
* persist the same compact representation in `ai_tasks.selected_knowledge_snapshot`;
* preserve strict validation unchanged in Python and Java.

## Tests And Verification

Passed:

* `./mvnw -Dtest=RestAIEngineClientTest,RestAIEngineClientIntegrationTest,AiTaskServiceTest,ProjectUnderstandingServiceTest,SelectedKnowledgePromptProjectionServiceTest test`
* `./mvnw verify`
* JaCoCo coverage checks
* `git diff --check`

Covered scenarios:

* prompt submission now uses the compact contract;
* persisted AI task snapshots now match the model-facing payload shape;
* workflow and HTTP client seams remain aligned after the contract change;
* heavy repository-context metadata is excluded from the prompt projection.

## Measured Impact

Measured on a real failed task snapshot:

* original serialized payload: `216067` characters
* compacted serialized payload: `86059` characters
* reduction: `130008` characters
* reduction ratio: `60.2%`

Measured on a fresh live task after the fix:

* persisted `selected_knowledge_snapshot`: `67729` characters

## Live Runtime Outcome

After rebuilding and restarting the local stack, a real understanding refresh
was executed successfully:

* analysis: `070a35f8-b38a-4de9-beaa-1e811a2b832a`
* ai task: `e2777b58-6f8b-4dfa-b483-fb757e7ccfa6`
* final analysis status: `COMPLETED`
* final ai task status: `COMPLETED`
* provider call: `HTTP 200 OK`
* AI engine log: `userMessageSize=92484`

This confirms that Story 0045 resolves the refresh-timeout symptom on the
local running stack, not just in repository tests.

## Architectural Outcome

The refresh path now has three complementary protections:

* Story 0042 guarantees grounding closure in the base `AnalysisContext`;
* Story 0041 guarantees grounding closure in the derived `SelectedKnowledge`;
* Story 0045 keeps the AI-facing payload bounded enough to complete within the
  provider timeout budget.

The model contract remains strict throughout.

## Quality Gates

* Backend targeted tests: **PASS**
* Backend full verify: **PASS**
* JaCoCo: **PASS**
* Diff formatting: **PASS**
* Live end-to-end refresh retest: **PASS**

## Limitations

Prompt growth can still come from other sections over time, especially:

* `selectedFacts`
* `selectedObservations`
* `projectProfile`
* `validatedProposals`

That is not a blocker for Story 0045 because the local live retest is green,
but it remains the next pressure point to watch if prompt size drifts upward
again.

## Final Outcome

Completed.

The codebase now sends a significantly smaller and more purposeful
AI-facing `selectedKnowledge` payload, and the understanding refresh path is
again usable end-to-end on the local stack.
