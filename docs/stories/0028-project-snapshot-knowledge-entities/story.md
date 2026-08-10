# Story 0028 — Project Snapshot: Knowledge Entities

## Story ID

0028

## Title

Enrich Project Context Snapshot with Challenges and Knowledge Relations

## Status

Completed

## Priority

Medium

## Date

2026-08-10

---

## User Story

As a developer using DevLog AI,
I want my project context snapshot to include open challenges and knowledge relationships,
So that I have a complete picture of the project's technical memory in a single view.

---

## Context

The `ProjectContextSnapshot` already includes:
- ✅ Project info
- ✅ Project profile
- ✅ Recent knowledge events
- ✅ Validated proposals
- ✅ Architecture artifacts
- ✅ Related decisions
- ✅ Recent milestones
- ✅ Recent analyses
- ✅ Validated engineering events

What's missing:
- ❌ Open Challenges
- ❌ Knowledge Relations

---

## Problem Statement

Without Challenges and Knowledge Relations in the snapshot:
- The project context lacks visibility into unresolved technical issues
- Cross-entity relationships are invisible in the aggregated view
- Consumers must make separate API calls to understand the full knowledge graph

---

## Scope

### In Scope
1. Add `List<ChallengeSnapshot>` to `ProjectContextSnapshot`
2. Add `List<KnowledgeRelationSnapshot>` to `ProjectContextSnapshot`
3. Add `ChallengeRepository` queries for open challenges
4. Add `KnowledgeRelationRepository` queries for project relations
5. Update `ProjectContextProviderImpl` to populate new fields
6. Add snapshot mapping methods
7. Add tests

### Out of Scope
1. Filtering/pagination of challenges or relations in the snapshot
2. New API endpoints (snapshot is internal context)
3. Challenge or relation creation/modification

---

## Impact

- **Files Changed**: 4-5 Java files
- **Migration**: None
- **Tests**: 3-5 new tests

---

## Acceptance Criteria

1. Given a project with open Challenges, when I build the snapshot, then open Challenges are included
2. Given a project with Knowledge Relations, when I build the snapshot, then relations are included
3. Given a project with no Challenges, when I build the snapshot, then an empty list is returned
4. All existing tests continue to pass
