# Story 0109 — Implementation Report

## Status

**IMPLEMENTED_AWAITING_FINAL_HUMAN_REVIEW — PRODUCT_GATE_NOT_PASSED AFTER RELATIONSHIP-AWARE RETRY (0/3 STRONG, 3/3 ACCEPTABLE, 0/3 WEAK)**

## Summary

Implemented the smallest deterministic relationship-evidence vertical slice:
Docker Compose service wiring is collected as Facts and deterministically
projected into an `ARCHITECTURE_MODULARIZATION` Observation. The existing
selection and prompt-projection pipeline consumes that observation without any
Story 0109 prompt or ranking changes.

This resolves the extraction gap identified after Story 0108. The post-change
benchmark improved from 0/6 STRONG before Story 0109 to 0/3 STRONG, 3/3 ACCEPTABLE.
The product gate requires at least 2/3 STRONG; this requirement is not met.

## Baseline

```text
BASELINE_SHA = 2e849641cf74361d7703e4b3f53609b9c5b3e83e
BRANCH = main
PROJECT_ID = f3d56247-aada-4a76-982b-e6802c0b309c
INTENT = architecture-overview-v2
PROVIDER = openai
MODEL = gpt-4.1-mini
PROMPT_CHANGED_BY_STORY_0109 = NO
```

## Implemented Slice

### Facts

- `DOCKER_SERVICE_DEPENDS_ON` represents an explicit Compose `depends_on`
  relationship.
- `DOCKER_SERVICE_ENV_REFERENCE` represents a Compose environment value that
  deterministically names another declared service.
- Docker collector version increased from `docker-v1` to `docker-v2` so the
  changed output participates correctly in collection fingerprints.

Environment references recognize service-oriented variable substitutions and
HTTP service URLs. Self-references, loopback hosts, and values that do not name
a declared Compose service are excluded.

### Observation

The `DOCKER_SERVICE_WIRING` rule requires both a declared Docker service and a
cross-service environment reference. It emits an
`ARCHITECTURE_MODULARIZATION` observation with supporting Fact references.

`ProjectProfileServiceImpl` maps that observation to the
`DOCKER_SERVICE_WIRING` profile characteristic. This mapping is required for
the observation to traverse profile construction rather than fail as an
unsupported profile-v1 observation type.

## Deterministic Tests

`DockerCollectorWiringTest` covers:

- `depends_on` extraction;
- environment-variable service references;
- HTTP service URLs;
- self-reference exclusion;
- non-service environment exclusion;
- loopback exclusion;
- external-host exclusion;
- provenance;
- deterministic repeated collection;
- preservation of existing Docker facts.

`DeterministicObservationEngineTest` covers:

- positive service-wiring derivation;
- no derivation without a cross-service reference;
- no derivation without a declared service;
- supporting Fact linkage.
- derivation from either `depends_on` or an environment reference.

```text
FOCUSED_RELATIONSHIP_TESTS = 19 passed
FULL_BACKEND_SUITE = 1070 passed, 0 failed
FULL_AI_ENGINE_SUITE = 109 passed, 0 failed
BACKEND_BUILD = SUCCESS
```

## Runtime Pipeline Trace

Canonical analysis `472a30b5-97f7-47e4-9348-4b4fde3d1b3e` completed and
produced:

```text
DOCKER_SERVICE_ENV_REFERENCE from=ai-engine,to=backend,protocol=http
DOCKER_SERVICE_ENV_REFERENCE from=backend,to=ai-engine,protocol=http
ARCHITECTURE_MODULARIZATION The project defines multiple Docker Compose
  services with inter-service communication references.
```

The relationship evidence traversed the complete existing path:

```text
docker-compose.yml
  -> DockerCollector facts with docker-compose.yml provenance
  -> DOCKER_SERVICE_WIRING deterministic rule
  -> ARCHITECTURE_MODULARIZATION observation
  -> profile compatibility mapping
  -> selected evidence
  -> architecture prompt projection
  -> persisted current-state synthesis
```

