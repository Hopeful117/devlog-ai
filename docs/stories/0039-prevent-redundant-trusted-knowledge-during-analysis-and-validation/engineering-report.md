# Story 0039 — Prevent Redundant Trusted Knowledge During Analysis And Validation — Engineering Report

## Status

Reported

## Story

### Number

0039

### Title

Prevent Redundant Trusted Knowledge During Analysis And Validation

### Status

Implemented

### Acceptance Criteria

Met for the approved V1 scope.

## Scope Delivered

Implemented:

* business-level duplicate guarding before trusted promotion
* exact trusted-duplicate hard-blocking for accepted `INSIGHT` proposals
* preservation of repeated proposal history
* preservation of legitimate `ENRICHES` behavior
* backend regression tests for duplicate conflicts and enrichment safety

Deferred:

* broad semantic near-duplicate enforcement
* contradiction / supersession lifecycle
* historical duplicate remediation

## Design Outcome

### Boundary retained

`AiTaskResultServiceImpl`

* unchanged as proposal-history persistence boundary

`ValidationServiceImpl`

* now enforces trusted duplicate policy before acceptance / promotion

`InsightPromotionService`

* remains focused on successful trusted promotion

### Why this matters

This keeps ADR-006 intact while adding the downstream safeguard required by
ADR-051.

## Implementation Summary

### Added

* `InsightPayloadSupport`
* `TrustedKnowledgeDuplicateGuard`

### Updated

* `ValidationServiceImpl`
* `InsightPromotionService`
* `ValidationServiceTest`
* `TrustedKnowledgeDuplicateGuardTest`

## Duplicate Behavior

### Now blocked

* exact duplicate accepted `NEW` insights
* exact restatement accepted `ENRICHES` insights

### Still allowed

* repeated proposal history
* legitimate enrichments with materially new content
* non-insight proposal lifecycle

## Quality Gates

* targeted backend tests: **PASS**
* backend `./mvnw verify`: **PASS**
* JaCoCo coverage check: **PASS**
* `git diff --check`: **PASS**
* AI-engine tests: **N/A**

## Documentation Outcome

No canonical documentation update required beyond existing ADR coverage.

## Vault Outcome

* curated vault context materially informed the work: no
* vault action: none
* outcome remained proposal-only: not applicable

## Limitations

1. Duplicate blocking is exact-match oriented, not full semantic
   near-duplicate reasoning.
2. Legacy trusted insights without rich `sourceType` provenance may match less
   precisely.
3. Existing duplicate stock already in the database remains for Story 0040.

## Next Architectural Questions

1. Should strong near-duplicate conflicts remain exact-match-only for a while,
   or gain a narrow heuristic second tier?
2. Should duplicate conflicts eventually create an explicit review artifact
   rather than leaving the proposal in `PROPOSED`?
3. Should legacy trusted insights with missing `sourceType` be backfilled to
   improve duplicate precision?
