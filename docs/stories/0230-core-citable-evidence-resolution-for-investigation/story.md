# Story 0230 - Core Citable Evidence Resolution for Story Context Investigation

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING**

This Story is a follow-up to the technical failure observed during the
Story 0223 bounded live pilot. It defines a narrowly scoped Core correction for
evidence materialization during Story Context investigation. It does not
authorize implementation, provider calls, live evaluation, commits, pushes, or
merges.

Story 0223 remains unchanged and its completed live authorization cannot be
reused.

## Epic

- [Epic - Fiabiliser les réponses DevLog](../../epics/agent-response-reliability/epic.md)

## Problem

The bounded Story Context investigation path can receive an authorized
repository evidence item for the requested Story while that item has no
materialized content and no resolved revision available to the investigation
capability.

When the analyst requests bounded evidence, Core then cannot satisfy the
required path-and-revision match. The action returns `EMPTY`, even though the
authorized Story evidence exists in the Core context. After three such actions,
the investigation correctly terminates with
`INVESTIGATION_INCOMPLETE_NO_EVIDENCE`.

This is a Core evidence-resolution defect, not a provider-quality result. The
absence of citable evidence must remain visible, but an already authorized
and resolvable Story document should be made usable by the bounded investigation
contract.

## Goal

Ensure that a Core-authorized Story document can be resolved into bounded,
revision-pinned, citable evidence for the existing Story Context investigation
actions, while preserving project scope, source identity, trust classification,
budgets, action authorization, and the Python/Core boundary.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0184 - Bounded Story Context Investigation and Targeted Retrieval
- Story 0219 - Freshness, Scope and Provenance Explicitness
- Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot
- Story 0227 - DevLog Context Availability and Bounded Resume
- `docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json`

## Dependencies

- The existing Core investigation action contracts and capability registry.
- The existing `RepositoryContextAdapter` and project/source revision model.
- The existing revision-pinned repository document or Story evidence
  collection path.
- The existing evidence trust and citability classification.
- The existing Story 0184 investigation completion guard.

## In Scope

- Identify why an authorized Story evidence item reaches investigation without
  content or a resolvable source revision.
- Define one deterministic Core resolution path for an already authorized Story
  document at the execution's existing project and source revision.
- Materialize only the bounded content required by a valid
  `READ_EVIDENCE_RANGE` action.
- Preserve the canonical evidence reference, repository-relative path,
  provenance, trust tier, source identity, and resolved revision.
- Ensure the investigation action returns a citable result when the authorized
  document exists at the pinned revision.
- Return a deterministic, explicit unavailable or revision-mismatch outcome
  when the document cannot be resolved; never substitute an unverified current
  document.
- Preserve existing action, path, locator, byte, token, call, and elapsed-time
  limits.
- Add focused Core tests for successful resolution, missing content, missing
  revision, revision mismatch, out-of-scope paths, bounded ranges, and
  unavailable source data.
- Add an end-to-end or contract-level regression proving that a valid Story
  evidence item can become citable investigation evidence without changing the
  investigation accounting contract.

## Out of Scope

- Changes to Story 0223, its oracle, benchmark, pricing, identities, or live
  campaign authorization.
- Any new provider call, live campaign, or automatic retry.
- Broad retrieval, ranking, selection, context-budget, or semantic-search
  changes.
- New repository access for Python, the AI Engine, or MCP clients.
- RAG, embeddings, vector search, generic agent capabilities, or unrestricted
  file access.
- Relaxing revision, project, source, trust, grounding, citability, or scope
  validation.
- Increasing investigation budgets or changing action types.
- Treating an unavailable or stale document as current.
- Promoting investigation output to trusted knowledge.
- Prompt tuning or changes to provider/model policy.

## Architectural Constraints

1. Java/Core remains authoritative for project scope, source identity, revision,
   evidence resolution, trust, citability, action authorization, budgets, and
   terminal acceptance.
2. Python receives only Core-authorized immutable evidence and remains unable to
   access the repository, database, filesystem, or retrieval index.
3. Resolution must use the execution's authorized source and revision; it must
   fail closed when either cannot be established.
4. A canonical evidence reference must not be reconstructed from model output or
   from an unverified path.
5. Bounded content materialization must obey the existing action and evidence
   limits.
6. Missing or unusable evidence remains distinguishable from provider failure,
   malformed provider output, and semantic incorrectness.
7. Existing investigation accounting remains authoritative: a rejected or
   unavailable action is recorded once, and terminal callback accounting is not
   duplicated.
8. The change must not silently alter the one-shot Story Context path or other
   context consumers.

## Invariants

1. An evidence item is citable only when its reference, content, source, and
   revision are all valid for the authorized execution.
