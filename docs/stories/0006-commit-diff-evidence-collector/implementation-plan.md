# Implementation Plan — Story 0006

## Story
**ID**: 0006
**Title**: Commit Diff Evidence Collector
**Status**: Planning

---

## Implementation Steps

### Step 1: Add repository method for temporal query

**File**: `backend/src/main/java/com/hopeful117/devlogai/history/ProjectCommitRepository.java`

**Change**: Add `findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc` method.

```java
List<ProjectCommit> findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
    UUID projectId, Instant after
);
```

**Rationale**: JPA derives the query from the method name. This avoids loading the entire commit history and lets the database filter by temporal window efficiently.

**Effort**: 1 minute
**Tests**: No new tests needed (JPA-derived query, existing integration tests cover the repository)

---

### Step 2: Create CommitDiffEvidenceCollector

**File**: `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/CommitDiffEvidenceCollector.java`

**Order**: `@Order(35)` — after `GitHistoryContextCollector` (30) but before `RepositoryStructureCollector` (40).

**Constructor injection**:
- `ProjectCommitRepository projectCommitRepository`
- `@Value("${devlog.context.commit-diff.max-items:50}") int maxItems`
- `@Value("${devlog.context.commit-diff.window-days:90}") int windowDays`

**collect() method flow**:
1. Compute cutoff: `request.analysisDate().minus(Duration.ofDays(windowDays))`
2. Query commits: `projectCommitRepository.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(request.projectId(), cutoff)`
3. If empty → return `List.of()`
4. Flatten all `changedFiles` from commits
5. Group by normalized path (`file.getNewPath() != null ? file.getNewPath() : file.getOldPath()`)
6. For each group:
   - Skip if excluded (binary, generated/vendor path)
   - Build deduplicated evidence using most recent commit's metadata
   - Sum insertions/deletions across commits
   - Collect all commit hashes into `relatedReferences`
7. Sort by: recency (desc) → magnitude (desc) → path (asc)
8. Limit to `maxItems`
9. Return `List<RepositoryEvidence>`

**Evidence construction per file**:
```java
RepositoryEvidence.builder()
    .layer(RepositoryContextLayer.COMMIT_DIFF)
    .kind("CHANGED_FILE")
    .reference("diff:" + mostRecentCommit.getCommitHash() + ":" + path)
    .summary(formatSummary(changeType, path, totalInsertions, totalDeletions, commitCount))
    .originatingFile(path)
    .relatedReferences(commitHashes)  // all commits that touched this file
    .occurredAt(mostRecentCommit.getCommittedAt())
    .sourceType(SourceType.DETERMINISTIC_EXTRACTION)
    .collectorId("commit-diff")
    .collectorVersion("v1")
    .repositoryLocation(request.projectId())
    .build();
```

**Summary formatting**:
- Binary: `"Binary {path}"`
- ADDED: `"Added {path} (+{insertions})"`
- MODIFIED (single commit): `"Modified {path} (+{insertions}/-{deletions})"`
- MODIFIED (multiple commits): `"Modified {path} (+{totalInsertions}/-{totalDeletions}) in {commitCount} commits"`
- DELETED: `"Deleted {path} (-{deletions})"`
- RENAMED: `"Renamed {oldPath} → {newPath}"`

**Exclusion logic** (reuse from `CommitDiffContextBuilder`):
```java
private static final Set<String> GENERATED_SEGMENTS = Set.of(
    "node_modules", "vendor", "target", "build", "dist", "coverage", ".venv", "venv"
);

private boolean isExcluded(String path, boolean binary) {
    if (binary) return true;
    String normalized = "/" + path.toLowerCase(Locale.ROOT).replace('\\', '/') + "/";
    if (GENERATED_SEGMENTS.stream().anyMatch(s -> normalized.contains("/" + s + "/")))
        return true;
    if (normalized.endsWith(".min.js/") || normalized.endsWith(".map/"))
        return true;
    return false;
}
```

**Estimated lines**: ~180-220

---

### Step 3: Create CommitDiffEvidenceCollectorTest

**File**: `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/CommitDiffEvidenceCollectorTest.java`

**Test cases** (10-12 tests):

| # | Test | Description |
|---|------|-------------|
| 1 | `producesChangedFileEvidenceForRecentCommits` | Creates commit with MODIFIED file, verifies evidence produced |
| 2 | `excludesGeneratedAndVendorPaths` | Creates commit with `target/` and `node_modules/` paths, verifies excluded |
| 3 | `excludesBinaryFiles` | Creates commit with binary file, verifies excluded |
| 4 | `excludesMinJsAndMapFiles` | Creates commit with `.min.js` and `.map` files, verifies excluded |
| 5 | `deduplicatesFilesAcrossMultipleCommits` | Creates 2 commits modifying same file, verifies ONE evidence with summed metrics |
| 6 | `usesMostRecentCommitMetadata` | Creates 2 commits for same file, verifies most recent commit's hash/date in evidence |
| 7 | `filtersOldCommits` | Creates commit outside window, verifies not included |
| 8 | `formatsAddedFileSummary` | Verifies summary format for ADDED files |
| 9 | `formatsDeletedFileSummary` | Verifies summary format for DELETED files |
| 10 | `formatsRenamedFileSummary` | Verifies summary format for RENAMED files |
| 11 | `respectsMaxItemsLimit` | Creates 60 files, verifies only 50 returned |
| 12 | `returnsEmptyListWhenNoCommits` | No commits → empty list |

**Test setup**: Uses `@ExtendWith(MockitoExtension.class)` with mocked `ProjectCommitRepository`. Creates `ProjectCommit` + `ChangedFile` entities in test setup methods.

**Estimated lines**: ~200-250

---

### Step 4: Compile and test

```bash
cd backend && mvn compile -q
cd backend && mvn test -pl . -Dtest=CommitDiffEvidenceCollectorTest
cd backend && mvn test -q
```

---

## Files Summary

| Action | File | Est. Lines |
|--------|------|------------|
| **Modify** | `backend/.../history/ProjectCommitRepository.java` | +1 |
| **Create** | `backend/.../repositorycontext/collector/CommitDiffEvidenceCollector.java` | ~200 |
| **Create** | `backend/.../repositorycontext/collector/CommitDiffEvidenceCollectorTest.java` | ~230 |

**Total new code**: ~430 lines across 2 new files, 1 modified file.

---

## Validation Criteria

After implementation:

1. `mvn compile -q` → BUILD SUCCESS
2. `CommitDiffEvidenceCollectorTest` → all tests pass
3. `mvn test -q` → no new failures (pre-existing 6 failures remain unchanged)
4. No modifications to existing interfaces, collectors, ranker, or profiles
5. Collector produces correct evidence for: MODIFIED, ADDED, DELETED, RENAMED files
6. Generated/vendor paths excluded
7. Binary files excluded
8. Files changed in multiple commits are deduplicated
9. Collector limit enforced

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| N+1 queries for changedFiles | Accept for V1; collector limit bounds impact. Optimize in future story if needed. |
| Deduplication complexity | Simple grouping by path. Most recent commit metadata wins. Sum metrics. |
| Repository method | JPA derives query automatically from method name. No schema change. |
