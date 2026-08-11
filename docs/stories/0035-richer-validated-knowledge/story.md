# Story 0035 — Richer Validated Knowledge

## Status

Approved

## Priority

High

## Objective

Implement the first slice of **ADR-049 (Semantic Preservation During Knowledge Promotion)**: make the promotion of an accepted `ValidatableProposal` into an `Insight` **non-destructive** so that semantically useful information (rationale, confidence, evidence, source classification, decision metadata) survives the trust transition. This directly addresses friction points P1 and P2 of the Knowledge Usability Audit.

The governing principle, inherited verbatim from ADR-049:

> **Promotion changes trust status, not semantic value.**

A Knowledge Usability Audit (`docs/knowledge-usability-audit.md`) found that DevLog can possess richer information *before* validation than *after* it: `InsightPromotionService.toDomainType` collapses 8 proposal categories into 3 generic `InsightType`s, and `confidence`, `evidence`, `rationale` and the validation decision timestamp are silently discarded. This story stops that regression at the lifecycle layer.

## Motivation

Trusted knowledge is a shared engineering asset consumed by:
- humans (project understanding), and
- machines/agents (selecting and reasoning from structured context).

If the trusted model stores only a superficial summary, neither consumer can recover rationale, evidence, confidence or provenance. ADR-049 §11 makes this explicit: the frontend must not compensate for knowledge discarded earlier in the lifecycle, so the fix belongs in the knowledge lifecycle.

This story proves, end-to-end through the read API, that **accepted knowledge can retain enough semantic richness to become genuinely useful**.

## Scope

### In Scope

1. **Backend — schema (migration V37)**
   - Add to `insights`:
     - `rationale` `TEXT` (nullable) — why the statement matters;
     - `confidence` `NUMERIC(5,4)` (nullable) — originating interpretation confidence;
     - `evidence_references` `jsonb` (nullable) — traceable evidence provenance;
     - `source_type` `VARCHAR(100)` (nullable) — original proposal semantic classification, recoverable after domain normalization.

2. **Backend — entity**
   - Extend `Insight` with `rationale`, `confidence`, `evidenceReferences`, `sourceType`.

3. **Backend — promotion service**
   - `InsightPromotionService.promote` now also copies:
     - `rationale` from `payload["rationale"]`;
     - `confidence` from `proposal.getConfidence()`;
     - `evidenceReferences` from `proposal.getEvidenceReferences()`;
     - `sourceType` from `payload["insightType"]` (preserved unchanged).
   - The existing `toDomainType` mapping is **retained** as a normalized domain type, but is no longer destructive: the original classification stays recoverable via `sourceType` (ADR-049 §5).

4. **Backend — read API**
   - Extend `InsightResponse` record and `InsightMapper` with `rationale`, `confidence`, `evidenceReferences`, `sourceType`.

5. **Backend — decision metadata (P2)**
   - Populate `ValidatableProposal.decidedAt` in `ValidationServiceImpl` when a proposal is decided (accept or reject), so trusted knowledge stays traceable to its decision timestamp (ADR-049 §6).

6. **Backend — tests**
   - Update `InsightPromotionServiceTest` (rationale/confidence/evidence/sourceType preserved).
   - `InsightMapperTest` (new fields mapped).
   - `ValidationServiceImpl` test for `decidedAt` population.

### Out of Scope

- **Frontend projection** — per ADR-049 §11, the responsibility is at the knowledge lifecycle layer; exposing the richer fields in the Knowledge UI is a later slice.
- **`KnowledgeEvent` redesign** (ADR-049 §14) — explicitly deferred.
- **`KnowledgeRelation` expansion / Knowledge Graph** (ADR-049 §15) — deferred and explicitly not required.
- **Detailed git diff persistence** (ADR-049 §16) — out of scope.
- **Raw AI payload replication** (ADR-049 §8, §9) — we add structured fields, not an opaque `Map<String,Object>` blob.
- **Changing the `InsightType` taxonomy** — `toDomainType` normalization is retained; taxonomy changes are a separate decision.
- **Historical data backfill** — existing insights remain unchanged.
- No new ADR: ADR-049 already governs this slice.

## Constraints

- **Non-destructive promotion**: promotion must preserve `rationale`, `confidence`, `evidenceReferences` and `sourceType` when present; it must not silently drop them.
- **No opaque blob**: semantic dimensions are stored as typed, structured columns (ADR-049 §8); no unrestricted `Map<String,Object>` substitute.
- **Normalization + recoverability**: `type` remains the normalized domain type; `sourceType` keeps the original classification recoverable (ADR-049 §5).
- **Backward compatible schema**: all new columns are nullable; API additions are additive.
- **Raw AI output still not trusted**: only validated, explicitly promoted information becomes trusted knowledge (ADR-049 §9).

