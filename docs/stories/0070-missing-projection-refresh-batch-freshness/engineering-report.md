# Story 0070 — Missing Projection Refresh Batch Freshness — Engineering Report

## Architecture Impact

This Story extends the `MaintenanceRemediationService` with another domain-specific
action that leverages existing freshness infrastructure. The batch freshness
pattern established in Story 0068 is reused here.

## What Changed

| Layer | Change |
|-------|--------|
| Backend service | New `refreshMissingProjection()` method in `MaintenanceRemediationService` |
| Backend controller | New `POST /actions/refresh-missing-projection` endpoint |
| Frontend service | New `refreshMissingProjection()` method |

## Workflow Coverage After Story

| Finding Type | Acknowledge | Dismiss | Resolve | Auto-Resolve | Remediation Action |
|-------------|-------------|---------|---------|--------------|-------------------|
| PROJECTION_REFRESH_GAP | ✅ | ✅ | ✅ | ❌ | Refresh projection |
| STALE_PROJECT_UNDERSTANDING | ✅ | ✅ | ✅ | ✅ | ❌ |
| MISSING_PROJECTION_REFRESH | ✅ | ✅ | ✅ | ✅ | Refresh missing projection |
| STALE_HUMAN_CONTEXT_INPUT | ✅ | ✅ | ✅ | ✅ | Archive context input |
| TRUSTED_KNOWLEDGE_EXACT_DUPLICATE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TRUSTED_KNOWLEDGE_OVERLAP_REVIEW | ✅ | ✅ | ✅ | ❌ | ❌ |

## Known Limitations

1. **Batch freshness check**: Processes sources sequentially (not parallel)
2. **No progress feedback**: User cannot see which sources are being checked
3. **Source errors**: Individual source failures don't stop the batch

## Recommendations

Future improvements could include:

* Parallel freshness checks for better performance
* Progress indicator showing sources being checked
* Detailed results showing which sources were refreshed
