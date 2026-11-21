# Repository Analysis - Story 0223

## Status

ANALYSIS APPROVED - implementation plan required.

Story 0223 has been approved as a design. This analysis does not authorize
implementation, provider calls, paid experiments, production changes, prompt
changes, budget changes, retrieval changes, commits, or pushes.

## Repository State

- Branch observed during analysis: `story/0210-agent-response-acceptability-contract`.
- HEAD observed during analysis: `525c97011f7c572614a1b656cae294e50067b323`.
- Worktree: modified with unrelated tracked and untracked changes.
- Existing worktree changes were not reverted, staged, or modified.
- DevLog preflight: previously attempted, but unavailable because the service
  returned `401 Unauthorized`; this analysis is grounded in repository
  artifacts and current source inspection.

## Governing Context

Story 0223 is the final planned Story in the `agent-response-reliability` epic.
It follows the contract, freshness, runtime-loss, live-capture, specificity,
abstention, and confidence slices. Its purpose is to evaluate real engineering
questions, not to introduce another production capability.

The benchmark specification is still marked as requiring human oracle review.
The eight questions Q1-Q8 are proposed candidates, not official quality
evidence. The current Story 0222 report also records that its oracles remain
proposed and that human acceptance is pending.

## Existing Comparative Infrastructure

### Story 0133 Offline Infrastructure

`ai-engine/evaluations/comparative_baseline/infrastructure.py` is reusable for
the contract patterns but is intentionally narrower than Story 0223:

- three frozen questions: `CASE-01-COMPARATIVE`, `CASE-03`, and `CASE-04`;
- two executable conditions: `DEVLOG` and `AGENT_DIRECT`;
- three repetitions;
- an 18-assignment matrix;
- explicit `HUMAN_DIRECT` policy, currently non-executable;
- immutable raw observation validation;
- SHA-256 identity and raw-output validation;
- oracle leakage rejection;
- pairing compatibility checks;
- missing/invalid/semantic eligibility states;
- historical artifact exclusion;
- execution completeness alongside grounded correctness.

The current `compare_pair` and `completeness` functions assume exactly two
conditions and the Story 0133 question set. Modifying them in place to support
Story 0223 would silently change an accepted historical contract.

Story 0223 therefore requires a new versioned protocol or an isolated adapter
that reuses primitives without changing Story 0133 semantics.

### Existing Collection Runtime

`ai-engine/evaluations/comparative_baseline/collection_runtime.py` provides:

- typed assignment and provider request records;
- bounded provider turns and tool operations;
- pinned repository tools;
- deterministic tool-result identities and byte accounting;
- separate `DEVLOG` and `AGENT_DIRECT` execution modes;
- contamination and secret checks.

It does not currently represent `STORY_CONTEXT_INVESTIGATION` as a third
condition. Story 0184 uses a different bounded Core action flow and must not be
represented as a disguised direct-agent condition.

### Pilot Storage and Official Baseline Boundaries

The existing comparative adapters provide isolated pilot storage and reject
pilot observations from official baseline eligibility. These boundaries should
be reused, but Story 0223 needs a new campaign identity and an explicit three-
treatment compatibility key.

Historical V4 reports are useful technical evidence about provider failures,
tool trajectories, censoring, and completeness. They must not be silently
converted into Story 0223 observations or treated as proof of comparative
quality.

## Existing Evidence and Limitations

The V4 official collection report establishes experimental integrity, not
product quality:

- 18 assignments were finalized;
- only 5 observations were semantically evaluated;
- 13 were not semantically evaluated;
- 6 provider failures occurred;
- no correct-grounded result was recorded;
- the direct treatment did not produce a semantically eligible observation.

This supports the need for explicit technical eligibility and failure
attribution. It does not support ranking treatments or declaring DevLog quality.

The current-state investigation also identifies that Story 0184 is a distinct
bounded capability whose real answer quality has not yet been measured. A direct
agent trace and a Core-authorized Story Context investigation trace cannot be
merged merely because both can perform read/search actions.

## Contract and Oracle State

The Answer Quality Benchmark Specification provides the correct starting
structure:

