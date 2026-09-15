# Story 0129 Engineering Report

## Root Cause

`architecture-overview-v3` carried typed Insight references, but Java callback validation resolved them against the general task mapping rather than the exact `existingArchitectureKnowledge` subset. A project Insight could therefore be visible and resolvable without being an authorized enrichment target.

## Corrective Design

The task mapping snapshot now stores both the complete typed binding map and the exact architecture-knowledge reference subset. `AiReferenceResolver.resolveArchitectureTarget` first resolves the typed reference, then requires membership in that persisted subset. Callback handling performs the domain mapping and verifies that the Insight still exists in the task project before persistence.

Python applies the same subset rule defensively. Java remains authoritative.

The corrected boundary is explicit:

```text
PROJECT_VISIBILITY != TARGET_AUTHORIZATION
typed reference validity != membership in authorized task context
```

The authoritative membership source is the persisted mapping snapshot of the
originating `AiTask`; current selected knowledge is not a fallback.

## Compatibility

Older snapshots without `architectureKnowledgeReferences` deserialize as an empty subset. This preserves backward compatibility while failing closed for v3 enrichment targets. Existing v1/v2 paths remain unchanged.

## Residual Risk

Mockito emits a JDK dynamic-agent warning during tests; this is an existing test-runtime warning and not a Story 0129 failure.
