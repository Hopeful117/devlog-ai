# Story 0227 - Implementation Plan

## Plan Status

**APPROVED - IMPLEMENTATION IN PROGRESS**

This plan translates the approved Repository Analysis into an executable
implementation strategy. It does not authorize production code changes,
provider calls, commits, or pushes.

## Scope

Make the DevLog Story Agent context path resilient to availability and timeout
failures by separating submission deadlines from polling deadlines, preserving
idempotent resume semantics, and exposing bounded phase-level diagnostics.

The implementation must remain separate from the current uncommitted Python and
Playwright changes in the worktree.

## Decisions Preserved

- Java/Core remains authoritative for task identity, task state, deadlines,
  authorization, context scope, revision, grounding, and terminal acceptance.
- MCP remains a thin transport adapter and never becomes task authority.
- AI Engine remains responsible for provider execution and defensive validation.
- `POLL_TIMEOUT` remains a caller read deadline and never means task cancellation,
  provider failure, or successful analysis.
- Existing Story 0226 durable task timeout recovery is reused rather than
  duplicated.
- Existing Story 0214 idempotency and Story 0217 retry authority are preserved.
- No automatic replay occurs when the result may already exist and no
  authoritative deduplication contract proves it safe.
- No availability fix may relax grounding, trust, delegation, or evidence rules.

## Target Contract

The MCP adapter will distinguish these phases and outcomes:

```text
SUBMISSION_IN_PROGRESS
SUBMITTED
POLLING
POLL_TIMEOUT
COMPLETED
FAILED
TIMED_OUT
SUBMISSION_TIMEOUT
UNAVAILABLE
AUTHENTICATION_REQUIRED
```

The exact externally visible names must be reconciled with existing MCP error
contracts before implementation. The contract must preserve a stable task
identity whenever Core confirms task creation.

When no identity can be recovered, the response must state that the outcome is
unknown or unavailable. It must not claim that no task exists and must not
blindly submit a new task.

## Work Packages

### 1. Submission-Phase Timeout Boundary

Files and areas:

- `mcp-server/.../DevLogStoryAgentTool.java`
- MCP HTTP client configuration and request factory
- `mcp-server/.../DevlogProjectContextClient.java`
- existing MCP tool tests

Changes:

- Define one overall operation deadline and allocate bounded submission and
  polling time within it.
- Ensure the initial Core submission call cannot block beyond the local deadline.
- Preserve interruption and cancellation behavior for the in-flight client call.
- Return structured submission-timeout diagnostics without treating the task as
  absent or terminal.
- Record whether submission returned an identity before the timeout.

Constraints:

- Do not use an unbounded HTTP client timeout.
- Do not create a second task during local timeout handling.
- Do not add an independent retry loop in the MCP adapter unless the approved
  policy explicitly permits it.

Tests:

- submission returns within budget;
- submission blocks past budget;
- submission fails with connection error;
- submission returns an accepted task identity;
- cancellation does not turn into task cancellation;
- bounded diagnostics contain no bearer or request payload.

### 2. Idempotency-Scoped Recovery

Files and areas:

- Core Story Context submission use case and repository;
- `AiTaskRepository` and task identity queries;
- MCP client contract;
- authorization and delegation checks;
- related Core and MCP tests.

Changes:

- Verify whether the existing idempotency-key hash and submission digest are
  sufficient for safe status recovery.
- If sufficient, expose a read-only, authorization-scoped lookup for an existing
  operation by canonical request identity.
- If insufficient, define the smallest additional persisted identity required,
  with migration and retention behavior.
- Return the existing task/snapshot status rather than creating a duplicate.
- Reject the same idempotency key bound to a different canonical request.
- Preserve project, principal, operation, and delegation scope.

Tests:

- same key and same request returns the original task;
- same key and different request returns deterministic conflict;
- unknown key does not disclose another principal's task;
- recovery sees `SUBMITTED`, `PROCESSING`, `COMPLETED`, `FAILED`, and
  `TIMED_OUT` correctly;
- concurrent recovery creates no duplicate task;
- expired delegation fails closed.

### 3. Phase-Level Diagnostics and Metrics

Files and areas:

- `DevLogStoryAgentTool` diagnostics;
- `McpToolObservabilityService` and related event mapping;
- Core task submission diagnostics;
- existing metrics and structured logging.

Changes:

- Add bounded phase metadata for submission, polling, snapshot read, and result
  read.
- Correlate `operationId`, `aiTaskId`, `snapshotId`, source revision, and
  idempotency hash where each identity is available.
- Distinguish transport, Core, provider, callback, timeout, and unknown causes
  according to ADR-081.
