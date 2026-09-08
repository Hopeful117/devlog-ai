# Story 0115 — Story-Aware Knowledge Selection

## Status

**IMPLEMENTED — AWAITING HUMAN REVIEW**

## Baseline

- Baseline SHA: `7e1d468`
- Baseline branch: `design/validated-knowledge-sca-integration`
- Implementation branch: `story/0115-story-aware-knowledge-selection`
- Governing design: `docs/investigations/validated-knowledge-sca-integration-design.md`
- Governing ADRs: ADR-006, ADR-063, ADR-067

## Problem

The deterministic knowledge-selection pipeline is intent-aware but does not materially
rank Facts, Observations, or Insights against the current Engineering Story. Story ID and
requested files primarily filter repository evidence after ranking, while direct Insight
selection is newest-first. A fixed recent candidate window can therefore exclude or
deprioritize older Story-relevant knowledge before final selection.

## Goal

Make existing deterministic knowledge selection sufficiently aware of the current
Engineering Story for the `engineering-story-context-analysis` intent, without completing
the later selected-knowledge-to-SCA integration.

```text
coherent baseline candidates
        -> Story relevance
        -> existing intent and guidance relevance
        -> deterministic ordering
        -> existing KnowledgeBudget
```

## Scope

### In Scope

- Reuse the current latest-profile Analysis as one coherent Fact/Observation baseline.
- Reuse `IntentTerms` for deterministic Story relevance.
- Use only persisted Story title/path and explicit requested files as Story signals.
- Preserve intent and user-guidance scoring.
- Rank relevant Facts and Observations before the bounded candidate cap.
- Rank eligible ACTIVE Insights by Story relevance before recency for the SCA intent.
- Preserve Observation-to-Fact closure and existing final `KnowledgeBudget`.
- Add behavioral tests for relevance, files, guidance, intent, closure, lifecycle, empty
  matches, and determinism.

### Explicit Non-Goals

- Full `AnalyzeStoryContextUseCase` selected-knowledge integration.
- Grounding-contract, provenance-intersection, canonical-reference, or callback validation
  implementation.
- Story persistence changes or Story Markdown parsing.
- Decisions, EngineeringEvents, HumanContextInputs, or KnowledgeRelations redesign.
- Freshness changes or historical backfill.
- RAG, embeddings, vectors, AI ranking, new agents, services, or infrastructure.

## Available Story Signals

`EngineeringStory` currently persists title, story path, status, base/target commits, and
timestamps. It does not persist structured objective, description, acceptance criteria,
component scope, or requested files.

For this Story, deterministic relevance may use:

- current Story title;
- current Story path;
- explicit requested files supplied to repository-context preparation;
- existing intent ID/objective;
- existing user-guidance priorities/focus/output context.

For direct `KnowledgeSelectionService` ranking, the SCA `AnalysisContext` must contain at
most one Engineering Story: the current Story. Ambiguous multi-Story contexts do not
receive a Story-specific boost.

## Design

### Baseline Candidate Retrieval

`RepositoryContextAdapter` retains the existing latest-profile Analysis and bounded
overfetch windows. Before the final adapter caps, it scores candidates against a
deterministic query composed from intent text, current Story title/path, and requested
files. Relevance precedes the final 8-Fact/6-Observation cap; recency is a tie-breaker.

Observation candidates retain complete support only when supporting Facts from the same
baseline are available. The final `KnowledgeSelectionService` closure rules remain
authoritative.

### Direct Knowledge Ranking

For `engineering-story-context-analysis` only, `KnowledgeSelectionServiceImpl` derives
Story terms from the single current Story in `AnalysisContext`.

- Facts score term overlap across type, content, source, and evidence references.
- Observations score term overlap across type and content.
- ACTIVE Insights score term overlap across type, severity, title, content, source type,
  and evidence references.
- Existing intent-specific and guidance scores remain additive.
- Story relevance precedes recency for Insights; recency remains a tie-breaker.
- A no-match selection outcome remains valid; the adapter does not force irrelevant
  Fact/Observation candidates into its bounded Story-preparation pool.

### Determinism

Term extraction and matching use `IntentTerms`. Stable semantic fields and IDs break ties.
No model call participates in retrieval, scoring, or ordering.

## Acceptance Criteria

### AC1 — Story-Relevant Fact Preferred

Given an older Fact matching current Story terms and a newer unrelated Fact in the same
coherent baseline window, the matching Fact is ranked and selected first.

### AC2 — Story Title Affects Ranking

Given otherwise comparable candidates, a candidate matching the current Story title
ranks above one that does not.

### AC3 — Requested Files Affect Candidate Relevance

When explicit files are supplied to repository-context preparation, a Fact whose source
or evidence path matches those files is preferred before the adapter candidate cap.

