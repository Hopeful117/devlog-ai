# Story 0228 - MCP Live Evaluation Preparation

## Status

IMPLEMENTATION AUTHORIZED - human authorization granted in the current session.

The Story definition and implementation are authorized. This authorization does
not authorize live provider execution, budget changes, commits, pushes or
merges.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Governing Artifacts

- [ADR-006](../../decisions/ADR-006.md) - AI Proposal and Knowledge Promotion Workflow
- [ADR-066](../../decisions/ADR-066.md) - Replayable AI Intent Evaluation Harness
- [ADR-067](../../decisions/ADR-067.md) - DevLog Engineering Story Context Agent
- [ADR-072](../../decisions/ADR-072.md) - Sign in with ChatGPT for User-Authorized AI Usage
- [ADR-073](../../decisions/ADR-073.md) - Core-Owned Binding Between Principal, AI Task and User Credential
- [ADR-081](../../decisions/ADR-081.md) - MCP Error Classification, Retry and Idempotency Policy
- [ADR-082](../../decisions/ADR-082.md) - MCP-Orchestrated Preparation of Bounded Live Evaluations
- [Story 0220](../0220-revision-pinned-runtime-loss-evaluation/story.md)
- [Story 0221](../0221-bounded-live-provider-captures/story.md)
- [Story 0223](../0223-agentic-quality-real-story-pilot/story.md)
- [Story 0227](../0227-devlog-context-availability-and-bounded-resume/story.md)

## Objective

Allow an authorized MCP consumer to prepare a bounded ABC live evaluation
without repeating manual readiness checks, while preserving Core authority,
OAuth boundaries, provenance, idempotency and the existing campaign
authorization gate.

The capability prepares an immutable, redacted execution plan. It does not
execute the provider evaluation.

## Problem

The provider-free ABC runner validates the manifest, oracle, normalization,
provenance and artifact integrity, but live preparation still requires manual
coordination of revision identity, worktree state, runtime contracts, OAuth
binding, operational limits and artifact destinations.

Manual preparation makes configuration drift difficult to diagnose and does not
provide one authoritative, replayable readiness plan. The current ChatGPT OAuth
context also does not expose a measurable monetary provider cost. The approved
non-metered contract must therefore be represented as `NOT_MEASURED`, never as
zero cost, while operational ceilings remain mandatory.

## Architectural Constraints

1. Java/Core owns capability semantics, project scope, benchmark identity,
   authorization checks, deterministic validation and plan authority.
2. MCP is a thin adapter over the Core capability and contains no duplicate
   preparation workflow.
3. OAuth identity and provider usage authorization remain distinct from project
   authorization.
4. OAuth credentials remain inside the AI Engine provider boundary and never
   enter plans, MCP responses, logs or diagnostics.
5. Preparation cannot create, renew or broaden a campaign authorization.
6. Preparation cannot execute a provider call or modify repository/production
   state.
7. A stale, mismatched, incomplete or ambiguous identity fails closed.
8. Error classification, retries and idempotency follow ADR-081.
9. Evaluation output and preparation metadata remain untrusted,
   evaluation-only artifacts.
10. The separate live runner must revalidate the plan, runtime identity,
    authorization and expiration immediately before execution.

## In Scope

### Core capability

- Define a versioned preparation request and response contract.
- Return exactly `READY`, `BLOCKED` or `NOT_AUTHORIZED`.
- Validate benchmark, case, manifest and oracle identity/digests.
- Validate source revision, runtime revision and reproducible worktree state.
- Validate A/B/C runtime identities, contracts, adapters and normalization.
- Validate operational budgets and stop conditions.
- Validate provider/model identity and non-secret OAuth/MCP readiness.
- Validate artifact destination isolation and retention metadata.
- Produce bounded diagnostics for every blocker.

### Preparation plan

- Persist or expose an authoritative immutable plan identity.
- Bind the plan to the authorized principal, project, benchmark and request
  digest.
- Bind the approved non-metered pricing contract and digest.
- Support exact replay with the same idempotency key and equivalent request.
- Return a deterministic conflict for the same key with a different request.
- Prevent concurrent duplicate plan creation.

The Repository Analysis must confirm the smallest repository-consistent
persistence representation. Existing `AiTask` execution records must not be
reused unless their semantics are proven compatible with preparation-only
plans.

### Backend transport

- Add the Core HTTP adapter consumed by `mcp-server`.
- Propagate authenticated principal and delegation scope.
- Map authentication, authorization, conflict, rate-limit and infrastructure
  failures according to ADR-081.
- Keep responses bounded and redacted.

### MCP adapter

Add a thin tool such as `prepare_live_evaluation` with inputs for the explicitly
selected benchmark/observation set, operational policy and idempotency key.
The adapter calls Core, serializes the canonical plan and does not implement
business validation.

### Python runner boundary

- Accept a prepared plan as an input to the live path.
- Verify the plan digest and local runtime/worktree identity.
- Refuse expired, changed or incomplete plans.
- Preserve the existing final campaign-authorization validation.
- Keep Core as the authority for authorization and scope decisions.
- Keep preparation at zero provider calls and zero external provider network
  calls.

### Documentation

- Update the ABC live runbook with the preparation workflow.
- Document the distinction between `READY`, technical blockers and
  `NOT_AUTHORIZED`.
- Document the non-metered OAuth contract and operational ceilings.
- Document plan replay, conflict and pre-execution revalidation behavior.

## Out of Scope

