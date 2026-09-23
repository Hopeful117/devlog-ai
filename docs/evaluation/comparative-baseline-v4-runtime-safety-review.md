# Comparative Baseline V4 Runtime and Safety-Ceiling Review

## Review Scope

This is a read-only review of the implementation currently under
`ai-engine/evaluations/comparative_baseline_v4/`. No code, configuration, V3
artifact, V4 artifact, question, oracle, or repository history was changed.
No provider or network call was made.

The review covers the actual code paths in:

- `comparative_baseline_v4/runtime.py`
- `comparative_baseline_v4/protocol.py`
- `comparative_baseline_v4/v4-manifest.json`
- `comparative_baseline_v4/preflight.py`
- `tests/test_comparative_baseline_v4.py`

V3 trace facts are used only to interpret likely V4 behavior, especially the
V3 malformed `search_repository` calls and byte-exhaustion traces.

## 1. AGENT_DIRECT_OPEN Lifecycle

The current runtime is a single-assignment helper, not yet a complete 18-slot
collection orchestrator. A caller must construct `V4CollectionRuntime` with a
provider, an approved `SafetyCeilings` object, and a tool factory, then call
`run_assignment` with an existing V3 `Assignment` object. The runtime does not
create a run ledger, schedule all assignments, or persist an observation by
itself.

The actual direct path is:

1. `V4CollectionRuntime.run_assignment` calls
   `SafetyCeilings.require_approved()`. Any unresolved ceiling blocks before
   the provider is called.
2. It creates an empty `ResourceAccounting`, records a monotonic start time,
   and creates an empty raw response list and empty direct evidence list.
3. It obtains a repository tool server from `tool_factory(assignment)`.
4. It enters a loop from `1` through `max_model_turns`. Before each provider
   request it checks wall clock time and then compares
   `tool_calls + final_answer_provider_calls` to
   `max_provider_calls_per_observation`.
5. It sends a `ProviderRequest` through `_request`. The request mode is the
   legacy `AGENT_DIRECT` value even when the assignment condition is
   `AGENT_DIRECT_OPEN`. The payload contains only question identity and the
   open read-only access mode. The tool schema is an inline list of six
   operation names.
6. The provider response is appended to `rawOutput`. The implementation then
   increments `navigation_provider_calls` for every direct provider response,
   including a response that is actually a final answer.
7. If `response.kind == "FINAL"`, the runtime increments
   `final_answer_provider_calls`, invokes `evaluate_answer`, and returns a
   V4 observation through `build_observation`.
8. If the response is a tool call, it obtains either `payload["calls"]` or a
   one-item list containing the payload. Each call is checked for a string
   operation and dictionary arguments.
9. A malformed call is recorded as an invalid tool call and immediately stops
   the assignment with `MODEL_TOOL_CONTRACT_FAILURE`. There is no error result
   sent back to the model and no next model turn.
10. A valid call is sent to `tools.execute(operation, arguments)`. The result is
    converted to a byte estimate with `len(str(result).encode("utf-8"))`.
11. If that estimate exceeds `max_read_bytes_per_operation`, the tool has
    already executed. The result is recorded as rejected and the assignment is
    censored with `SAFETY_MAX_READ_BYTES`. Otherwise the result is counted as
    returned. A result containing `reference` and `content` is appended to the
    internal evidence list with a computed SHA-256 digest.
12. The next model request is then made if no stop reason was recorded.
    **Critically, the accumulated tool result is not placed in the next
    `ProviderRequest` payload.** The next request again contains only the
    question identity and access mode. The direct model therefore cannot see
    the repository result through this V4 runtime.
13. If the loop ends due to a ceiling or provider failure without a final
    answer, the runtime creates an observation with structural, grounding, and
    semantic states represented as not evaluated and returns it.
14. For a final answer, `evaluate_answer` first calls the reused V3
    `validate_answer`; a structural failure stops later dimensions. For a
    structurally valid answer, `evaluate_grounding` checks the supplied direct
    evidence list, locator shape/range, exact excerpt occurrence, and optional
    content digest. `evaluate_semantics` then runs independently of grounding.
15. `correctGroundedAnswer` is true only when grounding passes and semantic
    correctness is true. A grounding failure does not suppress semantic
    evaluation for structurally valid answers.
16. `build_observation` embeds raw output, evaluation, resource counters,
    stopping reason, and a raw-output hash. It does not itself write a file.
