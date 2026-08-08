# Implementation Plan

## Overview

Add a `RepositoryStructureCollector` that scans the project's filesystem and produces `RELATED_SOURCE_CODE` evidence about the repository's file structure. The collector uses `SecureRepositoryScanner` with `includeContent=false` to discover files without reading content, then classifies them into module summary, source directories, test directories, configuration files, and file extension distribution. The collector injects `SourceRepository` and `WorkspaceManager` directly to resolve the workspace path.

The `engineering-story-v1` profile is updated to include `RELATED_SOURCE_CODE` in preferred layers.

---

## Planned Changes

### Step 1: Create `RepositoryStructureCollector`

**Component:** `com.hopeful117.devlogai.repositorycontext.collector`

**Change:** New class implementing `RepositoryContextCollector`.

**What it does:**
- Injects `SecureRepositoryScanner`, `CollectorLimits`, `SourceRepository`, `WorkspaceManager`
- `collect(ContextRequest)`: resolves project ID from `request.analysisContext().project().id()`
- Queries `SourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId)` to find the first active source
- If no source found, returns empty list with a warning
- Calls `WorkspaceManager.synchronize(source, null)` to get `SynchronizedWorkspace`
- Constructs `CollectionContext` with workspace path
- Calls `scanner.scan(collectionContext, path -> false)` — no file content reading
- Analyzes the `RepositoryScan`:
  - **Module summary**: count directories containing `pom.xml` or `build.gradle` (excluding root). If >1, multi-module.
  - **Source directories**: scan `RepositoryFile.relativePath` for common source roots: `src/main/java`, `src/main/kotlin`, `src/main/python`, `src/main/typescript`, `src/app`, `src/lib`
  - **Test directories**: paths containing `src/test/`, `__tests__/`, `test/`, `tests/`
  - **Configuration files**: filenames matching `pom.xml`, `build.gradle`, `build.gradle.kts`, `application.properties`, `application.yml`, `application.yaml`, `package.json`, `tsconfig.json`, `pyproject.toml`, `requirements.txt`, `Dockerfile`, `docker-compose.yml`, `.gitignore`
  - **File extensions**: count by extension from `relativePath`, sort descending, take top 10
- Returns up to 5 `RepositoryEvidence` items, each with `layer = RELATED_SOURCE_CODE`

**Constraints:**
- Uses `EvidenceFactory` for evidence creation
- Bounded by `CollectorLimits` (maxFiles, excludedDirectories, timeout)
- No file content reading
- Returns empty list on workspace unavailability (no exception propagation)

**Dependencies injected:** `SecureRepositoryScanner`, `CollectorLimits`, `SourceRepository`, `WorkspaceManager`

### Step 2: Create `RepositoryStructureCollectorTest`

**Component:** `com.hopeful117.devlogai.repositorycontext.collector`

**Change:** New test class with 5 unit tests.

**Tests:**
1. `producesRelatedSourceCodeLayer` — verifies all evidence items have `layer() == RELATED_SOURCE_CODE`
2. `producesModuleSummaryEvidence` — verifies module count is detected
3. `producesSourceDirectoryEvidence` — verifies source directories are listed
4. `returnsEmptyListWhenNoSource` — verifies graceful handling when no active source exists
5. `returnsEmptyListWhenWorkspaceUnavailable` — verifies graceful handling when workspace sync fails

### Step 3: Update `RepositoryContextAdapter`

**Component:** `com.hopeful117.devlogai.projectcontext`

**Change:** Minimal — no changes needed.

**Reason:** The collector resolves workspace access independently via injected `SourceRepository` + `WorkspaceManager`. The adapter does not need to be modified for V1. This keeps the adapter thin and the collector self-contained.

**Note:** If workspace synchronization proves too slow for the REST endpoint in practice, a future story can optimize by caching or pre-resolving the workspace path in the adapter.

### Step 4: Update `engineering-story-v1` Profile

**Component:** `com.hopeful117.devlogai.repositorycontext.intelligence.DeterministicContextIntelligence`

**Change:** Add `RepositoryContextLayer.RELATED_SOURCE_CODE` to the preferred layers list for `engineering-story-v1`.

**Current:** `GIT_HISTORY`, `COMMIT_DIFF`, `ADR`, `PROJECT_DOCUMENTATION`, `ROADMAP`

**New:** `RELATED_SOURCE_CODE`, `GIT_HISTORY`, `COMMIT_DIFF`, `ADR`, `PROJECT_DOCUMENTATION`, `ROADMAP`

