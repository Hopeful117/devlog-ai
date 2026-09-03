# Story 0110 — Implement the Minimal Replayable AI Intent Evaluation Harness

## Status

**IMPLEMENTED_AWAITING_FINAL_HUMAN_REVIEW — VERIFIED (42 FOCUSED, 171 FULL AI ENGINE TESTS)**

## Problem

The Post-0109 investigations produced a reviewed regression case for
`architecture-overview-v2`, but the executable benchmark remains under
`/tmp/opencode/benchmark/`. Its fixtures, semantic expectations, grounding
review, and false-`STRONG` regression are therefore not durable repository
evidence and cannot protect normal development.

Ordinary AI Engine tests cover production contracts and services, but they do
not answer this complete question from persisted artifacts:

> Does this reviewed replay still satisfy the scenario's declared structural,
> semantic, grounding, and trust expectations?

Copying the temporary benchmark would preserve accidental architecture:
absolute paths, generated identifiers, scenario-specific rules, a live OpenAI
runner, and a keyword-based grounding scanner. Keeping it temporary would lose
the reproducible behavior boundary decided by ADR-066.

## Context

The relationship between the preceding work and this Story is:

```text
Story 0109 / Post-0109
        -> produced the regression case and reviewed behavioral evidence

ADR-066
        -> decided the persistence architecture and trust boundaries

Story 0110
        -> implements the smallest persistent replayable vertical slice
```

Story 0110 does not reopen Story 0109 acceptance criteria or redesign ADR-066.
It turns the accepted architecture into implementation-ready scope.

Repository inspection established these implementation facts:

- `app.schemas.ai_task.PromptRequest` is the production Pydantic input contract;
- `app.schemas.insight.InsightGenerationOutput` is the current parsed production
  output contract needed by the canonical replay;
- `app.schemas.insight.KnowledgeDeltaType` contains only `NEW` and `ENRICHES`;
- `InsightProposalOutput` already enforces target presence for `ENRICHES` and
  target absence for `NEW`;
- `pyproject.toml` configures pytest to discover `ai-engine/tests/`;
- no repository CI workflow exists that requires a separate evaluation job;
- the runtime Docker image copies only `ai-engine/app/`, so evaluation remains
  outside the production image without a Dockerfile change.

These are implementation choices derived from current repository structure,
not additional architectural decisions attributed to ADR-066.

## Goal

Persist one deterministic, replay-first AI Intent evaluation slice consisting
of:

```text
ONE CANONICAL JSON SCENARIO
        +
ONE REVIEWED PARSED REPLAY
        +
ONE GENERIC DETERMINISTIC EVALUATOR
        +
ONE SMALL EXPLICIT RUNNER
        +
REGRESSION TESTS
        |
        v
REPRODUCIBLE AI INTENT EVALUATION IN THE NORMAL PYTEST SUITE
```

The canonical scenario is:

```text
scenario          = architecture-overview-v2-enriches-v1
Intent            = architecture-overview / v2
selected relation = configured backend -> ai-engine dependency/reference
trusted target    = existing containerization Insight
expected delta    = ENRICHES
expected target   = exact deterministic fixture UUID
```

The replay path must perform zero network or model calls and deterministically
answer whether the reviewed output remains structurally valid, semantically
correct, grounded within its declared boundary, trust-safe, `STRONG`, and able
to pass its scenario-owned gate.

## Scope

### In Scope

- create the ADR-066 `ai-engine/evaluations/` package and operational README;
- define small typed Pydantic models for scenario, replay, result, and gate data;
- load one explicitly selected JSON scenario and reviewed JSON replay;
- validate the controlled request through production `PromptRequest`;
- validate parsed replay output through production `InsightGenerationOutput`;
- validate required selected inputs, expected delta, target, and fixture-local
  deterministic grounding/trust constraints before evaluation;
- evaluate proposal presence, delta, exact target, deterministic grounding,
  reviewed qualitative grounding, trust, quality, counters, and gate;
- emit concise text and JSON-compatible reports;
- add pure evaluator tests, scenario validation tests, and one canonical replay
  integration test under the existing pytest path;
- document operation and future scenario authoring in the evaluation README.

### Expected Files

```text
ai-engine/evaluations/__init__.py
ai-engine/evaluations/README.md
ai-engine/evaluations/evaluator.py
ai-engine/evaluations/runner.py
ai-engine/evaluations/scenarios/architecture-overview-v2-enriches-v1/scenario.json
ai-engine/evaluations/scenarios/architecture-overview-v2-enriches-v1/replay.json
ai-engine/tests/test_evaluation_harness.py
```

One evaluation-only `models.py` or `loader.py` may be added only if keeping that
responsibility in `evaluator.py` or `runner.py` materially harms clarity. No
layer is required merely to match a framework pattern.

## Non-Goals

