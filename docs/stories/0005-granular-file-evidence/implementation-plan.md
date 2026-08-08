# Implementation Plan

## Story ID
0005

## Story Title
Add file-level RELATED_SOURCE_CODE evidence to the Repository Context Engine

## Planning Date
2026-08-08

## Reference
- Repository Analysis: `docs/stories/0005-granular-file-evidence/repository-analysis.md`
- Story: `docs/stories/0005-granular-file-evidence/story.md`

---

## Implementation Strategy

The strategy is to **extend `RepositoryStructureCollector`** to produce file-level evidence items alongside the existing aggregate summaries. The collector already calls `scanner.scan()` and receives individual `RepositoryFile` entries with `relativePath` and `size`. The work is transforming these individual files into `RepositoryEvidence` items with `originatingFile = relativePath` and appropriate kind references (`SOURCE_FILE`, `TEST_FILE`, `CONFIG_FILE`).

No new classes. No interface changes. No new dependencies. One collector extended.

---

## Step 1: Add File Classification Methods

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`

Add private helper methods to classify files:

- `isSourceFile(String relativePath)` — checks if path ends in a known source root and recognized source extension (`.java`, `.kt`, `.py`, `.ts`, `.tsx`, `.js`, `.jsx`)
- `isTestFile(String relativePath)` — checks if path contains a known test root and recognized source extension
- `isConfigFile(String relativePath)` — checks if filename is in `CONFIGURATION_FILE_NAMES` set
- `extractModule(String relativePath)` — extracts module name from path (first segment before a known source root or build descriptor)

**Rationale:** These methods determine what kind of evidence each `RepositoryFile` produces. They use existing constants (`SOURCE_ROOTS`, `TEST_ROOTS`, `CONFIGURATION_FILE_NAMES`, `MODULE_BUILD_FILES`).

**Validation:** Existing tests cover aggregate evidence which is preserved. New tests will cover each classification method.

---

## Step 2: Add File-Level Evidence Production

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`

Add a new private method `produceFileLevelEvidence(RepositoryScan scan, String sourceId, ContextRequest request)` that:

1. Iterates over `scan.files()`
2. For each `RepositoryFile`, classifies it using the helpers from Step 1
3. Creates `RepositoryEvidence` items:
   - `SOURCE_FILE` for source files: `reference = "file:{relativePath}"`, `summary = relativePath`, `originatingFile = relativePath`
   - `TEST_FILE` for test files: same pattern
   - `CONFIG_FILE` for configuration files: `reference = "config:{relativePath}"`, `summary = relativePath`, `originatingFile = relativePath`
   - `MODULE` for inferred modules: `reference = "module:{moduleName}"`, `summary = "Module: {moduleName} ({modulePath}) — {fileCount} files"`

4. Story-term prioritization:
   - Extract terms from `request.intent().objective()` (normalized, ≥3 chars)
   - Score each file by number of path segments matching any term
   - Sort: files with matches first, then by match count descending, then alphabetically

5. Return list (capped at max file items — default 40)

**Rationale:** This is the core change. It transforms individual `RepositoryFile` entries into the `RepositoryEvidence` format that the existing ranker understands. The `originatingFile = relativePath` enables `DeterministicEvidenceRanker.semanticRelevance()` to match story terms against file paths.

---

## Step 3: Update `collect()` Method

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`

Modify `collect()` to:

1. Produce aggregate evidence (existing logic — unchanged)
2. Produce file-level evidence (from Step 2)
3. Combine: aggregate first, then file-level items
4. Return combined list (no `.limit(5)` — remove this restriction)

**Before:**
```java
return List.copyOf(evidence.stream().limit(5).toList());
```

**After:**
```java
// Aggregate evidence (5 items) + file-level evidence (up to 40 items)
return List.copyOf(evidence);
```

**Rationale:** The existing `.limit(5)` is the bottleneck. Removing it allows file-level evidence through. The collector limit (40) and budget selector (60/6000) handle final budget enforcement.

---

## Step 4: Add Collector Limit Constant

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`

