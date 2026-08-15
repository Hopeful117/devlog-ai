# Story 0067 — Maintenance Stale Projection Workflow — Engineering Report

## Architecture Impact

This bugfix extends the existing workflow support to cover all finding types
that users should be able to dismiss.

## What Changed

| Layer | Change |
|-------|--------|
| Backend service | `supportsWorkflow()` now returns true for STALE_PROJECT_UNDERSTANDING and MISSING_PROJECTION_REFRESH |
| Frontend component | `supportsWorkflow()` now returns true for same types |

## Workflow Coverage After Fix

| Finding Type | Acknowledge | Dismiss | Resolve | Auto-Resolve |
|-------------|-------------|---------|---------|--------------|
| STALE_PROJECT_UNDERSTANDING | ❌ | ✅ | ❌ | ✅ |
| MISSING_PROJECTION_REFRESH | ❌ | ✅ | ❌ | ✅ |
| STALE_HUMAN_CONTEXT_INPUT | ✅ | ✅ | ✅ | ✅ |
| TRUSTED_KNOWLEDGE_EXACT_DUPLICATE | ✅ | ✅ | ✅ | ❌ |
| TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE | ✅ | ✅ | ✅ | ❌ |
| TRUSTED_KNOWLEDGE_OVERLAP_REVIEW | ✅ | ✅ | ✅ | ❌ |

## Why Only Dismiss (Not Acknowledge/Resolve)

* **Dismiss**: User judges the finding is a false positive → dismiss with rationale
* **Acknowledge**: Not needed for stale/missing types (no intermediate state required)
* **Resolve**: Already handled by auto-resolve when condition clears

## Known Limitations

1. **No Acknowledge for stale/missing types**: User can only dismiss or wait
2. **Auto-resolve still works**: Finding will auto-resolve if condition clears before dismiss
3. **Dismiss is permanent**: Once dismissed, finding won't reappear unless re-evaluated

## Recommendations

Future improvements could include:

* Allow Acknowledge for stale/missing types (intermediate "I'll handle it later")
* Add a "Snooze" action (dismiss for N days, then reappear)