Three independent benchmark runs each selected five observations, including
one `ARCHITECTURE_MODULARIZATION` observation, and two
`DOCKER_SERVICE_ENV_REFERENCE` facts.

After hardening target validation and completing `depends_on` support, deployed
smoke analysis `60a11450-0e56-4854-bcac-34cf497748e3` completed with five
selected relationship Facts, all sourced from `docker-compose.yml`:

```text
DOCKER_SERVICE_DEPENDS_ON from=backend,to=ai-engine
DOCKER_SERVICE_DEPENDS_ON from=backend,to=postgres
DOCKER_SERVICE_DEPENDS_ON from=frontend,to=backend
DOCKER_SERVICE_ENV_REFERENCE from=ai-engine,to=backend,protocol=http
DOCKER_SERVICE_ENV_REFERENCE from=backend,to=ai-engine,protocol=http
```

The smoke result retained the `ARCHITECTURE_MODULARIZATION` observation,
produced a four-section synthesis describing explicit dependencies and
interconnected services, and produced zero proposals.

## Product Benchmark (Final Code)

Three fresh `architecture-overview-v2` executions were run against the final
implementation (including hardened `depends_on` support, target validation,
grounding validation, and delta-contract enforcement). All runs completed with
`NO_MATERIAL_DELTA` and zero proposals.

### Run 1

```text
analysisId = 1279de00-f99f-43ec-a42f-b9ba747731bc
taskId = e389e5ed-7420-4685-98ae-c46f47436af1
status = COMPLETED
synthesisSections = 4
proposals = 0
validPathModelCalls = 1
correctiveRetries = 0
CURRENT_STATE_SYNTHESIS_QUALITY = ACCEPTABLE
DELTA_CORRECTNESS = CORRECT (no genuine delta)
```

The synthesis covers application architecture, containerization, testing, and
documentation but adds unsupported scalability/maintainability claims.

### Run 2

```text
analysisId = feeec4fa-79b1-48a5-ba96-a63b73dd1e6d
taskId = 1043944a-ba10-4026-aaaa-3287fdfdcba6
status = COMPLETED
synthesisSections = 4
proposals = 0
validPathModelCalls = 1
correctiveRetries = 0
CURRENT_STATE_SYNTHESIS_QUALITY = ACCEPTABLE
DELTA_CORRECTNESS = CORRECT (no genuine delta)
```

The synthesis describes modular structure and deployment but treats directional
service dependencies as already covered by generic containerization knowledge.

### Run 3

```text
analysisId = a7a7ab1d-c642-4931-bde2-bcf49dc95905
taskId = 9d3e041a-80b9-43e7-ae12-f330331cffe9
status = COMPLETED
synthesisSections = 4
proposals = 0
validPathModelCalls = 1
correctiveRetries = 0
CURRENT_STATE_SYNTHESIS_QUALITY = ACCEPTABLE
DELTA_CORRECTNESS = CORRECT (no genuine delta)
```

Similar coverage with unsupported quality attributions.

### Aggregate Result

```text
STRONG_COUNT = 0
ACCEPTABLE_COUNT = 3
WEAK_COUNT = 0
DELTA_CORRECT_COUNT = 3
TRUST_BOUNDARY_VIOLATIONS = 0
VALID_PATH_MODEL_CALL_COUNT = 1 per run
PRODUCT_GATE_REQUIREMENT = at least 2/3 STRONG, remaining at least ACCEPTABLE
PRODUCT_GATE_RESULT = NOT PASSED (0/3 STRONG)
```

Repeated canonical result reads returned the persisted synthesis without a new
model call. The synthesis created zero proposals and zero trusted Insights.

## Before and After

| Measure | Before Story 0109 | After Story 0109 (Final Code) |
|---|---:|---:|
| Relationship Facts selected per run | 0 | 5 (3 DEPENDS_ON, 2 ENV_REFERENCE) |
| Relationship Observation selected per run | 0 | 1 (ARCHITECTURE_MODULARIZATION) |
| STRONG syntheses | 0/6 | 0/3 |
| ACCEPTABLE syntheses | 6/6 | 3/3 |
| Correct delta conclusions | 6/6 | 3/3 |
| Trust-boundary violations | 0 | 0 |

