# Q1 Live Run 28 — Reliability Assessment v1

## Status

ASSESSMENT — decision input only. Not a semantic score, not Story acceptance.

## Run Identity

| Field | Value |
|-------|-------|
| Artifacts | `data/devlog-ai-q1-live-run28/` |
| Source revision | `0673d1d32433cdaff054d5f1defd1bedd01c0039` |
| Runtime revision | `eacaa1e3e897850a87e8fee9bd2d68b103ca577d` |
| Manifest | `devlog-ai-abc-first-test` 1.1.0 |
| Integrity report | `PASS` (raw, envelope and derived hashes match) |
| Technical status | A, B, C = `COMPLETED`, retryCount 0 |
| Acceptability | `NOT_EVALUATED` x3 |

## Verdict

The run is **technically complete but not valid as a reliability measurement**.
It cannot support closing the epic `agent-response-reliability`.

## Findings

### F1 — A and C never received the Q1 question (validity defect)

`CoreLiveTreatmentAdapter.execute` sends the placeholder
`"question": "Q1 bounded live evaluation"`
(`ai-engine/evaluations/devlog_ai_abc_runner/live_execution.py`), scoped to
Story 0223 and the single file `docs/stories/0223-agentic-quality-real-story-pilot/story.md`
(per runbook `DEVLOG_LIVE_STORY_NUMBER=223`, `DEVLOG_LIVE_FILES=...0223...`).
The Story Context prompt does consume `request.question`
(`ai-engine/app/prompts/story_context_analysis.py`). A and C therefore
summarized the Story 0223 pilot instead of answering the frozen Q1 question
("How does a Story Context request move from Core authorization to the final
persisted analysis, and which side owns each invariant?").

### F2 — C could not investigate

C `allowedPathPrefixes` equals the single Story 0223 file; the investigation
trace is empty (`trace/events.json = []`). C nevertheless reported
`investigationStopReason=SUFFICIENT_EVIDENCE` and `confidence=HIGH`.

### F3 — B is advantaged (partial oracle leakage)

B receives the real question and its instructions enumerate the seven Q1
oracle evidence paths. The A/B/C comparison is not equivalent.

### F4 — Normalizer misses the Story Context contract

`_final_payload` stops at `finalResponse`, while the analysis lives at
`finalResponse.analysis.analysis`
(`ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py`). A and C
are normalized to `answerText=null`, `claims=[]` although raw payloads are
69–78 KB.

### F5 — Live summary mislabeled

`execution-summary.json` reports `status=Q1_RUNNER_DRY_RUN_READY` for a LIVE run.

### F6 — Freshness unknown

All DevLog observations: `freshness.status=UNKNOWN`, warning
`PROJECT_CONTEXT_STALE`; the last baseline analysis completed 2026-09-22.

Root cause (identified during Story 0237): `ProjectFreshnessSummary` carries no
aggregated status, so `StoryContextAgentProjectionV1.freshness()` always falls
back to `UNKNOWN`, while the only checked source reports `PARTIALLY_FRESH`.
The top-level `UNKNOWN` is therefore a Core aggregation defect, not only
staleness.

### F7 — Usable reliability signals

- A abstained honestly: `confidence=LOW`, summary prefixed `NOT_ESTABLISHED`,
  explicit `missingInformation`. This matches the epic's honesty goal.
- C's `HIGH` confidence with no investigation actions is a calibration concern
  to re-check once F1/F2 are corrected.
- A DevLog preflight (2026-11-25, task `efca347b-d19f-4699-bf4f-72ce933698da`)
  selected explicitly requested files (`epic.md`, Q1 reports) without
  retaining their content (`EVIDENCE_SUMMARY_TRUNCATED`) and abstained. This
  is a concrete instance of critical requested evidence lost to budget
  (epic objective 3; related Story 0234).

## Consequences

- No semantic conclusion about DevLog reliability may be drawn from run 28.
- The harness must be corrected (Story 0237) before any further live run.
- Any further live run requires a fresh plan, binding, preflight and explicit
  human authorization (Story 0232 AC-9).
