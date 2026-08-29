# Story 0102 — Restore Analysis Launch and Canonical Result Consultation

## Status

**READY_FOR_REPOSITORY_ANALYSIS**

## Priority

**P0 — PRODUCT REGRESSION**

## Objective

Restore the Analysis product to a functional state by fixing two P0 regressions that make the
Analysis workflow unusable for human operators.

## Human Story

As a human engineer working in a Project,
I want to choose a supported engineering objective and bounded guidance,
so that every offered Analysis launch is executable and I do not need to understand DevLog's
internal Analysis, Intent, scope, Prompt, or AiTask vocabulary.

Currently, the Analysis UI exposes a raw AnalysisType selector that Story 0099 intentionally
removed. This selector allows selecting `PROJECT_EVOLUTION`, which the backend correctly
rejects because all generic V1 objectives derive `ARCHITECTURE_REVIEW`. Additionally, the
canonical Analysis Result may become inaccessible due to a type-casting failure during
result composition.

## Context / Problem

Two reproducible regressions have been identified:

**Regression A — Frontend violates Story 0099 launch contract:**

Story 0099 established that AnalysisType is NOT human-selectable in generic launch. The human
chooses an engineering objective; Core derives the AnalysisType deterministically. All four
generic V1 objectives derive `ARCHITECTURE_REVIEW`.

Story 0100 accidentally reverted the Story 0099 frontend changes, reintroducing the
AnalysisType selector that Story 0099 intentionally removed. The frontend now exposes
`PROJECT_EVOLUTION` as a choice, which the backend correctly rejects.

**Regression B — Architecture Review result becomes inaccessible:**

Some Architecture Review + Intent combinations appear to start successfully and execute
upstream processing. However, the UI eventually displays an error. Backend logs include:

```text
Caused by: tools.jackson.databind.DatabindException:
Cannot cast
com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse$FactItem
to
com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse$EvidenceItem
```

This is a type-casting failure during canonical Analysis Result composition. The Analysis
execution succeeds; only the human-facing result surface is broken.

## Goal

- Reproduce both regressions with automated tests
- Identify root causes
- Implement minimal corrections
- Verify the real user workflow

## Governed By

- ADR-006: proposal lifecycle semantics unchanged
- Story 0099: generic launch contract (AnalysisType not human-selectable, objective-driven)
- Story 0100: canonical Analysis Result semantics intact
- Story 0101: trusted-artifact navigation intact
- Existing repository conventions and acceptance criteria

## Architectural Constraints

- AnalysisResult remains a query-time read model
- IntentDefinition ownership of execution semantics preserved
- Backend ownership of business validation preserved
- Angular ownership of SPA navigation preserved
- No new persistence solely to repair projection
- No Analysis redesign
- No RAG, vector retrieval, Engineering Query, category balancing, or Analysis-depth improvements

## Acceptance Criteria

1. Regression A is reproduced by automated test
2. Regression A root cause is identified and documented (Story 0100 reverted Story 0099 frontend)
3. Regression A is fixed by restoring Story 0099 frontend contract
4. Regression A fix is verified by automated test
5. Regression B is reproduced by automated test
6. Regression B root cause is identified and documented (unsafe evidence casting)
7. Regression B is fixed with explicit projection/mapping
8. Regression B fix is verified by automated test
9. Frontend and backend quality gates remain green
10. Existing Story 0099, Story 0100, and Story 0101 behavior remains intact
11. Story 0099 acceptance criteria are re-verified

## Explicit Non-Scope

- Analysis quality/depth improvements
- Story 0098
- Benchmark work
- New Analysis types
- New Intents
- New agents
- LLM/provider redesign
- Prompt tuning
- RAG/retrieval work
- Unrelated frontend redesign
- Unrelated refactoring
- Authentication/authorization work

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`

Human approval and merge remain pending.
