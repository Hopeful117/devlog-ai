# Story 0237 - ABC Harness Validity: Question Fidelity, Scope Parity and Multi-Case Campaign

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Design approved and implementation authorized by the human on 2026-11-25.
AC-1 to AC-8 are implemented. The Core blockers found during the first
implementation pass were resolved by Story 0238 (freshness aggregation) and
Story 0239 (multi-case contract registry). AC-6 was completed on 2026-11-25
after the human asked to continue. No provider call, live evaluation, commit, push
or merge was performed.

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Replayable AI Intent Evaluation
- ADR-067 - DevLog Engineering Story Context Agent
- Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot
- Story 0232 - Q1 Live Result and Revision Integrity
- [Q1 Live Run 28 Assessment](../../evaluation/devlog-ai-q1-live-run28-assessment-v1.md)

## Problem

Q1 now executes end to end live (run 28), but the measurement is invalid:

1. A and C receive the placeholder question `Q1 bounded live evaluation`
   instead of the frozen benchmark question, scoped to Story 0223 only.
2. C's investigation scope is the single Story 0223 file; it performs no
   action yet reports `SUFFICIENT_EVIDENCE`.
3. B receives the real question plus the Q1 oracle evidence paths.
4. The normalizer reads `finalResponse` instead of
   `finalResponse.analysis.analysis`, so A and C produce no claims.
5. LIVE summaries are labeled `Q1_RUNNER_DRY_RUN_READY`.
6. The harness is hard-coded to Q1, while the human-selected closure evidence
   level is the full 8 x 3 x 3 campaign defined by Story 0223. All eight
   oracles are `ACCEPTED`.
7. Campaigns may start with DevLog freshness `UNKNOWN`.

## Goals

- Every condition receives the exact frozen question for its case.
- All conditions share the same human-reviewed, oracle-independent scope.
- A and C answers normalize into evaluable claims, limitations, abstention and
  confidence.
- The runner supports the deterministic 72-cell matrix (Q1-Q8 x A/B/C x 3).
- Statuses describe the actual execution mode.
- A campaign refuses to start on unknown DevLog freshness.

## In Scope

- `ai-engine/evaluations/devlog_ai_abc_runner/` (live execution, preflight,
  runner, prepared plan, provenance, condition bindings).
- `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py`.
- `docs/evaluation/devlog-ai-abc-benchmark-v1.json` (`scopeFiles` per case),
  campaign manifest, runbook.
- Provider-free tests and fixtures (run 28 raw payloads as normalization
  fixtures).

## Out of Scope

- Production Core, AI Engine service, MCP, prompt, budget, retrieval or
  ranking changes (except a minimal optional `storyId` path if, and only if,
  Core already supports it; otherwise report the gap).
- Oracle modification, semantic scoring, live execution, campaign execution.
- Relaxing revision, scope, grounding, citability, trust or one-shot checks.

## Acceptance Criteria

### AC-1 - Question fidelity
A, B and C requests carry the exact question text of the case from
`devlog-ai-abc-benchmark-v1.json`. Preflight fails closed when the question is
missing, a placeholder, or its hash differs from the frozen question.

### AC-2 - Scope parity
Each benchmark case declares human-reviewed `scopeFiles`, not derived from
oracle evidence. The same set is used as A `files`, C
`allowedPathPrefixes`, and B citable paths. No case-specific path list is
hard-coded in B instructions.

### AC-3 - Story binding
A case not tied to an Engineering Story does not bind an unrelated Story. If
Core requires `storyId` for task binding, the Story records the constraint
and the chosen case-level Story reference explicitly.

### AC-4 - Normalization
Story Context results normalize from `finalResponse.analysis.analysis`:
summary to `answerText`, grounded findings to claims with references,
`missingInformation`/`uncertainties` to limitations, `NOT_ESTABLISHED` to
explicit abstention, `confidence` preserved. Run 28 A/C fixtures yield
non-empty claims.

### AC-5 - Status labeling
LIVE runs never report a DRY_RUN or simulation status.

### AC-6 - Multi-case matrix
The runner generates a deterministic 72-cell assignment matrix with explicit
ASSIGNED, EXECUTED, SEMANTIC_ELIGIBLE, INVALID and MISSING states. Oracle
lookup, statuses and idempotency keys are case- and repetition-aware.

### AC-7 - Freshness gate
Campaign preflight blocks when DevLog freshness is `UNKNOWN`, with an explicit
diagnostic.

### AC-8 - Provider-free validation
Focused tests, `DRY_RUN` and `INTEGRATION_SIMULATION` over 72 cells pass with
zero provider and network calls.

