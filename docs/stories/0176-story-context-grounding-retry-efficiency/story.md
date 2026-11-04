# Story 0176 - Story Context Grounding and Retry Efficiency

## Status

**PROPOSED - AWAITING HUMAN REVIEW**

This Story is derived from the post-0175 runtime investigation. It is not
implementation authorization. Repository Analysis, Implementation Plan,
implementation, acceptance and deployment remain subject to the established
human approval gates.

## Problem

Story 0175 resolved the insufficient completion allowance for complex Story
Context responses. The current runtime now commonly authorizes `8192` output
tokens and recent live executions complete successfully.

The remaining dominant failure mode is grounding validation. In the measured
24-hour window, Story Context produced:

| Outcome | Count | Average duration | P95 duration |
|---|---:|---:|---:|
| `SUCCEEDED` | 37 | 13.2 s | 26.5 s |
| `GROUNDING_ERROR` | 39 | 28.5 s | 44.5 s |
| `PROVIDER_INCOMPLETE` | 2 | 25.3 s | 25.4 s |

Grounding failures normally invoke the existing corrective retry. They
therefore consume an additional provider turn, increase latency, and still
finish as failures when the retry cannot produce an accepted grounded result.

The current normal traces do not preserve the detailed grounding validation
diagnostic. The system consequently cannot distinguish whether the dominant
failure is caused by an unknown reference, a missing relationship type,
requested-file scope mismatch, causal-support validation, or another contract
rule.

The prompt also contains a large authorized projection and a strict structured
output contract. Recent executions use approximately 60 selected items from a
candidate universe of about 470, with an input estimate around 86,000 tokens.
The investigation has not yet established which prompt sections are causally
responsible for grounding failures or provider latency.

## Goal

Reduce avoidable Story Context failures and response latency by:

1. making grounding failures diagnostically actionable;
2. measuring the cost and outcome of corrective retries;
3. improving prompt alignment with the Core grounding contract and requested
   file scope;
4. reducing unnecessary context and retry work without weakening grounding,
   trust, or fail-closed behavior.

The Story must produce evidence for any prompt or context reduction. It must
not trade correctness for a lower error rate.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0112 - DevLog Engineering Story Context Agent
- Story 0173 - Story Context Budget Policy and Provider Failure Diagnostics
- Story 0175 - Story Context Complex Response Completion Policy
- `docs/investigations/story-context-truncation-and-provider-incomplete-response.md`

## In Scope

- Add bounded, non-sensitive diagnostics identifying the grounding validation
  rule that failed.
- Preserve grounding diagnostics in interaction traces and bounded caller
  diagnostics without storing credentials, full prompts, repository payloads or
  raw provider output.
- Measure initial generation, corrective retry, total execution duration and
  provider token usage when supplied.
- Establish a deterministic taxonomy for grounding failures, including:
  - unknown or unauthorized evidence reference;
  - requested-file scope mismatch;
  - missing or invalid `relationType`;
  - causal evidence or classification mismatch;
  - invalid grounding contract or mapping;
  - other bounded structural grounding violations.
- Evaluate the current requested-file scope prompt behavior against targeted,
  broad and reduced projections.
- Evaluate bounded reductions of redundant prompt material or context only when
  Core authorization, grounding identities and projection semantics remain
  unchanged.
- Measure corrective-retry success rate, latency, provider calls and token
  usage before and after any prompt or validation change.
- Define whether a deterministic grounding failure should receive a corrective
  retry, with evidence and explicit compatibility tests.
- Add deterministic fixtures and replayable evaluation cases for successful
  grounding, requested-file mismatch, unknown references, relation-type errors,
  causal grounding errors and corrective-retry outcomes.
- Document the resulting operator diagnostics and measurement semantics.

## Out of Scope

- Increasing production output or context budgets.
- Parsing, persisting or retrying incomplete provider output as successful
  analysis.
- Weakening Core grounding validation or allowing untrusted references.
- Python-side context construction, selection, retrieval or authorization.
- RAG, embeddings, vector search, autonomous follow-up retrieval or generic
  agent loops.
- Changing MCP authentication, delegation, credential ownership or polling
  timeout as a substitute for fixing grounding failures.
- Removing corrective retries solely to improve latency without measured
  correctness evidence.
- Modifying unrelated uncommitted prompt, scenario or Playwright files unless
  they are explicitly included in the approved Implementation Plan.

