# Story 0226 - Durable AI Task Lifecycle and Timeout Recovery

## Status

**PROPOSED - AWAITING HUMAN REVIEW**

This Story addresses persistent timeout and orphaned-task failures across the
Core, AI Engine, and MCP boundaries. It is intended to improve DevLog's general
runtime reliability, not only to make the Q1-C evaluation pass.

This Story does not authorize implementation, production policy changes,
provider changes, commits, or pushes until the Repository Analysis and
Implementation Plan have passed their human approval gates.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Problem

AI tasks can remain indefinitely in `SUBMITTED` or `PROCESSING` when a provider
call, callback, worker, or service process fails. The MCP synchronous adapter
then reaches its polling deadline and returns `POLL_TIMEOUT`, even though the
underlying task has not reached a durable terminal state.

The Q1-C live investigation exposed this failure mode: provider generations
were executed, the Core callback was rejected with a `409` because provider
accounting did not match the interaction traces, the rejection was recorded,
but the task remained `SUBMITTED`. The MCP caller eventually observed a
timeout rather than a terminal failure with a recoverable diagnostic.

The current runtime has independent limits for provider calls, provider
retries, Core callbacks, background processing, and MCP polling. These limits
do not form one persisted execution deadline. AI Engine processing also relies
on FastAPI background tasks, which are not durable across process failure or
restart.

## Goal

Ensure that every accepted AI task reaches a durable terminal state or an
explicitly recoverable state without weakening authorization, grounding,
trust, provenance, or fail-closed behavior.

The solution must:

- prevent tasks from remaining indefinitely non-terminal;
- distinguish read timeouts from provider, callback, dispatch, and orphaned-task failures;
- support resumption after an MCP polling timeout;
- survive AI Engine process restart where the runtime contract requires it;
- expose bounded, actionable diagnostics;
- reduce timeout-related failures across DevLog generally.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-020 - Asynchronous AI Engine Callback Integration
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0112 - DevLog Story Context Agent
- Story 0176 - Story Context Grounding and Retry Efficiency
- Story 0179 - Story Context Runtime Loss Evaluation
- Story 0211 - MCP Error Retry Policy
- Story 0212 - MCP Runtime Retry Renewal
- Story 0213 - MCP Core Call Policy
- Story 0214 - MCP Idempotency Replay Safety
- Story 0215 - MCP Diagnostics Agent Operator Experience
- Story 0216 - MCP Resilience Validation
- Story 0217 - MCP Rate Limit Retry-After
- Story 0045 - Fix Understanding Refresh LLM Timeout
- `docs/stories/bugfix-llm-provider-timeout/story.md`

## Dependencies

- Existing Core `AiTask` lifecycle and callback contracts.
- Existing callback rejection persistence and interaction traces.
- Existing authenticated Story Context snapshot and result endpoints.
- Existing MCP synchronous Story Agent adapter and asynchronous protocol tools.
- Existing provider timeout and retry configuration.

## In Scope

- Formalize persisted task deadlines and terminal lifecycle semantics.
- Prevent `SUBMITTED` and `PROCESSING` tasks from remaining indefinitely non-terminal.
- Distinguish transient callback failures from permanent callback rejection.
- Terminalize permanently rejected callbacks with bounded failure diagnostics.
- Add Core-owned recovery for orphaned or expired tasks.
- Align Core, AI Engine, provider, callback, and MCP deadlines.
- Preserve resumption by `aiTaskId` after an MCP `POLL_TIMEOUT`.
- Make accepted AI dispatch recoverable after AI Engine restart.
- Replace or safely bound non-durable `BackgroundTasks` execution.
- Expose phase-level timing and bounded timeout diagnostics.
- Add unit, integration, restart, concurrency, and resilience tests.
- Measure timeout rate, orphan rate, callback rejection rate, and phase latency.

## Out of Scope

- Increasing the MCP timeout without correcting task lifecycle behavior.
- Weakening Core grounding, trust, authorization, or provenance validation.
- Treating incomplete or unvalidated output as a successful analysis.
- Broadening context or evidence selection to avoid a timeout.
- Introducing RAG, autonomous retrieval, or unbounded agent loops.
- Changing authentication, delegation, credential ownership, or trust boundaries.
- Removing retries without measured correctness and reliability evidence.
- Modifying Q1 evaluation artifacts to hide or reclassify timeout outcomes.
- Promoting AI output directly into trusted project knowledge.

## Architectural Constraints

1. Java/Core remains authoritative for task state, deadlines, context,
   authorization, grounding, trust, and terminal acceptance.
2. The AI Engine remains responsible for probabilistic provider execution and
   defensive output validation only.
3. MCP remains a transport adapter and must not become the task authority.
4. A read/poll timeout must not implicitly cancel or duplicate a Core task.
5. Callback retries must preserve the same task identity, payload identity, and
   idempotency semantics.
6. Provider retries must remain bounded by a Core-owned execution deadline.
7. Terminal callback transitions must be idempotent and concurrency-safe.
8. Diagnostics must not expose credentials, authorization headers, full prompts,
   repository payloads, or raw provider responses.
9. Timeout and recovery paths must never relax grounding or trust validation.

## Invariants

1. Every accepted task eventually reaches `COMPLETED`, `FAILED`, or `TIMED_OUT`,
   or is explicitly marked as recoverable with a durable next action.
