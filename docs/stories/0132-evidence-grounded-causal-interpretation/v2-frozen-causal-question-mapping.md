# Story 0132 - Frozen V2 Causal Question Mapping

## Status

`FROZEN`

`V2_CAUSAL_QUESTION_MAPPING_STATUS = HUMAN_APPROVED_AND_FROZEN`

`CASE_04_MAPPING_AUTHORITY = HUMAN_APPROVED`

`HUMAN_SEMANTIC_DECISION_REMAINING = NO`

This is the frozen projection of the frozen Story0131 causal relation data
onto the Story0132 V2 `CausalQuestion` contract. It does not change benchmark
semantics, the benchmark files, the oracle, production causal semantics, or
the historical captures.

This artifact is frozen for the Story0132 pre-live evaluation slice. It does
not authorize OpenAI execution by itself.

## Authoritative Sources

| Field | Source of truth |
|---|---|
| Case membership and causal relation identity | Story0130 `benchmark-suite-v1.json` |
| Frozen expected classification | Story0131 `oracle-freeze-v1.json` |
| Authorized evidence candidates | Case-level `expectedEvidence` in Story0130 benchmark |
| `relationAsked` normalization convention | Core `AnalyzeStoryContextUseCase.buildGroundingContract`: default `CAUSAL` |
| Historical model references | Story0132 captures, diagnostic only; not oracle authority |

`expectedEvidence` is frozen at case level. The benchmark does not contain a
relation-specific evidence-reference mapping for `CL-01` through `CL-10`.
Historical model-produced references are therefore not promoted to such a
mapping by this artifact.

## Canonical Mapping Table

| CASE_ID | CLAIM_ID | CURRENT_FROZEN_SOURCE | CURRENT_FROZEN_TARGET | CURRENT_FROZEN_RELATION | CURRENT_EXPECTED_OUTCOME | V2_SOURCE | V2_TARGET | V2_RELATION_ASKED | V2_ANSWER_REQUIRED | GROUND_TRUTH_REFERENCE_IDS | MAPPING_CLASS | MAPPING_RULE | HUMAN_DECISION_REQUIRED | NOTES |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| CASE-01 | CL-01 | ADR-042 | Story-0039 | CAUSAL / EXPLICITLY_DOCUMENTED | EXPLICITLY_DOCUMENTED | ADR-042 | Story-0039 | CAUSAL | true | CASE-01 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Copy `from`/`to`; normalize the causal-link relation to Core's `CAUSAL` question kind; copy oracle classification | NO for question identity; relation-specific evidence support remains absent | Five questions are retained for CASE-01 |
| CASE-01 | CL-02 | ADR-043 | Story-0040 | CAUSAL / EXPLICITLY_DOCUMENTED | EXPLICITLY_DOCUMENTED | ADR-043 | Story-0040 | CAUSAL | true | CASE-01 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Same rule as CL-01 | NO for question identity; relation-specific evidence support remains absent | Five questions are retained for CASE-01 |
| CASE-01 | CL-03 | Story-0039 | persisted Account identity and PAPER assignment | CAUSAL / STRONGLY_SUPPORTED | EXPLICITLY_DOCUMENTED | Story-0039 | persisted Account identity and PAPER assignment | CAUSAL | true | CASE-01 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Same rule as CL-01 | NO for question identity; relation-specific evidence support remains absent | Strength is expected outcome, not `relationAsked` |
| CASE-01 | CL-04 | Story-0041 | paper position valuation | CAUSAL / EXPLICITLY_DOCUMENTED | EXPLICITLY_DOCUMENTED | Story-0041 | paper position valuation | CAUSAL | true | CASE-01 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Same rule as CL-01 | NO for question identity; relation-specific evidence support remains absent | Five questions are retained for CASE-01 |
| CASE-01 | CL-05 | Story-0042 | paper local full exit and optimistic locking | CAUSAL / EXPLICITLY_DOCUMENTED | STRONGLY_SUPPORTED | Story-0042 | paper local full exit and optimistic locking | CAUSAL | true | CASE-01 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Same rule as CL-01 | NO for question identity; relation-specific evidence support remains absent | Strength is expected outcome, not `relationAsked` |
| CASE-02 | CL-06 | ADR-042 | PAPER local authority | CAUSAL / EXPLICITLY_DOCUMENTED | EXPLICITLY_DOCUMENTED | ADR-042 | PAPER local authority | CAUSAL | true | CASE-02 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Copy `from`/`to`; normalize to Core's `CAUSAL` question kind | NO for question identity; relation-specific evidence support remains absent | Three questions are retained for CASE-02 |
| CASE-02 | CL-07 | ADR-043 | mode-aware risk behavior | CAUSAL / EXPLICITLY_DOCUMENTED | EXPLICITLY_DOCUMENTED | ADR-043 | mode-aware risk behavior | CAUSAL | true | CASE-02 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Same rule as CL-06 | NO for question identity; relation-specific evidence support remains absent | Strength is expected outcome, not `relationAsked` |
| CASE-02 | CL-08 | Story-0042 | optimistic locking/idempotent exit | CAUSAL / STRONGLY_SUPPORTED | STRONGLY_SUPPORTED | Story-0042 | optimistic locking/idempotent exit | CAUSAL | true | CASE-02 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Same rule as CL-06 | NO for question identity; relation-specific evidence support remains absent | Strength is copied only as oracle outcome |
| CASE-03 | CL-09 | PaperSettlementService | PaperSettlementExitTest | CAUSAL / STRONGLY_SUPPORTED | STRONGLY_SUPPORTED | PaperSettlementService | PaperSettlementExitTest | CAUSAL | true | CASE-03 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Copy `from`/`to`; normalize to Core's `CAUSAL` question kind | NO for question identity; relation-specific evidence support remains absent | Two questions are retained for CASE-03 |
| CASE-03 | CL-10 | Story-0041 | PositionControllerTest | CAUSAL / EXPLICITLY_DOCUMENTED | STRONGLY_SUPPORTED | Story-0041 | PositionControllerTest | CAUSAL | true | CASE-03 `expectedEvidence` set | DETERMINISTIC_NORMALIZATION | Same rule as CL-09 | NO for question identity; relation-specific evidence support remains absent | Strength is expected outcome, not `relationAsked` |
| CASE-04 | CASE-04 | ADR-043 | ExecutionConfiguration refactor delivered by Story 0042 | CAUSAL / negative control only | NOT_ESTABLISHED | ADR-043 | ExecutionConfiguration refactor delivered by Story 0042 | CAUSAL | true | CASE-04 `expectedEvidence` set | HUMAN_APPROVED_AND_FROZEN | Human-approved question identity; the freeze does not assert that the relation is true | NO: the human freeze is recorded below | Must remain a normal generic V2 question; no CASE04-specific prompt behavior |