- live LLM execution;
- provider orchestration or a provider abstraction;
- model benchmarking or model-drift measurement;
- a generic AI evaluation framework;
- plugins or dynamic scenario discovery;
- Agent, trajectory, or tool-use evaluation;
- RAG, retrieval, embedding, vector-recall, or RAGAS evaluation;
- an LLM judge;
- a keyword-based natural-language grounding scanner;
- database-backed run history;
- dashboards or benchmark-management UI;
- statistical experiments or multi-run live aggregation;
- automatic golden-output generation;
- raw provider HTTP payload persistence;
- production proposal mutation, acceptance, or rejection;
- trusted knowledge creation, mutation, or promotion;
- changes to production prompts, schemas, validators, retries, services, or
  Intent behavior;
- a new CI workflow or scheduled live-model CI.

These exclusions must not enter implementation opportunistically.

## Architecture Constraints

### Dependency and trust boundary

The required dependency direction is:

```text
evaluations -> app production contracts

app -X-> evaluations
```

Evaluation output is engineering evidence only. The harness must not accept or
reject a domain proposal, bypass human validation, promote knowledge, write
trusted artifacts, mutate project state, or become an input to production
generation. Removing `ai-engine/evaluations/` must not change production runtime
behavior.

The implementation must preserve:

```text
EDGE_AWARE_RELATIONSHIP_VALIDATION
MANDATORY_DELTA_TYPE
CLASSIFICATION_AWARE_RETRY
TARGET_AWARE_RETRY
GROUNDING_AWARE_SYNTHESIS_CONTRACT
ADR006_TRUST_BOUNDARY
```

### Replay boundary

Replay consumes a reviewed parsed output. It is not a raw provider transcript,
a stored prompt, or proof of current live-model quality. Given the same
scenario JSON, replay JSON, and evaluator version, scoring and reporting data
must be identical except for optional presentation-only timestamps. Network,
credentials, randomness, wall-clock semantic logic, and external state must not
influence evaluation.

`EXECUTION_FAILED` remains part of the result vocabulary so a later explicit
live mode is not blocked, but Story 0110 does not add live execution or fake
future interfaces.

### Scenario ownership

Scenario-specific facts and answers belong in `scenario.json`. The evaluator
must not contain `backend`, `ai-engine`, `Docker`, `containerization`, the
canonical target UUID, or a rule that this relationship means `ENRICHES`.

The generic validator resolves only declared references. It must not search for
scenario meaning by content, title, or keywords.

## V1 Scenario Contract

### Chosen representation

Story 0110 chooses the smallest repository-native path:

```text
JSON
  -> evaluation-owned Pydantic scenario model
  -> PromptRequest.model_validate(...)
  -> deterministic cross-field/reference validation
```

No JSON Schema tool, YAML dependency, or custom schema language is introduced.
JSON aliases use lower camel case to match production transport conventions.

### Required top-level fields

Every V1 `scenario.json` must contain exactly these conceptual fields:

| Field | Required V1 meaning |
|---|---|
| `scenarioId` | Stable scenario identifier. |
| `scenarioVersion` | Semantic version for material fixture, expectation, evidence-boundary, or gate changes. |
| `intent` | Object containing required `id` and `version`; both must match `promptRequest.intent`. |
| `promptRequest` | Complete controlled request validated by production `PromptRequest`. |
| `requiredSelectedInputs` | Non-empty declared references required for this behavior. |
| `expectation` | Expected delta, target, deterministic constraints, and qualitative-review requirement. |
| `gate` | Scenario-owned integer thresholds. |
| `reproducibility` | Repository revision, prompt version/digest, and output schema digest for the controlled case. |

Unknown scenario-model fields must be rejected to expose stale or misspelled
configuration.

### Required selected input references

Each `requiredSelectedInputs` entry contains only:

```json
{
  "kind": "FACT | OBSERVATION",
  "id": "fixed UUID"
}
```

For V1, `FACT` resolves by exact `id` in
`promptRequest.selectedKnowledge.selectedFacts`; `OBSERVATION` resolves by exact
`id` in `selectedObservations`. The canonical scenario declares its material
relationship Facts and supporting Observation explicitly. More input kinds are
deferred until a real scenario requires them.

The expected target is resolved separately by exact `insightId` in
`selectedKnowledge.existingArchitectureKnowledge`.

### Expectation fields

The required `expectation` object contains:

| Field | Required V1 meaning |
|---|---|
| `expectedDelta` | `NEW`, `ENRICHES`, or JSON `null`; validate non-null values through `KnowledgeDeltaType`. |
| `expectedTargetId` | Exact UUID for `ENRICHES`; JSON `null` for `NEW` or expected absence. |
| `allowedFactIds` | Exact fixture Fact IDs permitted as proposal grounding references. |
| `allowedObservationIds` | Exact fixture Observation IDs permitted as proposal grounding references. |
| `allowedEvidenceReferences` | Exact evidence-reference strings permitted in proposals and synthesis. |
| `qualitativeGroundingRequired` | Whether a completed reviewed qualitative assessment is required for `STRONG`. |

