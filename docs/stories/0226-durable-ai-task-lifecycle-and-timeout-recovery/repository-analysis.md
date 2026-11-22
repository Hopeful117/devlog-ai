# Story 0226 - Repository Analysis

## Analysis Status

**COMPLETED - AWAITING HUMAN APPROVAL**

This document is the Repository Analysis for Story 0226. It is not an
Implementation Plan and does not authorize production code changes.

## Repository State

- Branch: `main`
- HEAD: `5fb84fc4b83da75974a7568e30b7fd55fdcafb01`
- Worktree: modified before this analysis
- Existing unrelated changes:
  - `ai-engine/app/prompts/story_context_analysis.py`
  - `ai-engine/evaluations/scenarios/engineering-story-context-analysis-v1/scenario.json`
  - `ai-engine/tests/test_story_context_analysis_prompt.py`
  - untracked `.playwright-mcp/` diagnostics
- Story artifact: `docs/stories/0226-durable-ai-task-lifecycle-and-timeout-recovery/story.md`

The existing worktree changes must not be included in Story 0226 unless a later
approved Implementation Plan explicitly establishes that relationship.

## DevLog Preflight

`devlog_devlog_story_agent` was invoked twice with the same request and stable
idempotency key. Both calls ended with `MCP error -32001: Request timed out`.

Result: `DEVLOG_CONTEXT_ERROR`.

No Story UUID was invented for Story 0226. This analysis therefore uses the
current repository, accepted repository artifacts, and runtime evidence as its
authoritative sources. The DevLog context limitation must be reported in later
workflow artifacts until the service can provide a terminal result.

## Governing Context

The repository establishes the following constraints:

- ADR-020 defines asynchronous AI Engine callbacks as the primary integration
  boundary and rejects polling as the primary inter-service workflow.
- ADR-067 requires Core to remain authoritative for context, authorization,
  grounding, trust, and failure semantics.
- Story 0112 defines AI Engine unavailability and timeout as transport errors
  without trust-boundary relaxation.
- Story 0176 explicitly excludes changing MCP polling timeout as a substitute
  for grounding remediation; this Story addresses the separate systemic runtime
  problem.
- Stories 0211, 0214, and 0216 define bounded retry, idempotency, ambiguous
  results, concurrency, and fail-closed invariants for MCP operations.
- Story 0179 records prior runtime evidence where a targeted scenario timed out
  and where identical questions were reused by task identity despite distinct
  idempotency keys.

## Current Architecture

### Core Task Lifecycle

`AiTaskStatus` currently contains only:

```text
CREATED -> SUBMITTED -> PROCESSING -> COMPLETED / FAILED
```

The entity stores task status, timestamps, attempt count, external job ID,
failure fields, callback digest, submission digest, and the last callback
failure. It does not currently store an execution deadline or a dedicated
`TIMED_OUT` state.

The repository has an index on task status, but no repository query or scheduled
recovery path for stale `SUBMITTED` or `PROCESSING` tasks was found.

### Core Submission and Callback

`StoryContextSubmissionService` creates the task and moves it to `SUBMITTED`
after dispatch preparation. The AI Engine callback is accepted only while the
task is `SUBMITTED` or `PROCESSING`.

`StoryContextAgentProtocolController` catches callback exceptions, records them
through `AiTaskCallbackRejectionService`, and rethrows the exception. The
rejection service persists an audit row and copies the last callback failure
onto `AiTask`, but it does not transition the task to a terminal state.

This is safe for genuinely transient callback failures, but it leaves no
terminalization path for permanent callback contract failures. The observed Q1-C
case demonstrates the result: a callback rejection can be durable while the
task remains `SUBMITTED`.

### AI Engine Dispatch

The AI Engine accepts a task and schedules processing through FastAPI
`BackgroundTasks`. The HTTP acceptance response is therefore not coupled to a
durable queue or a persisted dispatch record owned by the AI Engine.

Processing failures are logged by `process_with_observability`, but there is no
independent durable task state in the AI Engine that Core can reconcile when
processing or callback delivery is interrupted.

### Provider and Callback Timeouts

Current defaults include:

- provider timeout: `90` seconds;
- callback HTTP timeout: `5` seconds;
- callback attempts: `5`;
- callback retry delay: `100` ms to `1000` ms;
- MCP Story Agent polling timeout: `90` seconds;
- MCP polling interval: `2` seconds.

The provider may also perform provider-specific retries. These values are
configured independently and are not currently bounded by one persisted Core
deadline.

### MCP Synchronous Adapter

`DevLogStoryAgentTool` submits a task and polls the authenticated snapshot until
the configured deadline. It returns `TIMED_OUT` with `POLL_TIMEOUT`, the task ID,
and the last known status when the read deadline expires.

This correctly distinguishes a caller read timeout from a completed result, but
the implementation cannot repair a task that remains non-terminal in Core. The
asynchronous MCP tools already expose submission and snapshot reads, so the
repository has the transport primitives needed for resumable execution.

## Confirmed Runtime Evidence

The local Q1-C task `a524f064-b0d3-41b7-9d31-1276146b735c` remains `SUBMITTED`.
The associated AI Engine logs recorded provider execution followed by a Core
callback rejection caused by provider accounting mismatch. The MCP caller then
observed a polling timeout.

