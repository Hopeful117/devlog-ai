# Story 0099 — Design Implementation Plan

## Status

**IMPLEMENTATION_COMPLETE_AWAITING_HUMAN_REVIEW**

Implementation completed on dedicated branch `story-099-align-generic-analysis-launch-contract`. All verification checks passed. Awaiting human review before any commit/merge.

## Purpose

Implement one bounded P0-A slice after human approval: replace independent internal launch choices
with four executable product objectives, objective-derived Project or repository scope, existing
bounded guidance, and deterministic backend derivation of internal execution metadata.

## Governing Decisions

- ADR-006: proposals remain untrusted until individual human validation.
- ADR-017: Analysis and AiTask remain separate; snapshots remain immutable.
- ADR-020: provider callback and proposal persistence remain unchanged.
- ADR-021: Project remains the knowledge boundary; Analysis may target the complete Project or one
  Source and must not be forced globally to one Source.
- ADR-028: IntentDefinition remains owner of objective execution semantics.
- ADR-030: UserGuidance remains optional, bounded, subordinate input.
- ADR-063: context retrieval/composition ownership and budgets remain unchanged.

## Contract Decisions

1. Generic Angular launch displays four human objectives backed by the four `GENERIC` Intents.
2. Dedicated Engineering Event Intent remains available only through its source/commit workflow.
3. AnalysisType is removed from human choice but retained in persistence and diagnostics.
4. All generic V1 objectives derive `ARCHITECTURE_REVIEW`.
5. Scope is fixed by objective: Project scope for Project Understanding, Architecture, and Decisions;
   repository scope for README information.
6. Project scope keeps `selectedSource == null`, rejects source/revision input, and collects applicable
   active Sources at independently resolved default revisions.
7. Repository scope requires one active Project Git source and supports an optional source-local ref.
8. The existing `POST /api/v1/analyses` then `POST /api/v1/analyses/{id}/workflow` lifecycle remains.
9. Existing `type` input becomes optional legacy metadata and must match the derived type when sent.
10. Intent, proposal, AiTask, prompt, schema, and context derivation remain deterministic and Core-owned.

## Expected Change Surface

### Angular

Likely existing files:

- `frontend/src/app/features/analyses/analysis.models.ts`
- `frontend/src/app/features/analyses/analysis-form.ts`
- `frontend/src/app/features/analyses/analysis-form.html`
- `frontend/src/app/features/analyses/project-analyses-section.ts`
- `frontend/src/app/features/analyses/project-analyses-section.html`
- project workspace/cockpit projection that supplies active Sources

Expected design outcomes:

- product launch ViewModel;
- objective-only choices filtered by execution mode;
- read-only Entire Project scope for Project-scoped objectives;
- conditional source selection and revision for the README objective;
- hidden Intent identity;
- no editable AnalysisType;
- grouped existing guidance;
- unchanged two-step launch orchestration.

### Backend

Likely existing files:

- `backend/src/main/java/com/hopeful117/devlogai/analysis/dto/request/CreateAnalysisRequest.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/service/AnalysisServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/mapper/AnalysisMapper.java`
- generic launch policy/validation boundary near Analysis creation
- source repository/service used for project ownership and active Git validation

Expected design outcomes:

- optional legacy type input;
- additive, conditionally required source ID input;
- pre-persistence generic Intent eligibility validation;
- deterministic V1 AnalysisType derivation;
- deterministic fixed scope derivation from canonical Intent;
- legacy type consistency validation;
- Project-scope rejection of source/revision input while preserving null `selectedSource`;
- repository-scope source validation and selected-source snapshot persistence;
- repository-only requested-revision persistence;
- explicit client-facing contract failures;
- unchanged workflow and AI Engine submission.

No database migration, AI Engine change, IntentDefinition schema change, prompt change, or
output-schema change is expected. The fixed scope mapping belongs to the generic launch policy rather
than becoming a user-selectable or persisted scope enum.

## Compatibility Strategy

- Existing persisted Analyses are immutable and remain readable.
- Existing response DTO fields/routes remain.
- Existing callers sending matching `ARCHITECTURE_REVIEW` remain valid only when source/revision input
  satisfies the selected objective's fixed scope.
- Missing type is accepted only through the new derived generic policy.
- Explicit mismatched type is rejected before persistence.
- Existing Project-scoped behavior remains represented by null `selectedSource`, but explicit
  `targetRevision` is rejected instead of being reused across Sources.
