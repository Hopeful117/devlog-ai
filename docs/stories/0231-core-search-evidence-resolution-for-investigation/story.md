# Story 0231 - Core Search Evidence Resolution for Story Context Investigation

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING**

This Story is a narrowly scoped corrective follow-up to Story 0230. It addresses
the provider-free Q1 C integration failure in which `SEARCH_EVIDENCE` receives
an authorized Story evidence item without a resolved revision.

Story 0223 remains unchanged. Story 0230 remains unchanged. No previous live
authorization may be reused.

This Story authorizes implementation only within the scope below. It does not
authorize provider calls, live evaluation, commits, pushes, merges, or Story
acceptance.

## Epic

- [Epic - Fiabiliser les réponses DevLog](../../epics/agent-response-reliability/epic.md)

## Problem

The Core investigation capability resolves an authorized Story document only
when processing `READ_EVIDENCE_RANGE`. A `SEARCH_EVIDENCE` action validates the
projected evidence item directly. When that item has a canonical Story
reference but no materialized content or `resolvedRevision`, Core returns
`UNAVAILABLE` before the search result can be produced.

The failure is therefore caused by an incomplete Core resolution path, not by
provider output quality:

```text
authorized Story evidence
→ SEARCH_EVIDENCE
→ no document resolution
→ missing resolved revision
→ EVIDENCE_UNAVAILABLE
→ Python/Core investigation does not complete
```

The existing Story 0230 correction remains effective for bounded reads but does
not cover the shared identity and revision resolution required by searches.

## Goal

Ensure that every authorized Story document used by bounded investigation has a
validated source and revision before action-specific processing, while
materializing document content only when the action requires it.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0184 - Bounded Story Context Investigation and Targeted Retrieval
- Story 0219 - Freshness, Scope and Provenance Explicitness
- Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot
- Story 0230 - Core Citable Evidence Resolution for Story Context Investigation
- `docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json`

## Dependencies

- The existing Core investigation action contracts and capability registry.
- `InvestigationCapabilityExecutionService`.
- `EvidenceResolutionFacade` and `PinnedDocumentEvidenceResolver`.
- `RepositoryContextAdapter` and `RepositoryRevisionScope`.
- Story Context investigation accounting and callback validation.
- The provider-free Q1 Core integration harness.

## In Scope

- Resolve authorized Story evidence identity, source, path, and revision for
  both `SEARCH_EVIDENCE` and `READ_EVIDENCE_RANGE`.
- Reuse the existing Core evidence-resolution seam; do not introduce a new
  retrieval architecture.
- Preserve the canonical `story:<uuid>` reference exposed to the investigation
  consumer.
- For `SEARCH_EVIDENCE`, make the resolved revision available for matching
  without exposing unnecessary document content.
- For `READ_EVIDENCE_RANGE`, continue materializing only the bounded requested
  content.
- Preserve existing path, project, source, trust, citability, locator, byte,
  token, retrieval-call, step, and elapsed-time limits.
- Return deterministic `UNAVAILABLE` or revision-mismatch outcomes when the
  authorized source or document cannot be resolved.
- Add focused unit, use-case, accounting, and Core integration regression tests.
- Re-run provider-free `DRY_RUN` and `INTEGRATION_SIMULATION` after
  implementation.

## Out of Scope

- Any modification to Story 0223 or its oracle, benchmark, identities, pricing,
  revisions, or live authorization.
- Any modification to Story 0230's accepted behavior or unrelated evidence
  resolution paths.
- New provider calls, live campaigns, automatic retries, or new live
  authorization.
- Changes to Python repository access, prompt policy, model policy, MCP
  transport, ranking, selection, semantic search, RAG, embeddings, or budgets.
- Relaxing project, source, revision, grounding, trust, citability, or scope
  validation.
- Changing the one-shot Story Context path.
- Promoting investigation output to trusted knowledge.

## Architectural Constraints

1. Java/Core remains authoritative for project scope, source identity, revision,
   evidence resolution, trust, citability, action authorization, budgets, and
   terminal acceptance.
2. Python receives only Core-authorized immutable evidence and cannot access the
   repository, database, filesystem, or retrieval index.
3. Search and read actions may share identity and revision resolution, but
   content materialization remains action-specific.
4. The canonical evidence reference must come from Core-authorized evidence and
   must not be reconstructed from model output or an unverified path.
