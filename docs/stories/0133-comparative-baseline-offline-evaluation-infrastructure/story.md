# Story 0133 - Comparative Baseline Offline Evaluation Infrastructure

## Status

`IMPLEMENTED - REVIEWED - ACCEPTED`

This Story implementation has passed human acceptance. It does not authorize
provider calls, data collection, statistical analysis, production changes, or
Story0132 reopening.

## Human Acceptance Closure

```text
PHASE = STORY_0133_HUMAN_ACCEPTANCE_CLOSURE
MODE = PAIR
STATUS = COMPLETE

STORY_0133_IMPLEMENTATION = COMPLETE
HUMAN_ACCEPTANCE = PASS

CASE01_HUMAN_APPROVAL_RECORDED = YES
CASE01_OPEN_QUESTION_REMAINING = NO
QUESTION_ID = CASE-01-COMPARATIVE
QUESTION_VERSION = 1.0.0
SOURCE_IDENTITY = ADR-042
TARGET_IDENTITY = Story-0039: Persisted Account Identity and Explicit PAPER Provisioning
CL01_ORACLE_REUSABLE = YES
CL01_MAPPING_REUSABLE = YES
DESIGN_C_PROJECTION_REUSABLE = YES
AUTHORIZED_EVIDENCE_COUNT = 14
PROVIDER_VISIBLE_EVIDENCE_COUNT = 4
EXPECTED_GROUNDING_EVIDENCE_COUNT = 2

ASSIGNMENT_COUNT = 18
PROVIDER_CALLS = 0
DATA_COLLECTION = 0
EXPERIMENTAL_OBSERVATIONS_CREATED = 0
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_SEMANTICS_CHANGED = NO
HISTORICAL_CASE01_BASELINE_ELIGIBLE = NO
DATA_SCIENCE_ANALYSIS = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```

The approved canonical question wording is unchanged:

> Does the available evidence establish a causal relationship between ADR-042
> and Story-0039's persisted account identity and explicit PAPER provisioning?

## Authorization Boundary

The human-approved source protocol is:

`docs/evaluation/devlog-comparative-baseline-protocol-design.md`

```text
PHASE = COMPARATIVE_BASELINE_INFRASTRUCTURE_STORY_DEFINITION
MODE = PAIR
STATUS = AUTHORIZED
COMPARATIVE_BASELINE_PROTOCOL_HUMAN_APPROVED = YES
IMPLEMENTATION_AUTHORIZED = YES
PROVIDER_CALLS_AUTHORIZED = NO
DATA_COLLECTION_AUTHORIZED = NO
STORY_0132_REOPENED = NO
```

Story0132 is closed. Its V3 evaluation assets are historical technical
infrastructure only. This Story must not modify Story0132 production contracts,
reopen its acceptance, or treat historical V3 results as comparative baseline
observations.

## Problem

DevLog does not yet have a small, offline-verifiable data contract for a fair
comparison between DevLog-mediated project understanding and direct repository
inspection. Existing V3 assets contain frozen questions, projections, oracle
identities, validation gates, and historical artifacts, but they do not by
themselves provide the new comparative baseline's assignment matrix, condition
policy identities, normalized raw observation contract, pairing rules, or
explicit execution-completeness accounting.

Without these boundaries, future evaluation could accidentally:

- mix historical invalid V3 outputs with new observations;
- compare different repository, model, question, or policy identities;
- treat missing observations as semantic failures;
- publish grounded correctness without execution completeness;
- leak oracle or cross-condition information;
- silently compare incompatible DevLog and AGENT_DIRECT observations.

## Product Research Question

The Story preserves exactly this neutral question:

> Does DevLog help an AI agent or a human understand project history,
> architectural decisions, causal relationships, and implementation evolution
> more effectively than reading the repository directly?

DevLog superiority is not an acceptance criterion. Negative, neutral, and mixed
results are equally valid.

## Product Motivation

