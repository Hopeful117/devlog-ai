# Implementation Plan - Story 0210

## Overview

This plan adds an evaluation-only acceptability contract and a reproducible
baseline for Story Context responses. It reuses the existing response-quality
normalization, grounding checks and benchmark artifacts rather than changing
the production response envelope, prompt budgets, retrieval pipeline or Core /
Python authority boundary.

The planned contract evaluates a completed observation only after technical
eligibility and grounding checks. It emits one of the Story states
`ACCEPTABLE`, `BOUNDED`, `INSUFFICIENT`, `STALE_OR_UNKNOWN` or `FAILED`, while
preserving `NOT_EVALUATED` for executions that cannot be assessed semantically.

Recommended policy choices for approval:

- Keep acceptability evaluation-only in this Story; do not add a production DTO
  field or persistence column.
- Treat unknown freshness as `STALE_OR_UNKNOWN` for current-state claims. A
  revision-pinned historical question may still be `BOUNDED` when its evidence
  and limitations are explicit.
- Require human-reviewed benchmark oracles and thresholds before reporting
  quality claims. Deterministic CI validates classification and evidence rules,
  not subjective answer quality.
- Treat the accepted 0209 provider-free implementation as an input baseline;
  live and qualitative results remain `NOT_EVALUATED` unless separately run and
  reviewed.

## Planned Changes

### 1. Freeze the evaluation contract

Add a small evaluation-only contract model and decision function in the AI
Engine evaluation package. It will consume existing normalized observation
fields: technical status, semantic eligibility, grounding result, evidence
coverage, freshness/revision state, truncation/loss diagnostics, abstention,
contradictions and next-step metadata.

The decision function will apply an explicit precedence order:

1. technically invalid, incomplete or absent result -> `NOT_EVALUATED`;
2. completed but no evaluable evidence -> `INSUFFICIENT`;
3. stale or unknown currentness -> `STALE_OR_UNKNOWN`;
4. known limitation requiring targeted verification -> `BOUNDED`;
5. sufficient evidence and coverage with no blocking limitation ->
   `ACCEPTABLE`.

`FAILED` remains reserved for a completed evaluation operation that cannot
produce a semantically evaluable decision, not for provider failures that must
be excluded as `NOT_EVALUATED`.

### 2. Reuse and extend normalization

Extend the existing ABC normalization path so the acceptability evaluator can
consume explicit fields without inferring semantics from prose or keywords.
Preserve raw and derived hashes, repository revision, technical status,
semantic eligibility, explicit references, limitations and abstention.

If a required field is absent or inconsistent, emit a bounded diagnostic and
`NOT_EVALUATED` rather than guessing a state.

### 3. Define the baseline scenarios

Create or update provider-free fixtures for the required Story 0210 cases:

- complete, grounded response;
- targeted response with a known verification limitation;
- insufficient evidence with correct abstention;
- unknown freshness/current-state claim;
- deliberately truncated context with uncovered principal conclusion;
- contradiction or historical question;
- provider-incomplete or invalid capture;
- structurally valid but semantically incomplete response.

Each observation will include the execution status, grounding status,
acceptability result, expected evidence, covered evidence, revision/freshness,
truncation, next step and `NOT_EVALUATED` reason where applicable.

### 4. Reconcile benchmark artifacts

Align the acceptability contract with the existing ABC benchmark manifest,
oracle proposals, review sheets and normalized-answer contract. Do not freeze
human-quality thresholds or present benchmark results as official until the
oracles are independently reviewed.

The benchmark will report technical completion, semantic eligibility and
acceptability separately. Missing or invalid observations will not be counted
as semantic failures.

### 5. Validate existing boundaries

Use existing Java and Python contract tests to prove that this Story does not
weaken grounding, revision, trust, provider-incomplete or callback acceptance.
No production API, database schema, MCP adapter, prompt budget or retrieval
component will be changed.

## Files to Modify

- `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py`: expose the
  explicit normalized fields needed by the acceptability evaluator, without
  prose inference.
- `ai-engine/evaluations/models.py`: add evaluation-only typed fields/enums if
  existing models cannot represent the contract without ambiguity.
- `ai-engine/evaluations/evaluator.py` or a focused evaluation module: apply
  deterministic acceptability precedence and emit bounded diagnostics.
- `ai-engine/evaluations/devlog_ai_abc_runner/`: wire the acceptability result
  into per-observation and per-case reports.
- `ai-engine/tests/test_story_context_response_quality.py`: extend deterministic
  response-quality and grounding cases with acceptability outcomes.
- `ai-engine/tests/` targeted normalization/evaluation tests: cover technical
  exclusion, abstention, freshness, truncation, contradictions and incomplete
  output.
- `docs/evaluation/devlog-ai-abc-benchmark-v1.json`: add the approved
  acceptability expectations only after oracle review.
