# Story 0133 - Implementation Report

## Status

```text
STORY_0133_IMPLEMENTATION = COMPLETE
HUMAN_ACCEPTANCE = PASS
PROVIDER_CALLS = 0
DATA_COLLECTION = 0
EXPERIMENTAL_OBSERVATIONS_CREATED = 0
```

This report covers evaluation-only offline infrastructure. It does not collect
the comparative baseline, call a provider, perform statistical analysis, or
change production behavior.

## Implemented Artifacts

- `ai-engine/evaluations/comparative_baseline/baseline-manifest.json`: frozen
  three-question, two-condition, three-repetition baseline manifest.
- `ai-engine/evaluations/comparative_baseline/devlog-policy.json`: frozen
  DevLog isolation policy.
- `ai-engine/evaluations/comparative_baseline/agent-direct-policy.json`:
  frozen read-only pinned repository policy.
- `ai-engine/evaluations/comparative_baseline/human-direct-policy.json`:
  frozen non-executable future human policy.
- `ai-engine/evaluations/comparative_baseline/infrastructure.py`: offline
  manifest, policy, raw artifact, hash, pairing, historical, and completeness
  validation.
- `ai-engine/tests/test_comparative_baseline.py`: deterministic contract tests.

## Frozen Identities

```text
BASELINE_MANIFEST = story0133-comparative-baseline-1.0.0
QUESTIONS = CASE-01-COMPARATIVE@1.0.0, CASE-03@1.0.0, CASE-04@1.0.0
CONDITIONS = DEVLOG, AGENT_DIRECT
HUMAN_POLICY = HUMAN_DIRECT@story0133-human-direct-policy-1.0.0
REPETITIONS = 3 per question per AI condition
ASSIGNMENTS = 18
REPOSITORY_REVISION = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
ORACLE = 1.0.0 / HUMAN_APPROVED
MAPPING_HASH = 67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0
```

CASE-01 uses the approved exact wording and source/target identity. Its
adaptation preserves CL-01 semantics and records unchanged oracle compatibility.
The frozen authorized/provider-visible/expected-grounding evidence subsets are
validated as `14`, `4`, and `2` respectively. No evidence-role labels enter
condition input.

## Contracts

The raw observation contract preserves question/version, condition policy,
repetition, repository and oracle identities, model/configuration, exact input,
raw output, validation results, timing/token/cost fields when available,
artifact reference, run identity, and raw output hash.

Raw artifacts are write-once and tamper-evident. Derived semantic fields are not
invented by raw capture. A response-bearing deterministic failure remains an
executed observation. An infrastructure-invalid observation requires
`semanticOutcome=NOT_EVALUATED`.

The five missing states remain distinct:

```text
NOT_APPLICABLE
NOT_AVAILABLE
NOT_MEASURED
INVALID
NOT_EVALUATED
```

Measured zero remains a measured value and is never normalized to a missing
state.

Pairing validates benchmark/question/version, repository identity/revision,
model/configuration, policy compatibility, repetition, oracle version, and
scoring contract. Incompatibility returns `INVALID_COMPARISON`; it is not
silently dropped.

Completeness exposes assigned, executed, semantic-eligible, invalid, missing,
paired-complete, paired-incomplete, and execution-completeness counts. The
primary outcome boundary requires these completeness fields alongside
`correct_grounded_answer_rate`; no composite score or statistical analysis is
implemented.

Historical CASE-01 is classified `HISTORICAL_PRE_BASELINE` and is rejected from
official baseline eligibility. Historical V3 observations cannot contribute to
the 18-assignment denominator.

## Verification

```text
Focused Story0133 tests = 22 passed
Focused tests were not rerun during documentation-only acceptance closure because
the frozen manifest, hashes, references, evaluation semantics, and implementation
were not modified.
Provider calls = 0
Network/API dependency = NO
Production code changed = NO
Production semantics changed = NO
Data Science analysis = NO
```

The tests cover deterministic assignment generation, CASE-01 adaptation,
condition isolation and oracle leakage, missingness, invalid identities,
pairing mismatch, completeness, infrastructure versus response-bearing
semantic failure, historical exclusion, primary-outcome completeness guards,
and immutable artifact hashing.

## Pedagogical Boundary

`LEARN` topics are missing-data semantics, zero versus missing, assignment
versus observation, semantic eligibility, execution completeness, and the
distinction between infrastructure and semantic failure.

`PAIR` topics are raw schema, CASE-01 compatibility, condition isolation,
pairing, historical exclusion, and acceptance-test structure.

`DELEGATE` work is mechanical JSON serialization, hashing, write-once plumbing,
fixture generation, and repetitive validation.

Pandas, DataFrames, descriptive statistics, paired effects, confidence
intervals, bootstrap, tests, plots, and result interpretation remain reserved
for a later Data Science phase.

## Known Limitations

- The future DEVLOG and AGENT_DIRECT runtimes are not implemented.
- The future human collection path is represented but non-executable.
- No derived dataset or analytical export is implemented.
- CASE-01's exact adaptation is frozen in the manifest but still requires
  human Story acceptance before observations are collected.
- This infrastructure does not prove comparative product value.

## Production Boundary

All implementation is evaluation-specific. Story0132 remains closed. No
provider, network, observation collection, production module, production
semantic, RAG, ML infrastructure, statistical analysis, commit, push, or merge
was performed.
