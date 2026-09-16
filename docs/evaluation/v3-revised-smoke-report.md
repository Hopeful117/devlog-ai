# V3 Revised Smoke Report

## Result

```text
PHASE = V3_REVISED_LIVE_SMOKE_EVALUATION
MODE = PAIR
STATUS = PARTIAL_BLOCKED

V3_REVISED_OUTPUT_LIMIT_HUMAN_APPROVED = YES
PREVIOUS_MAX_OUTPUT_TOKENS = 2000
MAX_OUTPUT_TOKENS = 3000
EXECUTION_CONFIGURATION_CHANGED = YES
SEMANTIC_MAPPING_CHANGED = NO

NEW_RUN_ID = cb0abb59-3d4f-4e7a-a8cc-4922520636e5
REVISED_SMOKE_PROVIDER_CALLS = 1
TECHNICAL_RETRIES = 0
SEMANTIC_RETRIES = 0
```

The revised run restarted from `CASE-01::CL-01` with execution configuration
revision `story0132-v3-execution-config-2.0.0` and digest
`5fb45c5a0e5d40e00e412aecb1af4756cad579573a3df4f2e611a9972dc1230d`.

`CASE-04::CASE-04` and `CASE-03::CL-09` were not executed.

## Failure

```text
SLOT = CASE-01::CL-01
TRANSPORT = PASS
STRUCTURAL = FAIL
FIRST_FAILURE = GATE_2_STRUCTURAL_SCHEMA_VALIDITY
SEMANTIC_ELIGIBLE = NO
CORE_VALIDATION = NOT_EVALUATED
OFFLINE_REPLAY = NOT_EVALUATED
```

The parser rejected the response because the `confidence` field arrived as the
string `HIGH`, while the frozen Python `StoryContextAnalysisResult` contract
expects a `Confidence` object. This is a structured-output/schema contract
defect, not evidence that 3,000 output tokens were insufficient.

The revised run did not reach the Java Core or offline replay gates. The runner
captured the parser's field-level input (`HIGH`) rather than the complete raw
provider response, so raw-response preservation is incomplete for this run. The
artifact remains immutable and records that limitation; no attempt was made to
repair it.

## Artifacts And Accounting

```text
SMOKE_ARTIFACT = ai-engine/evaluations/product_value/v3/smoke/cb0abb59-3d4f-4e7a-a8cc-4922520636e5.partial.json
SMOKE_ARTIFACT_SHA256 = 0725e6aa5812785b6ba21c6178da5c977fefc3a17afd83cf84418a386eea526b

HISTORICAL_V3_PROVIDER_CALLS_BEFORE_REVISED_SMOKE = 2
REVISED_SMOKE_PROVIDER_CALLS = 1
TOTAL_V3_PROVIDER_CALLS_TO_DATE = 3

PREVIOUS_2000_TOKEN_ARTIFACT_PRESERVED = YES
PREVIOUS_2000_TOKEN_ARTIFACT_SHA256 = 2e3b8b5fed74d236e8f51e71ede8135dee4c139df02a42b14311c5bede57be31
```

## Boundary

```text
STRUCTURED_OUTPUT_DEFECT_DISCOVERED = YES
PROMPT_CHANGE_REQUIRED = NO
SCHEMA_CHANGE_REQUIRED = HUMAN_REVIEW_REQUIRED
MODEL_CHANGE_REQUIRED = NO
RETRIEVAL_OR_RAG_CHANGE_REQUIRED = NO

V3_SMOKE_RUN_AUTHORIZED = NO
V3_FULL_RUN_AUTHORIZED = NO
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_SEMANTICS_CHANGED = NO
STORY_0132_REOPENED = NO
STORY_0133_AUTHORIZED = NO
TRADING_OS_IMPLEMENTATION_PERFORMED = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```

No additional provider call is authorized until the human resolves the frozen
structured-output contract mismatch and approves a new execution/run decision.
