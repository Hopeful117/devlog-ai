# Story 0071 — Stale Understanding Chain Refresh — Engineering Report

## Architecture Impact

This Story introduces the first chained remediation action, combining two
existing services (freshness check + understanding re-analysis) into a single
user-initiated workflow. This establishes the pattern for complex multi-step
remediation actions.

## What Changed

| Layer | Change |
|-------|--------|
| Backend service | New `refreshProjectUnderstanding()` method in `MaintenanceRemediationService` |
| Backend controller | New `POST /actions/refresh-understanding` endpoint |
| Frontend service | New `refreshProjectUnderstanding()` method |

## Workflow Coverage After Story

| Finding Type | Acknowledge | Dismiss | Resolve | Auto-Resolve | Remediation Action |
|-------------|-------------|---------|---------|--------------|-------------------|
| PROJECTION_REFRESH_GAP | ✅ | ✅ | ✅ | ❌ | Refresh projection |
| STALE_PROJECT_UNDERSTANDING | ✅ | ✅ | ✅ | ✅ | Chain: freshness → understanding |
| MISSING_PROJECTION_REFRESH | ✅ | ✅ | ✅ | ✅ | Refresh missing projection |
| STALE_HUMAN_CONTEXT_INPUT | ✅ | ✅ | ✅ | ✅ | Archive context input |
| TRUSTED_KNOWLEDGE_EXACT_DUPLICATE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TRUSTED_KNOWLEDGE_OVERLAP_REVIEW | ✅ | ✅ | ✅ | ❌ | ❌ |

## Known Limitations

1. **Sequential execution**: Freshness check must complete before understanding
2. **No progress feedback**: User cannot see which step is running
3. **Full re-analysis**: Understanding re-analysis is always full (not incremental)

## Recommendations

Future improvements could include:

* Progress indicator showing current step (freshness/understanding)
* Incremental understanding re-analysis
* Parallel freshness checks for better performance
