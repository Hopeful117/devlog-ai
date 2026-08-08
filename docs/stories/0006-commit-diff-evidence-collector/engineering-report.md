# Engineering Report — Story 0006

## Story

| Field | Value |
|-------|-------|
| **ID** | 0006 |
| **Title** | Commit Diff Evidence Collector |
| **Repository** | devlog-ai |
| **Status** | Completed |
| **Completed** | 2026-08-08 |

---

## Summary

Filled the COMMIT_DIFF layer gap in the repository context pipeline. The `COMMIT_DIFF` layer was defined in the architecture (enum, profiles, ranker) but no collector produced evidence for it. Created a new `CommitDiffEvidenceCollector` that queries `ChangedFile` entities from the database, deduplicates across commits, excludes generated/vendor paths, and produces `CHANGED_FILE` evidence items with proper summaries and provenance.

---

## Goal

Create a new `CommitDiffEvidenceCollector` producing `COMMIT_DIFF` evidence from `ChangedFile` entities, filling a gap where the layer was defined but no collector existed.

---

## Artifacts

### Created

| File | Lines | Description |
|------|-------|-------------|
| `CommitDiffEvidenceCollector.java` | ~190 | New `@Component` collector |
| `CommitDiffEvidenceCollectorTest.java` | ~300 | 12 test cases |
| `repository-analysis.md` | ~150 | Repository analysis |
| `implementation-plan.md` | ~200 | Implementation plan |
| `implementation-report.md` | ~130 | Implementation report |
| `code-review.md` | ~130 | Code review |

### Modified

| File | Change |
|------|--------|
| `ProjectCommitRepository.java` | +1 repository method |
| `story.md` | Status → Completed, DoD checked |

---

## Architecture

### Design Decisions

1. **Order 35**: Placed after `GitHistoryContextCollector` (30) and before `RepositoryStructureCollector` (40), creating a logical progression: historical → diff → structure.

2. **Database-first**: Queries `ProjectCommitRepository` directly, consistent with ADR-037 (Repository-First Context Extraction). No filesystem access.

3. **Path-based deduplication**: Groups `ChangedFile` entities by normalized path, accumulating insertions/deletions across commits. Uses the most recent commit's metadata for the evidence item.

4. **Reuse of exclusion logic**: Reuses the `GENERATED_SEGMENTS` set and `.min.js`/`.map` exclusion from `CommitDiffContextBuilder`, ensuring consistency between context generation and evidence collection.

5. **EvidenceFactory pattern**: Uses `EvidenceFactory` for evidence creation, consistent with `GitHistoryContextCollector` and `RepositoryStructureCollector`.

### ADR Compliance

| ADR | Compliance |
|-----|------------|
| ADR-035 (Historical Analysis Boundaries) | ✅ Deterministic extraction |
| ADR-037 (Repository-First Context Extraction) | ✅ Queries database, not filesystem |
| ADR-038 (Extensible Collectors) | ✅ Implements `RepositoryContextCollector` |
| ADR-040 (Knowledge/Evidence Separation) | ✅ Raw evidence items only |
| ADR-041 (Deterministic Ranking) | ✅ COMMIT_DIFF handled by existing ranker |

---

## Validation

### Compilation
```
mvn compile -q → BUILD SUCCESS
```

### New Tests
```
mvn test -Dtest=CommitDiffEvidenceCollectorTest
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 → ALL PASS
```

### Full Test Suite
```
mvn test -q → 223 tests, 6 failures (all pre-existing)
```

### No Regressions
Pre-existing failures unchanged: `RestAIEngineClientIntegrationTest`, `AnalysisWorkflowServiceTest`, `InitialCollectorsTest`, `DevlogAiBackendApplicationTests`, `ValidationControllerWebMvcTest`.

---

## Test Summary

| Test | What it verifies | Status |
|------|------------------|--------|
| `producesChangedFileEvidenceForModifiedFiles` | MODIFIED files → CHANGED_FILE evidence | ✅ |
| `producesEvidenceForAddedFiles` | ADDED summary format | ✅ |
| `producesEvidenceForDeletedFiles` | DELETED summary format | ✅ |
| `producesEvidenceForRenamedFiles` | RENAMED summary with both paths | ✅ |
| `excludesBinaryFiles` | Binary files filtered out | ✅ |
| `excludesGeneratedVendorPaths` | target/, node_modules/, build/, dist/ excluded | ✅ |
| `excludesMinJsAndMapFiles` | .min.js and .map excluded | ✅ |
| `deduplicatesFilesAcrossMultipleCommits` | Cross-commit deduplication with summed stats | ✅ |
| `usesMostRecentCommitMetadataForDeduplicatedFiles` | Most recent commit metadata used | ✅ |
| `filtersCommitsOutsideTemporalWindow` | DB-level temporal filtering delegated | ✅ |
| `respectsMaxItemsLimit` | Output capped at maxItems | ✅ |
| `returnsEmptyListWhenNoCommitsExist` | Empty list when no commits | ✅ |

---

## Follow-ups

### Potential Future Stories
1. **Context Profile Tuning (Story 0007)** — Adjust weight multipliers now that COMMIT_DIFF is populated
2. **File Change Velocity (Story 0008)** — Track churn rate over time for prioritization
3. **Diff Content Analysis (Story 0009)** — Extract semantic signals from diff content

### Operational
- Story 0005 changes remain uncommitted (per working-tree policy)
- Story 0006 changes should be committed with Story 0005 in a single commit

---

## Files in Story Directory

```
docs/stories/0006-commit-diff-evidence-collector/
├── story.md
├── repository-analysis.md
├── implementation-plan.md
├── implementation-report.md
├── code-review.md
└── engineering-report.md
```

---

**Story 0006 — Completed.**