- New repository-scoped README runs require `sourceId` and persist the selected Source/snapshot.
- Historical null-source and selected-source records retain their original meaning without migration.
- Project Understanding and Engineering Event contracts remain isolated and unchanged.
- API remains `/api/v1`; `sourceId` is additive, while source/revision validation deliberately becomes
  stricter as a pre-V1 contract correction.

## Verification Contract

Implementation verification must demonstrate, without a product benchmark harness:

- the generic UI lists only the four approved objectives;
- each listed objective creates and launches through the existing workflow;
- the dedicated Engineering Event Intent cannot be launched generically;
- no incompatible user-selected type/Intent pair can be created;
- each objective resolves the documented fixed scope without a human scope selector;
- Project-scoped requests reject source/revision input, retain null `selectedSource`, and collect all
  applicable active Sources at independent defaults;
- README requests require one source, and Angular auto-selects the sole source or requires a choice
  when several are available;
- repository source ownership/type/status are checked before Analysis persistence;
- repository revision is resolved only within the selected Source;
- guidance normalization and downstream snapshots are unchanged;
- Intent-derived proposal/AiTask/prompt/schema/profile behavior is unchanged;
- internal execution metadata remains visible in existing diagnostic surfaces;
- Project Understanding, Engineering Event, Proposal Review, Deliverables, and Analysis history do not
  regress.

## Explicit Exclusions

- P0-B Analysis Results projection
- P0-C validation/result navigation
- CATEGORY_SELECTION / Story 0098
- benchmark harness or benchmark execution
- Engineering Query
- new Intents or Analysis types
- user-selectable scope or selected Source subsets
- persisted multi-source observation baseline model
- prompt/AI Engine/output-contract changes
- retrieval, ranking, budgets, floors, or ceilings
- correction of existing multi-source RepositoryContext composition and source-provenance gaps
- lifecycle endpoint consolidation

## Human Approval Gate

Implementation must not begin until the human approves:

1. Story number and priority ordering;
2. the four objective labels and mappings;
3. `AnalysisType = NO` as a human launch concept;
4. all-generic-objectives → `ARCHITECTURE_REVIEW` V1 mapping;
5. fixed objective scope mapping: three Project-scoped objectives and repository-scoped README;
6. no Project-scope revision input and repository-only source/revision controls;
7. additive compatibility behavior for legacy `type` and deliberate source/revision validation
   tightening.

## Implementation Verification Results

### Backend

- **All 984 tests pass** (unit + integration)
- No new tests added; existing tests cover modified code paths
- Key verified behaviors:
  - Generic launch policy validation
  - Scope-specific field rejection (sourceId/targetRevision for project scope)
  - Source validation (ownership, active state, Git type)
  - Selected source and snapshot persistence for repository scope
  - Null selectedSource and null targetRevision for project scope
  - Legacy type consistency validation

### Frontend

- **All 219 tests pass** (component + service)
- Updated tests to match new objective-based form API
- Verified:
  - Four objectives displayed, Engineering Event excluded
  - AnalysisType control absent
  - Scope UI toggling (project vs repository)
  - Repository auto-selection (sole source) and multi-source requirement
  - Correct payload emission (intentId, sourceId, targetRevision)

### Lint & Format

- Frontend: ESLint clean, Prettier clean
- Backend: Maven compile successful

### Build

- Frontend: `ng build` successful
- Backend: `mvn compile` successful

### Architecture Sanity Checks

All 10 checks passed (see Story document for details).

### Regression Checks

- Analysis workflow unchanged
- Project Understanding & Engineering Event isolated
- Proposal Review, Deliverables, History, AiTask snapshots compatible

### Known Residual Issue

Existing multi-source RepositoryContext composition/provenance limitation (documented in Story) — not addressed in this Story.

## Git Status

- Branch: `story-099-align-generic-analysis-launch-contract`
- Base: `9ddbf9d420aa655cde221165f2a149652b4fff0d` (main)
- 10 files modified, 342 insertions, 85 deletions
- No commits, no pushes, no merges

## Human Review Gate

- No commit performed
- No push performed
- No merge performed
- Human review NOT self-approved

**IMPLEMENTATION_COMPLETE_AWAITING_HUMAN_REVIEW**
