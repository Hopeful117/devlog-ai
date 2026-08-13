# Story 0050 — Internal Human Context Inputs — Repository Analysis

## Status

Completed

## Scope Of This Analysis

This Repository Analysis evaluates how to introduce internal human-authored
project context into DevLog as a first-class analysis input.

The goal is to determine:

* where this capability fits in the current architecture;
* which existing boundaries must remain intact;
* what the smallest useful implementation slice is;
* which backend, frontend, and AI-facing surfaces must evolve together.

## Story Context

ADR-052 is now present as a proposed repository decision and defines the target
shape:

* human context is persisted inside DevLog;
* it is project-owned;
* it remains distinct from repository evidence and trusted knowledge;
* it enriches future analyses through bounded selection.

This Story therefore is not a repository-ingestion Story.

It is also not a trusted-knowledge Story.

It is a new internal project-context capability whose main value is to improve
future analysis quality and preserve project-owner intent that is otherwise
invisible to the system.

## Relevant Existing Architecture

The most important current layers are:

1. project-owned business entities such as `Project`, `Source`, `Insight`,
   `EngineeringEvent`
2. deterministic and repository-grounded `AnalysisContext`
3. bounded `SelectedKnowledge` prompt projection
4. frontend workspace sections exposing project-facing read/write workflows

Relevant files already show stable extension points:

### Project-owned domain and CRUD patterns

* `backend/src/main/java/com/hopeful117/devlogai/project/entity/Project.java`
* `backend/src/main/java/com/hopeful117/devlogai/project/controller/ProjectController.java`
* `backend/src/main/java/com/hopeful117/devlogai/project/service/ProjectServiceImpl.java`

These establish the repository’s standard project-scoped CRUD style:

* dedicated entity
* service
* controller
* DTOs
* WebMvc tests
* service tests

### Authoritative analysis context

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContext.java`

`AnalysisContext` already carries several project-level contextual layers beyond
facts and observations:

* decisions
* milestones
* validated proposals
* engineering events
* open challenges
* knowledge relations
* engineering stories

This is a strong architectural signal:

human context inputs belong in the authoritative context layer, not only in a
frontend convenience API.

### AI-facing bounded transport

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`

The current AI-facing flow is:

`AnalysisContext`
→ `KnowledgeSelectionService`
→ `SelectedKnowledge`
→ prompt projection map

This matters because Story 0050 must not stop at persistence or UI.

If the new human inputs never enter the bounded context-selection path, the
feature will store notes but fail its core objective.

### Existing workspace entry points

* `frontend/src/app/features/workspace/project-workspace-section-page.ts`
* `frontend/src/app/features/workspace/project-workspace-section-page.html`

The project workspace already exposes user-facing sections:

* activity
* knowledge
* documentation
* settings

The `settings` section is currently the most pragmatic first host because it
already contains project configuration and source management.

That gives Story 0050 a low-risk UI landing zone without requiring a broader
workspace information architecture redesign.

## Architectural Interpretation

The repository already supports the conceptual move proposed by ADR-052.

Why:

* DevLog is already project-centric rather than repository-centric;
* `AnalysisContext` already accepts multiple contextual layers;
* bounded selection is already a first-class responsibility;
* the frontend already has a project workspace where project-owned inputs can
  live.

The missing part is therefore not architectural permission.

The missing part is a concrete project-owned entity and its wiring across the
backend, selection layer, and UI.

## Key Design Boundary

The most important boundary to preserve is:

* human context input is not trusted knowledge

The new entity must therefore **not** be modeled as:

* an `Insight`
* a `Decision`
* a `KnowledgeEvent`
* a `ValidatedProposal`

It needs its own explicit semantics.

Otherwise the feature would bypass ADR-006 and ADR-049 by allowing user-entered
content to appear as already-trusted project knowledge.

## Candidate Domain Modeling Options

### Option A — Reuse `Source` and add a synthetic source type

Approach:

* model notes as a new `SourceType`
* treat the stored note set as another source owned by the project

Benefits:

* superficially aligns with ADR-021 extensibility

Risks:

* `Source` is currently oriented around analyzable origins and synchronization
  metadata
* user-authored note CRUD is not naturally a synchronization workflow
* the first slice would pay abstraction cost before proving value

Assessment:

* poor fit for the first implementation slice

### Option B — Model a dedicated project-owned entity for internal human context