## Invariants

1. Java/Core remains authoritative for scope, selection, grounding, trust,
   budget policy and provider-call authorization.
2. Every accepted factual or interpretive finding remains grounded in an
   authorized Core reference.
3. Requested file scope can restrict accepted evidence but cannot authorize a
   reference absent from the Core grounding contract.
4. `truncated=true`, grounding failure, provider interruption, parsing failure
   and validation failure remain distinct outcomes.
5. Incomplete provider output remains terminal and is never parsed or retried
   as valid output.
6. Corrective retry behavior remains bounded and observable.
7. Normal diagnostics contain no credentials, authorization headers, full
   prompts, repository payloads or raw model output.
8. Prompt or context reduction cannot create a second retrieval universe,
   replace projection identities or move context authority into Python.
9. AI output remains untrusted and cannot be promoted directly to trusted
   knowledge.

## Acceptance Criteria

### AC-1 - Grounding failures are actionable

Every `GROUNDING_ERROR` trace contains a bounded diagnostic category and stable
phase. The category identifies the failed validation family without exposing
full model output or repository content. The caller receives a bounded,
non-sensitive failure classification.

### AC-2 - Retry cost is measurable

For each Story Context execution, the system records the initial attempt,
corrective retry when present, total duration, provider call count and token
usage when supplied. The implementation distinguishes a successful first
attempt, successful corrective retry and failed corrective retry.

### AC-3 - Grounding taxonomy is covered

Deterministic tests cover unknown references, requested-file scope mismatch,
missing or invalid relationship type, causal grounding mismatch, invalid Core
grounding mapping and at least one successful grounded response. Each fixture
maps to exactly one bounded primary failure category.

### AC-4 - Prompt alignment is evaluated

Targeted, broad and reduced projections are evaluated with the current prompt
and any proposed prompt change. The evaluation records prompt/input estimate,
selected and discarded counts, grounding outcome, retry outcome, latency and
quality/regression checks. No prompt change is accepted based on latency alone.

### AC-5 - No correctness regression

The proposed behavior does not accept unauthorized references, unsupported
relationship claims, invalid causal classifications or ungrounded factual
findings. Existing fail-closed and truncation-safety tests continue to pass.

### AC-6 - Retry policy is evidence-based

The Story explicitly documents which grounding failures remain eligible for a
corrective retry and which deterministic failures are rejected without a second
provider call. The decision is supported by measured success, latency and
provider-call evidence.

### AC-7 - Provider completion remains distinct

A ChatGPT `response.incomplete(max_output_tokens)` fixture remains a terminal
provider failure and is not classified as a grounding failure. Story 0175's
effective output allowance propagation remains unchanged.

### AC-8 - Transport parity

Equivalent REST and MCP executions use the same grounding validation,
diagnostic taxonomy and retry policy. No transport-specific prompt or retry
workaround is introduced.

### AC-9 - Safe documentation

Canonical operator documentation explains grounding failure categories, retry
semantics, available measurements and safe recovery. It does not expose prompts,
repository payloads, credentials or raw provider output.

### AC-10 - Reproducible validation

CI or replayable evaluation covers successful first attempts, grounding failure
with retry, grounding failure without retry, provider incomplete response and
truncated projections. A separately marked live validation correlates task ID,
context digest and projection digest when credentials are available.

## Expected Validation

- Focused AI Engine validation and interaction-trace tests.
- Backend contract and persistence tests for bounded grounding diagnostics.
- REST/MCP transport-parity tests.
- Prompt-size and token-accounting evaluation for broad, targeted and reduced
  requests.
- Deterministic corrective-retry fixtures.
- Full relevant backend and AI Engine suites when dependencies are available.
- `git diff --check` and review of diagnostic redaction boundaries.

## Risks

- More detailed diagnostics may leak repository-sensitive information if not
  bounded and redacted.
- Reducing context may lower grounding error rates while hiding relevant
  evidence; quality must be measured separately.
- Removing retries may improve latency but reduce recoverable success.
- Provider token usage may remain unavailable for some adapters.
- Prompt token estimates may not match provider tokenization.

## Human Gate

Human review is required for:

1. acceptance of this Story;
2. approval of the Repository Analysis;
3. approval of the Implementation Plan;
4. any change to production prompt, context or retry policy;
5. Story acceptance.

Creating this Story does not authorize implementation, commit, push, deployment
or Story acceptance.