- eight DevLog AI questions;
- pinned repository and question identities;
- required claims and acceptable qualified variants;
- required evidence and forbidden claims;
- expected uncertainty and answer class;
- two independent human raters and adjudication;
- technical eligibility separate from semantic quality;
- `NOT_EVALUATED` for missing or invalid provider results.

However, the specification is not yet a frozen oracle. Story 0223 must add or
validate a campaign manifest only after human review of the questions, evidence
scope, claims, abstention expectations, and scoring thresholds.

No live pilot can produce official semantic conclusions while the oracle,
revision, treatment policy, or scoring identity remains mutable.

## Proposed Implementation Boundary

The implementation should create a Story 0223-specific evaluation package and
protocol version. It should reuse existing hashing, raw artifact, contamination,
secret, policy, and completeness primitives where their semantics remain
compatible.

The new boundary must support:

- eight Q1-Q8 question identities;
- three treatments: `DEVLOG_CONTEXT`, `AGENT_DIRECT`,
  `STORY_CONTEXT_INVESTIGATION`;
- three repetitions;
- 72 deterministic assignment cells;
- treatment-specific policy manifests;
- common pairing fields and treatment-specific metadata;
- raw observations and bounded redacted captures;
- technical terminal outcomes and semantic eligibility;
- oracle reference without oracle leakage;
- claim-level evidence and abstention evaluation;
- execution completeness and paired completeness;
- human reviewer scores and adjudication state;
- separate human-utility protocol data, if authorized.

The new protocol must fail closed for:

- question, version, or repository mismatch;
- model or configuration mismatch;
- treatment-policy mismatch;
- repetition mismatch;
- oracle or scoring mismatch;
- capture digest or artifact mismatch;
- contamination between treatment inputs;
- missing provider attribution;
- unbounded or sensitive capture content.

## Treatment-Specific Boundaries

### `DEVLOG_CONTEXT`

Input may contain only the frozen Core-authorized DevLog context and its bounded
metadata. It must not contain direct repository traces, oracle material, or
other treatment outputs.

### `AGENT_DIRECT`

Input may use only the pinned repository and the allowed read-only operations.
It must not access DevLog, MCP context, oracle data, web resources, external
memory, or mutable repository state.

### `STORY_CONTEXT_INVESTIGATION`

Input and actions must use the Core-authorized Story 0184 capability. The
capture must preserve action authorization, action identity, revision, bounded
usage, canonical evidence, and terminal callback state. It must not be treated
as a direct repository agent or as an alternative authority path.

## Phase Analysis

### Provider-Free Readiness

This is the safe implementation baseline. It can validate the 72-cell matrix,
identities, treatment isolation, oracle exclusion, missing states, replay,
hashes, and report completeness without credentials or network access.

### Technical Validation

The repository can support a one-case-per-treatment technical validation, but
this requires explicit live authorization and must stop on provider/runtime
failure. It must not be bundled implicitly with implementation authorization.

### Quality Pilot

The 72-assignment live pilot requires a separate human decision after technical
readiness. It must use isolated campaign storage, immutable artifacts, reviewed
oracles, blind scoring, and explicit retention/redaction rules.

### Human Utility

No current artifact proves human utility. A human session must be a separate
protocol with participant, timing, treatment-blinding, and review metadata. The
automated evaluator must leave human utility unset when that protocol is absent.

## Expected Files

The exact file list belongs in the Implementation Plan. The analysis supports
the following bounded surface:

### Likely Files to Create

- A Story 0223-specific evaluation package under
  `ai-engine/evaluations/`.
- Versioned question, policy, assignment, and oracle-compatibility manifests.
- Provider-free raw observation and campaign fixtures.
- Focused tests for identities, isolation, completeness, pairing, and
  `NOT_EVALUATED` handling.
- Human scoring and adjudication templates only if an existing convention is
  confirmed.

### Likely Files to Reuse or Adapt Carefully

- `ai-engine/evaluations/comparative_baseline/infrastructure.py` for compatible
  hashing, artifact, identity, and missing-value primitives.
