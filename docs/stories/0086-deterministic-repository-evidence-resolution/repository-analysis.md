# Repository Analysis — Story 0086

## 1. The authoritative lineage lives on the Proposal, not the Insight

`Insight` carries an aggregate `evidenceReferences` (List<String>) populated at
promotion time from the validated AI result (`InsightPromotionService`, copies the
result's `evidenceReferences` verbatim). It is a string bag of reception metadata — not a
record of which repository files support the Insight, and not verified against lineage.

The trusted lineage is held by the Insight's mandatory Proposal:

- `ValidatableProposal.supportingFactIds` (Set<UUID>) — the Facts the user selected;
- `ValidatableProposal.supportingObservationIds` (Set<UUID>) — the Observations the user
  chose; each Observation has `supportingFacts` (its Facts).

The accepted proposal becomes the Insight via `InsightPromotionService`. `Insight.proposal`
is a **mandatory, immutable** association (`updatable = false`), and there is no
Deletion/add path that mutates `supportingFactIds` / `supportingObservationIds` /
`evidenceReferences`. The proposal lineage is therefore stable: `PROPOSAL_LINEAGE_IS_STABLE`.

## 2. Lineage resolution (deterministic UNION)

Given `Insight I`, `analysis = I.getAnalysis()`, `proposal = I.getProposal()`:

1. **Direct Facts** = `factRepository.findByAnalysisIdAndIdIn(analysis.id,
   proposal.getSupportingFactIds())` (`FactRepository` line 26).
2. **Observation-derived Facts** = for
   `observationRepository.findByAnalysisIdAndIdIn(analysis.id,
   proposal.getSupportingObservationIds())` (`ObservationRepository` line 26, with
   `@EntityGraph(attributePaths = "supportingFacts")`), collect the union of each
   Observation's `supportingFacts`.
3. **Resolution = UNION(direct Facts, observation-derived Facts), deduplicated by Fact ID,
   ordered by Fact ID asc** (deterministic, repeatable).

### Fail-closed lineage invariant (2.1)

The resolution MUST be complete and consistent. Any of the following invalidates the
WHOLE resolution (surfaces as `LINEAGE_UNAVAILABLE` / `DATA_INTEGRITY_ERROR` and yields
UNKNOWN — never a partial verdict):

- a `supportingFactId` that maps to no Fact in this Analysis;
- a `supportingObservationId` that maps to no Observation in this Analysis;
- an Observation whose `supportingFacts` reference a Fact absent from this Analysis
  (cross-Analysis or dangling);
- any resolved Fact whose `analysis.id != I.analysis.id`.

On fail-closed, the assessment returns UNKNOWN. It MUST NOT fall back to
`evidenceReferences` and MUST NOT emit CURRENT / SUSPECTED_STALE.

## 3. Baseline authority

The baseline is unchanged from Story 0083 / 0085:

    baseline = Analysis.selectedSource + Analysis.targetRevision (immutable, updatable = false)

Facts are evidence only. `RepositoryEvidenceProjection.baselineRevision()` IS
`Analysis.targetRevision`; `projection.source()` IS `Analysis.selectedSource`. Facts never
override the baseline. Invariant: every supporting Fact, and every observation-derived
Fact, satisfies `fact.analysis.id == I.analysis.id`.

## 4. Path classification (Option E)

A Fact's `evidenceReferences` (String set) become repository file evidence only when all
three conditions hold:

1. **Known-Fact origin** — the reference comes from a resolved lineage Fact
   (direct or observation-derived), never from free-form `evidenceReferences` only.
2. **Namespace exclusion** — the reference does not start with a known scope marker prefix:
   `analysis:`, `source:`, `commit:`, `git:`, `diff:`, `fact:`, `observation:`,
   `decision:`, `insight:`, `story:`, `artifact:`, `milestone:`, `repository:`.
   `repository:/` and `source:<uuid>` are repository-scope markers, NOT file paths, and are
   excluded.
3. **Relative-path validation** — the reference passes the existing relative-path
   validation semantics, identical to `KnowledgeCollectionServiceImpl.validateEvidenceReference`
   (lines 226-233): `\` normalized to `/`; rejects absolute (`/...`), drive-letter
   (`^[A-Za-z]:/..`), `.`, `..`, `../`, `/../`. Reuse these semantics (or the method /
   a shared extracted component) so acceptance is consistent with collection.

No line-number parsing is added: zero production references carry `:line` suffixes
(verified below), so file-level granularity is sufficient.

## 5. Runtime evidence distribution (real data, analysis `a7945221-...`)

Fact `evidenceReferences` across the runtime analysis:

- 42,435 plain relative repository paths
- 32,421 `source:<uuid>` scope markers
- 1,069 `repository:/` scope markers
- 138 `git:<...>` references
- 0 `analysis:` / `fact:` / `observation:` / `commit:` / `diff:` / `decision:` /
  `insight:` / `story:` / `artifact:` / `milestone:` refs
- 0 references with `:line` suffixes

Consequence: pure category-of-evidence determination. Plain relative paths are file
candidates; `source:<uuid>` and `repository:/` are scope markers that MUST be excluded by
namespace exclusion, not treated as files. `git:<...>` is excluded (not a workspace-relative
path).

## 6. Temporal integration (single consumer)

`TemporalAssessmentServiceImpl` is the single consumer of the resolved projection. Its
Story 0083 flow (baseline present/absent at `Analysis.targetRevision`, then present/absent
at `currentKnownRevision`, both scoped to `Analysis.selectedSource`) is UNCHANGED except
that the set of repository evidence evaluated comes from the projection.

- `currentKnownRevision` ownership: unchanged, stays in `TemporalAssessmentServiceImpl`
  via `ProjectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc`
  (lines 147-155).
- `RepositoryStatePort.isFilePresentAtRevision(source, hash, path)` (Story 0083 port):
  unchanged; each resolved path is checked at baseline revision and at currentKnownRevision.

## 7. Legacy fallback boundary

- **Genuine no-lineage Proposal** (empty `supportingFactIds` AND empty
  `supportingObservationIds`): no deterministic repository evidence arises from lineage.
  The service falls back to the previous Story 0083 behavior — evaluate the genuine
  repository-path references present in `Analysis.analysis.evidenceReferences` (plain
  relative paths passing relative-path validation; `source:` / `repository:` / other
  namespaces excluded). `analysis:<uuid>` references yield UNKNOWN (not a resolvable path).
- **Modern corrupt lineage** (fail-closed triggered): MUST NOT fall back. Returns UNKNOWN.
- A legacy fallback is a compatibility shim, not a primary path; it is the only case where
  `evidenceReferences` is used as temporal evidence, and only for genuine no-lineage.

## 8. No persistence / no effect on other subsystems

- No new entity, table, migration, or join table.
- `Insight` lineage is NOT duplicated onto the Insight (projection is a derived,
  non-persisted object).
- No AI Engine, Context Engine, MCP/API, or `InsightStatus` changes.
- `ValidatableProposal` creation, validation, and `InsightPromotionService` are untouched.
- Story 0083 / 0085 production behavior for the lineage path is extended; conclusion
  semantics unchanged.

## 9. API and model summary

New derived types (proposed names, subject to package conventions):

```text
RepositoryEvidenceProjection(source, baselineRevision, List<ResolvedFileEvidence>)
ResolvedFileEvidence(factId, path)
```

Resolver ownership: a dedicated `RepositoryEvidenceResolver`
(`com.hopeful117.devlogai.repositoryevidence`) returning the projection; it depends only on
`FactRepository`, `ObservationRepository`, and the Analysis/Proposal/Insight/Fact/
Observation entities (read-only). It does NOT touch the git workspace or revision state.
`TemporalAssessmentServiceImpl` calls the resolver and evaluates repository state via the
Story 0083 `RepositoryStatePort`.