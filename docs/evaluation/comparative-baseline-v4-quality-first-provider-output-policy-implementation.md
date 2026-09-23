# Comparative Baseline V4 Quality-First Provider Output Policy Implementation

## 1. Authorization Basis

Implemented the approved decision documented in:

`docs/evaluation/comparative-baseline-v4-quality-first-provider-output-ceiling-decision.md`

Approved policy:

```text
PROVIDER_MAXIMUM_SELECTED
selectedMaxOutputTokens = 32768
scope = ALL_PROVIDER_RESPONSES
```

No provider, model, prompt, evaluator, benchmark, runtime, or tool-contract
decision was reopened.

## 2. Configuration

Previous configuration:

```text
finalOutputTokens = 1800
intermediateOutputTokens = 512
```

New configuration:

```text
finalOutputTokens = 32768
intermediateOutputTokens = 32768
```

The configuration shape was preserved. The existing two fields remain separate;
no `maxOutputTokens` field was introduced.

## 3. Provider Propagation

The existing path remains unchanged:

```text
v4-manifest.json
  -> v4_execution_configuration()
  -> V4CollectionRuntime._request(..., max_output_tokens)
  -> ProviderRequest.max_output_tokens
  -> responses.create(max_output_tokens=request.max_output_tokens)
```

Verified by deterministic tests:

- DEVLOG request: `32768`.
- DIRECT first turn: `32768`.
- DIRECT intermediate/final-producing turn: `32768`.
- Every corrected V4 provider response uses the same configured value because both existing fields are equal.

## 4. Preserved Invariants

| Area | Result |
|---|---|
| Runtime | `comparative-v4-live-runtime-contract-5.0.0`, unchanged |
| Runtime digest | `a3211f44aad4633ca1d725b7a48506a6dd8725ab96becf182e867f1794e80edd`, unchanged |
| Instrumentation | `comparative-v4-instrumentation-3.0.0`, unchanged |
| Typed repository schema | `comparative-v4-typed-repository-tools-2.0.0`, unchanged |
| Provider/model | `openai` / `gpt-4.1-mini`, unchanged |
| Retry policy | `providerMaxRetries=0`, unchanged |
| Runtime-5 incomplete handling | unchanged; incomplete responses are not parsed, retried, repaired, or continued |
| Policy A malformed tool handling | unchanged |
| Prompts and instructions | unchanged |
| Benchmark/questions/oracle | unchanged |
| Repository revision | `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`, unchanged |
| Evaluators and failure taxonomy | unchanged |

Emergency guards remain exactly:

```text
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
```

Actual token accounting remains independent from configured capacity. The
implementation does not add reserved, predicted, or capacity-derived token
usage.

## 5. Identity Recalculation

The execution configuration hash was recomputed from the canonical existing
identity mechanism. Experiment and plan identities were then recomputed from
their existing deterministic mechanisms.

| Identity | Previous | New |
|---|---|---|
| Execution configuration | `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4` | `f72225f4bb0d7e062dab8eb4b1120f600734fac2e51a9573e910d306c29e6ea8` |
| Experiment | `1af6f9b79427f6f53bca1f9466fde9ee01c652f5bd082a810566428f2606f4d5` | `073b51104c49430f949407710aa98719e45af375c78a9ec92c69c078eadc1a62` |
| Validation/pilot plan | `6c423c1548c68365fd432becaa1edb7dedd0f0c7fa070dd668fb5fcba7ecc638` | `e0e26c17884e012137ee3912ab33df72bbd93c3546943c3773164e15783c38bc` |
| Official plan | `fe85cf393dcf9da830af5476ff1e6a398758d97457955223e75cd646f3d521fe` | `4972b2236c02c5d6aad74c8ae13809bb0c348dde4dcb51858b60337787668195` |

Identity provenance:

```text
finalOutputTokens 1800 -> 32768
intermediateOutputTokens 512 -> 32768
        -> execution configuration hash changed
        -> experiment identity changed because it binds executionConfiguration
        -> validation/pilot identity changed because it binds experimentIdentity
        -> official plan identity changed because it binds experimentIdentity
```

No unrelated identity changed. Runtime identity/digest and instrumentation
identity remain unchanged.

## 6. Plan and Preflight State

The generated future plans remain frozen in structure:

- Pilot/technical validation plan: 6 slots, one repetition, identity updated.
- Official plan: 18 slots, 9 DEVLOG and 9 `AGENT_DIRECT_OPEN`, 3 questions x 3 repetitions, identity updated.
- Official collection: disabled.
- Pilot execution: not invoked.
- Official collection: not invoked.

