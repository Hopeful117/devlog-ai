# Engineering Report

## Story

Story 0004 — Add Repository Structure Collector to the Repository Context Engine.

Add a new `RepositoryContextCollector` that scans the project's filesystem and produces `RELATED_SOURCE_CODE` evidence about the repository's file structure — modules, source directories, test directories, configuration files, and file extension distribution.

---

## Objective

After Stories 0001–0003, `EngineeringStoryContext` provided ranked evidence about commits, decisions, milestones, insights, and architecture artifacts — but zero information about the actual repository structure. Kiko could not identify which modules, packages, or files might be impacted when preparing an Engineering Story.

The `collection/collector` package already had comprehensive scanning capabilities (`SecureRepositoryScanner`, `RepositoryMetadataCollector`, `TestStructureCollector`, `BuildCollector`) but these produced `Fact` entities in the database pipeline, not `RepositoryEvidence` in the context engine. This story bridged that gap.

---

## Repository Analysis Summary

The analysis identified:

- **4 existing collectors** producing evidence from database entities (no filesystem awareness)
- **Comprehensive scanning infrastructure** in `collection/collector` package (fully implemented, tested)
- **Workspace access** via `WorkspaceManager.synchronize(Source, revision)` → `SynchronizedWorkspace`
- **The biggest gap**: file discovery exists but is not connected to the repository context pipeline

Key architectural constraint: the new collector must implement `RepositoryContextCollector` and be auto-detected by Spring via `List<RepositoryContextCollector>` injection.

---

## Implementation Plan Summary

Approved strategy:

1. Create `RepositoryStructureCollector` — self-contained, injects `SourceRepository` + `WorkspaceManager` directly
2. Create unit tests (5 tests)
3. Update `engineering-story-v1` profile to include `RELATED_SOURCE_CODE`
4. No adapter modifications, no interface changes, no database migrations

Key decision: collector resolves workspace independently rather than modifying `ContextRequest`.

---

## Implementation Summary

All planned work completed:

- `RepositoryStructureCollector` scans filesystem via `SecureRepositoryScanner` (no content reading)
- Produces 5 evidence kinds: MODULE_SUMMARY, SOURCE_DIRECTORIES, TEST_DIRECTORIES, CONFIGURATION_FILES, FILE_EXTENSIONS
- Gracefully handles missing sources and unavailable workspaces
- Profile updated with `RELATED_SOURCE_CODE` as first preferred layer
- All 5 tests pass, compilation clean, no regressions

---

## Modified Files

| File | Change |
|---|---|
| `DeterministicContextIntelligence.java` | Added `RELATED_SOURCE_CODE` to `engineering-story-v1` preferred layers |

---

## Created Files

| File | Purpose |
|---|---|
| `RepositoryStructureCollector.java` | New collector — scans filesystem, produces 5 kinds of `RELATED_SOURCE_CODE` evidence |
| `RepositoryStructureCollectorTest.java` | Unit tests — 5 tests covering layer, modules, directories, graceful failures |

---

## Architecture Impact

No architectural changes. The new collector follows the existing `RepositoryContextCollector` contract and is auto-detected by Spring. No modifications to the engine, existing collectors, or public contracts.

---

## Validation

```
Command: cd backend && mvn compile
Result: Success

Command: cd backend && mvn test -Dtest=RepositoryStructureCollectorTest
Result: Pass (5/5)

Command: cd backend && mvn test
Result: 6 failures/errors — all pre-existing and unrelated
```

---

## Review Outcome

Code Review recommendation: Ready for human approval.

Findings: 1 Observation (unused `CollectorLimits` injection — safe, can be cleaned up later).

No Blocker or Major findings.

Human Code Review approval: granted.

---

## Workflow Approvals

- Repository Analysis: Human approved
- Implementation Plan: Human approved
- Code Review: Human approved

---

## Remaining Work

- **Observation cleanup**: Remove unused `CollectorLimits` injection (optional, non-blocking)
- **Performance optimization**: Cache workspace path to avoid redundant git synchronization on repeated calls (future story)

---

## Lessons Learned

- The `collection/collector` package's scanning infrastructure is highly reusable — connecting it to the context pipeline required only a thin collector wrapper
- Self-contained collectors (injecting their own dependencies) are cleaner than modifying shared interfaces like `ContextRequest`
- `SecureRepositoryScanner` with `includeContent=false` is a lightweight, bounded way to get repository structure without the overhead of full collection

---

## Final Status

**Completed**
