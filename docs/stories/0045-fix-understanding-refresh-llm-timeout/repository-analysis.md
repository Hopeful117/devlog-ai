# Story 0045 — Fix Understanding Refresh LLM Timeout — Repository Analysis

## Status

Completed

## Scope Of This Analysis

This Repository Analysis focuses on the current understanding-refresh failures
that now end with:

* `LLM_PROVIDER_ERROR`
* `Request timed out.`

The goal of this analysis is to determine whether the remaining failure is:

* a transport timeout between Core and AI Engine,
* a callback timing problem,
* an OpenAI provider timeout,
* or a prompt/payload-size issue that makes the current timeout budget
  unrealistic.

## Story Context

Recent Stories changed the refresh path in a meaningful sequence:

* Story 0041 fixed `SelectedKnowledge` grounding closure;
* Story 0042 fixed source `AnalysisContext` grounding closure;
* Story 0043 strengthened refresh-path seam coverage;
* Story 0044 fixed diagnostics `/context` null-safety.

The latest runtime failures therefore represent a new stage of progress:

* the refresh path is getting further than before;
* grounding is no longer the primary failure in the latest attempts;
* the current dominant blocker is timeout at the AI-provider stage.

## DevLog Context Outcome

DevLog lifecycle registration succeeded for this Story:

* DevLog story id: `73dbdcb3-b44b-445a-8d6b-f94c659161d7`

DevLog engineering-story context retrieval also succeeded and was consulted.

Useful signals from that context:

* current repository revision resolved to `2c2a628dc553cbbba8501df7d3770108c6a733f1`
* recent relevant commits include Stories 0041, 0042, 0043, and 0035
* the compacted agent projection already applies heavy trimming rules to stay
  within `32768` bytes / `8192` estimated tokens

This is important because it highlights a contrast:

* the engineering-story context path already has a dedicated AI-facing
  projection;
* the understanding-refresh path still appears to send a much heavier
  AI-facing structure.

## Runtime Evidence Collected

### 1. The latest failures are now pure provider timeouts

Recent `ai_tasks` rows show the progression clearly:

* older failures: `INVALID_LLM_OUTPUT`
* latest failures: `LLM_PROVIDER_ERROR` with `Request timed out.`

Observed failed AI tasks:

* `56d86477-5ea3-4fed-8f16-6e9998c1cfc5`
* `52776808-fa8c-4285-8aca-579142c676c2`

Both failed with:

* `failure_code = LLM_PROVIDER_ERROR`
* `failure_message = Request timed out.`

This confirms that Stories 0041 and 0042 improved grounding consistency enough
to expose the next bottleneck.

### 2. The timeout is not on Core → AI Engine submission

The refresh orchestration still submits to the AI Engine through:

* `AnalysisWorkflowServiceImpl`
* `RestAIEngineClient`

Important behavior:

* Core submits `POST /api/v1/ai/tasks`
* the AI Engine returns `202 Accepted`
* actual LLM processing continues in a FastAPI background task

Evidence from runtime logs:

* task acceptance durations are around `6–7 ms`
* therefore the backend `ai-engine.read-timeout=45s` is not the failing
  budget for this path

Conclusion:

* Core → AI Engine submission is healthy
* the failure happens after acceptance, during provider processing

### 3. The timeout aligns exactly with the AI Engine provider budget

Runtime configuration:

* `.env`: `LLM_TIMEOUT_SECONDS=30`
* `ai-engine/app/core/config.py`: default `llm_timeout_seconds = 30.0`
* OpenAI provider client uses that timeout directly

Observed timing:

* accepted at `21:14:41.638`
* failure callback sent at `21:15:11.758`

That is essentially a `30s` provider-side timeout.

Conclusion:

* the current failure is consistent with the AI Engine’s own provider timeout
  budget
* this is not a random stall or callback retry issue

### 4. The AI-facing selected knowledge is extremely large

For the two latest timeout tasks:

* `selected_knowledge_snapshot` size is about `202k` characters
* `context_snapshot` size is about `70k` characters

More importantly, the dominant contributor inside `SelectedKnowledge` is:

* `repositoryContext` ≈ `178k` characters

Other sections are much smaller:

* `projectProfile` ≈ `4.1k`
* `selectedFacts` ≈ `14k`
* `selectedInsights` ≈ `3.5k`

Conclusion:

* the timeout is strongly correlated with prompt/payload bloat
* the main suspect is not the facts themselves, but the serialized
  `repositoryContext`

### 5. The `repositoryContext` budget is not an AI-facing prompt budget

The current repository context engine has:

* `devlog.repository-context.max-tokens = 6000`
* `maximumEvidenceItems = 60`