5. Missing or mismatched source/revision data fails closed and never falls back
   to the current repository revision.
6. Existing action accounting remains authoritative and each action is recorded
   exactly once.
7. The correction must not alter the one-shot Story Context path or broaden the
   authorized grounding set.

## Invariants

1. `SEARCH_EVIDENCE` and `READ_EVIDENCE_RANGE` validate the same authorized
   project/source/revision identity.
2. A valid authorized Story document is not unavailable solely because its
   initial projection omitted materialized content.
3. Search resolution never exposes unbounded document content.
4. Read resolution remains bounded by the existing action limits and locator.
5. Missing or mismatched source/revision data never falls back to current data.
6. Successful evidence results retain the canonical Story reference, provenance,
   trust classification, source identity, and resolved revision.
7. Unavailable, rejected, empty, and successful actions remain distinguishable
   and are accounted for exactly once.
8. No live execution is permitted merely because provider-free tests pass.

## Acceptance Criteria

### AC-1 - Shared resolution seam

The implementation identifies and reuses one deterministic Core seam for
authorized Story evidence identity and revision resolution across search and
read actions.

### AC-2 - Search resolution

For an authorized Story document with a valid source and pinned revision,
`SEARCH_EVIDENCE` returns a result that can pass the existing path and revision
contract without requiring initial projected content.

### AC-3 - Bounded read preservation

`READ_EVIDENCE_RANGE` continues to return only the requested bounded content,
with its canonical reference, locator, provenance, trust tier, source identity,
and resolved revision.

### AC-4 - Fail-closed behavior

Missing source, missing revision, mismatched revision, unavailable document,
out-of-scope path, or malformed locator produces the existing deterministic
unavailable, mismatch, or rejected outcome. No current unverified document is
substituted.

### AC-5 - Scope and budget preservation

Existing project/path authorization, action limits, byte/token budgets,
retrieval-call limits, step limits, and elapsed-time limits remain unchanged.

### AC-6 - Accounting and identity preservation

Search, read, unavailable, rejected, and duplicate actions are represented
exactly once in interaction traces, investigation snapshots, provider
accounting, and terminal callback handling.

### AC-7 - Regression coverage

Focused tests cover:

- authorized Story search without initially materialized content;
- bounded read after shared resolution;
- canonical Story reference preservation;
- missing source and missing revision;
- revision mismatch;
- unavailable document;
- out-of-scope path and malformed locator;
- budget exhaustion and duplicate action handling;
- investigation completion with citable evidence.

### AC-8 - Provider-free validation

The focused tests, applicable backend tests, and provider-free `DRY_RUN` and
`INTEGRATION_SIMULATION` pass with zero external provider and network calls.

### AC-9 - Live reauthorization gate

Any future live C validation uses a new revision-specific plan, binding,
preflight, and explicit human authorization. No authorization from Story 0223
or Story 0230 is reusable.

## Required Validation

- Focused investigation capability and adapter tests.
- Focused Story Context investigation use-case and controller tests.
- `StoryContextCoreIntegrationTest` C regression.
- Applicable backend validation.
- `git diff --check`.
- Provider-free `DRY_RUN`.
- Provider-free `INTEGRATION_SIMULATION`.
- Independent review before any live planning.
- At most one newly authorized live C validation before any broader campaign.

## Workflow and Gates

1. Implement only after the Repository Analysis and Implementation Plan have
   been explicitly approved.
2. Run focused and provider-free validation.
3. Perform independent review and applicable quality validation.
4. Prepare a new live plan only if all provider-free gates pass.
5. Obtain separate human authorization before any live provider call.
6. Human accepts or rejects this Story; the agent does not self-accept it.

## Human Decisions Required

1. Confirm the Story remains limited to shared Core identity/revision
   resolution for bounded investigation actions.
2. Confirm that no new live execution is authorized by this Story.
3. Decide separately whether a new live C validation is desired after all
   provider-free gates pass.

## Initial Repository Analysis Questions

- Where should shared Story evidence identity and revision resolution occur so
  search and read cannot diverge again?
- What evidence metadata must be retained when search does not need content?
- Which existing result and accounting contracts must remain byte-for-byte
  compatible?
- Does the Core integration fixture expose the same source and revision scope as
  the production path?
- Which assertions prove that no content or retrieval scope is broadened for
  `SEARCH_EVIDENCE`?
