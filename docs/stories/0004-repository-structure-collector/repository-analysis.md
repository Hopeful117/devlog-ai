# Repository Analysis

## Story Understanding

Story 0004 requests adding a `RepositoryStructureCollector` that scans the project's filesystem and produces `RELATED_SOURCE_CODE` evidence about the repository's file structure — modules, source directories, test directories, configuration files, and file extension distribution. This gives Kiko awareness of the actual codebase when preparing an Engineering Story.

The existing `collection/collector` package has comprehensive scanning capabilities (`SecureRepositoryScanner`, `RepositoryMetadataCollector`, `TestStructureCollector`, `BuildCollector`) but these produce `Fact` entities in the database pipeline, not `RepositoryEvidence` in the context engine. This story bridges that gap.

Explicit scope: new collector, adapter workspace resolution, profile update, unit tests.
Explicit exclusions: AST parsing, file content analysis, story-to-file mapping, module dependency graphs, modifications to existing collectors or engine.

---

## Repository Summary

The repository context subsystem (`repositorycontext`) implements an evidence pipeline: collection → ranking → selection → digest. Four collectors currently produce evidence:
- `CurrentAnalysisContextCollector` — 1 item (synthetic analysis)
- `DeterministicKnowledgeContextCollector` — facts/observations (empty for story prep)
- `GitHistoryContextCollector` — git commits from database
- `ProjectKnowledgeContextCollector` — decisions, milestones, insights, artifacts

The collection subsystem (`collection/collector`) has filesystem scanning:
- `SecureRepositoryScanner` — bounded filesystem walker
- `RepositoryMetadataCollector` — file classification (source dirs, config, extensions)
- `TestStructureCollector` — test file identification
- `BuildCollector` — build descriptor parsing (modules, dependencies)
- `DocumentationCollector` — documentation inventory

Workspace access:
- `WorkspaceManager.synchronize(Source, revision)` → `SynchronizedWorkspace(path, resolvedRevision)`
- `SourceRepository.findByProjectId(projectId)` → list of `Source` entities
- `GitWorkspaceManager` — manages clone/fetch/checkout with locking

---

## Affected Modules

### 1. `repositorycontext.collector` — New Collector

**Package:** `com.hopeful117.devlogai.repositorycontext.collector`

**Why involved:** New `RepositoryStructureCollector` implements `RepositoryContextCollector`.

**Current responsibility:** 4 collectors produce evidence from database entities.

**Impact:** One new collector added. No existing collectors modified.

### 2. `projectcontext` — Adapter Extension

**Package:** `com.hopeful117.devlogai.projectcontext`

**Why involved:** `RepositoryContextAdapter` must resolve workspace path for the collector.

**Current responsibility:** Bridges `ProjectContextProvider` → `RepositoryContextEngine`.

**Impact:** Adapter injects `SourceRepository` and `WorkspaceManager`. Resolves workspace before calling engine.

### 3. `repositorycontext.intelligence` — Profile Update

**Package:** `com.hopeful117.devlogai.repositorycontext.intelligence`

**Why involved:** `engineering-story-v1` profile needs `RELATED_SOURCE_CODE` in preferred layers.

**Impact:** One line change in profile registration.

### 4. `collection.collector` — Reused (no modifications)

**Package:** `com.hopeful117.devlogai.collection.collector`

**Why involved:** `SecureRepositoryScanner`, `CollectorLimits`, `CollectionContext`, `RepositoryScan`, `RepositoryFile` are reused.

**Impact:** None — consumed as-is.

---

## Existing Implementation

### `SecureRepositoryScanner`

Scans the workspace filesystem. Returns `RepositoryScan` with:
- `files`: `List<RepositoryFile>` (relativePath, sizeBytes, content)
- `directoryCount`: int
- `visitedFileCount`: int
- `warnings`: `List<CollectionWarning>`

Bounded by `CollectorLimits`: maxFiles (10,000), excludedDirectories (.git, target, build, node_modules, etc.), collectorTimeout (10s), maxFileSize, maxTotalBytes.

The scanner accepts a `Predicate<String> includeContent` — when false, file content is not read (only path and size).

### `RepositoryMetadataCollector`

Already classifies files during the collection pipeline:
- Source directories: `src/main/java`, `src/main/kotlin`, `src/main/python`, `src`, `app`, `lib`
- Configuration files: `application.properties`, `application.yml`, `pom.xml`, `build.gradle`, `package.json`, etc.
- File extension distribution (top 10 by count)
- Multi-module structure (nested `pom.xml`/`build.gradle` count)

This logic can be directly reused in the new collector.

### `TestStructureCollector`

Detects:
- Test source directories
- Test files
- Integration test files
- Test frameworks (JUnit, Testcontainers)
- Test resource directories

### `BuildCollector`

Parses build descriptors:
- Build system (Maven, Gradle)
- Modules
- Dependencies
- Java version
- Project version

### `CollectionContext`

Record requiring: `analysisId`, `sourceId`, `projectId`, `workspacePath`, `resolvedRevision`, `sourceType`, `collectionTimestamp`.

