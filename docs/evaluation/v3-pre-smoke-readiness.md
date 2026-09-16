# V3 Pre-Smoke Readiness

## Status

```text
PHASE = V3_PRE_SMOKE_EVALUATION_INFRASTRUCTURE
STATUS = READY_FOR_HUMAN_REVIEW
NEW_PROVIDER_CALLS = 0
V3_SMOKE_RUN_AUTHORIZED = NO
V3_FULL_RUN_AUTHORIZED = NO
STORY_0132_REOPENED = NO
STORY_0133_AUTHORIZED = NO
TRADING_OS_IMPLEMENTATION_PERFORMED = NO
```

## Implemented Requirements

- Candidate V3 manifest with exactly three smoke identities.
- Explicit `MECHANICALLY_MIGRATED` versus `HUMAN_APPROVED` mapping state.
- Exact SelectedKnowledge snapshot and deterministic identity digest capture.
- Exact provider-visible UTF-8 evidence content, byte length, SHA-256, ordering,
  repository identity/revision, and locator contract version.
- Exact rendered prompt representation and prompt digest capture.
- Raw response, finish reason, token usage, request metadata, and ordered attempt
  capture helpers.
- Technical retry maximum of one; semantic retry disabled; no best-attempt
  selection.
- Ordered seven-gate fail-fast composition with first-failure preservation.
- Structural/evidence failures block semantic scoring and never become zeroes.
- Frozen denominator metadata and benchmark validity states.
- Secret-safe metadata handling and exact-field secret preflight.
- Manifest-authoritative provider configuration with fail-closed environment
  conflict validation.
- Explicit OpenAI SDK and provider-adapter retry separation: both zero beneath
  the single V3 harness technical retry.
- Immutable artifact writing that refuses overwrite.
- Offline replay through the existing real Java `CoreV2EvaluationBridgeTest`.
- Deterministic newline, UTF-8, evidence tamper, raw response, gate, retry,
  immutability, and replay tests.

## Candidate Manifest

```text
V3_MANIFEST_PATH = ai-engine/evaluations/product_value/v3/benchmark-manifest.json
V3_MAPPING_HASH = 67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0
V2_MAPPING_REUSED_UNCHANGED = YES
V3_MAPPING_HUMAN_APPROVED = YES
V3_PROVIDER_CONFIGURATION_HUMAN_APPROVED = YES
SMOKE_SLOT_COUNT = 3
```

Provider configuration:

```text
V3_PROVIDER = openai
V3_MODEL = gpt-4.1-mini
MAX_OUTPUT_TOKENS = 2000
OPENAI_SDK_MAX_RETRIES = 0
PROVIDER_ADAPTER_MAX_RETRIES = 0
V3_TECHNICAL_RETRY_MAX = 1
MAXIMUM_TOTAL_ATTEMPTS_PER_SLOT = 2
SEMANTIC_RETRY = DISABLED
TIMEOUT_SECONDS = 90
STRUCTURED_OUTPUT = responses.parse(..., text_format=...)
TEMPERATURE = PROVIDER_DEFAULT
TOP_P = PROVIDER_DEFAULT
SEED = NOT_CONFIGURED
TOOLS = NOT_CONFIGURED
REASONING = NOT_CONFIGURED
```

The manifest is the V3 benchmark execution authority. `LLM_PROVIDER`,
`LLM_MODEL`, `LLM_MAX_OUTPUT_TOKENS`, `LLM_MAX_RETRIES`, and
`LLM_TIMEOUT_SECONDS` are accepted only when explicitly equal to the frozen
manifest values; conflicting environment values fail closed.

Candidates:

| Case/question | Coverage |
|---|---|
| `CASE-01::CL-01` | Positive causal relation. |
| `CASE-04::CASE-04` | `NOT_ESTABLISHED` abstention control. |
| `CASE-03::CL-09` | Evidence-heavy positive relation. |

The V2 oracle classification and question identities are carried forward without
semantic changes. The three-slot smoke selection and provider configuration are
explicitly human-approved for this authorized smoke run.

## Call Envelope

```text
EXPECTED_PROVIDER_CALLS = 3
MAXIMUM_PROVIDER_CALLS = 6
TECHNICAL_RETRY_MAX = 1
SEMANTIC_RETRY = DISABLED
```

The maximum assumes one technical retry for each slot and no semantic retries.
No smoke call was made.

## Verification

```text
PYTHON_FOCUSED_TESTS = PASS
PYTHON_FULL_TESTS = PASS
PYTHON_FULL_PASSED = 263
PYTHON_FULL_FAILED = 0
JAVA_RELEVANT_TESTS = PASS
JAVA_RELEVANT_TEST_COUNT = 19
GIT_DIFF_CHECK = PASS
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_SEMANTICS_CHANGED = NO
PRODUCTION_DEFECT_DISCOVERED = NO
```

The Python full-suite count is the previous `244` tests plus the twelve new V3
protocol tests. All deterministic tests use fixtures; the Java replay test uses
the local Core bridge only. No provider, network, embedding service, or
semantic judge was invoked.

## Remaining Before Smoke Authorization

```text
REQUIRED_BEFORE_SMOKE_REMAINING = NONE
OPTIONAL_BEFORE_FULL_RUN_REMAINING = PROVIDER_METADATA_ADAPTER,
  FULL_RUN_CIRCUIT_BREAKER_THRESHOLD,
  LARGE_ARTIFACT_SIDECAR_POLICY,
  SEPARATE_METRIC_REPORT_RENDERER
```

The smoke execution is authorized for the three frozen candidates only. The full
benchmark remains unauthorized and is not executed by this phase.

## Boundary

No production code, production semantic contract, Story0132 artifact, Story0133,
or Trading OS checkout was modified. No commit, push, or merge was performed.
