# Story 0220 Implementation Report

## Repository State

- Branch: current working branch
- Worktree: modified with the Story 0220 evaluation artifacts listed below
- Commit/push: not performed

## Governance

- Governing ADRs: ADR-006, ADR-063, ADR-066, ADR-067, ADR-079
- Related Stories: 0178, 0209, 0219
- Authorized scope: evaluation and bounded reporting only

## DevLog Context

The DevLog Story Agent preflight was attempted before remediation, but its
structured-output contract failed with `STRUCTURAL_CONTRACT_ERROR`. Repository
Stories, contracts, tests, and the current source tree were therefore used as
the authoritative fallback. This limitation is recorded rather than presented
as successful DevLog context.

## Files Changed

- `ai-engine/evaluations/story_context_runtime_loss.py`: bounded capture
  validation, revision checks, terminal-state normalization, semantic
  eligibility, deterministic aggregation, and JSON/text CLI reporting.
- `ai-engine/evaluations/scenarios/story-0220-runtime-loss-v1/manifest.json`:
  deterministic scenario manifest and expected digest inputs.
- `ai-engine/evaluations/scenarios/story-0220-runtime-loss-v1/captures.jsonl`:
  eight provider-free captures covering success, projection reduction,
  insufficient evidence, provider incomplete, timeout, rejection, and mixed
  revision invalidation.
- `ai-engine/tests/test_story_context_runtime_loss.py`: validation, precedence,
  visibility, denominator, and deterministic digest tests.
- `ai-engine/evaluations/scenarios/architecture-overview-v2-enriches-v1/{scenario,replay}.json`:
  refreshed the stale schema digest required by the existing evaluation harness.
- `docs/stories/0220-revision-pinned-runtime-loss-evaluation/*`: implementation
  plan and reports.

## Implementation Summary

The evaluator accepts only bounded, revision-pinned captures and converts
invalid or technically incomplete evidence to `NOT_EVALUATED`. Provider
visibility remains `UNKNOWN` unless explicit attribution exists. Semantic
denominators exclude invalid, rejected, incomplete, timeout, and validation
failure rows while technical status counts remain visible separately.

No production behavior, retrieval policy, budget, provider contract, public
API, persistence model, or trust boundary was changed.

## Validation

Passed:

```text
uv run pytest tests/test_story_context_runtime_loss.py -q
33 passed

uv run pytest tests/test_story_context_runtime_loss.py tests/test_evaluation_harness.py -q
77 passed

python -m evaluations.story_context_runtime_loss \
  evaluations/scenarios/story-0220-runtime-loss-v1/captures.jsonl \
  --expected-revision abcdef1 \
  --context-digest aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  --projection-digest bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
  --ledger-digest cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc \
  --expected-project devlog-ai --expected-project-id project-devlog-ai \
  --expected-source-id source-main --expected-source-id source-main-and-docs \
  --expected-revision-source STORY_TARGET_COMMIT --json
completed; stable reportDigest=fc8b69526344e273af64e6f3399de4733e8f243c2b944dad5ba6a3d92c0a4ac7

./backend/mvnw -pl backend -am \
  -Dtest=ContextLossLedgerTest,StoryContextAgentCallbackFacadeTest,StoryContextAnalysisQueryServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
20 tests passed

./backend/mvnw -pl mcp-server -am \
  -Dtest=StoryContextAnalysisToolTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
7 tests passed
```

`python3 -m compileall -q ai-engine/evaluations ai-engine/tests` also passed.

The first backend command was initially run from the module directory and
failed because the reactor selector must be invoked from the repository root.
The corrected command above passed.

## Acceptance Criteria Status

- Revision-pinned capture identity: implemented and tested.
- Existing loss ledger correlation: reused as an input; no replacement ledger.
- Distinct terminal failure classes: implemented and covered by fixtures.
- Provider-incomplete is terminal: enforced as `NOT_EVALUATED`.
- Deterministic replay and stable digest: implemented and verified.
- Correct semantic denominator handling: implemented and verified.
- Dominant bottleneck report: documented in the decision report.
- Sensitive diagnostics excluded: bounded contract contains no credentials,
  prompts, repository payloads, or raw incomplete output.
- Production behavior unchanged: backend and MCP regression tests pass.

## Known Limitations

- No live provider capture was available. Provider-facing quality is therefore
  not measured and remains `NOT_EVALUATED`/`UNKNOWN` in the deterministic
  baseline.
- The deterministic fixture cannot justify a production corrective Story by
  itself.

## Readiness

READY_FOR_HUMAN_REVIEW
