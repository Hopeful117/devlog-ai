# Implementation Report — Story 0086

## Summary

Implemented deterministic repository evidence resolution for Insights, enabling Story 0083 temporal assessment to answer "which repository files deterministically support this trusted Insight" using the validated Proposal lineage instead of raw `evidenceReferences`.

## Changes Made

### New Files (6)

1. **shared/evidence/EvidencePathValidator.java** — Shared deterministic validator for repository-relative paths. Replicates exact semantics from `KnowledgeCollectionServiceImpl.validateEvidenceReference` (rejects absolute, drive-letter, `..`, `../`, `/../`). Provides `normalize`, `isValidRelativePath`, and `hasNonFileNamespacePrefix` (excludes 13 known namespace prefixes).

2. **repositoryevidence/RepositoryEvidenceProjection.java** — Derived projection: `source`, `baselineRevision`, `List<ResolvedFileEvidence>`. Not persisted.

3. **repositoryevidence/ResolvedFileEvidence.java** — Value record: `factId`, `path`.

4. **repositoryevidence/RepositoryEvidenceResolutionException.java** — Fail-closed exception with `Reason` enum (`LINEAGE_UNAVAILABLE`, `DATA_INTEGRITY_ERROR`).

5. **repositoryevidence/RepositoryEvidenceResolver.java** — Interface: `Optional<RepositoryEvidenceProjection> resolve(Insight)`.

6. **repositoryevidence/RepositoryEvidenceResolverImpl.java** — Implementation:
   - Reads `Insight.proposal.supportingFactIds` + `supportingObservationIds`
   - Queries `FactRepository.findByAnalysisIdAndIdIn` + `ObservationRepository.findByAnalysisIdAndIdIn` (with `@EntityGraph(supportingFacts)`)
   - Fail-closed: missing declared ID, cross-analysis, dangling observation fact → `LINEAGE_UNAVAILABLE`/`DATA_INTEGRITY_ERROR`
   - UNION of direct + observation-derived Facts, deduped by Fact ID, deterministic order (id asc)
   - Option E path classification: known-Fact origin + namespace prefix exclusion + relative-path validation
   - Returns `RepositoryEvidenceProjection`; empty Optional for genuine no-lineage (legacy fallback)

### Modified Files (5)

1. **observation/repository/ObservationRepository.java** — Added `@EntityGraph(attributePaths = "supportingFacts")` to `findByAnalysisIdAndIdIn`.

2. **collection/service/KnowledgeCollectionServiceImpl.java** — `validateEvidenceReference` now delegates to `EvidencePathValidator.isValidRelativePath` (identical behavior).

3. **temporal/service/TemporalAssessmentServiceImpl.java** — Rewrote `assess()`:
   - Calls `repositoryEvidenceResolver.resolve(insight)` first
   - Fail-closed exception → UNKNOWN
   - Optional.empty → legacy mode (filter `evidenceReferences` via Option E)
   - Projection → modern mode (use `resolvedFiles` paths)
   - Evaluation loop unchanged (baseline + current via `RepositoryStatePort`)
   - Enrichment uses original `evidenceReferences` for backward compatibility
   - CURRENT message distinguishes legacy vs modern

4. **temporal/service/TemporalAssessmentServiceImplTest.java** — Added `@Mock RepositoryEvidenceResolver` with lenient stub returning `Optional.empty()` to exercise legacy path in all existing tests.

5. **temporal/domain/TemporalAssessment.java** — No changes (existing enum `Conclusion.CURRENT/SUSPECTED_STALE/UNKNOWN` sufficient).

### New Test File (1)

- **repositoryevidence/RepositoryEvidenceResolverImplTest.java** — 16 unit tests covering:
  - Direct fact resolution
  - Observation-derived facts
  - Union + deduplication by Fact ID
  - Namespace exclusion (13 prefixes)
  - Relative-path validation (absolute, drive-letter, `..`, `../`, `/../`)
  - Plain relative paths kept
  - Missing declared Fact → LINEAGE_UNAVAILABLE
  - Cross-analysis Fact → DATA_INTEGRITY_ERROR
  - Dangling observation Fact → DATA_INTEGRITY_ERROR
  - Deterministic ordering (id asc, path asc)
  - Genuine no-lineage → Optional.empty
  - Fact IDs present → lineage path (not legacy)
  - Baseline/source propagated
  - factId carried per path
  - No writes
  - All refs excluded → empty resolvedFiles (distinct from no-lineage)

## Test Results

- **RepositoryEvidenceResolverImplTest**: 16/16 passed
- **TemporalAssessmentServiceImplTest**: 13/13 passed (all existing tests preserved)
- **Full backend suite**: 851 tests, 0 failures, 0 errors

## Verification of Non-Goals

- ❌ No persistence changes (no new tables, no Insight lineage duplication)
- ❌ No AI Engine changes
- ❌ No Context Engine changes
- ❌ No MCP/API contract changes
- ❌ No `InsightStatus` mutations
- ❌ No Story 0084 code
- ❌ No generic lineage framework
- ❌ No line-number parsing
- ❌ No repository-history reconstruction
- ❌ No new JPA methods (only `@EntityGraph` annotation on existing method)

## Diff Summary

| Category | Added | Modified | Deleted |
|----------|-------|----------|---------|
| Java Source | 7 files | 4 files | 0 |
| Test Source | 1 file | 1 file | 0 |
| **Total** | **8** | **5** | **0** |

No schema migrations, no dependency changes.