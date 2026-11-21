# Implementation Plan - Story 0222

## Status

PLAN APPROVED - implementation completed; human acceptance required.

The Story definition, Repository Analysis, and this plan were approved before
implementation. The resulting change remains evaluation-only and does not
modify production contracts, prompts, budgets, retrieval, commits, or pushes.

## Overview

This plan adds a bounded evaluation projection for abstention correctness and
ordinal confidence reporting. It reuses the existing Story 0158 specificity
evaluator, Story 0210 acceptability classifier, ABC normalization contract, and
the runtime identity rules from Stories 0220 and 0221.

The implementation will measure whether semantically eligible responses abstain
when required and whether their declared `HIGH`, `MEDIUM`, or `LOW` confidence
corresponds to reviewed grounded correctness. It will not infer quality from
confidence alone, convert confidence to a numeric score, or change the behavior
that produces responses.

## Fixed Policy

The following policies are fixed for this plan:

- Technical failures, provider-incomplete results, invalid captures, missing
  identity, and incomplete evaluation metadata are `NOT_EVALUATED`.
- Abstention correctness is determined by explicit scenario oracles, not by
  empty answer text or citation count.
- A response cannot pass grounding or acceptability because it declares high
  confidence.
- Confidence is reported by ordinal level using conditional correctness and
  evidence-support rates for semantically eligible observations.
- No numeric mapping, Brier score, ECE, confidence threshold, or acceptance
  promotion is introduced.
- Story 0158 specificity evaluation remains the source of truth for specificity
  regression gates.
- Provider-free replay is the required baseline. Live captures remain optional
  and `NOT_EVALUATED` unless identity, configuration, and oracle requirements
  are satisfied.

## Planned Changes

### 1. Define the evaluation contract

Add a focused evaluation-only model for one observation and aggregate results.
The observation projection should preserve:

- scenario identity and version;
- repository revision and capture digest;
- technical status and semantic eligibility;
- acceptability status;
- expected and actual abstention;
- abstention classification;
- confidence level;
- grounded correctness/support status;
- evidence coverage and grounding outcome;
- bounded diagnostics.

The evaluator should emit explicit abstention outcomes:

- `CORRECT_ABSTENTION`;
- `MISSED_ABSTENTION`;
- `UNNECESSARY_ABSTENTION`;
- `ABSTENTION_NOT_EVALUATED`.

It should preserve `NOT_EVALUATED` for observations that cannot be assessed
semantically.

### 2. Reuse normalized metadata

Extend the existing normalization path only if the current normalized result
does not expose the explicit confidence value required by the evaluator.

The implementation must:

- copy explicit confidence without interpreting its meaning;
- copy explicit abstention without inferring it from prose;
- reuse the existing acceptability classification;
- preserve raw and derived identities and hashes;
- fail closed when confidence, abstention, revision, capture, or semantic
  eligibility metadata is absent or inconsistent.

No prose parser, citation-count heuristic, or model-confidence inference may be
added.

### 3. Define provider-free scenarios and oracles

Create a versioned manifest using the repository's existing evaluation fixture
conventions. It should reuse the Story 0158 scenarios and add only the cases
needed to cover the missing dimensions:

- supported targeted answer;
- supported broad answer with a limitation;
- insufficient evidence requiring abstention;
- unsupported causal relationship requiring `NOT_ESTABLISHED`;
- truncated context with an uncovered principal conclusion;
- stale or unknown current-state claim;
- contradictory evidence;
- technically incomplete or provider-incomplete capture.

Each oracle must define:

- expected answer class;
- expected abstention;
- expected uncertainty or `NOT_ESTABLISHED` behavior;
- required evidence references;
- forbidden claims;
- semantic eligibility;
- confidence review status;
- repository revision and manifest digest.

Proposed oracles must remain separate from reviewed oracles. The evaluator may
run against proposed fixtures for development, but reports must identify them as
unreviewed and must not make official quality claims.

### 4. Calculate bounded aggregate reports

Produce per-observation and aggregate results containing:

- counts and rates for each abstention outcome;
- semantic denominator and `NOT_EVALUATED` count;
- conditional correctness/support by `HIGH`, `MEDIUM`, and `LOW`;
- overconfidence and underconfidence diagnostics based on reviewed oracle
  outcomes;
- specificity regression status from the existing Story 0158 evaluator;
- capture, revision, and manifest identity;
- bounded warnings and missing-data diagnostics.

The report must not collapse technical failure into semantic failure and must
not produce an overall quality claim when the oracle is not reviewed.

### 5. Preserve existing boundaries

Add regression tests proving that the Story 0222 evaluator does not change:

- Story 0210 acceptability precedence;
- Story 0158 specificity semantics;
- grounding and evidence-reference validation;
- `NOT_ESTABLISHED` causal validation;
- revision and capture-digest checks;
- `NOT_EVALUATED` denominator behavior.

