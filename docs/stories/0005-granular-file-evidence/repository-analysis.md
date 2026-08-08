# Repository Analysis

## Story Understanding

Story 0005 requests extending the `RepositoryStructureCollector` (introduced in Story 0004) to produce **individual file-level evidence** alongside the existing aggregate summaries. Today the collector scans the filesystem via `SecureRepositoryScanner`, receives individual `RepositoryFile` entries with `relativePath` and `size`, but collapses them into 5 aggregate evidence items (MODULE_SUMMARY, SOURCE_DIRECTORIES, TEST_DIRECTORIES, CONFIGURATION_FILES, FILE_EXTENSIONS). The ranker scores evidence against story terms via `semanticRelevance()` which checks `kind + summary + originatingFile` for term matches. With `originatingFile = null` on aggregate evidence, the story description has almost no influence on which evidence is selected.

The Story requests:

- **SOURCE_FILE** evidence for individual source files (under `src/main/java`, `src/main/kotlin`, `src/main/python`, `src/main/typescript`, `src/app`, `src/lib`)
- **TEST_FILE** evidence for individual test files (under `src/test/`, `__tests__/`, `test/`, `tests/`)
- **CONFIG_FILE** evidence for individual configuration files
- **MODULE** evidence with inferred module names and file counts
- **Story-aware prioritization**: files whose paths contain story terms are prioritized before collection
- **Budget-aware**: collector limits file-level output to prevent budget overflow
- Aggregate summaries preserved alongside file-level evidence

Explicitly out of scope: content reading, AST parsing, file→module mapping beyond first-segment inference, changed-file→commit association, new ranking criteria, database migrations.

---

## Repository Summary

The repository context subsystem (`repositorycontext/`) implements an evidence pipeline: **collection → ranking → selection → digest**. The `RepositoryStructureCollector` is a `@Component` at `@Order(40)` implementing `RepositoryContextCollector`. It uses `EvidenceFactory` to create `RepositoryEvidence` items with proper provenance.

The collection subsystem (`collection/collector/`) has `SecureRepositoryScanner` — a bounded filesystem walker that returns `RepositoryScan(List<RepositoryFile> files, ...)` where each `RepositoryFile` has `relativePath`, `size`, and optionally `content`.

---

## Affected Modules

### 1. `repositorycontext.collector` — Extend Existing Collector

**Package:** `com.hopeful117.devlogai.repositorycontext.collector`

**Why involved:** `RepositoryStructureCollector` must be extended to produce file-level evidence in addition to aggregate summaries.

**Current responsibility:** Produces 5 aggregate evidence items (MODULE_SUMMARY, SOURCE_DIRECTORIES, TEST_DIRECTORIES, CONFIGURATION_FILES, FILE_EXTENSIONS) from `RepositoryScan.files()`.

**Impact:** Add file-level evidence production, add story-term prioritization, add collector limit for file items.

### 2. `repositorycontext.ranking` — No Changes

**Package:** `com.hopeful117.devlogai.repositorycontext.ranking`

**Why involved:** Ranking logic is unchanged; file-level evidence with `originatingFile` set will be naturally scored.

**Impact:** None.

### 3. `collection.collector` — No Changes (Reused)

**Package:** `com.hopeful117.devlogai.collection.collector`

**Why involved:** `SecureRepositoryScanner` already provides individual `RepositoryFile` entries. No modifications needed.

**Impact:** None.

---

## Existing Implementation

### `RepositoryStructureCollector.collect()` (lines 65-94)

```java
List<RepositoryEvidence> evidence = new ArrayList<>();
evidence.add(moduleSummaryEvidence(scan, source.getId().toString(), request));
evidence.addAll(sourceDirectoryEvidence(scan, source.getId().toString(), request));
evidence.addAll(testDirectoryEvidence(scan, source.getId().toString(), request));
evidence.addAll(configurationFileEvidence(scan, source.getId().toString(), request));
evidence.addAll(fileExtensionEvidence(scan, source.getId().toString(), request));
return List.copyOf(evidence.stream().limit(5).toList());
```

**Key insight:** The collector already has `scan.files()` (individual `RepositoryFile` entries). The `.limit(5)` at the end returns only 5 items, collapsing everything into aggregate summaries. Removing this limit and adding file-level evidence is the approach.

### `EvidenceFactory.create()` (evidence-factories reference)

Creates `RepositoryEvidence` with:
- `layer`, `kind`, `reference`, `summary`, `occurredAt`
- `relatedReferences` (empty for file evidence)
- `originatingFile` — this is critical for story-term matching in the ranker
- `estimatedTokens` (bounded by `maximumSummaryCharacters`)

### `DeterministicEvidenceRanker.semanticRelevance()` (evidence-based analysis)

