# Story 0226 - Implementation Plan

## Plan Status

**APPROVED ANALYSIS - PLAN PREPARED - AWAITING IMPLEMENTATION AUTHORIZATION**

This document translates the approved Repository Analysis into an executable
implementation plan. It does not itself authorize production code changes.

## Scope

Implement durable lifecycle and timeout recovery for AI tasks across Java Core,
the Python AI Engine, and the MCP adapter while preserving Core authority for
task state, deadlines, grounding, trust, and terminal outcomes.

The implementation must not include unrelated changes currently present in the
worktree.

## Decisions Preserved

- Core remains the sole authority for `AiTask` state and terminalization.
- MCP `POLL_TIMEOUT` remains a caller read timeout, not a task outcome.
- AI Engine remains a probabilistic executor and callback client; it does not
  reconstruct Core context or persist trusted knowledge.
- Callback retries remain bounded and idempotent.
- No trust-boundary relaxation is allowed for timeout recovery.
- Recovery must be safe under duplicate callbacks, duplicate watchdog runs,
  provider retries, and MCP resume calls.

## Proposed Lifecycle Model

### Core Task States

Retain existing states and add a dedicated terminal `TIMED_OUT` state rather
than overloading `FAILED`:

```text
CREATED -> SUBMITTED -> PROCESSING -> COMPLETED
                         |             |
                         v             v
                       FAILED       FAILED
                         |
                         v
                      TIMED_OUT
```

`TIMED_OUT` represents a Core-owned deadline outcome. It must be distinct from
transport-level `POLL_TIMEOUT` and must remain terminal and immutable except for
diagnostic enrichment.

The final state transition rules will be implemented as a guarded state
machine, not as unconstrained setters:

- accepted completion wins only while the task is non-terminal;
- a duplicate completion is idempotent;
- a callback rejected as permanently invalid transitions the task to `FAILED`
  with a stable failure code;
- transient callback rejection remains retryable and does not terminalize the
  task;
- watchdog timeout transitions only stale non-terminal tasks whose deadline has
  elapsed;
- a task already terminal cannot be reopened by a late callback.

### Deadline Model

Persist one Core-owned deadline per task, plus lifecycle phase timestamps and
the recovery/terminalization reason. Deadline values should be derived from a
central policy by task type, with configuration bounded by safe minimum and
maximum values.

The implementation must expose enough data to distinguish:

- queue/dispatch delay;
- provider execution delay;
- callback delivery delay;
- Core callback validation/rejection;
- total task age;
- recovery action and actor.

The exact default durations remain an implementation configuration decision to
be confirmed against existing deployment profiles; they must not be copied from
the MCP polling timeout.

## Work Packages

### 1. Core Domain and Persistence

Files and areas:

- `backend/.../ai/task/entity/AiTaskStatus.java`
- `backend/.../ai/task/entity/AiTask.java`
- `backend/.../ai/task/repository/AiTaskRepository.java`
- task services and status mappers
- new Flyway migration under
  `backend/src/main/resources/db/migration/`

Changes:

- add `TIMED_OUT` and update status classification helpers;
- add deadline, terminal reason/code, lifecycle phase, and recovery metadata;
- add guarded transition methods and optimistic/concurrency protection;
- add repository queries for stale `SUBMITTED`/`PROCESSING` tasks;
- add indexes required for bounded watchdog scans;
- preserve existing idempotency digests and callback rejection audit rows;
- make migration/backfill behavior explicit for existing non-terminal tasks.

Tests:

- transition matrix tests;
- duplicate callback and late callback tests;
- stale-task selection tests;
- migration/entity serialization tests;
- concurrency tests proving one terminal transition wins.

### 2. Callback Failure Classification and Recovery

Files and areas:

- `AiTaskResultServiceImpl`
- `AiTaskCallbackRejectionService`
- `StoryContextAgentProtocolController`
- callback contract validators and related tests

Changes:

- define stable transient versus permanent callback failure categories;
- persist the category, code, message, timestamp, and attempt metadata;
- terminalize permanent callback contract failures deterministically;
- retain retryability for transient transport/provider failures;
- ensure late or duplicate callbacks are recorded without reopening tasks;
- expose the final callback/recovery reason in task snapshots.

Tests:

- permanent grounding/accounting rejection;
- transient callback timeout;
- duplicate callback digest;
- callback after `TIMED_OUT`;
- callback after `FAILED`;
- malformed callback and authorization rejection.

### 3. Core Watchdog and Recovery Job

Files and areas:

- existing scheduled-job infrastructure, if present;
- new task recovery service/job in the AI task module;
- metrics and structured logging;
- repository integration tests.

Changes:

- scan stale tasks in bounded batches;
- acquire ownership using database locking or an atomic guarded update;
- transition expired tasks to `TIMED_OUT` with a stable reason;
- record whether provider cancellation was requested, unavailable, or not
  supported;
- make repeated runs idempotent;
- avoid broad unbounded scans and avoid processing user-controlled task data in
  logs.

Tests:

- task just before deadline is not timed out;
- task past deadline is timed out;
- concurrent watchdog instances produce one transition;
- already terminal tasks are ignored;
- batch continuation and failure isolation;
- restart recovery.