No Java Core, shared production contract, MCP, REST, prompt, retrieval, budget,
retry, persistence, or provider-policy file should be modified.

## Files to Create

The expected implementation surface is:

- `ai-engine/evaluations/story_context_abstention_confidence.py`: focused
  evaluation models, classification, aggregation, and bounded report output.
- `ai-engine/evaluations/scenarios/story-0222-abstention-confidence-v1/manifest.json`:
  versioned scenario and oracle metadata.
- `ai-engine/tests/test_story_context_abstention_confidence.py`: unit and
  integration-boundary tests for the new evaluator.

Additional fixture files may be created only if they follow an existing
provider-free fixture convention and are necessary to keep the manifest
replayable.

## Files to Modify

Modify only when required by the existing evaluation structure:

- `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py`: expose
  explicit confidence in the normalized evaluation projection if it is not
  already available.
- Existing evaluation report or test registration files: only to wire the new
  evaluator without changing existing evaluator behavior.
- Story 0222 documentation: update evidence and status only after implementation
  and validation; do not mark human acceptance automatically.

## Files Not to Modify

- Java Core production contracts, entities, services, and migrations.
- MCP and REST adapters.
- Story Context generation prompts and schemas.
- Retrieval, ranking, projection, budget, retry, and provider policy code.
- Existing Story 0158 evaluator semantics.
- Existing Story 0210 acceptability precedence.

## Dependencies and Ordering

1. Confirm that the approved Story and Repository Analysis remain unchanged.
2. Implement the bounded observation contract and abstention classification.
3. Reuse or minimally extend normalization for explicit confidence.
4. Add provider-free manifest and proposed oracle fixtures.
5. Add focused deterministic tests.
6. Run Story 0158 and Story 0210 regression tests.
7. Run replay and digest-stability checks.
8. Produce a structured implementation report with reviewed versus unreviewed
   oracle status.
9. Stop for human Story acceptance.

## Test Plan

### New Tests

- Correct abstention when the oracle requires abstention.
- Missed abstention for an unsupported affirmative answer.
- Unnecessary abstention for a supported answer.
- `ABSTENTION_NOT_EVALUATED` for technical or capture failures.
- Unsupported high confidence cannot pass grounding or acceptability.
- Semantically eligible confidence observations are grouped by ordinal level.
- `NOT_EVALUATED` observations are excluded from confidence denominators.
- Missing or inconsistent identity fails closed.
- Revision and capture-digest mismatches fail closed.
- Proposed and reviewed oracle states remain distinguishable.
- Reports are bounded and contain no secrets, full prompts, repository payloads,
  or raw incomplete provider output.

### Regression Tests

```bash
cd ai-engine && python -m pytest tests/test_story_context_specificity.py -q
cd ai-engine && python -m pytest tests/test_acceptability.py tests/test_devlog_ai_abc_normalization.py -q
cd ai-engine && python -m pytest tests/test_story_context_response_quality.py -q
```

### New Focused Tests

```bash
cd ai-engine && python -m pytest tests/test_story_context_abstention_confidence.py -q
```

### Static and Replay Validation

```bash
cd ai-engine && python -m compileall -q evaluations tests
cd ai-engine && python -m pytest evaluations/test_story_context_runtime_readiness.py evaluations/test_story_context_policy_comparison.py -q
git diff --check
```

The full AI Engine suite and live provider executions are optional additional
checks. If unavailable or incomplete, they must be reported as such and must
not be counted as passed acceptance evidence.

## Risks

1. **Oracle subjectivity:** keep proposed and reviewed oracle artifacts
   separate, and record independent review status.
2. **Confidence misuse:** calculate confidence metrics only after semantic
   eligibility and grounding checks.
3. **False semantic failures:** preserve `NOT_EVALUATED` for technical and
   provider-incomplete outcomes.
4. **Specificity duplication:** reuse Story 0158 rather than reproducing its
   gates.
5. **Scope expansion:** do not respond to evaluation findings with prompt,
   retrieval, budget, or contract changes in this Story.
6. **Dirty worktree:** preserve all unrelated tracked and untracked changes.

## Acceptance Evidence Required

Before Story acceptance, the implementation report must include:

- files changed and bounded scope confirmation;
- test commands and results;
- deterministic replay and digest results;
- abstention outcome counts;
- confidence-level conditional reports;
- semantic and `NOT_EVALUATED` denominators;
- oracle review status;
- confirmation that production behavior and contracts were unchanged;
- unresolved limitations and any follow-up Story recommendation.

## Implementation Authorization

Explicit implementation authorization was granted before code and fixture
implementation began.

## Readiness

IMPLEMENTATION_COMPLETED - HUMAN_ACCEPTANCE_REQUIRED