Approach:

* create a dedicated entity such as `ProjectHumanContextInput`
* attach it directly to `Project`
* keep explicit fields for title, markdown content, type, and status

Benefits:

* matches ADR-052 directly
* preserves semantic clarity
* fits existing CRUD patterns
* easier to surface distinctly in API and UI

Risks:

* introduces a new entity and lifecycle surface

Assessment:

* best fit for this Story

## Candidate UI Placement Options

### Option A — New dedicated workspace section

Benefits:

* semantically clean

Risks:

* wider routing and navigation change
* more UI surface than required for the first slice

Assessment:

* potentially right long term, but larger than needed now

### Option B — Add a “Project Notes” panel under `Settings`

Benefits:

* smallest user-facing vertical slice
* aligns with “project-owned internal configuration/context”
* reuses existing workspace section

Risks:

* not the final ideal information architecture

Assessment:

* best fit for the first slice

## Candidate Context-Integration Options

### Option A — Persist and expose notes, but do not yet include them in analysis context

Benefits:

* smallest coding scope

Risks:

* fails the architectural objective of improving analysis context
* turns the Story into a storage/UI-only slice

Assessment:

* insufficient

### Option B — Include active notes directly in `AnalysisContext` and pass them through bounded selection

Benefits:

* achieves the real feature objective
* keeps context governance in the authoritative backend
* allows explicit distinction between human context and other layers

Risks:

* requires coordinated changes across backend context assembly and prompt
  projection

Assessment:

* required for this Story

## Recommended First Vertical Slice

The smallest useful implementation path is:

1. introduce a dedicated project-owned entity for internal human context inputs
2. support minimal CRUD with:
   * title
   * markdown content
   * type
   * status
3. expose the list and create flow in the workspace `settings` section
4. extend `AnalysisContext` with a dedicated human-context snapshot list
5. include active human-context inputs in `SelectedKnowledge`
6. expose that layer in the AI-facing prompt projection
7. add tests proving:
   * persistence and API behavior
   * separation from trusted knowledge
   * bounded context inclusion
   * first frontend flow

This preserves the real objective while avoiding premature complexity such as:

* full revision history
* ranking heuristics across a large note corpus
* a separate navigation area
* automatic trusted-knowledge promotion

## Open Design Questions Narrowed By Analysis

The repository analysis reduces the most important design questions to these:

1. **Entity name**
   Recommended working name:
   `ProjectHumanContextInput`
   because it is explicit in the backend even if the UI later says
   “Project Notes”.

2. **Status model**
   First slice should likely support a minimal status set such as:
   * `ACTIVE`
   * `ARCHIVED`

   This is sufficient to satisfy bounded selection without over-modeling.

3. **Type model**
   First slice should likely start with a small enum, for example:
   * `GOAL`
   * `CONSTRAINT`
   * `ASSUMPTION`
   * `KNOWN_GAP`
   * `DOMAIN_CONTEXT`

4. **Selection behavior**
   For the first slice, active notes can be included with a small hard cap and
   stable ordering rather than a sophisticated ranking engine.

5. **Seed insertion**
   The first stored note should capture the already-agreed medium-term project
   objective:
   improving the semantic quality and usefulness of information provided to both
   human users and AI agents.

## Testing Implications

This Story crosses a real boundary and therefore needs more than CRUD tests.

Minimum expected coverage:

* entity persistence and repository behavior
* service-level project scoping and status filtering
* controller validation and response mapping
* `AnalysisContext` propagation of active human context
* `SelectedKnowledge` and prompt projection inclusion
* frontend service and UI rendering of the first workflow

The context-path tests are especially important because Story 0050 can appear
complete at the UI layer while still failing its architectural purpose if the
AI-facing context does not actually include the new data.

## Documentation Implications

Canonical repository documentation should likely be updated during
implementation in at least:

* `docs/knowledge-model.md`
* possibly `docs/architecture.md`

The implementation report should confirm whether those updates are required once
the final slice is implemented.

## Conclusion

The repository is well prepared for this feature.

The correct first slice is not repository ingestion and not trusted-knowledge
promotion.

It is a new project-owned human-context entity wired end-to-end through:

* backend persistence
* user-facing CRUD
* authoritative analysis context
* bounded AI-facing selected knowledge

That slice is small enough to implement safely and substantial enough to prove
the architectural value defined by ADR-052.
