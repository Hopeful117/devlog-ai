# Story 0035 — Richer Validated Knowledge — Code Review

## Status

Reviewed

## Review Scope

Code review of the semantic-preservation slice: migration V37, `Insight` entity, `InsightPromotionService`, `InsightResponse`, `InsightMapper`, `ValidationServiceImpl` (decidedAt), and the associated tests.

## Findings

### 1. Schema — additive and nullable ✅
`V37__add_semantic_preservation_to_insights.sql` only adds nullable columns. No breaking change, no data migration required, backward compatible with existing rows.

### 2. Typed structured columns, no opaque blob ✅
The four dimensions are dedicated typed columns (`TEXT`, `NUMERIC(5,4)`, `jsonb`, `VARCHAR(100)`), consistent with ADR-049 §8 (no unrestricted `Map<String,Object>`). Good.

### 3. Non-destructive promotion ✅
`InsightPromotionService` copies `rationale`, `confidence`, `evidenceReferences` and `sourceType` without dropping them. `added` fields use `@Builder.Default` for `evidenceReferences` and nullable handling for the rest, so absent optional data never blocks promotion.

### 4. Normalization + recoverability (ADR-049 §5) ✅
`toDomainType` is retained for the normalized `type`, and `sourceType` keeps the *original* proposal classification. This satisfies the "original semantic classification should remain recoverable" requirement without a taxonomy change.

### 5. `optionalText` nullable safety ✅
New helper returns `null` for missing/blank optional fields rather than throwing — distinguishes required (`title`, `summary`, `insightType`) from optional (`rationale`, `sourceType`) payload fields. No over-strictness (Risk #1 in the story).

### 6. Raw AI payload not blindly copied (ADR-049 §9) ✅
Only explicitly named, validated fields are promoted to structured columns; the full AI payload remains on the proposal, not duplicated onto the Insight.

### 7. decidedAt population (P2) ✅
`ValidationServiceImpl` sets `decidedAt` on both accept and reject, so trusted knowledge stays traceable to its decision timestamp (ADR-049 §6). Uses `Instant.now()` consistent with validation timing semantics.

### 8. JSON null/empty-list default consistency ⚠️
`evidenceReferences` uses `@Builder.Default` empty list; serializes as `[]` rather than `null`. This is intentional and asserted in `InsightMapperTest.mapsNullableSemanticFields` (`List.of()`). Consistent with `ValidatableProposal`'s own `evidenceReferences` default. No action required.

### 9. Test coverage ✅
- Promotion: preserved fields asserted.
- Mapper: full + nullable paths.
- Validation: `decidedAt` asserted.
- Controller WebMvc: constructor updated for new record shape.
- Flyway version assertion bumped `36` → `37`.

## Gate Results

- Backend: `./mvnw verify` BUILD SUCCESS, **556 tests**, coverage checks met.
- SonarQube: Quality gate **OK** (new_coverage 80.4%, new_violations 0, no dup).

## Conclusion

Approve. The slice is minimal, faithful to ADR-049, additive, nullable-safe, and fully verified. No blocking or non-blocking issues requiring changes.