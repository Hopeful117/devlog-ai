# Comparative Baseline V4 Staged DIRECT Treatment Design

## 1. Objective and Decision Boundary

This work designed and minimally validated a new staged DIRECT treatment. It
did not modify the frozen `AGENT_DIRECT_OPEN` treatment in place, execute a
pilot, execute DEVLOG, execute official collection, compare quality, or tune
the provider/model/token policy.

The scientific invariant remains:

```text
DEVLOG structured context -> answer
DIRECT independent repository reconstruction -> answer
```

The staged treatment changes only the DIRECT infrastructure needed to make the
repository-reconstruction arm operationally testable:

```text
question
  -> raw repository exploration
  -> deterministic phase transition
  -> no-tool strict finalization
  -> existing deterministic evaluators
```

The first draft-surface compatibility diagnostic passed, but a second
diagnostic using the exact delivered tool schema failed at the first provider
response. The exact proposed surface is therefore provider-blocked. A
provisional implementation was created after the draft result and one live
validation was attempted; it was removed after the exact-schema gate failed.
No further provider call, retry, simplification, pilot, or official collection
was authorized.

## 2. Repository Audit

Repository-first inspection covered:

- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`
- `ai-engine/evaluations/comparative_baseline_v4/runtime.py`
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`
- `ai-engine/evaluations/comparative_baseline_v4/preflight.py`
- `ai-engine/evaluations/comparative_baseline_v4/pilot.py`
- `ai-engine/evaluations/comparative_baseline/live_adapters.py`
- `ai-engine/evaluations/comparative_baseline/collection_runtime.py`
- `ai-engine/tests/test_comparative_baseline_v4.py`
- `ai-engine/tests/test_comparative_live_adapters.py`
- the previous incomplete-response investigation
- the post-remediation live validation
- the provider observability/accounting remediation report
- the quality-first provider output ceiling decision

Current frozen V4 values were verified:

```text
model = gpt-4.1-mini
finalOutputTokens = 32768
intermediateOutputTokens = 32768
providerMaxRetries = 0
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
```

The current runtime sends six typed tools and the strict final schema on every
DIRECT turn. The staged implementation was kept separate while under test and
was removed after the exact replacement surface failed compatibility.

The existing RAW/replay path already preserves provider lifecycle diagnostics,
tool traces, evidence, usage, and deterministic evaluation. Staged execution
therefore uses the same evaluation semantics but has separate phase-aware RAW,
projection, accounting, and identity values.

## 3. Treatment Identity

The proposed new treatment name was:

```text
AGENT_DIRECT_STAGED
```

The provisional implementation computed these staged identity values; they are
not valid for collection and are not retained as an active treatment:

```text
identity = comparative-v4-staged-direct-experiment-1.0.0
experimentSha256 = cc6357f5f105daaa072e26190ce3a6d6e2689f2b0377ba482e0b5f9404b7491d
runtime = comparative-v4-staged-direct-runtime-1.0.0
raw = comparative-v4-staged-raw-observation-1.0.0
projection = comparative-v4-staged-deterministic-projection-1.0.0
resources = comparative-v4-staged-resource-accounting-1.0.0
instrumentation = comparative-v4-staged-instrumentation-1.0.0
toolSchema = comparative-v4-capability-tools-1.0.0
toolSchemaSha256 = e6583953663885ee19c298a97c3f8531973328c46765152e0794979038998d02
```

The staged identity binds the frozen V4 experiment hash, treatment name,
phase-transition policy, tool schema digest, model, token ceilings, retry
policy, and safety ceilings. It does not reuse the historical `AGENT_DIRECT_OPEN`
identity.

Historical V3/V4 observations, manifests, identities, official plans, pilot
plans, and prior diagnostic artifacts remain immutable.

## 4. Staged Architecture

### Phase 1: Exploration

Exploration receives the frozen question and pinned repository revision. It has
three raw capability tools and no `text` structured-output field. The provider
uses `gpt-4.1-mini`, `max_output_tokens=32768`, and retries disabled.