The infrastructure enables a reproducible longitudinal baseline for future
comparisons:

```text
current DevLog baseline
→ DevLog enrichment/projection changes
→ future Retrieval Layer
→ future DevLog Agent
→ future Kiko integration
```

The primary comparison is initially:

```text
DEVLOG vs AGENT_DIRECT
```

with a frozen policy boundary for future `HUMAN_DIRECT` collection. The Story
measures infrastructure validity, not product superiority and not Human Utility.

## Scope

### In Scope

- Create a frozen comparative baseline manifest for exactly three questions:
  `CASE-01` adapted, `CASE-03`, and `CASE-04`.
- Define exactly two initial executable condition identities: `DEVLOG` and
  `AGENT_DIRECT`.
- Define the non-executable-for-now `HUMAN_DIRECT` policy identity.
- Define three repetitions per question per AI condition and the 18-assignment
  matrix without generating observations.
- Version the questions, including the required CASE-01 adaptation.
- Freeze repository, benchmark, oracle, DevLog/context, model/configuration,
  condition-policy, and scoring identities.
- Define and validate immutable raw observation envelopes.
- Define explicit missing-value states and measured-zero semantics.
- Validate pairing compatibility and fail closed on identity mismatches.
- Validate offline assigned/executed/eligible/invalid/missing and paired
  complete/incomplete counts.
- Preserve historical CASE-01 as `HISTORICAL_PRE_BASELINE`.
- Provide deterministic offline fixtures and tests for the contract.
- Define boundaries for a future derived dataset without implementing it.

### Explicit Non-Goals

- No provider calls, API keys, network access, or experimental data collection.
- No DEVLOG or AGENT_DIRECT runtime implementation.
- No human session collection.
- No production Java, Python service, prompt, schema, MCP, frontend, database,
  repository collector, or domain-contract change.
- No Pandas requirement, DataFrame exercise, analytics, statistics, confidence
  intervals, cost analysis, or plotting.
- No new observations from historical V3 artifacts.
- No oracle regeneration, ground-truth correction, or casual question rewrite.
- No Retrieval Layer, RAG, vector database, ML platform, or generic agent
  framework.
- No composite DevLog effectiveness score.
- No Story0132 reopening or implicit authorization of implementation.
- No commit, push, merge, or Story acceptance by the implementing agent.

## Domain and Evaluation Model

### Experimental unit

The primary unit is:

```text
one frozen question
× one condition
× one repetition
```

The initial assignment matrix is:

```text
3 questions × 2 AI conditions × 3 repetitions = 18 assigned observations
```

An assignment is not an observation until a raw observation artifact exists.
Missing assignments remain missing; they are not semantic failures.

### Primary outcome

```text
PRIMARY_OUTCOME = correct_grounded_answer_rate
```

For a semantic-eligible observation:

```text
correct_grounded = semantic_correct == YES
                   AND grounding_valid == YES
                   AND structural_valid == YES
```

The denominator is the count of `semantic_eligible == YES` observations.
Execution completeness must be reported alongside it:

```text
execution_completeness = semantic_eligible_count / assigned_observation_count
```

Any publication-ready primary-outcome record must expose, in the same result:

```text
assigned_observation_count
executed_observation_count
semantic_eligible_count
invalid_observation_count
missing_observation_count
execution_completeness
correct_grounded_answer_rate
```

The validator must make it impossible for an analytical result to contain only
`correct_grounded_answer_rate` without the completeness counts. There is no
composite effectiveness score.

### Secondary outcomes

The raw contract must permit later derivation of:

- semantic correctness;
- grounding validity;
- correct abstention;
- overclaim rate;
- fabricated evidence rate;
- unauthorized-reference failures;
- evidence excerpt mismatch rate;
- structural failure rate;
- input/output tokens;
- provider cost and model-call count;
- wall-clock latency;
- provider-visible evidence bytes/items;
- sources available/consulted;
- repository operations;
- human completion time and confidence.

