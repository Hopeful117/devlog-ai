# Story 0110 — Implementation Report

## Status

**IMPLEMENTED_AWAITING_FINAL_HUMAN_REVIEW — VERIFIED (42 FOCUSED, 171 FULL AI ENGINE TESTS)**

## Summary

Implemented the minimal replay-first Intent evaluation harness authorized by
ADR-066. One versioned controlled scenario and one reviewed parsed replay now
exercise a generic deterministic evaluator through the normal AI Engine pytest
suite. The canonical result is `STRONG` and its one-run gate passes without a
credential, network call, or model call.

## Baseline

```text
BRANCH = main
HEAD = 2e849641cf74361d7703e4b3f53609b9c5b3e83e
INITIAL_WORKTREE = dirty with intentional Post-0109 production and documentation work
```

The production contracts were confirmed before implementation:

```text
PromptRequest = production request contract
InsightGenerationOutput = production parsed replay contract
KnowledgeDeltaType = NEW | ENRICHES
ENRICHES_TARGET = required
NEW_TARGET = forbidden
```

No contradiction with Story 0110 or ADR-066 was found.

## Implemented Slice

- evaluation-owned Pydantic scenario, replay, result, and gate models;
- production `PromptRequest`, `InsightGenerationOutput`, and
  `KnowledgeDeltaType` reuse;
- explicit scenario identifier resolution beneath one fixed scenarios root;
- deterministic fixture, cross-reference, prompt/schema digest, and replay
  validation;
- all-proposal delta and exact UUID target evaluation;
- separate deterministic grounding and reviewed qualitative grounding;
- trust, quality, counters, and scenario-owned gate derivation;
- text and JSON-compatible runner output;
- one canonical controlled scenario and one reviewed replay;
- focused evaluator, validation, architecture, determinism, runner, and
  integration tests.

## Temporary Benchmark Disposition

| Temporary artifact | Disposition |
|---|---|
| semantic comparisons and gate counters | `REUSE_CONCEPT`; rewritten with typed models |
| evaluator regression cases | `REUSE_CONCEPT`; rewritten as pytest tests |
| live OpenAI runner | `REWRITE`; replaced by deterministic replay runner only |
| keyword grounding scanner | `DISCARD` |
| generated/hardcoded fixture builder | `DISCARD`; replaced with fixed reviewed JSON |
| absolute `/tmp` paths | `DISCARD` |
| raw experimental reporting/results | `DISCARD`; reduced to V1 result dimensions |

```text
TMP_FILE_COPIED_WHOLESALE = NO
```

## Architecture

```text
EVALUATION_LOCATION = ai-engine/evaluations/
DEPENDENCY_DIRECTION = evaluations -> app production contracts
PRODUCTION_IMPORTS_EVALUATION = NO
LIVE_MODE_IMPLEMENTED = NO
NETWORK_DEPENDENCY = NO
MODEL_DEPENDENCY = NO
NEW_FRAMEWORK_DEPENDENCY = NO
```

The runtime Dockerfile and production package configuration remain unchanged.
Evaluation code is not copied into the runtime image.

## Canonical Scenario

```text
SCENARIO_ID = architecture-overview-v2-enriches-v1
SCENARIO_VERSION = 1.0.0
INTENT = architecture-overview / v2
EXPECTED_DELTA = ENRICHES
EXPECTED_TARGET = 00000000-0000-0000-0000-000000000301
REQUIRED_FACTS = 2
REQUIRED_OBSERVATIONS = 1
QUALITATIVE_GROUNDING_REQUIRED = YES
```

The fixture contains a declared directional startup dependency, a configured
HTTP service reference, one supporting Observation, and one existing
containerization Insight. Its reviewed boundary does not claim successful
runtime communication, actual API invocation, data exchange, network
reliability, or post-start operational dependency.

## Canonical Result

```text
EXECUTION_STATUS = EVALUATED
STRUCTURAL_VALIDITY = VALID
PROPOSAL_CORRECTNESS = CORRECT
DELTA_CORRECTNESS = CORRECT
TARGET_CORRECTNESS = CORRECT_TARGET
GROUNDING_QUALITY = GROUNDED
TRUST_SAFETY = SAFE
OVERALL_QUALITY = STRONG
GATE_RESULT = PASSED
```