This establishes the following causal chain:

```text
provider execution
  -> callback rejected
  -> rejection persisted but task not terminalized
  -> snapshot remains SUBMITTED
  -> MCP polling deadline expires
  -> caller receives POLL_TIMEOUT
```

This is a confirmed lifecycle defect, not evidence that increasing the MCP
polling timeout would solve the underlying problem.

## Findings

### Finding 1 - No durable global deadline

Provider, callback, and MCP timeouts are independent. A task can outlive the
caller timeout without a Core-owned deadline or recovery outcome.

### Finding 2 - Permanent callback rejection can orphan a task

Callback rejection persistence is observational only. It does not distinguish
transient rejection from permanent invalid callback, nor does it guarantee a
terminal task transition.

### Finding 3 - AI dispatch is process-lifetime dependent

FastAPI `BackgroundTasks` does not provide durable delivery or restart recovery.
An accepted task can be lost between Core submission and callback completion.

### Finding 4 - `TIMED_OUT` exists at protocol level but not task level

The MCP response can emit `TIMED_OUT`, while Core `AiTaskStatus` has no matching
state. This creates ambiguity between caller timeout and business execution
timeout.

### Finding 5 - Diagnostics are incomplete for stuck tasks

`AiTask` stores last callback failure data, but the Story Agent execution and
snapshot projections do not expose a complete lifecycle timeline, deadline,
phase, or recovery action.

### Finding 6 - Existing idempotency is useful but requires lifecycle policy

Submission digest and idempotency-key scoping prevent duplicate equivalent
submissions, but a failed or orphaned equivalent task cannot be reasoned about
as a new execution until retry/recovery semantics are explicitly defined.

## Scope Mapping

### Core Backend

Likely affected surfaces:

- `ai/task/entity/AiTask.java`
- `ai/task/entity/AiTaskStatus.java`
- `ai/task/repository/AiTaskRepository.java`
- `ai/task/service/AiTaskServiceImpl.java`
- `ai/engine/service/AiTaskResultServiceImpl.java`
- `ai/engine/service/AiTaskCallbackRejectionService.java`
- `storycontextanalysis/controller/StoryContextAgentProtocolController.java`
- `storycontextanalysis/usecase/DevLogStoryAgentService.java`
- database migrations and task response DTOs

### AI Engine

Likely affected surfaces:

- `app/api/ai_tasks.py`
- `app/services/task_processing_service.py`
- `app/clients/core_callback_client.py`
- `app/core/config.py`
- provider timeout/retry adapters
- provider and callback tests

### MCP Server

Likely affected surfaces:

- `DevLogStoryAgentTool.java`
- authenticated snapshot/result clients
- MCP timeout and resumption tests

### Documentation and Operations

Likely affected surfaces:

- AI task lifecycle README files;
- MCP operator diagnostics/runbooks;
- timeout and recovery configuration documentation;
- migration and operational validation instructions.

## Risks and Unknowns

1. Adding `TIMED_OUT` to the enum may affect API consumers, analysis status
   mapping, serializers, database constraints, and existing terminal queries.
2. Marking callback rejection as terminal too early could prevent a valid
   bounded retry; classification must be explicit and tested.
3. A durable dispatch mechanism may be a larger architectural change than an
   orphan watchdog and requires a decision about outbox/queue ownership.
4. Provider cancellation semantics are not yet established; a Core timeout may
   not cancel an in-flight remote request.
5. Existing callback rejection rows may need migration or reconciliation rules.
6. The current working tree contains unrelated changes and must be isolated
   from Story 0226 implementation.
7. DevLog context remains unavailable after two same-key timeout attempts.

## Recommended Implementation Direction

The Implementation Plan should be staged rather than starting with a larger MCP
timeout:

1. Define the Core state/deadline model and callback failure taxonomy.
2. Add bounded lifecycle diagnostics and metrics.
3. Add idempotent orphan detection and terminal recovery in Core.
4. Define callback retry and permanent rejection transitions.
5. Align provider and callback retries with the Core deadline.
6. Decide and implement durable AI dispatch or an explicitly bounded interim
   recovery mechanism.
7. Harden MCP read-timeout/resumption behavior without making MCP the task
   authority.
8. Validate with provider-free lifecycle, restart, concurrency, and timeout
   integration tests before any live-provider retest.

## Validation Required Before Planning

- Confirm all consumers of `AiTaskStatus` and terminal-status queries.
- Inventory existing Flyway migrations and status serialization contracts.
- Determine whether a scheduler/recovery framework already exists in Core.
- Determine the supported durable dispatch primitives in the deployment.
- Map current provider cancellation and retry behavior per provider.
- Add or identify test fixtures for callback rejection and stuck tasks.
- Establish a baseline from existing runtime captures without treating one run as
  a general population estimate.

## Repository Analysis Conclusion

The Story is technically justified and its scope is consistent with the
repository architecture. The confirmed defect is an incomplete task lifecycle:
callback rejection and process failure can leave Core tasks non-terminal, after
which MCP polling reports a timeout. The next permitted workflow stage is an
Implementation Plan, but only after explicit human approval of this Repository
Analysis.

**Readiness:** `WAITING_FOR_ANALYSIS_APPROVAL`
