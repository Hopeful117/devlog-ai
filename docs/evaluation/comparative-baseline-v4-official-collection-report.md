# Comparative Baseline V4 Official Collection Report

## Authorization

The human explicitly authorized the frozen official 18-slot collection. No
additional repetition, replacement, rerun, tuning, or pilot was executed.

## Attempt and Frozen Identities

```text
attempt: v4-official-20260917T204411Z-e947a9a6
path: ai-engine/data/comparative-baseline-v4/official-collection/v4-official-20260917T204411Z-e947a9a6
execution class: OFFICIAL_COLLECTION
experiment: a9864fba8bb0da4fb337de83e8890e6a30ecfcf7a8adebd6dbd40252c2af27cf
runtime contract: comparative-v4-live-runtime-contract-1.0.0
runtime digest: 6966a5b77d5f4f9469f5a556e9dad66ba82580d637d465947d230a5246c71e40
execution configuration: 74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2
official plan: bea0b41c3e095447c6a5d257ca16b956136601f6a4749f611c06f2a478373457
repository revision: 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
```

Preflight passed before the first provider call. Provider/model, benchmark,
questions, oracle, conditions, prompts, policies, ceilings, timeouts, and
retry configuration matched the frozen contract.

## Completeness

```text
expected assignments: 18
attempted: 18
finalized: 18
DEVLOG: 9
AGENT_DIRECT_OPEN: 9
repetitions: 1=6, 2=6, 3=6
RAW: 18
DERIVED: 18
ledger: complete, no missing, no unexpected, no duplicates
```

## Execution and Evaluation Outcomes

```text
COMPLETED: 7
CENSORED: 5
PROVIDER_FAILURE: 6
PROVIDER_TIMEOUT: 0
TOOL_TIMEOUT: 0
RUNTIME_FAILURE: 0
```

```text
structural valid: YES=5, NO=2, NOT_EVALUATED=11
semantic evaluated: 5
semantic correct: 4 true, 1 false, 13 NOT_EVALUATED
correct grounded: 0
primary diagnostics: PROVIDER_FAILURE=6, GROUNDING_FAILURE=5,
                     SAFETY_CEILING_CENSORING=5, MODEL_STRUCTURAL_FAILURE=2
```

The collection is classified:

```text
OFFICIAL_COLLECTION_VALID
```

This classification concerns experimental integrity, not answer quality.
Provider failures and censoring are retained as outcomes under the frozen
contract.

## Provider Parse Outcomes

All six provider failures were attributed as `RESPONSE_PARSE_ERROR` with
`JSONDecodeError`, `RESPONSE_PARSE`, `JSON_TEXT`, and no retry. No valid
SDK-native function call was shown to be rejected. These remain provider/model
contract-invalid output outcomes rather than a demonstrated runtime defect.

Diagnostics were persisted using the redacted allow-list contract. No API key,
bearer token, authorization header, credential, or sensitive environment value
was persisted.

## Condition Summary

### DEVLOG

```text
slots: 9
completed: 7
provider failures: 2
censored: 0
structural valid: 5
grounding valid: 0
semantic eligible: 5
semantic correct among eligible: 4
correct grounded: 0
provider calls: 7
tool attempts/executed/invalid/skipped: 0 / 0 / 0 / 0
repository bytes delivered: 0
input/output tokens: 45839 / 5499
latency: 78767 ms aggregate
```

### AGENT_DIRECT_OPEN

```text
slots: 9
completed: 0
provider failures: 4
censored: 5
structural valid: 0
grounding valid: 0
semantic eligible: 0
semantic correct: 0
correct grounded: 0
provider calls: 50
tool attempts/executed/invalid/skipped: 92 / 58 / 25 / 9
ceiling-counted operations: 83
repository bytes delivered: 424786
input/output tokens: 246567 / 3162
latency: 110732 ms aggregate
```

The direct condition's observed behavior included natural search/read
trajectories, malformed requests returned through Policy A, provider/model
parse failures, and legitimate safety censoring. No preferred navigation path
was imposed.

## Semantic Eligibility and Grounding

Semantic evaluation was counted separately from eligibility. There were five
structurally eligible observations and four semantically correct answers among
them. The result is not an overall condition accuracy and is not used to rank
conditions.

All five structurally valid DEVLOG answers failed strict grounding. Semantic
correctness remains visible independently where structural eligibility allowed
it. `NOT_EVALUATED` values were not converted into incorrect answers.

## Resource and Byte Summary

```text
provider calls: 57
model turns: 57
tool attempts: 92
executed tool operations: 58
invalid tool requests: 25
skipped tool operations: 9
ceiling-counted operations: 83 aggregate, maximum 12 per observation
repository bytes delivered: 424786
largest individual delivered result: 39296 bytes
input tokens: 292406
output tokens: 8661
cost: NOT_AVAILABLE
```

The per-operation byte invariant held for every observation. Cumulative
repository bytes were not interpreted as the per-operation ceiling.

## Replay and Contamination

```text
replay: 18/18 PASS
replay provider calls: 0
replay network calls: 0
contamination: PASS
attempt isolation: PASS
official storage: OFFICIAL_COLLECTION / baselineEligible=true
```

DEVLOG observations contain no direct tool conversation or repository tool
trace. AGENT_DIRECT_OPEN observations contain no DevLog context, oracle,
evaluator material, or expected evidence. Diagnostics are not shared between
observations.

## Technical Anomalies

The initial attempt to invoke the official runner stopped before provider
construction because the runner read questions from the V4 manifest instead of
the benchmark manifest. It created no official artefact and made zero provider
calls. The runner was corrected before the actual official attempt; the final
attempt is fresh and complete. No post-start runtime defect, replay mismatch,
accounting violation, contamination, or RAW corruption occurred.

## Dataset and Notebook

Machine-readable dataset:

`ai-engine/data/comparative-baseline-v4/official-collection/v4-official-20260917T204411Z-e947a9a6/official-dataset.csv`

The dataset contains one row per observation and 29 authoritative fields,
including status, eligibility, semantic correctness, tool accounting, bytes,
tokens, latency, cost, and replay status.

Notebook:

`notebooks/evaluation/comparative-baseline-v4-official.ipynb`

The notebook loads the persisted CSV, verifies the 18-row matrix, calculates
semantic eligibility separately from conditional accuracy, summarizes quality
and resources, shows direct-agent behavior, and produces restrained
descriptive plots. `Restart Kernel -> Run All` completed with zero execution
errors.

## Limitations

The sample is 18 observations with three repetitions per question-condition
pair. Provider/model failures and censoring reduce semantic eligibility, and
cost is unavailable. Results are descriptive paired evidence only. No
superiority, causal, population, or product conclusion is made here.

## Next Human Analysis Step

Review the immutable V4 official dataset and descriptive notebook together with
the human, compare them with the V3 diagnostic baseline where appropriate, and
decide what the quality, grounding, efficiency, and stability observations
imply for the next DevLog hypothesis or architecture iteration.
