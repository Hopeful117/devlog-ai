# Comparative Baseline V4 Quality-First Provider Output Ceiling Decision

## 1. Question

Determine a defensible numeric provider output ceiling for corrected V4 such
that `max_output_tokens` is a finite technical ceiling rather than an ordinary
quality-affecting micro-budget.

The decision is question-independent, condition-principled, repetition-
independent, and oracle-independent. No value was selected by probing CASE-04
or by comparing live outcomes at multiple candidate values.

## 2. Investigation Boundaries

This investigation is documentation-only plus offline repository/evidence
review.

- Provider calls: `0`.
- Network model calls: `0`.
- New observations: `0`.
- Live validation: not run.
- Pilot: not run.
- Official collection: not run.
- Manifest, runtime, adapter, schemas, instrumentation, tests, identities, prompts, questions, oracle, and evaluators: not modified.

Official OpenAI documentation was accessed on `2026-09-18`.

## 3. Frozen Provider and API

Repository evidence fixes the V4 provider configuration as:

| Field | Frozen value | Evidence |
|---|---|---|
| Provider | `openai` | `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json:25` |
| Model | `gpt-4.1-mini` | `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json:26` |
| API surface | OpenAI Responses API, `responses.create` | `ai-engine/evaluations/comparative_baseline/live_adapters.py:298-335` |
| SDK | Python `openai` SDK, repository constraint `>=2.0,<3.0` | `ai-engine/pyproject.toml:11-15`; runtime identity `sdk_version` in `live_adapters.py` |
| Retries | SDK/provider retries disabled for V4 | `v4-manifest.json:55`; adapter `live_adapters.py:306-314` |

The exact resolved SDK patch version is not frozen in the repository: no lock
file is present and the dependency is a range. The exact API surface and SDK
family are nevertheless established.

The frozen request sends `max_output_tokens` directly to
`responses.create`, and sends V4 typed tools only for DIRECT requests.

## 4. Official Sources

All sources below are first-party OpenAI documentation.

### Source S1: GPT-4.1 Mini model page

- URL: <https://developers.openai.com/api/docs/models/gpt-4.1-mini>
- Access date: `2026-09-18`
- Relevant facts: model ID `gpt-4.1-mini`; context window `1,047,576` tokens; maximum output `32,768` tokens; low latency without a reasoning step; Responses API supported; function calling supported.

### Source S2: Responses API `responses.create` reference

- URL: <https://developers.openai.com/api/reference/python/resources/responses/methods/create>
- Access date: `2026-09-18`
- Relevant facts: `max_output_tokens` is an upper bound for tokens generated for a response, including visible output tokens and reasoning tokens; `max_tool_calls` is a separate parameter for built-in tools.

### Source S3: Token counting guide

- URL: <https://developers.openai.com/api/docs/guides/token-counting>
- Access date: `2026-09-18`
- Relevant facts: reported output usage includes all model-generated tokens, not only visible text; formatting/delimiter tokens and tool-call-related structure can be non-visible; `max_output_tokens` limits all generated tokens, including non-visible tokens; headroom is required when a target amount of visible output is needed.

### Source S4: Reasoning guide

- URL: <https://developers.openai.com/api/docs/guides/reasoning>
- Access date: `2026-09-18`
- Relevant facts: when a model has reasoning tokens, they consume output capacity; reaching `max_output_tokens` can produce `status=incomplete` with `incomplete_details.reason=max_output_tokens`, potentially before visible output; the guide's `25,000` starting buffer recommendation is explicitly for reasoning models and is not transferred to frozen `gpt-4.1-mini` as a V4 number.

### Source S5: Responses streaming events reference

- URL: <https://developers.openai.com/api/reference/resources/responses/streaming-events>
- Access date: `2026-09-18`
- Relevant facts: an incomplete response exposes `incomplete_details.reason`; `max_output_tokens` is a documented reason for incomplete termination.

### Source S6: Function calling guide

- URL: <https://developers.openai.com/api/docs/guides/function-calling>
- Access date: `2026-09-18`
- Relevant facts: a tool call is model-generated response output; Responses tool calling is a multi-step flow where the model emits a function call, the application executes it, and a later response is requested.