The allowlists are deterministic constraints, not natural-language claim
classifiers. Every allowlisted ID/reference must itself resolve in the fixture;
an allowlist cannot authorize unknown evidence.

### Expected delta and target semantics

```text
expectedDelta = "NEW"
  -> one or more proposals expected
  -> every actual proposal must have delta NEW
  -> expectedTargetId must be null
  -> actual targets must be absent

expectedDelta = "ENRICHES"
  -> one or more proposals expected
  -> every actual proposal must have delta ENRICHES
  -> expectedTargetId is required and must exist in fixture trusted knowledge
  -> every actual proposal target must equal that exact UUID

expectedDelta = null
  -> zero proposals expected
  -> expectedTargetId must be null
```

V1 evaluates all proposals rather than silently selecting the first. A mixed or
partly incorrect proposal set is incorrect. This is a Story-level implementation
choice that closes an ambiguity without changing ADR-066's production contract.

Production `KnowledgeDeltaType` remains:

```text
NEW | ENRICHES
```

The implementation must not add `NONE`, add `CONTRADICTS`, or introduce a
`ProposalExpectation` enum. If current production contracts contradict these
semantics during implementation, work must stop for human review rather than
changing ADR-066.

### Gate fields

The required canonical gate is the following fixed object shape:

```json
{
  "requiredRuns": 1,
  "minimumStrong": 1,
  "maximumIncorrectDelta": 0,
  "maximumIncorrectTarget": 0,
  "maximumTrustViolations": 0,
  "maximumExecutionFailures": 0
}
```

All values are non-negative integers. V1 does not implement expressions, global
thresholds, percentages, or a gate DSL.

### Reproducibility fields

The scenario's required `reproducibility` object contains:

```text
repositoryRevision
promptVersion
promptContentDigest
schemaDigest
```

Digests are lowercase SHA-256 values. `schemaDigest` identifies the canonical
JSON form of the expected `InsightGenerationOutput` schema; prompt version and
content digest retain their distinct ADR-032 meanings. Temporary Analysis,
request, or task identifiers must not be added merely because they existed in
Post-0109 experiments.

## V1 Replay Contract

`replay.json` contains only:

| Field | Required V1 meaning |
|---|---|
| `scenarioId` | Must match the loaded scenario. |
| `scenarioVersion` | Must match the loaded scenario version. |
| `output` | Reviewed parsed output validated by `InsightGenerationOutput`. |
| `capture` | Minimum metadata listed below. |
| `qualitativeGrounding` | Completed reviewed assessment when the scenario requires one; otherwise nullable. |

The required `capture` metadata is:

```text
repositoryRevision
capturedAt
provider
model
promptVersion
promptContentDigest
schemaDigest
```

It allows a reviewed replay to retain its provenance without retaining raw HTTP
payloads, headers, credentials, hidden reasoning, the full rendered prompt,
provider transport noise, or temporary diagnostics. Scenario and replay
reproducibility values must agree where both identify the same execution
contract.

### Reviewed qualitative grounding

When `qualitativeGroundingRequired` is true, the replay must contain a completed
human-reviewed assessment with these fields:

```text
status = REVIEWED
unsupportedMaterialClaims = non-negative integer
contradictedMaterialClaims = non-negative integer
plausibleButUnprovenMaterialClaims = non-negative integer
limitedNonCriticalImprecision = boolean
notes = optional bounded review explanation
```

The evaluator consumes this declaration; it does not infer those categories
from prose. The report must identify the assessment as reviewed, not
deterministic. Missing required review prevents `STRONG` and is conservatively
`WEAK`. Unsupported or contradicted material claims are `WEAK`. Limited
non-critical imprecision may be `ACCEPTABLE` only when structure, proposal,
delta, target, and trust are otherwise correct. The canonical replay has a
completed review with no disqualifying claims.

No reviewer identity is required in V1. Git review history supplies artifact
accountability without persisting personal data in the fixture.

## Fixture and Replay Validation

Validation must complete before evaluation and must report all useful
validation errors deterministically.

At minimum it verifies:

1. `scenarioId` and `scenarioVersion` are present and non-blank.
2. `promptRequest` validates through production `PromptRequest`.
3. The embedded production `IntentDefinition` validates, and its ID/version
   exactly match the scenario `intent` pair.
4. The request is an Insight-generation request compatible with
   `architecture-overview-v2` output for the canonical scenario.
5. Every declared required selected input resolves by kind and exact UUID.
6. Every grounding allowlist item resolves in its applicable fixture
   collection or selected evidence-reference set.
7. `expectedDelta` is null or a production `KnowledgeDeltaType` value.
8. `ENRICHES` has an exact expected UUID present in
   `existingArchitectureKnowledge`.
9. `NEW` and null expectations have no expected target.
10. Gate fields are valid non-negative integers and coherent for one replay.
11. Replay scenario identity/version match the loaded scenario.
12. Replay output validates through production `InsightGenerationOutput`.
13. Required qualitative review exists and is complete when configured.
14. Scenario/replay reproducibility identities agree.

