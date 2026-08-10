# Story 0027 — Decision CRUD Completion

## Story ID

0027

## Title

Complete the Engineering Decision CRUD API with update and delete operations

## Status

Completed

## Priority

Medium

## Date

2026-08-10

---

## User Story

As a developer using DevLog AI,
I want to update and delete Engineering Decisions,
So that I can maintain an accurate record of architectural choices as the project evolves.

---

## Context

The Decision entity was created in an earlier phase with basic CRUD:
- ✅ Create (POST)
- ✅ Read (GET by ID, GET by project)
- ❌ Update (PUT)
- ❌ Delete (DELETE)

The entity has: `id`, `project`, `title`, `context`, `choice`, `rationale`, `consequences`, `createdAt`, `updatedAt`.

---

## Problem Statement

Without update/delete operations:
- Incorrect decisions cannot be corrected
- Obsolete decisions accumulate noise
- The knowledge model lacks maintenance capabilities

---

## Scope

### In Scope
1. Add `update()` method to `DecisionService`
2. Add `delete()` method to `DecisionService`
3. Add `UpdateDecisionRequest` DTO
4. Add PUT and DELETE endpoints to `DecisionController`
5. Add unit tests for update and delete operations

### Out of Scope
1. Soft delete (hard delete is sufficient for this phase)
2. Audit trail for decision changes
3. Cascade deletion of related knowledge relations

---

## Impact

- **Files Changed**: 5-6 Java files
- **Migration**: None
- **Tests**: 4-6 new tests

---

## Acceptance Criteria

1. Given a Decision, when I update its title, then the change is persisted and `updatedAt` is refreshed
2. Given a Decision, when I delete it, then it is removed from the database
3. Given a non-existent Decision ID, when I attempt update, then `EntityNotFoundException` is thrown
4. Given a non-existent Decision ID, when I attempt delete, then `EntityNotFoundException` is thrown
5. All existing tests continue to pass
