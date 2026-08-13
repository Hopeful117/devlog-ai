# Story 0049 — Graceful Empty States In Project Overview — Engineering Report

## Status

Completed

## Story Recap

Story 0049 started as a frontend UX cleanup for awkward overview rows such as
confidence-only fragments and bare separators.

During live validation, that framing proved incomplete.

The ugly rendering was not only a presentation problem:

* the backend overview projection exposed only the generic proposal
  `type` (`INSIGHT`), which is too weak for useful overview display;
* the frontend interpreted proposal `confidence` on the wrong scale and turned
  `0.95` into `1%`.

The delivered fix therefore went beyond the original frontend-only boundary in
order to solve the real defect instead of polishing around it.

## Problem

Before the final fix:

* overview proposal rows could render with low-value content;
* even after the first UI pass, the page could still show repetitive generic
  proposal rows because the backend contract lacked a meaningful label;
* confidence values were mathematically wrong in the UI because the projection
  supplied `0..1` values and the frontend rendered them as if they were
  already percentages.

The result was a bad user experience for two reasons at once:

* weak display data;
* incorrect display logic.

## Implemented Outcome

The final implementation fixes both causes.

Backend changes:

* [ProposalSummary.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectstate/dto/inner/ProposalSummary.java:1)
  now includes `insightType`, `title`, and `summary`.
* [ProjectStateMapper.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapper.java:1)
  now maps those fields from the existing proposal payload so the overview
  receives meaningful proposal metadata.
* [ProjectStateMapperTest.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/test/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapperTest.java:1)
  now protects that additive projection behavior.

Frontend changes:

* [project-state.models.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state.models.ts:1)
  now reflects the enriched overview contract.
* [project-state-page.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.ts:1)
  now derives meaningful primary labels from `title`, falls back to
  `insightType`, suppresses non-displayable proposal rows, and normalizes
  confidence from `0..1` to percentages.
* [project-state-page.html](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.html:1)
  now renders proposal titles as primary content with secondary type metadata.
* [project-state-page.scss](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.scss:1)
  supports that clearer hierarchy.
* [project-state-page.spec.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.spec.ts:1)
  now protects both sparse-data behavior and real proposal-display behavior.

## Why This Matters

This Story became a useful reminder that graceful rendering and correct data
contracting are tightly linked.

The final result is materially better because:

* the overview now shows actual proposal titles instead of the generic
  `INSIGHT` label;
* `95% confidence` is now rendered correctly from `0.95`;
* rows with no meaningful detail still collapse gracefully into empty states;
* the UI is no longer “polished wrong”.

## Scope Correction

The important engineering decision in Story 0049 was to leave the original
scope when the live defect proved to be partly backend-owned.

That was the right decision.

Stopping at a frontend-only patch would have left:

* incorrect percentage math in production;
* repetitive low-information proposal rows;
* a story that looked visually improved while still failing its real purpose.

The additive backend contract change is small, local, and justified by the
observed production behavior.

## What The Story Intentionally Does Not Do

Story 0049 does **not**:

* redesign proposal review or proposal detail workflows;
* expose full rationale in the overview;
* redefine the overall project-state information architecture;
* attempt to solve every root cause behind why so many proposals exist in the
  project.

It fixes the overview projection and rendering quality where the defect was
actually observed.

## Tests And Verification

Passed:

* `./mvnw -Dtest=ProjectStateMapperTest,ProjectStateProjectionServiceTest test`
* `npm exec ng test -- --watch=false --include='src/app/features/project-state/project-state-page.spec.ts'`
* `npm run lint`
* `npm run format:check`
* `git diff --check`

Live verification also confirmed that the deployed `/api/v1/projects/{id}/state`
payload now includes:

* `insightType`
* `title`
* `summary`

and that the frontend container was rebuilt with the corresponding rendering
logic.

## Documentation Reconciliation

Documentation update: **Not required**

Reason:

* the change is an additive projection refinement plus a rendering correction;
* it does not alter the broader user workflow or trust model.

## Architectural Outcome

Story 0049 ends in a healthier place than a pure UI patch would have.

The architecture now makes better sense:

* the backend overview projection exposes the minimum meaningful metadata the
  frontend needs;
* the frontend remains responsible for presentation decisions and graceful
  empty-state behavior;
* no fake UI synthesis is needed to compensate for an avoidably weak contract.

That is a cleaner separation than the previous state.

## Workflow Learning

This Story is a strong example of why live verification remains necessary even
when unit tests pass.

The first implementation passed its local checks but still missed the actual
production defect because the real payload shape and confidence scale were not
fully exercised.

The durable lesson is simple:

* a frontend empty-state test policy is not sufficient when display quality
  depends on backend projection semantics;
* overview contracts need tests that reflect real payload shapes, not only
  synthetic sparse placeholders;
* live verification is especially valuable when the system has additive
  projections that compress richer domain objects.

## Final Outcome

Completed.

Story 0049 now fixes the real overview defect by combining a small backend
projection refinement with the correct frontend rendering behavior, instead of
stopping at a cosmetic frontend-only patch.