The AI Engine currently carries the complete Intent definition in
`PromptRequest`; there is no separate Python Intent registry to query. Therefore
"Intent resolves" means successful production `IntentDefinition` validation and
exact agreement between the scenario identity and embedded request identity.
Introducing a second evaluation Intent registry is out of scope.

Expected scenario or fixture failures produce a structured result with
`executionStatus = INVALID_SCENARIO`, a stable validation error code, and no
model-behavior measurement. A replay that is valid JSON but violates the
production output contract is also an invalid artifact under this
pre-evaluation boundary, not `EXECUTION_FAILED` and not model behavior. Where
the invalid fields remain observable, the result exposes the failed dimensions;
in particular, `ENRICHES` without a target is structurally `INVALID`, has
`MISSING_REQUIRED_TARGET`, is `WEAK`, and fails the gate. Earlier parse failures
use `NOT_EVALUATED` dimensions and quality. The runner exits non-zero while
still emitting the structured failure report; it must not expose a raw,
low-context parser traceback as its normal expected-error interface.

## Result Model

The smallest V1 result keeps execution, dimensions, quality, and gate separate.
It exposes:

| Field | V1 values or meaning |
|---|---|
| `scenarioId`, `scenarioVersion` | Evaluated artifact identity. |
| `evaluatorVersion` | Explicit evaluator contract version. |
| `executionStatus` | `INVALID_SCENARIO`, `EXECUTION_FAILED`, `EVALUATED`. |
| `validationErrors` | Stable code/message records; empty for a valid replay. |
| `proposalCount` | Actual number of parsed proposals. |
| `expectedDelta`, `actualDeltas` | Expected nullable delta and all actual production delta values. |
| `expectedTargetId`, `actualTargetIds` | Expected nullable UUID and all actual nullable targets. |
| `structuralValidity` | `VALID`, `INVALID`, `NOT_EVALUATED`. |
| `proposalCorrectness` | `CORRECT`, `INCORRECT`, `NOT_EVALUATED`. |
| `deltaCorrectness` | `CORRECT`, `INCORRECT`, `NOT_EVALUATED`. |
| `targetCorrectness` | `CORRECT_TARGET`, `WRONG_TARGET`, `MISSING_REQUIRED_TARGET`, `TARGET_NOT_APPLICABLE`, `NOT_EVALUATED`. |
| `groundingQuality` | `GROUNDED`, `LIMITED`, `UNSUPPORTED`, `CONTRADICTED`, `REVIEW_REQUIRED`, `NOT_EVALUATED`. |
| `trustSafety` | `SAFE`, `VIOLATION`, `NOT_EVALUATED`. |
| `overallQuality` | `STRONG`, `ACCEPTABLE`, `WEAK`, `NOT_EVALUATED`. |
| `gateResult` | `PASSED`, `FAILED`, `NOT_EVALUATED`. |
| `counters` | Runs, qualities, incorrect deltas/targets, trust violations, and execution failures. |

`EXECUTION_FAILED` has no ordinary replay producer in Story 0110 but remains a
plain enum value and gate counter from ADR-066. No state machine or live
execution abstraction is added.

For expected `NEW` or expected absence, `TARGET_NOT_APPLICABLE` is the correct
target outcome and counts as target-correct for quality/gate calculations. For
expected `ENRICHES`, absent actual output is `MISSING_REQUIRED_TARGET`; a
different UUID is `WRONG_TARGET`; exact identity is `CORRECT_TARGET`.

## Evaluation Semantics

### Generic deterministic evaluator

The evaluator owns only:

- proposal-presence correctness;
- all-proposal delta correctness;
- exact UUID target correctness;
- structural correctness after production-compatible parsing;
- deterministic grounding allowlist and fixture-membership checks;
- trust-boundary checks observable in replay data;
- consumption of the explicit reviewed qualitative assessment;
- quality derivation;
- counters and scenario-owned gate derivation.

It receives validated models and performs no file discovery, network access,
model call, output repair, production mutation, or scenario-answer inference.

### Quality derivation

For applicable scenarios:

```text
STRONG =
    structurally valid
    AND proposal behavior correct
    AND expected delta correct
    AND expected target correct when applicable
    AND deterministic grounding constraints pass
    AND required qualitative grounding review is complete
    AND no unsupported material claim
    AND no contradicted material claim
    AND no trust-boundary violation
    AND no disqualifying plausible-but-unproven claim presented as project fact
```

`ACCEPTABLE` is available only for semantically correct, structurally valid,
trust-safe output with a completed review identifying limited non-critical
grounding imprecision. It must never mask a wrong delta, wrong required target,
missing required proposal, material structural failure, material grounding
failure, or trust violation.

`WEAK` covers any material semantic, structural, grounding, or trust failure.
In particular:

```text
EXPECTED = ENRICHES + target
ACTUAL   = no proposal + no target
QUALITY  = WEAK
```

