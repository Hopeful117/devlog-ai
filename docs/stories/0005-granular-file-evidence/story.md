# Story 0005 — Granular File Evidence

## Story ID
0005

## Title
Add file-level RELATED_SOURCE_CODE evidence to the Repository Context Engine

## Status
Completed

## Priority
High

## Date
2026-08-08

---

## User Story

As Kiko (the engineering context agent),
I want DevLog to produce individual file-level evidence about source files, test files, configuration files, and modules,
So that when I prepare an Engineering Story, I receive story-specific repository context identifying the relevant files, tests, and modules — not just aggregate summaries.

---

## Acceptance Criteria

### AC-1: Source file evidence

The `RepositoryStructureCollector` must produce individual `SOURCE_FILE` evidence items for source files discovered by `SecureRepositoryScanner`.

- Each evidence item: `layer = RELATED_SOURCE_CODE`, `kind = "SOURCE_FILE"`, `reference = "file:{relativePath}"`, `summary = relativePath`, `originatingFile = relativePath`
- Source files are files under known source roots (`src/main/java`, `src/main/kotlin`, `src/main/python`, `src/main/typescript`, `src/app`, `src/lib`) with recognized extensions (`.java`, `.kt`, `.py`, `.ts`, `.tsx`, `.js`, `.jsx`)
- NOT produced for files in test roots, build directories, or excluded directories

### AC-2: Test file evidence

The `RepositoryStructureCollector` must produce individual `TEST_FILE` evidence items for test files.

- Each evidence item: `layer = RELATED_SOURCE_CODE`, `kind = "TEST_FILE"`, `reference = "file:{relativePath}"`, `summary = relativePath`, `originatingFile = relativePath`
- Test files are files under known test roots (`src/test/`, `__tests__/`, `test/`, `tests/`)
- Extension must be a recognized source extension

### AC-3: Configuration file evidence

The `RepositoryStructureCollector` must produce individual `CONFIG_FILE` evidence items for configuration files.

- Each evidence item: `layer = RELATED_SOURCE_CODE`, `kind = "CONFIG_FILE"`, `reference = "config:{relativePath}"`, `summary = relativePath`, `originatingFile = relativePath`
- Configuration files match the existing `CONFIGURATION_FILE_NAMES` set plus any `pom.xml` or `build.gradle` beyond the root

### AC-4: Module evidence

The `RepositoryStructureCollector` must produce `MODULE` evidence items for detected modules.

- Each evidence item: `layer = RELATED_SOURCE_CODE`, `kind = "MODULE"`, `reference = "module:{moduleName}"`, `summary = "Module: {moduleName} ({modulePath}) — {fileCount} files"`
- Module name is the first path segment before a recognized source root or build descriptor
- For single-module repositories: produce one `MODULE` evidence with name = project slug or "root"

### AC-5: Budget-aware selection

File-level evidence must be bounded by the existing `ContextBudget` (maxEvidenceItems=60, maxTokens=6000).

- The collector must limit file-level evidence items to a configurable maximum (default: 40 file items)
- When more files exist than the limit, the collector must prioritize files whose paths contain terms from the story description
- The existing `BudgetedDiverseEvidenceSelector` handles final budget enforcement

### AC-6: Story-aware prioritization in collector

The collector must use the story description from `ContextRequest.intent().objective()` to prioritize which files are individually surfaced.

- Extract normalized terms (≥3 chars) from the story description
- Files whose `relativePath` contains at least one term are prioritized
- Files not matching any term are still included but at lower priority
- This is a deterministic, path-based operation — no AI or content reading

### AC-7: Aggregate summaries preserved

The existing aggregate evidence items (`MODULE_SUMMARY`, `SOURCE_DIRECTORIES`, `TEST_DIRECTORIES`, `CONFIGURATION_FILES`, `FILE_EXTENSIONS`) must continue to be produced.

- File-level evidence is ADDITIONAL to aggregate evidence, not a replacement
- Both aggregate and file-level evidence coexist in the candidates list

### AC-8: Evidence provenance

All file-level evidence items must have correct provenance:

- `sourceType = "REPOSITORY_STRUCTURE"`
- `collectorId = "repository-structure"`
- `collectorVersion = "v1"`
- `repositoryLocation = sourceId`
- `originatingFile = relativePath` (for SOURCE_FILE, TEST_FILE, CONFIG_FILE) or `null` (for MODULE)

### AC-9: Graceful handling

- When no source is found → return empty list (existing behavior preserved)
- When workspace is unavailable → return empty list (existing behavior preserved)
- When no files match story terms → still return aggregate evidence + as many file-level items as budget allows

### AC-10: Existing tests pass

All existing tests must continue to pass. The existing `RepositoryStructureCollectorTest` tests must still pass (they test aggregate evidence which is preserved).

### AC-11: New tests

Create tests verifying:

- `SOURCE_FILE` evidence is produced for files under source roots
- `TEST_FILE` evidence is produced for files under test roots
- `CONFIG_FILE` evidence is produced for configuration files
- `MODULE` evidence is produced for multi-module repositories
- File evidence is bounded by collector limit
- Story terms influence which files are prioritized

### AC-12: No content reading

File content is NOT read. The `SecureRepositoryScanner` is called with `includeContent = false` (existing behavior). All relevance is path-based.

### AC-13: No interface changes

No modifications to `ContextRequest`, `RepositoryContextCollector` interface, `RepositoryContextEngine`, `RepositoryContext`, `RepositoryEvidence`, or any existing collector other than `RepositoryStructureCollector`.

---

## Scope

### In Scope

- Extend `RepositoryStructureCollector` to produce file-level evidence
- Add `MODULE` evidence with module name inference
- Add story-term prioritization in collector
- Add configurable collector limit for file evidence items
- Update `DeterministicContextIntelligence` if needed (likely no change — RELATED_SOURCE_CODE already first)
- New unit tests for file-level evidence

### Out of Scope

- Content reading or AST parsing
- File → module association beyond first-segment inference
- Changed-file → commit association
- Dependency analysis
- New `ContextProfile` or ranking criteria
- Frontend changes
- Database migrations
- Commit-diff evidence (separate story)

---

## Risks

### R1: Evidence count may exceed budget

**Risk:** 500+ files in the repository could produce 500+ evidence items, overwhelming the budget.

**Mitigation:** Collector-level limit (default 40 file items). Budget-aware prioritization using story terms. Existing `BudgetedDiverseEvidenceSelector` enforces final limits.

### R2: Workspace synchronization latency

**Risk:** `WorkspaceManager.synchronize()` may be slow for large repositories.

**Mitigation:** Already handled by `SecureRepositoryScanner` timeout (`CollectorLimits.collectorTimeout`). Same behavior as Story 0004.

### R3: Story-term matching may be too broad

**Risk:** A common term like "service" or "model" could match many files, producing too many low-relevance items.

**Mitigation:** Collector prioritizes files with more term matches. Budget limit caps output. Ranker scores each item individually.

---

## Architecture Notes

### Evidence Production

The collector transforms `RepositoryFile` → `RepositoryEvidence`:

```
SecureRepositoryScanner.scan() → RepositoryScan(List<RepositoryFile>)
→ Filter by source/test/config classification
→ Extract module names from paths
→ Prioritize by story term overlap
→ Limit to N items
→ Create RepositoryEvidence via EvidenceFactory
```

### Ranking

The existing `DeterministicEvidenceRanker` scores each evidence item:
- `semanticRelevance()`: matches story terms against `kind + summary + originatingFile`
- For `SOURCE_FILE` evidence: `originatingFile = relativePath` → strong path matching
- For `MODULE` evidence: `summary = "Module: backend"` → "module" term matches

### Budget

Default: 40 file-level items + 5 aggregate items = 45 total candidates. Budget selector reduces to 60 items max / 6000 tokens max.

---

## Dependencies

- Story 0004 (Repository Structure Collector) — completed
- `SecureRepositoryScanner` — existing, tested
- `EvidenceFactory` — existing, tested
- `DeterministicEvidenceRanker` — existing, tested
- `BudgetedDiverseEvidenceSelector` — existing, tested

---

## Definition of Done

- [x] All 13 acceptance criteria satisfied
- [x] `mvn compile` succeeds
- [x] All existing tests pass
- [x] New tests pass
- [x] Code review complete
- [x] Engineering report produced
