# Story 0175 - Story Context Complex Response Completion Policy

## Status

**PROPOSED - AWAITING HUMAN REVIEW**

This Story is created from the follow-up investigation of Story 0173. It is not
implementation authorization. Repository Analysis, Implementation Plan,
implementation, acceptance and deployment remain subject to the established
human approval gates.

## Dependency

Implementation must start only after:

- Story 0173 - Story Context Budget Policy and Provider Failure Diagnostics has
  been reviewed and its current runtime behavior is accepted or explicitly
  superseded;
- the current MCP and AI Engine runtime configuration has been captured;
- the human project owner explicitly authorizes this Story's Repository Analysis
  and Implementation Plan gates.

## Problem

Story 0173 correctly distinguishes Core selection truncation from provider
failure and records ChatGPT `response.incomplete` diagnostics. It does not,
however, guarantee that a complex Story Context request can produce a complete
structured response.

The current MCP path can pass Core preflight while the AI Engine still sends a
provider output allowance of `2,000` tokens. A complex analysis may then reach
ChatGPT and terminate with:

```text
response.incomplete; reason=max_output_tokens
```

The result is correctly classified as a terminal failure, but the caller still
does not receive a usable response. The existing preflight proves that the
configured allowance fits the model context capacity; it does not prove that
the allowance is sufficient for the requested response complexity.

## Evidence

- Story 0173 implementation reports that live provider reproduction was not
  executed and Python behavioral tests were unavailable because `pytest` was
  not installed.
- `StoryContextBudgetPreflight` uses the effective output allowance and model
  capacity, but the default allowance remains `2,000` tokens.
- The ChatGPT adapter sends `max_output_tokens` from the authorized parameters
  or its configured default and treats `response.incomplete` as terminal.
- A complex DevLog MCP request reproduced
  `LLM_PROVIDER_ERROR: response.incomplete; reason=max_output_tokens` after
  Story 0173.

## Goal

Define and implement a measured, Core-authorized policy for complex Story
Context responses so that the MCP capability either:

1. produces a complete structured response within a validated output policy; or
2. rejects the request before provider invocation with a bounded, actionable
   diagnostic explaining that the requested response cannot safely fit the
   configured policy.

The solution must not silently increase production limits, weaken grounding,
parse incomplete output or move budget authority into Python or MCP.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0112 - DevLog Engineering Story Context Agent
- Story 0173 - Story Context Budget Policy and Provider Failure Diagnostics
- `docs/investigations/story-context-truncation-and-provider-incomplete-response.md`

## In Scope

- Measure and classify response-complexity requirements for Story Context
  intents, guidance and requested scope using deterministic bounded policy
  inputs.
- Define a versioned output-completion policy owned by Java/Core.
- Ensure the effective output allowance used by Core preflight is exactly the
  allowance sent by the AI Engine provider adapter.
- Establish a measured policy for supported complex responses, including model,
  provider, output allowance, completion rate, latency, cost and quality
  evidence.
- Reject requests before provider invocation when the completion policy cannot
  establish a safe supported response path.
- Preserve provider-incomplete responses as terminal failures when the provider
  remains the only authority able to report the failure.
- Add deterministic fixtures and behavioral tests for broad, targeted and
  reduced Story Context requests through both REST and MCP.
- Add a credential-free or separately marked live reproduction procedure that
  correlates task, context digest and projection digest.
- Expose bounded caller diagnostics that distinguish policy rejection from
  provider interruption.
- Update canonical operator documentation with the final policy and recovery
  guidance.

## Out of Scope

- Arbitrarily increasing production context or output limits.
- Retrying or parsing incomplete provider output as a successful result.
- Automatic autonomous follow-up retrieval, RAG, embeddings or vector search.
- Python-side context construction, selection or budget authorization.
- Changing MCP bearer renewal, delegation or authentication semantics.
- Moving ChatGPT credentials outside the AI Engine provider boundary.
- Promoting AI output to trusted knowledge.
- Introducing a generic agent loop or transport-specific workaround.

## Invariants

1. Java/Core remains authoritative for scope, selection, grounding, budget
   policy and provider-call authorization.
2. MCP and REST invoke the same Core Story Context capability and cannot bypass
   the completion policy.
3. The effective output allowance is single-valued and traceable from Core
   preflight through the provider request.
