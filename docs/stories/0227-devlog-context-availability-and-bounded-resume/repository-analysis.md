# Repository Analysis - Story 0227

## Status

**ANALYSIS COMPLETE - AWAITING HUMAN APPROVAL**

This analysis covers the proposed availability and bounded-resume Story. It is
based on the current repository state at local `main` `5db1d521` and the
merged remote state `origin/main` `7c021ade`. The local worktree contains
unrelated pre-existing Python and Playwright changes; those changes are not
part of this analysis.

## DevLog Preflight

The mandatory DevLog preflight was attempted with project slug `devlog-ai` and
the idempotency key `0227-repository-analysis-preflight-20261122`. The request
timed out twice with the exact same parameters and did not return an
`aiTaskId`, `snapshotId`, or terminal status.

This is recorded as:

`DEVLOG_CONTEXT_ERROR: Story Agent context preflight timed out twice without a resumable identity.`

Repository Analysis therefore continues using repository evidence only. No
DevLog historical conclusion is treated as established for this analysis.

## Evidence Reviewed

- Story 0227 proposed Story definition.
- Story 0184 bounded investigation contract.
- Story 0214 MCP idempotency and replay safety.
- Story 0217 bounded rate-limit retry policy.
- Story 0226 durable AI task lifecycle and timeout recovery.
- ADR-081 MCP error classification, retry, and idempotency policy.
- `mcp-server/.../DevLogStoryAgentTool.java`.
- `mcp-server/.../DevlogProjectContextClient.java`.
- `mcp-server/.../DevLogStoryAgentToolTest.java`.
- `backend/.../StoryContextAgentProtocolController.java`.
- `backend/.../StoryContextSubmissionService.java`.
- `backend/.../StoryContextInvestigationActionService.java`.
- `backend/.../AiTaskTimeoutRecoveryService.java`.
- `backend/.../AiTaskTimeoutTransitionService.java`.
- `ai-engine/.../core_callback_client.py`.
- `mcp-server/src/main/resources/application.properties`.

## Current Architecture

### MCP adapter

`DevLogStoryAgentTool` is the synchronous MCP adapter. It:

1. requires an idempotency key;
2. submits an asynchronous Core task;
3. extracts `aiTaskId` from the submission response;
4. polls the authorized snapshot endpoint;
5. reads the persisted result after `COMPLETED`;
6. returns bounded diagnostics on timeout or missing result.

The default polling timeout is 90 seconds and the implementation rejects a
configured timeout above 90 seconds. A polling timeout returns `TIMED_OUT` with
`POLL_TIMEOUT`, `lastKnownStatus`, and `aiTaskId` when the initial submission
returned an identity.

### Core submission

The Core protocol accepts the request, creates an `AiTask`, persists its
context snapshot, applies authorization and budget preflight, and optionally
dispatches the AI Engine request. The task stores submission identity,
submission duration, and bounded submission errors.

The HTTP submission endpoint returns `202 Accepted` only after the Core
submission path has returned an `aiTaskId`. If the synchronous backend call
does not return, the MCP adapter has no identity to use for resume.

### Core task recovery

Story 0226 added persisted task deadlines and a scheduled recovery service.
Expired `SUBMITTED` and `PROCESSING` tasks transition to `TIMED_OUT` in an
isolated transaction with bounded failure and recovery metadata.

This protects the durable task lifecycle, but it does not itself provide the
MCP caller with a task identity when the initial HTTP submission response is
lost or times out.

### Investigation actions

Investigation actions are Core-authorized and revision-scoped. The service:

- validates task identity, provider binding, execution identity, project, and
  revision;
- validates the investigation budget;
- deduplicates action IDs by request digest;
- executes only `SEARCH_EVIDENCE` and `READ_EVIDENCE_RANGE`;
- persists action metadata, usage, ledger, grounding, and evidence references.

This path is not the first cause of the MCP availability timeout, but it is the
next validation surface because the previous live C result completed without
usable evidence.

## Confirmed Findings

### Finding 1 - Submission and polling have different timeout boundaries

The MCP adapter applies its explicit deadline in `awaitTerminalExecution`, but
the initial `submitStoryContextAgentTask` call occurs before that method and is
not wrapped by the same local deadline. A backend connection or request stall
during submission can therefore prevent the adapter from returning the
documented `POLL_TIMEOUT` response.

This is the strongest repository-supported explanation for a DevLog MCP call
that timed out without returning `aiTaskId`.

### Finding 2 - Resume exists only after identity acquisition

