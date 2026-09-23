# Comparative Baseline V4 Provider Output Token Budget Audit

## Audit Scope

Offline audit only. No provider call, network call, observation, configuration
change, runtime change, identity change, or historical artifact change was made.

The audited frozen configuration is runtime
`comparative-v4-live-runtime-contract-5.0.0`, execution identity
`479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4`, and
experiment identity
`1af6f9b79427f6f53bca1f9466fde9ee01c652f5bd082a810566428f2606f4d5`.

## Exact Configuration and Propagation

The authoritative source is:

```text
ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json
```

It defines:

```text
executionConfiguration.finalOutputTokens = 1800
executionConfiguration.intermediateOutputTokens = 512
```

Propagation is:

```text
v4-manifest.json
  -> v4_execution_configuration()
  -> V4CollectionRuntime._request(..., output_tokens)
  -> ProviderRequest.max_output_tokens
  -> OpenAIProviderTransport.complete()
  -> responses.create(max_output_tokens=request.max_output_tokens)
```

The exact provider parameter is:

```text
max_output_tokens
```

Turn policy:

- `DEVLOG`: `1800` on its single request.
- `AGENT_DIRECT_OPEN`, turn 1: `1800`.
- `AGENT_DIRECT_OPEN`, turns 2 onward: `512`.

There is no environment variable, CLI override, provider-adapter default,
question override, or condition-specific manifest override. The manifest wins;
the runtime selects final versus intermediate value by turn; the adapter passes
that value directly to the SDK. The SDK default is not used.

```text
CONFIGURED_OUTPUT_TOKEN_LIMIT = 1800 final / 512 intermediate
```

## Provenance and Rationale

Story 0134 explicitly freezes:

```text
FINAL_OUTPUT_TOKEN_ENVELOPE = 1800
AGENT_INTERMEDIATE_TOKEN_ENVELOPE = 512
```

The comparative runtime design describes `1800` as the proposed final output
envelope and `512` as the separate AGENT_DIRECT action envelope. The same design
requires these values to be frozen in the execution manifest before collection.
The implementation report records both values as preserved runtime decisions.

Therefore:

```text
provenance = EXPLICIT_EXPERIMENTAL_DESIGN_DECISION
documented rationale = YES, envelope distinction and structured-contract bounds
RATIONALE_NOT_DOCUMENTED = NO
```

The values are experimental execution parameters, not OpenAI defaults and not
an inherited environment setting.

## Identity Binding

| Identity | Bound to output-token configuration? | Evidence |
|---|---:|---|
| Runtime contract | `NO` | `V4_RUNTIME_CONTRACT` contains runtime semantics but no output-token fields |
| Runtime digest | `NO` | Digest is computed from the runtime component map, which excludes execution config |
| Instrumentation | `NO` | Instrumentation identity is separate |
| Execution identity | `YES` | `validate_v4_identity()` hashes the complete execution configuration, including both token fields |
| Experiment identity | `YES` | Experiment identity includes the execution configuration and its hash |
| Validation plan | `YES, transitively` | Pilot identity includes experiment identity |
| Official plan | `YES, transitively` | Official plan identity includes experiment identity |

```text
TOKEN_LIMIT_CHANGE_REQUIRES_NEW_RUNTIME_IDENTITY = NO
TOKEN_LIMIT_CHANGE_REQUIRES_NEW_EXECUTION_IDENTITY = YES
TOKEN_LIMIT_CHANGE_REQUIRES_NEW_EXPERIMENT_IDENTITY = YES
```

No identity was changed by this audit.

## Historical Observation Audit

Evidence-supported counts:

- Confirmed `incomplete` with `max_output_tokens`: `1`, the runtime-5 post-remediation validation.
- Confirmed `incomplete` with unknown/unpersisted reason: `1`, the instrumented runtime-4 validation.
- Two earlier runtime-4 parse failures did not persist response status, so they are not counted as confirmed incomplete responses or as confirmed unknown-reason incomplete responses.
- Other historical provider failures and normal completions were not reclassified.

The latest runtime-5 observation is confirmed evidence that `max_output_tokens`
can terminate a provider response before any usable DIRECT interaction. It does
not prove that the budget is globally inadequate.

## Historical DIRECT Evidence

The same `(final=1800, intermediate=512)` execution configuration appears in
64 persisted V4 artifacts inspected offline. Four historical DIRECT artifacts
under that configuration reached completed final answers after repository-tool
trajectories of 15, 13, 12, and 11 tool attempts respectively. Additional V4
pilot reports record successful DIRECT searches and reads under the same envelope
family.

Therefore:

```text
SAME_LIMIT_HAS_SUPPORTED_DIRECT_INTERACTION = YES
```

