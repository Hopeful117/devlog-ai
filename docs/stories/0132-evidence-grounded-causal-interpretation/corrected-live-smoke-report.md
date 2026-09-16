# Story 0132 - Corrected Design C Live Smoke Report

## Status

```text
PHASE = V3_DESIGN_C_CORRECTED_LIVE_SMOKE
MODE = PAIR
STATUS = PARTIAL_BLOCKED
V3_DESIGN_C_FULL_RUN_CANDIDATE = NO
V3_FULL_RUN_AUTHORIZED = NO
```

This report records the human-authorized corrected smoke only. It does not
accept Story0132, establish semantic model capability, or authorize the full
benchmark.

## Frozen Identities

| Identity | Revision | Digest |
|---|---|---|
| Context selection | `story0132-v3-design-c-context-selection-1.0.0` | `de3d7236b83f752c68347c9a3bf03ec2f97c15f8869ba170c9b83ecdad1dbf90` |
| Provider schema | `story0132-v3-design-c-causal-provider-schema-1.0.1` | `6b291e77e1708b0bc3a745869544f8ba9215389d469811e1205b9cd3e129dd24` |
| Execution configuration | `story0132-v3-design-c-execution-config-1.2.1` | `b47bb5205d7757b3998ebdb2aa88f85989fb7f23a5bd921767d8fe0c46aaa2cf` |

```text
MAPPING_HASH = 67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0
MAX_OUTPUT_TOKENS = 2500
PROVIDER = openai
MODEL = gpt-4.1-mini
SEMANTIC_RETRY = DISABLED
TECHNICAL_RETRY_MAX = 1
```

## Run Accounting

```text
RUN_ID = 0a19e309-fb9b-424e-b2ff-22d81805e006
HISTORICAL_V3_PROVIDER_CALLS_BEFORE_CORRECTED_SMOKE = 4
CORRECTED_SMOKE_PROVIDER_CALLS = 1
TOTAL_V3_PROVIDER_CALLS_TO_DATE = 5
EXPECTED_PROVIDER_CALLS = 3
MAXIMUM_PROVIDER_CALLS = 6
TECHNICAL_RETRIES = 0
SEMANTIC_RETRIES = 0
```

The mandatory recursive schema, manifest, identity, projection, ordering,
digest, budget, mapping, provider-configuration, and secret preflights passed.
The provider accepted the corrected schema and returned a complete response.

## Slot Result

### CASE-01::CL-01

```text
TRANSPORT = PASS
RAW_CAPTURE = PASS
PROVIDER_STATUS = completed
OUTPUT_TOKENS = 548
STRUCTURAL = PASS
CONTEXT_IDENTITY = PASS
REFERENCE_AUTHORIZATION = PASS
EVIDENCE_VALIDITY = PASS
CORE_VALIDATION = FAIL
SEMANTIC_ELIGIBLE = NO
OFFLINE_REPLAY = NOT_EXECUTED
PRELIMINARY_SEMANTIC_RESULT = NOT_EVALUATED
```

Core rejected the generated evidence assertion because the generated excerpt
did not match the resolved authoritative content. The full response was
captured and hashed before parsing:

```text
RAW_PROVIDER_RESPONSE_SHA256 = b41fc1ab104f711256ea41ef13638316d1a2121f7f343a00844949f528fcd6f5
```

No output repair and no response-bearing retry were performed. CASE-04 and
CASE-03 were not executed by the fail-closed runner after the deterministic
Core rejection.

## Artifact

```text
SMOKE_ARTIFACT = ai-engine/evaluations/product_value/v3/design-c-smoke/0a19e309-fb9b-424e-b2ff-22d81805e006.json
SMOKE_ARTIFACT_SHA256 = 0675964d2ebf2432e5b3e1ae9359392597bd7b3ddba36c17005d4279300ce6b0
```

The historical failed artifact remains unchanged. No provider secret was
persisted.

## Verification

```text
PYTHON_FOCUSED_TESTS = PASS (41)
PYTHON_FULL_TESTS = PASS (289)
JAVA_RELEVANT_TESTS = PASS (32)
SECRET_PERSISTENCE_PREFLIGHT = PASS
GIT_DIFF_CHECK = PASS
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_SEMANTICS_CHANGED = NO
PRODUCTION_CONTRACT_CHANGED = NO
STORY_0132_REOPENED = NO
STORY_0133_AUTHORIZED = NO
COMMIT = PENDING
PUSH = NO
MERGE = NO
```

Semantic model capability, AI utility, and human utility remain not
established. The smallest next action is a human decision on whether a
separate continuation run for CASE-04 and CASE-03 is appropriate; no retry or
repair is justified for the captured CASE-01 response.