The model can issue natural tool calls and receives the bounded raw result in
the same conversation. The runtime does not prescribe paths, queries,
commits, operation counts, or a navigation sequence.

### Phase 2: Finalization

When exploration returns a completed assistant message without a function call,
the runtime appends that message and a deterministic finalization instruction
to the accumulated conversation. It then sends a separate request with:

```text
model = gpt-4.1-mini
strict V4 response schema = enabled
max_output_tokens = 32768
provider retries = 0
```

Finalization is mechanical conversion into the existing common answer contract.
It is not allowed to execute tools or acquire new repository knowledge.

### Phase Transition

Selected transition:

```text
NATURAL_MESSAGE_WITHOUT_TOOL_CALL
```

This preserves agent autonomy without adding a fourth provider-facing
`finalize_analysis` capability. A provider response with no tool call is usable
exploration completion only in the non-strict exploration phase. An incomplete,
timeout, malformed, or otherwise failed exploration response stops the
observation and never reaches finalization.

The rejected alternative was a `finalize_analysis` function. It would make the
transition explicit but adds another provider-facing schema element and a
synthetic action that has no repository meaning. The selected transition is
smaller and preserves natural stopping behavior; its premature-stop risk is a
documented threat to validity.

## 5. Capability Matrix

| Existing V4 capability | Staged capability | Mapping | Preserved |
|---|---|---|---|
| ranged file read | `repository_read` | maps to `read_file` | YES |
| repository search | `repository_search` | maps to `search_repository` | YES |
| commit history | `git_inspect(operation=LOG)` | maps to `git_log` | YES |
| commit show | `git_inspect(operation=SHOW)` | maps to `git_show` | YES |
| commit diff | `git_inspect(operation=DIFF)` | maps to `git_diff` | YES |
| commit metadata | `git_inspect(operation=INSPECT)` | maps to `inspect_commit` | YES |

The provider-facing capability count is exactly three:

```text
repository_search
repository_read
git_inspect
```

The surface exposes raw repository content only. It has no question-aware
search, relevance ranking, causal explanation, Story/ADR suggestion, evidence
selection, or answer summarization.

The `git_inspect` operation enum is provider-visible and runtime-validated.
Operation-specific optional fields remain nullable for strict schema
compatibility; full revision/path validity remains a runtime concern. The
offline parity tests cover valid and invalid examples.

## 6. Compatibility Diagnostic

Exactly two new provider compatibility calls were made. Both were
non-benchmark, used no assignment, executed no repository tool, used no strict
final schema, and had no retry. The first tested the three capability concept;
the second used the exact schema generated by the proposed implementation.

```text
model = gpt-4.1-mini
resolved = gpt-4.1-mini-2025-04-14
tools = 3
structured output = OFF
max_output_tokens = 32768
status = completed
first function call = repository_search
arguments = parsed object
output tokens = 26
total tokens = 328
response = resp_0edc9c2245383fca006aad3c44a27087d2b81d286804cd6b0e
request = req_b0557260f18744778ac83b53d7b58bd7
```

The first draft-surface call met the compatibility criterion:

```text
status = completed
first function call = repository_search
arguments = parsed object
```

The exact delivered schema call did not:

```text
toolSchemaSha256 = e6583953663885ee19c298a97c3f8531973328c46765152e0794979038998d02
status = incomplete
incompleteReason = max_output_tokens
outputItems = 0
usage = input 0 / output 0 / total 0
response = resp_0229ac610a8c8de2006aad3efcbec887d2bb5d976d17eab6c2
request = req_fcd6a660bb804915b36740df4983ac16
```

The exact surface therefore fails the Story's compatibility success criterion.
This is the stopping condition `STAGED_DIRECT_EXPLORATION_PROVIDER_BLOCKED`,
not an invitation to continue provider forensics. The diagnostic outputs were
not placed in official benchmark or pilot paths.

