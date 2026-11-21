# Story 0221 - Implementation Report

## Repository State

- Branch: `story/0210-agent-response-acceptability-contract`
- Worktree: modified before this implementation; unrelated existing changes were preserved.
- DevLog preflight: attempted for `devlog-ai`; failed with `PROMPT_CONSTRUCTION_FAILED` after a
  partially fresh, bounded context response. Current repository evidence remains authoritative.

## Governance

- Governing ADRs: ADR-006, ADR-063, ADR-066, ADR-067, ADR-069, ADR-079.
- Authorized Story: Story 0221 - Bounded Live Provider Captures and Attribution.
- Human decisions: recorded in `decision-report.md`.

## Files Changed

- `ai-engine/evaluations/story_context_runtime_loss.py`: additive live-provider projection,
  validation, evaluation and aggregation functions; Story 0220 baseline remains unchanged.
- `ai-engine/tests/test_story_context_live_captures.py`: provider-free live projection, terminal,
  attribution, identity, redaction and denominator tests.
- `ai-engine/evaluations/scenarios/story-0221-live-provider-captures-v1/manifest.json`: versioned
  live-capture contract and approved retention/usage metadata.
- `ai-engine/evaluations/scenarios/story-0221-live-provider-captures-v1/captures.jsonl`: bounded
  complete and incomplete provider-free representations.
- `docs/stories/0221-bounded-live-provider-captures/decision-report.md`: approved decisions.

## Implementation Summary

The evaluation boundary now projects an existing Core snapshot and the actual `AiInteractionTrace`
shape into an explicit `LIVE_PROVIDER` capture. The adapter maps `modelIdentifier`, flat token
measurements, `durationMs`, provider status and trace identifiers; Core task/correlation binding is
explicit because the trace DTO does not carry task identity. Provider visibility becomes `MEASURED`
only when provider, model and the explicit binding are present. Provider-incomplete, timeout,
transport and callback-validation outcomes remain distinct and excluded from semantic denominators;
absent usage and latency remain unknown.

The projection uses an allowlist and rejects sensitive values in allowed metadata fields; it never
copies prompts, raw output, repository payloads or credentials. The Core loss ledger is copied
without mutation, so its digest and contents remain authoritative. The existing Story 0220 capture
version, fixtures and report behavior were not modified.

The adapter also handles the real trace terminal vocabulary: `validationStatus=SUCCEEDED` with a
missing `providerStatus` is a completed provider attempt, while `PARSING_FAILED` and
`VALIDATION_FAILED` remain callback validation failures rather than transport failures.

## Documentation Reconciliation

Documentation update: required. The approved decisions and evaluation contract are recorded in
`decision-report.md`; no public API, runtime configuration or retention configuration changed.

## Vault Outcome

- Vault consulted: no. Repository Story, ADRs and current implementation provided sufficient
  project context.
- Outcome: no vault action.
- Rationale: this Story changes an evaluation artifact contract and does not establish transverse
  knowledge requiring curation.

## Validation

Executed:

```bash
uv run pytest ai-engine/tests/test_story_context_runtime_loss.py ai-engine/tests/test_story_context_live_captures.py -q
python3 -m compileall -q ai-engine/evaluations ai-engine/tests
```

Results:

- Focused Story 0220/0221 and evaluation-harness tests passed.
- Python compilation passed.
- Diff whitespace validation passed.

Not executed: backend and MCP suites; this implementation is confined to the AI Engine evaluation
boundary and does not change Java, MCP, REST, persistence or provider runtime code.

## Known Limitations

- No real provider capture was available; live provider execution remains optional and unmeasured.
- The live evaluator is an evaluation adapter, not a production trace persistence path.
- Retention reuses the approved existing ceiling as metadata policy; it does not alter diagnostic
  retention configuration.

## Readiness

READY_FOR_HUMAN_REVIEW