17. A caller may invoke `write_observation`, which wraps the observation in a
    write-once artifact and writes it. `replay_observation` only verifies the
    raw-output hash and returns a zero-provider-call replay marker. It does not
    recompute the evaluation or resource projection.

The DEVLOG path is one provider request using the caller-supplied context's
`as_input()` result. It has no repository tool server. The existing reusable
`FrozenDevlogContextAdapter` remains V3-specific and still checks V3
`REPOSITORY_BYTE_BUDGETS` when constructing context, although V4 itself does
not define a direct repository byte budget.

## 2. Resource Accounting

| Metric | Exact implementation semantics | Includes failures? | Includes rejected work? | Reliable for V4 analysis? | Notes |
|---|---|---:|---:|---:|---|
| `tool_calls` | Incremented by `record_tool` once for each processed tool call. | Yes, for calls that reach `record_tool`. | No, a call skipped because the operation ceiling was already reached is not recorded. | Partial | It is not a provider-call count. |
| `valid_tool_calls` | Incremented when `record_tool(..., valid=True)` is called after tool execution succeeds and the result is below the per-operation limit. | No. | No. | Partial | A tool execution that returns an oversized result is marked valid before being recorded as rejected. |
| `invalid_tool_calls` | Incremented for malformed call shape or caught `RuntimeContractError`/argument errors from the tool. | Yes. | Not applicable. | Partial | Runtime contract errors and model-generated malformed arguments are conflated. |
| `repository_searches` | Incremented only for valid calls whose operation string is exactly `search_repository`. | No. | No. | Partial | Invalid searches do not count here, although they do count as tool calls. |
| `repository_reads` | Incremented only for valid calls whose operation string is exactly `read_file`. | No. | No. | Partial | Oversized reads are counted as valid only in the local call to `record_tool`, despite being rejected. |
| `bytes_requested` | Caller-supplied `requested` value. Runtime sets it to the serialized result size after tool execution, not request size. Invalid calls record zero. | No for invalid calls. | Yes, for oversized results, using the full estimated result size. | No | The name is misleading: it is mostly post-execution result-size accounting. |
| `bytes_returned` | Caller-supplied `returned` value. Runtime sets it to the result estimate only when under the per-operation limit. | No. | No. | Partial | It excludes rejected results. |
| `bytes_rejected` | Caller-supplied `rejected` value. Runtime sets it to the full result estimate for an oversized result. | No. | Yes. | Partial | It does not mean bytes rejected before execution; the tool already ran. |
| `cumulative_repository_bytes` | Incremented by `returned` only. | No. | No. | No | It is not cumulative requested, attempted, or total repository work. |
| `navigation_provider_calls` | Incremented once after every direct provider response, including final responses. | Provider failures before a response are not counted. | Not applicable. | No | Final-answer calls are double-counted with `final_answer_provider_calls`. |
| `final_answer_provider_calls` | Set/incremented when a final response is detected. | No. | Not applicable. | Partial | It is distinguishable in isolation but overlaps with navigation calls. |
| Model turns | The loop variable exists, but no model-turn counter is persisted in `ResourceAccounting`. | No. | Not applicable. | No | The stopping reason may identify max turns, but consumed turns are absent. |
| Provider calls total | No dedicated total field. The runtime uses `tool_calls + final_answer_provider_calls` as a ceiling check. | Incorrectly. | Incorrectly. | No | Tool operations are substituted for provider calls. |
| Input tokens | Not copied from `ProviderResponse.usage` into the V4 observation/resources. | No. | Not applicable. | No | Raw provider response may contain usage, but there is no normalized V4 field. |
| Output tokens | Not copied from `ProviderResponse.usage` into the V4 observation/resources. | No. | Not applicable. | No | Same limitation as input tokens. |
| Latency | No elapsed duration is recorded per provider call, tool call, or assignment. | No. | Not applicable. | No | The start time is used only for a pre-request wall-clock check. |
| Cost | No cost field or provider-cost extraction exists. | No. | Not applicable. | No | Must remain `NOT_AVAILABLE` when the provider does not expose cost. |
| Stopping reason | `censor(reason)` sets both `stopping_reason` and `safety_ceiling_reached` to the supplied string. | Not a resource counter. | Not a resource counter. | Partial | A later call can overwrite an earlier reason; the runtime usually breaks immediately after the first reason. |
| Safety ceiling reached | Same value as the last call to `censor`. | Not applicable. | Not applicable. | Partial | It cannot currently distinguish a safety ceiling from all other censoring reasons at the field level. |

