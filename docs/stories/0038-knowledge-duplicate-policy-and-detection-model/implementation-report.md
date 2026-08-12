# Story 0038 — Knowledge Duplicate Policy And Detection Model — Implementation Report

## Status

Implemented

## Summary

Implemented Story 0038 as a documentation-first architectural slice.

The Story does not add operational duplicate-enforcement behavior yet.

Instead, it establishes the governance baseline required before code changes:

* duplicate policy is now explicit;
* proposal history and trusted knowledge are treated as separate policy domains;
* the primary responsibility split is defined:
  - upstream prevention;
  - downstream safeguards;
  - human review for ambiguity;
  - remediation of legacy debt.

This baseline is formalized in `ADR-051 — Trusted Knowledge Duplicate Policy`.

## Changes

### 1. Added ADR-051

Added:

* `docs/decisions/ADR-051.md`

The ADR formalizes:

* duplicate categories across the knowledge lifecycle;
* the difference between acceptable repeated proposal history and unacceptable
  trusted-knowledge redundancy;
* the preferred duplicate-control split between upstream, downstream, and human
  review;
* the initial enforcement posture for exact duplicates and strong
  near-duplicates;
* the treatment of existing trusted duplicates as remediation debt rather than
  target behavior.

### 2. Aligned follow-up Stories with the new ADR

Updated:

* `docs/stories/0039-prevent-redundant-trusted-knowledge-during-analysis-and-validation/story.md`
* `docs/stories/0040-audit-and-remediate-existing-trusted-knowledge-duplicates/story.md`

Changes:

* added `ADR-051` to follow-up Story dependencies;
* kept the sequence intact:
  - Story 0039 = prevention and safeguards for new duplicate creation
  - Story 0040 = audit and remediation of existing duplicate stock

No renumbering or scope expansion was required.

## Decision Outcome

### Acceptable

* repeated proposal history when it represents distinct lifecycle events;
* legitimate enrichments;
* historically distinct successor knowledge.

### Not acceptable as steady state

* exact trusted duplicates in the same project;
* obvious semantic restatements accepted as separate trusted knowledge without
  meaningful distinction;
* repeated `NEW` knowledge when the correct outcome should have been:
  - no proposal;
  - `ENRICHES`;
  - or downstream rejection.

### Enforcement posture

* upstream prevention is primary;
* downstream safeguards remain mandatory;
* human review is for ambiguity, not first-line duplicate detection.

## Documentation Outcome

Documentation update: Required.

Reason:

* the implemented change is itself an architectural policy decision;
* the canonical repository place for that decision is the ADR set;
* follow-up Story dependency references also needed alignment.

Updated documentation:

* `docs/decisions/ADR-051.md`
* Story dependency sections in:
  - Story 0039
  - Story 0040

## Vault Outcome

* Vault consulted during Repository Analysis: No
* Outcome: no vault action
* Rationale: this Story formalizes repository-local policy and sequencing; no
  new curated transverse-memory proposal is required yet.

## Validation

Performed:

* consistency review against ADR-006
* consistency review against ADR-049
* consistency review against ADR-050
* follow-up Story dependency alignment check
* repository diff formatting check

Not required for this documentation-only slice:

* backend test execution
* AI-engine test execution

Reason:

* no Java, Python, prompt, schema, database, or frontend behavior was changed.