This demonstrates that the budget can support realistic DIRECT interaction, but
does not establish sufficiency for every question, trajectory, or response.

## Provider Budget Semantics

The local adapter sends `max_output_tokens` once per provider response/model
turn. V4 does not apply it across the entire observation. DIRECT receives the
larger final envelope on turn 1 and the smaller intermediate envelope on later
turns. Tool-call generation and final-answer generation are therefore bounded by
the request turn's value.

Whether the provider internally combines reasoning tokens and visible output
tokens for this limit cannot be established from the installed SDK type or local
adapter code:

```text
provider internal token allocation semantics = INCONCLUSIVE
```

## Natural Completion and Guards

The output limit is separate from the emergency guards:

```text
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
```

In the latest validation, `max_output_tokens` caused an explicit provider
incomplete response before any of those guards could matter:

```text
OUTPUT_LIMIT_ACTS_AS_EARLY_EXECUTION_CEILING = YES
```

Global natural-completion compatibility is not proven incompatible because the
same frozen configuration has supported completed DIRECT tool trajectories:

```text
NATURAL_COMPLETION_COMPATIBILITY = QUESTIONABLE
```

The current value is a deliberate per-response experimental envelope, but the
latest failure shows it can be a practical early ceiling for some trajectories.

## Experimental Validity Classification

A hypothetical increase would change the execution configuration hash and
transitively the experiment, pilot, and official plan identities. Since the
current values are explicit experimental design decisions and the same values
have supported successful DIRECT trajectories, an increase is not established as
an accidental technical correction.

```text
hypothetical change = EXPERIMENTAL_PROTOCOL_CHANGE
```

No numeric replacement is recommended. Resource equalization, provider/model
switching, prompt changes, and optimization based on CASE-04 are outside this
audit.

## Accounting

```text
TOKEN_USAGE_FOR_INCOMPLETE_RESPONSE_AVAILABLE = NO
ACCOUNTING_LIMITATION_AFFECTS_TOKEN_BUDGET_AUDIT = PARTIALLY
```

The latest incomplete response has provider request/response evidence but no
token usage. Existing resources record zero response-bearing provider calls when
the adapter raises before returning `ProviderResponse`. This limits quantitative
cost/usage analysis, but does not prevent the contract-level conclusion that the
provider reported `max_output_tokens`.

## Frozen Dimensions and Privacy

- Benchmark, questions, oracle, repository revision, condition, prompts, provider, model, evaluators, schema, Policy A, natural completion, retry policy, guards, and resource semantics were unchanged.
- Runtime/configuration values changed: `NO`.
- No raw, derived, observation, manifest, ledger, or historical report was changed.
- No secrets or provider payloads were read or persisted by this audit.
- No additional token value was tested.

## Files Inspected and Modified

Inspected the V4 manifest, protocol identity code, V4 runtime, provider adapter,
Story 0134, implementation/design reports, V4 historical reports, and persisted
V4 artifacts.

Modified:

```text
docs/evaluation/comparative-baseline-v4-provider-output-token-budget-audit.md
```

No runtime, configuration, test, identity, or provider file was modified.

## Mandatory Answers A-W

- A: `1800` final / `512` intermediate.
- B: `max_output_tokens`.
- C: `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`, `executionConfiguration`.
- D: `NO`; DEVLOG is 1800, DIRECT is 1800 first turn then 512 intermediate turns.
- E: `YES`; it is explicitly frozen in Story 0134, though adequacy was not empirically guaranteed.
- F: `EXPLICIT_EXPERIMENTAL_DESIGN_DECISION`.
- G: `YES`.
- H: `1` confirmed response with `max_output_tokens`.
- I: `1` confirmed incomplete response with unknown/unpersisted reason.
- J: `YES`.
- K: `YES` for the latest observation; not proven globally for all trajectories.
- L: `QUESTIONABLE`.
- M: `NO`.
- N: `YES`.
- O: `YES`.
- P: `EXPERIMENTAL_PROTOCOL_CHANGE`.
- Q: `NO`; token usage is not available for the incomplete response.
- R: `PARTIALLY`.
- S: `NO`.
- T: `NO / NO`.
- U: `NO`.
- V: `YES`.
- W: `YES`, but only as a separate human-reviewed experimental protocol decision, not as an authorized correction.

## Readiness and Next Decision

The audit does not authorize a token change or another live validation. Human
review must decide whether to open a separate protocol decision regarding the
`1800/512` envelope. No numeric value should be selected from this audit alone.

TOKEN_BUDGET_CHANGE_REQUIRES_EXPERIMENTAL_PROTOCOL_DECISION
STOP_FOR_HUMAN_REVIEW