## Impact

- **Backend**:
  - migration `V37__...sql` (ALTER TABLE `insights`, additive nullable columns);
  - `Insight` entity, `InsightResponse`, `InsightMapper`, `InsightPromotionService`;
  - `ValidationServiceImpl` (set `decidedAt`);
  - test updates: `InsightPromotionServiceTest`, `InsightMapperTest`, `ValidationServiceImpl`-related.
- **Frontend**: none in this slice.
- **CI**: no change (covered by existing backend `quality` job).
- **Tests**: backend +~8.

## Acceptance Criteria

- AC-1: Migration V37 adds `rationale`, `confidence`, `evidence_references`, `source_type` to `insights` (nullable, additive).
- AC-2: Promoting an accepted INSIGHT proposal persists `rationale` from `payload["rationale"]`.
- AC-3: Promoting persists `confidence` from `proposal.getConfidence()`.
- AC-4: Promoting persists `evidenceReferences` from `proposal.getEvidenceReferences()`.
- AC-5: Promoting persists `sourceType` from `payload["insightType"]` unchanged (original classification recoverable).
- AC-6: `type` normalization (`toDomainType`) still works and throws on unsupported types.
- AC-7: `InsightResponse` exposes the four new fields via `InsightMapper`.
- AC-8: New fields are nullable-safe (absent optional data, non-null handling).
- AC-9: `ValidatableProposal.decidedAt` is populated when a proposal is decided (accept or reject).
- AC-10: Existing gates: backend `verify` (554+ tests, jacoco ≥0.80), frontend lint/build/test, SonarQube gate (`new_violations=0`, `new_coverage≥80`).

## Technical Context

Verified source structures:

- `InsightPromotionService.promote(proposal, validation, severity)` builds `Insight` from `payload` (`insightType`, `title`, `summary`) and maps via `toDomainType` (8 proposal categories → 3 `InsightType`). Discards `rationale`, `confidence`, `evidenceReferences`, decision metadata.
- `ValidatableProposal` already carries `confidence` (`BigDecimal`), `evidenceReferences` (`List<String>`), and `decidedAt` (`Instant`, null, never set).
- `insights` table (V8): `id`, `project_id`, `analysis_id`, `proposal_id`, `validation_id`, `type`, `severity`, `title`, `content`, audit timestamps. Note: sole addition here is additive `ALTER TABLE`.
- `InsightResponse` record: `id, projectId, analysisId, proposalId, validationId, type, severity, title, content, createdAt, updatedAt`.
- `ValidationServiceImpl.validate` sets proposal status (ACCEPTED/REJECTED) and calls promotion on accept; `decidedAt` is currently unset.
- Latest migration: `V36__create_engineering_stories_table.sql` → new migration `V37`.

## Dependencies

- ADR-049 (Accepted) — semantic preservation during promotion.
- Story 0034 (read-model conventions), Story 0032 (quality gates).
- Existing `Insight`/`InsightMapper`/`InsightResponse`/`InsightPromotionService`/`ValidationServiceImpl`.

## Risks

1. **Making promotion too rigid** on now-being-preserved fields — mitigated: all preserved fields are nullable and copied only when present; a missing optional field never blocks promotion (only `title`/`summary`/`insightType` remain strictly required).
2. **Schema migration against existing rows** — mitigated: additive nullable columns, existing rows unaffected.
3. **Scope creep into Knowledge Graph / KnowledgeEvent / frontend** — mitigated by explicit non-goals inherited from ADR-049.
4. **Tests coupling** — `InsightMapperTest` and promotion tests updated in the same slice; JaCoCo coverage floor preserved.

## Decisions for validation (resolved)

An ADR is **not** proposed: this slice is the direct implementation of the already-Accepted ADR-049.

1. **Schema**: dedicated typed columns (`rationale`, `confidence`, `evidence_references`, `source_type`) rather than an opaque JSON blob. ✅ Approved
2. **Normalization retained**: `type` stays normalized; `sourceType` preserves the original proposal classification. ✅ Approved
3. **Decision metadata**: `decidedAt` populated on `ValidatableProposal` (addresses P2). ✅ Approved
4. **No new ADR**; no frontend change in this slice. ✅ Approved

## Artifacts

- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`