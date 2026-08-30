# Story 0105 — Preserve Proposal-Specific Information in Canonical Analysis Results

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Baseline

- Baseline SHA: `23424276b73445aa5d3345e797b13e845658abc8`
- Working branch: `story/0105-proposal-specific-analysis-results`
- Governing investigation: `docs/investigations/post-0104-structured-context-to-analysis-output-investigation.md`
- Prior merged story: Story 0104

## Objective

Stop losing already-persisted proposal-specific information between `ValidatableProposal` and the canonical human-facing `GET /api/v1/analyses/{id}/result` projection.

## Approved Scope Boundary

```text
ValidatableProposal
        ↓
ProposalSummaryMapper
        ↓
ProposalSummary
        ↓
AnalysisResultResponse
        ↓
GET /api/v1/analyses/{id}/result
        ↓
Angular AnalysisResultPage
```

Preserved separately:

- `supportingFactIds`
- `supportingObservationIds`

Explicitly not introduced:

- `groundingIds`
- raw payload exposure
- prompt changes
- Python schema changes
- generation changes
- context composition changes
- evidence selection changes
- database schema changes

## Verified Outcome

Implemented and verified behavior:

- INSIGHT proposals expose `rationale`, `insightType`, `deltaType`, `supportingFactIds`, and `supportingObservationIds`
- ENGINEERING_DECISION proposals expose `context`, `choice`, `rationale`, and `consequences`
- ENGINEERING_DECISION grounding remains empty and is not fabricated
- ENGINEERING_EVENT proposals expose `category`, `significance`, `supportingFactIds`, and `supportingObservationIds`
- Angular result page renders the type-specific fields and separate grounding counts

## Verification Summary

- Targeted backend tests: `22` passing
- Full backend suite: `1049` passing
- `mvn clean verify`: `BUILD SUCCESS`
- JaCoCo: configured, report generated, coverage gate passed
- Frontend tests: `260` passing
- `ng build`: success
- `eslint`: clean
- `prettier --check`: clean
- Product benchmark executed on the canonical `describe-project-v1`, `architecture-overview-v1`, and `analyze-engineering-decision-v1` intents

## Lifecycle State

- Story materialization: completed
- Human design approval: completed
- Implementation: completed
- Verification: completed
- Human implementation review: pending
- Commit: not authorized
- Push: not authorized
- Merge: human-only

Terminal state:

`STORY_0105_IMPLEMENTATION_READY_FOR_HUMAN_REVIEW`
