# Story 0134 - Implementation Report

## Repository State

```text
Branch = main
Story = 0134
Human acceptance = PENDING
Commit = NO
Push = NO
Merge = NO
```

## Governance

- Governing protocol: `docs/evaluation/comparative-baseline-collection-runtime-design.md`
- Frozen predecessor: Story0133 and Comparative Baseline Protocol V1.
- Scope: evaluation-only offline runtime; no provider or collection authority.

## Implementation Summary

Implemented an offline-testable runtime for the frozen 18-assignment baseline.
The runtime provides isolated DEVLOG and AGENT_DIRECT input paths, a strict
common answer contract, bounded pinned repository tools, raw capture before
validation, explicit retry/budget accounting, failure continuation, immutable
observation artifacts, offline replay, and secret persistence guards.

The implementation preserves the V1 design choice that AGENT_DIRECT receives
the question-specific DEVLOG visible-context byte budget. The choice is
documented as an experimental assumption and no sensitivity analysis is run.

## Files Changed

- `ai-engine/evaluations/comparative_baseline/collection_runtime.py`: runtime
  contracts, condition runners, budgets, state machine, persistence, and replay.
- `ai-engine/evaluations/comparative_baseline/testing.py`: offline scripted
  provider fixtures.
- `ai-engine/evaluations/comparative_baseline/__init__.py`: package boundary.
- `ai-engine/tests/test_comparative_collection_runtime.py`: Story0134 offline
  runtime tests.
- `ai-engine/tests/test_story0134_human_runtime_review.py`: complete offline
  18-assignment human runtime review.
- `docs/stories/0134-comparative-baseline-collection-runtime/story.md`: Story
  lifecycle, scope, decisions, and acceptance criteria.
- `docs/stories/0134-comparative-baseline-collection-runtime/implementation-report.md`:
  this report.
- `docs/stories/0134-comparative-baseline-collection-runtime/human-runtime-review.md`:
  dry-run review report.

## Runtime Decisions Preserved

- Common final model boundary and evaluator-facing answer contract.
- DEVLOG repository isolation.
- AGENT_DIRECT autonomy with only typed read-only tools.
- Repository revision `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.
- Six tool operations, eight model turns, question-specific byte budgets.
- 1,800 final and 512 intermediate output envelopes.
- Eight logical AGENT_DIRECT turns plus one pre-response technical retry ceiling.
- Technical retry only; no semantic retry or answer selection.
- Slot-local failure continuation and clean repetition/condition isolation.
- Write-once raw artifacts and partial offline replay.
- No Protocol V1, Story0133, production, RAG, ML, or Data Science changes.

## Verification

```text
Provider calls = 0
Network calls = 0
Data collection = 0
Experimental observations created = 0
Production code changed = 0
Protocol changes = 0
```

Focused tests are run with the command below. Tests use only scripted offline
provider responses and in-memory repository tools.

## Commands Executed

```bash
cd ai-engine && python3 -m pytest tests/test_comparative_collection_runtime.py tests/test_comparative_baseline.py -q
cd ai-engine && python3 -m pytest -q
python3 -m compileall -q ai-engine/evaluations/comparative_baseline ai-engine/tests/test_comparative_collection_runtime.py ai-engine/tests/test_comparative_baseline.py
git diff --check
```

## Test Results

```text
Focused Story0134/0133 tests = 32 passed
Full AI Engine suite = 326 passed
Python compilation = PASS
Text hygiene/secret persistence checks = PASS
git diff --check = PASS
```

## Known Limitations

- Real provider transport and real repository checkout integration are not
  implemented or authorized.
- Java Core grounding is an injected evaluation authority; no live bridge call
  occurs.
- Offline replay remains `PARTIAL` for provider sampling, hidden reasoning,
  unavailable usage, network latency, and absent raw responses.
- No observations, analytical dataset, statistics, or sensitivity analysis are
  produced.

## Readiness

```text
STORY_0134_IMPLEMENTATION = COMPLETE
HUMAN_ACCEPTANCE = PENDING
READY_FOR_HUMAN_REVIEW
```