This Story defines fields and validation states only. It does not calculate
analytical summaries.

## Baseline Identities

The baseline manifest must freeze and validate these identities:

```text
benchmark_version = devlog-comparative-baseline-1.0.0
benchmark_source = devlog-product-value:1.0.0
repository_id = 1feead5d-dfc9-4b2c-aa9c-045a8524a9f8
repository_revision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
oracle_version = 1.0.0
oracle_status = HUMAN_APPROVED
oracle_repository_revision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
mapping_hash = 67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0
repetition_policy = 3_PER_QUESTION_PER_AI_CONDITION
```

The manifest also carries immutable hashes/references for:

- the approved protocol document;
- the question-set manifest;
- the oracle reference, without embedding oracle answers in condition input;
- condition policies;
- scoring/evaluation contract version;
- raw-observation schema version.

Any identity mismatch fails closed. A changed question, question scope, oracle,
repository revision, model/configuration, access policy, or scoring semantics
requires a new benchmark or policy version rather than silent replacement.

## Initial Question Strategy

### CASE-01 adaptation

The existing CASE-01 is a broad compound question spanning project evolution,
architectural decisions, causal links, components, and tests. It is not an
atomic comparative observation in its historical form.

The smallest deterministic adaptation is a new question identity:

```text
question_id = CASE-01-COMPARATIVE
question_version = 1.0.0
question_category = CAUSAL_RELATIONSHIP
source = ADR-042
target = Story-0039 persisted account identity and PAPER assignment
```

The adapted question must ask only for the relationship represented by the
existing canonical `CL-01` mapping. The broad historical CASE-01 wording,
evidence universe, and aggregate expectations must not be presented as though
they were unchanged. The implementation must create an explicit adaptation
record containing:

- original question identity and wording;
- adapted question identity, wording, and category;
- retained oracle/mapping references;
- expected evidence scope;
- changed fields and rationale;
- human review status.

Because wording and scope change, `CASE-01-COMPARATIVE@1.0.0` is a new question
version. No observation may use it until its adaptation/oracle compatibility
record is frozen. Existing historical CASE-01 outputs cannot satisfy this
identity.

### CASE-03

Reuse the frozen CASE-03 question/objective and its existing evidence,
component/test, causal mapping, projection, and oracle identities where
compatible. Do not regenerate ground truth.

### CASE-04

Reuse the frozen CASE-04 negative-control objective and oracle identity. A valid
abstention remains a semantic observation; an infrastructure failure remains
`NOT_EVALUATED`. Do not prompt toward the expected answer.

### CASE-02

CASE-02 remains outside this initial baseline. It is the first future expansion
candidate after a comparable DevLog projection is frozen. This Story does not
implement that projection.

## Condition Policies

Each policy is a versioned, hashed manifest. Policy compatibility is required
for pairing.

### DEVLOG policy

Allowed:

- frozen DevLog evaluation context/projection only;
- declared context strategy, projection, prompt/input, and metadata;
- condition-local answer generation.

Forbidden:

- direct repository access, shell, Git, or unapproved source reading;
- oracle, expected answer, scoring identifiers, or thresholds;
- AGENT_DIRECT or HUMAN_DIRECT answer, trace, or result;
- silent supplementation when DevLog context misses evidence.

### AGENT_DIRECT policy

Allowed read-only operations on the pinned repository revision:

- read and search files;
- inspect Git history, log, show, diff, and commit metadata;
- inspect ADRs, Engineering Stories, source, tests, and documentation;
- record operation target, result size, and duration.

Forbidden:

- DevLog, DevLog projections, DevLog answers, or MCP/REST context;
- oracle, human answers, or other condition outputs;
- web, external memory, package installation, or arbitrary code execution;
- test execution with side effects;
- mutable working tree or repository changes.

The future agent runtime is not part of this Story. The policy is frozen and
validated only.

### HUMAN_DIRECT policy

