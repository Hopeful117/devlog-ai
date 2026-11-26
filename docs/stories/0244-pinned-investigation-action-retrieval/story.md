# Story 0244 - Pinned Investigation Action Retrieval

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Requested by the human on 2026-11-25 ("Fix D-1 and D-2") after the
[Q1 Technical Gate 2 report](../../evaluation/devlog-ai-q1-gate-2-report.md).

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)
(objective 3: preserve critical evidence through selection, enrichment and projection)

## Problem

In gate 2, condition C (investigation-enabled Story Context, pinned at
`0673d1d3`, no Story) failed with `INVESTIGATION_ACTION_REJECTED`. Both actions
were rejected by Core (`InvestigationCapabilityExecutionService`), not by the
model:

- **D-1.** `SEARCH_EVIDENCE` passed no requested files to
  `buildRepositoryContext`. Retrieval was project-wide, the Story 0243
  requested-scope ranking did not apply, and none of the 60 selected candidates
  lay under the authorized prefixes (`pathMatches=0`). Result `EMPTY`.
- **D-2.** `READ_EVIDENCE_RANGE` returned `UNAVAILABLE` ("Authorized evidence
  has no resolved revision") on the first path-matching item without a
  revision. Commit/diff evidence for the same file carries none, so it hid
  the `SOURCE_FILE` evidence that does carry the pinned revision.

## Design

- **D-1.** A search action's retrieval is scoped to its `pathPrefix`, or else to
  the scope's authorized prefixes. These are passed as requested files, so the
  existing requested-scope selection and ranking apply. The authorized path
  filter is unchanged.
- **D-2.** A path-matching item without a resolved revision is skipped, never
  projected. The action returns `UNAVAILABLE` (`EVIDENCE_UNAVAILABLE`) only when
  path-matching evidence exists, none of it has a revision, and nothing was
  projected. The revision equality check is unchanged.
- **Action timeout (found by the live check).** A scoped search runs Core
  retrieval and in-scope ranking (5.6 to 9.4 s measured). The AI Engine used
  the 5 s callback timeout for actions too, so it gave up while Core
  completed the action. Actions now use their own
  `CORE_INVESTIGATION_ACTION_TIMEOUT_SECONDS` (default 30 s, must be positive).
  Callbacks keep 5 s. The investigation elapsed-time budget (enforced by Core)
  still bounds the whole investigation.
- No budget, selection limit, content policy, authorization or grounding
  change. Both paths stay fail-closed.

## Acceptance Criteria

1. A search with a path prefix requests retrieval for that prefix; a search
   without one requests the authorized prefixes.
2. An unrevisioned item before revisioned evidence for the same path no longer
   prevents a read; the revisioned evidence is returned.
3. Evidence that has no revision at all is still `UNAVAILABLE`.
4. Live: the pinned Q1 condition C executes investigation actions that return
   in-scope evidence.

## Implementation Report

### Files Changed
- `backend/.../storycontextanalysis/investigation/InvestigationCapabilityExecutionService.java`
- `ai-engine/app/clients/core_callback_client.py`, `ai-engine/app/core/config.py`,
  `ai-engine/app/core/dependencies.py`, `docker-compose.yml`: dedicated
  investigation action timeout.
- `ai-engine/tests/test_core_callback_client.py`: +2 (action timeout applied;
  non-positive value rejected).
- `backend/.../storycontextanalysis/investigation/InvestigationCapabilityExecutionServiceTest.java`:
  +3 tests (prefix-scoped search, authorized-prefix fallback, unrevisioned item
  does not hide revisioned evidence). The Story search test now expects the
  `docs` prefix as requested scope.

### Validation
- Focused: `InvestigationCapabilityExecutionServiceTest` (14 tests) and
  `StoryContextInvestigationActionServiceTest`: pass.
- Full backend `clean verify`: BUILD SUCCESS, 1975 tests, 0 failures.
- AI Engine: 950 passed, 2 skipped.
- Live, pinned C-style request through MCP (backend and AI Engine rebuilt,
  same question, scope, revision `0673d1d3` and investigation budget as the
  gate):
  - Run `5314bb3c`: Core completed the scoped search in 9.4 s, but the AI
    Engine timed out at 5 s (`httpx.ReadTimeout`), which led to the dedicated
    action timeout.
  - Run `a7b5a176`: the first `SEARCH_EVIDENCE` returned `RESULTS`, 10
    in-scope items at the pinned revision (5.6 s), including
    `AnalyzeStoryContextUseCase`, `StoryContextCallbackService`,
    `StoryContextCallbackIdentityValidator` and `StoryContextAnalysis`
    (gate 2: `EMPTY`, 0 path matches). AC-1 and AC-4 met.

### Budget handling (requested by the human, 2026-11-25: "Clamp and finalize")

Live run `a7b5a176` showed that the model's second action requested more than
the remaining budget. Core refused it (`BUDGET_EXHAUSTED`), and the AI Engine
treated the refusal as terminal although 10 relevant items had been gathered.

Changes (AI Engine only; Core authority unchanged):
- **Clamp.** Before sending an action, its `resultLimit`, `maximumBytes` and
  `maximumTokens` are lowered to the remaining budget. Remaining usage mirrors
  Core's accounting: one retrieval call per executed action, returned evidence
  items, UTF-8 bytes of summary plus content, tokens = bytes // 4, and nothing
  for rejected actions. Limits are only ever lowered.
- **Finalize.** When no action can fit the remaining budget, or when Core
  refuses an action for budget after at least one action returned evidence,
  the loop asks for the FINAL decision over the gathered results
  (`final_required`). Without gathered evidence the task still fails
  `BUDGET_EXHAUSTED`.
- **Citable list in the investigation prompt.** The investigation prompt now
  carries the Story 0243 `CITABLE REFERENCES` block, and its corrective retry
  carries the `UNKNOWN REFERENCE CORRECTION` rule. Both were missing from the
  investigation path, so the FINAL step repeated the unknown-reference failure
  that Story 0243 fixed for one-shot runs.

Tests:
- `tests/test_story_context_analysis_wire_contract.py`: +4 (limits clamped to
  the remaining budget; finalize after a budget refusal with gathered evidence;
  a budget refusal without evidence stays terminal; finalize without sending an
  action when the evidence budget is spent).
- `tests/test_story_context_analysis_prompt.py`: +1 (investigation prompt and
  retry carry the citable list and unknown-reference correction).
- AI Engine: 955 passed, 2 skipped.

Live run `2b586d86` (clamp and finalize; before the citable-list change):
- Action 1: `RESULTS`, 8 items. Action 2: clamped to the 7 remaining items,
  `RESULTS`, 7 items (15/15). The model's next action could not fit, so the
  loop asked for FINAL instead of sending it. Clamp and finalize behaved as
  designed.
- FINAL attempt 1: 99 s, 5184 output tokens, `GROUNDING_UNKNOWN_REFERENCE`
  (the citable list was missing; fixed above). Corrective retry: 133 s,
  7211 output tokens.
- Core refused the final callback, correctly: "Investigation final provider
  generation exceeds the remaining budget". Totals were about 13.4k of 12k
  provider output tokens and about 264 s of 120 s elapsed.

### C investigation budget (human decision, 2026-11-25: "Raise to 300 s / 24k")

- Harness (`live_execution.py`): C now gets `maximumElapsedMillis=300000` and
  `maximumProviderOutputTokens=24000` (was 120000 / 12000). Other C budget
  values are unchanged. Core caps are 600 s and 100k tokens.
- **Task deadline (consequence of the decision).** `ai-task.timeout-seconds`
  (300 s after submission, every task) would cut a 300 s investigation short
  with `TIMED_OUT`. `StoryContextSubmissionService.applyInvestigationDeadline`
  now sets the deadline of an initialized investigation task to
  max(default timeout, elapsed budget + 120 s margin). One-shot and other tasks
  keep the default deadline set at submission.
- Tests: `StoryContextSubmissionServiceTest` +3 (budget plus margin; short
  budget keeps the default; one-shot untouched).
- Validation: backend `clean verify` BUILD SUCCESS, 1978 tests, 0 failures.
  AI Engine (harness included): 955 passed, 2 skipped.

### Live validation (run `3696e2d1`, pinned at `0673d1d3`, full stack rebuilt)
- **COMPLETED.** Deadline 420 s after submission.
- Actions:
  1. `SEARCH_EVIDENCE`: `RESULTS`, 8 items.
  2. `READ_EVIDENCE_RANGE` on `AnalyzeStoryContextUseCase`: `RESULTS`, 1 item.
  3. `SEARCH_EVIDENCE`: clamped, `RESULTS`, 6 items (15/15).
- The FINAL passed on its first attempt; the citable list removed the
  unknown-reference retry. Usage: 134 s of 300, 5986 of 24k provider output
  tokens, 3 of 5 steps.
- Answer: confidence `MEDIUM`, 2 grounded architecture findings:
  - the controller delegates to the Core use case, and Core supplies and
    protects context while the AI Engine interprets it;
  - Core preparation owns project and Story resolution, consistency and the
    canonical context.
  It reports the callback-to-persistence write path as missing information
  instead of inventing it.
- The harness MCP call reported `POLL_TIMEOUT` because the task outlived the
  tool's wait. The harness re-reads terminal status (`_resolve_poll_timeout`),
  but this should be checked in the next gate.

### Open points
- The live evaluation contracts plan each observation at
  `maxWallClockSeconds=120`, and the campaign 3 h ceiling (Story 0239 D3)
  assumes it. C can now legitimately take up to about 5 minutes. This is a
  planning ceiling, not enforced at runtime, but it no longer reflects C. It
  must be reconciled before the 72-cell campaign.
- Not yet run as a full A/B/C gate. That needs these changes committed and
  pushed, so that Core can verify the runtime revision.
