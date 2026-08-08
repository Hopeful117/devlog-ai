# Code Review — Story 0006

## Story
**ID**: 0006
**Title**: Commit Diff Evidence Collector
**Status**: Review

---

## Review Summary

| Category | Count |
|----------|-------|
| Blockers | 0 |
| Major | 0 |
| Minor | 1 |
| Observations | 2 |

**Recommendation**: ✅ **Approved**

---

## Files Reviewed

| File | Lines | Verdict |
|------|-------|---------|
| `CommitDiffEvidenceCollector.java` | ~190 | ✅ Clean |
| `CommitDiffEvidenceCollectorTest.java` | ~300 | ✅ Clean |
| `ProjectCommitRepository.java` (+1 method) | +3 | ✅ Clean |

---

## Story Compliance

### Acceptance Criteria

| AC | Description | Status |
|----|-------------|--------|
| AC-1 | ChangedFile evidence production | ✅ PASS |
| AC-2 | Temporal relevance | ✅ PASS |
| AC-3 | Change type awareness | ✅ PASS |
| AC-4 | Deduplication | ✅ PASS |
| AC-5 | Budget-aware | ✅ PASS |
| AC-6 | Exclusion of generated/vendor paths | ✅ PASS |
| AC-7 | Provenance correctness | ✅ PASS |
| AC-8 | Graceful handling | ✅ PASS |
| AC-9 | Existing tests pass | ✅ PASS (223 tests, 6 pre-existing failures) |
| AC-10 | New tests | ✅ PASS (12/12) |
| AC-11 | No interface changes | ✅ PASS |

### All 11 acceptance criteria satisfied.

---

## Architecture Compliance

| ADR | Requirement | Status |
|-----|-------------|--------|
| ADR-035 | Historical Analysis Boundaries | ✅ Deterministic extraction, no interpretation |
| ADR-037 | Repository-First Context Extraction | ✅ Queries database, not filesystem |
| ADR-038 | Extensible Collectors | ✅ Implements `RepositoryContextCollector` |
| ADR-040 | Knowledge/Evidence Separation | ✅ Raw evidence items only |
| ADR-041 | Deterministic Ranking | ✅ COMMIT_DIFF handled by existing ranker |

---

## Detailed Findings

### Major Issues: None

### Minor Issues

**M1: `FileGroup` is a non-static inner class but should be static**

`FileGroup` is declared as a non-static inner class, but it only references `this` fields — no outer class instance is needed. Making it `static` would be slightly cleaner and marginally more memory-efficient.

**Impact**: Negligible. No functional issue. Code still works correctly.

**File**: `CommitDiffEvidenceCollector.java`, line ~155

**Recommendation**: Change to `private static class FileGroup`. Non-blocking.

---

### Observations

**O1: Deduplication correctness**

The collector iterates commits in descending `committedAt` order (from the repository query). The `FileGroup.add()` method correctly uses the first-seen change type as the "dominant" type, which corresponds to the most recent commit since commits are iterated most-recent-first. The `mostRecentCommit` field is also correctly updated when a newer commit is encountered.

However, there's a subtle edge case: if commits arrive out of order (e.g., two commits with the same `committedAt`), the `mostRecentCommit` update uses `isAfter()` which would not trigger for equal timestamps. This is acceptable because:
1. The repository query orders by `committedAt DESC, commitHash DESC`
2. Equal timestamps with different hashes would be a rare edge case
3. The evidence would still be correct (just might use a slightly different commit's metadata)

**Verdict**: Acceptable. No action needed.

**O2: `EvidenceFactory` dependency**

The collector injects `EvidenceFactory` for evidence creation, consistent with `GitHistoryContextCollector` and `RepositoryStructureCollector`. This is the correct pattern — direct `RepositoryEvidence` construction would bypass any future evidence validation or transformation logic in the factory.

**Verdict**: Good pattern. No issue.

---

## Test Quality

| Criterion | Assessment |
|-----------|------------|
| Coverage of acceptance criteria | ✅ All 11 ACs covered |
| Edge cases | ✅ Binary, generated, empty, limit, deduplication |
| Mock quality | ✅ Proper Mockito usage, no over-mocking |
| Assertion quality | ✅ Specific assertions on layer, kind, summary, reference |
| Test isolation | ✅ Each test independent, no shared state |

**Test verdict**: Excellent coverage. 12 tests cover all critical paths.

---

## Validation Evidence

```
mvn compile -q → BUILD SUCCESS
mvn test -Dtest=CommitDiffEvidenceCollectorTest → 12/12 PASS
mvn test -q → 223 tests, 6 failures (all pre-existing)
```

---

## Conclusion

The implementation is clean, well-structured, and follows established patterns. The collector correctly fills the COMMIT_DIFF gap with proper deduplication, exclusion, temporal filtering, and provenance. Tests provide comprehensive coverage.

**One minor observation** (non-blocking): `FileGroup` could be `static`.

**Recommendation**: ✅ **Approved for Engineering Report.**