Missing outputs increment incorrect delta and, when `ENRICHES` applies,
incorrect target counters. They do not disappear from denominators.

Expected absence remains first-class:

```text
expectedDelta = null
actual proposals = []
target correctness = TARGET_NOT_APPLICABLE
quality = STRONG eligible when every other applicable condition passes
```

### Deterministic grounding and trust

Proposal supporting Fact IDs, supporting Observation IDs, proposal evidence
references, synthesis grounding references, and the expected/actual target are
checked against scenario-declared allowlists and the controlled fixture. A
foreign reference fails deterministic grounding and is reported as a trust
violation because it escapes the scenario's selected evidence boundary.

Natural-language support, contradiction, and qualification are supplied only by
the reviewed qualitative assessment. No keyword search, fuzzy title matching,
semantic target matching, embedding lookup, or dynamic target inference is
permitted.

## Canonical Scenario

Create exactly one scenario directory:

```text
ai-engine/evaluations/scenarios/architecture-overview-v2-enriches-v1/
```

The controlled `PromptRequest` uses fixed UUIDs and only the material input
needed to represent:

- the directional `backend -> ai-engine` startup dependency;
- the configured backend reference/reachability to `ai-engine`;
- explicit protocol only where present in the controlled URL evidence;
- the supporting architecture Observation;
- one existing containerization Insight with a fixed target UUID.

The fixture and reviewed qualitative assessment preserve this evidence boundary:

```text
supported:
  declared startup dependency
  configured service reference/reachability
  configured protocol when explicit

not automatically established:
  successful runtime communication
  actual API invocation
  data exchange
  network reliability
  post-start operational dependency
```

It must not contain the giant experimental context, random UUID generation,
temporary Post-0109 run identifiers, or forensic debug data.

The canonical expectation and result are:

```text
expectedDelta        = ENRICHES
expectedTarget       = exact fixed containerization Insight UUID
executionStatus      = EVALUATED
structuralValidity   = VALID
proposalCorrectness  = CORRECT
deltaCorrectness     = CORRECT
targetCorrectness    = CORRECT_TARGET
groundingQuality     = GROUNDED
trustSafety          = SAFE
overallQuality       = STRONG
gateResult           = PASSED
```

## Runner and Reporting

The V1 invocation is:

```bash
cd ai-engine
python -m evaluations.runner architecture-overview-v2-enriches-v1
```

The identifier resolves only beneath the fixed
`evaluations/scenarios/` directory. The loader must reject path traversal and
does not scan for plugins or construct a registry framework.

The standard text report includes:

```text
scenario and version
execution status
proposal count
expected and actual deltas
expected and actual targets
structural validity
proposal, delta, and target correctness
deterministic and reviewed grounding result
trust safety
overall quality
gate result
```

The same result model must support `model_dump(mode="json")` or an equivalent
JSON-compatible representation. The simplest standard-library argument parsing
and JSON emission are sufficient; no CLI dependency is added. A failed gate or
invalid artifact returns a non-zero process exit code.

## Temporary Benchmark Disposition

Before implementation, inspect `/tmp/opencode/benchmark/` and record this
classification in the implementation report:

| Temporary artifact | Disposition |
|---|---|
| evaluator semantic comparisons and gate counters | `REUSE_CONCEPT`; rewrite against typed V1 models. |
| evaluator regression cases | `REUSE_CONCEPT`; rewrite as pytest tests. |
| live OpenAI run script | `REWRITE`; replace with deterministic repository runner only. |
| keyword-based claim scanner | `DISCARD`. |
| hardcoded/generated fixture builder | `DISCARD`; replace with controlled JSON and fixed IDs. |
| absolute `/tmp` and workspace paths | `DISCARD`. |
| raw/experimental reporting and results archive | `DISCARD` or reduce to V1 result fields. |

No temporary source file is copied wholesale.

## Implementation Plan

1. Reconfirm production `PromptRequest`, `InsightGenerationOutput`, and
   `KnowledgeDeltaType` contracts and stop if they contradict ADR-066.
2. Inspect the temporary benchmark and record `REUSE_CONCEPT`, `REWRITE`, and
   `DISCARD` decisions without copying it.
3. Define the evaluation scenario, replay, result, reviewed-grounding, and gate
   Pydantic models in the smallest clean file structure.
4. Implement explicit identifier/path loading and deterministic pre-evaluation
   validation.
5. Implement the pure generic evaluator, quality derivation, counters, and gate.
6. Rewrite the historical evaluator regression cases as focused pytest tests.
7. Create the one minimal canonical scenario with fixed IDs and material input.
8. Create its reviewed parsed replay and reviewed grounding assessment.
9. Add the canonical integration test through the real loader/evaluator path.
10. Implement the standard-library runner and text/JSON-compatible reporting.
11. Add the concise operational README linked to ADR-066.
12. Run focused tests and the full AI Engine test suite; confirm existing pytest
    discovery supplies normal deterministic CI coverage without credentials or
    workflow changes.
