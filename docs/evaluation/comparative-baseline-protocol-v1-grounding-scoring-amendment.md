# Comparative Baseline Protocol V1 Grounding and Scoring Amendment

## Status

```text
PHASE = COMPARATIVE_BASELINE_PROTOCOL_V1_GROUNDING_SCORING_AMENDMENT
MODE = PAIR
STATUS = HUMAN_ACCEPTED

PROTOCOL_V1_AMENDMENT = HUMAN_ACCEPTED
IMPLEMENTATION_PERFORMED = NO
PROVIDER_CALLS = 0
LIVE_PILOT_EXECUTED = NO
BASELINE_OBSERVATIONS_CREATED = 0
GROUNDING_CONTRACT_VERSION = story0135-comparative-grounding-1.0.0
SCORING_PROJECTION_VERSION = story0135-comparative-scoring-projection-1.0.0
THREE_LAYER_MODEL = APPROVED
OPTION_B_PLUS_C = APPROVED
MULTIPLE_EXCERPT_OCCURRENCES = APPROVED
ESTABLISHED_MINIMUM_EVIDENCE_ASSERTIONS = 1
CLAIM_REFERENCE_POLICY = APPROVED
CAUSAL_BINARY_PROJECTION = APPROVED_FOR_APPLICABLE_FROZEN_QUESTION_CONTRACTS
JAVA_EVIDENCE_ONLY_BRIDGE = APPROVED
EXPERIMENTAL_MEANING_CHANGED = NO
CONDITION_FAIRNESS_CHANGED = NO
ORACLE_SEMANTICS_CHANGED = NO
PRIMARY_OUTCOME_MEANING_CHANGED = NO
```

This document records the human-accepted amendment design. It does not modify
the current Protocol V1 implementation, Story0135, the common answer contract,
the oracle, or any observation artifact. Story0135 implementation may resume
under this amendment, but is not performed here.

## Decision

The Comparative Baseline uses three independent evaluation layers:

```text
LAYER_1 = STRUCTURAL_VALIDATION
LAYER_2 = DETERMINISTIC_EVIDENCE_GROUNDING
LAYER_3 = SEMANTIC_ORACLE_EVALUATION
```

No layer performs another layer's responsibility.

The historical Story0132 causal contract is not the Comparative Baseline
grounding authority. Story0132 causal classifications remain historical oracle
semantics and historical engineering evidence.

## Version Identities

```text
COMPARATIVE_GROUNDING_CONTRACT_VERSION = story0135-comparative-grounding-1.0.0
COMPARATIVE_SCORING_PROJECTION_VERSION = story0135-comparative-scoring-projection-1.0.0
```

Both identities must be persisted with future observations, replay metadata,
and derived evaluation records.

The existing Story0133 manifest, repository revision, question identities,
condition policies, repetition count, oracle source, and primary outcome remain
unchanged.

## Layer 1: Structural Validation

Layer 1 validates only the Story0134 common answer contract:

- question identity and version;
- required fields and no unknown fields;
- bounded answer, claim, evidence, and excerpt sizes;
- relationship-result, confidence, abstention, claim-type, role, and locator enums;
- locator shape and bounds;
- secret, oracle, and cross-condition contamination.

Structural validity does not establish that a reference exists or that a claim
is correct.

## Layer 2: Deterministic Evidence Grounding

Comparative grounding is an evidence-fidelity check only. It validates:

1. The cited reference belongs to the captured authorized evidence universe.
2. The reference resolves against the frozen repository identity and revision.
3. The typed locator resolves deterministically.
4. The submitted non-empty excerpt occurs exactly in the resolved immutable content.
5. The resolved content digest is recorded.
6. The provider-visible source and evaluator canonical identity are recorded.

Grounding does not decide causal classification, causal strength, evidence role,
claim correctness, affected-component correctness, or abstention correctness.

### Excerpt Rule

The approved design is `OPTION_B_PLUS_C`:

- The provider-facing answer retains its reference, locator, and bounded excerpt.
- The evaluator resolves the canonical locator after raw capture.
- The evaluator verifies exact substring occurrence in the resolved content.
- The evaluator records the resolved-content digest and all deterministic match offsets.
- Evaluator-only canonical metadata is never included in provider-visible context.

Multiple exact occurrences are valid. Match offsets are recorded in source order.
Uniqueness is not required unless a future locator contract explicitly requires
a unique resolved unit.

An empty excerpt is structurally invalid. A non-empty excerpt with no exact
match maps to the existing `EVIDENCE_EXCERPT_MISMATCH` taxonomy value.

