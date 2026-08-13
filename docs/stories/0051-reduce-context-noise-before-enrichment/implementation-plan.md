# Implementation Plan — Story 0051

## Goal

Implement Story 0051 by reducing low-value duplicate noise in project-facing
proposal summaries and by making the Engineering Story Context projection more
robust under transport pressure.

The plan deliberately stays at the projection/summarization boundaries.

It does not introduce new AI ranking behavior and does not change proposal
history persistence.

## Design Principles

* noise reduction must be deterministic
* reduction must happen at projection boundaries, not in persistence
* project-state cleanup must not mutate proposal stock
* agent projection must prefer degraded usefulness over avoidable hard failure
* warnings and counters must remain explicit when reduction occurs

## Step 1 — Introduce a deterministic proposal-noise reduction policy for Project State

### Objective

Reduce redundant `PROPOSED` proposal clutter before building
`ActiveWorkSection` and `PendingActionsSection`.

### Planned changes

Add a narrow backend policy component near the project-state boundary, for
example under:

* `backend/src/main/java/com/hopeful117/devlogai/projectstate/`

Potential shape:

* `ProjectStateProposalNoiseReducer`
* or equivalent helper/service owned by the project-state projection layer

The reducer should:

* transform `ValidatableProposal` records into deterministic comparison keys
* remove exact or obvious near-duplicate proposal summaries
* keep one representative item per duplicate cluster
* prefer the strongest representative using deterministic tie-breaks

### Recommended comparison inputs

Use normalized summary-level fields only, for example:

* proposal type
* normalized `insightType`
* normalized title
* normalized summary

Normalization should remain intentionally simple:

* trim
* lowercase
* collapse whitespace

The first implementation should prefer conservative reduction:

* exact normalized duplicates first
* narrowly bounded grouping of obviously repetitive project presentation /
  technology / architecture summaries only if needed by the data shape

### Representative selection policy

When multiple proposals collapse into one reduced item, prefer the
representative with:

1. non-blank title over blank title
2. longer/more specific summary over weaker summary
3. higher confidence
4. stable final tie-breaker such as UUID ordering

### Why here

This keeps reduction authoritative for all project-state consumers and avoids
duplicating business rules in Angular.

## Step 2 — Integrate the reducer into Project State assembly

### Objective

Ensure both active-work and pending-actions proposal sections benefit from the
same deterministic reduction.

### Planned changes

Update
[ProjectStateProjectionServiceImpl.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectstate/service/ProjectStateProjectionServiceImpl.java)
to:

* fetch raw `PROPOSED` proposals as today
* pass them through the reducer before mapping
* reuse the same reduced list for:
  * `ActiveWorkSection`
  * `PendingActionsSection`

Potential refinement:

* apply a bounded limit after reduction rather than before reduction

This preserves more distinct signal while removing repetition first.

### Mapper impact

[ProjectStateMapper.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapper.java)
can likely stay mostly unchanged, as long as the reducer returns
`ValidatableProposal` representatives.

If explicit grouped metadata becomes necessary, extend DTOs only as much as the
Story needs.

### Recommendation

Prefer keeping the DTO contract small unless grouped-count visibility proves
necessary for clarity.

For Story 0051, reduction without API expansion is likely sufficient if:

* distinct proposals remain visible;
* the visible list becomes materially cleaner.

## Step 3 — Keep frontend project-state rendering aligned with reduced backend output

### Objective

Prevent the frontend from reintroducing redundant display logic or assuming the
old noisy backend shape.

### Planned changes

Review:

* [project-state-page.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.ts)
* [project-state-page.html](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.html)

Expected frontend outcome:

* keep existing filtering of non-meaningful proposals
* rely on cleaner backend-provided proposal lists
* ensure human context remains visible in the objective section

No broad UI redesign is required in this Story.

The frontend should stay simple and reflect the reduced backend signal.

## Step 4 — Extend agent projection with a final compact fallback before hard failure

### Objective

Avoid `500` failures when the selected context can still be turned into a
minimum viable compact payload.

### Current behavior

`AgentContextProjectionService` currently degrades through:

1. related references
2. reasons
3. declarations
4. content text
5. tail evidence removal

Then `removeTailEvidence(...)` throws if even one remaining evidence item does
not fit.

### Planned changes

Extend
[AgentContextProjectionService.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionService.java)
with one documented last-resort step before throwing.