### Source S7: OpenAI pricing

- URL: <https://developers.openai.com/api/docs/pricing>
- Access date: `2026-09-18`
- Relevant facts: standard `gpt-4.1-mini` output is listed at `$1.60` per one million tokens; tokens are billed at the selected model's input/output rates; the API surface itself is not separately priced.

## 5. Documented Capacity and Semantics

### Model maximum

The documented model maximum is:

```text
gpt-4.1-mini maximum output tokens = 32768
gpt-4.1-mini context window = 1047576
```

The context window is not the selected output ceiling. It is the total model
context capacity and must accommodate the request input and generated output.
The V4 input/tool payloads are therefore still subject to context-window
availability even when the output ceiling is set to `32768`.

### `max_output_tokens` coverage

| Generated material | Classification | Basis |
|---|---|---|
| Visible final output | `CONFIRMED` | S2 explicitly includes visible output tokens. |
| Reasoning tokens | `CONFIRMED` for models that emit reasoning; `NOT_APPLICABLE` to frozen model | S2 and S4 include reasoning tokens; S1 says frozen `gpt-4.1-mini` has no reasoning step. |
| Model-generated tool-call arguments/call structure | `CONFIRMED` as generated output accounting | S3 says output limits apply to all generated/non-visible tokens and names tool-call-related structure; S6 establishes tool calls as model-generated response output. |
| Tool execution output supplied by the application | `NOT_INCLUDED` in provider generation ceiling | S6 distinguishes the model tool call from the application-generated function-call output. |
| Other non-visible output formatting/delimiters | `CONFIRMED` | S3 explicitly includes non-visible generated formatting/delimiter tokens. |

For this frozen non-reasoning model, the practical ceiling is therefore a
single bound on model-generated output, including visible structured answer or
tool-call generation and any non-visible generated structure. There is no
documented separate reasoning allocation to reserve for `gpt-4.1-mini`.

### Incomplete semantics

OpenAI documents that a response can terminate with:

```text
status = incomplete
incomplete_details.reason = max_output_tokens
```

The documentation also shows that the output can be empty when the generation
limit is exhausted during reasoning. For the frozen model, the exact internal
token-by-token stopping point is not documented; the public contract is the
incomplete status and reason.

## 6. Runtime-5 Failure Interpretation

Persisted V4 evidence records:

```text
status = incomplete
reason = max_output_tokens
visible usable interaction = NO
```

This event is compatible with the documented provider semantics: the provider
terminated generation because the per-response output bound was reached, and
the runtime correctly observed the incomplete response before attempting to
parse a final answer or tool call.

The exact internal allocation between prompt processing, structured-output
generation, tool-call arguments, and any provider-internal work is not exposed
for this model. The investigation therefore does not claim that a particular
number of hidden tokens was consumed.

```text
runtime-5 interpretation = COMPATIBLE_BUT_INTERNAL_ALLOCATION_UNKNOWN
```

This is not a semantic wrong answer and is not evidence that CASE-04 requires
special treatment.

## 7. Provider Maximum vs V4 Ceiling

The provider/model maximum is `32768` generated output tokens per response.
The V4 technical ceiling is a protocol choice that can be lower, but a lower
value would need a principled non-interference basis. Historical averages,
condition differences, cost minimization, and CASE-04 completion would not be
sufficient reasons.

Because the explicit V4 objective is to avoid ordinary treatment truncation,
and because independent observation-level runaway guards already exist, the
provider maximum is the simplest defensible finite technical ceiling. It avoids
introducing an arbitrary fraction or a hidden intermediate micro-budget.

## 8. Historical V4 Evidence

The existing persisted V4 evidence remains secondary and descriptive:

| Condition | N | Median output usage | P95 output usage | Maximum output usage |
|---|---:|---:|---:|---:|
| DEVLOG | 26 | 807 | 960 | 1026 |
| DIRECT | 261 | 33 | 120 | 215 |
| All measured provider responses | 287 | 42 | 778 | 1026 |

These are survivor statistics from response-bearing artifacts. Incomplete
responses lack comparable usage, so they are not a complete distribution of
required generation capacity and are not used to derive `32768`.

