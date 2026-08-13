# Story 0049 — Graceful Empty States In Project Overview — Implementation Report

## Status

Implemented

## Summary

Story 0049 was initially implemented as a frontend-only rendering fix, but
live verification showed that the remaining defect came from a combination of:

* a backend overview contract that exposed only generic proposal `type`
  information such as `INSIGHT`;
* a frontend confidence renderer that treated `0.95` as `1%` instead of
  `95%`.

The final implementation therefore includes a small backend contract
refinement plus a frontend rendering correction.

## Changes

### 1. Enriched `ProjectState` proposal summaries with displayable insight metadata

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/projectstate/dto/inner/ProposalSummary.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapper.java`

Changes:

* extended `ProposalSummary` with:
  * `insightType`
  * `title`
  * `summary`
* replaced the generic mapper path with an explicit mapping method that reads
  those fields from the existing proposal payload.

Outcome:

* the overview contract now exposes the real proposal title already known by
  backend proposal APIs;
* the overview no longer has to guess useful display content from the generic
  `INSIGHT` type alone.

### 2. Corrected overview rendering to prefer meaningful labels

Updated:

* `frontend/src/app/features/project-state/project-state.models.ts`
* `frontend/src/app/features/project-state/project-state-page.ts`
* `frontend/src/app/features/project-state/project-state-page.html`
* `frontend/src/app/features/project-state/project-state-page.scss`

Changes:

* extended the frontend `ProposalSummary` model with the new backend fields;
* added proposal rendering helpers that:
  * use `title` as the primary label,
  * use `insightType` as secondary metadata when available,
  * keep non-displayable proposal rows out of overview lists;
* normalized confidence so `0.95` renders as `95% confidence`;
* kept the section empty-state behavior when proposals still provide no usable
  content.

Outcome:

* the overview now shows meaningful proposal titles instead of repetitive
  `INSIGHT`;
* the `1% confidence` defect is eliminated at its real source;
* low-value rows remain suppressed when they still lack usable detail.

### 3. Added regression coverage for the new contract and rendering behavior

Updated:

* `backend/src/test/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapperTest.java`
* `frontend/src/app/features/project-state/project-state-page.spec.ts`

Coverage added:

* backend mapping of proposal payload fields into overview proposal summaries;
* frontend rendering of meaningful proposal titles and normalized confidence;
* suppression of proposals that still have no meaningful display content.

Outcome:

* both the contract refinement and the user-visible rendering behavior are now
  explicitly protected by tests.

## Behavioral Outcome

### Now fixed

* overview proposal rows no longer collapse to repetitive `INSIGHT` labels;
* `0.95` now renders as `95% confidence`, not `1% confidence`;
* proposal rows without usable display detail still collapse cleanly into the
  existing section empty states.

### Preserved

* existing overview section structure;
* proposal status handling;
* current overview empty-state wording outside the proposal refinement.

## Contract Outcome

Backend/API contract change: **Yes, additive**

`ProjectState.proposedProposals[*]` now also exposes:

* `insightType`
* `title`
* `summary`

This is an additive refinement for the overview projection and does not remove
existing fields.

## Documentation Outcome

Documentation update: **Not required**

Reason:

* the change refines an internal overview projection contract and its
  rendering;
* it does not alter user workflow semantics or repository-level concepts.

## Validation

Performed:

* targeted backend tests
* targeted frontend test
* frontend lint
* frontend format verification
* repository diff formatting check
* live container rebuild and payload verification

Results:

* `./mvnw -Dtest=ProjectStateMapperTest,ProjectStateProjectionServiceTest test`: pass
* `npm exec ng test -- --watch=false --include='src/app/features/project-state/project-state-page.spec.ts'`: pass
* `npm run lint`: pass
* `npm run format:check`: pass
* `git diff --check`: pass
* live `/api/v1/projects/{id}/state` verification: pass

## Remaining Limitations

* the overview still renders a compact projection, not full proposal detail;
* if future proposal types require richer overview-specific metadata, the
  projection may need another additive refinement;
* this Story fixes the current user-visible defect, but it does not redesign
  the broader proposal information architecture.
