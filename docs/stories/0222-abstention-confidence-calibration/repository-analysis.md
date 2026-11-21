# Repository Analysis - Story 0222

## Status

ANALYSIS APPROVED - implementation completed; human acceptance required.

Story 0222 has been approved as a design. This analysis does not authorize
implementation, production changes, prompt changes, budget changes, retrieval
changes, commits, or pushes.

## Repository State

- Branch observed during analysis: `story/0210-agent-response-acceptability-contract`.
- Worktree: modified with unrelated tracked and untracked changes.
- Existing changes were not reverted, staged, or modified.
- Story 0222 definition: `story.md`.
- DevLog preflight: attempted, but unavailable because the service returned
  `401 Unauthorized`; this analysis is therefore grounded in repository
  artifacts and current source inspection.

## Governing Context

The epic orders the work as contract and baseline, freshness and scope,
runtime loss evaluation, bounded live capture, then specificity, abstention and
confidence calibration. Story 0222 is the next evaluation slice and must not
reopen the earlier architectural decisions.

Relevant repository evidence:

- Story 0158 defines specificity, correct abstention for insufficient evidence,
  next-step utility, and comparison with a direct MCP baseline.
- `ai-engine/evaluations/story_context_specificity.py` already implements a
  provider-free specificity evaluator with deterministic evidence, scope,
  trust, abstention, next-step, repetition, and capture-digest gates.
- Story 0210 defines evaluation-only acceptability states and the
  `NOT_EVALUATED` denominator rule.
- `ai-engine/evaluations/acceptability.py` consumes explicit metadata and does
  not infer quality from confidence, prose, or citation counts.
- `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py` already
  extracts explicit abstention and passes normalized metadata to acceptability
  classification.
- `ai-engine/app/schemas/story_context_analysis.py` exposes ordinal confidence
  and `NOT_ESTABLISHED` causal semantics. Existing validation prevents
  confidence from promoting an unestablished causal claim.
- The benchmark specification requires claim-level oracles, expected
  uncertainty, correct abstention, technical exclusion, and human review before
  quality claims are made.
- Stories 0220 and 0221 establish bounded runtime identity, loss accounting,
  provider outcome handling, and live-capture exclusion rules.

## Current Implementation Surfaces

### Existing Specificity Evaluation

The Story 0158 evaluator provides:

- four scenarios: broad, targeted, insufficient evidence, and truncated
  context;
- three repetitions per condition;
- Story Agent versus MCP baseline comparison;
- deterministic checks for unknown evidence references, fabricated relations,
  scope violations, trust violations, and question repetition;
- explicit `abstentionCorrect` and `nextStepGrounded` assessment fields;
- stable runtime capture and capture-digest validation.

This is reusable regression infrastructure. Story 0222 must not create a second
specificity protocol or reinterpret its existing gates.

### Existing Acceptability Evaluation

The acceptability classifier evaluates explicit normalized fields in a fail-closed
order. Technical failures, semantically ineligible results, and incomplete
metadata become `NOT_EVALUATED`. Grounding, evidence coverage, freshness,
truncation, contradictions, limitations, and abstention determine the
evaluation-only status.

This classifier is the semantic eligibility boundary for Story 0222. The new
evaluation must consume its result rather than duplicate or modify the
acceptability policy.

### Existing Response Contracts

The Story Context analysis schema already contains:

- `confidence` with ordinal levels `HIGH`, `MEDIUM`, and `LOW`;
- causal classification including `NOT_ESTABLISHED`;
- explicit evidence-basis validation;
- response status values including `COMPLETED`, `NOT_ESTABLISHED`, `FAILED`, and
  `TIMED_OUT`.

The Python prompt and validation rules already require `NOT_ESTABLISHED` when
causal evidence is insufficient and prohibit confidence from establishing
causality. Story 0222 should evaluate these rules, not change them.

## Gap Identified

The repository currently has the fields and isolated gates needed for the
Story, but lacks a single bounded evaluation that correlates them with reviewed
scenario expectations.

The missing evaluation must distinguish:

1. A correct abstention from an empty or generic answer.
2. A missed abstention from an unsupported affirmative answer.
3. An unnecessary abstention from a supported answer.
4. A high-confidence answer that fails grounding or acceptability.
5. A low-confidence answer that is nevertheless correctly grounded.
6. A technically unavailable observation from a semantically incorrect answer.

No evidence currently justifies changing retrieval, prompt instructions,
confidence semantics, or budgets. Any such change would exceed this Story and
would require a separate measured decision.

## Proposed Evaluation Boundary

The implementation should add a focused evaluation-only projection that joins:

- scenario oracle expectations;
- normalized abstention and confidence;
- Story 0210 acceptability status;
- grounding and evidence coverage;
- revision, freshness, truncation, and loss metadata;
- runtime capture identity and digest;
- Story 0158 specificity results.

The projection should emit bounded per-observation and aggregate results. It
should preserve the distinction between:

- `CORRECT_ABSTENTION`;
- `MISSED_ABSTENTION`;
- `UNNECESSARY_ABSTENTION`;
- `ABSTENTION_NOT_EVALUATED`;
- confidence-level observations that are semantically eligible;
- `NOT_EVALUATED` technical or capture outcomes.

