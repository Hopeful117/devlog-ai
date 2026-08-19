# Story 0086 — Implementation Plan

## Execution Context

- **Branch**: story/0086-deterministic-repository-evidence-resolution
- **Base**: main at a31ddba (Story 0085 merged)
- **ADR Compliance**: ADR-059 (Temporal Engineering Knowledge), ADR-060 (Deterministic
  Core), ADR-006 (Governance), ADR-058 (Data Lineage), ADR-061 (Repository Baselines)
- **Classification**: A. READY_FOR_STORY_0086 (no new ADR required)
- **Architecture**: 1. DERIVED_ONLY — deterministic derivation from the trusted Proposal
  lineage; zero persistence, zero AI/MCP change

## Corrections Applied (during analysis)

1. **LINEAGE_SOURCE_CORRECTION**: `resolveRepositoryEvidence` = validatable proposal lineage
   (Insight.proposal.supportingFactIds / supportingObservationIds), NOT free-form
   `evidenceReferences`. Evidence references are reception metadata; the proposal carries
   the human-validated lineage.
2. **FAIL_CLOSED_CORRECTION**: any partial/inconsistent lineage (missing, cross-Analysis,
   dangling observation fact) invalidates the WHOLE resolution -> UNKNOWN. No silent
   partial verdict, no fallback from a modern corrupt lineage.
3. **NAMESPACE_EXCLUSION_CORRECTION**: `source:<uuid>` and `repository:/` are scope
   markers, not files; `git:*` excluded; only plain relative workspace paths survive as
   evidence. Verified against 42,435 plain / 32,421 source: / 1,069 repository: / 138 git:
   runtime references.
4. **BASELINE_AUTHORITY_CORRECTION**: `Analysis.selectedSource` + `Analysis.targetRevision`
   remain the baseline. Facts are evidence only; `fact.analysis.id == insight.analysis.id`
   invariant enforced. currentKnownRevision ownership stays in
   TemporalAssessmentServiceImpl.
5. **LEGACY_BOUNDARY_CORRECTION**: legacy fallback (assess genuine `evidenceReferences`
   paths) is allowed only for a GENUINE no-lineage Proposal (empty supportingFactIds AND
   empty supportingObservationIds). A fail-closed (modern corrupt) lineage returns UNKNOWN
   and MUST NOT fall back.

## Exact Production Packages / Files

### New Files

| File | Package | Purpose |
|---|---|---|
| `RepositoryEvidenceResolutionException.java` | `com.hopeful117.devlogai.repositoryevidence` | Internal fail-closed marker (lineage incomplete/inconsistent) |
| `RepositoryEvidenceProjection.java` | `com.hopeful117.devlogai.repositoryevidence` | Derived projection: `source`, `baselineRevision`, `List<ResolvedFileEvidence>` (NOT persisted) |
| `ResolvedFileEvidence.java` | `com.hopeful117.devlogai.repositoryevidence` | value record: `factId`, `path` |
| `RepositoryEvidenceResolver.java` | `com.hopeful117.devlogai.repositoryevidence` | Resolver: proposal lineage -> UNION(direct + observation-derived Facts), fail-closed, Option E classification -> projection |

> Proposed names only; adjust to package conventions at implementation. Resolver depends
> only on FactRepository, ObservationRepository, and domain entities (read-only).

### Modified Files

| File | Change | Reason |
|---|---|---|
| `TemporalAssessmentServiceImpl.java` | Call `RepositoryEvidenceResolver` first; assess the projection's resolved paths instead of raw `evidenceReferences`; implement genuine-no-lineage legacy fallback | Provide deterministic repository evidence set |
| `KnowledgeCollectionServiceImpl.java` | Extract `validateEvidenceReference` relative-path semantics into a reusable shared component (or expose package-visible static helper) | Single source of truth for relative-path validation consumed by resolver |

### NO Changes

- No entity modifications, no new JPA mappings
- No Flyway / migrations / new tables
- No changes to Insight.proposal association (`updatable=false` preserved)
- No changes to ValidatableProposal / review / InsightPromotionService
- No AI Engine / Context Engine / MCP / API changes
- No changes to `RepositoryStatePort`, `GitWorkspaceManager.isFilePresentAtRevision`,
  `ProjectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc` (Story 0083)
- No change to `FactRepository.findByAnalysisIdAndIdIn` /
  `ObservationRepository.findByAnalysisIdAndIdIn` (line 26 both) — reused as-is

