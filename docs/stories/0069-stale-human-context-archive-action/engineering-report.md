# Story 0069 — Stale Human Context Archive Action — Engineering Report

## Architecture Impact

This Story extends the `MaintenanceRemediationService` pattern with a
domain-specific action that leverages existing infrastructure. The pattern
of extracting IDs from finding details and delegating to existing services
is established here for reuse in future stories.

## What Changed

| Layer | Change |
|-------|--------|
| Backend service | New `archiveStaleHumanContext()` method in `MaintenanceRemediationService` |
| Backend controller | New `POST /actions/archive-context-input` endpoint |
| Frontend service | New `archiveStaleHumanContext()` method |

## Workflow Coverage After Story

| Finding Type | Acknowledge | Dismiss | Resolve | Auto-Resolve | Remediation Action |
|-------------|-------------|---------|---------|--------------|-------------------|
| PROJECTION_REFRESH_GAP | ✅ | ✅ | ✅ | ❌ | Refresh projection |
| STALE_PROJECT_UNDERSTANDING | ✅ | ✅ | ✅ | ✅ | ❌ |
| MISSING_PROJECTION_REFRESH | ✅ | ✅ | ✅ | ✅ | ❌ |
| STALE_HUMAN_CONTEXT_INPUT | ✅ | ✅ | ✅ | ✅ | Archive context input |
| TRUSTED_KNOWLEDGE_EXACT_DUPLICATE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TRUSTED_KNOWLEDGE_OVERLAP_REVIEW | ✅ | ✅ | ✅ | ❌ | ❌ |

## Known Limitations

1. **Input ID extraction**: Relies on UUID pattern in finding details
2. **No undo**: Archival cannot be reversed from maintenance UI
3. **Single input**: Only archives one input per action

## Recommendations

Future improvements could include:

* Add undo archival action
* Support bulk archival of multiple stale inputs
* Show input details before archival