### Minimum Citation Cardinality

For a frozen causal question whose common contract defines binary
`relationshipResult`:

- `ESTABLISHED` requires at least one item in `evidence[]`.
- `NOT_ESTABLISHED` requires zero evidence items or any number of valid cited items.
- `NOT_APPLICABLE` has no causal citation minimum.

This is a deterministic grounding/eligibility rule, not a semantic truth rule.
It does not identify which evidence is expected or correct. Claims references
are independently validated and do not substitute for the required causal
`evidence[]` item when `ESTABLISHED` is asserted.

For non-causal questions, the frozen question-specific contract remains the
authority for any additional minimum. No universal causal rule is imposed on
future benchmarks.

### Claims References

`claims[].references` are independently grounded. A claim reference does not
need to be duplicated in `evidence[]` solely to become groundable.

Every claim reference used for grounded evaluation must belong to the same
captured authorized evidence universe and resolve through the same deterministic
reference, revision, and content authority. A claim reference has no locator or
excerpt of its own; the evidence assertion rules apply where an assertion is
present. The evaluator does not infer relationships between a claim and another
evidence assertion.

## Canonical Evidence Identity

The evaluator-side canonical identity contains:

```text
repositoryId
repositoryRevision
canonicalReference
path / commit / hunkIdentity where applicable
normalizedLocator
resolvedContentDigest
providerVisibleSourceIdentity
```

Condition-specific provider reference strings may differ. Normalization uses
only captured authorized evidence and deterministic repository resolution. The
oracle and expected-correct evidence never participate in normalization.

The canonical identity is evaluator metadata. It is not provider input and is
not used to repair or redirect an answer.

## Authorization Boundary

The following concepts remain separate:

```text
AUTHORIZED_EVIDENCE_UNIVERSE != EXPECTED_CORRECT_EVIDENCE
```

Authorized evidence is the only universe available to Layer 2. Expected-correct
evidence remains evaluator/oracle-side information and cannot:

- supply a missing citation;
- repair an invalid citation;
- redirect a locator;
- credit uncited evidence;
- enter provider-visible context.

For `DEVLOG`, only evidence actually cited by the final captured answer receives
grounding credit. Projection presence alone is not credit.

For `AGENT_DIRECT`, only evidence actually cited by the final captured answer and
present in captured authorized tool results receives grounding credit. Merely
inspecting an artifact does not create grounding credit.

## Layer 3: Semantic Oracle Evaluation

Layer 3 consumes the normalized common answer only after raw capture. It is the
sole layer that evaluates substantive correctness against the frozen oracle.

For applicable frozen causal questions, the approved scoring-only projection is:

```text
EXPLICITLY_DOCUMENTED -> ESTABLISHED
STRONGLY_SUPPORTED -> ESTABLISHED
NOT_ESTABLISHED -> NOT_ESTABLISHED
```

This is a versioned evaluator projection. It does not claim that
`EXPLICITLY_DOCUMENTED` and `STRONGLY_SUPPORTED` are semantically identical,
and it is never sent to the provider or used by Layer 2.

The binary projection applies only where the frozen comparative question
contract explicitly defines binary `relationshipResult`. For other questions,
`relationshipResult = NOT_APPLICABLE` and the question-specific frozen semantic
evaluator remains authoritative.

An `ESTABLISHED` answer without the required citation may be semantically
correct if its conclusion matches the oracle, but it is not grounded and cannot
be a correct-grounded answer.

## Primary Outcome and Eligibility

The amended primary outcome remains:

```text
correct_grounded_answer =
    structural_valid == YES
    AND grounding_valid == YES
    AND semantic_correct == YES
```

`structural_valid`, `grounding_valid`, and `semantic_correct` remain separately
reportable.

`semantic_eligible` is true only after the required structural and deterministic
grounding gates pass. A grounding failure does not become semantic incorrectness.
Where the frozen evaluation path permits semantic evaluation despite grounding
failure, the semantic result remains separately recorded, but the observation
is ineligible for `correct_grounded_answer` and does not alter denominator
semantics.

Execution completeness remains separate from semantic eligibility and the
primary outcome.

## Error Taxonomy

The frozen public taxonomy is unchanged:

```text
REAL_BUT_UNAUTHORIZED -> UNAUTHORIZED_REFERENCE
UNRESOLVABLE -> EVIDENCE_EXCERPT_MISMATCH
LOCATOR_MISMATCH -> EVIDENCE_EXCERPT_MISMATCH
EXCERPT_MISMATCH -> EVIDENCE_EXCERPT_MISMATCH
```