### AC-9 - Live gate
After acceptance, a single Q1 technical validation (one observation per
condition) requires a fresh plan, binding, preflight and explicit human
authorization. The 72-cell campaign requires a separate authorization.

## Required Validation

- `cd ai-engine && python -m pytest tests/test_devlog_ai_abc_runner.py tests/test_devlog_ai_abc_preflight.py tests/test_devlog_ai_abc_live_preflight.py -v`
- New normalizer tests with run 28 fixtures.
- Provider-free `DRY_RUN` and `INTEGRATION_SIMULATION` (72 cells).
- `./backend/mvnw -pl backend test -Dtest=StoryContextCoreIntegrationTest` if
  Core is touched.
- `git diff --check`; independent review.

## Workflow and Gates

1. Human design approval of this Story and of the per-case `scopeFiles`.
2. Explicit implementation authorization.
3. Implement, validate provider-free, review diff.
4. Stop for human acceptance before any live preparation.

## Implementation Report

### Delivered

| AC | Status | Implementation |
|----|--------|----------------|
| AC-1 | Done (Q1) | `benchmark.py` loads the frozen case; A/C send `case.question`; B config question is checked against the same hash; placeholder or altered questions fail closed. |
| AC-2 | Done (proposed scopes) | `scopeFiles` per case in the benchmark (`scopeReviewStatus=PROPOSED_HUMAN_REVIEW`); same set for A `files`, C `allowedPathPrefixes`, B citable-scope instruction. The hard-coded Q1 oracle path list and the "non-trusted" hint were removed from B instructions. |
| AC-3 | Done | A/C send `storyId` only when the case declares `storyNumber` (Q1: none; Q5: 184; Q8: 177). Core accepts a null Story (`AnalyzeStoryContextUseCase`). The runbook Story remains used for the B credential binding only. |
| AC-4 | Done | Normalizer unwraps `finalResponse.analysis.analysis`; summary to `answerText`; `NOT_ESTABLISHED` prefix to abstention (warning `ABSTENTION_FROM_SUMMARY_MARKER`); container freshness kept for acceptability. Run 28 A/C re-normalized in memory: 12 and 17 claims (stored run artifacts untouched). |
| AC-5 | Done | LIVE runs report `Q1_LIVE_EXECUTION_COMPLETED` / `Q1_LIVE_EXECUTION_PARTIAL`. |
| AC-6 | Done | See "AC-6 completion" below. |
| AC-7 | Done (Core defect fixed by Story 0238) | `require_known_freshness` reads the Core projection before B binding reservation and authorization consumption; blocks on `UNKNOWN`/`NOT_ESTABLISHED`/`NO_BASELINE`. |
| AC-8 | Done (Q1) | Tests, DRY_RUN and INTEGRATION_SIMULATION pass with zero external provider/network calls. |
| AC-9 | Unchanged | Any live run still requires a fresh Core plan and explicit human authorization. |

### Conflict 1 - AC-6 requires Core changes outside this Story

`backend/.../evaluation/livepreparation/LiveEvaluationPreparationService.java`
freezes the Q1 contract: manifest id/version/digest, the three Q1 observation
ids, `caseId=Q1`, the Q1 oracle digest and source revision. Any 72-cell
manifest is rejected by Core preparation, and `LiveEvaluationAuthorizationService`
hard-codes `Q1_ORACLE_ACCEPTED`. The Python harness must not bypass Core
authority (ADR-067). A multi-case campaign therefore needs a Core Story that
generalizes live preparation/authorization to a campaign contract. Not done
here; human decision required.

### Conflict 2 - Core freshness is always UNKNOWN

`ProjectFreshnessSummary` has no aggregated status field, so
`StoryContextAgentProjectionV1.freshness()` finds neither `state` nor `status`
and falls back to `UNKNOWN`, even when every checked source reports a status
(run 28: source `PARTIALLY_FRESH`, top level `UNKNOWN`). The AC-7 gate will
therefore block every live run until Core aggregates freshness. Fixing it is a
production Core change outside this Story; human decision required.

### Files Changed

- `ai-engine/evaluations/devlog_ai_abc_runner/benchmark.py` (new)
- `ai-engine/evaluations/devlog_ai_abc_runner/live_execution.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/runner.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/provenance.py` (benchmark added to harness identity)
- `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py`
- `ai-engine/tests/test_devlog_ai_abc_benchmark_case.py` (new)
- `ai-engine/tests/test_devlog_ai_abc_normalization.py`, `test_devlog_ai_abc_runner.py`, `test_devlog_ai_abc_live_preflight.py`
- `docs/evaluation/devlog-ai-abc-benchmark-v1.json`, `docs/evaluation/devlog-ai-q1-asymmetric-pilot-runbook-v1.md`

