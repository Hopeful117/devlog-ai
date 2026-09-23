# Comparative Baseline V4 Corrected Official Collection

## Collection

- Attempt: `v4-official-20260917T233253Z-0a5f0919`
- Path: `ai-engine/data/comparative-baseline-v4/official-collection/v4-official-20260917T233253Z-0a5f0919/`
- Authorized/expected observations: `18`
- Actual observations: `18`
- Conditions: `9 DEVLOG`, `9 AGENT_DIRECT_OPEN`
- Experiment identity: `3c9b60e071cab1342b78ab8bf3d448d6e6d3b673a9c3b317c3b96070fdf4a1fe`
- Execution identity: `f40a1dfd969f148ee8654f48189a2b78882ff89f9f8d517dccf0f9385e9add73`
- Runtime: `comparative-v4-live-runtime-contract-3.0.0`
- Runtime digest: `9d80f6677c37fadbc743b1bc523d781c771872b655b38affde5ac540c228dbb0`
- Official plan: `e1282e398bb17c111aa517a3d15ac4cf91a85de5bb248b4b9d1d2aa1ad1b7809`
- Benchmark: `devlog-comparative-baseline-1.0.0`
- Oracle: `1.0.0`, human-approved
- Repository revision: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`
- Provider/model: `openai` / `gpt-4.1-mini`

## Preflight

`PASS` before provider execution. The frozen 18-slot assignment plan, runtime,
execution, provider/model, benchmark, oracle, repository, six typed tools,
natural-completion policy, Policy A, and emergency guards were active. The
historical generic tool, 12-operation ceiling, and resource equalization were
inactive.

## Status Summary

| Status | Total | DEVLOG | AGENT_DIRECT_OPEN |
| --- | ---: | ---: | ---: |
| `COMPLETED` | 11 | 8 | 3 |
| `RUNTIME_FAILURE` | 5 | 0 | 5 |
| `PROVIDER_FAILURE` | 1 | 0 | 1 |
| `PROVIDER_TIMEOUT` | 1 | 1 | 0 |

### Quality Counts

| Metric | DEVLOG | AGENT_DIRECT_OPEN |
| --- | ---: | ---: |
| Structural `YES` | 6/9 | 3/9 |
| Grounding `YES` | 0/9 | 3/9 |
| Semantic evaluated/eligible | 6/9 | 3/9 |
| Semantic correct | 5/6 evaluated | 0/3 evaluated |
| Correct-grounded `true` | 0 | 0 |

`NOT_EVALUATED` values are excluded from denominators and were not converted to
false or zero. No comparative winner or efficiency conclusion is reported.

## Resource Summary

| Metric | DEVLOG | AGENT_DIRECT_OPEN |
| --- | ---: | ---: |
| Provider calls | 8 | 69 |
| Model turns | 8 | 69 |
| Tool attempts | 0 | 78 |
| Valid executed operations | 0 | 78 |
| Invalid requests | 0 | 0 |
| Searches | 0 | 70 |
| Reads | 0 | 5 |
| Git operations | 0 | 0 |
| Result bytes delivered | 0 | 11,769 |
| Input tokens measured | 54,285 | 83,479 |
| Output tokens measured | 6,709 | 3,002 |
| Latency sum | 80,594 ms | 92,215 ms |
| Cost | unavailable | unavailable |

The `NOT_MEASURED` timeout fields are excluded from token totals. Guard-trigger
count was `0`; no `CENSORED_RUNAWAY` observation occurred.

## Anomaly and Stop Condition

The collection exposed a remaining schema/runtime parity defect despite the
typed tool remediation:

- `CASE-01-COMPARATIVE:AGENT_DIRECT_OPEN:r2` and `r3` emitted `read_file`
  `startLine=0`. The provider schema allowed an integer, but runtime requires
  `startLine >= 1`, producing `invalid repository line range`.
- `CASE-04:AGENT_DIRECT_OPEN:r1` and `r2` emitted `git_log` with `commit=ADR-043`.
- `CASE-04:AGENT_DIRECT_OPEN:r3` emitted `git_diff` with `commit=STORY-0042`.

The provider schema accepts arbitrary strings for commit fields, while runtime
requires a full Git object ID. These are statically knowable/runtime-visible
requirements not expressed by the provider contract. The resulting five
`RUNTIME_FAILURE` observations make the official collection invalid under the
task stop condition. This report does not reinterpret them as treatment quality
outcomes.

## Integrity

- RAW: `18`; DERIVED: `18`.
- Unique observation IDs: `18`.
- Duplicate assignments: `0`.
- Missing assignments: `0`.
- Unexpected assignments: `0`.
- RAW hashes: `PASS`.
- Wrapper hashes: `PASS`.
- RAW-to-DERIVED linkage: `PASS`.
- Deterministic replay: `PASS`; provider/network replay calls `0/0`.
- Resource accounting: `PASS`.
- Contamination: `PASS`.
- Condition isolation: `PASS`.

The artifacts are preserved exactly and are not eligible for corrected official
comparative analysis because the provider-visible contract was still not fully
truthful.

## Calls and Files

- Live provider calls: `77`.
- Live provider network transports: `77`.
- Replay provider/network calls: `0/0`.
- Files created by collection: 18 RAW, 18 DERIVED, collection manifest, and
  ledger under the attempt path.
- Documentation created: this report.
- Historical artifacts: untouched.
- No protocol, runtime, schema, evaluator, guard, benchmark, oracle, or
  assignment changes were made after collection.

## Classification

`OFFICIAL_COLLECTION_INVALID`

`NOT_READY_FOR_ANALYSIS`

Exact next human decision: review and authorize a further offline contract
remediation for statically knowable line-range and Git-object requirements.
Do not rescore or use this collection as the corrected official dataset, and do
not start another collection without fresh human authorization.

STOP_FOR_HUMAN_REVIEW
