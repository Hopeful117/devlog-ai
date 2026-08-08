# Code Review — Story 0005

## Story
Add file-level RELATED_SOURCE_CODE evidence to the Repository Context Engine

## Review Date
2026-08-08

## Files Reviewed
1. `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`
2. `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java`

---

## Story Compliance

| AC | Status | Notes |
|----|--------|-------|
| AC-1: Source file evidence | ✅ | `SOURCE_FILE` produced for files under source roots |
| AC-2: Test file evidence | ✅ | `TEST_FILE` produced for files under test roots |
| AC-3: Config file evidence | ✅ | `CONFIG_FILE` produced for files matching `CONFIGURATION_FILE_NAMES` |
| AC-4: Module evidence | ✅ | `MODULE` with inferred names and file counts |
| AC-5: Budget-aware | ✅ | `MAX_FILE_EVIDENCE_ITEMS = 40` + `BudgetedDiverseEvidenceSelector` |
| AC-6: Story-term prioritization | ✅ | `extractStoryTerms()` + sorting by match count |
| AC-7: Aggregate preserved | ✅ | All 5 aggregate items still produced |
| AC-8: Evidence provenance | ✅ | Correct `sourceType`, `collectorId`, `collectorVersion` |
| AC-9: Graceful handling | ✅ | Empty list on missing source/workspace (unchanged) |
| AC-10: Existing tests pass | ✅ | 11/11 pass |
| AC-11: New tests | ✅ | 6 new tests added |
| AC-12: No content reading | ✅ | Scanner called with `includeContent = false` |
| AC-13: No interface changes | ✅ | No changes to `ContextRequest`, `RepositoryContextCollector`, etc. |

---

## Plan Compliance

All 6 implementation steps executed as planned:

1. ✅ File classification helpers added (`isSourceFile`, `isTestFile`, `isConfigFile`, `extractModuleName`, `extractStoryTerms`, `containsSourceRoot`, `containsTestRoot`, `hasSourceExtension`)
2. ✅ `produceFileLevelEvidence()` implemented with story-term prioritization
3. ✅ `produceModuleEvidence()` implemented
4. ✅ `collect()` updated — `.limit(5)` removed, file-level evidence added
5. ✅ `MAX_FILE_EVIDENCE_ITEMS = 40` constant added
6. ✅ Tests updated — 1 existing + 6 new = 11 total

---

## Code Quality

### Positive

- **Clean separation**: File classification helpers are private static, pure functions — easy to test and reason about
- **Consistent patterns**: New evidence follows the exact same `evidenceFactory.create()` pattern as existing aggregate evidence
- **Provenance correctness**: `originatingFile = relativePath` for file evidence, `null` for MODULE evidence — matches ranker expectations
- **Budget enforcement**: Two-level defense — collector limits to 40 items, selector enforces 60/6000 final budget
- **Story-term prioritization**: Deterministic, path-based, no AI involvement — exactly as designed
- **Test coverage**: 6 new tests cover all new evidence kinds, limit enforcement, and story-term prioritization

### Observations

1. **`TEST_ROOTS` uses `contains()` while `SOURCE_ROOTS` uses `startsWith()`**: The test root detection uses `relativePath.contains(root)` which is more permissive — it matches test roots anywhere in the path. This is intentional and correct for test directories which can appear at various nesting levels.

2. **`extractModuleName()` returns "root" for single-module repos**: For paths like `src/main/java/com/App.java`, the first segment is `src` which matches `SOURCE_ROOTS`, so module = "root". This is correct.

3. **FQCN in `produceFileLevelEvidence` and `produceModuleEvidence`**: The methods use `com.hopeful117.devlogai.repositorycontext.ContextRequest` instead of importing it. This is a style choice — the class already has `ContextRequest` imported. Minor but not a blocker.

4. **Unicode em-dash in module summary**: `"Module: " + moduleName + " \u2014 " + fileCount + " files"` — uses em-dash (—) consistently with the rest of the codebase.

---

## Architecture Compliance

- ✅ ADR-037 (Repository-First): Evidence comes from repository scanning, not AI inference
- ✅ ADR-038 (Extensible Collectors): Collector implements `RepositoryContextCollector` interface
- ✅ ADR-040 (Knowledge/Evidence Separation): Produces `RepositoryEvidence`, not `Fact` entities
- ✅ No new Spring beans, no interface changes, no database changes

---

## Test Coverage

| Test | What it verifies | Status |
|------|-----------------|--------|
| `producesRelatedSourceCodeLayer` | All evidence has `RELATED_SOURCE_CODE` layer | ✅ |
| `producesSourceFileEvidenceForSourceFiles` | `SOURCE_FILE` items with correct paths | ✅ |
| `producesTestFileEvidenceForTestFiles` | `TEST_FILE` items for test roots | ✅ |
| `producesConfigFileEvidenceForConfigFiles` | `CONFIG_FILE` items for config files | ✅ |
| `producesModuleEvidenceForMultiModuleRepos` | `MODULE` items with module names | ✅ |
| `limitsFileEvidenceItems` | 40-item cap enforced | ✅ |
| `prioritizesFilesMatchingStoryTerms` | Matching files appear first | ✅ |
| `producesModuleSummaryEvidence` | Aggregate MODULE_SUMMARY preserved | ✅ |
| `producesSourceDirectoryEvidence` | Aggregate SOURCE_DIRECTORIES preserved | ✅ |
| `returnsEmptyListWhenNoSource` | Graceful degradation | ✅ |
| `returnsEmptyListWhenWorkspaceUnavailable` | Graceful degradation | ✅ |

---

## Validation Evidence

- `mvn compile -q` → BUILD SUCCESS
- `mvn test -Dtest=RepositoryStructureCollectorTest` → 11/11 pass
- `mvn test -q` → 211 tests, 6 pre-existing failures (DB not running, legacy API issues — none from this change)

---

## Residual Risks

| Risk | Severity | Mitigation |
|------|----------|-----------|
| `MODULE_BUILD_FILES` not matched in `extractModuleName` for build files at root | Low | `pom.xml` at root → first segment is `pom.xml` → `MODULE_BUILD_FILES.contains("pom.xml")` → returns "root" ✅ |

---

## Recommendation

**Approve.** Implementation matches the plan exactly. All 13 acceptance criteria satisfied. No regressions. Code is clean, deterministic, and follows existing patterns.

---

Code Review completed.

Human approval required before finalization.

Awaiting explicit human approval.
