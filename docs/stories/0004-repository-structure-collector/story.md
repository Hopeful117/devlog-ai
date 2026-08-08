# Story 0004 — Add Repository Structure Collector

## Metadata

**ID:**
`0004`

**Title:**
Add Repository Structure Collector to the Repository Context Engine

**Status:**
Draft

**Created:**
2026-08-08

**Author:**
Kiko (OpenClaw)

---

## Objective

Add a new `RepositoryContextCollector` that scans the project's filesystem and produces `RELATED_SOURCE_CODE` evidence about the repository's file structure — source directories, test directories, configuration files, modules, and file inventory by extension — so that Kiko receives repository structure information when preparing an Engineering Story.

---

## Motivation

After Stories 0001–0003, `EngineeringStoryContext` provides ranked evidence about commits, decisions, milestones, insights, and architecture artifacts. However, it provides **zero information about the actual repository structure** — what files exist, what modules are present, where source and test code lives.

When Kiko prepares an Engineering Story, it cannot identify which modules, packages, or files might be impacted. The system ranks commits and decisions but is blind to the codebase itself.

The `collection/collector` package already has comprehensive deterministic capabilities for repository scanning (`SecureRepositoryScanner`, `RepositoryMetadataCollector`, `TestStructureCollector`, `BuildCollector`), but these produce `Fact` entities stored in the database during the collection pipeline — they are not available as `RepositoryEvidence` in the repository context pipeline.

This story bridges that gap by creating a new `RepositoryContextCollector` that uses the existing scanning infrastructure to produce `RELATED_SOURCE_CODE` evidence.

---

## Scope

### In Scope

- New `RepositoryStructureCollector` implementing `RepositoryContextCollector`
- Uses `SecureRepositoryScanner` to scan the project's workspace
- Produces `RepositoryEvidence` items in the `RELATED_SOURCE_CODE` layer:
  - Module summary (multi-module structure, module names)
  - Source directory inventory (src/main/java, src/main/python, etc.)
  - Test directory inventory (src/test/java, etc.)
  - Configuration file inventory (pom.xml, build.gradle, application.properties, etc.)
  - File extension distribution (top extensions by count)
- Adapter resolves workspace path from `SourceRepository` + `WorkspaceManager`
- Unit tests for the collector

### Out of Scope

- AST parsing or symbol discovery
- File content analysis or semantic interpretation
- Story-to-file mapping
- Module dependency graph
- Source code evidence beyond structure (classes, methods, symbols)
- Modifications to existing collectors
- Modifications to `RepositoryContextEngine`
- Frontend
- Database migrations

---

## Facts / Evidence

### Established (code exists)

- `SecureRepositoryScanner.scan(CollectionContext, Predicate)` scans the filesystem and returns `RepositoryScan` with `files` (list of `RepositoryFile`), `directoryCount`, `visitedFileCount`, `warnings`.
- `RepositoryFile` record: `relativePath`, `sizeBytes`, `content` (nullable — only when `includeContent` predicate returns true).
- `RepositoryMetadataCollector` already classifies files: source directories, configuration files, extensions, module structure. Logic is in `collect()` method.
- `TestStructureCollector` detects test directories, test files, test frameworks.
- `BuildCollector` parses `pom.xml` and `build.gradle` for modules, dependencies, versions.
- `DocumentationCollector` inventories ADRs, README, documentation directories.
- `CollectionContext` requires: `analysisId`, `sourceId`, `projectId`, `workspacePath`, `resolvedRevision`, `sourceType`, `collectionTimestamp`.
- `WorkspaceManager.synchronize(Source, revision)` returns `SynchronizedWorkspace` with `path` and `resolvedRevision`.
- `SourceRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)` returns sources for a project.
- `GitWorkspaceManager` manages workspace clone/fetch/checkout with locking.
- `CollectorLimits` provides: `maxFiles` (10,000), `excludedDirectories` (.git, target, build, node_modules, etc.).
- The `RELATED_SOURCE_CODE` layer already exists in `RepositoryContextLayer`.
- The `engineering-story-v1` profile does NOT currently list `RELATED_SOURCE_CODE` as a preferred layer — this will need to be added.

### Design Constraints

- The collector must work within the `RepositoryContextEngine` pipeline — it implements `RepositoryContextCollector` and is auto-detected by Spring.
- The collector needs filesystem access. The adapter must resolve the workspace path from the `Source` entity.
- `WorkspaceManager.synchronize()` performs git operations (fetch, checkout). This is the intended behavior for maintaining a local workspace, but adds latency to the adapter call. The workspace is already maintained by the collection pipeline, so it should be available.
- The `CollectionContext` requires a `resolvedRevision`. The adapter can use the workspace's `resolvedRevision` from `SynchronizedWorkspace`.
- The collector should be bounded by `CollectorLimits` (max files, excluded directories, timeout).
- The collector should NOT read file content — only file paths, sizes, and extension classification. The `includeContent` predicate should return `false`.

---

## Acceptance Criteria

### AC-1: `RepositoryStructureCollector` exists and is a Spring `@Component`

**Evidence:** `RepositoryStructureCollector` is annotated `@Component` and implements `RepositoryContextCollector`. It is injected with `SecureRepositoryScanner` and `CollectorLimits`.

### AC-2: Collector produces `RELATED_SOURCE_CODE` evidence

**Evidence:** `collect(ContextRequest)` returns `List<RepositoryEvidence>` where every item has `layer() == RepositoryContextLayer.RELATED_SOURCE_CODE`.

### AC-3: Collector produces module summary evidence

**Evidence:** At least one evidence item with `kind = "MODULE_SUMMARY"` and summary containing the number of modules detected. When the repository has multiple `pom.xml` or `build.gradle` files in subdirectories, the summary reflects this.

