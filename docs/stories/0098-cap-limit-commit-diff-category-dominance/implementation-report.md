# Story 0098 — Implementation Report

## Status

**IMPLEMENTATION_COMPLETED_AWAITING_HUMAN_REVIEW**

## Human Approval

- `HUMAN_IMPLEMENTATION_REVIEW = APPROVED`
- `history-v1` ceiling-only policy extension = HUMAN APPROVED

## RED

Targeted selector regression tests were added before production changes.

Observed failures:

- `ceilingEnforcementPreventsCategoryDominance`
  - `COMMIT_DIFF should be capped at 20% of 60 = 12, was 48`
- `strongRelevanceCannotBypassCategoryCeiling`
  - `Strong relevance COMMIT_DIFF (score >= 75) should not bypass ceiling, was 30`

These failures proved the current defect under realistic conditions:

- global budget = 60
- abundant COMMIT_DIFF candidates
- strong relevance >= 75
- other eligible categories present

## Implementation

Production changes:

1. `EvidencePrecisionPolicy`
   - Added `maximumCategorySharePercentage`
   - Default-compatible unrestricted value = `100`
   - Composed policy now takes the minimum category ceiling across active profiles

2. `BudgetedDiverseEvidenceSelector`
   - Computes `maximumCategoryItems = ceil(budget * maximumCategorySharePercentage / 100.0)`
   - Enforces hard cap in ordinary selection
   - Enforces the same cap during knowledge-floor selection

3. `DeterministicContextIntelligence`
   - `engineering-story-precision` now uses `20%`
   - Added a ceiling-only `history-v1` precision policy so generic Analysis repository-context selection also respects the approved cap

## GREEN

Targeted Story 0098 results:

- `BudgetedDiverseEvidenceSelectorTest`: PASS
- `DeterministicContextIntelligenceTest`: PASS
- `KnowledgeFloorSelectionTest`: PASS
- `RepositoryContextEngineTest`: PASS
- `RepositoryContextServiceTest`: PASS
- `Story0097CommitDiffReconnectionTest`: PASS
- `DeterministicEvidenceRankerTest`: PASS

Key GREEN assertions:

- COMMIT_DIFF selected `<= 12`
- strong relevance cannot bypass the ceiling
- knowledge floor still reserves relevant knowledge
- full 60-item budget is still filled when enough kinds exist
- sparse-kind scenarios remain capped deterministically

## Benchmark Before/After

### Before

| Intent | Candidate Count | Selected Count | COMMIT_DIFF | GIT_HISTORY | Other |
|---|---:|---:|---:|---:|---:|
| `describe-project-v1` | 269 | 60 | 44 (73.3%) | 9 (15.0%) | 7 (11.7%) |
| `architecture-overview-v1` | 262 | 60 | 45 (75.0%) | 8 (13.3%) | 7 (11.7%) |
| `analyze-engineering-decision-v1` | 264 | 60 | 45 (75.0%) | 8 (13.3%) | 7 (11.7%) |

### After

| Intent | Candidate Count | Selected Count | COMMIT_DIFF | GIT_HISTORY | Other |
|---|---:|---:|---:|---:|---:|
| `describe-project-v1` | 270 | 60 | 12 (20.0%) | 12 (20.0%) | 36 (60.0%) |
| `architecture-overview-v1` | 263 | 60 | 12 (20.0%) | 12 (20.0%) | 36 (60.0%) |
| `analyze-engineering-decision-v1` | 265 | 60 | 12 (20.0%) | 12 (20.0%) | 36 (60.0%) |

## Qualitative Comparison

Representative persisted Analysis: `describe-project-v1`

- Before (`8a06fd62-785b-4172-9ce8-2afbe1e2beed`): repository evidence was mostly changed files (`37/60`) with limited prior context.
- After (`45af5ee5-f5fc-49a3-81d5-c31c0614c481`): repository evidence includes balanced changed files, commits, prior insights, previous analyses, roadmap items, and ADR evidence.

Observed improvement:

- materially broader engineering context selection
- validated insight and previous-analysis evidence now survive selection in volume
- COMMIT_DIFF no longer crowds out most other repository-context evidence

Remaining weakness:

- canonical result preview still shows the top 5 highest-ranked repository items, which remain COMMIT_DIFF-heavy even when the full selected set is balanced

## Quality Gates

- Targeted Story 0098 tests: PASS
- Affected repository-context tests: PASS
- Full backend suite: PASS (`1010` tests)
- `mvn clean verify`: PASS
- JaCoCo coverage check: PASS
- `git diff --check`: PASS

## Implementation Deviation

One deviation from the original plan was required:

- The original plan only attached the `20%` ceiling to `engineering-story-precision`.
- To improve actual generic Analysis repository-context selection, the same approved ceiling was also attached to `history-v1` through a ceiling-only policy.

No prompt, scoring-model, frontend, intent, or AI-driven budgeting changes were introduced.
