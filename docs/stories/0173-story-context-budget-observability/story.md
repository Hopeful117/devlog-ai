# Story 0173 - Story Context Budget Policy and Provider Failure Diagnostics

## Status

**IMPLEMENTATION COMPLETE - AWAITING CODE REVIEW**

This Story is derived from the investigation and ADR-079. The human project
owner approved the Story, ADR-079, Repository Analysis and Implementation Plan
on 2026-10-31. Implementation is complete within the approved scope and awaits
independent code review.

## Problem

The Story Context Agent can reduce its authorized context at the Core selection
stage and the provider can independently terminate generation at its output
ceiling. This is a recurring multi-layer budget problem, not a single prompt
or MCP request defect. The runtime must make a provider call only when the
effective input, output and model-capacity policy can be shown to be safe.

The latest investigation reproduced both conditions: a projection with hundreds
of candidates reduced to the Core budget, and a ChatGPT
`response.incomplete(max_output_tokens)` failure.

## Goal

Make Story Context executions policy-safe and diagnostically complete enough to
distinguish:

- candidate selection truncation;
- projection or prompt-size exhaustion;
- provider output exhaustion;
- provider transport failure;
- response parsing failure;
- authoritative validation failure.

Preserve fail-closed behavior and the existing Java/Core authority boundary.
The policy must apply consistently to every Story Context execution, regardless
of transport, intent or requested scope.

## Governing Artifacts

- Proposed ADR-079: Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- ADR-006: AI Proposal and Knowledge Promotion Workflow
- ADR-067: DevLog Engineering Story Context Agent
- ADR-069: Canonical Engineering Context Boundary
- Story 0112: DevLog Engineering Story Context Agent

Implementation must not begin until the human approves this Story and the
associated Repository Analysis and Implementation Plan gates.

## In Scope

- Define versioned, bounded budget-accounting fields for Core snapshots and AI
  interaction traces.
- Propagate selection accounting and projection identities through the existing
  Story Context execution.
- Measure serialized projection/prompt size where technically reliable.
- Record effective provider model, output limit, response status and bounded
  incomplete reason.
- Record a terminal execution phase and normalized failure category.
- Add a preflight check that reports insufficient or unknown budget capacity
  before provider invocation, without silently increasing limits. Unknown
  capacity is fail-closed with a normalized `BUDGET_CAPACITY_UNKNOWN` diagnostic.
- Make the preflight an explicit Core authorization decision for every provider
  call, with outcomes for authorized, deterministically reduced projection and
  rejected execution.
- Define the effective provider/model output policy as versioned configuration;
  production budget changes remain separate, measured policy decisions.
- Expose an actionable, non-sensitive failure message to the caller.
- Add metrics with bounded labels for truncation, preflight rejection, provider
  incomplete response, parsing failure and validation failure.
- Add deterministic unit, contract and integration/evaluation scenarios for each
  failure class.
- Define the versioned/additive wire contract, mixed-version behavior and rollout
  compatibility for all new fields before implementation.

## Out of Scope

- RAG, embeddings, vector search or a new retrieval engine.
- Python-side repository access or context reconstruction.
- Generic agent loops, autonomous follow-up retrieval or persistent memory.
- Automatic increase of production context/output limits.
- Provider credential, OAuth or MCP transport changes.
- Parsing, persisting or retrying incomplete provider output as a valid result.
- Promotion of any AI output to trusted knowledge.

## Invariants

1. Core remains authoritative for scope, selection, trust, grounding and context
   identity.
2. `truncated=true` remains distinct from provider failure.
3. `response.incomplete` is terminal failure for the current execution.
4. Incomplete output is never parsed or persisted as a successful analysis.
5. Diagnostics contain no credentials, tokens, full prompts, repository payloads
   or raw model output in normal operation.
6. Existing context, projection and grounding digests remain authoritative and
   are not replaced by provider-generated identities.

## Acceptance Criteria

### AC-1 - Selection truncation is explainable

An execution snapshot and trace expose candidate, selected, discarded, budget,
usage, warning and `truncated` values. A test proves that Core truncation alone
is not classified as provider failure.

### AC-2 - Provider incomplete response is explainable

A deterministic provider fixture simulating `response.incomplete` with reason
`max_output_tokens` is recorded with a bounded provider failure category,
terminal phase, effective output limit and no successful analysis result. A
ChatGPT live reproduction is optional and environment-dependent.

