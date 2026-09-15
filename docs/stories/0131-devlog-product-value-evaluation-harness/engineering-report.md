# Story 0131 - Engineering Report

## Repository State

- Branch: `main`
- Production behavior changed: `NO`
- Commit/push: `NO`
- Worktree: modified by the isolated evaluator, tests and Story 0131 documents

## Files Changed

- `ai-engine/evaluations/product_value/`: benchmark manifest validation, exact
  scoring, live Core-context capture adapter, CLI and usage documentation.
- `ai-engine/tests/test_product_value_evaluator.py`: evaluator unit tests.
- `docs/stories/0131-devlog-product-value-evaluation-harness/`: implementation
  record and authorization boundaries.

## Verification

```text
python3 -m pytest -q
188 passed
python3 -m compileall -q evaluations/product_value
PASS
git diff --check
PASS
```

The evaluator returns `ORACLE_NOT_APPROVED` for the frozen Story 0130 suite and
`REVISION_MISMATCH` for captures from another revision. The live adapter ran all
four cases against the existing Core endpoint at the pinned revision. It
produced a 19-entry manifest: 10 resolved by the product projection and 9
explicitly unresolved. The manifest is therefore invalid for formal acceptance.

## Acceptance State

```text
ORACLE_STATUS = CANDIDATE_REQUIRES_HUMAN_VALIDATION
ORACLE_VALIDATION = REPOSITORY_GROUND_TRUTH_VERIFIED; HUMAN SEMANTIC REVIEW PENDING
FORMAL_RED_BASELINE = NOT_EXECUTED
HUMAN_UTILITY = NOT_MEASURED
PRODUCT_VALUE_ACCEPTANCE = NOT_AUTHORIZED
PRODUCTION_CODE_CHANGED = NO
```

## Known Limitations

- The live adapter exercises the existing Core context projection, but it does
  not provide a generated AI answer or claim-level grounding.
- Unsupported-claim measurement is accepted as reviewed capture metadata and is
  `NOT_MEASURED` when absent; the harness does not use an LLM or keyword judge.
- Human Utility requires the protocol in Story 0130 and cannot be automated.

## Readiness

`READY_FOR_HUMAN_REVIEW - BLOCKED_FOR_FORMAL_BASELINE`

## Harness Defect Review

```text
HARNESS_DEFECT = YES, corrected during continuation
WHY_IT_IS_A_DEFECT = the live adapter copied the expected revision into the actual capture field instead of preserving the revision returned by DevLog
FIX = capture and compare the observed revision from every live response; emit null when responses disagree
BENCHMARK_SEMANTICS_CHANGED = NO
```

## Required Final Fields

```text
DEVLOG_HEAD = c64db9791cc33eea5095161501da26c75f37d8d5
DEVLOG_ORIGIN_MAIN = c64db9791cc33eea5095161501da26c75f37d8d5
HARNESS_IMPLEMENTED = YES
PINNED_TRADING_OS_REVISION = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
PINNED_REVISION_RESOLVED = YES (independent Git object/tree verification)
EXPECTED_ARTIFACT_COUNT = 19
RESOLVED_ARTIFACT_COUNT = 19 repository / 10 DevLog retrieval
UNRESOLVED_ARTIFACT_COUNT = 0 repository / 9 DevLog retrieval
ARTIFACT_MANIFEST_VALID = YES repository ground truth; NO DevLog retrieval manifest
ORACLE_MECHANICAL_VALIDATION = COMPLETE artifact existence; semantic causal review pending
ORACLE_DEFECT_FOUND = NOT_DETERMINED
ORACLE_HUMAN_APPROVAL = NO
REAL_CAPTURE_ADAPTER_IMPLEMENTED = YES
DEVLOG_AI_ENTRY_POINT = POST /api/projects/{projectId}/engineering-story-context?detail=AGENT
REAL_DEVLOG_CAPTURE_EXECUTED = YES, all four cases
DIRECT_REPOSITORY_BASELINE_EXECUTED = NO, separate agent experiment pending
FORMAL_AI_RED_BASELINE_EXECUTED = NO, oracle not approved
AI_UTILITY = NOT_AUTHORIZED
HUMAN_PROTOCOL_READY = YES
HUMAN_DIRECT_BASELINE_EXECUTED = NO
HUMAN_DEVLOG_BASELINE_EXECUTED = NO
HUMAN_UTILITY = NOT_MEASURED
HUMAN_AI_PARITY = NOT_AUTHORIZED
BENCHMARK_LEAKAGE_CHECK = PASS for production paths; benchmark terms occur only in evaluation/docs/tests
PRODUCTION_ANALYSIS_BEHAVIOR_CHANGED = NO
ENGINEERING_CORRECTNESS = NOT_FORMALLY_SCORED
STORY_0131_TECHNICAL_MEASUREMENT_LOOP_COMPLETE = NO
PRODUCT_PARITY_EVALUATION_COMPLETE = NO
NEXT_PRODUCT_IMPROVEMENT_AUTHORIZED = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```