## 7. Context Preservation

The staged runtime passes the full accumulated authorized conversation to
finalization. It includes:

- the original frozen question;
- assistant function-call items;
- raw repository tool outputs already delivered to the model;
- assistant's exploration completion message;
- the generic finalization instruction.

No intermediate AI summarizer, deterministic evidence compactor, DevLog
context, oracle, expected answer, evaluator hint, or fresh repository request
is inserted.

The full conversation is serialized deterministically into Responses API input
items. This preserves the natural model/tool trajectory and is auditable from
RAW. The same request uses no repository tools during finalization.

## 8. Fairness Analysis

The staged design preserves the scientific comparison at the capability level:

- same frozen question;
- same frozen repository revision;
- same model and output capacity;
- same read-only repository boundary;
- same deterministic answer, grounding, and semantic evaluators;
- no DevLog-derived path, query, commit, or evidence selection;
- no oracle or expected-answer material in DIRECT payloads;
- no interpreted repository capability exposed to DIRECT.

The staged design does not give DIRECT new repository knowledge. It changes only
the transport protocol required to acquire the same raw capabilities.

Finalization is an additional DIRECT provider call and is part of DIRECT cost.
It is not evaluator overhead and is not hidden. DEVLOG is not artificially
given a matching extra call because fairness is capability-oriented, not
syntactic call-count symmetry. The two arms remain:

```text
DEVLOG: structured context -> answer
DIRECT: repository reconstruction -> structured answer
```

Resource efficiency is interpreted conditional on quality/evaluable opportunity;
no equal resource budget or composite winner is introduced.

## 9. Resource Accounting and Guards

The staged runtime maintains separate `EXPLORATION` and `FINALIZATION`
`ResourceAccounting` objects and an aggregate account. It records:

- transport attempts, received responses, usable responses, and response-bearing
  turns per phase and in total;
- navigation and final-answer provider calls;
- input, output, and total tokens;
- provider transport/provider/assignment latency;
- repository tool attempts, executed operations, failures, timeouts, searches,
  reads, produced/delivered/rejected bytes;
- phase transition and stopping reason.

The emergency ceilings span the complete observation and are not reset at
finalization:

```text
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
```

The finalization call consumes provider-call, model-turn, token, latency, and
wall-clock budget. A guard reached in either phase produces typed censoring and
leaves quality dimensions `NOT_EVALUATED` where appropriate.

## 10. Failure Semantics

The runtime preserves existing typed statuses and uses phase-specific stopping
reasons:

```text
EXPLORATION_PROVIDER_FAILURE
EXPLORATION_PROVIDER_TIMEOUT
EXPLORATION_TOOL_TIMEOUT
EXPLORATION_TOOL_FAILURE
FINALIZATION_PROVIDER_FAILURE
FINALIZATION_PROVIDER_TIMEOUT
FINALIZATION_STRUCTURAL_FAILURE
RUNAWAY_GUARD_*
```

An incomplete exploration response is a provider failure. It is not a blank
answer, semantic failure, or successful abstention. A completed but incorrect
final structured answer would remain a semantic failure, not a runtime failure.

The live validation stopped before any final answer, so structural, grounding,
and semantic quality were not evaluated.

## 11. RAW and Replay Design

Staged RAW persists:

- treatment and assignment identity;
- explicit phase on every provider attempt and response;
- provider lifecycle diagnostics and usage;
- complete conversation transitions;
- tool calls and raw bounded results;
- evidence extracted from delivered repository results;
- phase transition reason;
- finalization response when present;
- phase-specific and aggregate resource accounting;
- failure attribution and latency.

`replay_staged_observation()` verifies the RAW hash, recomputes deterministic
evaluation from the persisted final answer and evidence, checks phase-accounting
presence, and rebuilds the deterministic replay result without provider,
network, or repository-tool execution.

The live artifact was written to:

```text
ai-engine/data/comparative-baseline-v4-staged/live-validation/staged-direct-live-20260918T133526Z-66fd1309/
```

The first launch attempt failed before provider construction because of an
incorrect shell `.env` path; it made zero provider/network calls. The corrected
launch performed the single effective staged validation.

## 12. Implementation Changes

The following provisional implementation was created after the first draft
surface diagnostic passed. It was removed after the exact delivered schema
failed the second compatibility diagnostic. It is listed here for auditability,
not as an active treatment:

- `ai-engine/evaluations/comparative_baseline_v4/staged.py`
  - staged identity;
  - three capability schemas;
  - schema/runtime validation;
  - two-phase runtime;
  - aggregate and phase accounting;
  - phase-aware RAW;
  - provider-free staged replay;
  - staged preflight.
- `ai-engine/evaluations/comparative_baseline_v4/staged_live.py`
  - one-assignment `CASE-04:r1` technical validation runner;
  - isolated live storage;
  - no pilot/official integration.
- `ai-engine/tests/test_comparative_baseline_v4_staged.py`
  - capability parity;
  - adapter phase request shape;
  - staged transition and accounting;
  - incomplete exploration stop;
  - replay;
  - preflight.

The provisional prototype also temporarily modified shared infrastructure:

- `ai-engine/evaluations/comparative_baseline/collection_runtime.py`
  - staged request mode/phase fields;
  - non-strict exploration completion response kind;
  - staged capability names.
- `ai-engine/evaluations/comparative_baseline/live_adapters.py`
  - staged exploration without strict output;
  - staged finalization with strict output and no tools;
  - staged function-name normalization.
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`
  - provider attempt phase metadata.

The frozen V4 manifest, runtime contract, experiment identity, pilot plan,
official plan, historical artifacts, and old reports were not modified. Those
temporary shared changes were removed, and no staged runtime remains after the
exact-schema gate failed.

## 13. Tests and Preflight

Passed before removal of the provisional prototype:

```text
python -m pytest tests/test_comparative_baseline_v4_staged.py tests/test_comparative_baseline_v4.py tests/test_comparative_live_adapters.py -q
95 passed