### AC-3 - Budget layers are separated

Tests distinguish selection budget, serialized prompt/input measurement, provider
output allowance and model capacity. Every measured value has an explicit unit
and certainty status (`EXACT`, `ESTIMATED` or `UNKNOWN`). No value is inferred
from `max_output_tokens` as actual usage.

### AC-4 - Preflight is fail-closed

When a safe budget cannot be established, including unknown model capacity, the
execution fails before provider invocation with an actionable
`BUDGET_CAPACITY_UNKNOWN` or equivalent normalized preflight diagnostic. A
provider-side incomplete response remains a separate terminal provider failure
when the provider is the only authority capable of reporting it.

### AC-4a - Provider calls are policy-authorized

Every Story Context provider call has a recorded Core preflight outcome. The
preflight uses the effective serialized input, output allowance, safety margin
and model capacity. It cannot silently raise limits, broaden context, or be
bypassed by Python, MCP or REST. An insufficient configured output allowance is
rejected with a bounded actionable diagnostic rather than discovered only after
provider truncation.

### AC-5 - Diagnostics are safe

Redaction and bounded-size tests prove that credentials, authorization headers,
delegation proofs, prompts, repository content and raw incomplete output are not
persisted in normal diagnostics. The implementation distinguishes the immutable
Core-authorized execution snapshot, which may contain the canonical context and
projection required for grounding/audit, from normal traces and short-lived
diagnostic traces. Retention and access rules for each artifact are tested.

### AC-6 - Existing contracts remain compatible

REST, MCP, callback identity, grounding validation and Story Context projection
contracts preserve their current authority and digest semantics. Any new wire
fields use a versioned Story Context envelope or an additive contract accepted
by both sides. Tests cover old/old, new/new, new/old and old/new Core/AI Engine
combinations; unsupported combinations fail closed with a version diagnostic.

### AC-7 - Provider coverage is explicit

All configured providers have a normalized terminal diagnostic contract. The
common fields are tested for mock, OpenAI-compatible, Anthropic, Gemini,
OpenRouter and ChatGPT adapters where those providers are supported by the
repository. Provider-specific identifiers or incomplete reasons are optional;
unavailable values must be represented as `UNKNOWN`, never inferred.

### AC-8 - Deterministic reproduction does not require live credentials

CI can reproduce Core truncation, unknown-capacity preflight rejection and
provider-incomplete output using deterministic fixtures without network access or
real credentials. Live provider validation is optional and separately marked as
environment-dependent.

### AC-9 - Live reproduction is measurable

A documented test procedure can reproduce both a deliberately truncated Core
projection and a provider `max_output_tokens` interruption, and can correlate
the result to one task, context digest and projection digest.

### AC-10 - No budget tuning is smuggled into instrumentation

The implementation report explicitly records whether production budgets changed.
Any proposed budget increase is separately justified with measured quality,
latency and cost evidence.

### AC-11 - Policy applies across transports and intents

Equivalent Story Context executions through REST and MCP use the same Core
preflight and provider-budget policy. Tests cover a broad request, a targeted
request and a deliberately reduced projection without introducing a
transport-specific or intent-specific bypass.

## Expected Validation

- AI Engine focused provider and interaction-trace tests.
- Backend Story Context projection, callback and persistence tests.
- Contract tests for additive diagnostic metadata.
- Evaluation scenarios for complete, truncated, preflight-rejected and provider-
  incomplete executions.
- Redaction and `git diff --check` validation.
- Full relevant module suites after focused tests pass.

## Documentation Impact

If accepted and implemented, update the canonical operator/runtime documentation
with the diagnostic fields, failure categories, safe reproduction procedure and
retention rules. Do not add speculative troubleshooting instructions before the
runtime contract exists.

## Risks

- Prompt-size measurement may vary by provider tokenizer.
- Persisting too much diagnostic detail could leak repository-sensitive content.
- Increasing budgets without a quality benchmark could increase cost while
  worsening output completion.
- Adding labels with unbounded project, intent or error values could create metric
  cardinality problems.

## Human Gate

Human review is required for:

1. acceptance of ADR-079;
2. approval of this Story;
3. Repository Analysis approval;
4. Implementation Plan approval.

No implementation, commit, push, Story acceptance or deployment authorization is
implied by this document.