The adapter can construct this from:
- `analysisId` = synthetic UUID (from Story 0003 adapter)
- `sourceId` = from `Source` entity
- `projectId` = available
- `workspacePath` = from `WorkspaceManager.synchronize()`
- `resolvedRevision` = from `SynchronizedWorkspace.resolvedRevision()`
- `sourceType` = `SourceType.GIT_REPOSITORY`
- `collectionTimestamp` = `Instant.now()`

### `RepositoryContextCollector` Interface

```java
public interface RepositoryContextCollector {
    String collectorId();
    String collectorVersion();
    List<RepositoryEvidence> collect(ContextRequest request);
}
```

The collector receives a `ContextRequest` with `analysisContext`, `intent`, `guidance`, `validatedInsights`, `contextPlan`, `budget`.

### `ContextRequest`

Does NOT currently include workspace path. The collector needs filesystem access, which requires resolving the workspace from the project ID.

### `EvidenceFactory`

Creates `RepositoryEvidence` items with bounded summaries and estimated tokens. Used by all existing collectors.

### `engineering-story-v1` Profile

Currently preferred layers: `GIT_HISTORY`, `COMMIT_DIFF`, `ADR`, `PROJECT_DOCUMENTATION`, `ROADMAP`.
Does NOT include `RELATED_SOURCE_CODE`.

---

## Relevant Documentation

- `docs/decisions/ADR-037.md` — Repository-First Context Extraction
- `docs/decisions/ADR-038.md` — Repository Context Engine
- `docs/decisions/ADR-039.md` — Context Intelligence
- `docs/decisions/ADR-040.md` — Knowledge and Evidence Separation
- `docs/stories/0003-enable-repository-context-for-story-preparation/story.md` — Adapter pattern
- `docs/architecture.md` — Core principles

---

## Constraints

1. **ADR-037 (Repository-First):** Repository context must be assembled through the Repository Context Engine pipeline. The new collector follows this pattern.

2. **ADR-038 (Extensible Collectors):** New collectors implement `RepositoryContextCollector` and are auto-detected by Spring.

3. **ADR-040 (Knowledge/Evidence Separation):** Facts/observations are analysis-scoped. The new collector produces `RepositoryEvidence`, not `Fact` entities.

4. **`CollectorLimits` bounds:** The scanner must respect maxFiles, excludedDirectories, and timeout.

5. **No file content reading:** The collector scans paths and sizes only — `includeContent` predicate returns `false`.

6. **Workspace access:** The adapter must resolve the workspace path. This requires `SourceRepository` and `WorkspaceManager`.

7. **Backward compatibility:** Existing collectors and profiles unchanged.

---

## Risks

### Risk-1: Workspace synchronization adds latency

`WorkspaceManager.synchronize()` performs git fetch and checkout. For an on-demand REST endpoint, this adds latency (potentially seconds for large repositories). However, the workspace is already maintained by the collection pipeline — if the workspace exists and is current, synchronization is fast.

**Mitigation:** For V1, accept the latency. The adapter can log synchronization duration. Future optimization: cache workspace path, skip sync if workspace is recent.

### Risk-2: Workspace may not exist

If a project has no `Source` entity or the workspace has never been synchronized, the collector cannot scan. The adapter must handle this gracefully.

**Mitigation:** Adapter returns `repositoryContext` with no structure evidence when workspace is unavailable. Collector produces empty evidence list.

### Risk-3: `ContextRequest` does not include workspace path

The collector needs filesystem access but `ContextRequest` only provides `AnalysisContext` (project ID, not workspace path). Two approaches:
- (a) Collector injects `SourceRepository` + `WorkspaceManager` and resolves workspace itself
- (b) Adapter resolves workspace and passes path through a modified `ContextRequest`

Approach (a) is cleaner — the collector is self-contained. Approach (b) requires modifying `ContextRequest` (invasive).

**Recommendation:** Approach (a) — collector injects workspace dependencies directly.

### Risk-4: Duplicate workspace resolution

The adapter already calls `ProjectContextProvider` which queries project data. Adding workspace resolution means the adapter also queries `SourceRepository` and calls `WorkspaceManager`. This is additional I/O.

**Mitigation:** Acceptable for V1. The workspace resolution is a single `SourceRepository` query + workspace path resolution.

---

## Open Questions

None. The implementation path is clear. The collector uses existing scanning infrastructure. The adapter resolves workspace access. The profile is updated.

---

## Recommendation

**Ready for planning**

The repository is well understood. The new collector reuses existing, tested scanning infrastructure. The workspace access pattern is established by the collection pipeline. No architectural conflicts.

---

## Implementation Readiness

The Story can be implemented:

- `SecureRepositoryScanner` is fully functional and bounded
- `RepositoryMetadataCollector` classification logic can be extracted/reused
- `TestStructureCollector` test detection logic can be reused
- `BuildCollector` module detection logic can be reused
- `SourceRepository` and `WorkspaceManager` are available
- `RepositoryContextCollector` interface is simple and well-defined
- `EvidenceFactory` handles evidence creation and bounding
- `RELATED_SOURCE_CODE` layer already exists

No missing contracts, no missing architecture, no blocking ADR conflicts.

---

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
