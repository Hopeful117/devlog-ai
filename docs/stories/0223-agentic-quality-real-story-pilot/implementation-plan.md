# Implementation Plan - Story 0223

## Status

IMPLEMENTED - human acceptance pending.

The Story definition and Repository Analysis are approved. This plan defines
the provider-free implementation boundary and the authorization boundary for
any live pilot. It does not authorize provider calls, paid experiments,
production changes, prompt changes, budget changes, retrieval changes, commits,
or pushes.

## Overview

This plan creates a new versioned evaluation protocol for the final epic pilot.
It reuses compatible Story 0133 primitives without changing Story 0133's
accepted three-question, two-treatment semantics.

The implementation first delivers deterministic provider-free readiness for:

```text
8 questions x 3 treatments x 3 repetitions = 72 assignments
```

The three treatments are:

- `DEVLOG_CONTEXT`;
- `AGENT_DIRECT`;
- `STORY_CONTEXT_INVESTIGATION`.

Live technical validation and the quality campaign remain separately gated
operations. Their interfaces may be validated offline, but no provider call is
made by this implementation unless a later explicit authorization is granted.

## Fixed Policy

- Story 0133 assets and semantics remain unchanged.
- Story 0223 receives a new protocol version, manifest identity, and pairing
  compatibility key.
- The Q1-Q8 benchmark remains proposed until human oracle review is recorded.
- Provider-free readiness is required before any live validation.
- One technical validation per treatment is the maximum initial live step.
- A technical failure stops the live phase; it does not trigger tuning, retry,
  campaign expansion, or a quality conclusion.
- Missing, invalid, provider-incomplete, and technically ineligible observations
  remain visible and outside semantic quality denominators.
- Human utility is not calculated by the automated evaluator.
- No production code, prompt, retrieval, budget, provider policy, or authority
  boundary changes are included.

## Planned Changes

### 1. Create an isolated protocol package

Add a Story 0223-specific package under `ai-engine/evaluations/` rather than
extending Story 0133 in place.

The package should define:

- protocol and evaluator versions;
- treatment identities and compatibility keys;
- question, revision, oracle, model, and configuration identities;
- assignment matrix generation;
- missing and execution states;
- raw observation and immutable artifact contracts;
- pairing and completeness views;
- bounded report projections.

The package may import generic hashing and safe serialization helpers from Story
0133 only where their behavior is unchanged. Any three-treatment behavior must
be implemented in the new package.

### 2. Freeze the 72-cell assignment model

Create a versioned campaign manifest containing:

- Q1-Q8 question IDs and versions;
- repository identity and revision;
- benchmark and scoring contract versions;
- oracle reference and review status;
- three treatment policy identities;
- three repetitions;
- deterministic assignment IDs;
- campaign and compatibility hashes.

The manifest loader must fail closed on duplicate questions, duplicate
assignments, missing versions, mutable revisions, unknown treatments, or an
oracle status that is not explicitly classified.

Proposed oracles may be used for provider-free contract fixtures, but the
manifest must expose that they are not approved for official semantic claims.

### 3. Define isolated treatment policies

Create separate policy manifests for:

#### `DEVLOG_CONTEXT`

- Core-authorized context projection only;
- no direct repository tools;
- no oracle or expected-answer data;
- no other treatment output or trace.

#### `AGENT_DIRECT`

- pinned repository revision;
- read-only operations only;
- no DevLog, MCP context, oracle, web, external memory, or mutable checkout;
- bounded operation, byte, model-turn, and output limits.

#### `STORY_CONTEXT_INVESTIGATION`

- Core-authorized Story 0184 actions only;
- action ID, revision, scope, usage, evidence, and callback identity required;
- no direct Python repository or database access;
- separate action and terminal-outcome accounting.

The policy validator must detect treatment contamination before a provider input
or capture is considered valid.

### 4. Define immutable raw observations

Implement a bounded raw observation envelope containing:

- observation and assignment identity;
- question, treatment, repetition, benchmark, and policy versions;
- repository identity and revision;
- model and configuration identity;
- exact bounded input and raw output references;
- bounded tool/action trace;
- provider and terminal outcome;
- oracle reference without oracle answers;
- raw input/output digests;
- usage, latency, and cost states;
- run identity and timestamp;
- execution class and baseline eligibility.

Raw observations must be write-once and hash-bound. The validator must reject
credentials, bearer tokens, unbounded repository content, raw incomplete output,
and unsupported sensitive fields.

### 5. Implement three-treatment completeness and pairing

The completeness view must expose all 72 assignment cells with:

- `ASSIGNED`;
- `EXECUTED`;
- `SEMANTIC_ELIGIBLE`;
- `INVALID`;
- `MISSING`.

It must calculate:

- assigned count;
- executed count;
- eligible count;
- invalid count;
- missing count;
- complete and incomplete pairing counts;
- execution completeness.

Pairing must compare shared identity fields across all three treatments without
assuming that treatment-specific traces or context metadata are identical.
Missing and invalid treatment observations remain visible and must not be
silently dropped.

### 6. Reuse Story 0222 quality projection

The report adapter should consume Story 0222's abstention and ordinal confidence
projection where compatible. It must preserve:

- correct, missed, and unnecessary abstention;
- confidence safety and reviewed-oracle gating;
- `NOT_EVALUATED` semantics;
- answer-class and required-evidence validation.

The adapter must not duplicate Story 0222 classification logic or turn proposed
oracle output into accepted quality evidence.

### 7. Define live authorization boundaries

Implement offline-validatable authorization metadata for the technical
validation and pilot phases:

- provider authorization identity;
- campaign identity;
- approved treatment set;
- approved question/repetition range;
- repository revision;
- retention/redaction policy;
- maximum permitted provider calls;
- stop conditions.

