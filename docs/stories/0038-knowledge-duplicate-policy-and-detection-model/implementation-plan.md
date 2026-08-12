# Story 0038 — Knowledge Duplicate Policy And Detection Model — Implementation Plan

## Overview

Implement the duplicate-policy decision as a documentation-first engineering
slice.

This Story should not attempt operational enforcement yet.

Its purpose is to:

1. define the duplicate semantics precisely;
2. decide where duplicate prevention responsibility belongs;
3. establish the initial enforcement posture for later Stories;
4. create a stable architectural baseline for implementation and remediation.

The preferred outcome is a clear policy that distinguishes:

* acceptable duplicate-like proposal history;
* unacceptable redundant trusted knowledge;
* exact duplicates vs semantic near-duplicates;
* prevention vs safeguard vs remediation responsibilities.

## Planned Changes

### 1. Create a dedicated ADR for duplicate policy

Add:

* `docs/decisions/ADR-051.md`

Implementation intent:

* use the next available ADR number and the repository ADR template exactly;
* align explicitly with:
  - ADR-006 for proposal lifecycle authority;
  - ADR-049 for semantic preservation of promoted knowledge;
  - ADR-050 for incremental knowledge evolution and no-redundant-delta intent;
* define duplicate semantics at architecture level before any code enforcement;
* record explicit non-goals so the Story does not drift into remediation or
  semantic-search redesign.

### 2. Formalize duplicate categories across the knowledge lifecycle

Update likely artifacts:

* `docs/decisions/ADR-051.md`
* `docs/stories/0038-knowledge-duplicate-policy-and-detection-model/implementation-report.md`

Implementation intent:

* distinguish at least:
  - exact duplicate proposal;
  - repeated proposal with legitimate distinct lifecycle history;
  - exact trusted-knowledge duplicate;
  - semantic near-duplicate trusted knowledge;
  - legitimate enrichment;
  - historically distinct successor / replacement candidate;
* define which categories are acceptable in proposal history;
* define which categories are not acceptable as steady-state trusted
  knowledge.

Preferred policy direction:

* proposal-history repetition can be acceptable under ADR-006;
* trusted-knowledge duplication should be minimized and treated as a defect
  when no lifecycle distinction exists.

### 3. Decide the control split between upstream, downstream, and human review

Update likely artifacts:

* `docs/decisions/ADR-051.md`
* Story 0038 implementation artifacts

Implementation intent:

* state that upstream prevention during analysis is the primary line of
  defense;
* state that downstream validation / promotion safeguards are still required as
  a protection boundary;
* state that the human reviewer resolves ambiguous overlap rather than serving
  as the primary duplicate detector;
* make the policy explicit enough to drive Story 0039 scope.

### 4. Define the minimum comparison model for later implementation

Update likely artifacts:

* `docs/decisions/ADR-051.md`
* Story 0038 implementation artifacts

Implementation intent:

* define the minimum signals later code may use, such as:
  - project scope;
  - proposal family / trusted knowledge family;
  - normalized knowledge type;
  - `sourceType`;
  - enrichment target identity;
  - title / summary / rationale similarity;
  - evidence overlap;
  - accepted relation context when applicable;
* keep the model deterministic and implementation-guiding;
* avoid prematurely locking the repository into embeddings or heavy semantic
  infrastructure.

### 5. Define the first enforcement posture for later code stories

Update likely artifacts:

* `docs/decisions/ADR-051.md`
* Story 0038 implementation artifacts

Implementation intent:

* recommend a graduated posture, for example:
  - hard-block obvious exact trusted duplicates;
  - warn or soft-block strong semantic near-duplicates;
  - escalate ambiguous cases to human review;
* explicitly state whether repeated equivalent analyses should prefer:
  - no proposal;
  - `ENRICHES`;
  - or rejection before promotion;
* define the intended boundary that Story 0039 should implement first.

### 6. Align the follow-up Story sequence with the approved policy

Update likely artifacts:

* `docs/stories/0039-prevent-redundant-trusted-knowledge-during-analysis-and-validation/story.md`
* `docs/stories/0040-audit-and-remediate-existing-trusted-knowledge-duplicates/story.md`
* Story 0038 implementation artifacts

Implementation intent:

* confirm Story 0039 remains the behavior/enforcement slice;
* confirm Story 0040 remains the audit/remediation slice;
* adjust wording only if Story 0038 decisions reveal a mismatch between policy
  and planned sequencing;
* avoid broad renumbering or restructuring unless the policy makes it
  necessary.

### 7. Keep implementation scope documentation-only unless analysis reveals a
purely local fix

Implementation intent:

* do not modify Java or AI-engine production code as part of Story 0038 unless
  a tiny documentation-coupled fix is unavoidably required for consistency;
* treat this Story as the governance and policy baseline for later coding
  Stories;
* preserve repository quality expectations even though the main changes are
  documentation artifacts.

## Validation Plan

1. Verify the new ADR is consistent with:
   * ADR-006;
   * ADR-049;
   * ADR-050.
2. Verify Story 0038 acceptance criteria are explicitly satisfied by the final
   artifacts.
3. Verify Stories 0039 and 0040 still reflect the approved policy direction.
4. Run targeted repository checks needed for documentation consistency.
5. Run broader quality gates only if repository conventions or changed files
   require them for this documentation-only slice.

## Risks And Controls

### Risk 1: Policy too vague

If the ADR stays abstract, Story 0039 will invent its own semantics.

Control:

* define categories, acceptable states, and enforcement posture explicitly.

### Risk 2: Policy too rigid

If the ADR overcommits on semantic matching details, later implementation may
be forced into a poor technical design.

Control:

* define minimum comparison signals and behavior goals, not a premature heavy
  algorithm.

### Risk 3: Mixing proposal history and trusted knowledge rules

If those are conflated, the repository may accidentally violate ADR-006.

Control:

* keep immutable proposal history and trusted-knowledge hygiene as separate
  policy domains.

### Risk 4: Remediation scope bleeding into the policy Story

If Story 0038 starts solving existing DB debt directly, the workflow will lose
clarity.

Control:

* keep remediation in Story 0040 and limit Story 0038 to governance decisions.

## Expected Deliverables

* `docs/decisions/ADR-051.md`
* `docs/stories/0038-knowledge-duplicate-policy-and-detection-model/implementation-report.md`
* `docs/stories/0038-knowledge-duplicate-policy-and-detection-model/code-review.md`
* `docs/stories/0038-knowledge-duplicate-policy-and-detection-model/engineering-report.md`

Possible wording-only adjustments:

* Story 0039
* Story 0040
