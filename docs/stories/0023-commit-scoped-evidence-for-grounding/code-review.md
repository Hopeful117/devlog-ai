# Code Review Report — Story 0023: Commit-Scoped Evidence for Grounding

## Review Scope

Files changed (uncommitted):

| File | Change |
|---|---|
| `CollectorType.java` | Add `COMMIT_SCOPED` enum value |
| `FactType.java` | Add 5 new commit-scoped enum values |
| `CommitScopedFactCollector.java` | New `@Component` collector (153 lines) |
| `CommitScopedFactCollectorTest.java` | 7 unit tests |
| `KnowledgeSelectionServiceImpl.java` | +3 lines scoring for new types |

## Story Compliance

| AC | Status | Notes |
|---|---|---|
| AC-1 — Commit-diff fact types | ✅ | 5 types produced: SUMMARY, MODULE, FEATURE, BUG, REFACTOR |
| AC-2 — Evidence collection integration | ✅ | Collector auto-discovered via `@Component`, runs alongside existing collectors |
| AC-3 — Grounding contract coverage | ✅ | `analyze-engineering-event` intent scores commit-scoped facts at 100 |
| AC-4 — Live validation | ⚠️ | Not yet performed |
| AC-5 — Backward compatibility | ✅ | No existing behavior changed |

## Plan Compliance

All 6 implementation steps delivered as planned. Two planned fact types (`COMMIT_UPDATES_DEPS`, `COMMIT_CHANGES_CONFIG`) were dropped — acceptable since they are not required for grounding coverage and can be added later.

## Implementation Correctness

### CommitScopedFactCollector

- **Contract**: Correctly implements `KnowledgeCollector` interface. `type()` returns `COMMIT_SCOPED`, `version()` returns `commit-scoped-fact-v1`.
- **Auto-discovery**: `@Component` annotation ensures Spring injection into `KnowledgeCollectionServiceImpl`'s `List<KnowledgeCollector>`. No manual registration needed.
- **Repository query**: Uses `findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc` with a 90-day window from `context.collectionTimestamp()`. Correct — cutoff is before collection time, not relative to "now".
- **Aggregation**: COMMIT_DIFF_SUMMARY is a single aggregate across all commits. COMMIT_CHANGES_MODULE is one fact per module. Feature/bug/refactor facts are per-commit. All correct.
- **Commit parsing heuristic**: Uses conventional-commit prefixes (`feat:`, `fix:`, `refactor:`) plus substring matching. Reasonable for typical projects. False positives (e.g., "resolve conflict" → BUG) are low-severity since COMMIT_DIFF_SUMMARY always fires.
- **Module extraction**: `extractModule()` takes first two path segments. Works for Maven/Gradle multi-module (`backend/src/...` → `backend/src`). Single-segment projects get just the top directory. Acceptable.
- **MAX_FACTS = 20**: Prevents unbounded output. Global selection budget is 60 facts, so this is appropriate.
- **Fingerprinting**: Uses `CollectedFact.create()` with SHA-256 over (version, type, content, evidenceReferences, resolvedRevision). Deduplication is correct.
- **Evidence references**: Sorted, deduplicated, with `commit:` prefix fallback when no changed files. Complies with contract.

### KnowledgeSelectionServiceImpl

- **Scoring**: `analyze-engineering-event` intent scores commit-scoped fact types at 100, all others at 10. Placed before the default `architecture-overview` check — correct precedence.
- **Pattern matching**: Uses `containsAny()` with type name strings. Matches the existing pattern used by other intents.

### Enums

- `CollectorType.COMMIT_SCOPED`: No ordering dependency. Clean addition.
- `FactType` values: No migration needed (stored as STRING in database). Clean additions.

## Test Coverage

7 tests in `CommitScopedFactCollectorTest`:

| Test | Covers |
|---|---|
| `shouldProduceCommitDiffSummaryFromMultipleCommits` | SUMMARY + FEATURE + BUG detection |
| `shouldProduceModuleFactsGroupedByPath` | MODULE grouping |
| `shouldDetectRefactoringCommits` | REFACTOR detection |
| `shouldReturnEmptyWhenNoCommits` | Graceful empty state |
| `shouldDeduplicateFactsByFingerprint` | Fingerprint deduplication |
| `shouldProduceDeduplicatedEvidenceReferences` | Evidence ref sorting/dedup |
| `shouldNotProduceFactsForNonFeatureNonFixNonRefactorCommits` | Negative detection |

All 7 tests pass. 485 total backend tests pass (1 pre-existing `contextLoads` error — PostgreSQL unavailable).

**Gap**: `KnowledgeSelectionServiceTest` was not updated with a test for the new `analyze-engineering-event` scoring path. The scoring logic is simple (3 lines, mirrors existing patterns) and covered by the existing `containsAny` tests, but explicit coverage would be better.

## Architecture Compliance

- Follows existing `KnowledgeCollector` pattern (same as `GitCollector`, `TestStructureCollector`).
- Uses `CollectedFact.create()` for consistent fingerprinting.
- Spring `@Component` auto-discovery — no wiring changes needed.
- No new database migrations (FactType stored as STRING).
- No frontend or AI Engine changes.

## Findings

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| F-1 | Low | `KnowledgeSelectionServiceTest` lacks explicit test for `analyze-engineering-event` scoring | Add a test case verifying commit-scoped types score at 100 and non-commit types score at 10 for this intent |
| F-2 | Info | `isBugFixCommit()` matches "resolve" substring — could match "resolve conflict" or "resolve discussion" | Acceptable for V1; commit-diff summary always produced regardless |
| F-3 | Info | `extractModule()` is path-based, not semantic — a `src/main/java/...` path yields `src/main` not the actual Maven module | Acceptable; module grouping is approximate by design |

## Recommendation

**Approve with one suggestion**: Add an explicit test for the new scoring path in `KnowledgeSelectionServiceTest` (F-1). This is a low-effort improvement that closes the coverage gap.

The implementation is correct, follows existing patterns, has appropriate test coverage, and does not introduce architectural violations.