- `ai-engine/evaluations/comparative_baseline/collection_runtime.py` for the
  existing DevLog/direct runtime boundaries, without changing Story 0133
  behavior.
- Existing bounded live-capture and runtime-loss projections for technical
  outcome attribution.
- Story 0222 evaluation output for abstention and ordinal confidence.

### Files Not Expected to Change

- Java Core production contracts and persistence.
- AI Engine generation prompts and production schemas.
- MCP/REST production adapters.
- Retrieval, ranking, budget, retry, or provider policy code.
- Story 0133 historical semantics and frozen baseline assets.
- Story 0222 evaluator semantics.

## Dependencies and Blockers

The following are prerequisites for official pilot conclusions:

- human acceptance of Story 0222;
- human-reviewed Story 0222 and benchmark oracles;
- accepted or explicitly classified dependencies Stories 0210, 0220, and 0221;
- a selected repository identity and immutable revision;
- approved provider/model/configuration policy;
- approved artifact retention and redaction rules;
- separate live-provider authorization.

The Story can proceed through provider-free readiness while live authorization
or human utility remains unavailable. It must then report the pilot as blocked or
`NOT_EVALUATED`, not as a failed quality campaign.

## Risks and Mitigations

### Protocol Drift

Changing Story 0133 in place could invalidate historical comparisons. Use a new
Story 0223 protocol version and compatibility key.

### Oracle Leakage

The eight questions contain expected evidence and forbidden claims. Keep oracle
inputs outside every treatment and validate serialized inputs against a
forbidden-marker allow-list.

### Treatment Conflation

Direct repository actions and Core-authorized investigation actions may look
similar. Preserve distinct condition identities, traces, budgets, action
authorization, and evidence provenance.

### Technical Failure Bias

Provider failure or censoring can leave one treatment with no eligible results.
Report completeness and failure categories beside semantic outcomes and do not
rank treatments from eligible-only rates.

### Small Sample Claims

72 assignments with three repetitions cannot support broad population claims.
Limit conclusions to the frozen questions, repository revision, treatment
policies, and campaign configuration.

### Human Scoring Bias

Blind treatment labels, randomize answer order, use two independent raters, and
record adjudication and disagreement rather than collapsing scores silently.

### Sensitive Captures

Provider traces may contain prompts, repository content, credentials, or raw
incomplete output. Reuse bounded redaction and secret checks before persistence.

### Dirty Worktree

The current worktree contains unrelated changes. Only Story 0223 planning and
later explicitly authorized evaluation artifacts may be touched.

## Validation Implications

The Implementation Plan should require, at minimum:

```bash
cd ai-engine && python -m pytest evaluations -q
cd ai-engine && python -m compileall -q evaluations tests
git diff --check
```

Focused tests must cover:

- exact 72-cell assignment matrix;
- deterministic ordering and identity digests;
- three-condition policy isolation;
- oracle leakage prevention;
- raw artifact hash and write-once behavior;
- missing versus invalid versus `NOT_EVALUATED` states;
- treatment-specific pairing and compatibility;
- revision/model/configuration/policy mismatch rejection;
- provider-incomplete and capture-integrity exclusion;
- Story 0222 abstention/confidence projection reuse;
- historical pilot exclusion from official denominators;
- execution completeness alongside every quality result;
- no provider/network requirement for readiness tests.

Live validation commands, provider credentials, and human scoring execution must
be documented as separate authorized steps and must not be required for the
provider-free test suite.

## Decision Recorded

This Repository Analysis recommends proceeding to an Implementation Plan with
the following fixed boundary:

- new versioned Story 0223 protocol, without changing Story 0133 semantics;
- provider-free 72-cell readiness first;
- three isolated treatments;
- human-reviewed Q1-Q8 oracles before official quality claims;
- separate live-provider authorization;
- human utility kept separate;
- no production, prompt, budget, retrieval, or authority changes.

Human approval was granted before creating the Implementation Plan. The next
gate is approval of the Implementation Plan.

## Readiness

READY_FOR_IMPLEMENTATION_PLAN_REVIEW