The numeric selection comes from the official model maximum plus the quality-
first requirement, not from these historical percentiles.

## 9. Strategy Comparison

### Strategy A: Provider maximum

Selected. It is officially documented, finite, model-specific, question-
independent, and avoids an arbitrary quality-affecting fraction. The primary
risk is maximum theoretical output exposure, which is bounded independently by
V4 observation guards.

### Strategy B: Fixed fraction/headroom below maximum

Not selected. No official source provides a V4-specific fraction, and any
percentage would be an arbitrary budget choice rather than a demonstrated
non-interference boundary.

### Strategy C: Historical/documentation-derived lower ceiling

Not selected. Historical output usage is survivor data and does not measure the
capacity needed by incomplete responses. Deriving a lower number from observed
percentiles would optimize around the observed sample and risk reproducing the
current failure mode.

### Strategy D: Different DEVLOG/DIRECT ceilings

Not selected. The conditions have different treatment architectures, but that
alone does not establish different provider capacity requirements. Different
values would add avoidable treatment-specific budget semantics after resource
usage stopped being an equalization target.

## 10. Selected Policy

```text
selected policy = PROVIDER_MAXIMUM_SELECTED
selectedMaxOutputTokens = 32768
scope = ALL_PROVIDER_RESPONSES
```

The selected value is sufficiently high to function as technical capacity
because it is the documented maximum for the frozen model, not a percentile or
condition-specific budget. It remains finite, and it leaves V4's independent
observation-level guards in force.

The selection is independent of CASE-04 outcome optimization:

```text
CASE-04 outcome optimization = NO
candidate-value probing = NO
live tuning loop = NO
```

The selection is not a claim that every response needs 32768 tokens or that no
response could ever be semantically complete below it. It is a non-interference
ceiling choice.

## 11. DEVLOG Fairness

DEVLOG receives the same natural-completion protection as DIRECT. The policy
does not encode that DEVLOG answers should be short, nor does it derive a
DEVLOG limit from the historical P95 of `960`.

Future DEVLOG responses are allowed to require more output than historical
DEVLOG survivors, up to the same documented per-response ceiling.

## 12. DIRECT Fairness

DIRECT should not remain intentionally restricted to the `512` intermediate
envelope. A DIRECT response may need to reason through a tool choice, emit tool
arguments, inspect repository evidence, continue across turns, and finally
produce the common answer. The provider output ceiling should not decide that
ordinary intermediate capacity is `512` tokens.

The selected common ceiling applies per provider response. It does not remove
the separate V4 limits on tool operations, model turns, provider calls, wall
clock, or repository read size.

## 13. Emergency Guards

The following remain unchanged:

```text
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
```

A `32768` per-response ceiling increases the maximum work possible in one
provider response, but does not permit unbounded observations. The model can
still be stopped by the existing operation, turn, provider-call, wall-clock,
and repository-read guards. These controls address different failure modes and
should not be replaced by a small output micro-budget.

The maximum theoretical provider-generated output exposure per observation is
the common ceiling multiplied by the maximum provider calls, subject also to
the wall-clock and other guards. This is a worst-case bound, not an expected
usage estimate and not an authorization to alter the guards.

## 14. Cost Implications

The configured ceiling is not a token charge. It is an upper bound on one
provider response. Actual generated output remains measured and is the relevant
usage variable.

OpenAI's pricing page lists standard `gpt-4.1-mini` output at `$1.60` per one
million tokens and states that tokens are billed at the selected model's input
and output rates. At that listed standard rate, 32768 generated output tokens
would be approximately `$0.0524` for output tokens alone, before input tokens,
other processing tiers, and any separately priced tools. This is a theoretical
per-response ceiling exposure, not an expected cost.

Raising the ceiling can increase theoretical exposure while leaving actual
expected usage unchanged when responses naturally stop below the ceiling. The
experiment therefore continues to measure actual output tokens rather than
optimizing or equalizing them.

## 15. Configuration Shape Recommendation

```text
CONFIGURATION_SHAPE_RECOMMENDATION = KEEP_EXISTING_FIELDS
```