The compact fallback should operate only on the final remaining evidence item
and strip optional payload that is still preventing transport fit.

Possible reductions, ordered conservatively:

1. compact verbose summary if necessary
2. remove optional extraction/provenance fields that are not required by the
   minimum contract
3. collapse content/symbol structures to status-only forms

The exact fallback must preserve a minimum viable evidence contract for the
existing agent consumer:

* layer
* kind
* reference
* usable summary and/or equivalent navigational anchor

### Important rule

Throw `AgentContextProjectionException` only when the minimum viable compact
representation itself cannot fit.

That preserves explicit failure semantics while making avoidable hard failures
rarer.

## Step 5 — Preserve explicit reduction warnings and accounting

### Objective

Ensure reduction remains visible and auditable.

### Planned changes

For agent projection:

* add a new bounded warning for the final compact fallback if introduced
* update accounting if additional payload classes are removed

For project-state proposal reduction:

* if no API expansion is introduced, document the reduction policy clearly in
  code/tests and implementation report
* if grouped-count metadata becomes necessary, expose it in a bounded response
  field

### Recommendation

Keep project-state reduction warnings out of the public API unless the user
experience proves ambiguous.

For this Story, the main explicitness requirement is strongest on the
Engineering Story Context projection, because that is already a bounded
diagnostic transport contract.

## Step 6 — Add focused backend tests

### Project State tests

Extend or add tests under:

* `backend/src/test/java/com/hopeful117/devlogai/projectstate/service/`
* `backend/src/test/java/com/hopeful117/devlogai/projectstate/mapper/`

Minimum coverage:

* exact duplicate proposal summaries collapse to one visible representative
* distinct proposals are preserved
* representative selection is deterministic
* active-work and pending-actions both use the reduced list
* human context remains visible alongside the reduced proposal stock

### Agent projection tests

Extend
[AgentContextProjectionServiceTest.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/test/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionServiceTest.java)
to cover:

* final compact fallback on one remaining evidence item
* success path when old code would have thrown
* explicit failure only when even the minimum viable representation cannot fit
* warning/counter behavior for the new degradation step

## Step 7 — Add focused frontend tests

### Objective

Verify the overview renders the reduced signal cleanly.

### Planned areas

* `frontend/src/app/features/project-state/project-state-page.spec.ts`
* `frontend/src/app/features/project-state/project-state.service.spec.ts`

Minimum coverage:

* proposals with meaningful labels still render
* reduced proposal fixtures do not regress rendering
* human context note remains visible in the objective section

Frontend tests should not own deduplication semantics. They should verify the
page behaves correctly with the reduced backend shape.

## Step 8 — Documentation reconciliation

### Objective

Update canonical docs if the repository contract or architecture boundary
meaningfully changes.

Likely candidates:

* [docs/architecture.md](/home/ludo/Bureau/workspace/devlog-ai/docs/architecture.md)

Possible additions:

* project-state surfaces a reduced summary view rather than raw proposal stock
* agent projection includes an additional last-resort compacting stage before
  hard failure

Only update documentation if the implementation changes visible repository
behavior or boundary semantics materially.

## Validation Plan

### Backend

Run focused tests first:

* proposal-noise reduction tests
* `AgentContextProjectionServiceTest`
* `ProjectStateProjectionServiceTest`
* related mapper/controller tests as needed

Then run broader backend validation:

* `./mvnw clean verify -B`

### Frontend

Run focused Angular tests for project-state rendering and service fixtures.

If templates or models change materially, also run:

* lint
* format check

### Live validation

Re-check:

* `GET /api/v1/projects/{projectId}/state`
* `POST /api/projects/{projectId}/engineering-story-context`

Expected live outcome:

* fewer semantically repetitive proposals in project state
* human context remains visible
* engineering-story-context returns a usable payload instead of an avoidable
  `500`

## Risks / Watchouts

* overly broad proposal grouping can erase distinct intent
* too-weak final projection fallback can technically succeed while becoming
  practically useless
* project-state and frontend assumptions can drift if reduction is implemented
  in multiple places

## Recommended Execution Order

1. implement backend proposal-noise reducer
2. wire reducer into project-state service
3. implement agent projection final compact fallback
4. update focused tests
5. reconcile frontend fixtures/rendering
6. run validation and live checks
7. reconcile docs if needed
