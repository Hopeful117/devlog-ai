# Story 0051 — Reduce Context Noise Before Enrichment

## Status

Draft

## Priority

High

## Objective

Reduce low-value noise in DevLog's project-facing and agent-facing context so
future context enrichment work operates on a cleaner, more useful, and more
robust signal base.

## Motivation

The first slice of internal human context is now available in DevLog.

That is already a positive result: the project can expose an explicit
medium-term objective that is not recoverable from repository evidence alone.

However, the latest evaluation also showed that DevLog still suffers from a
major signal-to-noise problem.

Observed issues:

* `project-state` exposes long lists of semantically redundant proposed
  proposals;
* the same generic project descriptions appear multiple times with only minor
  wording changes;
* important human context is visible, but competes with a large amount of
  repetitive low-value output;
* the Engineering Story Context projection can fail entirely with
  `AgentContextProjectionException: Agent context cannot fit configured
  projection limits`;
* as a result, DevLog can act as a context supplier, but not yet as a reliable
  context distiller.

At this stage, adding more context sources without first reducing noise would
likely increase confusion rather than improve understanding.

The next short-term step should therefore focus on noise reduction before
semantic enrichment.

## Scope

### In Scope

1. Reduce visible semantic duplication in project-facing proposal summaries.
2. Introduce deterministic filtering and/or grouping for low-value redundant
   `PROPOSED` proposals exposed through Project State.
3. Ensure important distinct signals remain visible even after reduction.
4. Make the Engineering Story Context projection robust when the full selected
   context does not fit configured limits.
5. Prefer graceful degradation and compacting over hard projection failure when
   useful evidence can still be returned.
6. Preserve explicit warnings/counters when content is compacted, grouped, or
   omitted.
7. Keep the distinction between:
   * repository evidence,
   * human-authored context,
   * trusted knowledge,
   * reduced transport/projected summaries.

### Out Of Scope

* adding new context sources
* semantic enrichment of project knowledge itself
* vector retrieval, embeddings, or semantic search
* collaborative note workflows beyond the existing first slice
* broad redesign of the entire project cockpit information architecture
* changing trusted-knowledge validation semantics

## Constraints

* reduction must be deterministic and explainable
* noise reduction must not silently hide the only instance of a meaningful
  signal
* projection compaction must preserve a usable agent-facing contract
* project-state filtering must not mutate underlying proposal history
* the Story must optimize signal quality, not introduce a second AI ranking
  engine

## Acceptance Criteria

* AC-1: project-facing `proposedProposals` sections no longer display obvious
  semantic duplicates as separate first-class items when they are materially
  the same proposal.
* AC-2: DevLog applies a documented deterministic policy for reducing redundant
  proposal noise in Project State while preserving distinct high-value items.
* AC-3: active human context remains visible and is no longer drowned out by a
  long tail of repetitive generic proposals.
* AC-4: `POST /api/projects/{projectId}/engineering-story-context` no longer
  fails with a hard projection error merely because the initial selected context
  exceeds transport limits, as long as a degraded usable payload can still be
  produced.
* AC-5: when compaction removes or groups content, the response exposes bounded
  warnings/counters so the reduction remains explicit.
* AC-6: tests cover proposal-noise reduction, deterministic grouping/filtering,
  projection degradation behavior, and the non-regression path for human
  context visibility.
* AC-7: documentation explains the reduction policy at the relevant project
  state / projection boundary when repository behavior or contracts are
  changed.

## Dependencies

* ADR-046 — Separate Rich Repository Context from Agent Transport Projection
* ADR-052 — Internal Human Context Inputs for Analysis
* Story 0019 — Agent-Ready Engineering Story Context
* Story 0050 — Internal Human Context Inputs

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