13. Record production-boundary and scope-integrity checks in the implementation
    report.

## Acceptance Criteria

### AC1 — One-way evaluation boundary

Given the evaluation harness exists, when production AI Engine modules are
loaded or their imports are inspected, then no module under `ai-engine/app/`
imports or depends on `evaluations`, while evaluation may import production
contracts.

### AC2 — Valid canonical replay

Given `architecture-overview-v2-enriches-v1` and its reviewed replay, when the
real loader and evaluator path runs, then status is `EVALUATED`, structure and
proposal behavior are correct, delta is correct, target is `CORRECT_TARGET`,
grounding is `GROUNDED`, trust is `SAFE`, quality is `STRONG`, and the gate is
`PASSED`.

### AC3 — Historical false positive is blocked

Given an `ENRICHES` scenario requiring target A and a production-compatible
replay containing no proposal and no target, when evaluated, then proposal and
delta correctness are `INCORRECT`, target correctness is
`MISSING_REQUIRED_TARGET`, quality is `WEAK`, and the gate fails.

### AC4 — Legitimate no-proposal behavior

Given `expectedDelta` is JSON null and the replay contains no proposal, when all
applicable structure, grounding, and trust expectations pass, then proposal and
delta behavior are correct, target is `TARGET_NOT_APPLICABLE`, and the replay is
eligible for `STRONG`.

### AC5 — Invalid fixture is not model behavior

Given required selected inputs are declared but absent from the controlled
`PromptRequest`, when the scenario is loaded, then status is
`INVALID_SCENARIO`, validation identifies the missing references, no output is
evaluated, quality is not `STRONG`, and no model call occurs.

### AC6 — Unsupported expected delta is rejected

Given a scenario declares `CONTRADICTS`, when scenario validation runs, then it
is rejected as `INVALID_SCENARIO`; no `CONTRADICTS`, `NONE`, or
`ProposalExpectation` enum is introduced and production `KnowledgeDeltaType`
remains unchanged.

### AC7 — Missing required target is weak

Given expected delta `ENRICHES` and exact target A, when a valid replay contains
an `ENRICHES` proposal without a target, then production-contract validation
marks the replay structurally invalid, target correctness is
`MISSING_REQUIRED_TARGET`, overall quality is `WEAK`, and the gate fails. It is
never repaired, silently omitted, or awarded `STRONG`.

### AC8 — Wrong target is weak

Given expected target A and actual target B, when evaluated, then target
correctness is `WRONG_TARGET`, quality is `WEAK`, the incorrect-target counter
increments, and the gate fails.

### AC9 — Trust violation rejects strong quality

Given otherwise correct semantics and a foreign Fact, Observation, evidence, or
target reference outside the scenario-declared fixture boundary, when
evaluated, then grounding fails, trust is `VIOLATION`, quality is `WEAK`, and
the gate fails.

### AC10 — Replay has no model dependency

Given the focused and full AI Engine pytest suites run in a clean environment,
then no provider credential is required, no network or model call occurs, and
no live execution is discoverable by ordinary pytest.

### AC11 — Production contracts are reused

Given scenario and replay JSON are loaded, then the request validates through
`PromptRequest`, replay output validates through `InsightGenerationOutput`, and
non-null expected delta validates through `KnowledgeDeltaType`; shadow copies
of these contracts do not exist in evaluation code.

### AC12 — Qualitative grounding remains explicit

Given a scenario requires qualitative grounding, when no completed reviewed
assessment is present, then grounding is `REVIEW_REQUIRED` and `STRONG` is not
awarded. When an assessment reports unsupported or contradicted material
claims, quality is `WEAK`. No keyword scanner classifies replay prose.

### AC13 — NEW without target is supported

Given expected delta `NEW`, a valid `NEW` proposal, and no expected or actual
target, when evaluated, then delta is correct, target is
`TARGET_NOT_APPLICABLE`, and the replay remains eligible for `STRONG`.

### AC14 — Missing outputs remain in denominators

Given one or more positive expectations with absent proposals, deltas, or
required targets, when counters and the gate are derived, then every attempted
replay remains in total runs and every missing applicable value increments its
incorrect counter.

### AC15 — Result and runner reporting are multidimensional

Given a valid or invalid explicitly selected scenario, when the runner finishes,
then it emits a concise text report and JSON-compatible structured result with
the required execution, structure, proposal, delta, target, grounding, trust,
quality, counter, and gate fields; a failed gate or invalid artifact returns a
non-zero exit status.

### AC16 — Canonical fixture preserves the reviewed evidence boundary

Given the committed canonical scenario, when its fixture and review are
inspected, then fixed IDs and minimal directional relationship/reference
evidence support only the declared startup dependency, configured reachability,
and explicit protocol, while runtime communication, API invocation, data
exchange, network reliability, and post-start operational dependency are not
asserted automatically.

### AC17 — Production behavior remains unchanged

