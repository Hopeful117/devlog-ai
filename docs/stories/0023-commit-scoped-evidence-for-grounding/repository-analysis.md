# Repository Analysis — Story 0023: Commit-Scoped Evidence for Engineering Event Grounding

## Problem

The grounding contract in `EngineeringEventPromptBuilder._grounding()` builds `allowedSupportingFactIds` from `selectedFacts`. But the `CommitDiffEvidenceCollector` produces `RepositoryEvidence` items (file-level metadata), not `FactSnapshot` items. As a result, `allowedSupportingFactIds` is always empty for engineering events, and the AI Engine cannot produce grounded proposals.

## Architecture Understanding

### Evidence Flow (Current)

```
CommitDiffEvidenceCollector
  → List<RepositoryEvidence>  (kind=CHANGED_FILE, layer=COMMIT_DIFF)
  → RepositoryContext.evidence()  ← stored in SelectedKnowledge

KnowledgeSelectionServiceImpl.select()
  → context.facts() → selectedFacts  (FactSnapshot list, max 40)
  → context.observations() → selectedObservations
  → repositoryContextService.build() → repositoryContext

EngineeringEventPromptBuilder._grounding(selectedKnowledge)
  → iterates selectedFacts → extracts evidenceReferences → allowedEvidenceReferences ✓
  → iterates selectedFacts → extracts IDs → allowedSupportingFactIds ✓
  → iterates selectedObservations → extracts IDs → allowedSupportingObservationIds ✓
  → iterates repositoryContext.evidence() → extracts reference → allowedEvidenceReferences ✓
  → BUT: repositoryContext evidence items have NO `id` field → NOT added to allowedSupportingFactIds ✗
```

### Root Cause

1. `CommitDiffEvidenceCollector` produces `RepositoryEvidence` (no UUID `id` field, just `reference` string)
2. `EngineeringEventPromptBuilder._grounding()` only adds fact IDs from `selectedFacts` to `allowedSupportingFactIds`
3. The AI Engine needs `supportingFactIds` that resolve to actual `allowedSupportingFactIds`
4. There are no commit-scoped `FactSnapshot` items in the analysis context

### Key Files

- `backend/.../repositorycontext/collector/CommitDiffEvidenceCollector.java` — produces file-level evidence
- `backend/.../repositorycontext/collector/EvidenceFactory.java` — creates RepositoryEvidence items
- `backend/.../fact/entity/FactType.java` — enum of fact types (no commit-scoped types exist)
- `backend/.../knowledge/selection/KnowledgeSelectionServiceImpl.java` — selects facts for grounding
- `backend/.../knowledge/selection/SelectedKnowledge.java` — serializes selected facts
- `ai-engine/app/prompts/engineering_event.py` — builds grounding contract from SelectedKnowledge JSON

### Design Insight

The gap is between two separate evidence systems:
- **FactSnapshot** (database entity, collected by repository context collectors, ranked by KnowledgeSelection) — has `id`, `type`, `content`, `evidenceReferences`
- **RepositoryEvidence** (in-memory, collected by RepositoryContextCollector, stored in RepositoryContext) — has `reference`, `kind`, `summary`, but no UUID `id`

The grounding contract needs UUID-traceable facts. The `CommitDiffEvidenceCollector` currently produces `RepositoryEvidence` items that lack UUID identity.

## Recommended Approach

**Option A — Add commit-scoped FactType values** (preferred)

Add new `FactType` enum values (e.g., `COMMIT_DIFF_SUMMARY`, `COMMIT_CHANGES_MODULE`, `COMMIT_ADDS_FEATURE`, `COMMIT_FIXES_BUG`, `COMMIT_REFACTORS_CODE`, `COMMIT_UPDATES_DEPS`, `COMMIT_CHANGES_CONFIG`) and produce them as `FactSnapshot` items via a new collector or extended collector.

Pros:
- Facts flow through existing `KnowledgeSelectionService` → `selectedFacts` → grounding contract
- AI Engine can reference them by UUID in `supportingFactIds`
- Backward compatible (existing fact types unchanged)

Cons:
- Requires a new collector (or extending CommitDiffEvidenceCollector to also produce facts)
- Facts must be persisted to the database (new Flyway migration)

**Option B — Extend grounding contract to reference RepositoryEvidence**

Modify `EngineeringEventPromptBuilder._grounding()` to also add evidence references from `repositoryContext.evidence()` as allowed `supportingFactIds` (using the `reference` string as an identifier).

Pros:
- No new fact types or database changes
- Leverages existing commit-diff evidence

Cons:
- `supportingFactIds` would use string references instead of UUIDs (schema change in AI Engine)
- Breaks the clean UUID-based grounding model
- Less traceable

## Recommendation

**Option A** is architecturally cleaner and aligns with the long-term goal of a strict, auditable grounding contract. The implementation is small:
1. Add 3-4 new `FactType` values to the enum
2. Create a `CommitScopedFactCollector` that produces facts from commit diff analysis
3. Register the collector in the pipeline
4. Add a Flyway migration if needed (facts are stored in the `fact` table)
5. Update `KnowledgeSelectionService` fact scoring for the new types

The `CommitDiffEvidenceCollector` remains unchanged (file-level evidence continues to work).
