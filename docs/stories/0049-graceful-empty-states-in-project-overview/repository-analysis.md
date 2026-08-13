# Story 0049 — Graceful Empty States In Project Overview — Repository Analysis

## Status

Completed

## Scope Of This Analysis

This Repository Analysis focuses on the Project Overview page and, more
specifically, on how it renders partial or semantically weak proposal data.

The goal is to determine:

* where the awkward rendering currently comes from;
* whether the problem is a frontend composition issue or a missing backend
  contract;
* what the smallest safe implementation path is;
* how to keep the Story frontend-scoped without denying that upstream data
  quality may also need future work.

## Story Context

This Story is intentionally frontend-first.

The motivating defect is not that the overview has no data at all.

It is that the current template still renders incomplete proposal entries as if
they were informative rows, which can produce outputs like:

* `— 1% confidence`

That is a presentation failure even if the upstream data is weak.

The Story should therefore improve the user experience of incomplete data
without turning into a root-cause investigation Story.

## Current Rendering Location

The affected surface is:

* `frontend/src/app/features/project-state/project-state-page.html`
* `frontend/src/app/features/project-state/project-state-page.ts`
* `frontend/src/app/features/project-state/project-state-page.scss`
* `frontend/src/app/features/project-state/project-state-page.spec.ts`

Two repeated sections are especially relevant:

1. `activeWork.proposedProposals`
2. `pendingActions.proposedProposals`

Current rendering shape in both places:

* primary text: `proposal.type`
* separator: `—`
* secondary text: confidence or `No confidence`

Conceptually:

`type — confidence`

This is structurally fragile because the row assumes the type string alone is
meaningful enough to carry the line.

If the upstream proposal content is weak, generic, or visually empty in
practice, the row still renders like a complete sentence fragment.

## Data Contract Reality

The current frontend model is:

```ts
export interface ProposalSummary {
  readonly id: string;
  readonly type: string;
  readonly status: 'PROPOSED' | 'ACCEPTED' | 'REJECTED';
  readonly confidence: number | null;
}
```

Important implication:

the overview proposal summary currently contains no richer primary human text
such as:

* title;
* summary;
* rationale;
* domain label;
* explanation.

That means the frontend cannot “recover” a meaningful sentence from hidden
fields.

However, it can still render the available data much more gracefully.

## Architectural Interpretation

This is primarily a frontend composition problem.

Why:

* the backend contract may be sparse, but it is not forcing awkward separators;
* the template is currently choosing a sentence-like composition that overstates
  the usefulness of weak data;
* the same data could be rendered as:
  * a softer metadata chip,
  * an incomplete-state row,
  * a compact proposal marker,
  * or a more explicit “proposal awaiting richer detail” treatment.

So the Story can stay frontend-first as long as it accepts one key reality:

the current backend contract is enough to render gracefully,
but not enough to render richly.

That distinction matters.

## Existing Empty-State Behavior

The overview already handles fully empty sections well in several places:

* `No active work.`
* `No recent changes.`
* `No roadmap data.`
* `No recent knowledge.`
* `No recent evolution.`

This is good.

The current weakness is not full emptiness.

The weakness is **partial emptiness inside a non-empty section**.

That means Story 0049 should not redesign section-level empty states from
scratch.

It should improve row-level graceful degradation.

## Candidate Implementation Directions

### Option A — Frontend-only graceful rendering with proposal metadata demotion

Approach:

* keep the current backend contract unchanged;
* change the proposal row rendering so confidence is secondary metadata, not
  sentence content;
* avoid unconditional separators;
* introduce explicit fallback wording when a proposal exists but lacks richer
  user-facing meaning.

Examples of direction:

* render `proposal.type` as a badge or label rather than as sentence text;
* show confidence only when it adds value;
* omit confidence entirely when it would produce a low-value fragment;
* use intentional incomplete-state copy such as:
  * `Proposal awaiting clearer detail`
  * `Limited proposal detail available`

Benefits:

* fully inside Story scope;
* no API change required;
* directly addresses the visible UX problem.

Risks:

* fallback wording must avoid pretending the data is richer than it is;
* too much UI copy could become noisy if repeated often.

Assessment:

* best fit for this Story.

### Option B — Backend contract enrichment for overview proposal summaries

Approach:

* extend `ProposalSummary` with richer display fields such as title or summary

Benefits:

* could make the overview substantially more informative

Risks:

* this changes repository scope from frontend polish to API evolution;
* it shifts the Story toward upstream data-shape work;
* not required to fix the current awkward rendering.

Assessment:

* valuable as a future improvement, but out of scope for this Story unless the
  frontend analysis proves graceful rendering impossible without it.

## CSS And Presentation Implications

The current `project-state-page.scss` is functional but relatively plain.

This Story likely benefits from small presentational refinement around the
affected proposal rows:

* clearer metadata hierarchy;
* better spacing between primary and secondary information;
* softer visual treatment for incomplete data;
* intentional styling for “limited detail” states.

This should remain a local polish, not a broad overview redesign.

## Testing Implications

Current tests cover:

* loading, not-found, and error states;
* section-level empty states;
* story-number rendering;
* recent knowledge/evolution rendering.

They do **not** yet cover the partial-data proposal rendering problem.

Story 0049 should add focused regression coverage for:

* proposal rows with low-value or incomplete data;
* omission of awkward separator-driven fragments;
* graceful fallback wording or structure when meaningful proposal detail is not
  available;
* preservation of normal rendering when data is actually usable.

## DevLog And Vault Context

This Story is another useful example of the distinction between:

* repository-local truth in DevLog and code artifacts;
* cross-project display principles in the Obsidian vault.

DevLog/repository evidence tells us:

* which exact template currently produces the ugly output;
* which response fields are truly available;
* which tests already protect the page.

The vault contributes the more general display rule:

* missing or partial data must render gracefully;
* secondary metadata must not pretend the row remains informative when the
  primary content is absent.

This complement is healthy:

* DevLog locates the defect precisely;
* the vault explains the principle behind the fix.

## Conclusion

The awkward `— 1% confidence` behavior is caused by a frontend rendering choice
over a sparse but valid backend contract.

The current API does not provide rich proposal text for the overview, but that
does not prevent a graceful UX.

Recommended implementation direction:

* stay frontend-first;
* improve row-level rendering of partial proposal data;
* avoid bare separators and low-value confidence-only fragments;
* add focused regression tests for incomplete-data rendering;
* treat any deeper data-richness issue as a separate future Story.