The policy is defined but not executable in this Story. It permits normal
direct repository inspection under a future human-session protocol and requires
start/end times, sources, operations where practical, answer, confidence, and
facilitator metadata. It must not expose either agent answer or the oracle
before submission.

## Raw Observation Contract

Raw observations are append-only immutable evidence. They must preserve exact
inputs and outputs, not manually derived semantic conclusions. The proposed
normalized envelope is:

```json
{
  "observation_id": "uuid",
  "benchmark_version": "...",
  "question_id": "...",
  "question_version": "...",
  "case_id": "...",
  "question_category": "...",
  "condition": "DEVLOG | AGENT_DIRECT | HUMAN_DIRECT",
  "condition_policy_version": "...",
  "repetition": 1,
  "repository_id": "...",
  "repository_revision": "...",
  "devlog_version": "... | NOT_APPLICABLE",
  "context_strategy": "... | NOT_APPLICABLE",
  "provider": "... | NOT_APPLICABLE",
  "model": "... | NOT_APPLICABLE",
  "model_configuration": {},
  "exact_input": {},
  "raw_output": {},
  "tool_trace": [],
  "validation_results": {},
  "oracle_reference": "...",
  "raw_output_sha256": "...",
  "input_tokens": "number | NOT_MEASURED | NOT_APPLICABLE",
  "output_tokens": "number | NOT_MEASURED | NOT_APPLICABLE",
  "provider_cost": "number | NOT_AVAILABLE | NOT_APPLICABLE",
  "latency_ms": "number | NOT_MEASURED",
  "artifact_reference": "...",
  "run_id": "...",
  "captured_at": "UTC timestamp"
}
```

Derived semantic fields may be stored in a separate derived record. If a raw
artifact contains validation metadata, it must be the captured validator output
and not a hand-edited conclusion. Raw artifacts are write-once and hash-bound.

## Missing-Value Contract

The contract uses distinct states:

```text
NOT_APPLICABLE = field has no meaning for this condition
NOT_AVAILABLE = field should exist but source could not provide it
NOT_MEASURED = protocol did not instrument the field
INVALID = observation or comparison cannot be used for this purpose
NOT_EVALUATED = downstream evaluation was blocked
```

Measured numeric zero is a real value and must not be encoded as any missing
state. Examples:

- provider cost for `HUMAN_DIRECT`: `NOT_APPLICABLE`;
- input tokens for uninstrumented human session: `NOT_APPLICABLE`;
- semantic correctness after infrastructure failure: `NOT_EVALUATED`;
- missing raw output after failed persistence: `INVALID` for the raw artifact;
- missing assigned observation: `NOT_MEASURED`/`MISSING_ASSIGNMENT` in the
  completeness view, never a semantic failure.

## Pairing Contract

Two observations are comparable only if all required pairing fields match:

```text
benchmark_version
question_id
question_version
repository_id
repository_revision
model
model_configuration
condition_policy_compatibility_key
repetition
oracle_version
scoring_contract_version
```

`DEVLOG` and `AGENT_DIRECT` differ in condition identity and may differ in
condition-specific context metadata, but their shared pairing fields must be
identical. Model/configuration mismatch, repository revision mismatch,
question/version mismatch, repetition mismatch, policy incompatibility, or
oracle/scoring mismatch produces `INVALID_COMPARISON`; it is not silently
dropped from denominators.

## Completeness Model

Offline validation must expose assignment status for all 18 matrix cells:

```text
ASSIGNED
EXECUTED
SEMANTIC_ELIGIBLE
INVALID
MISSING
```

It must also compute:

```text
assigned_observation_count
executed_observation_count
semantic_eligible_count
invalid_observation_count
missing_observation_count
paired_complete_count
paired_incomplete_count
execution_completeness
```

Missing is not failure. Invalid is not semantic incorrectness. A pair is
complete only when compatible DEVLOG and AGENT_DIRECT observations exist for
the same pairing key. A pair with one missing/invalid/incompatible side is
`paired_incomplete` and remains visible.

