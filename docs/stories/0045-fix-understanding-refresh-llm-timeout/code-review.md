# Story 0045 — Fix Understanding Refresh LLM Timeout — Code Review

## Status

Reviewed

## Review Scope

Review of the understanding-refresh timeout bugfix:

* compact prompt projection for `SelectedKnowledge`
* backend submission and persistence path updates
* regression coverage for prompt contract changes
* targeted tests, full backend quality gate, and live end-to-end refresh retest

## Findings

No blocking findings.

### 1. The fix attacks the actual bottleneck instead of masking it ✅

The repository analysis showed the main issue was not:

* Core-to-AI transport;
* broken health on the AI engine;
* or grounding validation.

The dominant issue was oversized AI-facing selected knowledge, especially the
verbose `repositoryContext`.

The implementation fixes that directly by compacting the prompt-facing payload
instead of:

* raising provider timeout first;
* weakening validation;
* or changing repository-context internals globally.

That is the right first move.

### 2. The new projection keeps grounding-relevant evidence while removing backend-only noise ✅

The dedicated prompt projection preserves:

* top-level selected facts and observations;
* repository evidence references;
* compact content and symbol context;
* context digest and warnings.

It removes:

* ranking explanations;
* provenance internals;
* extraction metadata;
* selection decisions;
* token-accounting diagnostics.

That boundary is clean:

* the LLM still sees the evidence it needs;
* the model no longer pays for backend-only bookkeeping.

### 3. Using the same compact representation for prompt submission and persisted task snapshots is a good decision ✅

`AiTaskServiceImpl` now stores the same compact payload shape that is actually
submitted to the AI engine.

This is important because it improves diagnostics:

* past snapshots could exaggerate prompt size relative to the real wire
  payload;
* future investigations now inspect the effective model-facing contract.

That reduces ambiguity during production debugging.

### 4. Contract adaptation is well covered by tests ✅

The fix required touching multiple seams:

* `PromptRequest`
* workflow submission
* AI task snapshot persistence
* HTTP client tests
* workflow tests

The added projection test plus the adapted integration tests give good
confidence that the compact contract is stable and that null-bearing maps no
longer break `PromptRequest`.

### 5. Live retest confirms the timeout regression is actually resolved on the local stack ✅

The most important verification is not just unit or integration coverage.

A real understanding refresh was executed after rebuilding the local stack:

* analysis `070a35f8-b38a-4de9-beaa-1e811a2b832a`
* task `e2777b58-6f8b-4dfa-b483-fb757e7ccfa6`
* final status `COMPLETED`
* OpenAI call returned `200 OK`
* AI-engine logged `userMessageSize=92484`
* persisted `selected_knowledge_snapshot` size was `67729`

This directly addresses the bug we were trying to fix.

### 6. Residual risk remains on prompt growth from other sections ⚠️

The Story correctly targets `repositoryContext`, which was the dominant source
of bloat.

However, prompt growth can still come from other sections over time:

* `selectedFacts`
* `selectedObservations`
* `projectProfile`
* `validatedProposals`

This is not a blocker for Story 0045 because the local live retest is now
green, but it is the next place to watch if prompt size drifts upward again.

## Gate Results

* `./mvnw -Dtest=RestAIEngineClientTest,RestAIEngineClientIntegrationTest,AiTaskServiceTest,ProjectUnderstandingServiceTest,SelectedKnowledgePromptProjectionServiceTest test`: **PASS**
* backend `./mvnw verify`: **PASS**
* JaCoCo coverage checks: **PASS**
* `git diff --check`: **PASS**
* live understanding refresh retest on rebuilt local stack: **PASS**

## Conclusion

Approve.

The Story fixes the timeout at the correct architectural layer, preserves
grounding strictness, materially reduces prompt size, and is validated both by
the full backend quality gate and by a successful live understanding refresh.
