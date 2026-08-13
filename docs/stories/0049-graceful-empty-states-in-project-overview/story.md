# Story 0049 — Graceful Empty States In Project Overview

## Status

Draft

## Priority

Medium

## Objective

Improve the Project Overview UX so partial or low-value data does not render as
awkward, misleading, or visually broken content.

## Motivation

The current overview can display proposal rows whose primary human meaning is
effectively empty while still rendering secondary metadata such as confidence.

This produces outputs like:

* `— 1% confidence`

which are both unattractive and unhelpful.

The frontend should render empty or partial data gracefully even when the
underlying upstream data is weak.

The root cause of why those fields are empty is important, but it does not
belong to this frontend Story.

## Scope

### In Scope

1. Improve graceful rendering of partial proposal data in the project overview.
2. Prevent awkward separators or low-value metadata fragments when the primary
   content is missing.
3. Introduce clearer empty or incomplete-state wording where appropriate.
4. Polish the affected overview rows or cards so missing data still feels
   intentional.
5. Add focused frontend regression coverage for the refined rendering behavior.

### Out of Scope

* backend investigation into why proposal summary data is missing or weak
* redesign of the project-state API contract unless analysis proves it is
  impossible to render the current response gracefully
* broader overview information architecture redesign unrelated to incomplete
  data rendering

## Constraints

* preserve the existing project-state backend contract unless a concrete
  blocker is found
* keep the fix frontend-first
* do not hide meaningful information when it is actually present
* distinguish gracefully between:
  * useful content,
  * incomplete content,
  * empty state

## Acceptance Criteria

* AC-1: proposal rows in the overview no longer render awkward fragments such
  as bare separators or confidence-only content when primary content is absent.
* AC-2: incomplete data is rendered intentionally and readably.
* AC-3: fully empty sections remain explicit and graceful.
* AC-4: focused frontend tests cover the refined overview rendering behavior.

## Dependencies

* none

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
