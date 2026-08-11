# Story 0035 — Richer Validated Knowledge — Repository Analysis

## Purpose

Reconnaissance of the knowledge promotion lifecycle to scope the first ADR-049 implementation slice. Read-only analysis performed before implementation.

## Relevant Components

### `InsightPromotionService`
- Entry point: `promote(ValidatableProposal, Validation, InsightSeverity)` — only handles `ProposalType.INSIGHT`.
- Previously built `Insight` with `type` (via `toDomainType`), `severity`, `title`, `content`.
- **Loss identified**: discarded `rationale`, `confidence`, `evidenceReferences`, and the original proposal classification; `toDomainType` collapsed 8 proposal categories → 3 `InsightType`s.

### `ValidatableProposal`
- Already carries: `confidence` (`BigDecimal`), `evidenceReferences` (`List<String>`), `supportingFactIds`, `supportingObservationIds`, `payload`, and `decidedAt` (`Instant`, null, never set).
- `toProposals` in `AiTaskResultServiceImpl` populates these from `AiProposalResult`.

### `Insight` entity / `insights` table
- Fields pre-change: `id, project, analysis, proposal, validation, type, severity, title, content, createdAt, updatedAt`.
- No rationale/confidence/evidence/source classification columns.

### `InsightResponse` / `InsightMapper`
- Record response with `projectId, analysisId, proposalId, validationId, type, severity, title, content, createdAt, updatedAt`.
- MapStruct mapper, name-based field mapping.

### `ValidationServiceImpl`
- On decision sets proposal status, saves validation, and on ACCEPTED invokes `promotionService.promote(...)`.
- `decidedAt` was never set → **P2**.

## Key Findings

1. The semantic metadata the audit wants preserved is **already present on the source** (`ValidatableProposal`), so the fix belongs in promotion, not re-acquisition.
2. `type` normalization is intentionally a controlled mapping; preserving `sourceType` provides recoverability per ADR-049 §5 without a taxonomy change.
3. New columns must be nullable + additive (V8 table, existing rows).
4. Latest migration was `V36` → new migration must be `V37`.
5. Test touch-points: `InsightPromotionServiceTest` (mock-based), no existing `InsightMapperTest`, `ValidationServiceTest` (mock), `InsightControllerWebMvcTest` (positional constructor), `ProjectDeletionPostgresIntegrationTest` (asserts latest Flyway version).

## Guidance Used for Implementation
- Preserve as typed, structured, nullable columns (ADR-049 §8).
- Copy from proposal/payload without dropping (non-destructive).
- Populate `decidedAt` on accept and reject.
- Keep `toDomainType` for normalized `type`; add `sourceType` for original classification.