## Stable Technical IDs

The proposed technical question identifiers are deterministic and preserve
claim identity:

```text
CASE-01::CL-01
CASE-01::CL-02
CASE-01::CL-03
CASE-01::CL-04
CASE-01::CL-05
CASE-02::CL-06
CASE-02::CL-07
CASE-02::CL-08
CASE-03::CL-09
CASE-03::CL-10
CASE-04::CASE-04
```

They are evaluation identifiers only. They must not be sent to the model as
benchmark answer keys or added to model-facing evidence.

## Cardinality and Repetition

The experimental hierarchy is:

```text
benchmark case
  -> one question per frozen causal relation
  -> exactly one CausalAssessment per question
```

The proposed question counts are:

| Case | Questions |
|---|---:|
| CASE-01 | 5 |
| CASE-02 | 3 |
| CASE-03 | 2 |
| CASE-04 | 1 proposed negative-control question |

The repetition unit remains the benchmark case. Each case execution repeats
three times and must produce exactly one assessment for every question in that
case. Positive accuracy and stability must therefore be scored at the relation
(`CLAIM_ID`) level, while the CASE-04 gate remains three case repetitions with
`NOT_ESTABLISHED`.

## Recorded Human Freeze Decision

The frozen benchmark/oracle did not structurally define the CASE-04 source and
target. The human engineer explicitly approved the following question without
asserting its causal truth:

```text
CASE-04 source = ADR-043
CASE-04 target = ExecutionConfiguration refactor delivered by Story 0042
CASE-04 relationAsked = CAUSAL
CASE-04 answerRequired = true
CASE-04 expected classification = NOT_ESTABLISHED
```

CASE-04 mapping authority is `EXPLICIT_HUMAN_FREEZE`; CL-01 through CL-10
remain `DETERMINISTIC_FROM_FROZEN_BENCHMARK`. The question exists even though
the expected relation is not established. `answerRequired=true` requires an
assessment and does not assert an affirmative result.

## Integrity

This proposal was created without modifying the frozen benchmark, oracle,
Ground Truth artifacts, legacy captures, model, retrieval, or production V2
contract.

Historical hashes remain verified separately:

```text
FIRST_GREEN = 011aed5f5c75264f2fea7272c745d8bc9fff45ee8c58b5c74ce8494190b345e1
ORIGINAL_RED_LIVE = bf148c3ed4b5e697478d7dc3325dd7c39a0e8b3ec604fec85e4c1545145f4128
```

## Metric Unit Matrix

| Metric | Unit | Denominator / grouping | Repetition semantics |
|---|---|---|---|
| POSITIVE_CAUSAL_ACCURACY | QUESTION | Positive question assessments only; 10 questions x 3 repetitions = 30 | Full case repetition assesses every question in that case |
| CASE_04_NOT_ESTABLISHED | CASE / QUESTION | One CASE04 question, required correct in 3 case repetitions | Exactly one negative assessment per repetition |
| GROUNDING | RUN | Each generated run/assessment must be grounded | Evaluated for every question in every case repetition |
| UNSUPPORTED_INFERENCE | RUN | Every generated run/assessment | Technical retries are not repetitions |
| CAUSAL_OVERCLAIM_RATE | QUESTION | Negative question assessments | Grouped by question identity |
| ABSTENTION_ACCURACY | QUESTION | Negative question assessments | Grouped by question identity |
| ROLE_ADMISSIBILITY_RATE | QUESTION | Causal question assessments | Grouped by question identity |
| CAUSAL_CLAIM_STABILITY | QUESTION | Same question identity across repetitions | Three signatures compared per question |
| EVIDENCE_ASSERTION_VALIDITY_RATE | ASSERTION | Every generated EvidenceAssertion | Traceable to case, question and repetition |
| ASSERTION_TRACEABILITY | ASSERTION | Every accepted/invalid assertion record | Traceable to case, question and repetition |

The current expected assessment-slot count is `33`: `15 + 9 + 6 + 3`.
