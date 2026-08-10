# Implementation Plan — Adapter Knowledge Propagation Bugfix

## Overview

Propagate three `ProjectContextSnapshot` fields (`validatedEngineeringEvents`, `openChallenges`, `knowledgeRelations`) into the synthesized `AnalysisContext` produced by `RepositoryContextAdapter`. One field is a pure propagation bug; two require adding fields to the `AnalysisContext` record.

## Step 1 — Add `openChallenges` and `knowledgeRelations` to `AnalysisContext`

**File**: `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContext.java`

Add two new fields to the canonical record, after `validatedEngineeringEvents`:

```java
List<ProjectContextSnapshot.ChallengeSnapshot> openChallenges,
List<ProjectContextSnapshot.KnowledgeRelationSnapshot> knowledgeRelations
```

Update the canonical constructor's copyOf block to include:

```java
openChallenges = List.copyOf(openChallenges);
knowledgeRelations = List.copyOf(knowledgeRelations);
```

Update the two convenience constructors to pass `List.of()` for both new fields (preserving backward compatibility).

**Note**: This introduces a dependency from `AnalysisContext` (in `analysis.context` package) to `ProjectContextSnapshot` inner records (in `projectcontext` package). Verify this does not create a circular dependency. If it does, extract the snapshot records to a shared package or use interface/record aliases.

## Step 2 — Propagate all three fields in `RepositoryContextAdapter`

**File**: `backend/src/main/java/com/hopeful117/devlogai/projectcontext/RepositoryContextAdapter.java`

In `synthesizeAnalysisContext()`, change the 11-argument constructor call to the full canonical constructor, passing:

- `snapshot.validatedEngineeringEvents()` (was hardcoded as `List.of()`)
- `snapshot.openChallenges()` (new)
- `snapshot.knowledgeRelations()` (new)

The `evolutionContext` parameter remains `null` (correct — no evolution scope in the adapter path).

## Step 3 — Propagate new fields in `AnalysisContextServiceImpl`

**File**: `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`

In the `build()` method (around line 95), add the two new parameters to the `AnalysisContext` constructor call:

- `projectContext.openChallenges()`
- `projectContext.knowledgeRelations()`

This ensures the normal analysis path also propagates these fields consistently.

## Step 4 — Add regression tests

**File**: `backend/src/test/java/com/hopeful117/devlogai/projectcontext/RepositoryContextAdapterTest.java`

### 4a. Update the `snapshot()` fixture

Replace the legacy 8-argument constructor with the full 11-argument constructor, populating non-empty lists for `validatedEngineeringEvents`, `openChallenges`, and `knowledgeRelations`.

### 4b. Add test: `validatedEngineeringEvents` survives adapter synthesis

- Build a snapshot with one `EngineeringEventSnapshot`
- Call `adapter.buildRepositoryContext(projectId, description)`
- Capture the `AnalysisContext` via `ArgumentCaptor`
- Assert `ctx.validatedEngineeringEvents()` has size 1 and matches the input

### 4c. Add test: `openChallenges` survives adapter synthesis

- Same pattern: snapshot with one `ChallengeSnapshot`
- Assert `ctx.openChallenges()` has size 1 and matches

### 4d. Add test: `knowledgeRelations` survives adapter synthesis

- Same pattern: snapshot with one `KnowledgeRelationSnapshot`
- Assert `ctx.knowledgeRelations()` has size 1 and matches

### 4e. Verify existing fields still propagate

The existing 4 tests already verify project, analysis, and intent. After updating the fixture, confirm they still pass with the new snapshot constructor.

## Step 5 — Verify compilation and tests

Run the full backend test suite:

```bash
cd /home/ludo/Bureau/workspace/devlog-ai/backend && mvn test
```

Expected: all existing tests pass + 3 new tests pass. No new failures.

## Out of Scope

- No new collectors or scoring rules
- No new evidence kinds
- No ranking weight changes
- No migration
- No AI behavior
- No changes to `devlog-context.mjs` or Engineering-Skills

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Circular dependency `analysis.context` → `projectcontext` | Low | Verify at compile time. If circular, extract shared records. |
| Existing tests break from constructor change | Very Low | Convenience constructors preserve backward compatibility |
| New fields cause projection size increase | Very Low | Fields default to `List.of()` in normal path; adapter only adds data when present |