### AC-4: Collector produces source directory inventory evidence

**Evidence:** At least one evidence item with `kind = "SOURCE_DIRECTORIES"` and summary listing detected source directories (e.g., `src/main/java`, `src/main/python`).

### AC-5: Collector produces test directory inventory evidence

**Evidence:** At least one evidence item with `kind = "TEST_DIRECTORIES"` and summary listing detected test directories (e.g., `src/test/java`).

### AC-6: Collector produces configuration file inventory evidence

**Evidence:** At least one evidence item with `kind = "CONFIGURATION_FILES"` and summary listing detected configuration files (e.g., `pom.xml`, `application.properties`, `build.gradle`).

### AC-7: Collector produces file extension distribution evidence

**Evidence:** At least one evidence item with `kind = "FILE_EXTENSIONS"` and summary listing the top file extensions by count (e.g., `java=361, ts=65, py=103`).

### AC-8: Collector is bounded by `CollectorLimits`

**Evidence:** The scanner uses `CollectorLimits` for `maxFiles`, `excludedDirectories`, and `collectorTimeout`. When limits are exceeded, evidence reflects the truncation.

### AC-9: Adapter resolves workspace path

**Evidence:** `RepositoryContextAdapter` resolves the `Source` from `SourceRepository`, calls `WorkspaceManager.synchronize()`, and creates a `CollectionContext` with the workspace path for the collector.

### AC-10: `engineering-story-v1` profile includes `RELATED_SOURCE_CODE`

**Evidence:** `DeterministicContextIntelligence` registers `engineering-story-v1` with `RELATED_SOURCE_CODE` in its preferred layers list.

### AC-11: Existing Analysis flow is unchanged

**Evidence:** No modifications to `RepositoryContextEngine`, `KnowledgeSelectionServiceImpl`, `AnalysisContextServiceImpl`, `IntentCatalog`, or any existing collector.

### AC-12: No `Analysis` is persisted

**Evidence:** The adapter's workspace resolution does not create, persist, or require a database `Analysis` entity.

### AC-13: Tests pass

**Evidence:** New unit tests for `RepositoryStructureCollector` pass. Existing tests unaffected.

---

## Impacted Components

### New Files

| File | Type | Package |
|---|---|---|
| `RepositoryStructureCollector.java` | Collector | `repositorycontext.collector` |
| `RepositoryStructureCollectorTest.java` | Test | `repositorycontext.collector` |

### Modified Files

| File | Nature of Modification |
|---|---|
| `RepositoryContextAdapter.java` | Inject `SourceRepository`, `WorkspaceManager`. Resolve workspace path before calling engine. |
| `DeterministicContextIntelligence.java` | Add `RELATED_SOURCE_CODE` to `engineering-story-v1` preferred layers. |

### Unchanged Files

| File | Reason |
|---|---|
| `RepositoryContextEngine.java` | Auto-detects new collector via Spring `List<RepositoryContextCollector>` |
| `KnowledgeSelectionServiceImpl.java` | Bypassed by adapter |
| `AnalysisContextServiceImpl.java` | Existing Analysis flow untouched |
| All existing collectors | No modifications |
| `DeterministicEvidenceRanker.java` | No modifications |
| `BudgetedDiverseEvidenceSelector.java` | No modifications |

---

## Risks

### Risk-1: Workspace synchronization adds latency

**Impact:** Medium. `WorkspaceManager.synchronize()` performs git fetch and checkout. For an on-demand REST endpoint, this adds latency.

**Mitigation:** The workspace is already maintained by the collection pipeline. If the workspace exists and is current, `synchronize()` is fast (fetch + checkout). For V1, this is acceptable. Future optimization: cache the workspace path or skip synchronization if the workspace is recent.

### Risk-2: Workspace may not exist for projects without imported history

**Impact:** Medium. If a project has no `Source` entity or the workspace has never been synchronized, the collector cannot scan.

**Mitigation:** The adapter gracefully handles missing sources (returns `repositoryContext` with no structure evidence). The collector produces an empty evidence list when the workspace is unavailable.

### Risk-3: `CollectionContext` requires `analysisId`

**Impact:** Low. The adapter uses the same synthetic analysis UUID as Story 0003. This is consistent and deterministic.

**Mitigation:** Already solved in Story 0003's adapter pattern.

### Risk-4: Large repositories may hit `CollectorLimits`

**Impact:** Low. The scanner is bounded by `maxFiles` (10,000), `maxFileSize`, `maxTotalBytes`, and `collectorTimeout`. Evidence is truncated gracefully.

**Mitigation:** The collector reports truncation warnings in evidence metadata. The budget selector handles remaining evidence naturally.

---

## Definition of Done

- [ ] Story document created
- [ ] Repository Analysis approved
- [ ] Implementation Plan approved
- [ ] Implementation completed
- [ ] `RepositoryStructureCollector` produces structure evidence
- [ ] Adapter resolves workspace path
- [ ] Profile updated with `RELATED_SOURCE_CODE`
- [ ] Unit tests pass
- [ ] `mvn compile` succeeds
- [ ] `mvn test` — no regressions
- [ ] Code Review approved
- [ ] Engineering Report completed

---

## Dependencies

- **Story 0001** (completed): `ProjectContextProvider` — provides project-scoped context
- **Story 0002** (completed): `EngineeringStoryContext` — provides the endpoint and record
- **Story 0003** (completed): `RepositoryContextAdapter` — the adapter to extend
- **`SecureRepositoryScanner`** (existing): Filesystem scanning
- **`WorkspaceManager`** (existing): Workspace synchronization
- **`SourceRepository`** (existing): Source entity lookup
- **`CollectorLimits`** (existing): Bounded scanning configuration

---

*Story created: 2026-08-08*
*Author: Kiko (OpenClaw)*