The test suite verifies counter arithmetic in isolation, but does not verify
multi-turn provider accounting, provider failures, token propagation, latency,
cost, or exact canonical byte accounting.

## 3. Safety-Ceiling Mechanics

The manifest declares all V4 ceiling values as null with status
`PENDING_HUMAN_APPROVAL`. `SafetyCeilings` defaults every field to `None` and
refuses collection until every field is non-null. It does not validate that
approved values are positive, mutually coherent, or identical to manifest
values. The manifest is not automatically loaded into the runtime object.

| Ceiling | Current value | Enforcement semantics | State when reached | Experimental consequence |
|---|---:|---|---|---|
| `max_tool_operations` | Unresolved (`None`) | Checked before processing each call in a provider tool-call response. A call at the limit is not sent to the tool. | `SAFETY_MAX_TOOL_OPERATIONS`; returned observation has no final answer and evaluation states are not evaluated. | Direct exploration is censored, but the skipped call is not represented as an explicit not-executed trace. |
| `max_model_turns` | Unresolved (`None`) | Bounds the `for` loop. The `else` branch censors only when the loop exhausts normally. | `SAFETY_MAX_MODEL_TURNS`. | No final answer is evaluated. Consumed turn count is not persisted. |
| `max_wall_clock_seconds` | Unresolved (`None`) | Checked only before each provider request and once before the DEVLOG request. | `SAFETY_MAX_WALL_CLOCK`. | A provider/tool operation that blocks beyond the limit is not interrupted; the ceiling is checked only afterward, if control returns. |
| `max_provider_calls_per_observation` | Unresolved (`None`) | Checked before each direct provider request using `tool_calls + final_answer_provider_calls`, not actual provider calls. | `SAFETY_MAX_PROVIDER_CALLS`. | Multi-operation responses can consume several tool operations while using one provider call, so the ceiling can stop too early or be misreported. |
| `max_read_bytes_per_operation` | Unresolved (`None`) | The tool executes first; the runtime estimates `len(str(result).encode("utf-8"))`, then rejects the result if over the limit. | `SAFETY_MAX_READ_BYTES`. | It applies to every operation result, not only reads. It is a post-execution result cap, not a pre-execution read safety limit. |

### Ceiling interaction

The runtime normally stops at the first reason and does not evaluate a final
answer afterward. If multiple calls are supplied in one provider response,
the operation ceiling can be encountered before later calls are considered.
The runtime then breaks and retains that reason. `ResourceAccounting.censor`
itself has no first-reason guard, so an independent caller can overwrite the
reason.

There is no cumulative repository-byte ceiling in V4. This is consistent with
the open-condition requirement, but the runtime still has a per-operation
result ceiling and model/operation/provider/wall-clock ceilings.

When a ceiling is reached, `primaryDiagnostic` becomes
`SAFETY_CEILING_CENSORING`, which is useful. However, `build_observation` sets
`structuralValid` to `NO` whenever the supplied evaluation has
`structural.valid == False`. The censor path supplies `structural.valid=False`
with `status=NOT_EVALUATED`, so the persisted observation simultaneously says
`primaryDiagnostic=SAFETY_CEILING_CENSORING` and `structuralValid=NO`. This loses
the required distinction between censored/no-answer and a model structural
failure.

No final answer can currently be produced after a ceiling is reached because
the runtime breaks and returns a no-answer observation. Downstream structural,
grounding, and semantic evaluation do not run in that case. That is correct
for an absent answer, but the persisted state must be explicitly censored and
not structurally invalid.

## 4. What `OPEN` Actually Means

`AGENT_DIRECT_OPEN` is open relative to V3's DEVLOG-derived cumulative
repository-byte budget: V4's manifest has no `repositoryByteBudget` or
`repositoryByteBudgets`, and the runtime has no cumulative byte limit.
Repository exploration cost is intended to be observed rather than normalized.

It is not unconstrained. The following limits remain:

### Experimental constraints

- The direct condition has a common final-answer schema and question identity.
- Repository access is delegated to a read-only tool server.
- The model can request only the six listed operation names through the inline
  tool schema.
