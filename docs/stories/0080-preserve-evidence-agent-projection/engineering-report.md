# Engineering Story 0080 — Engineering Report

## Original Production Failure

The agent projection path (`/api/projects/{id}/engineering-story-context`,
default mode) produced 0 evidence items despite 60 selected items in the
`RepositoryContext`. The `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` warning was
emitted even though the deterministic ranking and selection layers had already
decided which evidence was valuable.

The MCP tool path (`/api/v1/projects/{slug}/engineering-context`) was
unaffected — it does not use agent projection and returned 60 evidence correctly.

## Root Cause

`AgentContextProjectionService.fit()` applied 6 evidence-only steps (1–5
compaction + 6 removal) before 4 ProjectContext reduction steps (7–10). When
ProjectContext alone (~68KB) exceeded the 32KB budget, all evidence was removed
in step 6 before ProjectContext was reduced in steps 7–10. Evidence removal
could not make the projection fit because ProjectContext was the primary budget
consumer.

## Selected Strategy

**Option A: Reorder existing fitting steps.**

Moved the 4 ProjectContext reduction steps to run before evidence removal. No
new methods, models, or abstractions. The same steps run in a different order.

## Before/After Fitting Semantics

**Before:**

```
Step 0:  fits()? → return
Step 1:  removeRelatedReferences()       [evidence]
Step 2:  compactReasons()                [evidence]
Step 3:  removeDeclarations()            [evidence]
Step 4:  removeContent()                 [evidence]
Step 5:  compactSummary()                [evidence]
Step 6:  removeTailEvidence()            [evidence removal]
Step 7:  removeProfileDetails()          [ProjectContext]
Step 8:  compactHumanContextInputs()     [ProjectContext]
Step 9:  removeProjectContextLists()     [ProjectContext]
Step 10: minimalProjectContext()         [ProjectContext]
```

**After:**

```
Step 0:  fits()? → return
Step 1:  removeRelatedReferences()       [evidence]
Step 2:  compactReasons()                [evidence]
Step 3:  removeDeclarations()            [evidence]
Step 4:  removeContent()                 [evidence]
Step 5:  compactSummary()                [evidence]
Step 6:  removeProfileDetails()          [ProjectContext]
Step 7:  compactHumanContextInputs()     [ProjectContext]
Step 8:  removeProjectContextLists()     [ProjectContext]
Step 9:  minimalProjectContext()         [ProjectContext]
Step 10: removeTailEvidence()            [evidence removal]
```

## Test Evidence

- 12/12 focused projection tests pass (8 existing + 4 new)
- 807/807 full backend suite tests pass
- Regression test confirms evidence survives when ProjectContext is oversized
- Determinism confirmed: identical input produces identical projectionDigest

## Full Suite Result

```
Tests run: 807, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Architectural Boundaries Preserved

- **Ranking/selection unchanged:** Projection consumes their output; it does
  not reinterpret relevance.
- **Budget unchanged:** 32,768 bytes / 8,192 tokens.
- **No type special-casing:** DECISION, INSIGHT, ENGINEERING_STORY receive no
  special treatment. Evidence survives based on ranking order.
- **ADR-058 preserved:** Projection remains a thin, deterministic,
  budget-driven compaction layer.
- **No new domain models:** No migrations, no entity changes, no schema changes.

## Remaining Known Limitations

- **Evidence compaction still runs before ProjectContext reduction.** This is
  intentional — evidence compaction is lightweight and may suffice for small
  overages. For large overages, ProjectContext reduction now runs before
  evidence removal.
- **Warning order may change.** ProjectContext warnings may now appear before
  evidence warnings. This is acceptable — warnings describe the final state,
  not the order of operations.
- **Physically impossible budgets still throw.** When even the minimal
  projection cannot fit the budget, `AgentContextProjectionException` is thrown.
  This is correct behaviour.
