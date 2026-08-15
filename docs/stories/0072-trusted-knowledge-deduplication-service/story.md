# Story 0072 — Trusted Knowledge Deduplication Service

## Status

Draft

## Priority

High

## Objective

Create a knowledge deduplication service that resolves `TRUSTED_KNOWLEDGE_*`
maintenance findings by merging duplicate insights and archiving superseded ones.

## Motivation

The detection pipeline for duplicate knowledge is complete:

1. `TrustedKnowledgeDuplicateAuditService.audit()` detects duplicate clusters
2. `MaintenanceEvaluationServiceImpl.evaluate()` creates findings from audit
3. `DuplicateAmbiguityResolutionAgent` classifies ambiguous duplicates

But the **resolution pipeline is entirely missing**:

* The `Insight` entity has no status field (no `ACTIVE`/`ARCHIVED`/`SUPERSEDED`)
* There is no merge, supersede, or archive operation for insights
* The detection recommendations (`KEEP_NEWEST_AS_CANONICAL`, `KEEP_RICHEST_AS_CANONICAL`, `REVIEW_MANUALLY`) are never acted upon

This Story creates the missing resolution infrastructure.

## Scope

### In Scope

1. Add `status` field to `Insight` entity (`ACTIVE`, `ARCHIVED`, `SUPERSEDED`)
2. Create `InsightService.archiveInsight()` and `supersedeInsight()` methods
3. Create `KnowledgeDeduplicationService` with merge/resolve methods
4. Add `POST /actions/merge-duplicate` endpoint
5. Transfer knowledge relations from archived insights to canonical one

### Out Of Scope

* UI for side-by-side review of overlapping insights
* Auto-deduplication after evaluation
* Undo deduplication
* Bulk deduplication of multiple clusters

## Constraints

* Must preserve existing insight relationships
* Must handle insights with attached knowledge relations
* Must maintain referential integrity
* Comment required for deduplication action

## Acceptance Criteria

* AC-1: User can trigger merge action from maintenance UI
* AC-2: Merge action keeps canonical insight (newest or richest)
* AC-3: Merge action archives superseded insights
* AC-4: Knowledge relations transferred from archived to canonical
* AC-5: Finding transitions to RESOLVED after successful merge
* AC-6: Error handling for merge failures
* AC-7: Existing insight queries exclude ARCHIVED/SUPERSEDED by default

## Dependencies

* Story 0060-0065: Context Maintenance infrastructure
* `Insight` entity — needs status field
* `InsightService` — needs archive/supersede methods
* `TrustedKnowledgeDuplicateAuditService` — provides cluster data
