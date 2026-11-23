# Story 0232 - Q1 Live Result and Revision Integrity

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING**

This Story is a corrective follow-up to the provider-backed Q1 validation. It
addresses two independent technical failures observed after Story 0231:

1. the live MCP adapter receives a completed Core task without a readable
   analysis result;
2. the C investigation completes without citable evidence while Core reports a
   source revision different from the authorized runtime/source binding.

Story 0223 and Story 0231 remain unchanged. No previous live authorization may
be reused.

This Story authorizes implementation and provider-free validation only. It does
not authorize provider calls, live evaluation, commits, pushes, merges, or Story
acceptance.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0184 - Bounded Story Context Investigation and Targeted Retrieval
- Story 0219 - Freshness, Scope and Provenance Explicitness
- Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot
- Story 0231 - Core Search Evidence Resolution for Story Context Investigation
- Q1 live artifacts under `data/devlog-ai-q1-live-20261123-post0231-final/`

## Problem

The post-0231 Q1 live campaign produced:

- A: `status=COMPLETED` with `aiTaskId` and `snapshotId`, but no readable
  result in the MCP response;
- C: `INVESTIGATION_INCOMPLETE_NO_EVIDENCE` after three `EMPTY` action results;
- C freshness data resolved the source to `60ddd715...`, while the authorized
  runtime was `5d4eec3d...` and the frozen oracle source revision was
  `fa33f03d...`.

The integrity report for all captured artifacts passed. The failures are
therefore runtime contract and revision-integrity concerns, not artifact
corruption.

## Goals

- Make a completed MCP Story Agent execution readable when Core has persisted a
  valid result for its task identity.
- Preserve fail-closed behavior when the result is absent, unauthorized,
  malformed, or unavailable.
- Detect and classify incompatible source/runtime/revision bindings before C
  investigation can produce an ungrounded result.
- Ensure authorized Story searches continue to use the shared Core evidence
  resolution introduced by Story 0231.
- Preserve all identity, scope, freshness, grounding, citability, accounting,
  budget, and one-shot invariants.

## In Scope

- The Q1 live Core adapter and its bounded MCP result-reading contract.
- The authenticated Core result/snapshot read path needed by the adapter.
- Revision-binding validation relevant to the C live investigation.
- Diagnostics and status classification for missing result and revision
  mismatch.
- Focused unit, integration, and provider-free evaluation regression tests.
- Documentation of the exact live gate that remains after provider-free
  validation.

## Out of Scope

- Any modification to Story 0223, its oracle, manifest, pricing, identities,
  or live authorization.
- Any modification to Story 0231's accepted evidence-resolution behavior.
- Provider calls, live evaluation, automatic retries, or new live authorization.
- Replacing the Core context or evidence-resolution architecture.
- Relaxing source, revision, project, path, trust, citability, or grounding
  checks.
- Changing model, prompt, semantic ranking, budget, or provider policy.
- Promoting AI output to trusted knowledge.

## Architectural Constraints

1. Java/Core remains authoritative for task status, result authorization,
   project/source/revision identity, evidence resolution, and terminal
   acceptance.
2. Python and the evaluation runner never reconstruct Core context or
   substitute the current repository revision.
3. A completed task without a readable authorized result remains a bounded
   technical failure.
4. A source or revision mismatch fails closed and is reported explicitly.
5. No retry may create a second provider execution or consume an ambiguous
   authorization.
6. Existing accounting and identity fields remain authoritative and are
   recorded exactly once.

## Acceptance Criteria

### AC-1 - Completed result retrieval

When Core reports a completed Story Agent task and its authorized result
endpoint returns a valid result, the live adapter exposes that result as the
terminal answer.

### AC-2 - Missing result remains fail-closed

When a completed task has no readable result, the adapter returns a bounded
technical failure with the task identity and diagnostic classification, without
inventing an answer or retrying the provider.

### AC-3 - Result authorization and contract integrity

Unauthorized, malformed, or contract-incompatible result responses are rejected
without exposing sensitive response content.

### AC-4 - Revision binding

The C path validates the relationship between authorized plan/runtime revision,
Core source revision, and requested evidence revision. An incompatible binding
produces an explicit fail-closed diagnostic before an apparently successful
investigation result is accepted.

### AC-5 - Evidence preservation

When the binding is compatible, C preserves the canonical Story reference,
resolved revision, source identity, citability, grounding references, and
existing investigation accounting.

### AC-6 - Empty investigation diagnostics

Repeated `EMPTY` action results remain distinguishable from unavailable,
rejected, and successful results. The terminal diagnostic identifies the
bounded evidence failure without converting it into a semantic success.

### AC-7 - Regression coverage

Tests cover:

- completed MCP execution with a readable result;
- completed execution without a result;
- unauthorized and malformed result responses;
- source/runtime revision mismatch;
- compatible revision and citable Story evidence;
- three empty investigation actions;
- accounting, identity, and no-retry behavior.

### AC-8 - Provider-free validation

Focused tests, applicable backend tests, `DRY_RUN`, and
`INTEGRATION_SIMULATION` pass with zero external provider and network calls.

### AC-9 - Live gate

Any future live validation requires a fresh revision-specific plan, binding,
preflight, and explicit human authorization. No authorization consumed during
the failed post-0231 campaign is reusable.

## Required Validation

- Focused Q1 live adapter tests.
- Focused Core result/revision tests.
- `StoryContextCoreIntegrationTest` investigation regression.
- Applicable backend and AI Engine tests.
- `git diff --check`.
- Provider-free `DRY_RUN`.
- Provider-free `INTEGRATION_SIMULATION`.
- Independent review before any live planning.

## Workflow and Gates

1. Implement only within this Story.
2. Run focused and provider-free validation.
3. Review the complete diff and artifacts.
4. Stop for human validation before any new live preparation or authorization.