Extracts terms from `intent.id + intent.objective`, filters terms ≥3 chars, then checks:
```java
evidence.kind() + " " + evidence.summary() + " " + Objects.toString(evidence.provenance().originatingFile(), "")
```

For aggregate evidence: `originatingFile = null` → no path matching possible.
For file evidence: `originatingFile = relativePath` → strong path-based term matching.

### `RepositoryContextLayer` enum (already defined)

`RELATED_SOURCE_CODE` layer exists. `COMMIT_DIFF` layer also exists but no collector produces it (separate story).

### Existing Tests (`RepositoryStructureCollectorTest`)

5 tests:
1. `producesRelatedSourceCodeLayer()` — verifies `RELATED_SOURCE_CODE` layer
2. `producesModuleSummaryEvidence()` — verifies MODULE_SUMMARY with multi-module detection
3. `producesSourceDirectoryEvidence()` — verifies SOURCE_DIRECTORIES
4. `returnsEmptyListWhenNoSource()` — graceful degradation
5. `returnsEmptyListWhenWorkspaceUnavailable()` — graceful degradation

Tests use `.limit(5)` assert. After adding file evidence, tests need updating.

---

## Relevant Documentation

- `docs/stories/0004-repository-structure-collector/repository-analysis.md` — similar pattern
- `docs/decisions/ADR-037.md` — Repository-First Context Extraction
- `docs/decisions/ADR-038.md` — Repository Context Engine
- `docs/decisions/ADR-040.md` — Knowledge and Evidence Separation
- `docs/pipeline.md` — Context pipeline flow
- Story 0005: `docs/stories/0005-granular-file-evidence/story.md`

---

## Constraints

1. **No content reading** — `SecureRepositoryScanner` must remain called with `includeContent = false`. All relevance is path-based.

2. **No interface changes** — `ContextRequest`, `RepositoryContextCollector`, `RepositoryContextEngine`, `RepositoryEvidence` must remain unchanged.

3. **Aggregate evidence preserved** — existing 5 aggregate items must continue to be produced. File-level evidence is ADDITIONAL.

4. **Budget-aware** — `ContextBudget` enforces `maxEvidenceItems=60`, `maxTokens=6000`. Collector must limit file items to prevent overflow.

5. **Deterministic** — story-term prioritization must not involve AI or content reading. Path-based string matching only.

6. **Provenance correct** — all file-level evidence must have `sourceType="REPOSITORY_STRUCTURE"`, `collectorId="repository-structure"`, `collectorVersion="v1"`.

7. **Ranking integration** — `originatingFile = relativePath` enables `DeterministicEvidenceRanker` to score file-level evidence against story terms.

8. **Existing tests pass** — no regressions.

---

## Risks

### R1: Evidence count may exceed budget

**Risk:** 500+ files could produce 500+ evidence items, blowing past `maxTokens=6000` and `maxEvidenceItems=60`.

**Mitigation:** 
- Collector-level limit (default 40 file items)
- Story-term prioritization reduces irrelevant items
- `BudgetedDiverseEvidenceSelector` enforces final limits

### R2: Module inference from path may be inaccurate

**Risk:** Module name derived from first path segment before source root. Non-standard layouts could produce wrong names.

**Mitigation:** Accept for V1. MODULE evidence includes full path for traceability if name seems wrong.

### R3: Ranker scoring complexity

**Risk:** File paths with many segments could create noisy term matches (e.g., common words like "service", "model" matching many files).

**Mitigation:** 
- Story-term matching uses exact substring match (deterministic)
- Ranker combines multiple scores (semantic + architectural + historical + recency + confidence + guidance)
- Budget limit caps total output

### R4: Existing tests need updates

**Risk:** `RepositoryStructureCollectorTest` assertions check for exactly 5 items or specific kinds.

**Mitigation:** Update tests to check for presence of expected kinds rather than exact counts.

---

## Open Questions

None. All implementation details are clear based on existing code patterns.

---

## Recommendation

**Ready for planning.**

The repository is well understood. The `RepositoryStructureCollector` already has access to individual `RepositoryFile` entries via `SecureRepositoryScanner.scan()`. The `DeterministicEvidenceRanker` already has the scoring logic to match file paths against story terms. The `BudgetedDiverseEvidenceSelector` handles resource limits. No new components, interfaces, or architectural decisions are needed.

---

## Implementation Readiness

All technical prerequisites are met:
- ✅ `SecureRepositoryScanner` provides individual `RepositoryFile` entries
- ✅ `EvidenceFactory` can create file-level evidence with `originatingFile` populated
- ✅ `DeterministicEvidenceRanker.semanticRelevance()` scores `originatingFile` against story terms
- ✅ `engineering-story-v1` profile already prioritizes `RELATED_SOURCE_CODE` layer
- ✅ `ContextBudget` provides `maximumSummaryCharacters` for bounding evidence
- ✅ No ADR conflicts (ADR-037, ADR-040 support this approach)

---

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