## Error Taxonomy

The approved taxonomy is frozen without redesign:

```text
CORRECT_GROUNDED
CORRECT_POOR_GROUNDING
WRONG_RELATIONSHIP
OVERCLAIM
FALSE_ABSTENTION
FAILED_TO_ABSTAIN
FABRICATED_EVIDENCE
EVIDENCE_EXCERPT_MISMATCH
UNAUTHORIZED_REFERENCE
STRUCTURAL_FAILURE
INFRASTRUCTURE_FAILURE
INVALID_COMPARISON
NOT_EVALUATED
```

One `primary_error` and an ordered, deduplicated `secondary_errors` list are
used. A response-bearing semantic failure remains a captured observation; an
infrastructure failure leaves semantic outcomes `NOT_EVALUATED`. The Story
does not reinterpret approved taxonomy meanings.

## Historical Data Policy

The recent Design C CASE-01 artifact is permanently classified:

```text
HISTORICAL_CASE01 = HISTORICAL_PRE_BASELINE
HISTORICAL_CASE01_BASELINE_ELIGIBLE = NO
```

It may be imported only by a historical-classification compatibility fixture.
It must not contribute to assignment, semantic, paired, or DEVLOG versus
AGENT_DIRECT denominators. Historical invalid V3 runs are excluded from the
semantic dataset entirely.

The historical artifact may prove that a schema was accepted, raw capture was
preserved, and Core rejected an evidence excerpt. It cannot prove comparative
DevLog value or baseline semantic capability.

## Dataset Boundaries

The Story owns:

```text
RAW OBSERVATIONS
→ boundary/interface for DERIVED DATASET
→ boundary/interface for ANALYTICAL RESULTS
```

It does not implement derived transformations, Pandas DataFrames, statistics,
plots, effect sizes, or confidence intervals. Derived fields such as
`semantic_correct`, `grounding_valid`, `correct_abstention`, `overclaim`,
`primary_error`, and `semantic_eligible` must be attributable to a frozen
evaluator/scoring contract when that later layer is implemented.

## Invariants

- The research question remains outcome-neutral.
- Exactly 18 assignments are defined and no observations are generated.
- Every initial question has an explicit versioned identity.
- CASE-01 adaptation is not confused with historical CASE-01.
- DEVLOG, AGENT_DIRECT, and HUMAN_DIRECT policies remain distinct.
- Oracle data cannot enter condition input through normal manifest construction.
- Raw output is immutable and hash-bound.
- Missing states remain distinct from measured zero.
- Invalid identities fail closed.
- Pairing never silently drops incompatible observations.
- Infrastructure failures leave semantic outcomes `NOT_EVALUATED`.
- Semantic failures remain captured observations when an output exists.
- Historical artifacts never enter official baseline denominators.
- Execution completeness is always available with grounded correctness.
- No provider, network, API key, or production code is required for tests.
- No acceptance claim is inferred from passing infrastructure tests.

## Acceptance Criteria

1. [x] A deterministic baseline manifest defines exactly 18 assignments for
   `CASE-01-COMPARATIVE`, `CASE-03`, and `CASE-04` across `DEVLOG` and
   `AGENT_DIRECT`, with three repetitions each.
2. [x] All three initial questions have explicit versioned identities and
   CASE-01 has an explicit adaptation record.
3. [x] `DEVLOG`, `AGENT_DIRECT`, and `HUMAN_DIRECT` policies are distinct,
   versioned, and frozen; only the first two are initial executable conditions.
4. [x] Oracle data cannot enter condition input through normal manifest
   construction.
5. [x] Representative valid raw observations validate offline.
6. [x] `NOT_APPLICABLE`, `NOT_AVAILABLE`, `NOT_MEASURED`, `INVALID`, and
   `NOT_EVALUATED` remain distinct from measured zero.
