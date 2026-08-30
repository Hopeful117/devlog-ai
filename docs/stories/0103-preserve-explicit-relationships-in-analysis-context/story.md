# Story 0103 — Preserve Explicit Relationships in Analysis Context Composition

## Status

**HUMAN_IMPLEMENTATION_REVIEW_APPROVED_AUTHORIZED_FOR_COMMIT**

## Baseline

- Baseline SHA: `127a58e`
- Working branch: `story/0103-relationship-preservation`
- Baseline branch at start of correction: `main` at `127a58e`

## Objective

Preserve canonical `AnalysisContext.knowledgeRelations` into `SelectedKnowledge`, then project only Policy-A-eligible relationships into the AI-facing payload when both endpoints are already independently selected/projected.

## Authoritative Boundary

```text
AnalysisContext.knowledgeRelations
        ↓
SelectedKnowledge.knowledgeRelations
        ↓
SelectedKnowledgePromptProjectionService
        ↓
Policy A closure against already selected/projected endpoints
        ↓
bounded deterministic relationshipHighlights
        ↓
PromptProjection.relationshipHighlights
        ↓
existing automatic shared serialization
        ↓
AI-facing selectedKnowledge
```

Key invariant:

```text
SELECTION != COMPOSITION
```

## Final Scope

### Production
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`

### Tests
- `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
- `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionServiceTest.java`
- `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelectorTest.java` (Story 0098 regression verification only)

### Explicit Non-Scope Confirmed
- No production change in `BudgetedDiverseEvidenceSelector.java`
- No Python production change
- No frontend production change
- No selector/ranking/floor/ceiling behavior change
- No Insight lineage change
- No Decision to Proposal change
- No generic provenance change
- No already-preserved relationship duplication

## Acceptance Outcome

- AC1: canonical `knowledgeRelations` now survive into `SelectedKnowledge`
- AC2: Policy A enforced only in projection
- AC3: no selection expansion
- AC4: relationship highlights explicitly bounded to `20`
- AC5: no unexplained post-bound loss in the AFTER benchmark
- AC6: no invention verified by tests
- AC7: stable endpoint identity preserved via `entityType` + `entityId`
- AC8: additive backward-compatible projection
- AC9: Story 0098 selector behavior unchanged
- AC10: diagnostic `COMMIT_DIFF <= 12` verification remains green

## Benchmark State

### BEFORE
- Exact Story 0103 runtime BEFORE measurements were **not** captured before implementation.
- Historical `COMMIT_DIFF ~73-75%` numbers belong to pre-Story-0098 selector diagnostics and are **not valid** for baseline `127a58e`.
- For Story 0103 baseline, the only safe retrospective claim is that `relationshipHighlights` did not exist yet, so projected/payload relationship count would have been `0` by construction.

### AFTER
- Real product workflow benchmark executed for:
  - `describe-project-v1`
  - `architecture-overview-v1`
  - `analyze-engineering-decision-v1`
- In all three runs:
  - canonical `knowledgeRelations`: `44`
  - Policy-A eligible by actual endpoint membership: `0`
  - bound-retained: `0`
  - projected: `0`
  - final payload: `0`
  - `COMMIT_DIFF` selected evidence count: `12`

## Human Approval State

- HUMAN implementation review approved
- Authorized for commit
- Not pushed
- Not merged

`STORY_0103_CORRECTED_IMPLEMENTATION_READY_FOR_HUMAN_REVIEW`
