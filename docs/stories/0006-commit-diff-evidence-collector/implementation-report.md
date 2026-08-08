# Story 0006 — CommitDiffEvidenceCollector Implementation Report

**Date:** 2026-08-08
**Status:** ✅ Complete

## Files Created

### 1. `CommitDiffEvidenceCollector.java`
**Path:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/CommitDiffEvidenceCollector.java`

New `@Component` collector implementing `RepositoryContextCollector`:
- **Order:** `@Order(35)` (between `GitHistoryContextCollector` at 30 and `RepositoryStructureCollector` at 40)
- **Collector ID:** `"commit-diff"`
- **Collector Version:** `"v1"`
- **Layer:** `COMMIT_DIFF`
- **Kind:** `"CHANGED_FILE"`

Key behaviors:
- Queries `ProjectCommitRepository.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc()` for commits within the configured window (default 90 days)
- Flattens all `ChangedFile` entities from matching commits
- Excludes binary files, generated/vendor paths (`node_modules`, `vendor`, `target`, `build`, `dist`, `coverage`, `.venv`, `venv`), and `.min.js`/`.map` files
- Groups files by normalized path (`newPath ?? oldPath`), deduplicating across commits
- Accumulates insertions/deletions across commits; collects all commit hashes as `relatedReferences`
- Uses the most recent commit's metadata (hash, timestamp, change type) for each deduplicated file
- Sorts by: `occurredAt` desc → `(insertions + deletions)` desc → `path` asc
- Respects configurable `maxItems` limit (default 50)
- Uses `EvidenceFactory` for evidence creation, following the same pattern as `GitHistoryContextCollector`

Configuration properties:
- `${devlog.context.commit-diff.max-items:50}` — maximum evidence items
- `${devlog.context.commit-diff.window-days:90}` — temporal window in days

### 2. `CommitDiffEvidenceCollectorTest.java`
**Path:** `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/CommitDiffEvidenceCollectorTest.java`

12 test cases covering all requirements:

| # | Test | What it verifies |
|---|------|------------------|
| 1 | `producesChangedFileEvidenceForModifiedFiles` | MODIFIED files produce CHANGED_FILE evidence with correct summary format |
| 2 | `producesEvidenceForAddedFiles` | ADDED files produce evidence with "Added" prefix |
| 3 | `producesEvidenceForDeletedFiles` | DELETED files produce evidence with "Deleted" prefix |
| 4 | `producesEvidenceForRenamedFiles` | RENAMED files produce evidence with "Renamed" and both paths |
| 5 | `excludesBinaryFiles` | Binary files are filtered out |
| 6 | `excludesGeneratedVendorPaths` | target/, node_modules/, build/, dist/, vendor/ are excluded |
| 7 | `excludesMinJsAndMapFiles` | .min.js and .map files are excluded |
| 8 | `deduplicatesFilesAcrossMultipleCommits` | Files in multiple commits are merged (insertions/deletions summed, all hashes collected) |
| 9 | `usesMostRecentCommitMetadataForDeduplicatedFiles` | Most recent commit's timestamp, hash, and change type are used |
| 10 | `filtersCommitsOutsideTemporalWindow` | Verifies the collector processes commits returned by the repository (DB filtering is delegated) |
| 11 | `respectsMaxItemsLimit` | Output is capped at `maxItems` |
| 12 | `returnsEmptyListWhenNoCommitsExist` | Empty list when no commits match |

## Files Modified

### 3. `ProjectCommitRepository.java`
**Path:** `backend/src/main/java/com/hopeful117/devlogai/history/repository/ProjectCommitRepository.java`

Added one method to the Spring Data JPA repository interface:
```java
List<ProjectCommit> findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
        UUID projectId, java.time.Instant after);
```

This enables efficient querying of commits within a time window, ordered by most recent first.

## Validation Results

### Compile
```
mvn compile -q → SUCCESS (no errors)
```

### New Tests
```
mvn test -Dtest=CommitDiffEvidenceCollectorTest
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 → ALL PASS
```

### Full Test Suite
```
mvn test -q
Tests run: 223, Failures: 4, Errors: 2, Skipped: 0
```
All 6 failures are pre-existing (confirmed: RestAIEngineClientIntegrationTest, AnalysisWorkflowServiceTest, InitialCollectorsTest, DevlogAiBackendApplicationTests, ValidationControllerWebMvcTest). No new failures introduced.

## Observations

1. **Package path correction:** The task plan referenced `ProjectCommitRepository` at `.../history/ProjectCommitRepository.java`, but the actual path is `.../history/repository/ProjectCommitRepository.java`. This was discovered during implementation and handled correctly.

2. **ContextRequest structure:** The plan referenced `request.projectId()` and `request.analysisDate()`, but the actual `ContextRequest` record doesn't have these methods directly. The collector uses `request.analysisContext().project().id()` and `request.analysisContext().analysis().startedAt()` following the pattern established by `GitHistoryContextCollector`.

3. **SourceType enum:** The plan referenced `SourceType.DETERMINISTIC_EXTRACTION`, but the `SourceType` enum only contains `GIT_REPOSITORY`. The collector correctly uses the string `"DETERMINISTIC_EXTRACTION"` as the source type in `EvidenceFactory.ContextRequestMetadata`, matching how other collectors specify their source type (e.g., `"GIT"`, `"REPOSITORY_STRUCTURE"`).

4. **EvidenceFactory pattern:** The collector uses `EvidenceFactory` for evidence creation, consistent with `GitHistoryContextCollector` and `RepositoryStructureCollector`, rather than constructing `RepositoryEvidence` directly.

5. **Sorting:** The collector sorts evidence by `occurredAt` descending, then by `(insertions + deletions)` descending, then by path ascending — matching the plan's requirements. The sort is performed on `FileGroup` objects before converting to `RepositoryEvidence`, since the evidence record doesn't expose raw insertion/deletion counts.

## ADR Compliance

- **ADR-035** (Historical Analysis Boundaries): ✅ Uses `DETERMINISTIC_EXTRACTION` source type
- **ADR-037** (Repository-First Context Extraction): ✅ Queries database, not filesystem
- **ADR-038** (Extensible Collectors): ✅ Implements `RepositoryContextCollector` interface
- **ADR-040** (Knowledge/Evidence Separation): ✅ Produces raw evidence items, not knowledge
- **ADR-041** (Deterministic Ranking): ✅ COMMIT_DIFF layer already handled by ranker
