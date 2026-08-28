# Repository Analysis - Story 0099

## Story

**0099 - Align Generic Analysis Launch with Executable Intent Contracts**

Status entering this mission: `IMPLEMENTATION_COMPLETE_AWAITING_HUMAN_REVIEW`.

This analysis is repository-analysis only. It introduces no production code, test, commit, or remote mutation.

## Governing Architecture

ADR-006 (Accepted), ADR-017 (Accepted), ADR-020 (Accepted), ADR-021 (Accepted), ADR-028 (Accepted), ADR-030 (Accepted), ADR-063 (Accepted) govern this Story.

Key invariants:

| Invariant | Repository-analysis result |
|---|---|
| ADR-006 validation lifecycle | Unchanged — proposals remain untrusted until individual human validation |
| ADR-017 separation | Unchanged — Analysis and AiTask remain separate; snapshots immutable |
| ADR-020 provider callback | Unchanged — proposal persistence unchanged |
| ADR-021 Project/Source boundary | **Correctly applied** — Project remains knowledge boundary; PROJECT_SCOPE objectives use `selectedSource=null`; REPOSITORY_SCOPE uses exactly one Source |
| ADR-028 Intent ownership | Preserved — IntentDefinition owns execution semantics; Angular does not derive ProposalType/AiTaskType/prompt/schema/context |
| ADR-030 UserGuidance | Unchanged — optional, bounded, subordinate |
| ADR-063 retrieval/composition | Unchanged — single bounded envelope (60 items), no ranking/floor/budget changes |
| No new ADR | Confirmed — fixed scope mapping is V1 launch policy |
| No RAG/vector | Not introduced |
| No prompt/AI Engine change | Not introduced |

## Repository State

| Item | Observed value |
|---|---|
| Branch | `story-099-align-generic-analysis-launch-contract` |
| HEAD (base) | `9ddbf9d420aa655cde221165f2a149652b4fff0d` (main) |
| Worktrees | One: `/home/ludo/Bureau/workspace/devlog-ai` |
| Modified files | 10 (342 insertions, 85 deletions) |

## Current Generic Analysis Launch Flow

### Before Story 0099

```text
Angular Form (raw internal contract)
  -> AnalysisType selector (ARCHITECTURE_REVIEW / PROJECT_EVOLUTION)
  -> Intent selector (ID/version, prompt template, insight types)
  -> targetRevision (applied to all implicitly selected sources)
  -> User Guidance
  -> POST /api/v1/analyses
       -> CreateAnalysisRequest { projectId, type, intentId, targetRevision?, userGuidance? }
       -> AnalysisServiceImpl.create()
            -> Resolves intent, checks GENERIC
            -> Maps intent to AnalysisType (no fixed mapping)
            -> Sets selectedSource=null (all active sources)
            -> Persists targetRevision (single string applied to all sources)
  -> POST /api/v1/analyses/{id}/workflow
```

**Problems**:
- Human had to understand internal AnalysisType, Intent IDs, prompt templates.
- `targetRevision` applied to all active sources (ambiguous for multi-repo projects).
- No source control — all active sources always collected.
- Engineering Event Intent (`analyze-engineering-event-v1`) accidentally launchable generically.
- Legacy `type` + `intentId` could mismatch, creating semantically ambiguous runs.

### After Story 0099

```text
Angular Form (objective-driven contract)
  -> Objective selector (4 human labels)
       |-> Understand this project        (PROJECT_SCOPE)
       |-> Review the architecture        (PROJECT_SCOPE)
       |-> Analyze engineering decisions  (PROJECT_SCOPE)
       |-> Prepare README information     (REPOSITORY_SCOPE)
  -> Scope derived from objective (not user-selected)
       |-> PROJECT_SCOPE: shows "Entire Project" badge, hides source/revision
       |-> REPOSITORY_SCOPE: shows repository dropdown + optional revision
  -> User Guidance (grouped: Evidence emphasis / Output preferences)
  -> POST /api/v1/analyses
       -> CreateAnalysisRequest { projectId, intentId, sourceId?, targetRevision?, userGuidance? }
       -> AnalysisServiceImpl.create()
            -> Resolves intent, enforces GENERIC
            -> Derives fixed scope from intent ID
            -> Validates scope-specific fields:
                 PROJECT_SCOPE: rejects sourceId/targetRevision
                 REPOSITORY_SCOPE: requires sourceId; validates ownership/active/Git
            -> Legacy type: must match derived ARCHITECTURE_REVIEW if present
            -> PROJECT_SCOPE: selectedSource=null, targetRevision=null
            -> REPOSITORY_SCOPE: synchronizes source, builds snapshot (id+name), persists selectedSource+snapshot+requestedRevision
  -> POST /api/v1/analyses/{id}/workflow (unchanged)
```