- Autonomous provider execution.
- Campaign authorization creation, renewal or broadening.
- OAuth consent or reauthorization.
- Provider fallback or credential sharing.
- Budget expansion or retry-policy changes.
- Modification of the ABC manifest or oracle.
- Semantic scoring or human qualitative adjudication.
- RAG, retrieval, prompt or trust-boundary changes.
- Frontend changes.
- CI live execution.
- Promotion of evaluation output to trusted knowledge.

## Preparation Result Contract

The result must distinguish:

```text
technical readiness
authorization readiness
semantic evaluation status
```

The top-level preparation status is one of:

```text
READY
BLOCKED
NOT_AUTHORIZED
```

The result includes only bounded metadata such as identities, digests, limits,
capability envelope, artifact reference and blocker codes. It must not include
credentials, tokens, prompts, raw provider payloads, full repository content or
unbounded diagnostics.

## Non-Metered OAuth Contract

For ChatGPT OAuth, the implementation must bind the approved contract:

```text
billingModel       = CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED
currency           = NOT_APPLICABLE
costCeilingPolicy  = OPERATIONAL_LIMITS_ONLY
input/output rates = absent
```

`NOT_MEASURED` means that monetary cost is not observable through this provider
boundary. It must never be converted to zero or used to remove operational
ceilings.

Operational ceilings remain mandatory for observations, repetitions, model
turns, provider actions, output tokens, wall-clock duration, model/provider,
authorization expiration and artifact retention/destination.

## Acceptance Criteria

Implementation note: the current Core boundary has no independent worktree
attestation verifier. Therefore valid preparation requests must persist a
redacted `BLOCKED` plan until that verifier is introduced; a caller-supplied
attestation cannot produce `READY`.

- [ ] A valid request produces an authoritative plan with a stable identity.
- [ ] The result exposes only `READY`, `BLOCKED` or `NOT_AUTHORIZED`.
- [ ] Source/runtime revision mismatch blocks preparation.
- [ ] A non-reproducible worktree blocks preparation.
- [ ] Invalid manifest, oracle, contract or digest blocks preparation.
- [ ] The approved non-metered OAuth contract is accepted as `NOT_MEASURED`,
      never as zero cost.
- [ ] Operational ceilings remain mandatory when monetary cost is unavailable.
- [ ] Credentials, tokens, prompts, provider payloads and repository content are
      absent from plans, responses, logs and diagnostics.
- [ ] Equivalent replay with the same idempotency key returns the existing plan.
- [ ] Reuse of a key for a different canonical request returns a deterministic
      conflict.
- [ ] Concurrent equivalent submissions create one plan only.
- [ ] Preparation performs zero provider calls and zero external provider
      network calls.
- [ ] The live runner rejects an expired or digest-mismatched plan before any
      provider call.
- [ ] Live execution remains blocked without the existing campaign authorization.
- [ ] No production budget, retry, retrieval, trust or authority behavior changes.
- [ ] Focused Core, MCP and Python tests cover the contract and failure paths.
- [ ] The runbook documents preparation, replay, blockers and revalidation.

## Expected Validation

### Backend

- Contract and validation unit tests.
- Revision, worktree, digest and pricing tests.
- Idempotency, key conflict and concurrency tests.
- Immutable plan and diagnostic redaction tests.
- Core integration tests for `READY`, `BLOCKED` and `NOT_AUTHORIZED`.

### MCP Server

- Authentication and delegation propagation tests.
- Core call and response serialization tests.
- Error mapping tests for `401`, `403`, `409`, `429` and `5xx`.
- Secret and unbounded-diagnostic redaction tests.

### AI Engine

- Prepared-plan digest and expiration validation.
- Runtime/worktree mismatch rejection.
- Campaign-authorization separation tests.
- Zero provider/network calls during preparation.

### Integration

- Successful preparation with a fresh plan.
- Exact idempotent replay.
- Concurrent duplicate preparation.
- Conflicting key reuse.
- Revision, pricing, OAuth readiness and artifact isolation blockers.
- Pre-execution revalidation failure.

## Human Decisions Required

1. Confirm the canonical Story number and directory name.
2. Approve ADR-082 before implementation planning advances.
3. Confirm the durable representation of the preparation plan.
4. Confirm whether a public REST adapter is required in this Story or whether
   the Core HTTP adapter consumed by MCP is sufficient.
5. Confirm preparation-plan retention and cleanup policy.
6. Confirm whether blocked plans are persisted or returned without persistence.
7. Confirm the exact OAuth/MCP readiness states exposed to the caller.

## Gates

1. Story definition approval.
2. ADR-082 acceptance.
3. Repository Analysis.
4. Repository Analysis approval.
5. Implementation Plan.
6. Implementation Plan approval.
7. Explicit implementation authorization.
8. Focused implementation and quality validation.
9. Documentation reconciliation.
10. Independent review.
11. Human Story acceptance.

This Story does not authorize provider execution, campaign authorization,
commit, push, merge or final acceptance.

## References

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)
- [ADR-066](../../decisions/ADR-066.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-072](../../decisions/ADR-072.md)
- [ADR-073](../../decisions/ADR-073.md)
- [ADR-081](../../decisions/ADR-081.md)
- [ADR-082](../../decisions/ADR-082.md)
- [Story 0220](../0220-revision-pinned-runtime-loss-evaluation/story.md)
- [Story 0221](../0221-bounded-live-provider-captures/story.md)
- [Story 0223](../0223-agentic-quality-real-story-pilot/story.md)
- [Story 0227](../0227-devlog-context-availability-and-bounded-resume/story.md)
- [ChatGPT OAuth pricing contract](../../evaluation/devlog-ai-q1-chatgpt-oauth-pricing-draft-v1.json)
