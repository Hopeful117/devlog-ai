# Story 0098 — Repository Analysis

## Status

**UPDATED_POST_IMPLEMENTATION**

## Revalidated Baseline

- Baseline SHA: `e4925ab`
- Benchmark endpoint: `GET /api/v1/projects/devlog-ai/engineering-context?intent=<value>`
- Benchmark path uses repository-context selection and exposed the COMMIT_DIFF dominance defect.

## Baseline Distributions

| Intent | Candidate Count | Selected Count | COMMIT_DIFF | GIT_HISTORY | Other |
|---|---:|---:|---:|---:|---:|
| `describe-project-v1` | 269 | 60 | 44 (73.3%) | 9 (15.0%) | 7 (11.7%) |
| `architecture-overview-v1` | 262 | 60 | 45 (75.0%) | 8 (13.3%) | 7 (11.7%) |
| `analyze-engineering-decision-v1` | 264 | 60 | 45 (75.0%) | 8 (13.3%) | 7 (11.7%) |

## Verified Root Cause

- `BudgetedDiverseEvidenceSelector.categoryEligible()` allowed strong-relevance overflow above `kindAllowance`.
- No hard category ceiling existed.
- Real COMMIT_DIFF candidates are emitted as `layer=COMMIT_DIFF`, `kind=CHANGED_FILE`.
- Knowledge floors from Story 0095 were still functioning and were not the root problem.

## Implementation Notes

- Added `maximumCategorySharePercentage` to `EvidencePrecisionPolicy`.
- Enforced a hard category cap in `BudgetedDiverseEvidenceSelector` for both ordinary selection and floor selection.
- Preserved strong relevance scoring while preventing it from violating the ceiling.
- Applied the approved `20%` ceiling to:
  - `engineering-story-precision`
  - `history-v1` via a dedicated ceiling-only precision policy so generic Analysis intents using history also benefit.

## Post-Implementation Distributions

| Intent | Candidate Count | Selected Count | COMMIT_DIFF | GIT_HISTORY | Other |
|---|---:|---:|---:|---:|---:|
| `describe-project-v1` | 270 | 60 | 12 (20.0%) | 12 (20.0%) | 36 (60.0%) |
| `architecture-overview-v1` | 263 | 60 | 12 (20.0%) | 12 (20.0%) | 36 (60.0%) |
| `analyze-engineering-decision-v1` | 265 | 60 | 12 (20.0%) | 12 (20.0%) | 36 (60.0%) |

## Persisted Analysis Comparison

- Before Analysis: `8a06fd62-785b-4172-9ce8-2afbe1e2beed`
- After Analysis: `45af5ee5-f5fc-49a3-81d5-c31c0614c481`

Repository evidence selection:

- Before: `COMMIT_DIFF=37`, `GIT_HISTORY=13`, `VALIDATED_INSIGHT=6`, `PREVIOUS_ANALYSIS=2`, `ROADMAP=1`, `CURRENT_ANALYSIS=1`
- After: `COMMIT_DIFF=12`, `GIT_HISTORY=12`, `VALIDATED_INSIGHT=12`, `PREVIOUS_ANALYSIS=11`, `ROADMAP=11`, `ADR=1`, `CURRENT_ANALYSIS=1`

Observed effect: the selected repository context now includes materially more prior insight, roadmap, and previous-analysis evidence instead of being dominated by changed-file evidence.
