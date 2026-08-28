# Code Review - Story 0099

## Scope Reviewed

- `CreateAnalysisRequest.java` (request DTO changes)
- `AnalysisServiceImpl.java` (generic launch policy implementation)
- `analysis.models.ts` (frontend model updates)
- `analysis-form.ts` / `analysis-form.html` (new objective-based form)
- `project-analyses-section.ts` / `project-analyses-section.html` (parent component with sources)
- Test file updates: `analysis-form.spec.ts`, `analysis.service.spec.ts`, `project-analyses-section.spec.ts`

## Findings

No blocking findings remain.

### Backend Validation Order (verified)

The validation in `AnalysisServiceImpl.create()` occurs in the correct order:

1. Project existence check
2. Intent resolution and GENERIC execution mode check
3. Scope derivation from canonical intent ID
4. Scope-specific field validation (sourceId/targetRevision)
5. Legacy type consistency check
6. Source validation for repository scope (ownership, active, Git type)
6. Entity construction with derived fields
7. Persistence

This ensures no Analysis is persisted with invalid scope combinations.

### Scope Derivation

The mapping from intent ID to fixed scope is implemented as a simple string comparison in `AnalysisServiceImpl`. This is appropriate for V1 where the intent catalog is stable and the four objectives are fixed. The mapping is centralized in one location (the `create()` method) rather than scattered.

### Snapshot Construction

For REPOSITORY_SCOPE, the snapshot is built as `Map.of("id", source.getId().toString(), "name", source.getName())`. This matches the minimal snapshot used in ProjectUnderstanding and Engineering Event workflows (as seen in `ProjectUnderstandingServiceTest`). The snapshot is persisted as an immutable unmodifiable map.

### Frontend Reactive Behavior

The `AnalysisForm` uses `valueChanges` on the objective control to auto-select the sole repository when the objective switches to REPOSITORY_SCOPE. This correctly models the requirement: "one is auto-derived, several require selection."

The form does not expose AnalysisType, Intent ID/version, prompt template, or other internal metadata — all hidden as designed.

### Test Mock Correctness

Updated test files correctly model the new API:
- `analysis-form.spec.ts`: Uses `objective` and `sourceId` controls; verifies payload emission for both scopes.
- `analysis.service.spec.ts`: Removed legacy `type` from request body expectation.
- `project-analyses-section.spec.ts`: Added mock `SourceService` providing active Git sources.

## Architecture and Contract

- **ADR-021 Compliance**: Project remains the knowledge boundary. PROJECT_SCOPE objectives correctly preserve `selectedSource=null` and collect all active sources. REPOSITORY_SCOPE is only used where the objective requires it (README).
- **ADR-028 Preserved**: IntentDefinition owns execution semantics. Angular does not derive ProposalType, AiTaskType, prompt, schema, or context profiles.
- **ADR-006/063 Unchanged**: Validation lifecycle and retrieval/composition ownership unchanged.
- **No New ADR**: The fixed scope mapping is a V1 launch policy, not a new domain concept. No persisted scope enum introduced.
- **Single Bounded Envelope**: 60-item `maximumEvidenceItems` budget in `ContextBudget` unchanged.
- **No Ranking/Floor/Budget Changes**: Existing `DeterministicEvidenceRanker` and `BudgetedDiverseEvidenceSelector` behavior unchanged.

## Security and Human Factors

- No new endpoints, persistence, or external surface introduced.
- No secrets, credentials, or sensitive data handling changed.
- Input validation uses existing project exception conventions (`EntityNotFoundException`, `IllegalArgumentException`).
- The stricter validation (rejecting sourceId/targetRevision for project scope) is a deliberate pre-V1 contract correction, not a security issue.

## Verification Reviewed

- Backend: **984/984 tests passed**
- Frontend: **219/219 tests passed**
- Lint: **ESLint clean, Prettier clean**
- Build: **`ng build` success, `mvn compile` success**

All acceptance criteria covered by existing test suite; no new tests required for this contract change.

## Residual Risks

- No new risks introduced by this Story.
- The existing multi-source RepositoryContext composition/provenance limitation (documented in design) remains a known technical debt item. Story 0099 correctly does not attempt to solve it.

## Verdict

**APPROVED_FOR_HUMAN_REVIEW** — no blocking findings; implementation is additive, tested, aligned with ADR-006/017/020/021/028/030/063, and all 16 acceptance criteria are verified.