2. `POLL_TIMEOUT` is a caller read deadline and is distinct from task
   `TIMED_OUT`.
3. A permanent callback rejection cannot leave the task in `SUBMITTED` or
   `PROCESSING` indefinitely.
4. A transient callback rejection remains retryable only for a bounded period.
5. The same terminal callback cannot create duplicate results or analyses.
6. No retry may exceed the global task deadline.
7. An AI Engine restart cannot silently lose an accepted task.
8. A timeout never implies successful provider execution or accepted analysis.
9. Provider-visible evidence remains unknown unless the callback contract
   explicitly establishes it.

## Acceptance Criteria

### AC-1 - Terminal Task Lifecycle

Every accepted AI task reaches one of `COMPLETED`, `FAILED`, or `TIMED_OUT`.
No task remains indefinitely in `SUBMITTED` or `PROCESSING`.

### AC-2 - Persisted Execution Deadline

Each task has a persisted, deterministic execution deadline covering submission,
dispatch, provider execution, retries, callback processing, and result
persistence. The deadline remains available after service restart.

### AC-3 - Orphan Recovery

A Core-owned recovery mechanism identifies tasks without valid progress beyond
their deadline and transitions them safely to `TIMED_OUT` or an explicitly
documented terminal failure. The transition is idempotent and preserves bounded
provenance and diagnostics.

### AC-4 - Callback Failure Taxonomy

Callback failures are classified at minimum as:

- `TRANSIENT_NOT_READY`;
- `TRANSIENT_INFRASTRUCTURE`;
- `INVALID_CALLBACK`;
- `CONTRACT_REJECTION`;
- `IDENTITY_REJECTION`;
- `TERMINAL_STATE_CONFLICT`.

Permanent failures produce a terminal task state or an explicit documented
recovery action. `AI_TASK_NOT_READY` is not treated as a permanent failure.

### AC-5 - Bounded Callback Retries

Callback retries are attempted only for explicitly transient failures. Retries
are bounded by both attempt count and the persisted task deadline, reuse the
same callback identity, and record each attempt.

### AC-6 - Resumable MCP Timeout

When MCP reaches its read deadline, the response includes the task identifier,
the last known state, `POLL_TIMEOUT`, and enough bounded metadata to resume
reading the same task. A subsequent read can observe the eventual terminal
state without creating a duplicate task.

### AC-7 - Durable Dispatch

An accepted Core task is not dependent solely on the lifetime of an HTTP request
or FastAPI process. After AI Engine restart, the task is either resumed, safely
failed, or terminalized by Core recovery.

### AC-8 - Bounded Diagnostics

Authorized task snapshots expose, where available:

- current state;
- failure or timeout code;
- last transition and phase;
- deadline and task age;
- attempt counts;
- last callback rejection classification;
- bounded provider and callback timing.

No sensitive prompt, repository content, raw model output, or credentials are
included.

### AC-9 - Phase-Level Observability

The runtime records separate measurements for context preparation, submission,
dispatch wait, provider execution, validation, callback, persistence, retries,
and total execution. P50, P95, and P99 measurements can distinguish provider
timeouts from orchestration and callback timeouts.

### AC-10 - Resilience Test Coverage

Automated tests cover:

- a slow provider that completes after the MCP read deadline;
- provider timeout;
- temporary callback unavailability;
- permanent callback rejection;
- tasks stuck in `SUBMITTED`;
- tasks stuck in `PROCESSING`;
- AI Engine restart after task acceptance;
- MCP timeout followed by successful resumption;
- duplicate callbacks;
- concurrent terminal transitions;
- deadline expiry during retry.

### AC-11 - No Trust Relaxation

Timeout and recovery behavior never accepts unvalidated output, broadens
context, bypasses authorization, bypasses grounding, or converts incomplete
output into a successful analysis.

### AC-12 - Before/After Reliability Evidence

An engineering report compares the baseline and resulting runtime for:

- successful task rate;
- timeout rate;
- orphaned task rate;
- callback rejection rate;
- task recovery rate;
- P50/P95/P99 latency;
- retry count and retry cost.

## Proposed Implementation Phases

1. Map current lifecycle transitions, deadlines, and callback rejection paths.
2. Add phase-level metrics and bounded diagnostics.
3. Formalize terminal states and callback failure taxonomy.
4. Implement orphan detection and Core-owned recovery.
5. Propagate a global deadline across provider, callback, and persistence phases.
6. Make AI dispatch durable across process restart.
7. Harden MCP timeout responses and task resumption.
8. Add resilience, concurrency, restart, and regression tests.
9. Produce before/after reliability evidence and operational documentation.

## Human Decisions Required

- Approve the Story scope and acceptance criteria.
- Decide whether `TIMED_OUT` becomes a first-class `AiTaskStatus` or remains a
  specialized `FAILED` outcome.
- Decide whether durable dispatch uses an existing outbox or a new queue
  abstraction.
- Approve default deadlines by task type.
- Approve the Repository Analysis before implementation planning.
- Approve the Implementation Plan before code changes.

## Validation

- Backend lifecycle and callback tests.
- AI Engine provider, retry, and callback tests.
- MCP polling and resumption tests.
- Integration tests across Core, AI Engine, and MCP.
- Restart and orphan-recovery tests.
- Runtime metrics and bounded-diagnostics verification.
- No claim of timeout reduction without before/after evidence.
