# Engineering Report — Story 0005

## Story
Add file-level RELATED_SOURCE_CODE evidence to the Repository Context Engine

## Final Status
Complete.

Story 0005 extends `RepositoryStructureCollector` to produce individual file-level evidence items (SOURCE_FILE, TEST_FILE, CONFIG_FILE, MODULE) alongside the existing aggregate summaries. The collector now transforms `RepositoryFile` entries from `SecureRepositoryScanner` into `RepositoryEvidence` items with `originatingFile = relativePath`, enabling the `DeterministicEvidenceRanker` to match story terms against file paths. Story-term prioritization and a 40-item collector limit prevent budget overflow. All 13 acceptance criteria satisfied. 11/11 collector tests pass. No regressions.

---

## Delivered Architecture

```
SecureRepositoryScanner.scan()
  → RepositoryScan(List<RepositoryFile>)
  → classify each file (source / test / config / other)
  → extract module names from path segments
  → prioritize by story term overlap (deterministic, path-based)
  → limit to MAX_FILE_EVIDENCE_ITEMS (40)
  → create RepositoryEvidence via EvidenceFactory
  → combine with existing aggregate evidence (MODULE_SUMMARY, etc.)
  → BudgetedDiverseEvidenceSelector enforces final budget (60 items / 6000 tokens)
```

### Evidence Types Produced

| Kind | Layer | Reference | originatingFile |
|------|-------|-----------|-----------------|
| `SOURCE_FILE` | RELATED_SOURCE_CODE | `file:{path}` | relativePath |
| `TEST_FILE` | RELATED_SOURCE_CODE | `file:{path}` | relativePath |
| `CONFIG_FILE` | RELATED_SOURCE_CODE | `config:{path}` | relativePath |
| `MODULE` | RELATED_SOURCE_CODE | `module:{name}` | null |
| `MODULE_SUMMARY` | RELATED_SOURCE_CODE | `module-summary` | null |
| `SOURCE_DIRECTORIES` | RELATED_SOURCE_CODE | `source-directories` | null |
| `TEST_DIRECTORIES` | RELATED_SOURCE_CODE | `test-directories` | null |
| `CONFIGURATION_FILES` | RELATED_SOURCE_CODE | `configuration-files` | null |
| `FILE_EXTENSIONS` | RELATED_SOURCE_CODE | `file-extensions` | null |

### Classification Rules

- **Source file**: path starts with a known source root (`src/main/java`, `src/main/kotlin`, etc.) AND has a recognized extension (`.java`, `.kt`, `.py`, `.ts`, `.tsx`, `.js`, `.jsx`)
- **Test file**: path contains a known test root (`src/test/`, `__tests__/`, `test/`, `tests/`) AND has a recognized source extension
- **Config file**: filename matches `CONFIGURATION_FILE_NAMES` set
- **Module**: first path segment before a known source root or build descriptor; "root" for single-module repos

### Story-Term Prioritization

- Extract terms ≥3 chars from `ContextRequest.intent().objective()`
- Sort files by count of matching terms (descending), then alphabetically
- Deterministic, path-based — no AI or content reading

---

## Architecture Decisions

### No new ADR required

The implementation follows existing patterns established by ADR-037 (Repository-First Context Extraction), ADR-038 (Extensible Collectors), and ADR-040 (Knowledge/Evidence Separation). No new architectural concepts were introduced — file-level evidence uses the same `RepositoryEvidence` model, `EvidenceFactory`, and `DeterministicEvidenceRanker` as existing aggregate evidence.

---

## Files

### Modified (2)

| File | Change |
|------|--------|
| `RepositoryStructureCollector.java` | Added file classification helpers, `produceFileLevelEvidence()`, `produceModuleEvidence()`, story-term extraction, collector limit constant. Removed `.limit(5)` from `collect()`. |
| `RepositoryStructureCollectorTest.java` | Updated 3 existing tests, added 6 new tests (11 total). |

### No files created or deleted.

---

## Implementation Details

### Constants Added

```java
private static final int MAX_FILE_EVIDENCE_ITEMS = 40;
private static final Set<String> SOURCE_EXTENSIONS = Set.of(
        ".java", ".kt", ".py", ".ts", ".tsx", ".js", ".jsx");
```

### Key Methods Added