python -m evaluations.comparative_baseline_v4.preflight
status = PASS
providerCalls = 0
networkCalls = 0
deterministicReplay = READY
```

The provisional staged preflight also passed before cleanup with zero provider
and network calls. It is not an active preflight after the exact surface was
classified provider-blocked.

The full `ai-engine` pytest command was also attempted but could not collect
the unrelated FastAPI-dependent tests because `fastapi` is not installed in
the active environment. The affected historical comparative suites pass after
the provisional staged files were removed.

## 14. Single Live Validation

Exact effective validation:

```text
condition = AGENT_DIRECT_STAGED
question = CASE-04@1.0.0
repetition = r1
runId = staged-direct-live-20260918T133526Z-66fd1309
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
```

### Provider Trajectory

The first exploration request used the three staged tools with strict output
disabled. The provider returned:

```text
providerResponse = resp_00d46aecb9a65205006aad3e20ece087d29ae7305dd1de8537
providerRequest = req_baa68904e29f44fdb8e9275a0d7a8e17
resolvedModel = gpt-4.1-mini-2025-04-14
status = incomplete
incompleteReason = max_output_tokens
outputItems = 0
usage = input 0 / output 0 / total 0
transportLatencyMs = 2450
```

No retry was made. No exploration function call was available.

### Repository Tool Trajectory

```text
repository tool calls = 0
repository tool executions = 0
repository bytes delivered = 0
```

### Finalization Trajectory

Finalization was not reached because exploration produced no usable response.

```text
finalization provider calls = 0
finalization tools = not sent
final structured answer = not produced
```

### Resource Result

```text
exploration transport attempts = 1
exploration provider responses = 1
exploration usable responses = 0
exploration response-bearing turns = 0
finalization transport attempts = 0
finalization provider responses = 0
total transport attempts = 1
total provider responses = 1
total usable responses = 0
total model turns = 0
tool operations = 0
input/output/total tokens = 0/0/0
```

### Quality Result

```text
structural = NOT_EVALUATED
semantic = NOT_EVALUATED
correctGroundedAnswer = NOT_EVALUATED
```

The live artifact replayed successfully with:

```text
providerCalls = 0
networkCalls = 0
repositoryToolExecutions = 0
```

## 15. Threats to Validity

- The provider-specific incomplete behavior remains unresolved and blocks this
  treatment at the first live exploration request.
- The staged protocol is a new treatment and is not historically comparable to
  `AGENT_DIRECT_OPEN` at the execution-contract level.
- The capability-oriented schema differs from the historical six-tool schema.
- Finalization adds a DIRECT provider call and may change resource/latency
  distributions even when technically successful.
- Natural-message transition can end exploration prematurely.
- Full conversation transfer may increase input context and provider behavior.
- The compatibility sample was two provider calls and the live sample was one
  assignment.
- The model and provider are stochastic.
- Questions and oracle/evaluator contracts are human-authored.
- The live validation did not acquire evidence, so capability equivalence was
  tested structurally but not demonstrated end-to-end.

## 16. Acceptance Criteria

### Design

Met:

- explicit staged treatment: YES;
- exploration without strict schema: YES;
- finalization without tools and with strict schema: YES;
- same model/question/frozen repository: YES by contract;
- no DevLog/oracle material in DIRECT: YES by payload boundary;
- capability-equivalent raw access: YES by mapping;
- natural navigation: YES;
- explicit deterministic transition: YES;
- phase accounting, typed failures, phase RAW, replay, and identity changes:
  implemented and tested.

### Provider Compatibility

Not met for the exact delivered surface:

- two diagnostic calls were within the maximum three;
- the draft surface produced a usable first function call;
- the exact delivered surface returned `incomplete/max_output_tokens` with no
  function call;
- no benchmark assignment or repository execution occurred.

### Implementation

Not retained:

- a provisional runtime and tests were created and passed deterministic checks;
- they were removed after the exact-schema compatibility failure;
- no active staged runtime is eligible for further validation.

### Live Validation

Not met:

- no usable exploration response;
- no real repository tool result;
- no phase transition;
- no strict finalization;
- no parseable final answer.

## 17. Mandatory Answers A-Z

- A: `NO`; frozen historical V4 was not modified in place.
- B: `YES`; `AGENT_DIRECT_STAGED` was defined.
- C: `AGENT_DIRECT_STAGED`.
- D: `NO`; exploration sends no strict final schema.
- E: `NO`; finalization exposes no repository tools.
- F: `YES`.
- G: `YES`; `gpt-4.1-mini`.
- H: `YES`; `max_output_tokens=32768` in both phases.
- I: `NO`; no retries were introduced.
- J: `3`.
- K: `repository_search`, `repository_read`, `git_inspect`.
- L: `YES`; all six previous raw capabilities are mapped.
- M: `NO`.
- N: Natural assistant completion without a function call.
- O: Full accumulated conversation plus the generic finalization instruction.
- P: `YES`.
- Q: `YES`; provider-call and wall-clock guards span both phases.
- R: `2` diagnostic provider calls.
- S: `NO` for the exact delivered schema; the draft schema returned a valid
  `repository_search` call, but the exact schema returned no function call.
- T: `NO`; the provisional runtime was removed after the exact compatibility
  gate failed.
- U: `YES` for the provisional provider-free checks; this does not qualify the
  exact provider contract.
- V: `1` staged live technical validation; no benchmark observation.
- W: `NO`.
- X: `NO`.
- Y: `NO`; finalization was not reached.
- Z: `NO`; the exact exploration surface is provider-blocked.

## 18. Additional Answers AA-AQ

- AA: The six-tool plus strict contract repeatedly returned incomplete before a
  usable tool call; removing strict output alone also failed. The capability
  surface is therefore separated from finalization and reduced to raw domains.
- AB: `repository_read -> read_file`; `repository_search ->
  search_repository`; `git_inspect(LOG) -> git_log`; `SHOW -> git_show`; `DIFF ->
  git_diff`; `INSPECT -> inspect_commit`.
- AC: The draft three-capability surface returned a parsed valid
  `repository_search` call. The exact delivered schema then returned
  `incomplete/max_output_tokens`, output `[]`, and usage `0/0/0`.
- AD: Natural exploration completion without a function call, because it adds no
  synthetic repository capability and preserves agent-selected stopping.
- AE: A `finalize_analysis` tool was rejected as an unnecessary fourth surface;
  six specialized tools were rejected by the prior provider evidence; strict
  output during exploration was rejected by the same evidence.
- AF: The frozen question, all assistant/tool conversation items, delivered raw
  tool results, the exploration completion message, and generic finalization
  instruction.
- AG: DevLog context, oracle/expected answer, evaluator hints, hidden paths or
  commits, fresh repository searches, and interpreted summaries.
- AH: Phase-specific and aggregate provider attempts, responses, usable turns,
  tokens, latency, tool operations, and bytes are persisted; finalization cost
  is included in DIRECT totals.
- AI: Exploration failures use `EXPLORATION_*`; finalization failures use
  `FINALIZATION_*`; emergency guards remain typed censoring.
- AJ: Replay hashes RAW, recomputes evaluation from persisted final/evidence,
  verifies phase accounting, rebuilds projection, and performs zero provider,
  network, or repository executions.
- AK: Provisional staged treatment, runtime, RAW, projection, resource,
  instrumentation, and capability-tool identities were computed during the
  bounded implementation; none is active for collection.
- AL: All historical V3/V4 manifests, observations, reports, plans, identities,
  and the frozen `AGENT_DIRECT_OPEN` treatment remained immutable.
- AM: `NO`; only raw equivalent repository capabilities are exposed.
- AN: `NO` by design; the agent chooses queries, paths, commits, and operation
  count. The natural completion transition introduces only the minimum protocol
  constraint needed for finalization.
- AO: One additional provider call is required for finalization when exploration
  completes; it is fully counted in DIRECT resources. The live failure incurred
  no finalization call.
- AP: Provider-specific behavior, new protocol, schema change, extra call,
  context transfer, stochasticity, human-authored questions, and one-sample
  technical validation.
- AQ: Decide whether to redesign/authorize another staged treatment attempt,
  preserve the provider-blocked classification, or abandon staged DIRECT. Do
  not authorize pilot or official collection based on this result.

## 19. Files Modified and Artifacts

Net implementation files retained from this task:

- none; the provisional staged implementation was removed after the exact
  compatibility gate failed.

Live validation artifacts:

- `ai-engine/data/comparative-baseline-v4-staged/live-validation/staged-direct-live-20260918T133526Z-66fd1309/observation.json`
- `ai-engine/data/comparative-baseline-v4-staged/live-validation/staged-direct-live-20260918T133526Z-66fd1309/replay.json`

No commit, push, merge, rebase, reset, or history rewrite was performed.

## 20. Final Classification and Required Human Decision

The draft capability concept was compatible once, but the exact delivered
exploration schema was provider-blocked. The complete staged trajectory was
not technically validated, and the provisional runtime was removed rather than
continuing to simplify the contract.

Exact next human decision:

```text
Decide whether any new staged-direct treatment design is warranted after the
provider-blocked exploration result. Do not authorize pilot, DEVLOG execution,
semantic comparison, or official V4 collection. If another treatment is
considered, define a new protocol and identity rather than modifying this
historical or failed staged treatment in place.
```

STAGED_DIRECT_EXPLORATION_PROVIDER_BLOCKED
NOT_READY_FOR_STAGED_DIRECT_IMPLEMENTATION
STOP_FOR_HUMAN_REVIEW
