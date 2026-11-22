# Story 0226 - Implementation Report

## Repository State

- Branch: `main`
- HEAD: `5fb84fc4b83da75974a7568e30b7fd55fdcafb01`
- Worktree: modified; pre-existing prompt-fix changes and `.playwright-mcp/`
  diagnostics were not modified or reverted.
- Commit created: `NO`
- Push performed: `NO`

## Governance

- Governing ADRs: ADR-019, ADR-020, ADR-067
- Authorized Story: Story 0226, `durable-ai-task-lifecycle-and-timeout-recovery`
- Repository Analysis: approved by the human
- Implementation Plan: approved by the human

## Files Changed

- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTaskStatus.java`:
  Added the Core-owned terminal `TIMED_OUT` state.
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java`:
  Added deadline, terminal reason, and recovery action persistence fields.
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/service/AiTaskServiceImpl.java`:
  Assigns a five-minute default deadline when a task is submitted.
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/service/AiTaskTimeoutRecoveryService.java`:
  Added a bounded, failure-isolated scheduled watchdog for expired
  submitted/processing tasks.
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/service/AiTaskTimeoutTransitionService.java`:
  Applies one timeout transition in an isolated transaction and updates the
  related active analysis atomically.
- `backend/src/main/resources/db/migration/V91__add_ai_task_deadline_recovery.sql`:
  Added deadline/recovery columns and a partial recovery index.
- `backend/src/main/resources/db/migration/V92__backfill_ai_task_deadlines.sql`:
  Backfills deadlines for existing submitted/processing tasks.
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskCallbackRejectionService.java`:
  Terminalizes permanent callback contract/domain rejections as `FAILED` while
  preserving transient state conflicts as retryable.
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java`:
  Uses the centralized terminal-state predicate.
- `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidenceServiceImpl.java`:
  Treats `TIMED_OUT` as terminal evidence state.
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/dto/response/AiTaskResponse.java`:
  Exposes deadline and recovery diagnostics.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentTool.java`:
  Includes deadline and recovery metadata in resumed polling diagnostics.
- `backend/src/test/java/com/hopeful117/devlogai/ai/task/service/AiTaskServiceTest.java`:
  Verifies deadline assignment and rejects resubmission without changing it.
- `backend/src/test/java/com/hopeful117/devlogai/ai/task/service/AiTaskTimeoutRecoveryServiceTest.java`:
  Verifies timeout, pre-deadline, idempotent terminal behavior, and analysis
  consistency.
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/README.md`:
  Documents the lifecycle and timeout configuration.
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/README.md`:
  Documents Core timeout behavior and the remaining AI Engine dispatch limit.

## Implementation Summary

Core now assigns a persisted execution deadline only on initial submission and
has a bounded, database-locked recovery path that transitions stale tasks to
the distinct terminal state `TIMED_OUT`. Existing submitted/processing tasks
are backfilled during migration. Permanent callback contract, reference, and
domain rejections now become terminal `FAILED` tasks with bounded diagnostics,
while transient state conflicts remain retryable. MCP polling remains a
transport read concern and preserves Core recovery metadata when resuming a
task.

## Tests and Validation

Passed:

- `./backend/mvnw -pl backend -DskipTests compile -B`
- `./backend/mvnw -pl backend test -Dtest=AiTaskServiceTest,AiTaskTimeoutRecoveryServiceTest -B`
  - 18 tests passed.
- `./backend/mvnw -pl backend test -Dtest=AiTaskCallbackRejectionServiceTest -B`
  - 2 tests passed.
- `./backend/mvnw -pl backend test -Dtest=AiTaskCallbackRejectionServiceTest,AiTaskServiceTest,AiTaskTimeoutRecoveryServiceTest -B`
  - 20 tests passed.
- `./backend/mvnw -pl backend test -Dtest=AiTaskSelectedEvidencePersistenceIntegrationTest,StoryContextCoreIntegrationTest -B`
  - 8 integration tests passed; Flyway applied migrations through v92.
- `./backend/mvnw -pl mcp-server -am -DskipTests compile -B`
- `uv run pytest tests/test_ai_tasks.py -q`
  - 5 tests passed.
- `uv run pytest tests/test_core_callback_client.py tests/test_task_processing_authorization.py tests/test_ai_tasks.py -q`
  - 21 tests passed.
- `git diff --check`

Not fully validated:

- `./backend/mvnw -pl backend test -B` exceeded the 120-second execution limit
  and reported one unrelated existing test error in
  `AnalyzeStoryContextUseCaseTest.executeUsesCanonicalFacadeWithoutBroadeningGrounding`.
  The error is a null mocked `AiTaskSubmissionResponse` in the existing test
  setup, not a failure in the new timeout tests.
- `uv run pytest -q` reported two unrelated existing failures in
  `tests/test_devlog_ai_q1_b_binding.py` and
  `tests/test_story0184_bounded_behavioral_baseline.py`.
- No live-provider or restart integration test was run.

## Architectural Decisions Preserved

- Java Core remains the authority for task status, deadline, and terminal
  recovery.
- `POLL_TIMEOUT` remains distinct from Core `TIMED_OUT`.
- No trust, grounding, authorization, or project-scoping control was weakened.
- The AI Engine remains process-lifetime `BackgroundTasks` based; this change
  does not claim durable provider dispatch or provider cancellation.

## Known Limitations

- AI Engine restart replay is not implemented in this increment.
- Core timeout does not cancel a provider request.
- Full backend and full AI Engine suites remain red or incomplete because of
  pre-existing failures and execution limits.

## Documentation Reconciliation

Documentation update: **completed**.

The AI task and AI Engine package READMEs now document the new terminal state,
deadline/recovery configuration, distinction from MCP `POLL_TIMEOUT`, and the
remaining process-lifetime dispatch limitation.

## Vault Outcome

- Vault consulted: `NO`.
- Outcome: `no vault action`.
- Rationale: the Story was fully grounded in repository code, ADRs, Story
  artifacts, and runtime evidence; no curated transverse note was required.
- No `vault-outcome.json` was created because there is no proposal-only vault
  candidate.

## Readiness

`READY_FOR_HUMAN_REVIEW_WITH_VALIDATION_LIMITATIONS`

Human review is required for the implementation diff, the remaining full-suite
failures, and the explicit decision whether durable AI Engine dispatch belongs
in a follow-up Story or must be added before acceptance.
