# Story 0098 — Engineering Report

## Status

**ENGINEERING_REPORT_AWAITING_HUMAN_REVIEW**

## Human Approval

- `HUMAN_IMPLEMENTATION_REVIEW = APPROVED`
- `history-v1` ceiling-only policy extension = HUMAN APPROVED

## Problem

COMMIT_DIFF evidence was consuming roughly three quarters of the repository-context budget because strong relevance could bypass the existing soft per-kind allowance and there was no hard category ceiling.

## Solution

Added a deterministic hard category ceiling at the selector layer.

- ceiling source: `EvidencePrecisionPolicy.maximumCategorySharePercentage`
- approved value: `20`
- budget calculation: `ceil(60 * 20 / 100) = 12`
- enforcement point: `BudgetedDiverseEvidenceSelector`

## Preserved Semantics

- strong relevance still affects ranking and still bypasses the soft kind allowance
- strong relevance no longer bypasses the hard category ceiling
- Story 0095 knowledge floors remain in place
- no AI-driven redistribution or prompt-layer control was introduced

## Risks

- Hard ceilings can reduce total selected evidence when too few eligible kinds exist. This is now explicit and covered by test.
- Canonical result previews still show only top-ranked repository evidence, so preview-only inspection can understate the diversity of the full selected set.

## Recommendation

Review the `history-v1` policy extension specifically. It is the only implementation deviation from the original narrow plan, and it was added to ensure persisted generic Analysis composition benefits from the same approved ceiling.

## Remaining Weakness

- Canonical result preview remains top-ranked and may still look COMMIT_DIFF-heavy even when the full selected 60-item context is balanced.
- Story 0098 fixes the `CATEGORY_SELECTION` bottleneck, not overall Analysis usefulness.
