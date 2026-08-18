# Story 0082 — Post-Selection Priority Ordering for Projection Survival

## Status

**PLANNED**

## Problem

The `BudgetedDiverseEvidenceSelector` outputs evidence in diversity-first order:
diversity picks are inserted first (in preferred layer order), then ordinary ranked
items follow. The `AgentContextProjectionService.removeTailEvidence()` removes
evidence from the tail of this list, interpreting "late in selector output" as
"lowest survival priority."

This creates a priority inversion:

- ENGINEERING_STORY score=81 is removed (inserted late by ordinary selection)
- INSIGHT score=80 is removed (inserted late by ordinary selection)
- SOURCE_FILE score=48 survives (inserted early by diversity selection)

## Cause

Diversity-first selection inserts some lower-score evidence before higher-score
ordinary selections. The selector output order is an implementation artifact of
the two-phase algorithm, not a deliberate survival-priority signal.

## Consequence

Tail removal can preserve lower-score evidence while removing higher-score
evidence. The projection implicitly consumes selector insertion order as survival
priority, which was never the intended semantic.

## Correction

After selection has completed, evidence transmitted toward destructive budget
projection must have an explicit deterministic conservation order.

For V1, that conservation order is:

`relevanceScore DESC`

followed by the existing deterministic tie-breakers already established by the
ranking semantics (layer ordinal ASC, reference ASC).

This ensures projection tail removal removes the lowest-priority evidence first,
without changing which evidence is selected.

## Non-Goal

Changing which evidence gets selected. The selected SET remains unchanged.

## Investigation Reference

Projection Survival Policy Investigation — completed on branch `main`.

## Expected Invariant

Once the evidence SET has been definitively selected, ordering used by a
destructive budget projection MUST represent deterministic conservation priority
rather than selector insertion order.