Confidence should be reported using conditional correctness/support rates per
ordinal level. The analysis does not support introducing a numeric calibration
mapping, Brier score, ECE, or a confidence threshold that changes acceptance.

## Expected Files

The exact file list belongs in the Implementation Plan, but the repository
analysis supports the following bounded change surface:

### Likely Files to Create

- A focused evaluator under `ai-engine/evaluations/`, with a name such as
  `story_context_abstention_confidence.py`.
- A versioned provider-free scenario manifest and oracle fixture using existing
  evaluation conventions.
- Focused tests under `ai-engine/tests/`.

### Likely Files to Modify

- Existing evaluation normalization or reporting code only where the explicit
  normalized confidence field is currently omitted from the evaluation surface.
- Existing evaluation test registration or report wiring if required by the
  current harness.

### Files Not Expected to Change

- Java Core production contracts and persistence.
- MCP or REST transport adapters.
- Story Context generation prompts.
- Retrieval, ranking, projection, budget, retry, or provider policy code.
- Existing Story 0158 evaluator semantics.
- Existing Story 0210 acceptability precedence.

## Scenario and Oracle Analysis

The minimum scenario set should reuse the existing Story 0158 cases and add
only the cases required to exercise the missing dimensions:

- supported targeted answer with confidence;
- supported broad answer with bounded limitations;
- insufficient evidence requiring abstention;
- unsupported causal relationship requiring `NOT_ESTABLISHED`;
- truncated context with uncovered principal conclusion;
- stale or unknown current-state claim;
- contradiction requiring bounded or insufficient treatment;
- technically incomplete, invalid, or provider-incomplete capture.

Each semantically evaluated case should define:

- expected answer class;
- expected abstention;
- expected uncertainty or `NOT_ESTABLISHED` behavior;
- required evidence references;
- forbidden unsupported claims;
- confidence review requirements;
- repository revision and manifest identity.

Technical failures and unavailable live captures must not receive semantic
quality labels. They remain `NOT_EVALUATED`.

## Dependencies

- Story 0158 specificity evaluator and manifest.
- Story 0210 acceptability classifier and provider-free baseline.
- Story 0220 runtime loss and terminal-outcome metadata.
- Story 0221 bounded live-capture identity, if live captures are inspected.
- Existing ABC normalization and benchmark oracle conventions.
- Human review of oracles and thresholds before quality claims.

Story 0210, 0220, and 0221 are not human-accepted according to their current
repository artifacts. Their implementation outputs may be reused as technical
inputs, but they must not be described as accepted product behavior.

## Risks and Mitigations

### Oracle Subjectivity

Confidence calibration and semantic abstention are not fully deterministic
properties. Keep proposed oracles separate from reviewed oracles and report
inter-rater disagreement where qualitative review is used.

### Confidence Misuse

A confidence value can be mistaken for evidence. Keep confidence reporting
downstream of grounding and acceptability checks and prohibit it from changing
classification.

### False Semantic Failures

Provider timeout, incomplete output, invalid capture, and missing identity could
be counted as poor answers. Preserve the Story 0210 and Story 0220
`NOT_EVALUATED` rules before calculating any semantic metric.

### Duplicate Specificity Logic

Reimplementing Story 0158 gates would create divergent results. Reuse its
evaluator and treat specificity as regression coverage.

### Premature Production Changes

Observed calibration problems could invite prompt or contract changes. This
Story must only produce evidence; any corrective behavior belongs to a later
approved Story.

### Dirty Worktree

The current worktree contains unrelated changes. Only the Story 0222 analysis
and later explicitly authorized artifacts may be touched.

## Validation Implications

The Implementation Plan should require, at minimum:

```bash
cd ai-engine && python -m pytest tests/test_story_context_specificity.py -q
cd ai-engine && python -m pytest tests/test_acceptability.py tests/test_devlog_ai_abc_normalization.py -q
cd ai-engine && python -m compileall -q evaluations tests
git diff --check
```

New focused tests must verify:

- correct, missed, unnecessary, and non-evaluable abstention;
- unsupported high confidence cannot pass acceptability;
- confidence reports exclude `NOT_EVALUATED` observations;
- ordinal confidence is preserved without numeric reinterpretation;
- Story 0158 specificity regression remains stable;
- capture identity, revision, and digest mismatches fail closed;
- reports contain no secrets, full prompts, repository payloads, or raw
  incomplete output.

The full AI Engine suite and live provider runs are not acceptance evidence for
this analysis unless executed with the required configuration and reviewed
separately.

## Decision Recorded

This Repository Analysis recommends proceeding to an Implementation Plan with
the following fixed boundary:

- evaluation-only;
- provider-free baseline first;
- ordinal confidence reporting only;
- reviewed scenario oracles required for semantic claims;
- existing Story 0158 and Story 0210 evaluators reused;
- no production, prompt, retrieval, budget, or architecture changes.

Human approval was granted before creating the Implementation Plan. The next
gate is approval of the Implementation Plan.

## Readiness

IMPLEMENTATION_COMPLETED - HUMAN_ACCEPTANCE_REQUIRED