Offline preflight result:

```text
status = PASS
configurationReady = true
deterministicReplay = READY
pilotAuthorized = false
officialCollectionAllowed = false
officialAssignmentCount = 18
pilotAssignmentCount = 6
providerCalls = 0
networkCalls = 0
```

## 7. Legacy Budget Audit

The corrected V4 execution path contains no active `1800` or `512` token
values. The old values remain only in historical documentation, legacy
non-V4 code/tests, or generic fixtures outside the corrected V4 policy.

The legacy `collection_runtime.py` constants and their tests were not changed;
they are outside this Story's V4 manifest/runtime path.

## 8. Tests and Verification

Focused and comparative tests:

```text
python -m pytest tests/test_comparative_baseline_v4.py tests/test_comparative_live_adapters.py -ra
90 passed in 0.84s

python -m pytest tests/test_comparative_baseline_v4.py tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py -ra
132 passed in 0.65s
```

Additional offline verification:

```text
python -m compileall -q evaluations/comparative_baseline_v4 evaluations/comparative_baseline/live_adapters.py
PASS

python -m evaluations.comparative_baseline_v4.preflight
PASS
```

The regression suite continues to cover completed responses, malformed final
responses, incomplete provider responses, tool-call normalization, invalid
tool requests, typed repository constraints, Policy A behavior, accounting,
replay, and safety guards.

## 9. Historical Preservation

Historical pilots, validations, official attempts, RAW/DERIVED observations,
reports, notebooks, historical manifests, and historical identities were not
modified, deleted, relabeled, or regenerated.

## 10. Files Modified

- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`: set both output fields to `32768`; recomputed execution hash.
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`: validate the approved `32768/32768` configuration.
- `ai-engine/evaluations/comparative_baseline_v4/pilot.py`: update expected experiment, execution, and pilot identities.
- `ai-engine/evaluations/comparative_baseline_v4/official.py`: update expected experiment, execution, and official-plan identities.
- `ai-engine/tests/test_comparative_baseline_v4.py`: add deterministic propagation coverage and update identity expectations.
- `docs/evaluation/comparative-baseline-v4-quality-first-provider-output-policy-implementation.md`: this report.

Pre-existing unrelated worktree changes were not reverted or modified.

## 11. Mandatory Answers A-Z

- A: `1800`.
- B: `512`.
- C: `32768`.
- D: `32768`.
- E: `YES`.
- F: `NO`.
- G: `NO`.
- H: `comparative-v4-live-runtime-contract-5.0.0`.
- I: `YES`.
- J: `NO`.
- K: `NO`.
- L: `NO`.
- M: `NO`.
- N: `NO`.
- O: `NO`.
- P: `NO / NO / NO / NO`.
- Q: `NO`.
- R: `f72225f4bb0d7e062dab8eb4b1120f600734fac2e51a9573e910d306c29e6ea8`.
- S: `073b51104c49430f949407710aa98719e45af375c78a9ec92c69c078eadc1a62`.
- T: `e0e26c17884e012137ee3912ab33df72bbd93c3546943c3773164e15783c38bc`.
- U: `4972b2236c02c5d6aad74c8ae13809bb0c348dde4dcb51858b60337787668195`.
- V: `NO`.
- W: `YES`.
- X: `NO`.
- Y: `NO / NO / NO`.
- Z: `YES`.

## 12. Unresolved Limitations

- The repository does not lock the exact resolved Python OpenAI SDK patch version.
- No live validation was authorized or executed, so provider behavior under the new ceiling remains a future observation.
- The existing legacy non-V4 `1800/512` constants remain intentionally untouched.
- The report documents identity values but does not commit or push them.

## 13. Readiness

The approved policy is implemented, identity-bound plans are internally
consistent, offline tests and preflight pass, and no provider/network/new
observation activity occurred.

The exact next human decision is:

> Authorize ONE technical live validation under the new quality-first token policy. Do not authorize a pilot or official collection yet.

If that validation demonstrates technically valid realistic treatment execution,
stop protocol hardening and authorize the corrected V4 official 18-slot
collection unless a genuinely experiment-invalidating defect is found.

READY_FOR_HUMAN_AUTHORIZATION_QUALITY_FIRST_LIVE_VALIDATION
STOP_FOR_HUMAN_REVIEW