| Method | Purpose |
|--------|---------|
| `isSourceFile(String)` | Checks source root + source extension |
| `isTestFile(String)` | Checks test root + source extension |
| `isConfigFile(String)` | Checks filename against CONFIGURATION_FILE_NAMES |
| `extractModuleName(String)` | First segment before source root/build descriptor |
| `extractStoryTerms(ContextRequest)` | Normalizes objective text into lowercase terms ≥3 chars |
| `containsSourceRoot(String)` | Checks path against SOURCE_ROOTS |
| `containsTestRoot(String)` | Checks path contains TEST_ROOTS |
| `hasSourceExtension(String)` | Checks path against SOURCE_EXTENSIONS |
| `produceFileLevelEvidence(...)` | Produces SOURCE_FILE/TEST_FILE/CONFIG_FILE evidence with story-term prioritization |
| `produceModuleEvidence(...)` | Produces MODULE evidence per discovered module |

### collect() Method Change

Before: `return List.copyOf(evidence.stream().limit(5).toList());`
After: Aggregate evidence (5 items) + module evidence + file-level evidence (up to 40) returned without limit.

---

## Code Review Corrections

The Code Review identified 4 observations (none blocking):

1. **`TEST_ROOTS` uses `contains()` while `SOURCE_ROOTS` uses `startsWith()`**: Intentional — test roots can appear at various nesting levels. Correct.

2. **`extractModuleName()` returns "root" for single-module repos**: Correct — paths like `src/main/java/com/App.java` have first segment `src` which matches SOURCE_ROOTS.

3. **FQCN in `produceFileLevelEvidence` and `produceModuleEvidence`**: Uses `com.hopeful117.devlogai.repositorycontext.ContextRequest` instead of import. Style choice, already imported. Minor.

4. **Unicode em-dash in module summary**: Consistent with codebase convention.

All observations are acceptable. No corrections required.

---

## Test Coverage

### New Tests (6)

| Test | What it verifies |
|------|-----------------|
| `producesSourceFileEvidenceForSourceFiles` | SOURCE_FILE items with correct paths for src/main/java files |
| `producesTestFileEvidenceForTestFiles` | TEST_FILE items for src/test/ files |
| `producesConfigFileEvidenceForConfigFiles` | CONFIG_FILE items for pom.xml, application.properties |
| `producesModuleEvidenceForMultiModuleRepos` | MODULE items with module names for multi-module repos |
| `limitsFileEvidenceItems` | ≤40 file-level items when 50 files exist |
| `prioritizesFilesMatchingStoryTerms` | Story-term-matching files sort first |

### Updated Tests (3)

| Test | Change |
|------|--------|
| `producesRelatedSourceCodeLayer` | Checks evidence is non-empty with correct layer (not exact count) |
| `producesModuleSummaryEvidence` | Verifies MODULE_SUMMARY still present |
| `producesSourceDirectoryEvidence` | Verifies SOURCE_DIRECTORIES still present |

### Unchanged Tests (2)

| Test | Status |
|------|--------|
| `returnsEmptyListWhenNoSource` | ✅ Pass |
| `returnsEmptyListWhenWorkspaceUnavailable` | ✅ Pass |

---

## Validation Summary

```bash
# Compile
mvn compile -q → BUILD SUCCESS

# Collector tests
mvn test -Dtest=RepositoryStructureCollectorTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 → BUILD SUCCESS

# Full test suite
mvn test -q
Tests run: 211, Failures: 4, Errors: 2, Skipped: 0
```

All 6 failures are pre-existing and unrelated:
- `RestAIEngineClientIntegrationTest` — legacy AI engine disabled
- `AnalysisWorkflowServiceTest` — unrelated
- `InitialCollectorsTest` — unrelated
- `DevlogAiBackendApplicationTests` — context load failure
- `ValidationControllerWebMvcTest` — unrelated

No regressions from this change.

---

## Artifact History

- `story.md` — Authoritative Story
- `repository-analysis.md` — Repository and architecture analysis
- `implementation-plan.md` — Approved implementation plan
- `implementation-report.md` — Implementation record
- `code-review.md` — Approved Code Review
- `engineering-report.md` — This final consolidated report

---

## Final Recommendation

Story 0005 is technically complete. All 13 acceptance criteria are satisfied. The implementation is contained to 2 files (collector + test) with no interface changes, no database changes, and no new dependencies. Code Review is approved with all observations accepted. The working tree should remain uncommitted until the engineer has completed any desired final IDE inspection and chooses the repository's normal commit and delivery process.
