# Implementation Plan — Story 0023: Commit-Scoped Evidence for Engineering Event Grounding

## Architecture Summary

The DevLog AI pipeline has two separate evidence systems:

1. **Knowledge Collectors** → produce `CollectedFact` items → persisted to `facts` table → selected via `KnowledgeSelectionService` → appear in `selectedFacts` → used by grounding contract for `allowedSupportingFactIds`
2. **Repository Context Collectors** → produce `RepositoryEvidence` items → stored in `RepositoryContext.evidence()` → used by grounding contract for `allowedEvidenceReferences` only

The gap: The grounding contract's `allowedSupportingFactIds` only includes UUIDs from `selectedFacts`. There are no commit-scoped facts in that list because no `KnowledgeCollector` produces them.

## Implementation Steps

### Step 1 — Add COMMIT_SCOPED CollectorType

**File**: `backend/.../collection/collector/CollectorType.java`

Add `COMMIT_SCOPED` to the enum. This identifies the new collector in the collection pipeline.

### Step 2 — Add commit-scoped FactType values

**File**: `backend/.../fact/entity/FactType.java`

Add:
- `COMMIT_DIFF_SUMMARY` — high-level summary of changes (files changed, insertions, deletions)
- `COMMIT_CHANGES_MODULE` — which modules/packages are affected
- `COMMIT_ADDS_FEATURE` — commit message + file pattern indicate a new feature
- `COMMIT_FIXES_BUG` — commit message + file pattern indicate a bug fix
- `COMMIT_REFACTORS_CODE` — changes restructure without adding features/fixes

These are persisted to the `facts` table (no schema change needed — `FactType` is a JPA enum stored as STRING).

### Step 3 — Create CommitScopedFactCollector

**File**: `backend/.../collection/collector/CommitScopedFactCollector.java` (new)

Implements `KnowledgeCollector`. Reads `ChangedFile` entities and `ProjectCommit` entities from the database for the current analysis's source, then produces `CollectedFact` items for each commit-scoped type.

Logic:
- **COMMIT_DIFF_SUMMARY**: One fact per commit. Content: "X files changed, +Y/-Z lines across N modules". Evidence references: list of changed file paths.
- **COMMIT_CHANGES_MODULE**: One fact per affected top-level module. Content: "Module X: N files changed". Evidence references: file paths in that module.
- **COMMIT_ADDS_FEATURE**: Parse commit message for `feat:`, `feature:`, `add:` prefixes OR detect new file creation pattern. Content: feature description. Evidence references: new files.
- **COMMIT_FIXES_BUG**: Parse commit message for `fix:`, `bugfix:`, `hotfix:` prefixes. Content: bug description. Evidence references: fixed files.
- **COMMIT_REFACTORS_CODE**: Parse commit message for `refactor:`, `cleanup:`, `restructure:` prefixes OR detect pure deletion/rename without behavior change. Content: refactoring description. Evidence references: affected files.

Each fact uses `CollectedFact.create()` with fingerprinting for deduplication.

Access to commits: The collector reads from `ProjectCommitRepository` using the analysis's source ID and the workspace's resolved revision. It finds commits between the base commit and target commit (from the evolution context, when present) or recent commits within the window (for regular analyses).

### Step 4 — KnowledgeSelection scoring for new types

**File**: `backend/.../knowledge/selection/KnowledgeSelectionServiceImpl.java`

Add scoring rules in `factScore()` for the new types:
- For `analyze-engineering-event` intent: `COMMIT_DIFF_SUMMARY`, `COMMIT_CHANGES_MODULE`, `COMMIT_ADDS_FEATURE`, `COMMIT_FIXES_BUG`, `COMMIT_REFACTORS_CODE` → score 100 (highest priority)
- For other intents: score 10 (low priority, not relevant)

This ensures commit-scoped facts are selected with high priority for engineering event analyses.

### Step 5 — Tests

**File**: `backend/src/test/java/.../collection/collector/CommitScopedFactCollectorTest.java` (new)

Test cases:
- Produces COMMIT_DIFF_SUMMARY fact from multiple changed files
- Produces COMMIT_CHANGES_MODULE facts grouped by module
- Produces COMMIT_ADDS_FEATURE when commit message has `feat:` prefix
- Produces COMMIT_FIXES_BUG when commit message has `fix:` prefix
- Produces COMMIT_REFACTORS_CODE when commit message has `refactor:` prefix
- No facts when no commits in scope
- Fingerprint deduplication works correctly
- Evidence references are relative paths

**File**: `backend/src/test/java/.../knowledge/selection/KnowledgeSelectionServiceTest.java` (modify)

Add test: commit-scoped facts are selected with high priority for engineering event intent.

### Step 6 — Documentation Reconciliation

Update README or relevant architecture docs if commit-scoped collection is documented.

### Step 7 — Validation

- Backend: `mvn clean verify` passes
- Frontend: existing tests pass (no frontend changes)
- AI Engine: existing tests pass (no AI Engine changes)
- SonarQube: Quality Gate `OK`, new-code coverage ≥ 80%
- Live validation: trigger engineering event analysis against devlog-ai, verify commit-scoped facts in grounding contract, verify proposals generated

## Files Changed

| File | Change |
|---|---|
| `CollectorType.java` | Add `COMMIT_SCOPED` |
| `FactType.java` | Add 5 new commit-scoped types |
| `CommitScopedFactCollector.java` | New file |
| `CommitScopedFactCollectorTest.java` | New file |
| `KnowledgeSelectionServiceImpl.java` | Add scoring for new types |
| `KnowledgeSelectionServiceTest.java` | Add test for new scoring |

## Risks

- **Low**: New collector depends on `ProjectCommitRepository` data availability. If history import hasn't occurred, the collector produces zero facts (graceful degradation).
- **Low**: Commit message parsing is heuristic (prefix detection). Non-conventional commits won't produce feature/bug/refactor facts, but will still produce COMMIT_DIFF_SUMMARY.

## Out of Scope

- Changing the grounding contract validation logic
- Modifying the AI Engine prompt
- Frontend changes
- New database migrations (FactType enum values are stored as strings, not in a lookup table)
