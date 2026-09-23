# Comparative Baseline V4 Tool Calling and Structured Output Investigation

## 1. Executive Summary

This investigation was an isolated diagnostic exercise. It did not modify the
frozen V4 treatment, run a benchmark observation, execute official collection,
retry a diagnostic cell, execute a repository tool, or change any identity.

Two new provider calls were authorized and executed with the exact current V4
typed tool schemas, `gpt-4.1-mini`, `max_output_tokens=32768`, SDK retries set to
zero, and the same diagnostic prompt:

| Cell | Tools | Strict structured output | Result |
|---|---:|---:|---|
| `one_v4_tool_strict_on` | 1 actual V4 tool | ON | `incomplete/max_output_tokens`, output `[]`, usage `0/0/0` |
| `six_v4_tools_strict_off` | 6 actual V4 tools | OFF | `incomplete/max_output_tokens`, output `[]`, usage `0/0/0` |

The new evidence shows that strict structured output is **not necessary** for
the observed incomplete behavior when the six V4 tools are present. It also
shows that six tools are **not necessary** for the observed behavior when one
actual V4 tool is combined with the strict final schema.

The evidence does not isolate the provider's internal cause. The two new cells
are not a complete factorial matrix: the missing cells are one actual V4 tool
without strict output and six actual V4 tools with strict output is already
covered by historical evidence, but under a different diagnostic prompt and
not all prompt/schema variables are identical. The supported conclusion is
therefore:

```text
CURRENT_V4_TOOLING_CONTRACT_NOT_OPERATIONALLY_VIABLE_FOR_THIS_PROVIDER_SHAPE
```

This is not evidence that the provider generated 32768 tokens, used hidden
reasoning, or returned an unobserved partial tool call.

## 2. Authorization and Frozen Context

The diagnostic authorization allowed at most eight new provider calls, with a
target of two to six. Exactly two calls were made. There were no retries and no
automatic fallback.

Frozen provider configuration:

```text
provider = openai
model = gpt-4.1-mini
resolved model = gpt-4.1-mini-2025-04-14
max_output_tokens = 32768
providerMaxRetries = 0
```

Current V4 identities were inspected but not changed:

```text
experiment = 5cef12f1e9767b9b8ede4213f56cb18be00f7880ef2f16736d55df5c9d413ee4
runtime = comparative-v4-live-runtime-contract-5.1.0
runtimeDigest = 71dfe66779cba85f5439e7eb18b3af7cd95b0e4d9bc46c797316d81d705598a1
```

The current V4 tools are:

```text
read_file
search_repository
git_log
git_show
git_diff
inspect_commit
```

The two calls were executed directly through the installed OpenAI SDK only to
observe the provider response. They were isolated from V4 storage, ledgers,
scoring, repository tools, and official benchmark paths.

## 3. Documentation and SDK Evidence

The current official OpenAI documentation was consulted through Context7,
including the Responses API function-calling guide, Responses `create`
reference, structured-output guidance, reasoning/incomplete-response guidance,
and token-counting guidance.

Relevant documented behavior:

- Responses can expose `status`, `incomplete_details`, `output`, and `usage`.
- `max_output_tokens` is an upper bound for generated response tokens and can
  produce `incomplete_details.reason = max_output_tokens`.
- Function calls are model-generated response items; the application executes
  them only after receiving a usable call.
- Tool-call and other non-visible generated structure can contribute to output
  usage, but the public response does not reveal the provider's internal
  allocation decision.
- The GPT-4.1 Mini model documentation lists a maximum output of `32768` and
  supports Responses API function calling.

Installed SDK:

```text
openai = 2.54.0
dependency range = openai>=2.0,<3.0
```

The SDK returned normal `Response` objects for both incomplete calls. No SDK
upgrade or source modification was made.

## 4. Offline Request Fingerprints

All measurements below are canonical JSON byte counts, not provider tokenizer
counts:

```text
prompt bytes = 189
instructions bytes = 102
serialized input bytes = 219
one V4 tool schema bytes = 428
six V4 tool schemas bytes = 2183
strict response schema bytes = 2152
one-tool strict request shape bytes = 3085
six-tool non-strict request shape bytes = 2590
```

