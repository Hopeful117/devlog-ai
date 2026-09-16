# Story 0136 - Comparative Pilot Runtime Defect Closure

## Status

`IMPLEMENTED - REVIEWED - ACCEPTED`

This Story closes deterministic runtime defects demonstrated by the first
CASE-01 Comparative Baseline live pilot. It does not authorize a pilot rerun,
official baseline collection, provider calls, network access, statistical
analysis, or protocol changes.

## Authorization

```text
PHASE = STORY_0136_COMPARATIVE_PILOT_RUNTIME_DEFECT_CLOSURE
MODE = PAIR
STATUS = AUTHORIZED

PROTOCOL_CHANGE_AUTHORIZED = NO
EXPERIMENTAL_DESIGN_CHANGE_AUTHORIZED = NO
QUESTION_CHANGE_AUTHORIZED = NO
ORACLE_CHANGE_AUTHORIZED = NO
BUDGET_CHANGE_AUTHORIZED = NO

RUNTIME_FIXES_AUTHORIZED = YES
TESTS_AUTHORIZED = YES
ZERO_PROVIDER_PREFLIGHT_AUTHORIZED = YES

REAL_PROVIDER_CALLS_AUTHORIZED = NO
PILOT_RERUN_AUTHORIZED = NO
BASELINE_COLLECTION_AUTHORIZED = NO
```

## Source Finding

The first CASE-01 pilot produced two diagnostic `LIVE_PILOT` observations,
both with `BASELINE_ELIGIBLE = NO`. DEVLOG returned an identity that was not
the frozen assignment identity. AGENT_DIRECT exhausted the model-turn budget
after repeated failed or empty searches; eight repository operations were
traced even though the frozen maximum is six.

The pilot artifacts remain immutable under:

`/tmp/opencode/story0135-case01-live-pilot-20260916`

## Scope

### In Scope

- Make assignment question identity explicitly provider-visible for every
  frozen question.
- Bind structured-output identity fields to the current assignment literals.
- Express typed locator requirements, including mandatory `COMMIT_HUNK.header`.
- Count every valid repository operation attempt against the frozen budget.
- Preserve and trace provider responses containing multiple tool calls in order.
- Record calls that cannot execute because the tool budget is exhausted.
- Correct generic pinned-Git search parsing, including content containing `:`.
- Preserve separate successful, failed, and non-executed tool diagnostics.
- Add deterministic regression tests and zero-provider preflight checks.

### Explicit Non-Goals

- No change to CASE-01, CASE-03, or CASE-04 wording.
- No change to oracle, expected evidence, scoring, grounding, or experimental
  meaning.
- No increase to `MAX_TOOL_OPERATIONS`, `MAX_MODEL_TURNS`, or repository byte
  budgets.
- No semantic retry, answer selection, model change, or provider change.
- No modification, repair, or replacement of the first pilot artifacts.
- No provider call, pilot rerun, baseline collection, statistical analysis,
  commit, push, or merge.

## Frozen Invariants

```text
MAX_TOOL_OPERATIONS = 6
MAX_MODEL_TURNS = 8
CASE-01-COMPARATIVE_REPOSITORY_BYTES = 32401
CASE-03_REPOSITORY_BYTES = 31683
CASE-04_REPOSITORY_BYTES = 13504

GROUNDING_CONTRACT_VERSION = story0135-comparative-grounding-1.0.0
SCORING_PROJECTION_VERSION = story0135-comparative-scoring-projection-1.0.0
```

## Acceptance Criteria

- Provider-visible DEVLOG input contains exact assignment `questionId` and
  `questionVersion` derived from frozen metadata.
- Structured output binds identity literals when an assignment is available;
  wrong or missing returned identity remains rejected without repair.
- The structured locator contract requires `header` for `COMMIT_HUNK`; invalid
  locators are rejected without repair.
- Every valid repository operation attempt consumes one operation budget unit,
  including empty results and adapter failures.
- No seventh repository operation executes; additional requested calls remain
  traceable as `NOT_EXECUTED_BUDGET_EXHAUSTED`.
- Multiple calls in one provider response execute in returned order while the
  remaining frozen budget permits.
- Pinned Git search handles matches, zero matches, multiple matches, delimiter
  content, frozen revisions, and malformed output deterministically.
- Repository byte accounting remains limited to exposed successful results and
  preserves the frozen per-question budgets.
- The first pilot artifacts and ledger remain unchanged and baseline-ineligible.
- All verification is completed without provider or network execution.

## Verification

```text
FOCUSED_TESTS = 29 passed
FULL_AI_ENGINE_TESTS = 345 passed
JAVA_COMPARATIVE_TESTS = PASS
ZERO_PROVIDER_PREFLIGHT = PASS
REAL_PROVIDER_CALLS = 0
NETWORK_CALLS = 0
NEW_PILOT_OBSERVATIONS = 0
OFFICIAL_BASELINE_OBSERVATIONS = 0
SECRET_SCAN = PASS
GIT_DIFF_CHECK = PASS
```

## Review Gate

Human review must confirm that this Story enforces the already-frozen runtime
contract without changing the experimental design. The Story must remain
uncommitted until explicitly accepted.