- The tool server remains responsible for repository revision and path policy.
- No DevLog context, expected evidence, oracle, or evaluator output is placed
  in the direct input by `condition_input`.

### Safety ceilings

- Maximum tool operations
- Maximum model turns
- Maximum provider calls per observation
- Maximum wall-clock time
- Maximum serialized result size per operation

These are currently unresolved and block collection. They are intended as
operational limits, but several could shape normal work if set near V3 values.
In particular, V3 needed three searches followed by a large read for CASE-01,
and V3's direct agents commonly stopped when a decisive file read exceeded the
remaining budget. A low operation or per-result ceiling would reproduce that
censoring even without a cumulative byte cap.

### Technical constraints

- `ProviderRequest` accepts the legacy two-mode type and V4 maps open direct to
  `AGENT_DIRECT`.
- Final output is hardcoded to 1,800 tokens and direct action output to 512
  tokens in the runtime, rather than loaded from the V4 manifest.
- The provider's context window and request serialization capacity are not
  checked.
- Tool-server path/revision restrictions apply when a real tool server is
  supplied.
- The V4 runtime does not truncate or paginate tool results.

The existing V3 `FrozenDevlogContextAdapter` also retains V3 context-byte
identity checks on the DEVLOG side. That is not a direct-condition budget, but
it is inherited V3 behavior and should be identified as such rather than
described as a wholly independent V4 context implementation.

## 5. Large-File Behavior

The V4 runtime supports no built-in chunking or pagination. It passes the
model's arguments unchanged to `tools.execute`.

- A ranged read is possible only if the injected tool server supports
  `startLine` and `endLine`. The existing `PinnedGitRepositoryTools` does
  support those arguments.
- The runtime itself does not split a large file, request a second range, or
  offer bounded excerpts.
- Search-before-read is possible through the tool contract, but the runtime
  does not enforce or guide that workflow.
- Search result limits are delegated to tool arguments/server behavior. The V4
  runtime does not apply a `maxMatches` limit or record search-result
  truncation.
- A tool result is fully produced before the runtime estimates its size. If it
  exceeds `max_read_bytes_per_operation`, it is rejected after execution and
  not passed to the model.
- There is no provider-context-window check. A result under the per-operation
  ceiling can still contribute to a provider request that exceeds the model's
  context capacity, depending on the provider adapter.
- There is no serialization-capacity check distinct from the `str(result)`
  estimate. UTF-8/canonical JSON size can differ from this estimate.

Thus a generous global ceiling would not by itself guarantee that a model can
inspect an ordinary large repository file. The current V4 runtime needs a
proper result-delivery and bounded-read design before any ceiling value can be
meaningfully reviewed as a safety-only choice.

## 6. Malformed Tool-Call Behavior

For a V3-style request such as
`{"operation":"search_repository","arguments":{"pattern":"PAPER"}}`:

1. The provider response is received and appended to raw output.
2. The runtime sees a syntactically shaped call and invokes the tool server.
3. The tool server raises `RuntimeContractError` because `query` is missing.
4. `record_tool` increments `tool_calls` and `invalid_tool_calls`; byte counters
   remain zero.
5. The runtime calls `censor("MODEL_TOOL_CONTRACT_FAILURE")` and breaks.
6. No deterministic error result is appended to the model context.
7. No subsequent model turn is attempted. The current implementation therefore
   does not reproduce V3's repeated-error trace; it fail-stops after the first
   malformed call.
8. The returned observation contains the raw provider response, one invalid
   tool call, no final answer, and not-evaluated later stages. Its primary
   diagnostic is `MODEL_TOOL_CONTRACT_FAILURE`.

For a malformed outer call lacking a string operation or dictionary arguments,
the same stop behavior occurs before `tools.execute`. For an unexpected generic
exception from the tool server, the runtime records an invalid call and uses
`RUNTIME_INFRASTRUCTURE_FAILURE`. For `RuntimeContractError`, it always uses
`MODEL_TOOL_CONTRACT_FAILURE`, so a tool-server contract defect and a model
argument defect are not fully separable.

Interaction accounting is incomplete: the provider response consumed one
provider/model interaction, but no normalized model-turn or token field is
updated. The direct navigation provider count is incremented after the
response, while invalid tool-call counters are updated after the tool failure.

## 7. Policy A Versus Policy B

The current implementation selects neither policy completely. It does not
provide natural recovery because it does not return the contract error to the
model, and it does not implement a one-retry repair. It is a third policy:
**fail-stop on the first malformed tool call**.

