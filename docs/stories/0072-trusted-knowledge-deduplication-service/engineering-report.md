# Story 0072 — Trusted Knowledge Deduplication Service — Engineering Report

## Architecture Impact

This Story completes the detection → resolution pipeline for duplicate knowledge,
establishing the `KnowledgeDeduplicationService` as the new architecture pattern
for complex domain-specific remediation actions.

The Insight entity now has a status mechanism that enables archival and
superseding operations, which can be reused for other knowledge management
workflows.

## What Changed

| Layer | Change |
|-------|--------|
| Entity | Added `InsightStatus` enum and `status` field to `Insight` |
| Service | Added `archiveInsight()` and `supersedeInsight()` to `InsightService` |
| Repository | Updated queries to filter by status |
| Service | New `KnowledgeDeduplicationService` with merge/resolve methods |
| Controller | New `POST /actions/merge-duplicate` and `POST /actions/resolve-semantic-duplicate` endpoints |
| Frontend | New `mergeDuplicate()` and `resolveSemanticDuplicate()` methods |

## Complete Workflow Coverage After All Stories

| Finding Type | Acknowledge | Dismiss | Resolve | Auto-Resolve | Remediation Action |
|-------------|-------------|---------|---------|--------------|-------------------|
| PROJECTION_REFRESH_GAP | ✅ | ✅ | ✅ | ❌ | Refresh projection |
| STALE_PROJECT_UNDERSTANDING | ✅ | ✅ | ✅ | ✅ | Chain: freshness → understanding |
| MISSING_PROJECTION_REFRESH | ✅ | ✅ | ✅ | ✅ | Refresh missing projection |
| STALE_HUMAN_CONTEXT_INPUT | ✅ | ✅ | ✅ | ✅ | Archive context input |
| TRUSTED_KNOWLEDGE_EXACT_DUPLICATE | ✅ | ✅ | ✅ | ❌ | Merge exact duplicate |
| TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE | ✅ | ✅ | ✅ | ❌ | Resolve semantic duplicate |
| TRUSTED_KNOWLEDGE_OVERLAP_REVIEW | ✅ | ✅ | ✅ | ❌ | ❌ (human review only) |

## Known Limitations

1. **No side-by-side review**: Overlap review still requires manual navigation
2. **No undo**: Deduplication cannot be reversed
3. **No bulk merge**: Each cluster must be merged individually

## Recommendations

Future improvements could include:

* Side-by-side review UI for overlapping insights
* Undo deduplication action
* Bulk merge of multiple clusters
* Auto-deduplication for high-confidence exact duplicates