The deterministic relationship evidence is now collected and selected, but the
synthesis quality remains ACCEPTABLE because the model adds unsupported
scalability/maintainability claims and does not treat the explicit directional
dependency as a genuine enrichment delta over existing generic containerization
knowledge.

## Files

Production changes:

- `backend/src/main/java/com/hopeful117/devlogai/fact/entity/FactType.java`
- `backend/src/main/java/com/hopeful117/devlogai/collection/collector/DockerCollector.java`
- `backend/src/main/java/com/hopeful117/devlogai/collection/observation/DeterministicObservationEngine.java`
- `backend/src/main/java/com/hopeful117/devlogai/profile/service/ProjectProfileServiceImpl.java`

Test changes:

- `backend/src/test/java/com/hopeful117/devlogai/collection/collector/DockerCollectorWiringTest.java`
- `backend/src/test/java/com/hopeful117/devlogai/collection/observation/DeterministicObservationEngineTest.java`

## Final Assessment

```text
STORY_0109_TECHNICAL_RESULT = PASSED
STORY_0109_PRODUCT_EFFECT = PARTIAL (relationship evidence collected, but quality gate not met)
STORY_0108_EVIDENCE_BLOCKER = RESOLVED (relationship evidence now available)
STORY_0108_PRODUCT_GATE = NOT PASSED (0/3 STRONG, requires 2/3)
COMMIT = NOT PERFORMED
PUSH = NOT PERFORMED
```

The implementation remains uncommitted pending final human review.

## Post-0109 Edge-Aware Delta Validation Correction

### Defect corrected

The original Java and Python `hasUncoveredRelationship` implementations treated
an edge as already covered whenever both endpoint names appeared anywhere in
`existingArchitectureKnowledge`. In the canonical benchmark, the generic
containerization Insight referenced both `backend/Dockerfile` and
`ai-engine/Dockerfile`; endpoint co-occurrence therefore suppressed the genuine
new edge `backend -> ai-engine`.

The correction now compares normalized directed edges. Selected relationship
Facts are read from the canonical `from=<source>,to=<target>` representation.
Existing trusted knowledge covers an edge only when one of its semantic fields
(`title`, `content`, `summary`, `rationale`) explicitly contains the same
canonical edge or a directional `source -> target` / `source → target`
expression. Evidence references and unrelated endpoint mentions cannot cover an
edge, and reverse direction remains distinct.

The existing trusted-knowledge model has no structured relationship-kind field,
so this bounded correction compares endpoints and direction only. It does not
introduce fuzzy prose inference or a repository-specific special case.

### Regression coverage

Python and Java tests prove:

- endpoint co-occurrence without a relationship remains uncovered;
- `ai-engine -> backend` does not cover `backend -> ai-engine`;
- an exact `backend -> ai-engine` relationship is covered;
- the Python generation path performs its corrective retry when endpoint-only
  trusted knowledge accompanies an uncovered selected edge;
- exact covered relationships do not trigger a retry.

Validation results:

```text
FOCUSED_PYTHON_RELATIONSHIP_TESTS = PASSED
FOCUSED_JAVA_CONTRACT_TESTS = PASSED
FULL_AI_ENGINE_SUITE = 117 passed, 0 failed
FULL_BACKEND_SUITE = 1084 passed, 0 failed, 0 errors, 0 skipped
```

### Controlled production benchmark

The runtime was rebuilt and recreated with the same project, intent,
`gpt-4.1-mini` model, prompt template, selection rules, context budgets and
trusted-knowledge baseline. Each run selected the same material relationship
Fact `from=backend,to=ai-engine`. Each execution made two model calls: the
initial no-delta response was rejected and one corrective retry was performed.