## Exact Derived Domain Objects

```java
package com.hopeful117.devlogai.repositoryevidence;

import java.util.List;
import java.util.UUID;

import com.hopeful117.devlogai.source.entity.Source;

public record RepositoryEvidenceProjection(
        Source source,
        String baselineRevision,
        List<ResolvedFileEvidence> resolvedFiles) {
}

public record ResolvedFileEvidence(UUID factId, String path) {
}

/** Fail-closed marker. Mirrors no public enum; internal diagnostics only. */
public final class RepositoryEvidenceResolutionException extends RuntimeException {
    public enum Reason { LINEAGE_UNAVAILABLE, DATA_INTEGRITY_ERROR }
    // constructors, getters
}
```

## Exact Deterministic Evidence Source

**Input**: `Insight I`, `analysis = I.getAnalysis()` (non-null), `proposal = I.getProposal()`
(mandatory FK, always non-null).

**Baseline** (unchanged, Story 0083/0085): `analysis.selectedSource` +
`analysis.targetRevision`. `projection.source == analysis.selectedSource`;
`projection.baselineRevision == analysis.targetRevision`.

**Resolution logic**:

```text
resolveRepositoryEvidence(insight):

  proposal = insight.getProposal()

  if proposal.supportingFactIds empty AND proposal.supportingObservationIds empty:
      return GENUINE_NO_LINEAGE            # legacy fallback path allowed

  directFacts = factRepository.findByAnalysisIdAndIdIn(analysis.id, supportingFactIds)
  observed    = observationRepository.findByAnalysisIdAndIdIn(analysis.id, supportingObservationIds)
                 # ObservationRepository line 26 already carries @EntityGraph(supportingFacts) variant

  fail-closed checks (ANY failure -> RepositoryEvidenceResolutionException):
    - every supportingFactId maps to a Fact with fact.analysis.id == analysis.id
    - every supportingObservationId maps to an Observation with observation.analysis.id == analysis.id
    - each Observation.supportingFacts non-empty and every supportingFact has
      fact.analysis.id == analysis.id (no cross-analysis / dangling)
  # if proposal lists no Fact ids and no Observation ids at all -> GENUINE_NO_LINEAGE (as above)

  allFacts = LinkedHashSet(directFacts) U { observation.supportingFacts for each observed }
  # FAIL-CLOSED: if any union element duplicates id but differs in content/type/source
  #              -> RepositoryEvidenceResolutionException (DATA_INTEGRITY_ERROR)

  resolvedPathSet = LinkedHashSet<String>     # deterministic insertion order
  pairs = LinkedHashMap<UUID, String>         # factId -> first contributing path

  for each fact in allFacts ordered by fact.id asc:            # deterministic order
      for each ref in fact.evidenceReferences ordered deterministic:
          if NAMESPACE_PREFIX(ref) != null:                    # e.g. analysis:, source:,
              continue                                         # commit:, git:, diff:,
                                                               # fact:, observation:,
                                                               # decision:, insight:,
                                                               # story:, artifact:,
                                                               # milestone:, repository:
          if !VALID_RELATIVE_PATH(ref):                        # shared validator (see below)
              continue
          path = normalize(ref)
          if path not already present: pairs.put(fact.id, path)

      resolvedFiles = pairs.entrySet()
          .map(e -> ResolvedFileEvidence(e.getKey(), e.getValue()))
          .sorted(by path)                                     # deterministic output

      return RepositoryEvidenceProjection(analysis.selectedSource,
                                          analysis.targetRevision,
                                          resolvedFiles)       # possibly empty resolvedFiles
```

**Namespace prefix set** (exact, prefix match on the reference string, case-insensitive):
`analysis:`, `source:`, `commit:`, `git:`, `diff:`, `fact:`, `observation:`,
`decision:`, `insight:`, `story:`, `artifact:`, `milestone:`, `repository:`.

**Relative-path validation** (shared, identical semantics to
`KnowledgeCollectionServiceImpl.validateEvidenceReference` lines 226-233):

```text
normalize: replace '\' with '/'
reject if:
    startsWith "/"
    matches "^[A-Za-z]:/."           # drive-letter absolute
    equals ".." or startsWith "../"
    contains "/../"
```

No line-number parsing (`:line`). File-level granularity is sufficient (verified: zero
`<path>:<line>` production references).

## Temporal Assessment Integration