- Add counters for submission timeout, polling timeout, identity recovery,
  recovery conflict, and service unavailability.
- Redact tokens, prompts, payloads, and repository content.

Tests:

- classification matrix for all supported failure phases;
- metrics emitted once per logical operation;
- diagnostic redaction;
- no duplicate terminal or completion events after resume.

### 4. Availability Preflight Gate

Files and areas:

- live Q1 runbook;
- bounded live execution harness;
- validation scripts and test fixtures;
- Story 0223 implementation report reconciliation.

Changes:

- Add a preflight step that verifies MCP/Core reachability, authentication,
  delegation, task submission, resumable identity, and snapshot visibility.
- Require a successful preflight before any provider call in live C.
- On preflight timeout, resume with the same key before declaring the service
  unavailable.
- Persist bounded preflight diagnostics separate from provider-quality results.
- Block the provider call when no safe resume identity or terminal service state
  can be established.

Tests:

- healthy preflight;
- timeout followed by same-key successful resume;
- timeout with no identity;
- authentication failure;
- stale source revision;
- service unavailable;
- provider call is not started after failed preflight.

### 5. Evidence Completion Guard

Files and areas:

- Story Context investigation result validation;
- Core callback/result validation;
- AI Engine structured output validation;
- investigation and live-runner tests.

Changes:

- Define when evidence is required by the investigation objective.
- Reject or classify as incomplete a semantically successful result with no
  usable evidence when evidence was required.
- Preserve `LOW` confidence and `missingInformation` rather than fabricating
  evidence or promoting technical completion to product success.
- Include the evidence retrieval outcome and source revision in diagnostics.

Tests:

- evidence present and revision-pinned;
- no evidence with required retrieval;
- empty search result;
- rejected or budget-exhausted action;
- evidence from a mismatched revision;
- technically completed but semantically incomplete result.

### 6. Documentation Reconciliation

Update only canonical documentation affected by the implementation:

- Story 0227 implementation report;
- Story 0223 live validation runbook/report if the preflight contract changes;
- Story 0218 MCP runbook when operator recovery behavior changes;
- configuration documentation if new timeout settings are introduced.

Do not update evaluation results to hide the previous failure. Preserve the
distinction between technical pipeline completion and semantic success.

## Timeout and Resume Policy

The implementation must use this decision order:

1. Detect the failing phase.
2. Determine whether Core may already have applied the operation.
3. Check whether an authoritative idempotency contract permits observation or
   retry.
4. Reuse the same key and canonical request for an allowed resume.
5. Stop after the bounded budget.
6. Return the most specific safe diagnostic available.

The implementation must never:

- mutate a task to cancel it because the MCP caller timed out;
- submit the same logical request with a new key;
- interpret missing identity as proof of no task;
- interpret technical completion as evidence quality;
- retry authentication, authorization, delegation, protocol, or unknown errors
  automatically.

## Validation Sequence

1. Run focused MCP adapter tests.
2. Run focused Core submission, identity, and task recovery tests.
3. Run focused AI Engine investigation and callback tests.
4. Run backend and MCP integration tests sequentially to avoid shared build
   output races.
5. Run the full applicable backend, MCP, and AI Engine suites.
6. Run `git diff --check` and inspect the complete diff.
7. Run the availability preflight without a provider call.
8. Run exactly one bounded live C validation only after the preflight succeeds.
9. Stop and analyze if evidence remains empty; do not launch the campaign.

## Risks and Mitigations

- **Duplicate task after timeout:** use Core-authoritative idempotency lookup and
  same-key resume.
- **Hidden task after lost response:** expose an authorization-scoped status
  recovery path or return an explicit unknown outcome.
- **MCP deadline still exceeded:** measure submission and polling separately and
  keep HTTP calls inside the overall budget.
- **Secret disclosure:** centralize bounded diagnostic construction and redact
  all credentials and payloads.
- **False semantic success:** enforce the evidence completion guard.
- **Regression in existing MCP behavior:** retain existing polling/result tests
  and add compatibility tests for terminal task states.
- **Scope expansion into generic reliability:** reuse Story 0226, 0214, and
  0217 contracts and require a new decision for any broader change.

## Deliverables

- Implementation changes limited to approved work packages.
- Focused and full validation results.
- Updated runbook/report documentation where required.
- Story 0227 implementation report with explicit documentation outcome.
- Independent code review result.
- One bounded live C preflight and validation artifact, only if authorized and
  technically ready.

## Approval Gate

Implementation is blocked until the human explicitly approves this plan. The
next permitted stage after approval is implementation, preceded by Story
lifecycle start when DevLog lifecycle configuration is available.