The provider-free test suite must verify that no live execution occurs without
the required authorization state. The implementation must not invoke a provider
as part of normal tests, manifest loading, or report generation.

### 8. Produce neutral reports

Create structured report projections that include quality values only with their
technical denominators:

- assignment and execution completeness;
- semantic eligibility;
- grounded correctness;
- claim and evidence support;
- abstention correctness;
- confidence-level conditional results;
- technical failure categories;
- reviewer agreement and adjudication state;
- treatment and campaign identities.

The report must identify proposed/unreviewed oracle results as non-official and
must not emit treatment-superiority or rollout recommendations automatically.

## Expected Files to Create

- `ai-engine/evaluations/comparative_pilot_v1/` package for Story 0223
  protocol, models, validation, assignment, completeness, and reporting.
- Versioned question/campaign manifest for Q1-Q8.
- Three treatment policy manifests.
- Provider-free raw observation and contamination fixtures.
- Offline live-authorization contract fixtures.
- Focused tests under `ai-engine/tests/`.
- Story 0223-specific evaluation documentation and report templates only where
  an existing repository convention supports them.

## Files to Modify

Only if required to expose reusable evaluation-only interfaces:

- Evaluation package exports or test registration.
- Story 0223 documentation after implementation evidence exists.

## Files Not to Modify

- `ai-engine/evaluations/comparative_baseline/infrastructure.py` semantics for
  Story 0133.
- Existing Story 0133 manifests, policies, fixtures, and reports.
- Java Core production contracts, entities, services, and migrations.
- AI Engine production schemas, prompts, and generation services.
- MCP/REST adapters.
- Retrieval, ranking, projection, budget, retry, and provider policy code.
- Story 0222 evaluator semantics.

## Dependencies and Ordering

1. Confirm the approved Story 0223 and Repository Analysis boundaries.
2. Implement protocol constants, identity models, and policy manifests.
3. Implement the deterministic 72-cell assignment matrix.
4. Implement raw observation, bounded capture, hashing, and redaction checks.
5. Implement three-treatment isolation and pairing/completeness views.
6. Add Story 0222 quality projection reuse.
7. Add offline live-authorization and stop-condition validation.
8. Add provider-free fixtures and deterministic tests.
9. Run the full evaluation-only test suite without provider/network access.
10. Produce the implementation report and stop for independent review.
11. Require separate human authorization before any technical validation or live
    quality pilot.

## Test Plan

### Protocol and Identity Tests

- Exact 72-cell matrix and deterministic ordering.
- Question, version, revision, benchmark, oracle, policy, model, and scoring
  identity mismatch rejection.
- Stable manifest, policy, assignment, input, output, and artifact digests.
- Duplicate and missing assignment rejection.

### Isolation Tests

- Oracle markers rejected from every treatment input.
- `DEVLOG_CONTEXT` rejects direct repository traces.
- `AGENT_DIRECT` rejects DevLog/MCP context and mutable-repository metadata.
- `STORY_CONTEXT_INVESTIGATION` requires Core action and callback identity.
- Treatment outputs and diagnostics cannot cross condition boundaries.

### Observation and Completeness Tests

- Write-once and hash-bound artifact behavior.
- Secret and sensitive-field rejection.
- Missing versus invalid versus `NOT_EVALUATED` states.
- Provider-incomplete, timeout, and attribution failures excluded from semantic
  denominators.
- 72-cell completeness with zero, partial, invalid, and complete observations.
- Three-treatment complete and incomplete pairing.
- Execution completeness emitted with every quality projection.

### Story 0222 Integration Tests

- Correct, missed, and unnecessary abstention reuse.
- Confidence buckets excluded for unreviewed oracles.
- Unsupported high confidence cannot pass safety.
- Required evidence and answer class remain enforced.
- Historical and non-evaluable results remain excluded.

### Validation Commands

```bash
cd ai-engine && python -m pytest evaluations -q
cd ai-engine && python -m pytest tests/test_story_context_abstention_confidence.py tests/test_story_context_specificity.py tests/test_acceptability.py -q
cd ai-engine && python -m compileall -q evaluations tests
git diff --check
```

The exact commands must use the repository's available environment, such as
`uv run pytest`, when system Python lacks project dependencies.

Live-provider validation, human scoring, and the 72-assignment campaign are
not part of the provider-free test command and require separate authorization.

## Risks

1. **Story 0133 contract drift:** isolate the new three-treatment protocol.
2. **Oracle leakage:** validate every treatment input against forbidden markers
   and bounded serialization rules.
3. **Treatment conflation:** preserve distinct policies, traces, budgets, and
   authority boundaries.
4. **Provider failure bias:** report completeness and failure categories before
   any semantic comparison.
5. **Small-sample overclaim:** constrain conclusions to the frozen campaign.
6. **Sensitive artifacts:** apply secret scanning and bounded redaction before
   writing captures.
7. **Unapproved live execution:** require explicit authorization metadata and
   stop conditions; never infer authorization from passing tests.
8. **Unaccepted prerequisites:** keep reports provisional until dependencies and
   oracles are human-accepted.

## Acceptance Evidence Required

The implementation report must include:

- files changed and confirmation that Story 0133 semantics were untouched;
- exact assignment matrix and manifest identity;
- policy and contamination validation results;
- raw artifact/hash/redaction results;
- completeness and pairing results for provider-free fixtures;
- Story 0222 projection reuse evidence;
- tests executed and results;
- confirmation of zero provider/network calls during readiness validation;
- explicit statement that no live pilot was run unless separately authorized;
- unresolved prerequisites and limitations.

## Implementation Authorization

The planned provider-free implementation is complete. Explicit authorization is
still required for any live technical validation or quality pilot.

## Readiness

READY_FOR_HUMAN_ACCEPTANCE