| Policy | What it measures | Advantages | Disadvantages | Realism | Contamination/masking risk |
|---|---|---|---|---|---|
| A: natural recovery only | Whether an agent can interpret a deterministic tool error and correct its own request on a later turn. | Preserves model/tool-use weakness as an observed property; no runtime repair; directly resembles interactive agent use. | Consumes provider call, model turn, and latency; a repeated malformed request can consume safety ceilings without repository progress. | High, provided the exact error returned is the real tool-server error and the loop remains bounded. | Low evaluator contamination risk; high risk that poor schema recovery censors semantic performance. |
| B: one deterministic schema-recovery opportunity | Performance after one uniform, explicitly bounded contract correction opportunity. | Prevents one correctable schema slip from hiding all semantic work; deterministic and auditable if it contains no repository/evaluator hint. | Masks some agent tool-discipline weakness; adds an asymmetric runtime behavior that must be counted; can change the natural direct-exploration task. | Moderate. Real agent harnesses may expose schemas/errors, but automatic retry is not the same as model recovery. | Higher masking and contamination risk, especially if the runtime changes arguments rather than only returning schema information. |

Policy A should mean: return the unchanged deterministic error, make it
available in the next model context, consume normal interaction resources, and
stop only at a separately selected ceiling. Policy B should mean: one
contract-defined opportunity, no argument repair, no expected evidence, no
oracle, no semantic hint, and an explicit provider/turn/tool cost. Humans must
choose; neither is selected here.

## 8. Evidence for Ceiling Selection

No final values are selected in this review. The available evidence supports
candidate ranges and qualification methods, not a silently chosen configuration.

| Ceiling | Observed lower bound | Technical upper considerations | Pathological threshold | Evidence -> inference -> recommendation candidate |
|---|---|---|---|---|
| Tool operations | V3 useful traces used 1-4 successful searches/reads before byte censoring; malformed V3 slots consumed 6 invalid operations. | Provider cost, model-turn count, wall clock, and raw-trace size grow with each operation. | Infinite/repeated searches or repeated malformed calls. | V3 shows that six operations can censor ordinary work, not that a new value is justified. Candidate: qualify a range above representative search/read paths, then freeze a safety-only ceiling. |
| Model turns | V3 traces reached 2-7 logical calls; no final answer in direct failures. | Provider calls and context growth can become unbounded; model may loop after errors. | Repeated tool-search loops without a final answer. | V3 establishes a lower bound of more than one turn for direct exploration. Candidate: use a preflight fixture to establish the minimum representative turns, then add explicit safety headroom. |
| Wall clock | V3 completed observed slots in roughly 2.5-7.0 seconds in the derived data, but this excludes a future real provider/tool distribution. | Provider timeout, tool subprocess timeout, Java bridge timeout, and host scheduling impose finite limits. | Hung provider, hung tool, or pathological long-running loop. | Observed V3 latency is a lower-bound signal only. Candidate: choose a high operational timeout after measuring local tool/provider worst cases; enforce it around operations, not only before requests. |
| Provider calls | V3 collection summary recorded 36 logical model calls and 37 provider calls over 18 slots, including one technical retry. Direct slots used 2-7 calls. | Provider cost, rate limits, and model context accumulation. | Unbounded provider loop or retry storm. | V3 supports separating navigation and final calls, not deriving a single V4 cap from it. Candidate: count actual provider calls and qualify a ceiling above representative direct paths. |
| Per-operation result bytes | V3 attempted reads of 35,376 and 39,296 bytes and rejected them under remaining cumulative budgets; search results were 67-5,092 bytes. | Provider context window, serialization memory, subprocess output, and host memory. | A single pathological file/output dominating the interaction. | V3 proves that ordinary authoritative files can exceed the old remaining budget. Candidate: qualify ranged-read support and set a high safety-only result ceiling based on technical capacity, not DEVLOG context bytes. |
| Cumulative repository bytes | V3 used this as a fairness budget, but V4 explicitly removes that design. | Host memory, provider context, cost, and other ceilings still bound the run. | Unbounded repository scraping. | V3 cannot justify a V4 cumulative fairness limit. Candidate: no experimental cumulative cap; rely on separately approved operational ceilings and observe total bytes. |

The chain is therefore:

`V3 traces -> identify the minimum work and failure patterns -> qualify technical headroom offline -> human approves safety-only ceilings`.

The chain does not support:

`DEVLOG context bytes -> AGENT_DIRECT byte ceiling`.

## 9. Censoring Semantics

The intended conceptual state is `CENSORED / NOT_EVALUATED`. The current
implementation partially supports this:

- `stoppingReason` and `safetyCeilingReached` carry a safety reason.
- `primaryDiagnostic` becomes `SAFETY_CEILING_CENSORING`.
- `grounding.status` is `NOT_EVALUATED`.
- `semantic.semanticCorrect` is `NOT_EVALUATED`.
- `correctGroundedAnswer` is `NOT_EVALUATED`.

The distinction is currently lost or weakened in these locations:

1. `build_observation` converts a censor evaluation with `structural.valid=False`
   into `structuralValid=NO`, which is indistinguishable from structural
   failure unless consumers inspect `primaryDiagnostic`.
2. There is no explicit `executionStatus=CENSORED` or equivalent V4 state.
   Censoring is represented as a diagnostic string rather than a typed state.
3. `classify_failure` checks safety reasons first, but generic callers can
   overwrite the stopping reason by calling `censor` again.
4. `replay_observation` does not replay or verify censor classification; it only
   checks the raw-output hash.
5. There is no V4 collection summary or derived projection that would preserve
   censor counts independently from semantic, grounding, or structural counts.

The current code does not convert a censor into `semanticCorrect=False`, so the
semantic-zero error is avoided. It can nevertheless be misreported as a
structural `NO` or omitted from a future summary unless the typed censor state
is fixed before collection.

## 10. Replay and Reproducibility

V4 replay currently provides integrity verification, not full deterministic
evaluation replay.

| Capability | Current behavior | Classification |
|---|---|---|
| Raw artifact integrity | `write_observation` stores a raw-output hash and artifact hash; `replay_observation` checks only the observation's `rawOutputSha256`. | Integrity check, partial artifact verification |
| Structural evaluation | Not recomputed during replay. | Not replayed |
| Grounding evaluation | Not recomputed. | Not replayed |
| Semantic-only evaluation | Not recomputed. | Not replayed |
| Grounded-primary evaluation | Not recomputed. | Not replayed |
| Failure attribution | Existing persisted diagnostic is returned only as part of the original observation; replay does not validate it. | Stored value, not replayed |
| Censoring | Not recomputed or validated. | Stored value, not replayed |
| Resource accounting | Not recomputed or validated. | Stored value, not replayed |
| Provider/network calls | Replay makes zero provider calls and no network calls. | Offline integrity operation |

The `write_observation` artifact wrapper is write-once through the reused
`write_immutable_json`, but `replay_observation` accepts an observation rather
than validating the full wrapper's `artifactSha256` and `immutable` marker.
No separate deterministic derived-data projection exists; evaluation and
resource data are embedded in the observation.

The documentation must therefore describe current replay as offline raw
integrity verification only. It must not claim that replay reproduces V4
evaluation outputs until structural, grounding, semantic, primary-result,
failure, censor, and resource projections are recomputed and compared.

## 11. Recommended Pilot Shape

After the defects below are fixed and human decisions are approved, the
smallest useful pilot is:

- 3 frozen questions: CASE-01-COMPARATIVE, CASE-03, and CASE-04;
- 2 conditions: DEVLOG and AGENT_DIRECT_OPEN;
- 1 repetition per question and condition;
- 6 slots total;
- no publication-quality comparison claim.

The pilot must exercise:

- at least one successful direct search/read/result/final-answer loop;
- at least one direct ranged-read or large-result boundary case;
- malformed tool-call attribution under the selected recovery policy;
- a structurally valid answer with valid grounding;
- a structurally valid answer with invalid grounding but semantic evaluation;
- a semantic failure with valid grounding;
- a correct grounded answer;
- DEVLOG repository-access prohibition;
- write-once artifact and deterministic replay.

Stop before the 18-slot official collection if any pilot slot demonstrates that
direct tool results are not visible to the next model turn, a ceiling is hit
before a representative final-answer path, a malformed-call policy is not
uniformly accounted, grounding is not deterministic over the same content
domain, or replay changes/loses any evaluation state.

## 12. Implementation Defects Before Collection

The following are implementation defects, not unresolved experimental choices:

1. **Tool results are not returned to the model.** The direct loop does not
   include prior tool results in subsequent provider requests. AGENT_DIRECT_OPEN
   cannot currently perform context acquisition as implemented.
2. **No complete V4 collection orchestration exists.** There is no 18-slot
   scheduler, run ledger, assignment completeness projection, or collection
   summary for V4.
3. **V4 condition identity is not propagated end to end.** The runtime maps
   `AGENT_DIRECT_OPEN` to the legacy `AGENT_DIRECT` request mode and reuses a
   V3 `Assignment` type whose declared condition set does not include the new
   condition.
4. **Provider-call accounting is incorrect.** Tool operations are used in the
   provider-call ceiling check, and final responses are counted in both
   navigation and final-answer categories.
5. **Required resource measurements are absent.** Model turns, input/output
   tokens, per-call latency, total provider calls, cost, rejected/not-executed
   tool trace entries, and requested-versus-returned canonical byte sizes are
   not persisted as V4 fields.
6. **Censoring is not a typed state.** A censored no-answer path is persisted
   with `structuralValid=NO`, risking structural-failure misclassification.
7. **Malformed-tool behavior is not Policy A.** The current runtime does not
   return the deterministic contract error to the model and therefore provides
   no natural recovery opportunity.
8. **Tool/runtime failure attribution is incomplete.** All caught
   `RuntimeContractError` instances from tool execution become model
   tool-contract failures, even when the underlying cause is an adapter defect.
9. **Large-result enforcement is post-execution and broadly named.** The
   `max_read_bytes_per_operation` field applies to every result and is checked
   only after the tool has produced it.
10. **Grounding is not yet the complete frozen authority.** The V4 Python
    grounding helper combines unauthorized and unresolved references, has only
    partial section semantics, does not support commit-hunk resolution, and
    cannot verify an external authoritative snapshot digest.
11. **Semantic oracle binding is incomplete.** CASE-01 and CASE-04 expected
    results are hardcoded, while CASE-03 uses a normalized substring heuristic
    over answer JSON. The evaluator does not fully derive question-specific
    rules from the frozen oracle/semantic contract.
12. **Replay is integrity-only.** It does not validate the complete artifact or
    recompute deterministic evaluation and derived results.
13. **Manifest qualification is incomplete.** V4 identity validation checks
    broad benchmark/repository/oracle identity and assignment count, but not
    exact question wording, benchmark manifest hash, oracle hash, provider
    configuration identity, or safety-value consistency.
14. **The runtime hardcodes output envelopes.** Final and action output limits
    are not loaded from a versioned V4 execution configuration.
15. **Wall-clock enforcement is non-interrupting.** A blocking provider/tool
    call can exceed the ceiling before the next check.

These defects should be fixed and covered by deterministic tests before pilot
approval. None requires changing the questions, oracle, or intended condition
meanings.

## 13. Unresolved Human Decisions

- Final numeric values for all V4 safety ceilings.
- Whether malformed tool calls use Policy A natural recovery or Policy B one
  deterministic schema-recovery opportunity.
- The exact non-hinting error payload and resource cost if Policy A or B is
  approved.
- The definition of a representative offline direct search/read path for
  ceiling adequacy qualification.
- The acceptable technical upper bound for one repository result and the
  provider/context serialization strategy.
- The typed persisted state name and summary treatment for safety-censored
  observations.
- Approval of the V4 semantic-only evaluator contract, including the frozen
  CASE-03 impact-recall rule and oracle binding.
- Approval of the full deterministic replay contract before pilot execution.

# Human Approval Required

The following decisions must be made before the V4 pilot can run:

1. Approve final numeric values for `max_tool_operations`, `max_model_turns`,
   `max_wall_clock_seconds`, `max_provider_calls_per_observation`, and
   `max_read_bytes_per_operation`.
2. Choose malformed-tool policy A or policy B.
3. Approve the exact error/recovery semantics and accounting associated with
   that policy.
4. Approve the offline qualification method used to establish that the chosen
   ceilings permit a representative direct search/read/final-answer path.
5. Approve the V4 typed censoring state and its reporting/denominator rules.
6. Approve the V4 semantic-only evaluation contract and its frozen oracle-bound
   rules.
7. Approve the deterministic replay scope and acceptance criteria.
8. Approve the six-slot pilot authorization after the implementation defects
   listed above are fixed and the offline preflight is rerun.