`TemporalAssessmentServiceImpl.assess(insight)`:

1. If `insight.status != ACTIVE` -> throw IllegalStateException (NOT_APPLICABLE —
   unchanged).
2. Call `repositoryEvidenceResolver.resolve(insight)`:
   - normal projection -> use `projection.resolvedFiles` paths as the evaluated evidence set;
   - `GENUINE_NO_LINEAGE` -> legacy fallback: evaluated set =
     genuine repository-path references from `analysis.evidenceReferences` (plain relative
     paths passing relative-path validation; namespaces excluded; `analysis:<uuid>` -> the
     ref is UNKNOWN, never a path);
   - `RepositoryEvidenceResolutionException` -> UNKNOWN ("lineage unavailable" /
     "data integrity error"); MUST NOT fall back to legacy.
3. Proceed with the UNCHANGED Story 0083 evaluation pipeline over the evaluated set:
   baseline = `analysis.selectedSource` + `analysis.targetRevision` (present/absent),
   current = `analysis.selectedSource` + `currentKnownRevision`
   (`ProjectCommitRepository.findTopBySourceId...`, lines 147-155), each path via
   `RepositoryStatePort.isFilePresentAtRevision(selectedSource, hash, path)`.
4. `evidenceReferences` remains untouched; it continues to serve generic grounding /
   display metadata and, solely in the genuine-no-lineage legacy case, temporal evidence.

## Exact Conclusion Rules

| Condition | conclusion | supportingEvidence |
|---|---|---|
| Lineage normal; ≥1 resolved path; baseline has ≥1 resolved path present; ALL baseline-present paths also present at currentKnownRevision | CURRENT | "All N resolved repository evidence files verified present at baseline 'B' and currentKnownRevision 'C' (source S)" |
| Lineage normal; ≥1 resolved path present at baseline AND absent at currentKnownRevision | SUSPECTED_STALE | "File 'P' present at baseline 'B' (source S), absent at currentKnownRevision 'C'" |
| Lineage normal; resolvedFiles empty (all refs excluded/failed validation) | UNKNOWN | "No repository evidence resolved from lineage" |
| Lineage normal; selectedSource null | UNKNOWN | "Insufficient evidence: Analysis.selectedSource unavailable" |
| Lineage normal; targetRevision null/blank | UNKNOWN | "Insufficient evidence: baseline revision unavailable" |
| currentKnownRevision unavailable | UNKNOWN | "Insufficient evidence: currentKnownRevision cannot be determined for source" |
| RepositoryStatePort fails / unavailable | UNKNOWN | "Insufficient evidence: repository state verification unavailable" |
| Fail-closed (RepositoryEvidenceResolutionException) | UNKNOWN | "Lineage unavailable" / "Data integrity error: lineage inconsistent" |
| GENUINE_NO_LINEAGE -> legacy fallback (genuine evidenceReferences paths) | per Story 0083 rules | "Legacy evidence references evaluated (no proposal lineage)" |
| Insight status != ACTIVE | NOT_APPLICABLE | — (service throws) |

## V1 Final Semantics

```
Non-ACTIVE Insight
    -> NOT_APPLICABLE / rejected at boundary

ACTIVE + RESOLVER FAIL-CLOSED
    -> UNKNOWN (lineage unavailable / data integrity error)

ACTIVE + no lineage (genuine no-lineage)
    -> legacy fallback: evaluate genuine evidenceReferences paths per Story 0083

ACTIVE + lineage, no resolved evidence (all refs excluded)
    -> UNKNOWN

ACTIVE + lineage + missing selectedSource / targetRevision / currentKnownRevision
    -> UNKNOWN

For each resolved repository evidence path:
    present at baseline = true
    present at currentKnownRevision = false
        -> SUSPECTED_STALE

All baseline-present resolved paths present at currentKnownRevision
        -> CURRENT
```

## CURRENT Semantics (Final)

CURRENT means:

> The complete, fail-closed-validated lineage of this Insight resolved to ≥1 repository
> evidence file, all resolved baseline-present files were verified present at baseline
> (Analysis.selectedSource + Analysis.targetRevision) AND at currentKnownRevision
> (same Source), and no supported temporal degradation was observed for that resolved
> evidence set.

CURRENT does NOT mean:

> The semantic truth of the Insight has been globally proven.

## Tests

Test framework: existing project conventions (JUnit 5; run with the project's test
command). Test files live next to the code under test (per Story 0083/0085 convention).
No new external test dependencies.

