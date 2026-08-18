# Engineering Story 0081 — Preserve Evidence Priority Through RepositoryContext

## Status

**DONE**

## Problem Statement

After Story 0080 fixed evidence projection ordering, agent projection still removes trusted knowledge (ADR, INSIGHT, ENGINEERING_STORY) before lower-value repository evidence (SOURCE_FILE, COMMIT, CHANGED_FILE).

Post-Story 0080 benchmark: 35/60 evidence survive, but all survivors are lower-priority evidence. High-value trusted knowledge is removed despite having competitive or superior relevance scores.

## Root Cause

`RepositoryContextEngine.build()` re-sorts selected evidence from score-based priority (score DESC) to layer-based grouping (layer ordinal ASC). This places trusted knowledge (ADR=ordinal 4, ROADMAP=ordinal 5, VALIDATED_INSIGHT=ordinal 6) at the end of the list, where `AgentContextProjectionService.removeTailEvidence()` removes it first.

## Expected Behavior

When projection must remove evidence due to budget constraints, it should remove the lowest-priority items according to the selector's authoritative ordering, not according to layer grouping.

## Evidence

- DECISION (score 88) is removed while SOURCE_FILE (score 48) survives
- INSIGHT (score 80-86) is removed while CHANGED_FILE (score 80-89) survives
- ENGINEERING_STORY (score 79-81) is removed while COMMIT (score 77-86) survives
- All 35 survivors form a prefix of the layer-sorted list (positions 1-35)
- Trusted knowledge is NOT larger than repository evidence (avg sizes comparable)

## Out of Scope

- Ranking weight changes
- Selection algorithm changes
- Token/byte budget changes
- MCP changes
- Temporal knowledge
- Evidence compaction semantics
