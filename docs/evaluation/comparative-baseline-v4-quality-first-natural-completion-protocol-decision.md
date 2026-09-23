# Comparative Baseline V4 Quality-First Natural Completion Protocol Decision

## Decision Scope

This is an offline protocol audit. No provider call, network call, live
validation, official collection, configuration change, identity change, or new
observation was made.

The primary experimental target is:

```text
ANSWER_QUALITY
```

Resource usage is a dependent variable, not an optimization target and not an
equalization target.

## Metric Hierarchy

1. Observation integrity and technical execution opportunity.
2. Answer quality: structural validity, semantic eligibility, semantic correctness, grounding validity, correct-grounded, causal/historical correctness, overclaim, and abstention correctness.
3. Completion/evaluability rates: completion, semantic eligibility, provider failure, provider incomplete, runtime failure, and censoring/runaway rates.
4. Efficiency conditional on meaningful quality/evaluability: tokens, provider calls, model turns, tool calls, repository bytes, latency, and cost.

The following remain distinct and are not semantic errors:

```text
PROVIDER_FAILURE != WRONG_ANSWER
PROVIDER_INCOMPLETE_RESPONSE != WRONG_ANSWER
CENSORED_RUNAWAY != WRONG_ANSWER
NOT_EVALUATED != FALSE
```

## Current Policy and Evidence

Current frozen execution configuration:

```text
finalOutputTokens = 1800
intermediateOutputTokens = 512
```

Propagation remains:

```text
manifest executionConfiguration
  -> v4_execution_configuration()
  -> ProviderRequest.max_output_tokens
  -> responses.create(max_output_tokens=...)
```

Policy by condition:

- DEVLOG: `1800` on its single request.
- AGENT_DIRECT_OPEN: `1800` on turn 1, then `512` on subsequent turns.

Evidence is mixed and must be preserved:

- The same `(1800, 512)` configuration appears in 64 persisted V4 artifacts.
- Four DIRECT observations under that configuration completed after 15, 13, 12, and 11 tool attempts.
- The latest runtime-5 observation ended before usable interaction with `incomplete / max_output_tokens`.
- Therefore `1800/512` can support DIRECT execution, but can also interfere with treatment realization.

The latest failure is not evidence that all DIRECT trajectories require a
larger limit. Conversely, the successful trajectories do not prove adequacy for
all questions or response states.

## Historical Output Distribution

Offline analysis covered 64 persisted V4 observation artifacts. Aggregate
observation `resources.outputTokens` are not per-response ceilings, so they were
not used to select a number.

For 287 persisted provider responses with measured per-response output usage:

| Population | N | Min | Median | P75 | P90 | P95 | Max |
|---|---:|---:|---:|---:|---:|---:|---:|
| All responses | 287 | 16 | 42 | 90 | 158 | 778 | 1026 |
| DEVLOG | 26 | 609 | 807 | 851 | 897 | 960 | 1026 |
| DIRECT | 261 | 16 | 33 | 75 | 102 | 120 | 215 |

These samples are heavily affected by persisted successful/response-bearing
trajectories. Incomplete responses do not provide comparable token usage, and
the sample does not expose provider-internal reasoning allocation. The
statistics are descriptive only and do not justify a new ceiling.

## Natural Completion Definitions

`NATURAL_COMPLETION` means an observation receives a realistic opportunity to
proceed through its assigned treatment until the model emits a final response,
unless a genuine provider/infrastructure failure or an unchanged emergency
runaway guard terminates it. It does not mean unlimited execution, equal
resource use, automatic retry, or successful answer quality.

`REALISTIC_EVALUABLE_OPPORTUNITY` means the treatment was technically permitted
to execute sufficiently for answer quality to be observed and evaluated. It is
not equivalent to correctness. A wrong but completed grounded answer is quality
data; a provider incomplete response before usable interaction is not a wrong
answer.

## Condition Symmetry

DEVLOG and DIRECT do not necessarily require the same numeric per-response
ceiling. They must share the same natural-completion principle and receive
sufficient technical capacity for their intended treatment. DIRECT's multiple
turns and tools justify a distinct turn policy only if that policy does not
normally terminate ordinary treatment behavior. Numeric equality is not itself
the scientific invariant.

## Policy Families

### Policy A: Preserve 1800/512

Advantages: identity-stable, already implemented, and historically capable of
completed DIRECT trajectories. Disadvantage: the latest observation proves that
the envelope can terminate before any usable interaction, so it can act as an
ordinary treatment ceiling rather than only a pathological-runaway guard.

### Policy B: Higher Fixed Technical Ceiling

Best conceptual match for quality-first natural completion if a defensible
provider-supported number is available. It would reduce ordinary truncation
risk while leaving usage observable. No authoritative local numeric ceiling or
provider reasoning-allocation contract is available in this repository, so no
number can be selected here.

### Policy C: Separate Generous DEVLOG/DIRECT Ceilings

