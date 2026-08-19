# Story 0086 — Deterministic Repository Evidence Resolution

## Status

**READY_FOR_IMPLEMENTATION_PLAN_APPROVAL**

## Objective

Answer, deterministically and sourced from the trusted lineage that produced the
Insight (not from AI re-inference):

> What repository files deterministically support this trusted Insight?

and use that answer as the exact set of repository evidence for Story 0083 temporal
assessment:

    resolved repository evidence (from validated proposal lineage)
        -> RepositoryEvidenceProjection(source, baselineRevision, resolvedFiles)
           -> TemporalAssessment (CURRENT / SUSPECTED_STALE / UNKNOWN)

The authoritative lineage source is the Insight's mandatory, immutable Proposal:

    Insight.proposal.supportingFactIds  (direct Facts)
    Insight.proposal.supportingObservationIds (Observations -> their supporting Facts)

## Problem

Story 0083's `TemporalAssessmentServiceImpl` currently reads ONLY
`insight.getEvidenceReferences()` as the set of repository evidence to verify against
baseline and currentKnownRevision. This is insufficient:

1. `evidenceReferences` is reception metadata collected by Collectors; it is not an
   authoritative, validated record of which repository files support the Insight.
2. `evidenceReferences` is NOT verified against the deterministic lineage (the facts and
   observations selected and validated by the human user through the proposal lifecycle).
3. There is no deterministic source-scoped repository-evidence set for an Insight that is
   independent of the exact collection-time metadata string.
4. The proposal already holds the authoritative, immutable, validated Fact/Observation
   lineage, but TemporalAssessment does not use it.

## Resolution

Introduce a dedicated **RepositoryEvidenceResolver** that deterministically derives the
set of repository files supporting an Insight from its Proposal lineage:

1. **Lineage source (authoritative):** `Insight.proposal.supportingFactIds`
   (direct Facts) and `Insight.proposal.supportingObservationIds` (Observations whose
   `supportingFacts` contribute additional Facts).
2. **UNION policy:** resolve the union of direct Facts and observation-derived Facts,
   deduplicated by Fact ID, emitted in a deterministic order (Fact ID asc).
3. **Fail-closed lineage invariant:** if any referenced Fact/Observation cannot be fully
   resolved within the same Analysis (missing id, cross-Analysis id, or an Observation
   whose supporting Fact is missing/inconsistent), the WHOLE resolution is invalid and the
   assessment returns UNKNOWN — it never silently degrades to a partial verdict.
4. **Path classification (Option E):** a Fact evidence reference is treated as
   repository repository evidence only when it is (a) produced by the deterministic
   Fact lineage AND (b) survives namespace exclusion AND (c) passes the existing
   relative-path validation. Repository paths are the leaf references; `repository:/` and
   `source:<uuid>` are scope markers, not files, and are excluded.
5. **Baseline authority:** `Insight.analysis.selectedSource` + `Analysis.targetRevision`
   remain the baseline. Facts are evidence; they never override the baseline.
6. **Ownership:** a dedicated `RepositoryEvidenceResolver` resolves the projection;
   `TemporalAssessmentServiceImpl` stays the single consumer that evaluates repository
   state. `currentKnownRevision` ownership remains in `TemporalAssessmentServiceImpl`
   (Story 0083 source-scoped latest ProjectCommit).
7. **Return model:** `RepositoryEvidenceProjection(source, baselineRevision,
   List<ResolvedFileEvidence(factId, path)>)`.
8. **No persistence, no AI, no MCP change.** This is a pure derivation. `InsightStatus`
   is never modified.

For Insights whose Proposal provides NO lineage (genuine no-lineage legacy case), the
service falls back to the previous Story 0083 behavior — assessing only the genuine
repository-path references present in `evidenceReferences`. A modern corrupt lineage MUST
NOT trigger this fallback (fail-closed).

## Scope

### IN SCOPE

- Deterministic derivation of the repository-evidence set from Proposal lineage.
- Dedicated `RepositoryEvidenceResolver` + projection types.
- UNION of direct Facts and observation-derived Facts, deduplicated by Fact ID.
- Fail-closed lineage resolution (partial lineage -> UNKNOWN).
- Option E path classification: known-Fact origin + namespace exclusion + existing
  relative-path validation.
- Temporal assessment consuming the resolved projection (Story 0083 semantics unchanged;
  the supplied evidence set changes).
- Deterministic tests covering the resolver, the temporal integration, and the legacy
  fallback boundary.

### OUT OF SCOPE

- No new persistence / no Insight duplication of lineage.
- No new database tables or migrations.
- No AI provenance inference.
- No change to how `evidenceReferences` is collected or stored.
- No change to `InsightStatus`, `ValidatableProposal`, or review workflow.
- No change to Proposal creation or promotion.
- No MCP / API contract changes.
- No change to `currentKnownRevision` ownership.
- No line-number granularity (no production `:line` references exist; not needed).
- No repository-history reconstruction or rename tracking.
- No generic lineage framework / no broad provenance subsystem.
- No changes to Story 0083 conclusion semantics.

## Non-Negotiable Invariants

1. Repository provenance is deterministic. (`ADR-058`, `ADR-060`)
2. The repository evidence set is derived from the validated lineage, NEVER inferred by AI.
3. A repository baseline is identified by Source + immutable revision. (`ADR-061`)
4. `Analysis.selectedSource` + `Analysis.targetRevision` remain the baseline authority.
5. The lineage is the trusted, human-validated Proposal `supportingFactIds` /
   `supportingObservationIds` — NOT free-form `evidenceReferences`.
6. Supporting Facts and derived observation Facts MUST belong to the same Analysis as the
   Insight; cross-Analysis lineage is invalid.
7. The lineage invariant is fail-closed: partial or inconsistent lineage -> UNKNOWN,
   never a silently partial verdict, never FALSE certainty.
8. Missing/invalid lineage (modern path) NEVER falls back to the legacy path; only a
   genuine no-lineage Proposal falls back.
9. Repository `source:` and `repository:/` prefixes are scope markers, not file evidence.
10. `Analysis.selectedSource` + `targetRevision` unchanged; Facts are evidence only.
11. `InsightStatus` never modified; no authority mutation.
12. No AI Engine, no Context Engine, no MCP, no persistence changes.
13. Repository-state verification failure never becomes false certainty (UNKNOWN).
14. Deterministic ordering of resolved evidence (repeatable output).
15. Story 0083 conclusion semantics remain unchanged.

## References

- ADR-006 — AI proposals / trusted knowledge boundary
- ADR-058 — Data Lineage
- ADR-059 — Temporal Engineering Knowledge
- ADR-060 — Deterministic / Probabilistic Responsibility
- ADR-061 — Deterministic Repository Observation Baselines
- Story 0083 — Deterministic Insight Temporal Assessment Foundation
- Story 0085 — Deterministic Baseline Capture for New Source-Scoped Analyses