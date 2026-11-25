# Q1 Technical Gate 4 - Report

## Identity

| Field | Value |
|-------|-------|
| Artifacts | `data/devlog-ai-q1-gate-4/` |
| Manifest | `devlog-ai-abc-first-test` 1.1.0 (historical, repeatable Q1 contract) |
| Runtime revision | `b0c28391` (Stories 0237-0245, pushed, Core-verified) |
| Frozen source revision | `0673d1d3` |
| Core plan / authorization | `c58f303d...` READY / `0545b6a9...` APPROVED then CONSUMED |
| Status | **`Q1_LIVE_EXECUTION_COMPLETED`** |
| Integrity | PASS (all raw, envelope and derived hashes match) |
| Provider calls | 16 |

The live bootstrap flag for B was enabled for this run only (shell override),
then restored to the default `false`.

## Results per condition

| | A - DevLog current | B - Direct investigator | C - DevLog analyst |
|---|---|---|---|
| Technical status | COMPLETED | COMPLETED | COMPLETED |
| Task | `163e4f27...` | fresh binding | `6c82243e...` |
| Revision / freshness | `0673d1d3` / `FRESH` | `0673d1d3` (envelope) | `0673d1d3` / `FRESH` |
| Latency | 68 s | - | 92 s |
| Confidence | `LOW` | `HIGH` | `MEDIUM` |
| Abstention | yes (`NOT_ESTABLISHED`) | no | no |
| Claims / limitations | 8 / 5 | 4 / 0 | 17 / 2 |

- **C:** one `SEARCH_EVIDENCE` (`RESULTS`, 8 in-scope items), then FINAL on
  the first attempt (no grounding retry). Usage: 83 s of 300, 4.1k of 24k
  output tokens. It states the callback-to-persistence path as unresolved
  instead of inventing it.
- **A:** honest abstention. The use case, callback, generation and persistence
  bodies are truncated by the content policy (6 files x 4000 characters).

## Provisional semantic reading (not a scoring)

Human scoring with the review sheets remains required.

| Oracle claim | A | B | C |
|--------------|---|---|---|
| Q1-C1 Core resolution, authorization, Story/intent, preparation before submission | partial | covered | partial (controller, preparation, Story ownership; the execution authorization check is not shown) |
| Q1-C2 submission records, identities, budget, dispatch; submission is not completion | omitted | covered | omitted |
| Q1-C3 Python generation; PLAN/ACTION/FINAL only on the investigation path | omitted | partial | partial (AI Engine interprets under the Core contract; investigation path not described) |
| Q1-C4 Core callback validation, immutable non-trusted persistence | omitted | mostly covered | partial (Core-mediated callback and non-trusted AI output via ADR-006; persistence explicitly not established) |
| Forbidden claims | none | none | none |
| Calibration | abstention at `LOW`, consistent | `HIGH`, consistent with coverage | `MEDIUM` with explicit gaps, consistent |

## Gate verdict

**PASSED (technical).** All checks from the plan's phase 2 hold:
- A and C received the frozen question.
- A, B and C used the same scope.
- A and C were pinned at the frozen revision.
- A and C have non-empty normalization.
- C ran real in-scope actions.
- Freshness was known.
- Integrity passed.
- All three observations completed.

This is the first gate where all three conditions complete.

## What this says about reliability (not a closure decision)

- **Honesty and grounding hold.** No forbidden claim; DevLog conditions report
  their gaps instead of inventing the flow; confidence matches coverage.
- **Usefulness is still below B on Q1.** Both DevLog conditions miss the
  submission (C2) and persistence (C4) stages. The cause is the same one
  identified since gate 1: the content policy (6 files x 4000 characters)
  truncates the use case, submission and callback bodies, well below the
  token budget. Changing it is a budget change and needs evaluation evidence
  (epic invariant 6). One gate on one case is not that evidence.
- One observation per condition says nothing about variance; gate 3 showed
  that outcomes can change between runs.

## Before the 72-cell campaign

1. **Wall-clock planning ceiling.** The contracts plan 120 s per observation,
   and the campaign 3 h ceiling (Story 0239 D3) is built on it. C's budget is
   now 300 s. Measured latencies in this gate: A 68 s, C 92 s.
2. **Campaign Q1 slot.** It was consumed by campaign gate 1 (per-case single
   consumption).
3. **Runbook.** Document the live bootstrap flag required by B.
4. **Callback FK race** (pre-existing, Story 0242 report).
5. **Content policy.** Decide whether the campaign measures the current policy
   only, or also a variant (for example an A' condition), to produce the
   evidence epic invariant 6 requires.