2. A valid authorized Story document at the pinned revision is not reported as
   `EMPTY` solely because its initial context projection omitted content.
3. A missing or unresolved revision never falls back to the current repository
   revision.
4. An out-of-scope path or locator is rejected deterministically.
5. Investigation success requiring evidence always contains at least one valid,
   revision-pinned, citable evidence result.
6. No evidence materialization broadens the authorized grounding set.
7. No new live execution is permitted merely because the focused tests pass.

## Acceptance Criteria

### AC-1 - Root-cause and contract definition

The implementation identifies and documents the existing Core seam responsible
for resolving authorized Story evidence for investigation. The contract states
which project, source, revision, path, locator, content, provenance, trust, and
citability fields are required for a successful result.

### AC-2 - Valid bounded resolution

For an authorized Story document that exists at the execution's source and
revision, a valid bounded read action returns content with its canonical
evidence reference, bounded locator, provenance, trust tier, source identity,
and resolved revision.

### AC-3 - Fail-closed resolution

When source, revision, path, locator, or document content cannot be resolved,
Core returns a deterministic unavailable, mismatch, or rejected outcome. It
never reads an unverified current document and never marks the action
successful.

### AC-4 - Scope and budget preservation

The correction does not permit paths, locators, byte ranges, token sizes,
operation counts, or elapsed time outside the existing investigation budgets.
Existing out-of-scope and malformed-action tests remain green.

### AC-5 - Investigation completion behavior

When valid evidence is available, the investigation can complete with a citable
evidence result. When it is unavailable, the investigation remains explicitly
incomplete and exposes bounded diagnostics rather than claiming semantic
success.

### AC-6 - Accounting and identity preservation

Successful, empty, rejected, and unavailable actions are represented exactly
once in interaction traces and provider accounting. Existing callback terminal
accounting remains unchanged.

### AC-7 - Regression coverage

Focused tests cover:

- authorized Story document resolution;
- bounded range content and locator preservation;
- missing content;
- missing or mismatched revision;
- missing source;
- out-of-scope path and malformed locator;
- budget exhaustion;
- duplicate evidence;
- investigation completion with and without citable evidence.

### AC-8 - Provider-free validation

The focused Core and contract tests pass, followed by the applicable backend,
MCP, and AI Engine validation. A provider-free evaluation confirms that no
network or provider call is made by the validation.

### AC-9 - Live reauthorization gate

Any future live C validation uses a newly prepared plan, fresh binding,
revision-specific preflight, and new human authorization. The authorization
from Story 0223 is not valid for this Story.

## Required Validation

- Repository Analysis approved by a human.
- Implementation Plan approved by a human.
- Focused investigation capability and adapter tests.
- Focused Story Context investigation use-case and controller tests.
- Relevant backend/MCP/AI Engine integration tests.
- `git diff --check`.
- Provider-free `DRY_RUN` and `INTEGRATION_SIMULATION`.
- Final live preflight only after the implementation and provider-free
  validation gates pass.
- At most one newly authorized live C validation before any broader campaign.

## Workflow and Gates

1. Human approves this Story for Repository Analysis.
2. Repository Analysis identifies the exact evidence-resolution seam and any
   conflict with accepted ADRs or existing Story contracts.
3. Human approves the Repository Analysis.
4. Implementation Plan defines the minimal Core correction and test matrix.
5. Human approves the Implementation Plan and explicitly authorizes
   implementation.
6. Implement and run focused validation.
7. Run independent review and complete applicable quality validation.
8. Prepare a new live plan only if the provider-free gates pass.
9. Obtain separate human authorization before any live provider call.
10. Human accepts or rejects the Story; the agent does not self-accept it.

## Human Decisions Required

1. Approve creation and Repository Analysis of Story 0230.
2. Confirm that the intended correction may materialize content for already
   authorized Story evidence, without changing general retrieval policy.
3. Confirm whether one live C validation is required for Story acceptance or is
   a separately authorized follow-up.
4. Confirm the source/revision behavior when the project snapshot has an
   authorized evidence reference but no materialized document content.

## Initial Repository Analysis Questions

- Which Core component owns the authoritative source and revision for Story
  documents during an investigation?
- Why does the selected Story evidence have a canonical reference but no
  content or resolved revision in the investigation snapshot?
- Can the existing repository/document adapter resolve the bounded range without
  a new retrieval architecture or context projection change?
- Which evidence provenance and trust fields must be copied into the result?
- Which existing tests define `EMPTY`, unavailable, revision mismatch, and
  citable-result semantics?
- Does the correction affect only investigation actions, or does it alter a
  shared adapter used by the one-shot path?
