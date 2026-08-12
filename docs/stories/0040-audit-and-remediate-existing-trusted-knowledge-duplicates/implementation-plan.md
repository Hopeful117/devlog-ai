# Story 0040 — Audit And Remediate Existing Trusted Knowledge Duplicates — Implementation Plan

## Overview

Implement Story 0040 as a read-first remediation slice.

The primary goal is to make the current duplicate debt visible and classifiable
before any destructive or archival mutation is considered.

The preferred design is:

1. scan trusted insights project by project;
2. group duplicate candidates deterministically;
3. classify candidate clusters into policy-relevant categories;
4. emit a reviewable artifact for human inspection;
5. avoid write-side remediation in the first slice unless the scan itself
   proves insufficient and an additional mutation step is explicitly approved.

This Story should improve observability and decision quality first.

## Planned Changes

### 1. Add a dedicated audit service for trusted duplicate scanning

Add likely component:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/TrustedKnowledgeDuplicateAuditService.java`
* or an equivalent focused read-side service

Implementation intent:

* keep duplicate-stock auditing separate from Story 0039’s acceptance guard;
* operate on persisted trusted insights only;
* produce a deterministic cluster-oriented result rather than an unstructured
  list.

Preferred output model:

* project-scoped audit result
* ordered clusters
* ordered cluster members
* per-cluster category
* per-cluster remediation recommendation

### 2. Reuse deterministic normalization from existing duplicate logic

Update likely components:

* the new audit service
* existing normalization helper(s) if safe to share

Implementation intent:

* reuse the same conservative normalization approach already introduced for
  Story 0039 where possible;
* avoid divergent duplicate semantics between:
  - future prevention;
  - historical audit.

Likely signals:

* trusted `InsightType`
* `sourceType`
* normalized title
* normalized content
* normalized rationale
* created-at ordering

### 3. Distinguish exact duplicates from likely richer-successor clusters

Implementation intent:

* classify at least:
  - exact duplicate
  - likely semantic duplicate
  - likely richer successor / enrichment currently represented as duplicate
  - ambiguous / review required
* make classification deterministic enough to be testable;
* keep “likely semantic duplicate” intentionally narrow in V1.

Preferred rule shape:

* exact duplicate:
  - identical normalized fingerprint
* likely richer successor:
  - same coarse topic / title family
  - later record is richer via provenance / rationale / fuller wording
* ambiguous:
  - similar but not confidently classifiable

### 4. Emit a reviewable artifact instead of mutating data

Add likely outputs:

* repository-local JSON artifact under the Story directory
* optionally a small human-readable markdown summary if it improves review

Preferred authoritative artifact:

* `duplicate-audit.json`

Expected content:

* project ID
* scan timestamp
* clusters
* member insight IDs
* classification
* recommended action
* rationale

Implementation intent:

* the artifact should be deterministic and easy to diff;
* it should support later manual or automated remediation decisions;
* it must not itself modify trusted knowledge.

### 5. Keep remediation recommendation explicit but proposal-only

Implementation intent:

* recommend actions such as:
  - keep newest as canonical candidate
  - review manually
  - candidate archive
  - candidate no-op
* do not perform deletion or archival automatically in this first slice;
* if the audit suggests a later mutation workflow, describe it in the
  implementation report rather than silently implementing it.

### 6. Add a narrow repository/API surface only if needed

Possible updates:

* small repository query refinements for ordered insight retrieval
* a read-only endpoint if local artifact generation alone is not sufficient

Implementation intent:

* prefer reusing existing `InsightRepository` project-scoped reads first;
* do not introduce a broad admin remediation API unless the implementation
  genuinely needs one;
* keep the first delivery lightweight and auditable.

### 7. Preserve future remediation extensibility

Implementation intent:

* structure the audit result so a later Story can:
  - review candidates
  - approve remediations
  - apply traceable archive / relation / merge behavior
* avoid prematurely choosing a destructive storage model now.

## Validation Plan

### Backend unit tests

Add likely tests:

* empty trusted knowledge yields no clusters
* exact duplicate insights cluster deterministically
* richer successor cluster is identified deterministically
* different project insights never cluster together
* ambiguous cases remain classified as review-required
* artifact generation does not mutate trusted knowledge

### Integration / repository checks

If an artifact is generated from live data in-repo:

* validate deterministic ordering
* validate stable serialization

### Quality gates

Run unchanged:

* backend `./mvnw verify`

Run additional tooling only if the implementation actually adds a runtime
endpoint or non-backend code.

## Risks And Controls

### Risk 1: Overconfident semantic classification

If the classifier is too ambitious, it may produce misleading cleanup guidance.

Control:

* keep exact-duplicate logic strict;
* keep semantic categories narrow and explicit;
* use `review required` generously where confidence is lower.

### Risk 2: Audit output too weak to be actionable

If the artifact only lists raw insights with no grouping or recommendation, it
will not support real remediation.

Control:

* emit cluster-based output with category and recommended next action.

### Risk 3: Silent mutation sneaks into an audit Story

If the implementation mutates trusted knowledge while building the report, the
Story breaks its safety boundary.

Control:

* keep the first slice read-only by design;
* test for no mutation side effects.

### Risk 4: Drift from Story 0039 duplicate semantics

If historical audit and future prevention use different normalization logic,
the ecosystem becomes inconsistent.

Control:

* reuse shared normalization where feasible.

## Expected Deliverables

* production backend audit service
* deterministic duplicate-audit artifact generation
* backend tests covering clustering and classification
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`

Possible but not mandatory:

* a small read-only endpoint for audit consumption
* a markdown summary derived from the JSON artifact
