# Story 0050 — Internal Human Context Inputs — Implementation Report

## Status

Completed

## Summary

Story 0050 introduced the first usable vertical slice of internal human-authored
project context in DevLog.

The delivered slice includes:

* a dedicated backend entity for project-owned human context inputs;
* project-scoped list/create/archive API endpoints;
* authoritative propagation into `ProjectContextSnapshot` and `AnalysisContext`;
* bounded inclusion in `SelectedKnowledge` prompt projection;
* a first workspace UI under project `Settings`;
* a live seed note created on the `devlog-ai` project.

The implementation preserves the key architectural boundary defined by ADR-052:

human context enriches future analyses without becoming trusted knowledge
directly.

## Implemented Changes

### 1. Added a dedicated backend domain for internal human context inputs

New backend domain:

* `project_human_context_inputs` table with project ownership and
  `ON DELETE CASCADE`
* entity:
  `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/entity/ProjectHumanContextInput.java`
* enums:
  * `ProjectHumanContextInputType`
  * `ProjectHumanContextInputStatus`
* repository:
  `ProjectHumanContextInputRepository`

The first slice supports:

* types:
  * `GOAL`
  * `CONSTRAINT`
  * `ASSUMPTION`
  * `KNOWN_GAP`
  * `DOMAIN_CONTEXT`
* statuses:
  * `ACTIVE`
  * `ARCHIVED`

### 2. Added minimal project-scoped CRUD API

New controller and service:

* `ProjectHumanContextInputController`
* `ProjectHumanContextInputService`
* `ProjectHumanContextInputServiceImpl`

Exposed routes:

* `GET /api/v1/projects/{projectId}/context-inputs`
* `POST /api/v1/projects/{projectId}/context-inputs`
* `PATCH /api/v1/projects/{projectId}/context-inputs/{inputId}/archive`

The first slice intentionally does not add edit/delete endpoints.

### 3. Propagated active inputs into authoritative analysis context

Updated context layers:

* `ProjectContextSnapshot`
* `ProjectContextProviderImpl`
* `AnalysisContext`
* `AnalysisContextServiceImpl`

Active human context inputs are now part of the authoritative project context
surface rather than remaining a frontend-only feature.

### 4. Added bounded AI-facing prompt inclusion

Updated AI-facing transport:

* `SelectedKnowledge`
* `KnowledgeSelectionServiceImpl`
* `SelectedKnowledgePromptProjectionService`

The first slice includes active human context inputs in a dedicated prompt
section:

* `selectedHumanContextInputs`

Selection remains bounded:

* active-only
* stable ordering from the project-context provider
* capped to a small list in `KnowledgeSelectionServiceImpl`

### 5. Added a first workspace UI in project settings

New frontend feature:

* `frontend/src/app/features/project-context-inputs/`

Delivered UX:

* list of saved project notes
* create form with title, type, markdown content
* archive action for active notes
* integration into project workspace `Settings`

The user-facing naming is:

* `Project Notes`

while the backend retains the more explicit architectural name.

### 6. Seeded the motivating use case live

After rebuilding the local stack, the new endpoint was exercised live against
the existing `devlog-ai` project:

* project id: `f3d56247-aada-4a76-982b-e6802c0b309c`

Created live note:

* title: `Medium-term objective`
* type: `GOAL`
* status: `ACTIVE`

Stored content captures the agreed objective of improving the semantic quality
and usefulness of information provided by DevLog to human users and AI agents,
and introducing human context inputs to support that goal.

## Documentation Outcome

Updated canonical documentation:

* `docs/knowledge-model.md`

Reason:

The repository now supports a concrete internal DevLog slice for human project
context inputs and the knowledge model should acknowledge that this context may
be persisted inside DevLog while remaining distinct from trusted knowledge.

No broader documentation update was required in this slice.

## Validation Performed

### Backend targeted tests

Executed:

```bash
./mvnw -Dtest=ProjectHumanContextInputServiceTest,ProjectHumanContextInputControllerWebMvcTest,SelectedKnowledgePromptProjectionServiceTest,ProjectContextProviderTest,AnalysisContextServiceTest test
```

Result:

* passed

### Frontend targeted tests

Executed:

```bash
npm exec ng test -- --watch=false --include='src/app/features/project-context-inputs/project-context-input.service.spec.ts' --include='src/app/features/project-context-inputs/project-context-inputs-section.spec.ts'
```

Result:

* passed

Additional integration-focused frontend run:

```bash
npm exec ng test -- --watch=false --include='src/app/features/workspace/project-workspace-section-page.spec.ts' --include='src/app/features/project-context-inputs/project-context-inputs-section.spec.ts' --include='src/app/features/project-context-inputs/project-context-input.service.spec.ts'
```

Result:

* passed for the targeted set executed by Angular test filtering

### Frontend quality checks

Executed:

```bash
npm run lint
npm run format:check
git diff --check
```

Result:

* passed

### Live stack rebuild and API verification

Executed:

```bash
docker compose up -d --build backend frontend
curl -sf http://localhost:18080/api/v1/projects/f3d56247-aada-4a76-982b-e6802c0b309c/context-inputs
curl -sf -X POST http://localhost:18080/api/v1/projects/f3d56247-aada-4a76-982b-e6802c0b309c/context-inputs ...
```

Observed result:

* the new endpoint was available live;
* the project note was created successfully;
* subsequent GET returned the stored `GOAL` note in `ACTIVE` state.

## Not Implemented In This Slice

Intentionally deferred:

* edit endpoint
* delete endpoint
* version history for note changes
* dedicated workspace tab
* semantic ranking across a large note corpus
* automatic transformation of human context into trusted knowledge

## Conclusion

Story 0050 successfully proves the ADR-052 direction with a small but complete
vertical slice.

DevLog can now persist project-owned human context internally, expose it to the
user, and propagate it into bounded analysis context without collapsing it into
trusted knowledge.
