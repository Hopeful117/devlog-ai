# Story 0049 — Graceful Empty States In Project Overview — Implementation Plan

## Status

Completed

## Final Implementation Strategy

Story 0049 started as a frontend rendering refinement, but live verification
showed the user-visible defect had two distinct causes:

* the overview contract exposed only the generic proposal `type` (`INSIGHT`),
  which is not meaningful enough for overview display;
* proposal `confidence` values are stored on a `0..1` scale, while the
  overview rendered them as if they were already percentages.

The final strategy therefore expanded beyond the original frontend-only scope:

* enrich the backend `ProjectState` proposal summary contract with displayable
  insight metadata already present in proposal payloads;
* update the frontend overview to prefer that richer metadata and normalize
  confidence correctly;
* keep section-level empty-state behavior graceful when proposals still lack
  usable detail.

## Step 1 — Enrich backend overview proposal summaries

Targets:

* `backend/src/main/java/com/hopeful117/devlogai/projectstate/dto/inner/ProposalSummary.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapper.java`

Goals:

* expose overview-safe proposal display fields without inventing new data;
* reuse the existing proposal payload as the source of truth;
* preserve the current proposal status and confidence behavior.

Implementation direction:

* add `insightType`, `title`, and `summary` to `ProposalSummary`;
* map those fields from proposal payload keys already used elsewhere in the
  system;
* keep generic `type` for compatibility, but stop relying on it as the only
  display field.

## Step 2 — Update frontend overview rendering

Targets:

* `frontend/src/app/features/project-state/project-state.models.ts`
* `frontend/src/app/features/project-state/project-state-page.ts`
* `frontend/src/app/features/project-state/project-state-page.html`
* `frontend/src/app/features/project-state/project-state-page.scss`

Goals:

* show a meaningful proposal title when available;
* show a secondary insight-type label when useful;
* normalize `confidence` from `0..1` to a human percentage;
* avoid showing rows that still have no usable detail.

Implementation direction:

* extend the frontend model with the new backend fields;
* derive primary and secondary labels from `title` and `insightType`;
* convert `0.95` to `95% confidence`;
* keep section empty states when proposals remain effectively non-displayable.

## Step 3 — Add regression coverage on both sides

Targets:

* `backend/src/test/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapperTest.java`
* `frontend/src/app/features/project-state/project-state-page.spec.ts`

Goals:

* prove that the backend exposes usable overview proposal metadata;
* prove that the frontend renders meaningful titles and correct confidence;
* keep coverage for rows with no usable display detail.

## Step 4 — Validate and redeploy

Validation targets:

* targeted backend mapper/service tests
* targeted frontend overview test
* frontend lint
* frontend format verification
* diff formatting check
* live verification of the deployed `/state` payload

Performed commands:

* `./mvnw -Dtest=ProjectStateMapperTest,ProjectStateProjectionServiceTest test`
* `npm exec ng test -- --watch=false --include='src/app/features/project-state/project-state-page.spec.ts'`
* `npm run lint`
* `npm run format:check`
* `git diff --check`
* `docker compose up -d --build backend frontend`

## Documentation Expectation

Repository documentation update is not required.

Story 0049 changes the project-state response shape for overview proposal
summaries, but this is an internal UI-supporting contract refinement rather
than a repository-level concept change.
