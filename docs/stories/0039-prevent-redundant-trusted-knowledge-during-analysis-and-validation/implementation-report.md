# Story 0039 — Prevent Redundant Trusted Knowledge During Analysis And Validation — Implementation Report

## Status

Implemented

## Summary

Implemented the first operational trusted-duplicate prevention slice for
accepted `INSIGHT` proposals.

The chosen boundary preserves ADR-006 proposal history while protecting trusted
knowledge before promotion:

* AI callback handling still persists valid proposals unchanged;
* duplicate prevention now runs at acceptance time;
* exact trusted duplicates are blocked before validation / promotion completes;
* legitimate `ENRICHES` proposals still pass when they add materially new
  content.

This Story intentionally does **not** attempt broad semantic deduplication.

## Changes

### 1. Added a shared insight payload helper

Added:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPayloadSupport.java`

Purpose:

* centralize payload parsing shared between duplicate guarding and insight
  promotion;
* avoid mapping drift between:
  - candidate duplicate detection;
  - final trusted `Insight` construction.

The helper now owns:

* required / optional text extraction;
* UUID extraction;
* proposal `insightType` → trusted `InsightType` mapping;
* conservative string normalization used for exact duplicate comparison.

### 2. Added a dedicated trusted duplicate guard

Added:

* `backend/src/main/java/com/hopeful117/devlogai/validation/service/TrustedKnowledgeDuplicateGuard.java`

Behavior:

* applies only to `ProposalType.INSIGHT`;
* loads trusted insights from the same project;
* computes an exact-match fingerprint from the candidate proposal using:
  - trusted domain type;
  - `sourceType`;
  - normalized title;
  - normalized summary / content;
  - normalized rationale;
* raises `ConflictException` when an accepted proposal would create redundant
  trusted knowledge.

This is the first downstream safety net required by ADR-051.

### 3. Integrated duplicate enforcement into the validation boundary

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/validation/service/ValidationServiceImpl.java`

Behavior:

* the duplicate guard now runs only for `ACCEPTED` proposals;
* if a duplicate conflict is detected:
  - no `Validation` is saved;
  - no proposal status transition is persisted;
  - no trusted knowledge is promoted;
  - the proposal remains `PROPOSED`.

This keeps the failure as a business conflict rather than a low-level promotion
error.

### 4. Preserved promotion behavior for non-duplicate insights

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPromotionService.java`

Changes:

* promotion now reuses `InsightPayloadSupport`;
* enrichment relation behavior is unchanged.

Outcome:

* valid `NEW` insights still promote normally;
* valid `ENRICHES` insights still promote and create `DERIVED_FROM` traceability
  as before.

## Behavioral Outcome

### Now prevented

* exact duplicate accepted `NEW` trusted insights in the same project
* exact restatement `ENRICHES` proposals that would create a redundant trusted
  `Insight`

### Still allowed

* repeated proposal history before acceptance
* legitimate `ENRICHES` proposals with materially new content
* non-insight proposal lifecycle

### Explicitly deferred

* broad semantic near-duplicate enforcement
* contradiction / supersession lifecycle
* cleanup of existing duplicate stock

## Documentation Outcome

Documentation update: Not required.

Reason:

* repository behavior changed only in backend validation / promotion flow;
* the governing architectural policy was already captured by ADR-051 in Story
  0038;
* no additional canonical repository document required update for this narrow
  implementation slice.

## Vault Outcome

* Vault consulted during Repository Analysis: No
* Outcome: no vault action
* Rationale: this Story implements a repository-local backend guard and does
  not produce a new transverse-memory candidate on its own.

## Validation

Performed:

* targeted backend tests for duplicate guard and validation behavior
* full backend `./mvnw verify`
* repository diff formatting check

Results:

* backend targeted tests: pass
* backend `./mvnw verify`: pass
* JaCoCo coverage checks: pass
* `git diff --check`: pass

Not required:

* AI-engine pytest

Reason:

* no AI-engine code, prompt, schema, or contract file changed in this Story.
