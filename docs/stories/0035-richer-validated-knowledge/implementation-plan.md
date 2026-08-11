# Story 0035 — Richer Validated Knowledge — Implementation Plan

## Goal

Make promotion of an accepted `ValidatableProposal` → `Insight` non-destructive, per ADR-049. Preserve `rationale`, `confidence`, `evidenceReferences`, `sourceType`; populate `ValidatableProposal.decidedAt`.

## Steps

1. **Migration `V37`** — `ALTER TABLE insights` add nullable `rationale` (TEXT), `confidence` (NUMERIC(5,4)), `evidence_references` (jsonb), `source_type` (VARCHAR(100)).
2. **`Insight` entity** — add the 4 fields, `@Builder.Default` empty list for `evidenceReferences`, `@JdbcTypeCode(JSON)` for jsonb.
3. **`InsightPromotionService`** — copy `rationale` (optional), `confidence` (from proposal), `evidenceReferences` (from proposal), `sourceType` (from payload `insightType`); keep `toDomainType`. Add `optionalText` helper.
4. **`InsightResponse`** — add the 4 fields after `content`.
5. **`InsightMapper`** — no explicit mapping needed (name-based); verify.
6. **`ValidationServiceImpl`** — set `decidedAt = Instant.now()` on accept and reject.
7. **Tests**:
   - extend `InsightPromotionServiceTest` (preserved fields);
   - new `InsightMapperTest` (full + nullable);
   - `ValidationServiceTest` (decidedAt non-null after accept);
   - `InsightControllerWebMvcTest` (constructor shape);
   - `ProjectDeletionPostgresIntegrationTest` (Flyway 36→37).
8. **Gates** — `./mvnw verify`; frontend lint/format; SonarQube.

## Ordering & Dependencies

- Backend-first, single slice. No frontend change (ADR-049 §11). No new ADR (ADR-049 governs).
- No new third-party dependency.

## Definition of Done

Backend `verify` green (556 tests, coverage ≥80%), frontend clean, SonarQube gate OK, all Acceptance Criteria 1–10 met.