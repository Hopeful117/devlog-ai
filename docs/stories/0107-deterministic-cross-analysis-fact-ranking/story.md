# Story 0107 — Deterministic Cross-Analysis Fact Ranking

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Baseline

- Baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Baseline branch: `main`
- Implementation branch: `story/0107-deterministic-cross-analysis-fact-ranking`
- Governing investigation: `docs/investigations/story-0106-knowledge-collection-determinism-investigation.md`
- Originating story: Story 0106
- Governing ADR: `docs/decisions/ADR-064.md` (`KEEP_PAUSED`)

## Objective

Ensure cross-Analysis deterministic Fact ranking by replacing Analysis-local persistence identity tie-breaking with a stable canonical Fact ordering independent of persistence UUIDs.

## Problem

`KnowledgeSelectionServiceImpl` ranks Facts by score descending, Fact type, and finally `FactSnapshot.id`. Fact UUIDs are generated per Analysis, so persistence identity can change the relative order and bounded selection of otherwise equally ranked Facts.

```text
same semantic candidates
        +
different Analysis-local UUID assignments
        ↓
same semantic ranking and selected Fact set
```

## Approved Scope

### Production

- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`

### Tests

- focused Knowledge Selection regression tests

### Lifecycle

- Story 0107 artifacts in this directory

## Acceptance Criteria

### AC1 — Persistence identity independence

Given two Fact candidate universes with equivalent semantic information but different Analysis-local UUID assignments, when Knowledge Selection ranks those Facts, then their semantic ranking is identical.

### AC2 — Bounded selection stability

Given more same-score and same-type Facts than can survive the Fact budget, when persistence UUID assignments differ, then the same semantic Fact set is selected.

### AC3 — Stable total ordering

The final Fact ranking tie-breaker is deterministic, Analysis-independent, and distinguishes semantically distinguishable Fact snapshots.

### AC4 — Existing relevance semantics preserved

Existing scoring, budgets, closure behavior, and commit-diff policy remain unchanged.

### AC5 — No persistence identity relevance

`Fact.id` does not influence semantic Fact ranking.

### AC6 — Regression protection

Focused tests reproduce the prior UUID-sensitive bounded selection and pass with canonical semantic ordering.

### AC7 — Scope integrity

No changes are made to collectors, documentation overflow policy, prompts, AI Engine, grounding, model configuration, persistence UUID generation, frontend, MCP, database schema, or ADR-064.

## Explicit Non-Scope

- documentation candidate prioritization and overflow policy
- model-facing identity normalization
- deterministic proposal eligibility validation
- live-worktree source semantics
- Observation ranking changes unless a directly equivalent reachable defect is proven
- prompt or model changes
- ADR-064 implementation work

## Evidence Precision

```text
UUID_TIEBREAKER_DEFECT = CONFIRMED
HISTORICAL_CAUSAL_ATTRIBUTION = SUPPORTED_BUT_NOT_STRICTLY_PROVEN
```

Historical runs shared a Git HEAD but were collected from live worktrees; this Story corrects the independently confirmed ranking defect without claiming sole causation for all historical variance.

## Lifecycle State

- Story materialization: completed
- Repository analysis: completed
- Implementation plan: completed
- Implementation: completed
- Verification: completed
- Human implementation review: pending
- Commit: not authorized
- Push: not authorized
- Merge: human-only

Terminal state:

`DETERMINISTIC_FACT_RANKING_IMPLEMENTATION_READY_FOR_HUMAN_REVIEW`