Given Story 0110 is implemented, when the diff is inspected and production
regressions run, then production prompts, schemas, validators, retries, services,
packaging, Docker image, and runtime behavior are unchanged, and benchmark
expectations never enter production input.

### AC18 — Temporary artifacts are not migrated accidentally

Given `/tmp/opencode/benchmark/` has been inspected, when the repository diff is
reviewed, then semantic concepts and regression cases have been rewritten,
while the live runner, keyword scanner, generated/hardcoded fixture builder,
absolute paths, debug data, and raw temporary artifacts are absent.

## Test Strategy

### Evaluator unit tests

Pure deterministic tests cover:

- correct `ENRICHES` plus exact target is `STRONG`-eligible;
- no proposal instead of `ENRICHES` is `WEAK`;
- missing required target is never successful;
- wrong exact target is `WEAK`;
- expected absence plus no proposal is `STRONG`-eligible;
- expected absence plus a proposal is incorrect;
- `NEW` without target is supported;
- mixed or partially wrong proposal sets are incorrect;
- unsupported and contradicted reviewed material claims are `WEAK`;
- missing required qualitative review blocks `STRONG`;
- limited non-critical reviewed imprecision can be `ACCEPTABLE` only with
  otherwise correct and safe output;
- a foreign grounding reference fails grounding and trust;
- a trust violation blocks `STRONG`;
- structural invalidity cannot be `STRONG` or `ACCEPTABLE`;
- gate thresholds are scenario-owned and deterministic;
- missing values increment incorrect delta/target denominators.

### Scenario and replay validation tests

Tests cover:

- valid minimal scenario and replay;
- missing required selected input;
- unsupported expected delta including `CONTRADICTS`;
- `ENRICHES` without expected target;
- expected target absent from fixture trusted knowledge;
- target configured for `NEW` or null expectation;
- unresolved allowlist reference;
- scenario/embedded Intent mismatch;
- malformed `PromptRequest`;
- malformed production-compatible replay output;
- scenario/replay identity or reproducibility mismatch;
- missing required reviewed qualitative assessment;
- path traversal rejection in explicit scenario resolution.

The dedicated Phase-4-class regression removes a declared required selected
input from the fixture and proves `INVALID_SCENARIO`, no evaluated model
behavior, no model call, and no false `STRONG`.

### Canonical replay integration test

The integration test loads the committed `scenario.json` and `replay.json`
through the actual loader and evaluator, without mocking the core replay path.
It asserts `EVALUATED`, every required correct/safe dimension, `STRONG`, and
`PASSED`.

### Architecture and determinism tests

- cheaply inspect Python imports under `ai-engine/app/` with the standard
  library AST and assert none targets `evaluations`;
- run the same scenario/replay twice and assert equal JSON-compatible result
  data;
- fail a test if network/model code is invoked; the implementation should need
  no network mock because the replay path imports no provider runner.

### Required commands

From `ai-engine/`, run at least:

```bash
python -m pytest tests/test_evaluation_harness.py
python -m pytest
```

Because the test is under the existing configured `tests/` path, normal AI
Engine pytest execution is the CI integration. No new workflow is planned.
Backend tests are optional because no backend or production contract change is
allowed; run them only if implementation evidence reveals an unexpected shared
impact.

## README Requirements

`ai-engine/evaluations/README.md` must explain operationally:

- the Intent replay-regression purpose;
- why replay is deterministic and distinct from future live evaluation;
- scenario/replay directory and contract structure;
- the exact `python -m evaluations.runner ...` invocation;
- how to add a future reviewed canonical scenario without modifying evaluator
  answers;
- required fixed IDs, fixture validation, and qualitative review handling;
- prohibited model calls, trust mutation, generic framework growth, Agent/RAG
  evaluation, and keyword grounding scans;
- the ADR-066 reference.

It must not duplicate the ADR.

## Security and Data Hygiene

The canonical artifacts use controlled fixture data and fixed synthetic UUIDs.
They must contain no API key, provider credential, personal secret, sensitive
production data, raw headers, hidden reasoning, or provider transport payload.
Capture metadata must be reviewed before commit.

## Risks and Mitigations

### Benchmark overfitting

Risk: evaluator logic could encode the one Post-0109 answer.

Mitigation: keep all component names, relationships, evidence boundaries,
expected deltas, and targets in scenario/replay data; test generic mechanics
with synthetic cases.

### Fixture brittleness

Risk: large or transport-heavy fixtures create noisy failures.

Mitigation: persist only material controlled `PromptRequest` content, fixed IDs,
and minimal capture metadata; version material scenario changes.

### Shadow production contracts

Risk: evaluation-owned request/output copies drift from runtime behavior.

Mitigation: validate with `PromptRequest`, `InsightGenerationOutput`, and
`KnowledgeDeltaType`; wrappers contain only evaluation semantics.

### Production/evaluation coupling

Risk: production behavior starts depending on benchmark expectations or files.