The result is identical across repeated evaluations of the same artifacts.

## Regression Evidence

Tests prove:

- expected `ENRICHES` with no proposal is `WEAK` and increments both incorrect
  denominators;
- missing `ENRICHES` target remains observable as structurally `INVALID`,
  `MISSING_REQUIRED_TARGET`, and `WEAK` without weakening production schemas;
- exact wrong target is `WRONG_TARGET` and `WEAK`;
- expected absence with no proposal remains `STRONG`-eligible;
- an unexpected proposal under expected absence is `WEAK`;
- `NEW` without target remains `STRONG`-eligible;
- mixed proposal sets are incorrect;
- unsupported, contradicted, and material plausible-but-unproven reviewed claims
  prevent `STRONG`;
- missing required qualitative review yields `REVIEW_REQUIRED` and `WEAK`;
- limited non-critical imprecision is `ACCEPTABLE` only for otherwise correct,
  safe output;
- foreign grounding references fail deterministic grounding and trust;
- invalid fixtures and invalid replay metadata do not become model behavior;
- `CONTRADICTS`, incompatible targets, unresolved allowlists, identity/digest
  mismatches, malformed contracts, and path traversal are rejected;
- production code has no dependency on evaluation code;
- the evaluator contains no canonical scenario answer and no provider/network
  execution import.

## Verification

The environment exposes `python3` but no `python` executable, so the required
pytest invocations were run with the equivalent available interpreter:

```text
python3 -m pytest tests/test_evaluation_harness.py
42 passed in 1.00s

python3 -m pytest
171 passed in 5.62s

python3 -m evaluations.runner architecture-overview-v2-enriches-v1
exit = 0; result = STRONG / PASSED

python3 -m evaluations.runner ../outside
exit = 1; result = INVALID_SCENARIO / FAILED
```

## Files

Created:

- `ai-engine/evaluations/__init__.py`
- `ai-engine/evaluations/README.md`
- `ai-engine/evaluations/models.py`
- `ai-engine/evaluations/loader.py`
- `ai-engine/evaluations/evaluator.py`
- `ai-engine/evaluations/runner.py`
- `ai-engine/evaluations/scenarios/architecture-overview-v2-enriches-v1/scenario.json`
- `ai-engine/evaluations/scenarios/architecture-overview-v2-enriches-v1/replay.json`
- `ai-engine/tests/test_evaluation_harness.py`
- `docs/stories/0110-implement-minimal-replayable-ai-intent-evaluation-harness/implementation-report.md`

Updated:

- `docs/stories/0110-implement-minimal-replayable-ai-intent-evaluation-harness/story.md`

## Production Integrity

```text
PRODUCTION_PROMPT_CHANGED = NO
PRODUCTION_SCHEMA_CHANGED = NO
PRODUCTION_VALIDATOR_CHANGED = NO
PRODUCTION_RETRY_CHANGED = NO
PRODUCTION_SERVICE_CHANGED = NO
PRODUCTION_DOCKER_CHANGED = NO
BACKEND_CHANGED = NO
CI_WORKFLOW_CHANGED = NO
```

Pre-existing production changes were preserved and not modified by Story 0110.

## Acceptance

```text
AC1 = PASSED
AC2 = PASSED
AC3 = PASSED
AC4 = PASSED
AC5 = PASSED
AC6 = PASSED
AC7 = PASSED
AC8 = PASSED
AC9 = PASSED
AC10 = PASSED
AC11 = PASSED
AC12 = PASSED
AC13 = PASSED
AC14 = PASSED
AC15 = PASSED
AC16 = PASSED
AC17 = PASSED
AC18 = PASSED
```

## Repository State

```text
UNRELATED_FILES_TOUCHED_BY_STORY_0110 = NO
INTENTIONAL_PREEXISTING_CHANGES_PRESERVED = YES
COMMIT = NO
PUSH = NO
PR = NO
```