`RELATED_SOURCE_CODE` is listed first because repository structure is the most fundamental context for story preparation — it tells Kiko what files exist.

### Step 5: Compile and Test

**Commands:**
- `mvn compile -pl backend` — verify compilation
- `mvn test -pl backend -Dtest=RepositoryStructureCollectorTest` — new tests
- `mvn test -pl backend` — full test suite, check for regressions

---

## Files to Modify

| File | Nature of Modification |
|---|---|
| `DeterministicContextIntelligence.java` | Add `RELATED_SOURCE_CODE` to `engineering-story-v1` preferred layers |

---

## Files to Create

| File | Purpose |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java` | New collector |
| `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java` | Unit tests |

---

## Dependencies

No new external dependencies.

Internal dependencies:
- `SecureRepositoryScanner` (existing)
- `CollectorLimits` (existing)
- `SourceRepository` (existing)
- `WorkspaceManager` (existing)
- `CollectionContext` (existing record)
- `RepositoryScan` / `RepositoryFile` (existing records)
- `EvidenceFactory` (existing)
- `ContextRequest` (existing)
- `RepositoryContextCollector` (existing interface)

All are Spring-managed beans or simple records. No wiring issues.

---

## Test Plan

### New Tests

| Test | Validates |
|---|---|
| `producesRelatedSourceCodeLayer` | AC-2: All evidence has `RELATED_SOURCE_CODE` layer |
| `producesModuleSummaryEvidence` | AC-3: Module count detected |
| `producesSourceDirectoryEvidence` | AC-4: Source directories listed |
| `returnsEmptyListWhenNoSource` | AC-9 / Risk-2: Graceful handling |
| `returnsEmptyListWhenWorkspaceUnavailable` | Risk-2: Graceful handling |

### Existing Tests

No existing tests require modification. The collector is a new Spring component — existing collectors are unchanged.

### Validation Commands

```bash
cd /home/ludo/Bureau/workspace/devlog-ai
mvn compile -pl backend
mvn test -pl backend -Dtest=RepositoryStructureCollectorTest
mvn test -pl backend
```

---

## Risks

### Risk-1: Workspace synchronization latency

`WorkspaceManager.synchronize()` performs git fetch/checkout. For an on-demand REST endpoint, this adds latency.

**Mitigation:** The workspace is already maintained by the collection pipeline. If current, synchronization is fast. For V1, accept the latency. The collector logs warnings if synchronization is slow.

### Risk-2: Workspace may not exist

No `Source` entity or never-synchronized workspace → collector cannot scan.

**Mitigation:** Collector returns empty evidence list. Adapter returns `repositoryContext` with structure evidence absent. No exception propagation.

### Risk-3: `ContextRequest` does not include workspace path

**Mitigation:** Collector injects `SourceRepository` + `WorkspaceManager` directly (approach (a) from Repository Analysis). Self-contained, no interface changes.

---

## Validation Checklist

- [ ] `RepositoryStructureCollector.java` exists and implements `RepositoryContextCollector`
- [ ] `RepositoryStructureCollector` is annotated `@Component`
- [ ] `collect()` returns `List<RepositoryEvidence>` with all items having `layer == RELATED_SOURCE_CODE`
- [ ] Evidence includes MODULE_SUMMARY, SOURCE_DIRECTORIES, TEST_DIRECTORIES, CONFIGURATION_FILES, FILE_EXTENSIONS kinds
- [ ] No file content is read (`includeContent` predicate returns `false`)
- [ ] Collector returns empty list gracefully when workspace unavailable
- [ ] `DeterministicContextIntelligence` includes `RELATED_SOURCE_CODE` in `engineering-story-v1` preferred layers
- [ ] `RepositoryStructureCollectorTest` exists with 5 tests
- [ ] All new tests pass
- [ ] `mvn compile -pl backend` succeeds
- [ ] `mvn test -pl backend` — no regressions
- [ ] No modifications to `RepositoryContextEngine`, `KnowledgeSelectionServiceImpl`, existing collectors
- [ ] No database migrations
- [ ] AC-1 through AC-13 satisfied

---

## Recommendation

**Ready for implementation**

The implementation strategy is straightforward:
- One new collector class using existing scanning infrastructure
- One profile line change
- No interface modifications
- No database changes
- All dependencies exist

No blocking ambiguity.

---

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