Potentially justified by different treatment architectures, but only with a
question-independent technical rationale. It must not be used to advantage one
condition or equalize resources. No defensible condition-specific numeric
values are available offline.

### Policy D: Provider/Model-Supported Natural Maximum

Conceptually avoids micro-budgeting, but the installed SDK exposes only the
request parameter and does not provide a local resolved model maximum or
reasoning/output allocation contract. This policy cannot be implemented or
selected from available evidence.

### Policy E: Other

Not necessary. Additional policy complexity is not justified.

## Decision

The quality-first direction is clear: output limits should provide technical
capacity rather than serve as a hidden resource-equalization mechanism. The
numeric ceiling is not defensible from the available offline evidence because:

- incomplete responses do not expose token usage;
- the provider's internal reasoning/output accounting is not locally specified;
- historical successful outputs are response-bearing survivors and do not bound required capacity;
- the current failure is one confirmed interference event, not a sufficient distribution for tuning;
- no provider/model capability metadata authorizes a numeric replacement.

```text
chosen policy classification = POLICY_DIRECTION_CLEAR_NUMERIC_CEILING_UNRESOLVED
numeric ceiling selected = NO
```

No configuration or identity change is authorized by this audit.

## Protocol Contract Preserved

- Runtime-5 `incomplete` handling remains `PROVIDER_INCOMPLETE_RESPONSE` before parsing.
- Emergency guards remain `96 / 48 / 48 / 300s / 65536`.
- Typed schema `comparative-v4-typed-repository-tools-2.0.0` remains unchanged.
- Provider, model, prompts, questions, oracle, evaluators, Policy A, retry policy, and repository capabilities remain unchanged.
- Resource equalization is not reintroduced.
- Future DEVLOG capacity must be reconsidered if its own output ceiling repeatedly interferes with evaluability; DEVLOG is not assumed to be inherently short or cheap.

## Identity Impact

No implementation occurred, so all identities remain unchanged:

| Identity | Old | New |
|---|---|---|
| Runtime | `comparative-v4-live-runtime-contract-5.0.0` | unchanged |
| Runtime digest | `a3211f44aad4633ca1d725b7a48506a6dd8725ab96becf182e867f1794e80edd` | unchanged |
| Instrumentation | `comparative-v4-instrumentation-3.0.0` | unchanged |
| Execution | `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4` | unchanged |
| Experiment | `1af6f9b79427f6f53bca1f9466fde9ee01c652f5bd082a810566428f2606f4d5` | unchanged |
| Validation plan | `6c423c1548c68365fd432becaa1edb7dedd0f0c7fa070dd668fb5fcba7ecc638` | unchanged |
| Official plan | `fe85cf393dcf9da830af5476ff1e6a398758d97457955223e75cd646f3d521fe` | unchanged |

If a future token configuration is adopted, it requires a new execution,
experiment, validation-plan, and official-plan identity, but not a new runtime
identity unless runtime behavior itself changes.

## Mandatory Answers A-X

- A: `ANSWER_QUALITY`.
- B: `NO`.
- C: `YES`.
- D: `NO`.
- E: `YES`.
- F: `YES`.
- G: `NO`.
- H: `YES`.
- I: `NO`; equal numeric ceilings are not necessary.
- J: `YES`.
- K: `QUESTIONABLE`.
- L: `POLICY_DIRECTION_CLEAR_NUMERIC_CEILING_UNRESOLVED`.
- M: `NO`.
- N: `NOT_SELECTED`.
- O: `NO`.
- P: `NO`.
- Q: `NO`.
- R: `NO / NO / NO`.
- S: `YES`.
- T: `YES`.
- U: `NO`.
- V: `NO`.
- W: `NO / NO / NO`.
- X: `NO`.

## Verification and Preservation

- Offline distribution analysis: `PASS`.
- Provider calls: `0`.
- Network calls: `0`.
- New observations: `0`.
- Runtime/configuration implementation: `NO`.
- Focused tests: not run; no executable behavior changed.
- Comparative tests: not run; no executable behavior changed.
- Historical RAW/DERIVED observations, manifests, ledgers, and reports: untouched.
- Files modified: this report only; pre-existing worktree changes were not reverted.
- `git diff --check`: `PASS`.

## Unresolved Limitations

- No defensible numeric high ceiling is available from local provider/model metadata.
- Incomplete responses lack token usage, preventing direct headroom analysis for the failure population.
- Historical output usage is response-level and sample-selected, not a complete truncation distribution.
- Provider-internal reasoning versus visible-output token semantics remain unknown.
- The decision does not authorize another live validation.

## Readiness and Next Decision

Human review must decide whether to open a separate protocol decision to select a
numeric quality-first ceiling. No value should be implemented or tested until
that decision establishes an authoritative numeric basis and recomputes the
execution/experiment/plan identities.

READY_FOR_HUMAN_REVIEW_NUMERIC_CEILING_DECISION
STOP_FOR_HUMAN_REVIEW
