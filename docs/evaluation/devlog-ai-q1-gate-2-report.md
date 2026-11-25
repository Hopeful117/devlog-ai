# Q1 Technical Gate 2 - Report

## Identity

| Field | Value |
|-------|-------|
| Artifacts | `data/devlog-ai-q1-gate-2/` |
| Manifest | `devlog-ai-abc-first-test` 1.1.0 (historical, repeatable Q1 contract) |
| Runtime revision | `f60d6ee9` (Stories 0237-0243, pushed, Core-verified, worktree reproducible) |
| Frozen source revision | `0673d1d3` |
| Core plan / authorization | `8cdc8c4d...` READY / `b4404b15...` APPROVED then CONSUMED |
| Status | `Q1_LIVE_EXECUTION_PARTIAL` |
| Integrity | PASS (raw, envelope and derived hashes match for all 3 observations) |
| Provider calls | 13 (A 1, B 11, C 1 task with 2 generations) |

## Operational note

The live B reservation (`story_context_agent_prepare_task_binding` ->
`POST /story-context-agent/projects/{slug}/task-bindings`) answered 404
`ROUTE_NOT_FOUND`. The route is gated by
`devlog.evaluation.live-bootstrap-enabled` (default `false`). Since Story 0242,
live B always reserves a fresh binding, so a live gate now needs that flag.
For this run only, the backend was recreated with
`DEVLOG_EVALUATION_LIVE_BOOTSTRAP_ENABLED=true` as a shell override (no `.env`
change), then recreated again with the default (`false`) right after the run.
The runbook does not document this prerequisite yet.

## Results per condition

### A - DevLog current: COMPLETED
- Frozen question and accepted Q1 scope received. Pinned at `0673d1d3`
  (`REQUESTED_REVISION`). Freshness `FRESH` / `CURRENT`, with
  `revisionConsistency=MISMATCH` (evidence `0673d1d3`, baseline `7ecc1680`)
  reported honestly.
- Accounting: 60 of 1116 candidates selected, 11050 of 12000 tokens used
  (4392 at gate 1). Requested content is now delivered (Story 0243).
- Answer: honest abstention, `NOT_ESTABLISHED`, confidence `LOW`, 14 grounded
  claims, 4 explicit limitations. It names the missing pieces: the full use
  case, generation service and callback bodies are truncated or skipped by the
  content policy (6 files, 4000 characters each).

### B - Direct investigator: COMPLETED
- 11 provider calls, fresh credential binding reserved for this authorization.
- Answer: complete five-stage flow with an invariant ownership table,
  confidence `HIGH`, 4 claims, no stated limitation. 3 of 5 citations resolve
  in the raw ledger; 2 are `CITED_NOT_RESOLVED`.

### C - DevLog engineering analyst: FAILED (Core defect)
- Task `442ae0c7...` FAILED with `INVESTIGATION_ACTION_REJECTED` after two
  investigation actions. Both were rejected by Core, not by the model:
  1. `SEARCH_EVIDENCE` -> `EMPTY` / `NO_AUTHORIZED_EVIDENCE_MATCH`
     (candidates=60, pathMatches=0, revisionMatches=0).
  2. `READ_EVIDENCE_RANGE` on `AnalyzeStoryContextUseCase.java` ->
     `UNAVAILABLE` / `EVIDENCE_UNAVAILABLE`, "Authorized evidence has no
     resolved revision".
- Freshness `FRESH` / `CURRENT`; the scope was pinned at `0673d1d3`.

## Diagnosis of C (`InvestigationCapabilityExecutionService`)

- **D-1 (search not scoped).** A search action passes no requested files to
  `buildRepositoryContext`. Retrieval is therefore project-wide, and the Story
  0243 requested-scope ranking never applies. None of the 60 selected
  candidates lies under the authorized prefixes, so the path filter keeps
  nothing.
- **D-2 (read fails on the first unrevisioned item).** `projectResult` returns
  `UNAVAILABLE` as soon as one path-matching evidence item has no resolved
  revision. Without a Story, commit/diff evidence touching the same file stays
  in the context and carries no `resolvedRevision`. It can come before the
  `SOURCE_FILE` evidence, which does carry the pinned revision, so the read
  fails although valid evidence is present.
- Neither defect is in the model or the prompt. Both stay fail-closed.
  Possible fix, without weakening any check:
  1. scope search retrieval with the action `pathPrefix`, or else the
     authorized prefixes;
  2. skip unrevisioned items and return `UNAVAILABLE` only if no path-matching
     item has a revision.

## Provisional semantic reading (not a scoring)

Human scoring with the review sheets remains required. This reading only
checks the oracle's required claims and forbidden claims.

| Oracle claim | A | B |
|--------------|---|---|
| Q1-C1 Core resolution, authorization, Story/intent, preparation before submission | partial (preparation and Story ownership; the authorization check is not shown) | covered |
| Q1-C2 submission records, identities, budget, dispatch; submission is not completion | omitted (abstains) | covered; the submission vs completion qualification is implicit |
| Q1-C3 Python generation; PLAN/ACTION/FINAL only on the investigation path | omitted | partial (investigation path not mentioned) |
| Q1-C4 Core callback validation, immutable non-trusted persisted snapshot | omitted (abstains) | mostly covered; "non-trusted" not stated |
| Forbidden claims | none | none |
| Answer class (expected `GROUNDED_ARCHITECTURAL_EXPLANATION`) | honest abstention, calibrated | grounded explanation, `HIGH` |

## Gate verdict

**NOT PASSED.** C did not complete. Per the plan, the gate stops without retry.

Checks that pass, compared with gate 1:
- A and C received the frozen question.
- Scope parity holds.
- Revision pinning holds for A and C.
- A has non-empty normalization.
- Freshness is known.
- Integrity is PASS.
- B completes on a fresh binding.

Remaining blockers before the 72-cell campaign:
1. D-1 and D-2 (C cannot investigate a pinned scope without a Story).
2. A's usefulness on Q1 is limited by the content policy (6 x 4000
   characters), not by the token budget. Changing it needs evaluation
   evidence (epic invariant 6).
3. The runbook must document the live bootstrap flag for B.
4. The campaign Q1 slot is consumed (gate 1).
5. Pre-existing callback FK race (Story 0242 report).

## Follow-up (Story 0244)

D-1 and D-2 are fixed, together with a dedicated AI Engine timeout for
investigation actions. A pinned C-style request (same question, scope,
revision and budget) now gets 10 in-scope results from its first search.
The task still fails: the model's second action exceeds the remaining
investigation budget, and the AI Engine treats Core's `BUDGET_EXHAUSTED`
refusal as terminal. See
[Story 0244](../stories/0244-pinned-investigation-action-retrieval/story.md).

Clamp and finalize were added afterwards (Story 0244, "Budget handling"). On
the next pinned run, C gathered 15 in-scope items and reached FINAL. Core then
refused the final callback because the FINAL synthesis exceeded C's
investigation budget (120 s elapsed, 12k provider output tokens). Raising that
budget is an open human decision.

After the human raised C's budget to 300 s / 24k (with an investigation-aware
task deadline), the pinned C-style run `3696e2d1` COMPLETED: 3 actions,
15 in-scope items, FINAL on the first attempt, confidence `MEDIUM`, 2 grounded
architecture findings and an explicit gap on the callback/persistence path.
A full gate 3 (A/B/C through the authorized flow) has not been run yet.
