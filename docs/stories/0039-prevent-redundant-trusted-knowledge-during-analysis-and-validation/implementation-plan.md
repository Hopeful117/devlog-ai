# Story 0039 — Prevent Redundant Trusted Knowledge During Analysis And Validation — Implementation Plan

## Overview

Implement the first operational duplicate-prevention slice for trusted
knowledge without weakening ADR-006 proposal-history guarantees.

The preferred design is:

1. preserve proposal persistence in `AiTaskResultServiceImpl`;
2. add a dedicated business-level trusted duplicate guard before promotion;
3. hard-block obvious exact trusted duplicates in V1;
4. keep semantic near-duplicate handling narrow, deterministic, and
   test-driven;
5. preserve legitimate `ENRICHES` behavior introduced by Story 0037.

This Story should protect the trusted layer, not erase lifecycle history.

## Planned Changes

### 1. Add a dedicated trusted duplicate guard service

Add likely component:

* `backend/src/main/java/com/hopeful117/devlogai/validation/service/TrustedKnowledgeDuplicateGuard.java`
* or an equivalent focused service in the same layer

Implementation intent:

* keep duplicate-policy logic out of `ValidationServiceImpl` itself;
* encapsulate the “would this accepted proposal create forbidden redundant
  trusted knowledge?” check behind a dedicated service;
* scope the first implementation to `ProposalType.INSIGHT`;
* make the service return a deterministic allow / conflict outcome rather than
  an ambiguous heuristic score object.

Preferred behavior:

* non-insight proposals bypass the guard;
* insight proposals are checked only at acceptance time;
* rejected proposals remain unaffected.

### 2. Invoke the guard from `ValidationServiceImpl` before promotion

Update likely component:

* `backend/src/main/java/com/hopeful117/devlogai/validation/service/ValidationServiceImpl.java`

Implementation intent:

* run the duplicate guard after the proposal is loaded and before final
  acceptance / promotion completes;
* surface duplicate policy violations as a clear business conflict;
* avoid allowing the failure to emerge later as a low-level promotion
  exception;
* preserve the existing transaction boundary and accepted / rejected semantics.

Preferred behavior:

* `ACCEPTED` duplicate insight proposals fail with `ConflictException`;
* no `Validation` is saved;
* trusted knowledge is not mutated;
* the proposal remains in `PROPOSED` state for explicit follow-up handling.

### 3. Implement exact duplicate detection first

Update likely components:

* new guard service
* `InsightRepository` query surface if needed

Implementation intent:

* compare against trusted insights from the same project only;
* normalize the candidate proposal into the same semantic dimensions used by
  trusted `Insight` persistence;
* hard-block only when the duplicate condition is strong and deterministic.

Minimum comparison dimensions for V1:

* project ID
* trusted proposal family = `INSIGHT`
* normalized `insightType` / trusted domain type
* `sourceType`
* normalized title
* normalized summary / content
* normalized rationale

Normalization should be conservative:

* trim surrounding whitespace;
* compare case-insensitively where appropriate;
* avoid aggressive semantic rewriting in V1.

### 4. Preserve legitimate `ENRICHES` behavior

Update likely components:

* duplicate guard service
* `InsightPromotionService` tests
* `AiProposalContractValidator` tests only if required

Implementation intent:

* an `ENRICHES` proposal targeting an existing trusted insight must not be
  blocked merely because it references the same topic;
* only restatements that collapse to the same trusted semantic payload should
  be rejected;
* genuine extensions of target content must still pass and produce trusted
  evolution plus relation traceability.

Preferred rule:

* `ENRICHES` with materially new summary or rationale is allowed;
* `ENRICHES` that merely restates the target insight content is blocked.

### 5. Keep AI callback and proposal-history behavior unchanged

Update likely components:

* no production change expected in `AiTaskResultServiceImpl`
* tests may be updated only if regression coverage is useful

Implementation intent:

* do not suppress valid proposals at callback persistence time as the primary
  mechanism;
* preserve repeated proposal history for ADR-006 traceability;
* keep duplicate trusted-knowledge prevention focused on trusted acceptance,
  not on historical proposal existence.

### 6. Add only narrow upstream adjustments if they are cheap and safe

Possible updates:

* architecture prompt or contract only if analysis reveals a tiny targeted
  improvement that reduces duplicate `NEW` proposals without widening scope

Implementation intent:

* upstream changes are optional in Story 0039 unless they are small and
  clearly beneficial;
* the mandatory part of the Story is the downstream protection boundary;
* do not widen into a generic multi-intent prompt redesign.

### 7. Extend repository and test support as needed

Likely updates:

* `InsightRepository` with a query better suited to project-scoped duplicate
  checks
* backend test fixtures for accepted trusted insights and candidate proposals

Implementation intent:

* prefer a small deterministic repository read path over large ad hoc scans in
  unrelated services;
* keep query semantics project-scoped and straightforward;
* avoid introducing database uniqueness constraints for semantic duplication in
  this Story.

## Validation Plan

### Backend unit tests

Update or add:

* `ValidationServiceTest`
* `InsightPromotionServiceTest`
* a dedicated `TrustedKnowledgeDuplicateGuardTest`
* optionally integration-style service tests around acceptance flow

Minimum scenarios:

1. accepting an exact duplicate `NEW` insight is rejected
2. accepting a non-duplicate `NEW` insight succeeds
3. accepting a legitimate `ENRICHES` insight succeeds
4. accepting a restatement `ENRICHES` insight is rejected
5. different project trusted knowledge does not leak into duplicate detection
6. rejected proposals do not trigger duplicate guard failures

### Existing lifecycle regression

Verify:

* proposal history still persists through AI callback handling
* non-insight proposal acceptance remains unchanged
* accepted enrichments still create the expected trusted relation trace

### Quality gates

Run unchanged:

* backend `./mvnw verify`

Run AI-engine tests only if the implementation actually touches AI-engine code.

## Risks And Controls

### Risk 1: Blocking legitimate enrichments

If the duplicate guard only compares topic similarity, valid `ENRICHES` could
be rejected.

Control:

* include target-aware comparison and explicit enrichment tests.

### Risk 2: Preserving too many exact duplicates

If the comparison is too strict syntactically, obvious duplicate trusted writes
may still pass.

Control:

* normalize title / summary / rationale consistently and test exact duplicate
  cases explicitly.

### Risk 3: Wrong failure boundary

If duplicate checks remain inside promotion only, the system will expose
technical errors instead of business conflicts.

Control:

* perform the main check from `ValidationServiceImpl` before promotion.

### Risk 4: Scope explosion into full semantic deduplication

If the Story tries to solve all near-duplicate reasoning at once, delivery risk
will spike.

Control:

* hard-block exact duplicates first;
* keep near-duplicate handling narrow and evidence-based.

## Expected Deliverables

* production code for trusted duplicate guarding
* updated backend tests covering duplicate acceptance conflicts
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`

Possible but not mandatory:

* small `InsightRepository` query additions
* narrow upstream architecture duplicate-bias improvement if clearly justified
