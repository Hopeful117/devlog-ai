# Story 0227 - DevLog Context Availability and Bounded Resume

## Status

**IMPLEMENTATION IN PROGRESS - APPROVED FOR IMPLEMENTATION**

This Story addresses availability, timeout classification, and bounded resume
behavior for the `devlog_story_agent` context path. It was proposed after the
bounded live C validation completed technically but the DevLog preflight itself
timed out twice without returning a usable task or snapshot identity.

This Story does not authorize implementation, runtime policy changes, provider
calls, commits, or pushes until its Repository Analysis and Implementation Plan
have passed their human approval gates.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Problem

An MCP context request can time out before the caller receives a usable
`aiTaskId`, `snapshotId`, or terminal status. The caller cannot reliably
distinguish an unavailable transport from a submitted task whose response was
lost, an MCP polling timeout, an AI Engine failure, a Core callback failure, or
an authentication failure.

Blindly issuing a new request can duplicate work or violate idempotency. A
timeout must therefore be resumable and diagnosable, not silently converted
into a new request or treated as proof that no task exists.

## Goal

Ensure that every bounded DevLog context request can be observed, resumed, or
terminated with an explicit bounded diagnostic, while preserving Core task
authority, idempotency, authorization, grounding, provenance, and trust rules.

The next live investigation must not begin until the availability preflight is
observable and successful enough to provide a stable resume identity.

## Governing Artifacts

- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy
- Story 0184 - Bounded Story Context Investigation
- Story 0214 - MCP Idempotency Replay Safety
- Story 0217 - MCP Rate Limit Retry-After
- Story 0218 - MCP Documentation Runbooks
- Story 0226 - Durable AI Task Lifecycle and Timeout Recovery
- Story 0223 - Agentic Quality Real Story Pilot

## Dependencies

- Existing Core `AiTask` lifecycle, identity, callback, and timeout recovery.
- Existing MCP synchronous Story Agent adapter and asynchronous protocol.
- Existing idempotency-key and submission-digest contracts.
- Existing interaction traces and bounded timeout diagnostics.
- Existing investigation action and evidence contracts from Story 0184.

## In Scope

- Classify the phase and nature of a DevLog context timeout.
- Define observable states for submission, polling, completion, failure, and
  service unavailability.
- Preserve and expose a stable resume identity after accepted submission.
- Resume an equivalent request using the exact same idempotency key and request
  identity.
- Reject reuse of an idempotency key with a different canonical request.
- Add bounded availability and readiness diagnostics for MCP, Core, and the AI
  Engine path where the repository already has an appropriate seam.
- Correlate operation, task, snapshot, and source-revision identities.
- Add metrics and bounded logs for timeout phase, retry/resume attempt, and
  terminal outcome.
- Define the preflight gate required before the next live C validation.
- Document operator and agent recovery behavior.
- Validate that a completed investigation with required retrieval does not claim
  semantic success when no usable evidence was returned.

## Out of Scope

- Improving provider reasoning quality or evidence selection itself.
- Adding generic or unlimited retries.
- Replaying a mutation without an authoritative idempotency contract.
- Increasing timeouts merely to hide a failure.
- Introducing a queue, broker, durable scheduler, or generic agent framework.
- Adding RAG, embeddings, or unrestricted repository access.
- Changing trust, grounding, delegation, or credential ownership rules.
- Running the 72-assignment A/B/C campaign.
- Treating an unavailable DevLog context as successful project knowledge.

## Architectural Constraints

1. Java/Core remains authoritative for task state, deadlines, authorization,
   context scope, revision identity, grounding, and terminal acceptance.
2. The AI Engine remains responsible for provider execution and defensive output
   validation only.
3. MCP remains a transport adapter and does not become task authority.
4. A caller polling timeout never implicitly cancels or duplicates a Core task.
5. Equivalent resumes preserve the same task identity, request identity, and
   idempotency semantics.
6. Retry and resume behavior remains bounded and owned by the already-established
   runtime authority for each error category.
7. Diagnostics contain no credentials, authorization headers, full prompts,
   raw provider responses, or unbounded repository payloads.
8. A timeout never implies provider success, evidence availability, or accepted
   analysis.