### AC4 — Existing Intent and Guidance Remain Effective

Intent-specific type scoring and user-guidance term scoring continue to affect Fact and
Observation ordering.

### AC5 — Observation Closure Preserved

Every selected Observation retains all required supporting Facts under the existing final
budget and coherent baseline rules.

### AC6 — Story-Relevant Insight Preferred

For the SCA intent, a Story-relevant older ACTIVE Insight ranks above a newer unrelated
ACTIVE Insight. ARCHIVED and SUPERSEDED Insights remain ineligible.

### AC7 — Relevance Before Candidate Cap

Within the bounded overfetch window, Story relevance is evaluated before the adapter's
final Fact/Observation candidate caps. Recency cannot displace a relevant older candidate
solely because newer unrelated candidates precede it.

### AC8 — Empty and Ambiguous Story Context Remain Valid

No matching candidate is a valid empty adapter result. An absent or ambiguous current
Story does not introduce a nondeterministic or arbitrary Story boost.

### AC9 — Deterministic Output

Repeated selection with identical inputs produces identical ordering and output without
AI calls.

### AC10 — Architecture and Scope Preserved

`KnowledgeSelectionService` and its public interface are reused. The final budget,
grounding, trust, freshness, SCA prompt/output, persistence, and historical analyses are
unchanged.

### AC11 — Quality Gates

Focused knowledge-selection and repository-context tests, the full backend verification
suite with coverage, and `git diff --check` pass. No generated artifacts are committed.

## Test Expectations

- `KnowledgeSelectionServiceTest`: Story title Fact/Observation ranking, guidance and
  intent regression, closure, Story-aware Insight ordering, lifecycle, determinism.
- `RepositoryContextAdapterBoundedKnowledgeTest`: Story/file relevance before cap, older
  relevant candidate beats newer unrelated candidate, empty/no-match behavior, coherent
  latest-profile Analysis.
- Full backend Maven verification with coverage.
- ADR-066 end-to-end quality evaluation is deferred until selected knowledge is wired into
  Story Context Analysis.

## Known Limitations

- Story objective, description, acceptance criteria, and component scope are unavailable
  as structured persisted fields and are not ranked.
- The bounded overfetch window remains a deliberate retrieval boundary; knowledge outside
  it is not considered.
- This Story prepares selection only. SCA still supplies empty selected knowledge until a
  subsequent authorized integration Story.

## Implementation Evidence

### Production

- `KnowledgeSelectionServiceImpl` derives Story terms only for
  `engineering-story-context-analysis` when exactly one current Story is present.
- Story overlap precedes existing intent/guidance ordering for Facts and Observations.
- ACTIVE Insights are filtered and ranked by Story overlap before recency for SCA; the
  existing ACTIVE-only repository query remains authoritative.
- SCA selection emits `knowledge-selection-v5` and records
  `ENGINEERING_STORY_RELEVANCE`; other intents retain v4 behavior.
- `HistoricalSelectedEvidenceSnapshotProjector` accepts the additive v5 snapshot version.
- `RepositoryContextAdapter` resolves the exact requested Story before retrieval,
  separates Story/file relevance from intent relevance, and orders both before recency.
- Adapter Fact/Observation selection uses one latest-profile Analysis, existing 200-row
  overfetch windows, final 8/6 caps, stable ID tie-breaking, and closure-safe support.

### Tests

- Focused selector, adapter, Story scope, lifecycle, and historical projection suite:
  `73` tests passed.
- New behavioral tests: `10` tests covering Fact/Observation/Insight Story relevance,
  older relevant candidates, requested files, intent/guidance preservation, closure,
  empty outcomes, ambiguity fallback, and deterministic repetition.
- Full backend verification: `1120` tests passed; all JaCoCo coverage checks met.
- Full AI Engine regression: `173` tests passed.
- No ADR-066 fixture was changed because selected knowledge is not yet wired into SCA.

### Architectural Preservation

- `KnowledgeSelectionService` public interface and final budget are unchanged.
- No project-wide Fact/Observation query was introduced.
- Grounding, provenance, trust, freshness, SCA prompt/output, and persistence are unchanged.
- No AI call participates in ranking.

## Lifecycle State

- Story materialization: completed by this task
- Repository analysis: completed
- Human design review: completed through the governing design revision
- Human implementation authorization: granted by task request
- Implementation: completed
- Verification: completed (`73` focused, `1120` backend, `173` AI Engine)
- Human acceptance: pending
- Commit: authorized only after implementation and validation pass
- Push: not authorized
- Merge: human-only

Terminal implementation state:

`STORY_0115_IMPLEMENTED_AWAITING_HUMAN_REVIEW`