The current adapter can return an `aiTaskId` on a polling timeout and the Core
endpoint supports reading the task snapshot by ID. It cannot recover an
identity when the submission response itself is lost before the ID reaches the
caller.

The existing idempotency key protects Core submission deduplication, but the
MCP adapter does not expose a separate status lookup by idempotency key after a
transport timeout.

### Finding 3 - Existing tests cover polling, not submission timeout recovery

`DevLogStoryAgentToolTest` covers:

- bounded polling timeout;
- cancellation of an in-flight polling call;
- completed task with unreadable result;
- missing idempotency key;
- submission failure event.

The tests do not cover a submission call that blocks past the MCP deadline,
returns after the caller deadline, or requires recovery by idempotency key.

### Finding 4 - Task timeout is not the same as caller timeout

Core task deadlines and MCP polling deadlines are separate. A task can be
durably `SUBMITTED`, `PROCESSING`, or later `TIMED_OUT` while the MCP caller
has already received or failed to receive a `POLL_TIMEOUT` response.

The new Story must preserve this distinction and must not convert a caller
timeout into task cancellation or semantic failure.

### Finding 5 - ADR-081 forbids blind replay

ADR-081 requires structural error classification and forbids automatic replay
when the previous result is ambiguous and idempotence is not guaranteed. The
existing Core idempotency key and submission digest provide a potential safe
resume seam, but the exact lookup and response contract must be verified before
implementation.

## Likely Root Cause of the Previous Availability Failure

### Fact

The DevLog MCP preflight timed out twice without returning a resumable task or
snapshot identity. The MCP server and Core did not return a terminal diagnostic
to the caller.

### Supported interpretation

The most likely boundary is the initial synchronous submission call, because
the existing 90-second bounded handling starts only after submission returns.
The external MCP call may therefore have exceeded its own deadline while the
backend submission or downstream dispatch remained in progress.

### Unknown

The repository alone cannot establish whether the delay occurred in:

- the MCP-to-Core HTTP connection;
- Core context preparation or database work;
- AI Engine dispatch during the Core submission path;
- authentication/delegation resolution;
- the external MCP transport deadline.

The implementation must add phase-level evidence before selecting a retry
policy.

## Proposed Minimal Change Surface

The Repository Analysis recommends investigating these seams first:

1. Add a bounded timeout around the initial MCP submission call, using the same
   overall operation budget as polling.
2. Return a structured `SUBMISSION_TIMEOUT` diagnostic with the idempotency key
   hash and a safe recovery action, without pretending that a task was not
   created.
3. Add an authoritative Core status lookup by idempotency scope and canonical
   request identity, or prove that the existing task lookup is sufficient.
4. Reuse the existing idempotency and submission-digest contract instead of
   adding a second deduplication model.
5. Add phase-level observability for submission, polling, snapshot read, and
   result read.
6. Add tests for delayed submission, response loss, same-key resume, key
   conflict, and no-identity recovery.

The exact API change must remain a decision in the Implementation Plan until
the repository analysis verifies the existing repository and security seams.

## Risks

- A client-side timeout can leave Core work in progress; cancelling the local
  HTTP call must not cancel or duplicate the Core task.
- A lookup by idempotency key must be scoped by operation, project, principal,
  and delegation as required by the existing policy.
- Returning a task identity from a recovery lookup must not disclose whether an
  unauthorized task exists.
- Increasing the MCP deadline without bounding the initial submission can
  reproduce the same failure at a later point.
- Fixing availability alone will not fix the previous live C evidence failure.

## Required Implementation Decisions

The Implementation Plan must resolve:

1. Which layer owns the submission timeout and retry decision.
2. The exact timeout budget split between submission and polling.
3. Whether Core exposes a safe idempotency-key status lookup.
4. The behavior when no task identity can be recovered.
5. The diagnostic status and action recommended to the caller.
6. The preflight readiness contract required before the next live C run.

## Validation Baseline

- Existing MCP Story Agent timeout tests pass as the baseline suite for this
  surface; they do not cover submission timeout recovery.
- Existing Core task timeout recovery is implemented by Story 0226.
- DevLog preflight is currently unavailable through the MCP tool after two
  same-key timeout resumes.
- No new implementation has been made for Story 0227.

## Recommendation

Approve this Repository Analysis only after confirming that the proposed
availability Story is not duplicating the already implemented durable task
recovery from Story 0226. If approved, the Implementation Plan should begin
with submission-phase timeout instrumentation and an idempotency-scoped status
recovery contract, followed by the live investigation evidence guard.
