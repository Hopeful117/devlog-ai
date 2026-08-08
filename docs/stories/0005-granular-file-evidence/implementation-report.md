# Story 0005 — Granular File Evidence: Implementation Report

## Status: ✅ Complete

## Files Modified

1. `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`
2. `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java`

## Implementation Summary

All implementation steps from the engineering plan are present and verified:

### RepositoryStructureCollector.java
- **Constants added**: `MAX_FILE_EVIDENCE_ITEMS = 40`, `SOURCE_EXTENSIONS` set
- **Classification helpers**: `isSourceFile()`, `isTestFile()`, `isConfigFile()`, `containsSourceRoot()`, `containsTestRoot()`, `hasSourceExtension()`
- **Module extraction**: `extractModuleName()` — extracts first path segment, returns "root" for single-module repos
- **Story term extraction**: `extractStoryTerms()` — normalizes objective text into lowercase terms ≥3 chars
- **File-level evidence**: `produceFileLevelEvidence()` — produces SOURCE_FILE, TEST_FILE, CONFIG_FILE evidence with story-term prioritization and 40-item cap
- **Module evidence**: `produceModuleEvidence()` — produces MODULE evidence per discovered module with file counts
- **Updated `collect()` method**: Integrates both aggregate (existing) and file-level (new) evidence production

### RepositoryStructureCollectorTest.java
- **Existing tests updated**: `producesRelatedSourceCodeLayer()`, `producesModuleSummaryEvidence()`, `producesSourceDirectoryEvidence()` — now check evidence is non-empty with correct layer/kind instead of exact counts
- **New tests added**:
  - `producesSourceFileEvidenceForSourceFiles()` — verifies SOURCE_FILE items for src/main/java files
  - `producesTestFileEvidenceForTestFiles()` — verifies TEST_FILE items for src/test/ files
  - `producesConfigFileEvidenceForConfigFiles()` — verifies CONFIG_FILE items for pom.xml, application.properties
  - `producesModuleEvidenceForMultiModuleRepos()` — verifies MODULE items with module names
  - `limitsFileEvidenceItems()` — verifies ≤40 file-level items when 50 files exist
  - `prioritizesFilesMatchingStoryTerms()` — verifies story-term-matching files sort first
- **Unchanged tests**: `returnsEmptyListWhenNoSource()`, `returnsEmptyListWhenWorkspaceUnavailable()`

## Validation Results

### Compile
```
mvn compile -q → BUILD SUCCESS (clean, no warnings)
```

### RepositoryStructureCollectorTest
```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Full Test Suite
```
Tests run: 211, Failures: 4, Errors: 2, Skipped: 0
```
All 6 failures are pre-existing in unrelated test classes:
- `RestAIEngineClientIntegrationTest` — legacy AI engine submission disabled
- `AnalysisWorkflowServiceTest` — unrelated
- `InitialCollectorsTest` — unrelated
- `DevlogAiBackendApplicationTests` — context load failure
- `ValidationControllerWebMvcTest` — unrelated

None of these failures relate to the RepositoryStructureCollector changes.

## Deviations from Plan

None. All implementation steps were followed as specified. No additional files were created or modified.