Mitigation: enforce one-way imports, keep evaluation outside `app` and the
runtime image, and inspect the production diff explicitly.

### Qualitative review mislabeled deterministic

Risk: human judgment could be presented as automated semantic proof.

Mitigation: model reviewed assessment separately, report its status visibly,
and reject `STRONG` when a required review is absent.

### Replay success mistaken for live quality

Risk: deterministic replay may be interpreted as current provider/model
performance.

Mitigation: state the replay/live distinction in README and reports and retain
capture metadata; Story 0110 performs no live execution.

### Scope creep into a generic framework

Risk: one scenario triggers plugins, registries, provider abstractions, run
storage, dashboards, Agent evaluation, or RAG evaluation.

Mitigation: explicit path resolution, one evaluator, one runner, one scenario,
plain Pydantic models, and the stated non-goals.

## Definition of Done

Story 0110 is complete only when:

1. ADR-066's minimal evaluation directory exists in the expected location.
2. Exactly one canonical JSON scenario is persisted and deterministically valid.
3. Exactly one reviewed parsed replay is persisted.
4. Replay performs zero network/model calls and needs no provider credential.
5. The generic evaluator contains no canonical scenario answer.
6. The canonical `ENRICHES` plus exact target replay evaluates `STRONG`.
7. The canonical one-run gate evaluates `PASSED`.
8. The historical no-proposal false-`STRONG` regression evaluates `WEAK`.
9. Legitimate expected absence remains `STRONG`-eligible.
10. Missing outputs remain in delta and target correctness denominators.
11. Invalid fixture input is rejected before evaluation.
12. Unsupported expected delta configuration is rejected.
13. Exact target outcomes distinguish correct, wrong, missing, and not
    applicable.
14. Production input/output/delta contracts are reused where specified.
15. Production never imports evaluation code.
16. The keyword grounding scanner and hardcoded temporary fixture are absent.
17. Deterministic grounding and reviewed qualitative grounding remain visibly
    distinct.
18. Focused evaluation tests pass.
19. The full AI Engine pytest suite passes.
20. Existing normal pytest execution includes the canonical replay without CI
    workflow changes.
21. The operational README is complete and links ADR-066.
22. Security/data-hygiene review finds no secret or raw provider artifact.
23. Production prompts, schemas, validators, retries, services, and behavior are
    unchanged.
24. The implementation report records temporary-artifact disposition, test
    evidence, and production-boundary checks.

Required completion confirmation:

```text
PRODUCTION_PROMPT_CHANGED = NO
PRODUCTION_SCHEMA_CHANGED = NO
PRODUCTION_VALIDATOR_CHANGED = NO
PRODUCTION_RETRY_CHANGED = NO
PRODUCTION_SERVICE_CHANGED = NO
```

## Delivery Boundaries

- Human review of this Story is required before implementation.
- Story 0110 authoring creates no evaluation directory or implementation file.
- Implementation uses the existing AI Engine pytest path rather than changing
  CI configuration.
- No live model execution is required for acceptance.
- No backend implementation is planned.
- If implementation reveals a genuine contradiction with ADR-066, stop and
  report it; do not silently alter production contracts or architecture.
- Commit, push, and merge remain separate human-authorized actions.

## Open Questions

No unresolved question blocks implementation.

The implementer may keep evaluation models in `evaluator.py` or introduce one
small `models.py`, and may keep loading in `runner.py` or introduce one small
`loader.py`, based on the resulting file clarity. These are local organization
choices and must not add abstractions or expand scope.

## ADR References

- `docs/decisions/ADR-066.md` — governing replayable Intent evaluation
  architecture.
- `docs/decisions/ADR-006.md` — proposal and trusted-knowledge promotion
  boundary.
- `docs/decisions/ADR-028.md` — Intent identity and versioning.
- `docs/decisions/ADR-032.md` — prompt version and digest semantics.
- `docs/decisions/ADR-060.md` — deterministic, probabilistic, and governed-effect
  boundaries.
- `docs/decisions/ADR-063.md` — engineering-context retrieval ownership.
- `docs/decisions/ADR-064.md` — analysis-context composition ownership.
- `docs/decisions/ADR-065.md` — synthesis/proposal separation.
- `docs/stories/0109-produce-deterministic-architectural-relationship-evidence/`
  — source regression behavior, not reopened scope.
- `docs/investigations/post-0109-new-vs-enriches-investigation.md`
- `docs/investigations/post-0109-synthesis-grounding-investigation.md`

## Lifecycle State

- Story materialization: completed
- ADR-066 alignment review: completed during authoring
- Human Story review: approved for implementation by task request
- Implementation: completed
- Verification: completed (`42` focused, `171` full AI Engine tests)
- Commit: not authorized
- Push: not authorized
- Merge: human-only

Terminal implementation state:

`MINIMAL_REPLAYABLE_AI_INTENT_EVALUATION_HARNESS_IMPLEMENTED_AWAITING_FINAL_HUMAN_REVIEW`
