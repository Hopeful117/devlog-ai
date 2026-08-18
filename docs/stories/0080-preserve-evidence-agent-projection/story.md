# Engineering Story 0080 — Preserve Selected Evidence During Agent Context Projection

## Status

Draft.

## Priority

High.

## Context

The "Lineage Phase 2 — Engineering Context Exposure Diagnostics" investigation
identified a concrete projection-behaviour gap in
`AgentContextProjectionService.fit()`.

The deterministic context pipeline produces a healthy `RepositoryContext`:

- 217 candidates
- 60 selected (1 DECISION, 3 INSIGHT, 5 ENGINEERING_STORY, 7 COMMIT,
  43 CHANGED_FILE, 1 SOURCE_FILE)

The agent projection (`AgentContextProjectionService.project()`) then reduces
this to a 32KB budget. The canonical projection before fitting is approximately:

- Evidence: ~144 KB
- ProjectContextSnapshot: ~68 KB
- Total: ~213 KB

The current `fit()` strategy strips evidence-related information (steps 1–5),
then removes evidence items from the tail (step 6), and **only afterwards**
reduces ProjectContext (steps 7–10). When ProjectContext alone exceeds the
budget, all evidence is removed before ProjectContext is reduced — producing
`AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` despite highly relevant evidence having
already been selected by the deterministic ranking and selection layers.

This violates the intended purpose of the context pipeline: the ranking and
selection layers have already decided which evidence is valuable for the agent.
Agent projection should preserve as much of that selected evidence as possible.

## Problem Statement

Selected RepositoryEvidence is currently sacrificed because reducible
ProjectContext data consumes the agent projection budget. The `fit()` method
reduces evidence before reducing ProjectContext, so when ProjectContext is
independently oversized, all evidence is removed before the ProjectContext is
compact enough for the budget.

## Repository Analysis

See `repository-analysis.md` for the full analysis.

Summary:

- **Root cause:** `fit()` ordering — evidence steps (1–6) run before
  ProjectContext steps (7–10). When ProjectContext alone exceeds the budget,
  evidence is removed first and cannot be recovered.
- **Production failure:** 60 selected evidence → 0 after projection, despite
  ProjectContext being the primary budget consumer (~68KB vs 32KB budget).
- **Existing tests:** `shouldCompactProjectContextWhenEmptyEvidenceStillDoesNotFit`
  proves ProjectContext reduction works, but uses only 1 evidence item — doesn't
  reproduce the 60-evidence failure class.
- **MCP tool path is unaffected:** the MCP endpoint
  (`/api/v1/projects/{slug}/engineering-context`) does not use agent projection
  and returns 60 evidence correctly. Only the agent projection path
  (`/api/projects/{id}/engineering-story-context`, default mode) exhibits the
  failure.

## Architectural Constraints

- Deterministic correction only — same input produces same output.
- Existing projection budget (32KB / 8192 tokens) unchanged.
- No ranking / selection redesign.
- No special-casing of DECISION / INSIGHT / ENGINEERING_STORY types.
- Evidence removal remains permitted when physically necessary.
- ADR-058 projection responsibility preserved (thin, deterministic,
  budget-driven compaction).
- No new domain models, no migrations, no entity changes.

## Proposed Responsibility

Reorder the `fit()` steps in `AgentContextProjectionService` so that
ProjectContext reduction runs **before** total evidence removal:

**Current ordering:**

```
evidence compaction (steps 1–5)
→ evidence removal (step 6)
→ ProjectContext reduction (steps 7–10)
```

**Proposed ordering:**

```
evidence compaction (steps 1–5)
→ ProjectContext reduction (steps 7–10)
→ evidence removal (step 6, last resort)
```

This ensures that when ProjectContext is independently oversized, it is
reduced first, creating budget space for evidence to survive. Evidence
removal remains the final fallback when even the reduced projection cannot
fit all evidence alongside the required context.

## Proposed Behaviour

**Before:**

```
217 candidates → 60 selected → 0 agent-projected evidence
AGENT_PROJECTION_ALL_EVIDENCE_REMOVED
```

**After:**

```
217 candidates → 60 selected → N > 0 agent-projected evidence
```

Where N depends on how much budget remains after ProjectContext reduction.
High-ranked evidence (appearing first in the list) naturally survives because
`removeTailEvidence()` removes from the tail.

## Acceptance Criteria

- AC1: Final projected context respects configured maximum bytes and tokens.
- AC2: ProjectContext reduction precedes total evidence loss when ProjectContext
  is independently oversized and contains reducible information.
- AC3: After projection, if at least one evidence item can fit alongside the
  reduced required context, at least one evidence item remains.
- AC4: Evidence may still be compacted and/or tail evidence removed when
  necessary to satisfy the hard budget.
- AC5: `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` is only emitted when no evidence
  can fit even after appropriate reductions.
- AC6: For identical input context and identical budgets, projection output
  remains deterministic.
- AC7: No change to candidate generation, ranking, selection, diversity, or
  context profiles.
- AC8: A context that already fits the budget does not undergo unnecessary
  destructive reduction.
- AC9: No special-casing of DECISION / INSIGHT / ENGINEERING_STORY inside the
  projection service.
- AC10: Regression test reproducing the production failure class (oversized
  ProjectContext + multiple evidence items → evidence survives).

## Test Strategy

Focused deterministic tests proving observable behaviour. See
`repository-analysis.md` (§ Existing Tests) for the current test inventory.

New tests target:

1. Oversized ProjectContext + multiple evidence → ProjectContext reduced before
   total evidence loss → at least one evidence survives.
2. Context already under budget → unchanged / no destructive fitting.
3. Context still oversized after ProjectContext reduction → evidence
   reduction/removal works.
4. Physically impossible budget → all evidence may be removed → warning correctly
   emitted.
5. Deterministic repeated projection → same input produces same output.
6. Regression reproduction of the real failure class.

## Risks

- Reordering steps may change which warnings are emitted for some inputs
  (evidence-related warnings may now appear after ProjectContext warnings).
  This is acceptable — warnings describe the final state, not the order of
  operations.
- Existing tests that assert specific warning presence may need adjustment
  if they depend on step ordering. Verified: no existing test asserts warning
  **order**, only **presence**.
- The `shouldCompactProjectContextWhenEmptyEvidenceStillDoesNotFit` test
  currently runs steps 1–6 (no-op for 1 small evidence) then steps 7–10.
  After reordering, steps 1–5 still run (no-op), then steps 7–10 run, then
  step 6 runs. The test assertion checks for ProfileDetailsRemoved OR
  HumanContextInputsCompacted OR ProjectContextListsRemoved OR
  ProjectContextMinimal — all still valid.

## Dependencies

- `AgentContextProjectionService` — primary change target.
- `AgentContextProjectionPolicy` — budget constants (unchanged).
- `AgentRepositoryContext` — DTO records (unchanged).
- `ProjectContextSnapshot` — snapshot record (unchanged).
- ADR-058 (projection responsibility).
- Lineage Phase 2 investigation findings.

## Explicitly Out of Scope

- Budget increase (32KB → 64KB).
- Ranking / selection redesign.
- Evidence type priority encoding (DECISION > INSIGHT > STORY).
- Special-casing trusted knowledge types.
- MCP adapter changes.
- Database schema / migrations.
- Temporal Knowledge.
- Data Lineage persistence.
- Retrieval / RAG.
- Event Sourcing.
- AI Engine.
- Frontend.
- Context profile weights.