7. [x] Invalid condition values fail closed.
8. [x] Invalid question identities and question versions fail closed.
9. [x] Repository revision mismatch fails comparison.
10. [x] Model or model-configuration mismatch fails comparison.
11. [x] Repetition mismatch fails comparison.
12. [x] Condition-policy incompatibility fails comparison.
13. [x] Historical CASE-01 cannot enter official baseline denominators.
14. [x] Historical invalid V3 runs cannot become semantic baseline observations.
15. [x] Infrastructure failure leaves semantic outcome `NOT_EVALUATED`.
16. [x] A response-bearing semantic failure remains a captured semantic
    observation with the approved error taxonomy.
17. [x] Assigned, executed, eligible, invalid, missing, paired-complete, and
    paired-incomplete states are computable offline.
18. [x] Execution completeness is separately available from grounded correctness
    and includes assignment/eligibility/invalid counts.
19. [x] No provider, API, network, or API key is needed for the test suite.
20. [x] No production code or production semantics change.
21. [x] No analytical transformation, Pandas requirement, plot, or statistical
    result is introduced.

## Test Strategy

The future implementation is evaluation-only and offline. Tests should cover:

- exact 18-cell assignment matrix and deterministic ordering;
- identity/hash stability and manifest tampering;
- question/version/category validation;
- CASE-01 adaptation separation from historical CASE-01;
- distinct condition-policy manifests and forbidden-access declarations;
- oracle exclusion from condition input;
- valid DEVLOG and AGENT_DIRECT raw envelopes;
- future HUMAN_DIRECT envelope with `NOT_APPLICABLE` AI fields;
- raw immutability and artifact hash verification;
- all missing-value states versus numeric/string zero;
- invalid condition/question/revision/model/configuration/repetition/policy
  rejection;
- compatible and incompatible pairing;
- completeness with zero, partial, invalid, and complete matrices;
- historical CASE-01 classification and denominator exclusion;
- infrastructure versus response-bearing semantic failure states;
- no provider/network dependency.

The test suite must not require `LLM_API_KEY`, invoke OpenAI, inspect the
current branch dynamically, or alter production fixtures.

## Implementation Plan

Implementation, once separately authorized, should remain in evaluation-specific
modules and artifacts:

1. Freeze manifest constants and the three question identities.
2. Record the CASE-01 adaptation and compatibility references.
3. Define condition-policy records and access-forbidden declarations.
4. Define raw observation and missing-value models.
5. Define immutable artifact hashing/write-once validation.
6. Define pairing-key compatibility validation.
7. Define offline assignment/completeness views.
8. Add deterministic fixtures and tests for acceptance criteria.
9. Add only derived/analytical interfaces, not their implementations.
10. Verify no production module or provider integration changed.

The implementer must stop and report if production changes appear necessary.
No observation collection follows automatically from this Story.

## Pedagogical Classification

The delegation question is:

> Est-ce que je délègue cette implémentation parce que je la maîtrise
> suffisamment et que mon temps a plus de valeur ailleurs, ou parce que je ne
> saurais pas la réaliser moi-même ?

### `LEARN`

- missing-data semantics and their impact on denominators;
- experimental identity/invariant reasoning;
- interpreting assignment versus execution completeness;
- reviewing failure taxonomy and validity boundaries.

### `PAIR`

- raw observation/domain schema;
- CASE-01 adaptation and oracle compatibility;
- condition-policy isolation;
- pairing logic and incompatibility behavior;
- historical-data exclusion rules;
- acceptance-test design for the evaluation contract.

### `DELEGATE`

- mechanical JSON serialization/deserialization;
- SHA-256 hashing and write-once file plumbing;
- deterministic fixture generation after the schema is approved;
- repetitive validation tests once invariants are reviewed.

Future Data Science work is explicitly reserved for a later Story/phase and
should remain predominantly `LEARN` or `PAIR`: Pandas exploration, DataFrame
construction, descriptive statistics, paired outcomes, effect sizes,
confidence intervals, visualization, and interpretation.

## Risks

