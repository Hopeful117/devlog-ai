# Story 0006 — Commit Diff Evidence Collector

## Story ID
0006

## Title
Add COMMIT_DIFF evidence layer to the Repository Context Engine

## Status
Completed

## Priority
High

## Date
2026-08-08

---

## User Story

As Kiko (the engineering context agent),
I want DevLog to surface recent file changes (insertions, deletions, change types) as COMMIT_DIFF evidence items,
So that when I prepare an Engineering Story, I understand not just which files exist, but which files have recently changed and how — enabling me to identify active development areas and recent modifications.

---

## Context

### The Gap

After Stories 0001–0005, the Repository Context Engine produces rich evidence about:

- **Git history**: commit messages, authors, dates (COMMIT evidence via `GitHistoryContextCollector`)
- **Repository structure**: modules, source/test directories, config files, individual files (SOURCE_FILE, TEST_FILE, etc. via `RepositoryStructureCollector`)
- **Knowledge**: decisions, milestones, insights, documentation (ADR, ROADMAP, etc. via `ProjectKnowledgeContextCollector`)

However, the `COMMIT_DIFF` layer — which represents **what changed in each commit** — has no collector producing it.

### Evidence of the Gap

The `COMMIT_DIFF` layer is:
- Defined in `RepositoryContextLayer.COMMIT_DIFF`
- Referenced in 3 context profiles: `architecture-v1`, `history-v1`, `engineering-story-v1`
- Ranked in `DeterministicEvidenceRanker` with:
  - `ARCHITECTURAL_RELEVANCE = 80` (same as RELATED_SOURCE_CODE)
  - `HISTORICAL_RELEVANCE = 100` (same as GIT_HISTORY)
- **But no collector produces it** — the profiles request it, the ranker knows how to score it, but nothing supplies it

### What Exists

The data is already available:
- `ChangedFile` entities in the database (`commit_changed_files` table) with:
  - `changeType` (ADDED, MODIFIED, DELETED, RENAMED, COPIED)
  - `oldPath`, `newPath`
  - `binary` flag
  - `insertions`, `deletions` counts
- `ProjectCommit` entities with `changedFiles` association
- `CommitDiffContextBuilder` that transforms `ProjectCommit` → `CommitDiffAnalysisContext`
- `ProjectCommitRepository` with `findByProjectIdOrderByCommittedAtDescCommitHashDesc()`

### The Problem

When Kiko asks "what files changed recently in the auth module?", the system can:
- ✅ Show commit messages mentioning "auth" (GIT_HISTORY)
- ✅ Show file paths containing "auth" (SOURCE_FILE from RepositoryStructureCollector)
- ❌ NOT show which specific files had insertions/deletions in recent commits (COMMIT_DIFF)

This means Kiko cannot distinguish between:
- A file that hasn't changed in months
- A file that was heavily modified yesterday

Both appear as SOURCE_FILE evidence with the same ranking. The COMMIT_DIFF layer would provide the missing temporal dimension.

---

## Acceptance Criteria

### AC-1: ChangedFile evidence production

The `CommitDiffEvidenceCollector` must produce individual `COMMIT_DIFF` evidence items for recently changed files.

- Each evidence item: `layer = COMMIT_DIFF`, `kind = "CHANGED_FILE"`, `reference = "diff:{commitHash}:{path}"`, `summary = "{changeType} {path} (+{insertions}/-{deletions})"`, `originatingFile = path`
- Source: `ChangedFile` entities from recent `ProjectCommit` entries
- Maximum: configurable (default: 50 changed file items)

### AC-2: Temporal relevance

Changed files must be temporally relevant to the analysis.

- Only include files from commits within a configurable window (default: 90 days)
- More recent changes should rank higher via the existing `recency()` criterion
- Files changed multiple times should produce evidence with the most recent commit's metadata

### AC-3: Change type awareness

Evidence items must reflect the type of change.

- `ADDED` files: `summary = "Added {path} (+{insertions})"`
- `MODIFIED` files: `summary = "Modified {path} (+{insertions}/-{deletions})"`
- `DELETED` files: `summary = "Deleted {path} (-{deletions})"`
- `RENAMED` files: `summary = "Renamed {oldPath} → {newPath}"`
- Binary files: `summary = "Binary {path}"` (no insertion/deletion counts)

### AC-4: Deduplication

When a file appears in multiple commits within the window:

- Produce ONE evidence item per file (not one per commit)
- Use the most recent commit's metadata (hash, date, change type)
- `relatedReferences` should list all commit references that touched this file
- `summary` should reflect the cumulative impact: "Modified {path} (+{totalInsertions}/-{totalDeletions}) in {commitCount} commits"

### AC-5: Budget-aware

File-level evidence must respect the existing budget.

- Collector limits output to `MAX_COMMIT_DIFF_ITEMS` (default: 50)
- When more files exist than the limit, prioritize by:
  1. Recency (most recently changed first)
  2. Magnitude (highest total insertions+deletions)
  3. Alphabetical (deterministic tiebreaker)