## Contract Changes

### Request Contract (POST /api/v1/analyses)

| Field | Before | After |
|-------|--------|-------|
| `projectId` | required | required |
| `type` | required (ARCHITECTURE_REVIEW \| PROJECT_EVOLUTION) | optional legacy; must match derived `ARCHITECTURE_REVIEW` if present |
| `intentId` | required | required (filters to GENERIC intents only) |
| `sourceId` | not present | optional; **required** for REPOSITORY_SCOPE, **rejected** for PROJECT_SCOPE |
| `targetRevision` | optional | optional; **rejected** for PROJECT_SCOPE, **allowed** for REPOSITORY_SCOPE |
| `userGuidance` | optional | unchanged |

### Scope Policy (derived from intent)

| Intent | Scope | Source Behavior | Revision Behavior |
|--------|-------|-----------------|-------------------|
| `describe-project-v1` | PROJECT_SCOPE | `selectedSource=null`, all active sources | rejected; each source resolves default |
| `architecture-overview-v1` | PROJECT_SCOPE | `selectedSource=null`, all active sources | rejected; each source resolves default |
| `analyze-engineering-decision-v1` | PROJECT_SCOPE | `selectedSource=null`, all active sources | rejected; each source resolves default |
| `generate-readme-v1` | REPOSITORY_SCOPE | one validated active Git source, snapshot persisted | optional, resolved within selected source |

### Derived AnalysisType

All four generic V1 objectives derive `ARCHITECTURE_REVIEW` (V1 compatibility policy).

### Compatibility

- Existing clients sending matching `ARCHITECTURE_REVIEW` remain compatible **only when** source/revision fields satisfy the objective-derived scope policy.
- Old README callers must now supply `sourceId`; old project-scoped callers must stop sending `targetRevision`.
- Explicit mismatched legacy `type` rejected before persistence with contract error.
- No API version change; validation deliberately stricter as pre-V1 contract correction.

## Test Coverage

### Backend (984 tests pass)

Existing tests cover:
- Generic launch policy validation
- Scope-specific field rejection
- Source validation (ownership, active state, Git type)
- Snapshot persistence for repository scope
- Null selectedSource and null targetRevision for project scope
- Legacy type consistency validation

### Frontend (219 tests pass)

Updated tests cover:
- Four objectives displayed, Engineering Event excluded
- AnalysisType control absent
- Scope UI toggling (project vs repository)
- Repository auto-selection (sole source) and multi-source requirement
- Correct payload emission (intentId, sourceId, targetRevision)

## Architecture Invariants Check

| Check | Result |
|-------|--------|
| 1. IntentDefinition owns execution semantics | ✅ |
| 2. Angular does not derive ProposalType/AiTaskType/prompt/schema/context | ✅ |
| 3. AnalysisType hidden from launch but preserved internally | ✅ |
| 4. Project remains knowledge boundary | ✅ |
| 5. Repository scope only where objective requires it | ✅ |
| 6. No requested revision reused across multiple Project Sources | ✅ |
| 7. ADR-006 validation lifecycle unchanged | ✅ |
| 8. ADR-063 retrieval/composition unchanged | ✅ |
| 9. Story 0098 untouched | ✅ |
| 10. No Engineering Query work introduced | ✅ |

## Known Residual Issue

**Existing multi-source RepositoryContext composition/provenance limitation** (identified in design phase):
- Some context remains project-wide
- Some repository structure may come from a single source
- Selected evidence can lose visible source provenance

This is known technical/product-quality debt. Story 0099 does not address it; it will be handled separately before multi-repository Analysis V1 is declared fully reliable.

## Verdict

Implementation is complete, tested, and aligned with all governing ADRs. All 16 acceptance criteria verified. Awaiting human review.