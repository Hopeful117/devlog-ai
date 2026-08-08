# Repository Analysis — Story 0006

## Story Summary

Story 0006 adds a `COMMIT_DIFF` evidence collector to the Repository Context Engine. The `COMMIT_DIFF` layer is defined in `RepositoryContextLayer`, referenced in 3 context profiles, and ranked by `DeterministicEvidenceRanker`, but no collector produces it. The data is already available as `ChangedFile` entities in the database.

---

## Affected Modules

| Module | Change Type | Files |
|--------|-------------|-------|
| `repositorycontext.collector` | **New file** | `CommitDiffEvidenceCollector.java` |
| `repositorycontext.collector` | **New test** | `CommitDiffEvidenceCollectorTest.java` |

**No modifications** to existing files (interfaces, existing collectors, ranker, profiles).

---

## Existing Implementation Analysis

### 1. RepositoryContextCollector Interface

```java
public interface RepositoryContextCollector {
    String collectorId();
    String collectorVersion();
    List<RepositoryEvidence> collect(ContextRequest request);
}
```

**Pattern**: Each collector returns a flat `List<RepositoryEvidence>`. No streaming, no callbacks. The engine aggregates all collectors' output.

### 2. RepositoryEvidence Record

```java
public record RepositoryEvidence(
    RepositoryContextLayer layer,
    String kind,
    String reference,
    String summary,
    String originatingFile,
    List<String> relatedReferences,
    Instant occurredAt,
    SourceType sourceType,
    String collectorId,
    String collectorVersion,
    UUID repositoryLocation
) {}
```

**Key fields for COMMIT_DIFF**:
- `layer = COMMIT_DIFF`
- `kind = "CHANGED_FILE"`
- `reference = "diff:{commitHash}:{path}"`
- `summary = "{changeType} {path} (+{insertions}/-{deletions})"`
- `originatingFile = path` (for ranker path-matching)
- `relatedReferences = [commit references that touched this file]`
- `occurredAt = commit.committedAt`
- `sourceType = DETERMINISTIC_EXTRACTION`
- `collectorId = "commit-diff"`
- `collectorVersion = "v1"`
- `repositoryLocation = source.getId()`

### 3. GitHistoryContextCollector (Order 30)

**Pattern**:
- Injects `ProjectCommitRepository`
- Queries recent commits (configurable window)
- Maps each `ProjectCommit` → 1 `GIT_HISTORY` evidence
- Uses `ContextRequest.projectId()` for workspace resolution
- Uses `ContextRequest.analysisDate()` for recency calculations
- Returns early with empty list if no commits found

**Key observation**: Uses `@Value("${devlog.context.history.max-recent-commits:100}")` for configurable limits.

### 4. RepositoryStructureCollector (Order 40)

**Pattern**:
- Injects `SourceRepository` + `WorkspaceManager`
- Resolves workspace from `ContextRequest.projectId()`
- Uses `SecureRepositoryScanner` for filesystem access
- Produces multiple evidence kinds from single scan
- Story-term prioritization via `extractStoryTerms()`
- Collector limit (40 items for file evidence)

**Key observation**: This collector is ORDER 40. The new collector should be ORDER 35 (between GitHistory and RepositoryStructure).

### 5. ContextRequest Record

```java
public record ContextRequest(
    UUID projectId,
    String objective,
    Instant analysisDate,
    Map<String, String> parameters
) {}
```

**Key fields**: `projectId` (to query commits), `analysisDate` (for recency), `objective` (for story-term matching — not needed in this collector, ranker handles it).

### 6. ProjectCommitRepository

```java
public interface ProjectCommitRepository extends JpaRepository<ProjectCommit, UUID> {
    Optional<ProjectCommit> findByProjectIdAndCommitHash(UUID projectId, String commitHash);
    List<ProjectCommit> findByProjectIdOrderByCommittedAtDescCommitHashDesc(UUID projectId);
}
```

**Key observation**: No `findByProjectIdAndCommittedAtAfter` method exists. Need to add it or use existing methods. Since the 90-day window is a common pattern, adding a repository method is cleaner.

### 7. ChangedFile Entity

```java
@Entity
@Table(name = "commit_changed_files")
public class ChangedFile {
    @Id @GeneratedValue UUID id;
    @ManyToOne ProjectCommit commit;
    @Enumerated FileChangeType changeType;
    String oldPath;
    String newPath;
    boolean binary;
    int insertions;
    int deletions;
}
```