Internal diagnostics may preserve the more precise source status. Existing
public values such as `STRUCTURAL_FAILURE`, `UNAUTHORIZED_REFERENCE`, and
`EVIDENCE_EXCERPT_MISMATCH` are not renamed or redefined.

## Java Authority Reuse Boundary

The approved reuse direction is:

```text
AiReferenceResolver = REUSE_UNCHANGED
TaskSnapshotEvidenceResolver = REUSE_WITH_EVALUATION_ADAPTER
Java locator/revision/content-digest resolution = REUSE_WITH_EVALUATION_ADAPTER
CoreV2EvaluationBridge = REUSE_WITH_EVALUATION_ADAPTER
AnalyzeStoryContextUseCase causal semantics = NOT_REUSABLE_FOR_COMPARATIVE_GROUNDING
```

The evaluation-only Java surface must accept evidence citations and immutable
snapshot data without requiring Python to fabricate a historical causal DTO.
It must use the existing Maven/file/property bridge mechanism, isolated
temporary files, no production HTTP endpoint, and no production semantic
change.

## Raw and Derived Contract Impact

Future observations must preserve these fields independently:

```text
structural_valid
grounding_valid
semantic_correct
semantic_eligible
canonical_evidence_identity
resolved_content_digest
resolved_excerpt_match_metadata
layer_specific_error_classification
comparative_grounding_contract_version
comparative_scoring_projection_version
```

Oracle labels remain evaluator-side. They must not enter provider-visible input,
condition manifests, raw provider payloads, or grounding authorization data.

## Historical Separation

```text
Story0132 V3 artifacts = HISTORICAL_PRE_BASELINE / historical engineering evidence
Comparative Baseline observations = separate experiment
```

Historical Story0132 artifacts are not retroactively reinterpreted under this
amendment and cannot enter the official Comparative Baseline denominator.

## Protocol Impact

```text
PROTOCOL_VERSION_DECISION = V1_AMENDMENT
EXPERIMENTAL_MEANING_CHANGED = NO
CONDITION_FAIRNESS_CHANGED = NO
HISTORICAL_ORACLE_CHANGED = NO
PRIMARY_OUTCOME_MEANING_CHANGED = NO
```

The amendment freezes previously underspecified grounding and scoring interfaces.
It intentionally supersedes the current runtime-design wording that describes
Java excerpt equality for historical causal assertions: Comparative Baseline
grounding uses exact bounded-substring verification against the resolved
immutable locator content. Historical Story0132 causal validation retains its
own existing semantics.

## Story0135 Consequences After Acceptance

Story0135 may resume only after this amendment is accepted. Required work is:

- implement the evidence-only Java grounding adapter;
- invoke the evaluation-only Maven/file/property bridge;
- enforce the approved real-Java grounding capability in live mode;
- reject `AlwaysPassGrounding` and fixture grounding in live mode;
- isolate pilot namespace and ledger from official baseline storage;
- persist immutable `LIVE_PILOT` and `BASELINE_ELIGIBLE=NO` metadata;
- derive per-question projection metadata from frozen manifests;
- preserve CASE-01 with four items and CASE-03 with three items;
- persist layer-specific observation and replay metadata;
- persist both version identities;
- add the unified zero-provider-call live preflight.

## Cross-Check

The amendment is consistent with:

- Story0133's frozen repository, question, condition, oracle, repetition, and
  primary-outcome identities;
- Story0134's common answer envelope, bounded excerpts, condition isolation,
  and no-provider-call boundary;
- the Story0135 semantic-stop finding that historical causal validation cannot
  be reused as comparative evidence grounding;
- the existing Java bridge's deterministic snapshot, reference, revision,
  locator, and digest responsibilities.

The only intentional contract correction is the separation of evidence-only
grounding from historical causal classification and the corresponding bounded
excerpt verification rule. No contradiction requires changing the research
question, experimental conditions, oracle source, fairness model, primary
outcome, execution completeness, or public error taxonomy.

## Human Acceptance Closure

Human acceptance recorded:

- `OPTION_B_PLUS_C` excerpt verification;
- multiple exact excerpt occurrences;
- the `ESTABLISHED` minimum citation cardinality of one evidence assertion;
- independent claim-reference grounding;
- the causal oracle projection;
- the two version identities;
- the evaluation-only Java evidence bridge boundary.

Story0135 may resume after this documentation correction. Implementation remains
outside this document and is not performed here.
