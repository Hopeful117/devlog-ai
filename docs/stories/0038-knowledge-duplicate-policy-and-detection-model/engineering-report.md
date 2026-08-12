# Story 0038 — Knowledge Duplicate Policy And Detection Model — Engineering Report

## Status

Reported

## ADR

### Number

ADR-051

### Title

Trusted Knowledge Duplicate Policy

### Status

Accepted

### Main decisions

* duplicate proposal history and duplicate trusted knowledge are different
  policy domains;
* exact and semantic duplicate categories must be distinguished explicitly;
* trusted knowledge should minimize redundant semantic restatement;
* upstream prevention is the primary duplicate-control boundary;
* downstream safeguards are still required before trusted persistence;
* existing duplicates are development debt, not acceptable target behavior.

### Related ADRs

* ADR-006
* ADR-049
* ADR-050

## Engineering Story

### Number

0038

### Title

Knowledge Duplicate Policy And Detection Model

### Scope

Documentation-first architectural slice:

* define duplicate categories;
* define acceptable vs unacceptable duplication;
* define prevention / safeguard / human-review split;
* define minimum comparison signals;
* set the first enforcement posture for follow-up Stories.

### Status

Implemented

### Acceptance Criteria

Met.

## Policy Outcome

### Acceptable

* repeated proposal history with distinct lifecycle meaning;
* legitimate enrichments;
* historically distinct successor knowledge.

### Not acceptable as steady state

* exact trusted duplicates;
* obvious semantic trusted restatements with no meaningful distinction;
* repeated `NEW` records where the correct behavior should be:
  - no proposal;
  - `ENRICHES`;
  - or downstream rejection.

## Responsibility Split

### Upstream

Primary line of defense:

* trusted knowledge context;
* delta-oriented analysis behavior;
* no-proposal outcomes when nothing materially new is learned.

### Downstream

Protective safety net:

* hard-block obvious exact trusted duplicates;
* warn or soft-block strong near-duplicates;
* preserve human judgment for ambiguity.

### Human review

Not the main detector.

Responsible for:

* ambiguous overlap;
* remediation judgment when history or meaning is unclear.

## Comparison Model

Minimum signals retained for follow-up implementation:

* project scope;
* proposal / knowledge family;
* normalized type;
* `sourceType`;
* enrichment target identity;
* title / summary / rationale similarity;
* evidence overlap;
* accepted relation context when relevant.

## Follow-Up Story Alignment

### Story 0039

Implements prevention and downstream safeguards for new duplicate creation.

### Story 0040

Audits and remediates existing duplicate stock already present in the trusted
layer.

## Documentation Outcome

Canonical documentation updated through:

* `docs/decisions/ADR-051.md`

Dependency alignment updated in:

* Story 0039
* Story 0040

## Vault Outcome

* curated vault context materially informed the work: no
* vault action: none
* outcome remained proposal-only: not applicable

## Quality Gates

* ADR consistency review against ADR-006 / ADR-049 / ADR-050: **PASS**
* Story dependency alignment review: **PASS**
* `git diff --check`: **PASS**
* backend / AI-engine behavioral tests: **N/A** for documentation-only scope

## Limitations

1. The Story defines policy but does not yet enforce it operationally.
2. Strong semantic near-duplicate detection remains an implementation problem
   for Story 0039.
3. Existing database duplicates remain unresolved until Story 0040.

## Next Architectural Questions

1. Which exact Core boundary should hard-block obvious trusted duplicates in
   Story 0039?
2. Should strong semantic near-duplicates be warning-only first, or blocked by
   default when confidence is high?
3. What remediation workflow best preserves traceability while cleaning the
   existing duplicate stock?