### Unit Tests — RepositoryEvidenceResolver (proposed class `RepositoryEvidenceResolverTest`)

| Test | Expected |
|---|---|
| 1 `resolves_DirectFacts_Union` | supportingFactIds -> facts resolved; projection contains each fact's eligible evidence paths |
| 2 `resolves_ObservationDerivedFacts` | supportingObservationIds -> observation.supportingFacts contributed (via EntityGraph) |
| 3 `deduplicates_ByFactId` | same fact referenced directly and by observation -> appears exactly once |
| 4 `excludesNamespaceReferences` | refs `source:`, `repository:`, `git:`, `analysis:`, `fact:`, `observation:`, `commit:`, `diff:`, `decision:`, `insight:`, `story:`, `artifact:`, `milestone:` dropped |
| 5 `excludesNonRelativePaths` | `/abs`, `C:/drive`, `..`, `../x`, `a/../b` dropped (shared validator) |
| 6 `keepsPlainRelativePaths` | plain relative paths kept; `\` normalized to `/` |
| 7 `missingFactId_FailsClosed` | supportingFactId resolves to no Fact in this analysis -> RepositoryEvidenceResolutionException |
| 8 `crossAnalysisFact_FailsClosed` | resolved Fact has different analysis.id -> exception |
| 9 `observationWithDanglingFact_FailsClosed` | observation.supportingFacts contains absent/cross-analysis Fact -> exception |
| 10 `deterministicOrder` | same input twice -> identical projection (fact id asc, paths sorted) |
| 11 `emptyLineage_ReturnsNoLineage` | empty supportingFactIds AND empty supportingObservationIds -> GENUINE_NO_LINEAGE |
| 12 `emptyLineage_WithOnlyObservationIdsEmpty_WhenFactIdsPresent` | supportingFactIds non-empty -> lineage path, not legacy |
| 13 `baselineAndSource_Propagated` | projection.source == analysis.selectedSource; baselineRevision == analysis.targetRevision |
| 14 `projection_CarriesFactIdPerPath` | each ResolvedFileEvidence.factId matches contributing fact |
| 15 `noWrites_Resolver` | resolver issues only read queries; no save/persist |
| 16 `allRefsExcluded_EmptyResolvedFiles` | all refs are namespace/validation-excluded -> empty resolvedFiles (distinct from GENUINE_NO_LINEAGE) |

### Service Integration Tests — TemporalAssessmentServiceImpl (extend existing `TemporalAssessmentServiceImplTest`)

| Test | Expected |
|---|---|
| 17 `current_When_ResolvedEvidence_PositivelyVerified` | projection with ≥1 path, all baseline-present and current-present -> CURRENT |
| 18 `suspectedStale_When_ResolvedPath_DeletedAtCurrent` | ≥1 resolved path baseline-present, current-absent -> SUSPECTED_STALE |
| 19 `unknown_When_ResolverFailsClosed` | resolver throws -> UNKNOWN; evidenceReferences NOT evaluated |
| 20 `unknown_When_NoResolvedEvidence` | resolvedFiles empty -> UNKNOWN |
| 21 `unknown_When_SelectedSource_Missing` | selectedSource null -> UNKNOWN |
| 22 `unknown_When_TargetRevision_Missing` | targetRevision null/blank -> UNKNOWN |
| 23 `unknown_When_CurrentKnownRevision_Missing` | no commits for source -> UNKNOWN |
| 24 `unknown_When_Port_Fails` | RepositoryStatePort throws -> UNKNOWN |
| 25 `legacyFallback_ForGenuineNoLineage` | GENUINE_NO_LINEAGE -> evaluates genuine evidenceReferences paths per Story 0083 rules |
| 26 `noLegacyFallback_FromFailClosed` | resolver exception -> UNKNOWN, legacy path NEVER invoked |
| 27 `authority_Unchanged` | Insight.status unchanged; no save() calls |
| 28 `source_Scoped_AllStateCalls` | RepositoryStatePort called with selectedSource for baseline and current |

### Regression / Boundary Tests

| Test | Expected |
|---|---|
| 29 `legacy_analysisNamed_Reference_IsUnknown` | legacy path, evidenceReference `analysis:<uuid>` -> UNKNOWN (not a path) |
| 30 `legacyNoLineage_AllRefsNamespaceExcluded` | legacy path, refs excluded -> UNKNOWN |
| 31 `legacy_CallUseOnlyForGenuineNoLineage` | lineage present -> resolver results used, evidenceReferences untouched |

### Repository Integration Test

| Test | Expected |
|---|---|
| 32 `findByAnalysisIdAndIdIn_WithSupportingFacts` | ObservationRepository returns observations with supportingFacts populated (EntityGraph); resolver union complete |

## Test Categories

A. RESOLVER_UNION_AND_DEDUPE
B. OPTION_E_CLASSIFICATION (namespaces + relative-path validation)
C. FAIL_CLOSED_LINEAGE
D. DETERMINISTIC_ORDER
E. GENUINE_NO_LINEAGE -> LEGACY vs FAIL_CLOSED -> NO_FALLBACK
F. TEMPORAL_CONCLUSIONS (CURRENT / SUSPECTED_STALE / UNKNOWN over resolved set)
G. AUTHORITY_UNCHANGED / NO WRITES
H. SOURCE_SCOPED_REPOSITORY_STATE

## Transaction Behavior

- `RepositoryEvidenceResolver` and `TemporalAssessmentServiceImpl.assess()` are
  `@Transactional(readOnly = true)`.
- No writes to any entity; no save/persist/merge.
- Resolver uses only `FactRepository.findByAnalysisIdAndIdIn` and
  `ObservationRepository.findByAnalysisIdAndIdIn` (existing methods, line 26).

## No-Write Guarantees

1. Insight.status NEVER modified
2. Insight.proposal NEVER modified (immutable association preserved)
3. No ValidatableProposal / Validation modification
4. No Facts / Observations created or modified
5. No database writes (projection is a derived, non-persisted object)
6. No maintenance findings
7. No context eligibility changes
8. No changes to InsightServiceImpl / InsightPromotionService / review workflow
9. No changes to RepositoryStatePort or its Story 0083 implementation

## Explicit Non-Goals

- ❌ No persistence of the resolution / no lineage duplication on Insight
- ❌ No new database tables or migrations
- ❌ No new repositories beyond reused existing methods
- ❌ No AI-assisted evidence determination
- ❌ No AI Engine / Context Engine / MCP / API changes
- ❌ No change to Proposal creation or promotion lifecycle
- ❌ No re-inference of repository evidence from free-form evidenceReferences (except
  genuine-no-lineage legacy compatibility)
- ❌ No graceful degradation from a modern corrupt lineage (fail-closed; no fallback)
- ❌ No line-number granularity / `:line` parsing
- ❌ No repository-history reconstruction or rename tracking
- ❌ No repository synchronization behavior
- ❌ No changed-file-history-heavy conclusion (Story 0083 corroboration only, unchanged)
- ❌ No generic lineage framework / no broad provenance subsystem
- ❌ No change to conclusion semantics of Story 0083
- ❌ No change to ADR-061 status (separate human decision)
- ❌ No change to currentKnownRevision ownership

## Expected Production Diff Size

| Category | Add | Modify | Delete |
|---|---|---|---|
| Java Source | +4 files (4 new types/classes) | +1 file (TemporalAssessmentServiceImpl) +1 shared validation extraction (KnowledgeCollectionServiceImpl) | 0 |
| Test Source | +2 files (resolver test + timeline additions; existing temporal test extended in place) | +1 | 0 |
| **Total Source Files** | **+6** | **+2** | **0** |

No schema diff. No repository-POM/dependency changes.

## Approval Checklist

- [ ] Lineage source = Insight.proposal.supportingFactIds / supportingObservationIds
      (NOT free-form evidenceReferences)
- [ ] Dedicated RepositoryEvidenceResolver; TemporalAssessmentServiceImpl is the single
      consumer
- [ ] UNION of direct + observation-derived Facts, dedupe by Fact ID, deterministic order
- [ ] Fail-closed lineage (partial/cross-analysis/dangling -> UNKNOWN; no silent partial)
- [ ] Option E classification: known-Fact origin + namespace exclusion + shared
      relative-path validation (validateEvidenceReference semantics, lines 226-233)
- [ ] `source:` / `repository:` / `git:` / other namespaces excluded as scope markers
- [ ] Baseline authority unchanged: Analysis.selectedSource + Analysis.targetRevision
- [ ] supporting fact.analysis.id == insight.analysis.id enforced
- [ ] currentKnownRevision ownership unchanged (TemporalAssessmentServiceImpl, Story 0083)
- [ ] Genuine no-lineage -> legacy fallback ONLY; fail-closed -> UNKNOWN, no fallback
- [ ] CURRENT = positive verification of resolved baseline-present paths at baseline AND
      currentKnownRevision (same source)
- [ ] SUSPECTED_STALE only when baselinePresent=true AND currentPresent=false for a
      resolved path
- [ ] UNKNOWN for: no lineage, no resolved evidence, missing source, missing baseline,
      missing current revision, port failure, fail-closed
- [ ] InsightStatus never modified; no writes; read-only transaction
- [ ] No persistence / migration / AI / MCP changes
- [ ] No changes to Story 0083 conclusion semantics
- [ ] Tests prove behavior (32 tests: 16 resolver, 12 temporal, 3 boundary, 1 repo integration)
- [ ] Diff size ~8 source/test files, no schema

---

## READY_FOR_STORY_0086_IMPLEMENTATION_PLAN_APPROVAL

1. **branch**: story/0086-deterministic-repository-evidence-resolution
2. **base HEAD**: main @ a31ddba6b8f9ebd511428bb984f1fa05e524a8a5
3. **Story title**: 0086 — Deterministic Repository Evidence Resolution
4. **problem being solved**: TemporalAssessment currently evaluates the raw
   `evidenceReferences` string bag; it never answers "which repository files
   deterministically support this Insight" from the trusted, human-validated lineage
5. **selected lineage source**: `Insight.proposal.supportingFactIds` +
   `supportingObservationIds` (mandatory, immutable `updatable=false` FK, no deletion
   path, no setters for these fields -> PROPOSAL_LINEAGE_IS_STABLE; nothing duplicated
   onto the Insight)
6. **direct/observation policy**: UNION of direct Facts + observation-derived Facts
   (Observation.supportingFacts via `@EntityGraph`, `ObservationRepository` line 26),
   dedupe by Fact ID, deterministic (fact id asc, paths sorted)
7. **path-classification policy**: Option E — known-Fact origin AND namespace-prefix
   exclusion (`analysis: source: commit: git: diff: fact: observation: decision:
   insight: story: artifact: milestone: repository:`) AND existing relative-path
   validation semantics (`KnowledgeCollectionServiceImpl.validateEvidenceReference`
   lines 226-233; shared component). `source:<uuid>` / `repository:/` are scope markers,
   excluded. No `:line` parsing (zero production line refs)
8. **baseline authority**: `Analysis.selectedSource` + `Analysis.targetRevision`
   (immutable); invariant `fact.analysis.id == insight.analysis.id`; facts are evidence
   only
9. **source-scoping invariant**: baseline and current verify against the SAME
   `Analysis.selectedSource`; currentKnownRevision is source-scoped latest ProjectCommit
   (`findTopBySourceIdOrderByCommittedAtDescCommitHashDesc`, TemporalAssessmentServiceImpl
   lines 147-155), ownership unchanged
10. **partial-lineage fail-closed rule**: missing / cross-Analysis / dangling
    observation fact -> `RepositoryEvidenceResolutionException` (LINEAGE_UNAVAILABLE /
    DATA_INTEGRITY_ERROR) -> UNKNOWN; never CURRENT, never SUSPECTED_STALE, never silent
    legacy fallback
11. **resolver ownership**: dedicated `RepositoryEvidenceResolver`
    (`com.hopeful117.devlogai.repositoryevidence`, proposed) — depends only on
    FactRepository + ObservationRepository (read-only). TemporalAssessmentServiceImpl is
    the single consumer
12. **resolver return model**: `RepositoryEvidenceProjection(source, baselineRevision,
    List<ResolvedFileEvidence(factId, path)>)` — derived, NOT persisted
13. **exact production files**: NEW — RepositoryEvidenceResolver.java,
    RepositoryEvidenceProjection.java, ResolvedFileEvidence.java,
    RepositoryEvidenceResolutionException.java; MODIFIED —
    TemporalAssessmentServiceImpl.java (consume projection + genuine-no-lineage legacy
    fallback), KnowledgeCollectionServiceImpl.java (extract shared relative-path
    validator)
14. **exact test files**: NEW — RepositoryEvidenceResolverTest.java (+16 tests);
    EXTENDED — TemporalAssessmentServiceImplTest.java (+10 integration tests, +3
    boundary, +1 mapping of 32-group repo integration); total 32 documented tests
15. **exact repository methods reused/added**: REUSED —
    `FactRepository.findByAnalysisIdAndIdIn` (line 26),
    `ObservationRepository.findByAnalysisIdAndIdIn` (line 26, EntityGraph supportingFacts);
    ADDED — none (no new JPA methods)
16. **exact TemporalAssessment changes**: resolve projection first; evaluate
    projection.resolvedFiles set (replacing raw evidenceReferences); implement only the
    genuine-no-lineage legacy fallback; Story 0083 conclusion logic / RepositoryStatePort /
    currentKnownRevision logic unchanged
17. **exact legacy fallback rule**: ONLY when supportingFactIds AND supportingObservationIds
    are both empty (genuine no-lineage) -> evaluate genuine relative-path refs from
    `analysis.evidenceReferences` per Story 0083; `analysis:<uuid>` -> UNKNOWN;
    fail-closed modern corrupt lineage NEVER falls back
18. **precise CURRENT rule**: lineage fully resolved (no fail-closed), ≥1 resolved
    baseline-present path, all baseline-present resolved paths present at
    currentKnownRevision (same source), all state checks succeeded -> CURRENT
19. **precise SUSPECTED_STALE rule**: ≥1 resolved path with baselinePresent=true AND
    currentPresent=false; never from missing lineage, resolver failure, or repository-state
    failure
20. **precise UNKNOWN rule (internal diagnostics only, no new public enums)**:
    MISSING_SELECTED_SOURCE / MISSING_TARGET_REVISION / MISSING_CURRENT_KNOWN_REVISION /
    LINEAGE_UNAVAILABLE / DATA_INTEGRITY_ERROR / NO_REPOSITORY_EVIDENCE /
    REPOSITORY_STATE_UNAVAILABLE / ALL_REFERENCES_ABSENT_AT_BASELINE
21. **persistence impact**: ZERO (derived-only; no tables, no migrations, no Insight
    lineage duplication)
22. **AI impact**: ZERO
23. **MCP impact**: ZERO
24. **InsightStatus impact**: NONE (never modified)
25. **ADR assessment**: NO NEW ADR REQUIRED — implementation sits under ADR-058 (lineage),
    ADR-059 (temporal), ADR-060 (deterministic core), ADR-061 (repository baselines)
26. **expected production diff size**: SMALL — ~4 new Java files, 2 modified files
    (+1 shared validation extraction), ~2 test files; NO schema diff, NO dependency changes
27. **risks**: (1) cross-Analysis/dangling lineage data in legacy records (mitigation:
    fail-closed -> UNKNOWN, documented); (2) namespace tokens in evidence strings
    (mitigation: explicit exclusion set + tests); (3) legacy no-lineage Insights regressing
    (mitigation: explicit legacy fallback + tests 25/29/30); (4) performance of large
    resolved sets (bounded: dedupe + deterministic order; single isFilePresent call per
    path per revision); (5) tests depending on git availability (mock RepositoryStatePort);
    (6) accidental write (readOnly tx + no-write guarantees); (7) non-deterministic ordering
    (fact-id asc then path sort, tests 10); (8) registry duplication with 0083 signal
    (no new signal; new evidence source only); (9) drag from namespace detection
    (prefix match, fine); (10) fallback creep (fail-closed disallows); (11) EntityGraph
    lazy-loading N+1 (contained in one query; paths bounded); (12) resolution exception
    leaking outside temporal service (wrap; internal diagnostics)
28. **runtime benchmark target**: production Insight
    9b9fb9bb-de2b-4cde-ac62-0d85539e3615 (proposal
    2f9b327c-a6c6-49d4-a71f-15b1d65329aa; facts 2cf6883d/5f3fe981/6091e0bc
    (BUILD_MODULE_DECLARED), 8b52ed80 (SPRING_BOOT_DETECTED); source
    7819103b-37e7-4e15-95ec-fff9a12d21e4; baseline a31ddba6b...; analysis
    a7945221-2344-4f15-9f5d-91e63360c8f5) -> expect CURRENT (pom.xml +
    mcp-server/pom.xml both present at baseline == currentKnownRevision). Reference only;
    NOT hard-coded in code
29. **recommendation**: proceed with Story 0086 implementation plan; classification
    READY_FOR_STORY_0086_IMPLEMENTATION_PLAN_APPROVAL

Finish: READY_FOR_STORY_0086_IMPLEMENTATION_PLAN_APPROVAL

Do not implement before final approval.