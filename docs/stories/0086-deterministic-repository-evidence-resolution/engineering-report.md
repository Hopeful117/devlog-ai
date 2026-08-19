# Engineering Report — Story 0086

## Architecture Decisions Verified

### 1. Lineage Source: Proposal, Not Insight
The Proposal is the authoritative, human-validated lineage carrier:
- `Insight.proposal` is mandatory (`@OneToOne(optional=false)`) and immutable (`updatable=false`)
- `ValidatableProposal.supportingFactIds` / `supportingObservationIds` are JSONB Lists set once at AI result time (validated subset of selected facts/observations)
- No mutation setters, no deletion path → PROPOSAL_LINEAGE_IS_STABLE
- Nothing duplicated onto Insight; resolver reads proposal directly

### 2. Union + Deduplication
- Direct Facts from `supportingFactIds`
- Observation-derived Facts from `supportingObservationIds` → `Observation.supportingFacts` (loaded via `@EntityGraph`)
- Union deduped by Fact ID using `TreeMap<UUID, Fact>` (natural UUID ordering = deterministic)
- Data integrity check: same Fact ID with different content/type/source/fingerprint → `DATA_INTEGRITY_ERROR`

### 3. Fail-Closed Lineage Invariant
Any of the following throws `RepositoryEvidenceResolutionException` → UNKNOWN (never partial, never legacy fallback):
- Declared Fact/Observation ID not found in this Analysis (size mismatch)
- Found Fact/Observation belongs to different Analysis (cross-analysis)
- Observation declares no supporting facts
- Observation's supporting Fact belongs to different Analysis (dangling)
- Duplicate Fact ID with inconsistent data

### 4. Option E Path Classification
Applied per-resolved Fact, in deterministic order (Fact ID asc, then path asc):
1. Known-Fact origin (only refs from resolved Facts considered)
2. Namespace prefix exclusion (13 prefixes: `analysis:`, `source:`, `commit:`, `git:`, `diff:`, `fact:`, `observation:`, `decision:`, `insight:`, `story:`, `artifact:`, `milestone:`, `repository:`) — case-insensitive
3. Relative-path validation (shared `EvidencePathValidator.isValidRelativePath` — identical to KCS collector validation)

Result: `ResolvedFileEvidence(factId, path)` per unique path (first Fact wins), sorted by path asc for deterministic projection output.

### 5. Baseline Authority Unchanged
- `Analysis.selectedSource` + `Analysis.targetRevision` remain the baseline
- Invariant verified: every supporting Fact (direct and observation-derived) has `fact.analysis.id == insight.analysis.id`
- `RepositoryEvidenceProjection` carries `source` and `baselineRevision` directly from Analysis

### 6. Temporal Assessment Integration
- `TemporalAssessmentServiceImpl` is the single consumer of `RepositoryEvidenceResolver`
- `currentKnownRevision` ownership unchanged (Story 0083: source-scoped latest ProjectCommit)
- Evaluation loop unchanged: per-path `RepositoryStatePort.isFilePresentAtRevision(source, baseline, path)` + `(source, current, path)`
- Enrichment uses original `evidenceReferences` for backward compatibility (corroborating only)

### 7. Legacy Fallback Boundary
- **Allowed**: Genuine no-lineage Proposal (empty `supportingFactIds` AND `supportingObservationIds`) → filter `evidenceReferences` via Option E → evaluate
- **Forbidden**: Modern corrupt lineage (fail-closed triggered) → UNKNOWN, never legacy fallback
- Empty `evidenceReferences` in legacy mode → UNKNOWN "no evidence references to evaluate" (preserves Story 0083 behavior order)

### 8. Conclusion Semantics (Unchanged from Story 0083)
- **CURRENT**: All resolved baseline-present paths also present at currentKnownRevision
- **SUSPECTED_STALE**: ≥1 resolved path baselinePresent=true AND currentPresent=false
- **UNKNOWN**: 8 internal diagnostic causes (no new public enums):
  - MISSING_SELECTED_SOURCE
  - MISSING_TARGET_REVISION
  - MISSING_CURRENT_KNOWN_REVISION
  - LINEAGE_UNAVAILABLE
  - DATA_INTEGRITY_ERROR
  - NO_REPOSITORY_EVIDENCE
  - REPOSITORY_STATE_UNAVAILABLE
  - ALL_REFERENCES_ABSENT_AT_BASELINE
- `InsightStatus` never modified

## Risk Mitigations Verified

| Risk | Mitigation |
|------|------------|
| Cross-analysis/dangling lineage in legacy data | Fail-closed → UNKNOWN, documented |
| Namespace tokens in evidence strings | Explicit 13-prefix exclusion set + tests |
| Legacy no-lineage Insights regressing | Explicit legacy fallback + tests 25/29/30 |
| Performance of large resolved sets | Bounded: dedupe + deterministic order; single isFilePresent call per path per revision |
| Tests depending on git availability | Mock `RepositoryStatePort` |
| Accidental writes | `@Transactional(readOnly=true)` + no-write guarantees |
| Non-deterministic ordering | Fact-id asc then path sort, tests verify repeatability |
| Registry duplication with Story 0083 signal | No new signal; new evidence source only |
| Namespace detection performance | Prefix match on normalized string, O(1) per ref |
| Fallback creep | Fail-closed explicitly disallows legacy fallback from modern corrupt lineage |
| EntityGraph lazy-loading N+1 | Contained in one query; paths bounded |
| Resolution exception leaking | Wrapped; internal diagnostics only |

## ADR Compliance

- **ADR-006**: Proposal validation lifecycle untouched; resolver reads post-validation lineage
- **ADR-058**: Deterministic lineage (union + dedupe + fail-closed) implemented
- **ADR-059**: Temporal assessment consumes deterministic evidence; authority/freshness separation preserved
- **ADR-060**: Deterministic core owns evidence resolution; no AI inference
- **ADR-061**: Baseline = Source + immutable revision; fail-closed provenance; legacy UNKNOWN preserved

**ADR Assessment**: NO NEW ADR REQUIRED — implementation fully under existing ADRs.

## Performance Characteristics

- Resolver: 2 repository queries (`findByAnalysisIdAndIdIn` for Facts and Observations), O(N) Fact processing
- Path classification: O(M) per Fact evidenceReferences (M typically small, <10)
- Repository state checks: 2 × P `RepositoryStatePort` calls (P = resolved paths, typically <20)
- All read-only, single transaction, no locking contention
- No N+1: `@EntityGraph(supportingFacts)` on Observation query loads all in one SELECT

## Compatibility

- **Story 0083**: Full backward compatibility — existing tests pass; legacy insights evaluated identically
- **Story 0085**: Baseline capture unchanged; `targetRevision` persists observed revision correctly
- **Runtime benchmark**: Insight `9b9fb9bb-de2b-4cde-ac62-0d85539e3615` → CURRENT (pom.xml + mcp-server/pom.xml present at baseline == currentKnownRevision)

## Summary

Story 0086 implementation is complete, tested, and ready for commit. All 851 backend tests pass. The deterministic repository evidence resolver provides the authoritative lineage-based evidence set for temporal assessment, with strict fail-closed semantics and a narrow, well-defined legacy fallback boundary.