The frozen manifest, oracle and B configuration are unchanged, so the Core
manifest/oracle digests remain valid.

### Validation

- `pytest tests/test_devlog_ai_abc_*.py`: all pass (2 skipped, frozen-revision only).
- Full AI Engine suite: 916 passed, 2 skipped.
- `DRY_RUN`: `Q1_RUNNER_DRY_RUN_READY`, 0 provider / 0 network calls.
- `INTEGRATION_SIMULATION`: `Q1_INTEGRATION_SIMULATION_PASS`, 2 scripted provider calls, 0 external provider / 0 external network calls.
- Not run: backend tests (no Java changed), any live call.

### Human decisions required

1. Review and accept the proposed `scopeFiles` per case.
2. Approve [Story 0238 - Core Project Freshness Aggregation](../0238-core-project-freshness-aggregation/story.md) (unblocks the AC-7 gate and the epic freshness criterion).
3. Approve [Story 0239 - Core Multi-Case Live Evaluation Campaign Contract](../0239-core-multi-case-live-evaluation-campaign/story.md) (unblocks AC-6), or reduce the closure evidence level.

## AC-6 completion (2026-11-25)

### Delivered
- `docs/evaluation/devlog-ai-abc-campaign-manifest-v1.json` (new):
  - `devlog-ai-abc-campaign` 1.0.0, eight cases, three repetitions;
  - 72 observations derived deterministically in case, then repetition, then
    condition order, with ids `{caseId}-{CONDITION}-r{n}` (identical to Core);
  - status `PROPOSED_DO_NOT_EXECUTE`;
  - canonical digest `8bff8a655ad3daa907e67b4dcc44e999eaece40f9edef0729b9db607f7cc6dfe`.
- Oracle canonical digests frozen for Q2-Q8 in
  `docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json`. They use the same
  algorithm as Q1, Q1 is unchanged, and no oracle content was modified.
- `campaign.py` (new):
  - matrix derivation, structural validation and per-case oracle loading with
    digest verification;
  - the assignment states `ASSIGNED` / `EXECUTED` / `SEMANTIC_ELIGIBLE` /
    `INVALID` / `MISSING`;
  - a report CLI that aggregates batch runs. A cell recorded by two runs fails closed.
- Runner:
  - accepts the campaign manifest and the `--case-id` batch selection;
  - writes the case per observation;
  - adds the assignment matrix to campaign summaries;
  - uses `CAMPAIGN_*` status labels;
  - replays only the cells a batch recorded.
- Live path, one case batch per Core authorization:
  - preparation, plan validation and authorization validation use the batch
    case and its observation set;
  - the oracle status must be `{caseId}_ORACLE_ACCEPTED`;
  - Core's plan names the batch case, and the runner must execute exactly the
    authorized batch;
  - B receives the case question, question id and repetition through
    `_case_runtime_input`;
  - the Q1 B configuration question is still checked for Q1.
- Integration simulation: the Java Core boundary scenario is per condition, so
  a run executes each condition scenario once and records
  `coreBoundaryScenario` on every A/C cell (72 cells in 76 s instead of 48
  Maven runs).
- Core: `LiveEvaluationContractRegistry.ABC_CAMPAIGN` registered (Story 0239)
  with the manifest digest, the eight oracle digests, `repetitions=3`, one
  consumption per case batch, 72 observations and 3 h.

### Validation
- `tests/test_devlog_ai_abc_campaign.py` (new, 26 tests), including a
  cross-language check that the Java registry binds the computed manifest
  digest and the frozen oracle digests.
- ABC harness tests: all pass. Full AI Engine suite: 942 passed, 2 skipped.
- Campaign `DRY_RUN`: 72 cells, `CAMPAIGN_RUNNER_DRY_RUN_READY`, 0 provider / 0 network.
- Campaign `INTEGRATION_SIMULATION`: 72 cells, `CAMPAIGN_INTEGRATION_SIMULATION_PASS`,
  0 external provider / 0 external network calls; replay `PASS`.
- Q1 manifest `DRY_RUN` and `INTEGRATION_SIMULATION` unchanged (`Q1_*` statuses, 3 cells COMPLETED).
- Recomputed Q1 manifest digest equals the digest frozen in Core.

### Not validated
- No live call. A live batch needs, in order:
  1. a rebuilt stack and a refreshed project baseline;
  2. human acceptance of the per-case `scopeFiles`;
  3. one Core authorization per case batch.
- Semantic scoring (human review against the oracles) is a later step.
- Full backend `./backend/mvnw -pl backend -am clean verify -B`: BUILD SUCCESS, 1949 tests, 0 failures, 0 errors.