Both calls omitted the following provider-default fields, matching the current
adapter path:

```text
temperature
tool_choice
parallel_tool_calls
```

The diagnostic prompt hash is:

```text
1322c36c95882640cc1f2663e432ea5402ffa08a3f00e89496a0aae442add096
```

The sanitized machine-readable artifact is:

```text
ai-engine/evaluations/comparative_baseline_v4/diagnostics/tool-calling-structured-output-micro-reproducer-20260918.json
```

## 5. New Provider Results

### 5.1 One V4 Tool, Strict Output Enabled

Request shape:

```text
tool count = 1
tool = read_file
strict schema = story0134_common_answer
max_output_tokens = 32768
```

Provider result:

```text
response = resp_0deb4c1eff3ff746006aad3828791887d297253180a6f2fcc2
request = req_f1f387afa4a94b3f8018d0209dfadef1
status = incomplete
incomplete reason = max_output_tokens
output items = 0
first usable tool call = NO
input tokens = 0
output tokens = 0
total tokens = 0
reasoning tokens = 0
latency = 3038 ms
```

No function call was available to execute.

### 5.2 Six V4 Tools, Strict Output Disabled

Request shape:

```text
tool count = 6
strict schema = NOT_SENT
max_output_tokens = 32768
```

Provider result:

```text
response = resp_061980db0acd27f8006aad3829b91487d28c59cd25f884562b
request = req_fd04f6592571421d8d47224fa5f52d6c
status = incomplete
incomplete reason = max_output_tokens
output items = 0
first usable tool call = NO
input tokens = 0
output tokens = 0
total tokens = 0
reasoning tokens = 0
latency = 1281 ms
```

No function call was available to execute.

## 6. Combined Evidence Matrix

| Evidence | Tool shape | Strict output | Outcome | Provenance |
|---|---|---|---|---|
| Control | 0 tools | ON | completed message | previous isolated micro-call |
| Historical control | 1 trivial function tool | OFF | completed function call | previous isolated micro-call |
| New diagnostic | 1 actual V4 tool | ON | incomplete/max output | this investigation |
| Historical target | 6 actual V4 tools | ON | incomplete/max output | previous isolated micro-call and live validation |
| New diagnostic | 6 actual V4 tools | OFF | incomplete/max output | this investigation |

The matrix supports these bounded statements:

1. Strict structured output alone did not fail in the zero-tool control.
2. A trivial function tool alone did not fail in the historical control.
3. The current actual V4 tool schema plus strict output fails with one tool.
4. The current six-tool V4 surface fails even when strict output is removed.
5. The current provider behavior cannot be treated as a successful tool-call
   trajectory or as a structured final-answer trajectory.

The matrix does **not** prove whether the dominant factor is the exact V4 tool
schema, tool-call planning, schema compilation, the prompt interaction, a
provider-side allocation policy, or another undocumented interaction.

## 7. Contract and Treatment Implications

### Current Contract

The current contract requires typed repository tools and a strict final answer
schema in the same provider interaction. Under the observed provider/model
combination, the request can terminate before the first usable tool call. The
runtime must continue to classify this as a provider failure, not as an empty
answer or a successful abstention.

### Candidate A: Tools Without Provider Strict Output

The new six-tool/non-strict cell fails, so removing strict output alone is not a
sufficient treatment. Application-level validation could still be useful after
a usable response exists, but this cell provides no evidence that it restores
liveness.

### Candidate B: Staged Tool Exploration and Strict Finalization

Separate tool exploration from final structured-answer generation. The tool
phase would not send the strict final schema; after bounded tool results are
available, a separate no-tool strict finalization request would produce the
typed answer.

This is the most directly supported candidate because the historical no-tool
strict control completed. It changes the interaction protocol and therefore
requires a new treatment design, runtime identity, accounting treatment, and
replay contract. It must not be applied to the frozen V4 run without explicit
authorization.

### Candidate C: Reduce or Partition the Tool Surface

