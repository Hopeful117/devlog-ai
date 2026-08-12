# Story 0045 — Fix Understanding Refresh LLM Timeout — Implementation Report

## Status

Implemented

## Summary

Implemented a deterministic prompt-compaction fix for understanding-refresh
timeouts that were still occurring after grounding-coherence bugfixes.

The chosen fix does not relax any validation rule and does not increase the
LLM timeout as a first reaction.

Instead, the AI-facing `selectedKnowledge` payload now uses a dedicated compact
projection of `repositoryContext`, removing large ranking and allocation
metadata that was useful for backend selection but not required by the LLM.

## Changes

### 1. Added a dedicated AI-facing prompt projection

Added:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`

Purpose:

* project `SelectedKnowledge` into a compact map specifically for prompt
  submission and persisted AI-task snapshots;
* preserve the top-level sections required by the AI engine contract;
* keep `repositoryContext` grounded but significantly smaller.

Retained in compact `repositoryContext`:

* `contextVersion`
* `profile`
* `evidence`
* `warnings`
* `contextDigest`

Retained per evidence item:

* `layer`
* `kind`
* `reference`
* `summary`
* `occurredAt`
* `relatedReferences`
* `content`
* `symbols`

Removed from AI-facing projection:

* selection/ranking internals
* provenance metadata
* extraction metadata
* diagnostics
* token counters and candidate counters
* `selectionDecisions`
* `selectedByLayer`

Outcome:

* the LLM still receives grounded repository evidence;
* prompt payload size is materially reduced.

### 2. Switched AI-engine submission to the compact projection

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/PromptRequest.java`

Changes:

* `AnalysisWorkflowServiceImpl` now submits the compact projected map instead
  of the full `SelectedKnowledge` object;
* `PromptRequest.selectedKnowledge` now carries a `Map<String, Object>` rather
  than the backend domain record directly;
* the immutable prompt payload now tolerates nullable nested values instead of
  failing on `Map.copyOf(...)`.

Outcome:

* the AI engine receives the compact contract directly;
* prompt serialization no longer depends on the full backend domain shape.

### 3. Switched persisted AI-task snapshots to the same compact contract

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/ai/task/service/AiTaskServiceImpl.java`

Changes:

* `attachSelectedKnowledge(...)` now stores the compact projected map;
* task creation with preselected knowledge now stores the same compact
  representation;
* context snapshots remain unchanged.

Outcome:

* persisted `ai_tasks.selected_knowledge_snapshot` now mirrors the real
  AI-facing payload instead of a backend-only verbose structure;
* diagnostics on future failures become more representative of what the model
  actually saw.

### 4. Added regression coverage for the new contract

Added:

* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionServiceTest.java`

Updated:

* `backend/src/test/java/com/hopeful117/devlogai/ai/engine/client/RestAIEngineClientTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/ai/engine/client/RestAIEngineClientIntegrationTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/ai/task/service/AiTaskServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceTest.java`

Covered scenarios:

* prompt projection excludes heavy backend-only repository-context metadata;
* HTTP client tests submit the compact contract shape;
* task persistence continues to work with the projected map;
* workflow tests remain aligned with the new dependency and payload path.

## Behavioral Outcome

### Now prevented

* oversized AI-facing `selectedKnowledge` payloads caused mainly by verbose
  `repositoryContext` internals
* prompt snapshots that contain backend-only ranking/allocation detail not
  needed by the model

### Preserved

* strict grounding validation in Python and Java
* repository evidence references used by the validators
* deterministic selection logic
* existing selected-knowledge and analysis-context closure fixes

### Explicitly deferred

* increasing provider timeout as the primary fix
* relaxing model output validation
* redesigning `RepositoryContext` internally
* changing AI-engine prompt semantics beyond payload compaction

## Measured Impact

Measured on a real failed `ai_task` snapshot (`8ad5983f-ab53-461b-a3cd-4b0b8f22b31e`):

* original serialized payload: `216067` characters
* compacted serialized payload: `86059` characters
* reduction: `130008` characters
* reduction ratio: `60.2%`

This confirms that the implementation attacks the dominant payload-size source
identified during repository analysis.

## Documentation Outcome

Documentation update: Required.

Updated or added:

* `docs/stories/0045-fix-understanding-refresh-llm-timeout/story.md`
* `docs/stories/0045-fix-understanding-refresh-llm-timeout/repository-analysis.md`
* `docs/stories/0045-fix-understanding-refresh-llm-timeout/implementation-plan.md`
* `docs/stories/0045-fix-understanding-refresh-llm-timeout/implementation-report.md`

Reason:

* the Story required explicit documentation of the timeout root cause, the
  compact-contract fix, and the measured payload reduction.

## Vault Outcome

* Vault consulted during Repository Analysis: No
* Outcome: no vault action
* Rationale: this Story is a repository-local operational bugfix rather than a
  new cross-project engineering pattern.

## Validation

Performed:

* targeted backend contract tests
* full backend `./mvnw verify`
* repository diff formatting check
* payload-size impact measurement on a real persisted snapshot

Results:

* `./mvnw -Dtest=RestAIEngineClientTest,RestAIEngineClientIntegrationTest,AiTaskServiceTest,ProjectUnderstandingServiceTest,SelectedKnowledgePromptProjectionServiceTest test`: pass
* `./mvnw verify`: pass
* JaCoCo coverage checks: pass
* `git diff --check`: pass
* measured prompt-size reduction: `60.2%`

Not yet completed in this implementation stage:

* live end-to-end refresh retest against the running local backend and AI
  engine

Reason:

* the code and tests are green, but the currently running local stack has not
  yet been hot-restarted and re-exercised with a fresh understanding refresh
  after this implementation step.
