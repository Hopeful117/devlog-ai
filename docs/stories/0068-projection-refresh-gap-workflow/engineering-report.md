# Story 0068 — Projection Refresh Gap Workflow — Engineering Report

## Architecture Impact

This Story introduces the first domain-specific remediation action, creating
the `MaintenanceRemediationService` as the new architecture pattern for
connecting findings to actual domain operations.

## What Changed

| Layer | Change |
|-------|--------|
| Backend service | `supportsWorkflow()` now returns true for PROJECTION_REFRESH_GAP |
| Backend service | New `MaintenanceRemediationService` with `refreshProjection()` |
| Backend controller | New `POST /actions/refresh-projection` endpoint |
| Frontend service | New `refreshProjection()` method |
| Frontend component | `supportsWorkflow()` now returns true for PROJECTION_REFRESH_GAP |

## Workflow Coverage After Story

| Finding Type | Acknowledge | Dismiss | Resolve | Auto-Resolve | Refresh Action |
|-------------|-------------|---------|---------|--------------|----------------|
| PROJECTION_REFRESH_GAP | ✅ | ✅ | ✅ | ❌ | ✅ |
| STALE_PROJECT_UNDERSTANDING | ✅ | ✅ | ✅ | ✅ | ❌ |
| MISSING_PROJECTION_REFRESH | ✅ | ✅ | ✅ | ✅ | ❌ |
| STALE_HUMAN_CONTEXT_INPUT | ✅ | ✅ | ✅ | ✅ | ❌ |
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