- `docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json` and related review
  sheets: record proposed claim-level expectations and human-review status.
- `docs/stories/0210-agent-response-acceptability-contract/story.md`: update
  acceptance evidence and status only after implementation and human review.

Java Core and shared production contract files are inspection and regression
surfaces for this slice; they should not be modified unless a separate,
explicit production-contract decision is approved.

## Files to Create

- A focused evaluation module under
  `ai-engine/evaluations/devlog_ai_abc_normalization/` or
  `ai-engine/evaluations/` only if the existing evaluator cannot represent the
  contract cleanly.
- Provider-free fixture files for Story 0210 cases, using the repository's
  existing evaluation fixture conventions.

No database migration, production DTO, MCP contract, prompt contract or new
retrieval component is planned.

## Dependencies

- Story 0209 provider-free response-quality and grounding behavior.
- ADR-006, ADR-063, ADR-066, ADR-067 and ADR-079.
- Existing ABC benchmark manifests, normalization contract and oracle review
  sheets.
- Existing Python standard-library/test dependencies and repository test
  tooling; no new external dependency is required.

Ordering dependencies:

1. Confirm the recommended acceptability policy and human-review status of the
   benchmark oracles.
2. Implement the evaluation contract and normalization projection.
3. Add provider-free fixtures and deterministic tests.
4. Run replay and focused regression validation.
5. Update Story evidence and report unresolved qualitative/live limitations.

## Test Plan

### Tests to create or update

- Unit tests for each acceptability state and precedence rule.
- Tests proving provider failure, timeout, incomplete output, invalid capture
  and missing technical metadata become `NOT_EVALUATED` rather than semantic
  failures.
- Tests for insufficient evidence, correct abstention and missing next step.
- Tests for unknown freshness, deliberate truncation and uncovered principal
  conclusions.
- Tests for revision mismatch, unauthorized evidence and contradiction handling.
- Tests proving acceptability does not change existing grounding or structural
  validity outcomes.
- Deterministic ABC replay tests proving per-observation and aggregate reports
  preserve technical/semantic separation and stable digests.

### Validation commands

```bash
cd ai-engine && python -m pytest tests/test_story_context_response_quality.py tests/test_story_context_analysis_wire_contract.py -q
cd ai-engine && python -m pytest evaluations/test_story_context_runtime_readiness.py evaluations/test_story_context_policy_comparison.py -q
./backend/mvnw -pl backend -am test -Dtest=StoryContextResultValidatorTest,StoryContextCoreIntegrationTest -B
./backend/mvnw -pl mcp-server -am test -Dtest=StoryContextAnalysisToolTest -B
git diff --check
```

Expected success conditions:

- all deterministic fixtures produce the expected state;
- semantically ineligible observations remain outside semantic denominators;
- grounding, revision, trust and callback tests remain green;
- replay digests are stable;
- no live provider or credential is required;
- no unrelated MCP worktree changes are included.

## Risks

1. **Policy ambiguity for unknown freshness:** the plan proposes a conservative
   `STALE_OR_UNKNOWN` result for current-state claims; human approval must
   confirm or revise this before implementation.
2. **Oracle drift:** benchmark expectations may become claims about quality
   before independent review. Keep proposals and approved oracles distinct.
3. **Production contract leakage:** adding the status to shared DTOs would
   expand scope. Keep the first implementation in evaluation artifacts only.
4. **False semantic failures:** technical failures must remain `NOT_EVALUATED`.
   Enforce this before semantic scoring.
5. **Story 0209 governance mismatch:** do not claim the baseline is accepted
   until its human acceptance is explicitly recorded.

## Validation Checklist

- [ ] Acceptability states and precedence are represented in evaluation-only code.
- [ ] Technical validity, grounding, semantic eligibility and acceptability are
      reported separately.
- [ ] Provider-incomplete and invalid observations are `NOT_EVALUATED`.
- [ ] Insufficient evidence and correct abstention are distinguished.
- [ ] Freshness, revision and truncation limitations are visible.
- [ ] Principal conclusions without required evidence cannot be `ACCEPTABLE`.
- [ ] Contradictions and historical/current-state distinctions are covered.
- [ ] Benchmark oracles remain explicitly human-reviewed or proposed.
- [ ] No production DTO, database, prompt budget, retrieval or MCP contract is
      changed.
- [ ] Existing Java Core and Python grounding tests pass.
- [ ] Provider-free replay is deterministic and digest-stable.
- [ ] Story 0209 acceptance status is not overstated.
- [ ] Unrelated worktree modifications remain untouched.
- [ ] `git diff --check` passes.

## Recommendation

Ready for implementation

The implementation strategy is bounded, reuses existing evaluation surfaces and
does not require a new architecture or external dependency. The recommended
policy choices in this plan must be accepted as part of the human approval of
this plan before implementation begins.

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