4. `truncated=true` remains distinct from output-policy rejection and provider
   failure.
5. `response.incomplete` remains terminal for the current execution.
6. Incomplete output is never parsed, persisted as a successful analysis or
   promoted to trusted knowledge.
7. No production budget increase is introduced without measured quality,
   latency and cost evidence and separate human review.
8. Diagnostics contain no credentials, authorization headers, full prompts,
   repository payloads or raw provider output.
9. Unknown capacity, unknown policy inputs or unsupported provider behavior fail
   closed with bounded diagnostics.

## Acceptance Criteria

### AC-1 - Effective allowance is consistent

For every Story Context execution, tests prove that the output allowance used by
Core preflight is the same value sent by the AI Engine provider adapter. A
missing, altered or unsupported allowance fails closed and is never silently
replaced by a provider default.

### AC-2 - Complex requests have an explicit completion policy

Broad, targeted and reduced projections have deterministic policy outcomes. Each
outcome is one of `AUTHORIZED`, `REJECTED` or an explicitly documented bounded
policy result. The outcome records the policy version, provider, model, output
allowance and measurement certainty without storing prompt or repository
content.

### AC-3 - Insufficient policy is rejected before invocation

When the configured output allowance cannot satisfy the approved completion
policy for the request, Core rejects the execution before provider invocation
with a bounded actionable diagnostic. The test proves that no provider call is
made and no incomplete result is persisted.

### AC-4 - Validated complex responses complete

For at least one supported provider/model policy, a deterministic fixture and a
separately marked live or replayable evaluation prove that a representative
complex request returns a complete structured response within the approved
allowance. The evidence records completion, latency, cost and quality measures.

### AC-5 - Provider interruption remains distinct

A fixture for ChatGPT `response.incomplete` with
`incomplete_details.reason=max_output_tokens` remains a terminal provider
failure when Core cannot know the provider's internal completion decision. The
result contains bounded phase, provider status, reason, effective allowance and
task/correlation identity, and contains no successful analysis result.

### AC-6 - No silent budget tuning

Tests and the implementation report prove that no production output or context
limit was increased merely to make the reproduction pass. Any proposed policy
change is documented with measured completion, latency, cost and quality
evidence and remains separately reviewable.

### AC-7 - Transport parity

Equivalent broad, targeted and reduced requests through REST and MCP produce the
same Core policy outcome, diagnostic category and budget identities. No
transport-specific bypass or workaround exists.

### AC-8 - Contract compatibility

The additive/versioned contract behavior is tested for old/old, new/new,
new/old and old/new Core/AI Engine combinations. Unsupported completion-policy
combinations fail closed with a version diagnostic.

### AC-9 - Safe diagnostics and documentation

Redaction tests prove that credentials, delegation proofs, full prompts,
repository content and raw provider output do not enter normal diagnostics.
Canonical operator documentation describes policy rejection, provider
interruption and safe recovery procedures.

### AC-10 - Reproducible validation

CI reproduces policy authorization, policy rejection and provider-incomplete
paths without live credentials. The live or replayable reproduction procedure
correlates one execution using task ID, context digest and projection digest.

## Expected Validation

- Focused Java tests for Core completion-policy calculation and provider-call
  authorization.
- AI Engine tests for exact propagation of `maxOutputTokens` and ChatGPT
  incomplete handling.
- REST/MCP contract and transport-parity tests.
- Deterministic evaluation fixtures for complete, rejected and incomplete paths.
- Live or replayable complex-request measurement, explicitly marked as
  environment-dependent when applicable.
- Redaction tests and `git diff --check`.
- Full relevant backend, MCP and AI Engine suites after focused tests pass.

## Risks

- A larger output allowance may increase cost or latency without improving
  answer quality.
- Response complexity cannot be inferred safely from raw prompt length alone.
- Provider tokenization and structured-output overhead vary by provider.
- A policy that is too conservative may reject useful requests unnecessarily.
- A policy that is too permissive may continue to produce incomplete responses.
- Tests that cover only provider fixtures may miss configuration drift in the
  live MCP path.

## Human Gate

Human review is required for:

1. acceptance of the Repository Analysis;
2. approval of the Implementation Plan;
3. any production output-policy or budget change;
4. Story acceptance.

Creating this Story does not authorize implementation, commit, push, deployment
or Story acceptance.
