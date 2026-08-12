# Story 0035 — Richer Validated Knowledge — Implementation Report

## Status

Implemented

## Summary

Implemented the first slice of ADR-049 (Semantic Preservation During Knowledge Promotion): promotion of an accepted `ValidatableProposal` into an `Insight` is now **non-destructive**. `rationale`, `confidence`, `evidenceReferences` and `sourceType` survive the trust transition, and `ValidatableProposal.decidedAt` is populated on decision. This addresses audit friction points **P1** and **P2**.

## Changes

### Backend

1. **Migration `V37__add_semantic_preservation_to_insights.sql`** (new)
   - `ALTER TABLE insights` adds additive, nullable columns:
     - `rationale` `TEXT`
     - `confidence` `NUMERIC(5,4)`
     - `evidence_references` `jsonb`
     - `source_type` `VARCHAR(100)`

2. **`Insight.java`** — added 4 fields mirrored on the schema:
   - `rationale` (String, nullable)
   - `confidence` (BigDecimal, precision 5 scale 4, nullable)
   - `evidenceReferences` (`List<String>`, jsonb, `@Builder.Default` empty list)
   - `sourceType` (String, nullable)

3. **`InsightPromotionService.java`** — `promote(...)` now also copies:
   - `rationale` from `payload["rationale"]` (optional, nullable-safe)
   - `confidence` from `proposal.getConfidence()`
   - `evidenceReferences` from `proposal.getEvidenceReferences()`
   - `sourceType` from `payload["insightType"]` (unchanged, preserving the original proposal classification)
   - `toDomainType` normalization is retained for `type`; no longer destructive because `sourceType` keeps the original classification recoverable.

4. **`InsightResponse.java`** — added `rationale`, `confidence`, `evidenceReferences`, `sourceType` (positioned after `content`, before audit timestamps).

5. **`InsightMapper.java`** — implicit name-based mapping of the four new fields (no `@Mapping` needed, names match).

6. **`ValidationServiceImpl.java`** — sets `proposal.setDecidedAt(Instant.now())` whenever a proposal is decided (ACCEPTED or REJECTED), addressing P2.

### Tests

- **`InsightPromotionServiceTest`** — extended the main promotion test to assert the four preserved fields; added `BigDecimal`/`List` imports.
- **`InsightMapperTest`** (new) — 2 tests: full semantic-field mapping; nullable-semantic-field mapping (null rationale/confidence/sourceType, empty evidence list).
- **`ValidationServiceTest`** — asserts `decidedAt` is non-null after acceptance.
- **`InsightControllerWebMvcTest`** — updated the positional `InsightResponse` constructor for the new record shape.
- **`ProjectDeletionPostgresIntegrationTest`** — updated latest-Flyway-version assertion `36` → `37`.

## Verification

- `./mvnw verify` → **BUILD SUCCESS**, **556 tests pass**, `All coverage checks have been met` (jacoco ≥ 0.80).
- Frontend unchanged → lint & format:check clean.
- SonarQube → analysis SUCCESS, **Quality gate OK**: `new_coverage` 80.4% (threshold 80), `new_violations` 0, `new_duplicated_lines_density` 0.0, `caycStatus` compliant. No issues on `InsightResponse`.

## Frontend slice (Knowledge UI projection)

### Changes

1. **`insight.models.ts`** — extended `InsightFields` (inherited by `InsightSummary`/`InsightDetail`) with:
   - `rationale?: string | null`
   - `confidence?: ConfidenceValue`
   - `evidenceReferences?: readonly string[]`
   - `sourceType?: string | null`
   Mirrors `InsightResponse` (backend record) field-for-field.

2. **`insight-detail-page.ts`** — the Insight detail page now surfaces the preserved semantic richness:
   - **Rationale** — rendered as a heading + paragraph when present.
   - **Confidence** — `<dl>` entry when `confidence !== null`.
   - **Evidence** — bulleted list of `evidenceReferences` when non-empty.
   - **Source type** — inline `· source <sourceType>` next to the normalized `type`.
   Every section is guarded by a presence/nullability check, so a bare Insight (all optional fields absent) renders unchanged.

3. **`insight-detail-page.spec.ts`** (new) — 5 tests:
   - full render shows rationale, confidence, evidence references and source type;
   - bare Insight omits all semantic sections (nullable-safe);
   - multiple evidence references render as a list;
   - loading / error / not-found states;
   - no manual `.subscribe(`.

### Verification (frontend)

- `npx ng test --coverage --watch=false` → all tests pass; lines coverage `81.55%` (threshold 75%).
- `npx eslint .` → clean.
- `npx prettier --check .` → clean.
- `npx ng build` → production bundle succeeds.

## Acceptance Criteria Mapping

| AC | Result |
|---|---|
| AC-1 migration adds 4 nullable columns | ✅ `V37` |
| AC-2 rationale preserved | ✅ promotion copies `payload["rationale"]` |
| AC-3 confidence preserved | ✅ copies `proposal.getConfidence()` |
| AC-4 evidenceReferences preserved | ✅ copies `proposal.getEvidenceReferences()` |
| AC-5 sourceType preserved unchanged | ✅ copies `payload["insightType"]` |
| AC-6 toDomainType normalization still works | ✅ retained + test |
| AC-7 InsightResponse exposes 4 fields | ✅ mapper test |
| AC-8 nullable-safe | ✅ nullable + empty-list default, mapper test |
| AC-9 decidedAt populated | ✅ on accept & reject |
| AC-10 gates green | ✅ backend, frontend, Sonar |
| AC-11 InsightFields exposes 4 fields | ✅ `insight.models.ts` |
| AC-12 detail page renders the 4 fields | ✅ `insight-detail-page.ts` + spec |
| AC-13 detail page nullable-safe | ✅ bare-Insight spec test |

## Out of Scope Confirmed

- No `KnowledgeEvent` redesign, `KnowledgeRelation` expansion, graph DB, diff persistence, raw payload blob, taxonomy change, or historical backfill.
- The richer fields are projected on the **detail page only**; the insights list and analysis-section index views are left unchanged in this slice.