| Run | Analysis | Task | Result | Synthesis | Delta output | Assessment |
|---|---|---|---|---|---|---|
| 1 | `11699ad1-d01c-4434-b1ae-8c9b925011cf` | `842c3955-a75d-4b69-b08f-20bec8aacdf8` | COMPLETED | Explicit backend-to-ai-engine startup/deployment ordering | One `NEW` proposal | ACCEPTABLE synthesis; delta type incorrect (`ENRICHES` required) |
| 2 | `85331848-74d6-4cff-ba96-125b02c41654` | `4f5051be-310a-43e3-834d-6340424e9f6e` | COMPLETED | Explicit edge, but overstates `depends_on` as runtime communication | One `NEW` proposal | ACCEPTABLE synthesis; delta type and relationship kind incorrect |
| 3 | `e490bd9c-d040-472e-85a0-cd4e7c14b68f` | `d93d3fb5-2032-4ca1-b136-ff258c06cdc6` | FAILED | No persisted synthesis | Corrective retry still omitted required delta | WEAK / invalid output |

Aggregate:

```text
STRONG_COUNT = 0
ACCEPTABLE_COUNT = 2
WEAK_COUNT = 1
CORRECT_DELTA_COUNT = 0
INCORRECT_DELTA_COUNT = 2
FAILED_DELTA_COUNT = 1
TRUST_BOUNDARY_VIOLATIONS = 0
MODEL_CALLS_PER_RUN = 2 (initial + one corrective retry)
PRODUCT_GATE_RESULT = NOT PASSED
```

The correction successfully activates the intended causal control: endpoint
co-occurrence can no longer suppress a missing directional delta, and all three
runs entered the corrective path. It does not by itself make the model produce
the required `ENRICHES` classification reliably. The next blocker is therefore
post-retry delta-type correctness and model output reliability, not relationship
extraction or endpoint-aware coverage detection.

## Post-0109 Mandatory `deltaType` Correction

### Contract verification

The versioned `architecture-overview-v2` Intent lists `deltaType` in
`requiredProposalFields`, while Python previously declared:

```python
delta_type: KnowledgeDeltaType = Field(
    default=KnowledgeDeltaType.NEW, alias="deltaType"
)
```

Consequently, Pydantic accepted an omitted field and serialized it as `NEW`.
The Java architecture callback boundary already rejected a missing field through
`validateArchitectureDelta`; a dedicated v2 regression now proves that behavior.

The same Python output model is used by all generic Insight Intents. Their current
Intent contracts also list `deltaType` as required. Existing v1 and non-architecture
fixtures were therefore made explicit (`NEW`) rather than preserving an invalid
implicit fallback. No delta semantics were added to engineering-event or decision
outputs.

```text
V2_DELTA_TYPE_REQUIRED_BY_INTENT = YES
PYTHON_SCHEMA_BEFORE = optional, default NEW
JAVA_BOUNDARY_BEFORE = missing deltaType rejected for architecture-overview v2
IMPLICIT_NEW_FALLBACK_FOUND = YES, Python InsightProposalOutput only
```

### Minimal correction

The only production change in this corrective iteration removes the Python field
default:

```python
delta_type: KnowledgeDeltaType = Field(alias="deltaType")
```

The generated structured-output schema now includes `deltaType` in `required`
and exposes no default. Missing classification is invalid; explicit `NEW` and
explicit `ENRICHES` retain their existing target rules. The edge-aware
relationship comparison and corrective retry are unchanged.

```text
DELTA_TYPE_NOW_MANDATORY = YES
IMPLICIT_NEW_FALLBACK_REMOVED = YES
EXPLICIT_NEW_PRESERVED = YES
EXPLICIT_ENRICHES_PRESERVED = YES
V1_COMPATIBILITY = PASSED with explicit contract-valid NEW
OTHER_INTENT_COMPATIBILITY = PASSED with explicit contract-valid NEW
TRUST_BOUNDARIES_PRESERVED = YES
```

### Hidden-fallback audit