- The `BudgetedDiverseEvidenceSelector` handles final budget enforcement

### AC-6: Exclusion of generated/vendor paths

Changed files in generated or vendor directories must be excluded.

- Reuse the exclusion logic from `CommitDiffContextBuilder`:
  - `node_modules`, `vendor`, `target`, `build`, `dist`, `coverage`, `.venv`, `venv`
  - `.min.js`, `.map` files
  - Binary files (marked as binary in `ChangedFile`)
- Excluded files are not produced as evidence items

### AC-7: Provenance correctness

All evidence items must have correct provenance.

- `sourceType = "DETERMINISTIC_EXTRACTION"`
- `collectorId = "commit-diff"`
- `collectorVersion = "v1"`
- `repositoryLocation = sourceId`
- `originatingFile = path` (for file evidence)

### AC-8: Graceful handling

- When no commits exist → return empty list
- When no `ChangedFile` entities exist → return empty list
- When workspace is unavailable → return empty list (collector works from DB, not filesystem)

### AC-9: Existing tests pass

All existing tests must continue to pass. No regressions.

### AC-10: New tests

Create tests verifying:

- `CHANGED_FILE` evidence is produced for recently modified files
- Exclusion of generated/vendor paths
- Deduplication of files changed in multiple commits
- Temporal window filtering
- Change type awareness in summary
- Collector limit enforcement
- Graceful handling of empty state

### AC-11: No interface changes

No modifications to `ContextRequest`, `RepositoryContextCollector` interface, `RepositoryContextEngine`, `RepositoryContext`, `RepositoryEvidence`, or any existing collector.

---

## Scope

### In Scope

- Create `CommitDiffEvidenceCollector` implementing `RepositoryContextCollector`
- Produce `COMMIT_DIFF` evidence from `ChangedFile` entities
- Deduplication across commits
- Temporal window filtering
- Generated/vendor path exclusion
- Unit tests

### Out of Scope

- Content reading or AST parsing
- Diff text analysis
- File-level blame or ownership
- Dependency change tracking
- New context profiles
- Frontend changes
- Database migrations

---

## Architecture Notes

### Data Flow

```
ProjectCommitRepository.findByProjectIdAndCommittedAtAfter(...)
  → List<ProjectCommit>
  → flatten changedFiles
  → deduplicate by path (keep most recent)
  → exclude generated/vendor paths
  → create RepositoryEvidence per file
  → sort by recency, then magnitude
  → limit to MAX_COMMIT_DIFF_ITEMS
```

### Collector Position

The collector should run at `@Order(35)` — after `GitHistoryContextCollector` (Order 30) but before `RepositoryStructureCollector` (Order 40). This ensures commit-level file change data is available before aggregate structure data.

### Ranking Integration

The existing `DeterministicEvidenceRanker` already handles `COMMIT_DIFF`:
- `semanticRelevance()`: matches story terms against `kind + summary + originatingFile`
- `architecturalRelevance()`: `COMMIT_DIFF` gets 80 (same as RELATED_SOURCE_CODE)
- `historicalRelevance()`: `COMMIT_DIFF` gets 100 (same as GIT_HISTORY)
- `recency()`: uses `occurredAt` timestamp (commit date)
- `confidence()`: sourceType `DETERMINISTIC_EXTRACTION` gets 95

No changes needed to the ranker.

### Profile Integration

The following profiles already request `COMMIT_DIFF` in their preferred layers:
- `architecture-v1`: Architecture review benefits from knowing what changed
- `history-v1`: History analysis needs file-level change data
- `engineering-story-v1`: Story preparation needs recent modifications

No profile changes needed — the layer is already requested.

---

## Dependencies

- Story 0004 (Repository Structure Collector) — completed
- Story 0005 (Granular File Evidence) — completed
- `ProjectCommitRepository` — existing, tested
- `ChangedFile` entity — existing, tested
- `CommitDiffContextBuilder` — existing, for exclusion logic reference

---

## Risks

### R1: Performance with large commit history

**Risk:** Querying all commits with changed files for a project could be slow.

**Mitigation:** Use `committedAt` filter to limit to recent commits (90-day window). Index on `committed_at` column. Lazy-load `changedFiles` only when needed.

### R2: Memory usage with many changed files

**Risk:** 100 commits × 50 files = 5000 `ChangedFile` entities loaded into memory.

**Mitigation:** Collector limit (50 items). Deduplication reduces actual count. Only load commits within temporal window.

### R3: Deduplication complexity

**Risk:** Files changed in multiple commits need careful merging of metadata.

**Mitigation:** Simple deduplication: keep most recent commit's metadata, sum insertions/deletions, list all commit references. No complex merge logic needed.

---

## Definition of Done

- [x] All 11 acceptance criteria satisfied
- [x] `mvn compile` succeeds
- [x] All existing tests pass
- [x] New tests pass
- [x] Code review complete
- [x] Engineering report produced