**Key fields**: `changeType` (ADDED, MODIFIED, DELETED, RENAMED, COPIED), `insertions`, `deletions`, `binary`.

### 8. FileChangeType Enum

```java
public enum FileChangeType {
    ADDED, MODIFIED, DELETED, RENAMED, COPIED
}
```

### 9. CommitDiffContextBuilder (Exclusion Logic)

**Exclusion logic** (to reuse):
```java
private static final Set<String> GENERATED_SEGMENTS = Set.of(
    "node_modules", "vendor", "target", "build", "dist", "coverage", ".venv", "venv"
);

private String exclusion(String path, boolean binary) {
    if (binary) return "BINARY_FILE";
    String normalized = "/" + path.toLowerCase(Locale.ROOT).replace('\\', '/') + "/";
    if (GENERATED_SEGMENTS.stream().anyMatch(value -> normalized.contains("/" + value + "/")))
        return "GENERATED_OR_VENDOR_PATH";
    if (normalized.endsWith(".min.js/") || normalized.endsWith(".map/"))
        return "GENERATED_FILE";
    return null;
}
```

---

## Architecture Compliance

### ADR-035 (Historical Analysis Boundaries)
- ✅ Commit diff analysis is deterministic extraction, not interpretation
- ✅ Evidence items represent factual file changes, not semantic meaning
- ✅ No commit message interpretation beyond what `ChangedFile` metadata provides

### ADR-037 (Repository-First Context Extraction)
- ✅ Collector queries database, not filesystem
- ✅ Data is extracted deterministically from persisted entities
- ✅ No workspace/filesystem access needed

### ADR-038 (Extensible Collectors)
- ✅ Implements `RepositoryContextCollector` interface
- ✅ Independent module, no coupling to other collectors
- ✅ Self-contained data access

### ADR-040 (Knowledge/Evidence Separation)
- ✅ Evidence items are raw file change facts
- ✅ No knowledge interpretation or analysis
- ✅ No cross-entity enrichment

### ADR-041 (Deterministic Ranking)
- ✅ `COMMIT_DIFF` layer is already handled by `DeterministicEvidenceRanker`
- ✅ `originatingFile` enables path-matching for semantic relevance
- ✅ `occurredAt` timestamp enables recency scoring

---

## Dependencies

### New Dependencies
**None** — all required classes already exist:
- `ProjectCommitRepository` — existing
- `ChangedFile` — existing
- `FileChangeType` — existing
- `RepositoryContextCollector` — existing
- `RepositoryEvidence` — existing
- `RepositoryContextLayer` — existing

### Existing Dependencies
- `ProjectCommitRepository` — used by `GitHistoryContextCollector` already
- `ChangedFile` — JPA entity, already in persistence context

---

## Risks

### R1: Repository method for temporal query
**Risk**: `ProjectCommitRepository` has no `findByProjectIdAndCommittedAtAfter` method.
**Mitigation**: Add method to repository interface. JPA derives query from method name. Alternatively, use existing `findByProjectIdOrderByCommittedAtDescCommitHashDesc` and filter in-memory (less efficient but no schema change).

**Recommendation**: Add the repository method. It's a one-line addition to an existing interface.

### R2: Performance with eager-loaded changedFiles
**Risk**: `ProjectCommit` has `@OneToMany` to `ChangedFile` with default LAZY loading. Querying commits and then accessing `changedFiles` triggers N+1.
**Mitigation**: Use `@Query` with `JOIN FETCH` in repository, or accept N+1 for simplicity (collector limit of 50 items bounds the impact).

**Recommendation**: Accept N+1 for V1. The collector limit (50 items) and temporal window (90 days) bound the actual number of queries. Optimize in a future story if profiling shows it matters.

### R3: Deduplication correctness
**Risk**: Files changed in multiple commits need correct merging of metadata.
**Mitigation**: Simple algorithm: group by path, keep most recent commit's metadata, sum insertions/deletions, list all commit references. This is straightforward stream processing.

---

## Recommendation

**Proceed to Implementation Planning.**

The story is well-defined, low-risk, and follows established patterns. All required data and interfaces exist. The only new code is:
1. `CommitDiffEvidenceCollector` (~150-200 lines)
2. `CommitDiffEvidenceCollectorTest` (~10-12 tests)
3. One repository method addition (`findByProjectIdAndCommittedAtAfter`)