| Occurrence | Classification | Outcome |
|---|---|---|
| Python `InsightProposalOutput.delta_type` default `NEW` | DEFECT | Removed |
| Java architecture callback `text(payload, "deltaType", ...)` | INTENTIONAL strict boundary | Preserved |
| Java `InsightPromotionService` optional read before creating `DERIVED_FROM` | INTENTIONAL downstream handling after proposal validation | Preserved |
| Result/projection optional `deltaType` reads shared with non-Insight proposal types | UNRELATED | Preserved |
| Optional `targetInsightId` | INTENTIONAL because `NEW` forbids a target | Preserved |
| Test fixtures omitting `deltaType` | DEFECT in fixtures relative to current Intent contract | Updated to explicit `NEW` |

No constructor, mapper, persistence, Jackson, null-coalescing or database default
that manufactures `NEW` was found.

### Test-first evidence

Before the implementation change, the new Python regression failed because only
one provider request occurred: the missing field was silently accepted as `NEW`
instead of triggering corrective retry. The new Java v2 boundary regression
passed before production changes, proving Core was already strict.

After correction:

```text
FOCUSED_PYTHON_TESTS = 29 passed, 0 failed
FOCUSED_JAVA_TESTS = 10 passed, 0 failed
AI_ENGINE_SUITE = 119 passed, 0 failed
BACKEND_SUITE = 1085 passed, 0 failures, 0 errors, 0 skipped
DIFF_CHECK = PASSED
```

### Controlled three-run benchmark

The backend and AI Engine images were rebuilt and containers recreated. Recreate
removed the ephemeral Git authentication state used by the collection workspace;
the first setup attempt failed before AI submission. The actual three-run batch
used a temporary local mirror of the unchanged `origin/main` commit
`2e849641cf74361d7703e4b3f53609b9c5b3e83e` solely for workspace synchronization.
The mirror and temporary container Git mapping were removed after the batch.
This setup failure is not counted as a benchmark run.

Stable benchmark controls:

```text
provider = openai
model = gpt-4.1-mini
intent = architecture-overview-v2
prompt = unchanged
corrective retry = unchanged
selection and budgets = unchanged
trusted knowledge = unchanged
output token limit = unchanged
source revision = 2e849641cf74361d7703e4b3f53609b9c5b3e83e
```

| Run | Analysis | Task | Observable initial result | Retry | Final result | Assessment |
|---|---|---|---|---|---|---|
| 1 | `10628c4f-e29c-4f40-95e5-f5e0580c8d24` | `0a738451-1109-440e-9ac5-915fb1cd6d7c` | zero proposals; uncovered-edge validation error (proven by exact retry digest reconstruction) | Yes | COMPLETED; explicit `NEW`; no target | ACCEPTABLE structure, incorrect delta, unsupported runtime claim |
| 2 | `60612bb0-8828-4578-bb87-5f4480e3ff76` | `bc11fef3-07cb-4ff6-ad0a-33392c3fb6d0` | invalid output; exact raw initial output not persisted | Yes | FAILED; foreign grounding reference `01628c4f-e29c-4f40-95e5-f5e0580c8d24` | WEAK / invalid output |
| 3 | `992f91f0-63d1-4780-a595-f0659cdd9799` | `3cbb09cb-229b-44a2-9892-7c4b433d8274` | zero proposals; uncovered-edge validation error (proven by exact retry digest reconstruction) | Yes | COMPLETED; explicit `NEW`; no target | ACCEPTABLE structure, incorrect delta, unsupported runtime/communication claims |

Each run made exactly two successful OpenAI Responses calls, so corrective retry
occurred 3/3 times. For runs 1 and 3, rebuilding the original and relationship-
error retry prompts from the immutable task snapshot reproduced the persisted
final prompt digest exactly. Run 2's initial invalid response cannot be recovered:
the provider does not retain raw unsuccessful outputs. Its retry failed with a
foreign synthesis grounding UUID.

The mandatory schema guarantees that every parsed proposal carries an explicit
classification. No accepted or persisted proposal can now acquire `NEW` from an
application default. The two completed runs nevertheless returned explicit
`NEW`, so the aggregate product result is unchanged from the edge-aware batch.