### 4. AI Engine Submission and Dispatch Reliability

Files and areas:

- `ai-engine/app/api/ai_tasks.py`
- `ai-engine/app/services/task_processing_service.py`
- `ai-engine/app/clients/core_callback_client.py`
- configuration and provider adapters

Changes:

- align provider and callback attempts with the Core deadline;
- record task phase and attempt metadata required by Core diagnostics;
- replace or wrap process-lifetime-only `BackgroundTasks` dispatch with the
  repository-supported durable mechanism, if the deployment already provides
  one;
- if no durable mechanism exists, implement the explicitly approved bounded
  interim recovery behavior and document the remaining restart limitation;
- prevent callback retries beyond the Core deadline;
- preserve callback idempotency and stable correlation identifiers.

Tests:

- provider timeout;
- callback timeout and bounded retry;
- process restart before callback;
- duplicate callback after retry;
- deadline propagation;
- no callback retry after Core terminalization.

### 5. MCP Resume and Observability

Files and areas:

- `mcp-server/.../DevLogStoryAgentTool.java`
- snapshot/result protocol DTOs;
- MCP integration tests and diagnostics.

Changes:

- retain `POLL_TIMEOUT` as a non-terminal read result;
- return task identity, last known phase, Core deadline, and resumable status;
- ensure a later snapshot read observes `COMPLETED`, `FAILED`, or `TIMED_OUT`;
- avoid creating a new task when resuming the same idempotent request;
- preserve authorization and project scoping on every resume.

Tests:

- MCP polling timeout followed by successful resume;
- MCP polling timeout followed by Core `TIMED_OUT`;
- task completion between polling attempts;
- duplicate resume with same idempotency key;
- unauthorized or cross-project resume rejection.

### 6. Documentation and Operations

Update only established documentation locations:

- AI task lifecycle documentation;
- MCP operator diagnostics/runbook;
- configuration reference for deadlines and retry budgets;
- Story 0226 validation/evidence records.

Document the distinction between:

- provider timeout;
- callback rejection;
- Core `TIMED_OUT`;
- MCP `POLL_TIMEOUT`;
- ambiguous submission outcome.

## Migration and Compatibility Strategy

- Add a forward-only Flyway migration for new columns, enum/status support, and
  indexes.
- Use an explicit backfill policy for existing `SUBMITTED`/`PROCESSING` tasks;
  do not silently mark them completed or failed without evidence.
- Update API schemas and projections so unknown/new status values are handled
  deterministically by clients.
- Preserve existing submission and callback digests.
- Do not delete callback rejection history.
- Verify all terminal-status queries include `TIMED_OUT`.

## Validation Sequence

1. Run focused Core unit tests for status transitions and callback classification.
2. Run focused repository/integration tests for watchdog locking and migration.
3. Run AI Engine unit tests for provider/callback deadlines and retries.
4. Run MCP integration tests for timeout and resume behavior.
5. Run the relevant backend and AI Engine suites.
6. Run the frontend contract/build checks if generated API contracts change.
7. Re-run the provider-free Q1 scenarios.
8. Re-run a bounded live-provider scenario only after provider credentials and
   the required human validation are available.
9. Inspect the complete diff and verify unrelated worktree changes remain
   untouched.

## Acceptance Evidence

The implementation will be considered technically ready for human review only
when evidence shows:

- no accepted task remains indefinitely non-terminal after its Core deadline;
- permanent callback rejection is terminal and diagnosable;
- transient callback failure remains bounded and retryable;
- duplicate callbacks and watchdog runs are idempotent;
- MCP `POLL_TIMEOUT` can be resumed without duplicate task creation;
- Core `TIMED_OUT` is distinct from MCP transport timeout;
- task snapshots expose phase, deadline, terminal reason, and recovery data;
- provider-free tests cover restart, concurrency, and late callback behavior;
- no trust or authorization boundary was weakened.

## Rollback and Operational Safety

- Roll back application code and migration only through the repository's normal
  deployment procedure; do not use destructive database commands.
- Gate watchdog activation behind configuration if rollout sequencing requires
  observation before automatic terminalization.
- Emit metrics before enabling aggressive timeout policies.
- Keep recovery actions auditable and correlated to the task ID.
- Do not automatically cancel an external provider request unless the provider
  contract and authorization model explicitly support it.

## Open Decisions Requiring Explicit Confirmation During Implementation

1. The exact Core deadline policy per `AiTaskType`.
2. Whether the existing deployment has a durable queue/outbox suitable for AI
   Engine dispatch.
3. The callback failure taxonomy and which codes are permanently terminal.
4. The backfill policy for tasks already stuck at migration time.
5. Whether provider cancellation is supported and safe for each provider.

These are implementation-level decisions to resolve from repository evidence
and approved architectural constraints. They must not be silently broadened
into product or architecture changes.

## Implementation Authorization Gate

**READY_FOR_IMPLEMENTATION_AUTHORIZATION**

No production code has been changed for Story 0226 by this plan. Human
implementation authorization is required before work packages 1-6 begin.
