# Implementation Report - Story 0180

## Repository State

- Branch: `main`
- Base revision: `2a92d65b42e41819b4e3bd915cf486d2f1bc8b83`
- Worktree: Story 0180 files are uncommitted; no unrelated files changed.
- Commit/push: not performed.

## Governance

- Governing ADR: ADR-079.
- Related Stories: 0178 and 0179.
- Human approvals: Story scope, Repository Analysis, and Implementation Plan
  approved before implementation.

## Files Changed

- `ai-engine/evaluations/story_context_budget_live.py`: required target revision,
  readiness preflight, task/repetition identity tracking, bounded readiness
  envelope, stop-on-first-readiness-failure behavior, and scenario/repetition
  selectors for bounded MCP campaign runs.
- `ai-engine/evaluations/story_context_runtime_report.py`: readiness
  classification and separate non-evaluable campaign output.
- `ai-engine/evaluations/test_story_context_runtime_readiness.py`: deterministic
  readiness, duplicate identity, mismatch, and valid-capture tests.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/story.md`:
  canonical Story scope.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/repository-analysis.md`:
  repository analysis.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/implementation-plan.md`:
  approved implementation plan.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/evaluation-report.md`:
  current live readiness result.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/decision-report.md`:
  decision that no product-policy conclusion is justified.

## Implementation Summary

The evaluation runner now requires an explicit target revision, correlates task
and scenario/repetition identities, validates snapshot completeness and budget
coherence, and writes a bounded `READY` or `NOT_READY` envelope. By default it
stops after the first readiness failure and returns exit code `2`.

The report helper now emits a bounded readiness report for non-evaluable input
instead of failing on duplicate task IDs before explaining the runtime problem.
Valid campaigns still use the existing strict capture and aggregate validation.

The live runner also adds a bounded, explicitly labeled repetition marker to each
evaluation question. This is an evaluation-only workaround for the observed Core
fallback from distinct idempotency keys to the same logical submission digest; no
Core idempotency behavior was changed.

## Validation

### Commands Executed

```bash
python3 -m py_compile ai-engine/evaluations/story_context_budget_live.py ai-engine/evaluations/story_context_runtime_report.py ai-engine/evaluations/test_story_context_runtime_readiness.py
PYTHONPATH=ai-engine/evaluations python3 -m unittest -v ai-engine/evaluations/test_story_context_runtime_readiness.py
python3 ai-engine/evaluations/story_context_runtime_report.py /tmp/opencode/story0180-runtime-c0.jsonl --revision 2a92d65b42e41819b4e3bd915cf486d2f1bc8b83 --output /tmp/opencode/story0180-runtime-readiness-report.json
python3 ai-engine/evaluations/story_context_budget_live.py C0-current --project devlog-ai --output /tmp/opencode/story0180-readiness-live.jsonl --repetitions 1 --target-revision 2a92d65b42e41819b4e3bd915cf486d2f1bc8b83
git diff --check
```

### Results

- Python compilation: passed.
- Deterministic tests: 10 passed.
- Existing invalid 12-row campaign: classified as non-evaluable with bounded
  failure reasons instead of raising on duplicate task IDs.
- Live readiness smoke: stopped after the first non-ready capture and returned
  exit code `2` as designed.
- B6000 runtime campaign: 12/12 captures readiness-valid across 4 scenarios and
  3 repetitions; 11 completed outcomes and 1 bounded timeout observation.
- Live failure reasons included target revision mismatch, missing loss ledger,
  missing provider outcome, context budget mismatch, and missing completed
  analysis.
- Python `pytest`: not run; unavailable in the environment.

The readiness/report consistency review also required strict malformed-input
handling, target-revision format validation, bounded diagnostic rows, and
complete capture-contract validation before a campaign can be marked evaluable.

Independent code review: passed with no material findings.

## Architecture Preserved

- Java/Core remains the authority for context, revision, selection, budgets,
  grounding, and provider authorization.
- No backend, MCP protocol, database, provider, retrieval, or production policy
  code changed.
- No raw prompts, provider payloads, credentials, tokens, or repository payloads
  were added to committed artifacts.
- Provider-visible reference attribution remains `UNKNOWN` when unavailable.

## Documentation Reconciliation

Documentation update: Story-local documentation was added as required by the
approved plan. General operator, REST, MCP, deployment, and provider
documentation were not changed because this evaluation procedure is not yet a
stable supported operational contract.

## Vault Outcome

The vault was not consulted. Repository Stories, ADR-079, current implementation,
and direct runtime evidence were sufficient. No vault action is proposed.

## Known Limitations

- The campaign measures the observed `B6000` runtime and does not validate the
  stale `C0-current` declaration of `32000`.
- One valid capture has provider status `AUTHORIZED_NOT_YET_MEASURED` after a
  runtime timeout; it remains distinguished from `COMPLETED` in analysis.
- The Story does not change Core submission idempotency semantics.

## Readiness

`READY_FOR_HUMAN_REVIEW - B6000 CAMPAIGN READINESS VALIDATED`
