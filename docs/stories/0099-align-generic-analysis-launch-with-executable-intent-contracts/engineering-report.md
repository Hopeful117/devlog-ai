# Engineering Report - Story 0099

## Delivery State

Story 0099 is **IMPLEMENTATION_COMPLETE_AWAITING_HUMAN_REVIEW** — Work is on branch
`story-099-align-generic-analysis-launch-contract` off HEAD
`9ddbf9d420aa655cde221165f2a149652b4fff0d` on `main`, awaiting human review before any commit/merge.

## Story Outcome

The generic Analysis launcher now presents exactly four human-facing engineering objectives instead of raw internal metadata. Each objective deterministically derives:
- Fixed scope (PROJECT_SCOPE or REPOSITORY_SCOPE)
- AnalysisType (ARCHITECTURE_REVIEW for all four V1 objectives)
- Validation rules for sourceId and targetRevision
- Core continues to derive ProposalType, AiTaskType, prompt template, output schema, and context profiles from the resolved IntentDefinition.

The legacy editable AnalysisType is removed from the Angular launch UI but preserved in persistence, diagnostics, and internal context behavior.

## Backend

### Generic Launch Policy

`AnalysisServiceImpl.create()` now centralizes the V1 launch policy:

1. **Intent Resolution**: Resolves `intentId` via `IntentCatalog`; rejects non-`GENERIC` execution modes before persistence.
2. **Scope Derivation**: Maps canonical intent IDs to fixed scopes:
   - `describe-project-v1` → PROJECT_SCOPE
   - `architecture-overview-v1` → PROJECT_SCOPE
   - `analyze-engineering-decision-v1` → PROJECT_SCOPE
   - `generate-readme-v1` → REPOSITORY_SCOPE
3. **Validation** (before persistence):
   - PROJECT_SCOPE: rejects `sourceId` and `targetRevision` (400)
   - REPOSITORY_SCOPE: requires `sourceId`; validates source ownership, active state, Git type
   - Legacy `type`: if present, must equal derived `ARCHITECTURE_REVIEW` (400 on mismatch)
4. **Entity Construction**:
   - PROJECT_SCOPE: `selectedSource=null`, `selectedSourceSnapshot=null`, `targetRevision=null`
   - REPOSITORY_SCOPE: fetches Source, synchronizes via `WorkspaceManager`, builds snapshot (id + name), persists selected Source + snapshot + requested revision
5. **Derived Fields**: `AnalysisType = ARCHITECTURE_REVIEW`, intentId/version, userGuidance, status=PENDING

### Request Contract Evolution

| Field | Before | After |
|-------|--------|-------|
| `projectId` | required | required |
| `type` | required (ARCHITECTURE_REVIEW \| PROJECT_EVOLUTION) | optional legacy; must match derived if present |
| `intentId` | required | required (filters to GENERIC intents only) |
| `sourceId` | not present | optional; required for REPOSITORY_SCOPE, rejected for PROJECT_SCOPE |
| `targetRevision` | optional | optional; rejected for PROJECT_SCOPE, allowed for REPOSITORY_SCOPE |
| `userGuidance` | optional | unchanged |

### Compatibility

- Existing clients sending matching `ARCHITECTURE_REVIEW` remain compatible **only when** source/revision fields satisfy the objective-derived scope policy.
- Old README callers must now supply `sourceId`; old project-scoped callers must stop sending `targetRevision`.
- Explicit mismatched legacy `type` rejected before persistence with contract error.
- No API version change; validation deliberately stricter as pre-V1 contract correction.

## Frontend

### Objective-Based Launch Form

- **Objective Selector**: Four cards with human-readable labels/descriptions backed by immutable intent IDs.
- **Scope UI** (derived, not selectable):
  - PROJECT_SCOPE objectives: read-only "Entire Project" badge + active source count; repository/revision controls hidden.
  - REPOSITORY_SCOPE objective: repository dropdown (auto-selects sole active Git source, requires choice when multiple) + optional revision field.
- **Guidance**: All six existing fields preserved, grouped into "Evidence emphasis" (focus, priorities, output context) and "Output preferences" (audience, level of detail, writing style).
- **Internal Metadata Hidden**: AnalysisType, Intent ID/version, prompt template, execution mode, ProposalType, AiTaskType, context profiles removed from primary launch UI; preserved on diagnostic/detail surfaces.

### Data Flow

1. `ProjectAnalysesSection` fetches active Git sources (filtered for active + Git type) via `SourceService`.
2. Maps generic intents to objectives with fixed scope.
3. Passes objectives and sources to `AnalysisForm`.
4. `AnalysisForm` emits `CreateAnalysisRequest` with `intentId`, conditional `sourceId`, and optional `targetRevision`.
5. Two-step lifecycle unchanged: `POST /api/v1/analyses` → persist PENDING → `POST /api/v1/analyses/{id}/workflow`.

## Acceptance Assessment

All 16 acceptance criteria implemented:

1. ✅ Generic launcher presents exactly four approved objectives; `analyze-engineering-event-v1` excluded.
2. ✅ Primary UI does not display AnalysisType, Intent ID/version, prompt template, execution mode, ProposalType, AiTaskType, context profiles, collector mechanics.
3. ✅ Each objective deterministically resolves its versioned IntentDefinition and derives ARCHITECTURE_REVIEW.
4. ✅ Generic launch rejects non-GENERIC Intent before Analysis creation.
5. ✅ Legacy explicit AnalysisType accepted only when matching derived V1 type; incompatible rejected before persistence.
6. ✅ Each objective resolves fixed scope policy; no independent scope selector in UI.
7. ✅ Project-scoped objectives show Entire Project, submit no source/revision, preserve `selectedSource=null`, collect all active sources at independent defaults.
8. ✅ Repository-scoped README requires one active Git source; Angular auto-selects sole source or requires choice when multiple.
9. ✅ Repository scope persists selected source + immutable snapshot; optional revision resolved only against that source.
10. ✅ Core rejects source/revision for Project scope; rejects absent/invalid source for Repository scope before persistence.
11. ✅ With no applicable active Git source, launch unavailable; Core rejects before persistence.
12. ✅ All six UserGuidance fields available with unchanged validation/priority semantics; UI distinguishes deterministic evidence emphasis from model-only output preferences.
13. ✅ Core derives ProposalType, AiTaskType, prompt template, output schema, context profiles exclusively from resolved IntentDefinition.
14. ✅ Analysis workflow, AiTask snapshot, provider submission, proposal validation, Deliverable, history contracts behaviorally compatible.
15. ✅ Analysis detail/diagnostics expose internal type, Intent, AiTask, prompt/version, scope, source/revision provenance, execution metadata.
16. ✅ Project Understanding and dedicated Engineering Event launch behavior unchanged.

## Verification

- **Backend**: 984/984 tests pass
- **Frontend**: 219/219 tests pass
- **Lint**: ESLint clean (frontend)
- **Format**: Prettier clean (frontend)
- **Build**: `ng build` success, `mvn compile` success

No failures, no errors, no skipped tests.

## Residual Technical Debt

**Existing multi-source RepositoryContext composition/provenance limitation** (known, documented in design):
- Some context remains project-wide
- Some repository structure may come from a single source
- Selected evidence can lose visible source provenance

Story 0099 does not address this; it will be handled separately before multi-repository Analysis V1 is declared fully reliable.

## Engineering Verdict

**IMPLEMENTATION_COMPLETE_AWAITING_HUMAN_REVIEW** — implementation complete, all quality gates passed, all acceptance criteria verified, residual debt documented. Git delivery owned by human engineer.