However, the persisted timeout snapshots show that the resulting
`repositoryContext` still serializes to about `178k` characters.

That means the current budget controls:

* selection of repository evidence

but does **not** sufficiently control:

* the final serialized shape injected into the LLM prompt

The repository context currently carries rich internal metadata such as:

* evidence score breakdowns
* ranking reasons
* provenance
* extraction metadata
* diagnostics
* selection decisions

One particularly expensive section:

* `selectionDecisions` alone is about `54k` characters

Conclusion:

* we have a budgeted selection system,
* but we do **not** yet have a compact AI-facing projection for this context

### 6. The latest timeout cases do not need all this metadata to answer the intent

Latest timeout tasks had approximately:

* `40` selected facts
* `0` selected observations
* `10` selected insights
* `60` repository evidence items

For an `architecture-overview`/project-understanding style intent, the model
likely needs:

* compact evidence references,
* short summaries,
* selected fact content,
* trusted architecture deltas when relevant,
* and possibly trimmed content/symbol payloads

It almost certainly does **not** need the full internal ranking and accounting
machinery of repository-context selection.

## Code Paths Reviewed

### Core workflow

* `backend/src/main/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingService.java`
* `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/client/RestAIEngineClient.java`

### AI Engine runtime

* `ai-engine/app/api/ai_tasks.py`
* `ai-engine/app/services/task_processing_service.py`
* `ai-engine/app/services/insight_generation_service.py`
* `ai-engine/app/providers/openai.py`
* `ai-engine/app/core/config.py`

### SelectedKnowledge / RepositoryContext construction

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContext.java`
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidence.java`

## Root Cause Hypothesis

The strongest current hypothesis is:

1. understanding refresh now reaches provider execution successfully;
2. the AI Engine sends an excessively large prompt because `SelectedKnowledge`
   embeds a very heavy `repositoryContext` object;
3. that object includes internal ranking/diagnostic/accounting details not
   needed by the LLM;
4. the OpenAI provider times out at the configured `30s` budget before
   returning structured output.

This hypothesis is well-supported by:

* the exact `30s` runtime pattern,
* the very fast `202 Accepted` submission,
* the successful callback path,
* and the observed `~202k` selected-knowledge payload dominated by
  `repositoryContext`.

## Architectural Direction

The most credible minimal fix is **not**:

* blindly increasing provider timeout,
* weakening validation,
* or reducing evidence quality arbitrarily.

The best first direction appears to be:

* introduce a compact AI-facing projection of `repositoryContext` (or of the
  entire `SelectedKnowledge` repository-context section) before prompt
  construction

That projection should keep:

* evidence summaries,
* references,
* compact content/symbol snippets when selected,
* any context strictly needed for grounding

It should exclude or aggressively trim:

* score breakdown internals,
* selection decisions,
* rich ranking explanations,
* internal diagnostics/accounting,
* verbose provenance/extraction metadata not needed by the model

## Risks

### Risk 1 — Timeout-only fix by configuration

Increasing `LLM_TIMEOUT_SECONDS` alone could make the current refresh appear
less broken locally, but it would leave:

* oversized prompts,
* poor cost/latency characteristics,
* and weak observability on payload growth

This would be a masking fix, not a good deterministic repair.

### Risk 2 — Over-trimming grounding data

If we compact too aggressively, we may regress:

* factual grounding,
* evidence references,
* or architecture-delta quality

So the projection must be explicit and test-driven rather than ad hoc.

### Risk 3 — Missing observability

Current successful logs report `userMessageSize`, but timeout scenarios do not
yet surface equivalent structured prompt-size diagnostics before failure.

That makes operational debugging harder than necessary.

## Testing Gap Exposed

The current suite appears to cover:

* correctness of grounding,
* selection consistency,
* and refresh workflow seams

But it does not yet protect:

* AI-facing payload size regressions,
* compactness of repository-context projection,
* or timeout-susceptible prompt growth in the refresh path

This is likely why the refresh could become operationally unusable even while
many repository tests remained green.

## Recommendation For Implementation Planning

The Implementation Plan should evaluate a narrow, deterministic fix with this
priority order:

1. compact the AI-facing repository context projection used in refresh prompts
2. add payload-size observability/regression checks around prompt construction
3. only consider timeout-budget changes if compaction alone is insufficient

The plan should avoid broad redesign of:

* prompt architecture,
* provider abstraction,
* or repository-context ranking itself

## Repository Analysis Verdict

Ready for planning.

The timeout issue is now well enough localized to proceed with an
Implementation Plan. The most likely root cause is AI-facing prompt bloat,
driven primarily by the serialized `repositoryContext`, rather than transport
instability or callback failure.