Reduce schema complexity or partition repository operations across stages.
The current one-tool strict failure means simply reducing six tools to one is
not proven sufficient. This candidate would require additional authorized
diagnostics or a treatment implementation with a clearly defined contract.

### Candidate D: Preserve Current Treatment

Preserve the current treatment and classify the provider behavior as an
observed invalid/incomplete trajectory. This preserves comparability but leaves
the treatment unable to produce answers for this provider shape.

No candidate is selected by this report.

## 8. Fairness, Accounting, Replay, and Identity

### Fairness and Comparability

The two new calls are diagnostics only and must not enter V4 scores, means,
failure rates, or official ledgers. They use a non-benchmark prompt and no
benchmark assignment. A staged treatment would not be comparable to the frozen
single-request treatment without a new treatment arm and explicit identity.

### Accounting

The provider performed two transport attempts and returned two provider
responses, but neither response was usable and neither contained output items.
These diagnostic calls are not part of V4 `ResourceAccounting`. The existing
V4 remediation remains correct: transport attempts, received responses, usable
responses, response-bearing turns, and token usage need separate fields.

### Replay

The diagnostic artifact contains sanitized request fingerprints and provider
identifiers only. It is not a V4 observation and is not replayed through the
V4 deterministic evaluator. No provider replay is implied.

### Identity

No experiment, runtime, execution, validation, official, tool-schema, or
instrumentation identity changed. Any staged treatment, schema change, or
accounting-contract change must define and recompute the affected identities
before collection.

## 9. Root-Cause Classification

| Candidate | Classification |
|---|---|
| Actual generated output consumed all 32768 tokens | Unsupported by these responses; usage is explicit zero |
| Hidden reasoning exhaustion | Unsupported; GPT-4.1 Mini is documented as non-reasoning and the provider exposes no such cause here |
| Strict structured output is the sole cause | Refuted by six-tool/non-strict failure |
| Six tools are the sole necessary cause | Not established; one-tool/strict also fails |
| Current V4 request shape has provider-side incomplete behavior | Supported and reproducible |
| Malformed local V4 tool schema | Not supported; local schema/runtime parity checks pass |
| Runtime should parse or execute incomplete output | Refuted; no output item exists and safety behavior must remain no-parse/no-retry |
| Diagnostic/accounting gap | Confirmed as a separate concern, not the cause of provider termination |

Primary classification:

```text
CURRENT_V4_TOOLING_CONTRACT_NOT_OPERATIONALLY_VIABLE_FOR_THIS_PROVIDER_SHAPE
```

Secondary classifications:

```text
PROVIDER_INCOMPLETE_BEHAVIOR_REPRODUCED_WITHOUT_STRICT_OUTPUT
CAUSE_NOT_ISOLATED_BY_BOUNDED_MATRIX
NO_AUTOMATIC_TREATMENT_AUTHORIZED
```

## 10. Required Human Decision

No code or frozen experiment change is authorized by this report. The next
decision is whether to authorize a separate treatment-design task for one of:

1. staged tool exploration followed by no-tool strict finalization;
2. a newly defined reduced/partitioned tool contract;
3. preserving the current treatment and recording provider incompleteness as
   the outcome.

Before any treatment implementation, define its fairness unit, provider-call
and model-turn accounting, failure semantics, deterministic replay projection,
and all affected identities. Do not rerun official V4 collection until that
decision is explicit.

## 11. Files and Verification

Added diagnostic-only artifact:

```text
ai-engine/evaluations/comparative_baseline_v4/diagnostics/tool-calling-structured-output-micro-reproducer-20260918.json
```

Added this report:

```text
docs/evaluation/comparative-baseline-v4-tool-calling-structured-output-investigation.md
```

The following offline checks were completed before the calls:

```text
one V4 tool schema generated = PASS
six V4 tool schemas generated = PASS
strict response schema generated = PASS
current model/max-token/retry configuration verified = PASS
new provider calls = 2
new provider retries = 0
repository tool executions = 0
official benchmark observations = 0
```

## 12. Readiness

```text
READY_FOR_HUMAN_TREATMENT_DESIGN_DECISION
STOP_FOR_HUMAN_REVIEW
```