9. Source revision and snapshot freshness remain explicit in every successful
   context result.

## Invariants

1. A transport timeout, `POLL_TIMEOUT`, task timeout, provider failure, callback
   failure, and authentication failure are distinguishable.
2. A request that may already have been submitted is resumed with the same key;
   it is never blindly resubmitted under a new key.
3. A reused key with a different canonical request produces a deterministic
   conflict.
4. No unbounded wait or retry is possible.
5. A task without a stable resume identity is reported as unavailable or
   unknown, never as successfully completed.
6. A successful investigation requiring evidence has at least one valid,
   revision-pinned, cit-able evidence result.
7. Diagnostics are safe to expose to an agent or operator.
8. Existing Story 0226 terminal lifecycle and recovery invariants remain intact.

## Acceptance Criteria

### AC-1 - Timeout classification

The system distinguishes transport unavailability, submission timeout, polling
timeout, task timeout, provider failure, callback failure, and authentication
failure in structured status and diagnostics.

### AC-2 - Stable resume identity

After a request is accepted, the caller can obtain or recover the stable task or
snapshot identity needed to observe the existing operation.

### AC-3 - Exact idempotent resume

Repeating an equivalent request with the same idempotency key returns the
existing operation or its terminal result without creating a duplicate task.

### AC-4 - Key conflict protection

Reusing an idempotency key with a different canonical request is rejected with a
bounded, non-sensitive deterministic error.

### AC-5 - Bounded availability behavior

Availability checks, waits, and resumes have explicit maximum duration and
attempt budgets. Exhaustion produces a terminal diagnostic and recommended next
action.

### AC-6 - Correlated observability

Diagnostics and metrics correlate operation, task, snapshot, source revision,
phase, attempt, and terminal outcome without exposing secrets or raw payloads.

### AC-7 - Preflight gate

The live validation runbook requires a successful bounded availability preflight
before a new live C investigation. A failed preflight blocks the provider call.

### AC-8 - Evidence completion guard

When retrieval is required, a result with no usable evidence is classified as
incomplete or failed for the product objective and cannot be reported as
semantically successful.

### AC-9 - Resilience tests

Tests cover response loss after submission, repeated same-key resume, key
conflict, unavailable service, expired authentication, task timeout, callback
failure, concurrent resume, and diagnostics redaction.

### AC-10 - Documentation

The MCP runbook, live validation procedure, and Story implementation report
document the recovery contract, the preflight gate, and the distinction between
technical completion and semantic success.

## Expected Validation

- Focused Core unit tests for timeout classification and idempotent resume.
- Focused MCP tests for transport and polling behavior.
- AI Engine tests for bounded resume and diagnostic handling where applicable.
- Integration tests for task submission, callback, timeout recovery, and
  snapshot observation.
- Concurrency tests for duplicate resume prevention.
- Redaction tests for logs and error responses.
- `git diff --check`.
- Full applicable backend, MCP, and AI Engine suites.
- One bounded live C validation only after the preflight gate passes.
- No broader provider campaign before the live C objective succeeds.

## Workflow and Gates

1. Repository Analysis of this Story, including DevLog availability diagnosis.
2. Human approval of the Repository Analysis.
3. Implementation Plan with explicit timeout and resume policy.
4. Human approval of the Implementation Plan.
5. Minimal implementation and focused validation.
6. Documentation reconciliation.
7. Independent code review and complete quality validation.
8. Human acceptance of the Story.

## Human Decisions Required

1. Confirm the maximum preflight duration and resume-attempt budget.
2. Confirm the authoritative retry owner for each timeout category.
3. Confirm whether an accepted submission must always return a resume identity
   synchronously or may expose it through a follow-up status lookup.
4. Confirm the service-unavailable threshold that blocks live validation.
5. Confirm the exact status returned when no task identity can be recovered.

## Initial Repository Analysis Questions

- Where exactly did the two DevLog MCP timeouts occur: transport, submission,
  polling, or provider execution?
- Was a task created despite the missing response?
- Which existing Story 0226 mechanisms already cover this behavior?
- Which layer owns retries for the relevant error category under ADR-081?
- Can the existing MCP contract expose a stable resume identity without a new
  architectural seam?
- Which source revision and freshness metadata must be checked before live C?