Although one common ceiling is now scientifically preferred, retaining
`finalOutputTokens` and `intermediateOutputTokens` is the minimum-risk future
implementation shape. During the authorized implementation task, both fields
should receive the same value `32768`, preserving manifest compatibility,
explicit field provenance, and existing validation paths. A later cleanup may
unify the field only as a separately authorized schema change.

This report does not make that implementation change.

## 16. Identity Impact

No identities change during this investigation.

If the selected policy is later implemented:

```text
runtime identity: unchanged unless runtime semantics change
instrumentation identity: unchanged
execution identity: NEW
experiment identity: NEW
validation plan identity: NEW
official plan identity: NEW
```

No new identity values are computed here.

## 17. Unresolved Uncertainties

- The exact resolved Python OpenAI SDK patch version is not locked in the repository.
- The provider does not expose a public per-response token-allocation trace for this frozen model.
- The public documentation does not expose an internal token-by-token stopping allocation for the runtime-5 event.
- Context-window availability still depends on the actual request/input/tool payload; `32768` is an output maximum, not a guarantee that every request can use it.
- Pricing can vary by processing tier and separately priced tools; the qualitative ceiling/actual-usage distinction is stable.

These uncertainties do not prevent selecting the documented model maximum as a
technical ceiling, but they remain relevant to later implementation and
accounting review.

## 18. Files Inspected

- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`
- `ai-engine/evaluations/comparative_baseline_v4/runtime.py`
- `ai-engine/evaluations/comparative_baseline/live_adapters.py`
- `ai-engine/pyproject.toml`
- `ai-engine/data/comparative-baseline-v4/` persisted V4 artifacts
- `docs/evaluation/comparative-baseline-v4-provider-output-token-budget-audit.md`
- `docs/evaluation/comparative-baseline-v4-runtime5-post-remediation-direct-validation.md`
- `docs/evaluation/comparative-baseline-v4-quality-first-natural-completion-protocol-decision.md`

Official sources inspected are listed in Section 4.

## 19. Files Modified

- `docs/evaluation/comparative-baseline-v4-quality-first-provider-output-ceiling-decision.md` (this report only)

Pre-existing worktree changes and untracked artifacts were not reverted or
modified.

## 20. Mandatory Answers A-W

- A: `ANSWER_QUALITY`.
- B: `NO`.
- C: `YES`.
- D: `gpt-4.1-mini`.
- E: OpenAI Responses API, `responses.create`.
- F: `32768` maximum output tokens.
- G: `NO` for the frozen model; the generic Responses parameter includes reasoning tokens on models that emit them, but `gpt-4.1-mini` is documented as having no reasoning step.
- H: `YES`.
- I: `YES`.
- J: `COMPATIBLE_BUT_INTERNAL_ALLOCATION_UNKNOWN`.
- K: `NO`.
- L: `NO`.
- M: `YES`.
- N: `YES`.
- O: `PROVIDER_MAXIMUM_SELECTED`.
- P: `32768`.
- Q: Official GPT-4.1 Mini model documentation specifies a maximum output of 32,768 tokens; official Responses/token-counting documentation specifies that the request ceiling applies to generated output, including non-visible generated material.
- R: `YES`.
- S: `NO`.
- T: `NO / NO / NO / NO` for runtime / provider / model / tool semantics.
- U: `KEEP_EXISTING_FIELDS`.
- V: `YES / YES / YES / YES`.
- W: `NO / NO / NO`.

## 21. Readiness

The official documentation supplies an authoritative numeric basis. The policy
is ready for a separate human-authorized implementation task, but this report
does not authorize implementation or live validation.

The smallest next human decision is:

```text
Authorize implementation of max_output_tokens=32768 for every V4 provider response,
using the existing finalOutputTokens/intermediateOutputTokens fields set equally,
then recompute the execution/experiment/validation/official identities and run
offline tests/preflight only.
```

After that implementation task, exactly one technical live validation may be
authorized separately. If it demonstrates valid natural execution, stop
protocol hardening and authorize the official 18-slot collection unless a
genuinely experiment-invalidating defect is observed. Semantic correctness is
not a technical validation criterion.

READY_FOR_HUMAN_AUTHORIZATION_TOKEN_POLICY_IMPLEMENTATION
STOP_FOR_HUMAN_REVIEW