- CASE-01 adaptation may look equivalent while changing its semantic scope;
  require explicit versioning and oracle compatibility review.
- Existing V3 projections may not represent the final DevLog condition;
  record projection/context identity rather than assuming reuse is equivalent.
- A small 18-assignment design cannot support strong population claims.
- Three repetitions expose obvious variance but do not establish model behavior.
- Direct-agent tool traces may be less complete than DevLog traces; mark
  unmeasured values rather than inventing equivalence.
- Human author participation creates learning, fatigue, and independence bias.
- Cost metadata may be unavailable or provider-specific.
- A high eligible-only score can mislead if execution completeness is poor;
  require both in all generated reports.
- Historical artifacts may contain valuable technical signals but are not
  comparable observations.

## Open Questions

- Resolved by human approval: `CASE-01-COMPARATIVE@1.0.0`, the exact adapted
  wording above, and the frozen evidence scope of 14 authorized, 4
  provider-visible, and 2 expected-grounding items.
- Whether DevLog and AGENT_DIRECT can use a truly common answer contract without
  constraining the direct agent unfairly.
- Whether AGENT_DIRECT operation recording is complete enough for source-count
  comparisons.
- Whether the pinned repository revision remains the correct longitudinal
  project snapshot after the first baseline.
- Whether provider cost data is available under the chosen provider contract.
- Whether a future human participant pool is available beyond the project
  author.

## ADR Assessment

```text
ADR_REQUIRED = NO
```

This Story implements an already human-approved evaluation protocol and does
not introduce a cross-cutting production architectural decision. A future ADR
would be justified only if the raw observation/evaluation contract becomes a
shared platform boundary used by production services, MCP, multiple products,
or durable data governance beyond this evaluation initiative.

## Required Report

```text
PHASE = COMPARATIVE_BASELINE_INFRASTRUCTURE_STORY_DEFINITION
MODE = PAIR
STATUS = COMPLETE

COMPARATIVE_BASELINE_PROTOCOL_HUMAN_APPROVED = YES

PROPOSED_STORY_ID = 0133
PROPOSED_STORY_TITLE = Comparative Baseline Offline Evaluation Infrastructure

PRIMARY_OUTCOME_PRESERVED = YES
EXECUTION_COMPLETENESS_SEPARATE = YES
INITIAL_QUESTION_COUNT = 3
INITIAL_CONDITION_COUNT = 2
AI_REPETITIONS = 3
EXPECTED_ASSIGNMENT_COUNT = 18
CASE_01_ADAPTATION_REQUIRED = YES
CASE_01_NEW_QUESTION_VERSION_REQUIRED = YES
CASE_02_INCLUDED = NO
HISTORICAL_CASE01 = HISTORICAL_PRE_BASELINE
HISTORICAL_CASE01_BASELINE_ELIGIBLE = NO

RAW_OBSERVATION_CONTRACT_DEFINED = YES
MISSING_VALUE_CONTRACT_DEFINED = YES
CONDITION_POLICY_CONTRACT_DEFINED = YES
PAIRING_CONTRACT_DEFINED = YES
OFFLINE_COMPLETENESS_MODEL_DEFINED = YES

PROVIDER_REQUIRED = NO
NETWORK_REQUIRED = NO
PRODUCTION_CHANGE_REQUIRED = NO
ADR_REQUIRED = NO

LEARN_TASKS = missingness, experimental invariants, completeness interpretation, taxonomy review
PAIR_TASKS = schema, CASE-01 adaptation, policy isolation, pairing, historical exclusion, acceptance tests
DELEGATE_TASKS = serialization, hashing, fixture generation, repetitive validation tests

DATA_SCIENCE_ANALYSIS_INCLUDED = NO
IMPLEMENTATION_AUTHORIZED = YES
PROVIDER_CALLS_AUTHORIZED = NO
NEXT_ACTION = HUMAN_IMPLEMENTATION_REVIEW
STORY_0132_REOPENED = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```
