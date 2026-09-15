# Story0131 Formal RED Baseline Report

## Status

`PARTIAL RED - DIRECT REPOSITORY CONDITION BLOCKED`

The frozen oracle and real Ground-Truth Context passed their preconditions. The
same OpenAI interpreter was executed for DevLog and Ground Truth: four
cases, three repetitions per condition, 24 model calls total, using
`gpt-4.1-mini`. The formal three-condition RED run remains blocked because the
Direct Repository condition has no comparable agent/tool execution capture.

## Preconditions

| Gate | Result |
|---|---|
| Repository ground truth | `19/19 VERIFIED` |
| Oracle | `HUMAN_APPROVED / FROZEN` |
| Ground-Truth Context leakage | `PASS` |
| Direct Repository condition | `BLOCKED - NO COMPARABLE AGENT CAPTURE` |
| DevLog Context condition | `EXECUTED - OPENAI / gpt-4.1-mini / 12 runs` |
| Ground-Truth Context condition | `EXECUTED - OPENAI / gpt-4.1-mini / 12 runs` |
| Formal RED execution | `NO` |

## Comparison matrix

| Metric | DIRECT_REPOSITORY | DEVLOG_CONTEXT | GROUND_TRUTH_CONTEXT |
|---|---:|---:|---:|
| Evidence recall | `NOT_MEASURED` | `10/19` aggregate; `0.419` per-repeat average | `1.000` |
| Evidence precision | `NOT_MEASURED` | `1.000` canonical supplied references | `1.000` |
| Constraint context recall | `NOT_MEASURED` | `NOT_MEASURED` | `NOT_MEASURED` |
| Constraint interpretation recall | `NOT_MEASURED` | `NOT_MEASURED` | `NOT_MEASURED` |
| Causal reasoning accuracy | `NOT_MEASURED` | `0.433` | `0.483` |
| Change-impact context recall | `NOT_MEASURED` | `NOT_MEASURED` | `NOT_MEASURED` |
| Change-impact interpretation recall | `NOT_MEASURED` | `NOT_MEASURED` | `NOT_MEASURED` |
| Evidence grounding | `NOT_MEASURED` | `0.833` | `1.000` |
| Structured unsupported inference | `NOT_MEASURED` | `0.543` | `0.825` |
| Context size / tokens | `NOT_MEASURED` | `~8k estimated tokens` | `NOT_MEASURED` |
| Interactions | `NOT_MEASURED` | `1 context call + 3 model calls per case` | `3 model calls per case` |
| Stability | `NOT_MEASURED` | `NOT_MEASURED` | `NOT_MEASURED` |
| Failure attribution | `NOT_ATTRIBUTABLE` | `CONTEXT_AND_INTERPRETATION; Direct unavailable` | `INTERPRETATION_FAILURE observed` |

## Diagnostic conclusion

`CONTEXT_AND_INTERPRETATION_PROBLEM` for the two executed conditions, with the
Direct comparison still unavailable. DevLog has a measured context gap and
lower causal accuracy than Ground Truth (`0.433` versus `0.483`), while Ground
Truth itself remains below reliable interpretation quality and fails the
CASE-04 negative control in one of three repeats.

Human / agent utility remains `NOT_YET_ESTABLISHED` and is separate from this
technical baseline.

## Execution blocker

- `BLOCKED_CONDITION`: `DIRECT_REPOSITORY` only.
- `EXPECTED_EXECUTION_PATH`: one comparable structured interpreter for the
  unchanged benchmark question over Direct Repository, real DevLog context and
  Ground-Truth Context.
- `ACTUAL_BLOCKER`: no comparable coding-agent/tool execution harness exists for
  Direct Repository. The repository checkout is available, but an OpenAI model
  call without repository tools would not be a valid Direct condition.
- `EVIDENCE`: the existing Direct capture is `NOT_EXECUTED`; the repository
  contains no Direct agent capture runner; DevLog and Ground Truth both have
  real OpenAI structured captures in the dated partial artifacts.
- `MINIMUM_ACTION_REQUIRED`: execute the same interpreter with ordinary pinned
  repository tools for Direct Repository. No product, benchmark, oracle or
  prompt change is required.