```text
RUN_1 = COMPLETED, retry, explicit NEW, target omitted, ACCEPTABLE, incorrect delta
RUN_2 = FAILED after retry, INVALID_LLM_OUTPUT, WEAK
RUN_3 = COMPLETED, retry, explicit NEW, target omitted, ACCEPTABLE, incorrect delta

CORRECTIVE_RETRY_COUNT = 3
EXPLICIT_ENRICHES_COUNT = 0
EXPLICIT_NEW_COUNT = 2
MISSING_DELTA_TYPE_COUNT = 0 in parsed/final proposals; run-2 raw initial output unavailable

STRONG_COUNT = 0
ACCEPTABLE_COUNT = 2
WEAK_COUNT = 1
CORRECT_DELTA_COUNT = 0
INCORRECT_DELTA_COUNT = 2
FAILED_DELTA_COUNT = 1
TRUST_BOUNDARY_VIOLATIONS = 0
PRODUCT_GATE = FAILED
```

Unsupported product claims remain separate from trust-boundary violations. Run 1
called Compose `depends_on` an explicit runtime relationship; run 3 inferred
runtime communication and an interaction path. The deterministic evidence proves
ordering/dependency configuration, not runtime communication.

### Causal conclusion

```text
DID_MANDATORY_DELTA_TYPE_REMOVE_IMPLICIT_NEW_FALLBACK = YES
DID_MANDATORY_DELTA_TYPE_IMPROVE_DELTA_CLASSIFICATION = NO
DID_MANDATORY_DELTA_TYPE_ALONE_REACH_PRODUCT_GATE = NO
REMAINING_PRIMARY_LIMITATION = classification-aware and target-aware corrective retry under high-redundancy context
```

The isolated hypothesis is resolved as Outcome B: the fallback was a real contract
defect and is now removed, but the model still explicitly chooses `NEW`. Do not
restore the default. Per the corrective scope, no retry, prompt, target guidance,
context projection, selection or model change was implemented.

## Post-Retry Relationship-Aware Correction

### Contract verification

The original corrective retry appended only the validation message. The
relationship-aware retry now carries a structured `RelationshipRetryContext`
containing the uncovered directional relationships and the candidate trusted
architecture items that share evidence references with them. The retry prompt
includes:

- the exact relationship type, source, target, direction and evidence
  references;
- the exact candidate trusted Insight UUIDs, titles and contents;
- a neutral classification contract reminding the model that `NEW` means
  genuinely new knowledge and `ENRICHES` means material refinement of a
  supplied trusted item, without prescribing either choice.

No change was made to the initial prompt, the schema, the model, the context
budgets, the selection, or the directional validator.

### Implementation

Production changes are limited to the Python AI Engine:

- `InsightOutputValidationError` now optionally carries a
  `RelationshipRetryContext`.
- `InsightPromptBuilder.corrective_retry` accepts an optional
  `RelationshipRetryContext` and appends neutral, structured guidance.
- `InsightGenerationService` builds that context when an uncovered relationship
  exists without a proposal, attaches it to the validation error, and passes it
  through the retry builder.

The edge-aware validator, mandatory `deltaType`, and all existing validation
are unchanged. Java callback boundary did not change.

```text
INITIAL_PROMPT_UNCHANGED = YES
SCHEMA_UNCHANGED = YES
MODEL_UNCHANGED = YES
CONTEXT_BUDGETS_UNCHANGED = YES
SELECTION_UNCHANGED = YES
DIRECTIONAL_VALIDATOR_UNCHANGED = YES
JAVA_BOUNDARY_UNCHANGED = YES
RETRY_NOW_RELATIONSHIP_AWARE = YES
RETRY_NOW_CLASSIFICATION_AWARE = YES
RETRY_NOW_TARGET_AWARE = YES
```

### Test-first evidence

Before the implementation change, the new retry tests failed because the retry
contained only the generic error message without the relationship, direction,
evidence, or candidate target details. After correction:

```text
FOCUSED_PYTHON_RETRY_TESTS = 6 passed
AI_ENGINE_SUITE = 122 passed, 0 failed
BACKEND_SUITE = 1085 passed, 0 failures, 0 errors, 0 skipped
DIFF_CHECK = PASSED
```

### Controlled three-run benchmark

The AI Engine image was rebuilt and the container recreated. The backend
container was not rebuilt; a temporary local mirror of the unchanged
`origin/main` commit `2e849641cf74361d7703e4b3f53609b9c5b3e83e` was used only
for workspace synchronization and removed after the batch.

Stable benchmark controls:

```text
provider = openai
model = gpt-4.1-mini
intent = architecture-overview-v2
prompt = unchanged (123,657 chars initial; 125,341 chars retry)
corrective retry = relationship-aware, classification-aware, target-aware
selection and budgets = unchanged
trusted knowledge = unchanged
output token limit = unchanged
source revision = 2e849641cf74361d7703e4b3f53609b9c5b3e83e
```

| Run | Analysis | Task | Retry | Classification | Target | Assessment |
|---|---|---|---|---|---|---|
| 1 | `c5064be0-38f2-44a1-8087-252ee9e28992` | `e2062af5-e7d4-41e1-8588-e3eb1489cee6` | Yes | ENRICHES | exact | ACCEPTABLE synthesis; relationship described as runtime interaction |
| 2 | `579c64d9-5d90-494f-943b-37229ac6ce4c` | `c344534f-5190-42a3-98c3-26cfd143886a` | Yes | ENRICHES | exact | ACCEPTABLE synthesis; runtime communication implied |
| 3 | `73978d2a-679c-48a1-90be-a34058bea3f6` | `53168fa2-d5d1-4dbd-8d44-e97176b35362` | Yes | ENRICHES | exact | ACCEPTABLE synthesis; runtime orchestration relationship claimed |

Each run made exactly two successful OpenAI calls: one initial generation with
no proposals, rejected by the uncovered-relationship validator, and one
relationship-aware corrective retry.

The initial no-proposal outputs were not persisted. Reconstructing the initial
and retry prompts from the immutable task snapshots reproduces the persisted
final prompt digests exactly.

```text
RUN_1 = ACCEPTABLE, ENRICHES with correct target, incorrect runtime claim
RUN_2 = ACCEPTABLE, ENRICHES with correct target, incorrect runtime claim
RUN_3 = ACCEPTABLE, ENRICHES with correct target, incorrect runtime claim

CORRECTIVE_RETRY_COUNT = 3
EXPLICIT_ENRICHES_COUNT = 3
EXPLICIT_NEW_COUNT = 0
CORRECT_TARGET_COUNT = 3

STRONG_COUNT = 0
ACCEPTABLE_COUNT = 3
WEAK_COUNT = 0
CORRECT_DELTA_COUNT = 3
INCORRECT_DELTA_COUNT = 0
FAILED_DELTA_COUNT = 0
TRUST_BOUNDARY_VIOLATIONS = 0
PRODUCT_GATE = FAILED
```

The delta classification and target selection are now correct in all three
runs. The remaining product-quality gap is that the ACCEPTABLE syntheses
extrapolate unsupported runtime communication or interaction claims from Docker
Compose `depends_on`, which only specifies startup ordering and service
availability constraints.

### Causal conclusion

```text
DID_RELATIONSHIP_AWARE_RETRY_IMPROVE_DELTA_CLASSIFICATION = YES
DID_RELATIONSHIP_AWARE_RETRY_IMPROVE_TARGET_SELECTION = YES
DID_RELATIONSHIP_AWARE_RETRY_ALONE_REACH_PRODUCT_GATE = NO

REMAINING_PRIMARY_LIMITATION =
grounding overclaim: ACCEPTABLE synthesis extrapolates runtime
communication from Docker Compose startup ordering evidence
```

The corrective scope ends here. The next evidence-supported candidate is a
grounding-aware synthesis correction or a prompt ablation that separates the
small delta-comparison surface from the broad synthesis context. Do not change
the retry, schema, or validator.