Add constant:
```java
private static final int MAX_FILE_EVIDENCE_ITEMS = 40;
```

**Rationale:** Prevents unbounded file evidence. Configurable via constant (could be externalized to `CollectorLimits` later). The `BudgetedDiverseEvidenceSelector` handles final budget enforcement, but the collector limits initial output to avoid overwhelming the pipeline.

---

## Step 5: Update Tests

**File:** `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java`

Update existing tests:

1. `producesRelatedSourceCodeLayer()` — now expects more than 5 items (aggregate + file-level). Change assertion to check that at least one item has `RELATED_SOURCE_CODE` layer (already does this).

2. `producesModuleSummaryEvidence()` — verify MODULE_SUMMARY is still present and correct.

3. `producesSourceDirectoryEvidence()` — verify SOURCE_DIRECTORIES is still present and correct.

4. `returnsEmptyListWhenNoSource()` — unchanged.

5. `returnsEmptyListWhenWorkspaceUnavailable()` — unchanged.

Add new tests:

6. `producesSourceFileEvidenceForSourceFiles()` — verify `SOURCE_FILE` evidence items are produced for files under source roots.

7. `producesTestFileEvidenceForTestFiles()` — verify `TEST_FILE` evidence items are produced for files under test roots.

8. `producesConfigFileEvidenceForConfigFiles()` — verify `CONFIG_FILE` evidence items are produced for configuration files.

9. `producesModuleEvidenceForMultiModuleRepos()` — verify `MODULE` evidence with module names.

10. `limitsFileEvidenceItems()` — verify that file evidence is bounded by `MAX_FILE_EVIDENCE_ITEMS`.

11. `prioritizesFilesMatchingStoryTerms()` — verify that files matching story description terms appear first.

**Rationale:** Comprehensive test coverage for new behavior. Existing tests preserved.

---

## Step 6: Compile and Verify

**Command:**
```bash
cd /home/ludo/Bureau/workspace/devlog-ai/backend
mvn compile -q
mvn test -pl . -Dtest=RepositoryStructureCollectorTest
mvn test -q  # full suite
```

**Rationale:** Verify no compilation errors, no test regressions.

---

## Implementation Order

| Step | Files Modified | Risk |
|------|---------------|------|
| 1 | `RepositoryStructureCollector.java` | Low — private helpers |
| 2 | `RepositoryStructureCollector.java` | Medium — core logic |
| 3 | `RepositoryStructureCollector.java` | Low — limit removal |
| 4 | `RepositoryStructureCollector.java` | Low — constant |
| 5 | `RepositoryStructureCollectorTest.java` | Low — tests |
| 6 | None | Validation |

**Total files modified:** 2 (collector + test)

---

## Validation Requirements

- `mvn compile` succeeds
- `RepositoryStructureCollectorTest` — all 11 tests pass
- `mvn test` — full suite passes (no regressions)
- Verify aggregate evidence is still produced (MODULE_SUMMARY, SOURCE_DIRECTORIES, etc.)
- Verify file-level evidence has correct layer (`RELATED_SOURCE_CODE`), kind, reference, summary, originatingFile

---

## Rollback Strategy

Git revert. The change is contained to 2 files (collector + test). No database, no interface changes.

---

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| File count exceeds budget | Collector-level limit (40) + budget selector (60/6000) |
| Story-term matching too broad | Exact substring match only; prioritization by match count |
| Module inference inaccurate for non-standard layouts | Accept for V1; MODULE evidence includes full path for traceability |
| Existing tests need updating | Update assertions to check for presence of kinds, not exact counts |
| Performance with 500+ files | Single iteration through file list; O(n) classification |

---

## Out of Scope

- Content reading or AST parsing
- Changed-file → commit association
- File → module mapping beyond first-segment inference
- New ranking criteria or profiles
- Database migrations
- Frontend changes
