# Repository Analysis - Story 0184

## Status

**ANALYSIS COMPLETE - AWAITING HUMAN APPROVAL**

This artifact analyzes the approved Story 0184 scope. It does not authorize
Implementation Planning, production changes, schema changes, or deployment.

## Analysis Context

### Story

Story 0184 proposes the transition from the current one-shot Story Context
provider to a bounded engineering analyst. The intended implementation order is
Core-authorized targeted retrieval first, followed by an optional bounded
investigation loop with explicit planning, evidence accounting, and final Core
validation.

### Repository state

- Branch: `main`
- HEAD: `d83600a`
- Worktree: clean before this analysis artifact
- Current production fix: PR #175 is merged and the branch has been cleaned up.

### DevLog context

No usable `devlog_devlog_story_agent` capability or exact repository mapping was
available in this session. Analysis therefore uses current repository evidence,
accepted ADRs, existing Stories, and the canonical investigation. No DevLog
context is presented as validated evidence.

### Vault context

The curated Obsidian vault was not consulted. The repository contains sufficient
current evidence to establish ownership, contracts, constraints, and the
implementation boundary for this Story.

## Story Understanding

The Story is not a request to increase the initial context or output limits. It
addresses a missing control flow: when the initial Core projection is bounded or
incomplete, the current agent cannot request a narrowly scoped, revision-safe
piece of additional evidence during the same investigation.

The intended capability is therefore:

```text
question
  -> bounded initial Core context
  -> validated plan
  -> Core-authorized targeted retrieval
  -> evidence ledger
  -> hypothesis/contradiction checks
  -> grounded synthesis
  -> deterministic Core audit
```

The Story remains compatible with the accepted architecture because Core owns
all authority and Python remains a probabilistic executor over authorized
inputs.

## Current Architecture

### Core context construction and projection

`RepositoryContextEngine` is the deterministic source of repository evidence.
The existing pipeline collects candidates, ranks them, applies bounded
selection, enriches selected content/symbols, and produces accounting,
truncation, warnings, and a context digest.

The current default limits are bounded independently, including approximately:

- 60 repository evidence items;
- 6,000 repository-context tokens;
- 500 summary characters;
- 20 history items.

`StoryContextAgentProjectionV1` then constructs the authorized projection. It
preserves the request, scope, freshness, context, grounding candidates,
accounting, and projection digest. It also verifies that evidence has one
resolvable project revision and rejects mixed or unknown revisions.

The projection is intentionally a consumer projection, not a retrieval API. It
does not expose a contract through which the provider can request more evidence.

### Existing loss accounting

`ContextLossLedger` and related Story 0178 work already provide the foundation
for distinguishing candidate, selection, enrichment, projection, grounding,
prompt, and provider outcomes. This should be reused rather than replaced by a
second evidence identity or provider-generated ledger.

The existing ledger is suitable for measuring the initial execution and for
recording bounded follow-up retrieval outcomes. The implementation plan must
decide the additive snapshot fields needed for plan, step, retrieval, and stop
metadata without duplicating raw repository payloads.

### Prompt and provider boundary

`StoryContextSubmissionService` constructs the authorized prompt and invokes
`StoryContextBudgetPreflight` before provider submission. Preflight already
separates input estimate, output allowance, model capacity, policy version,
complexity class, and safety margin.

The AI Engine prompt builder receives the projection and applies explicit rules
when the projection is truncated. It is required to avoid presenting exact
implementation facts that are not supported by the authorized projection.

`story_context_analysis_generation_service.py` performs defensive structured
validation and grounding checks, then callbacks to Core. It must not become the
owner of retrieval, authorization, revision identity, or context construction.

### Existing follow-up capability

`StoryAgentFollowUpService` is a Core-owned one-shot follow-up over an existing
authorized snapshot. It validates the principal, snapshot identity, request
length, guidance keys, TTL, idempotency, and provider budget before submitting a
new task.

This service is useful evidence for authorization and snapshot handling, but it
does not implement Story 0184 targeted retrieval:

- it reuses the parent selected knowledge snapshot;
- it submits another provider request;
- it does not expose search/read actions;
- it does not maintain a multi-step investigation state;
- it does not validate a model-generated plan;
- it does not append new canonical evidence to an evidence ledger.

It must not be stretched into an implicit generic agent loop without a new
versioned contract and explicit budgets.

## Ownership Map

| Responsibility | Current owner | Story 0184 implication |
|---|---|---|
| Project and revision scope | Java Core | Reuse existing scope and freshness validation |
| Candidate collection/ranking/selection | Java Core | Remains unchanged initially |
| Evidence identity/provenance/trust | Java Core | Every retrieval response must use canonical references |
| Projection and grounding allow-list | Java Core | Follow-up evidence must enter through the same authority |
| Prompt construction | Python AI Engine with Core contract | May consume plan/step inputs, never authorize them |
| Provider generation | Python AI Engine/provider | Remains structured and defensive |
| Callback acceptance | Java Core | Extend deterministically for new execution metadata |
| Investigation plan authorization | New Core capability | Must be typed, allow-listed, revision-pinned, and budgeted |
| Retrieval execution | New Core capability | Must use bounded search/read seams, not Python access |
| Evidence ledger persistence | Existing execution snapshot/ledger boundary | Additive non-trusted metadata is preferred initially |

No frontend implementation is required for the first increment. MCP/REST should
remain thin adapters over the same Core capability if the new capability is
externally exposed.

## Proposed Implementation Seams

The following seams are implementation candidates, not yet an approved plan:

1. **Capability registry and typed action contracts**
   - Initial actions should be limited to bounded repository/document search and
     bounded file/document range reads.
   - Each action must declare target, revision, locator, result limit, and
     purpose within a bounded schema.

2. **Execution-scoped investigation state**
   - Parent snapshot identity and one authorized revision.
   - Plan digest, step sequence, idempotency key, and status.
   - Independent step, retrieval, evidence, byte/token, time, and provider
     budgets.

3. **Retrieval result projection**
   - Canonical evidence reference and bounded locator.
   - Revision, provenance, trust, citability, evidence family, and outcome.
   - No opaque unbounded repository payload in normal diagnostics.

4. **Ledger and final audit**
   - Reuse `ContextLossLedger` identity and reason conventions.
   - Add plan/step/retrieval/stop summaries to the non-trusted execution
     snapshot.
   - Validate every final claim reference against the authorized ledger and
     grounding contract.

5. **Compatibility boundary**
   - Keep the current one-shot path operational.
   - Gate the bounded analyst behind an explicit configuration or capability
     policy until comparative evaluation is complete.

## Required Repository Areas

The future Implementation Plan should inspect and likely affect only these
areas unless evidence requires otherwise:

- `backend/.../storycontextanalysis/` for execution, authorization, snapshots,
  budgets, callback validation, and ledger integration;
- `backend/.../repositorycontext/` for reuse of deterministic search/read
  capabilities and canonical evidence resolution;
- `backend/.../projectcontext/` and existing projection contracts for bounded
  evidence representation;
- `ai-engine/app/schemas/`, `prompts/`, and Story Context services for plan,
  step, and final-result contracts;
- backend and AI Engine tests for contract, grounding, budget, revision, and
  failure scenarios;
- existing evaluation fixtures and Story 0178 loss telemetry for comparison.

No new database entity is established by this analysis. The plan must first
determine whether execution-scoped plan and ledger summaries fit the existing
`AiTask.contextSnapshot` and interaction-trace boundaries. Creating a durable
domain entity would require a separate architectural justification.

## Existing Tests and Validation Baseline

Relevant existing coverage includes:

- `StoryContextSubmissionServiceTest`;
- `StoryContextBudgetPreflightTest`;
- `ContextLossLedgerTest`;
- `AnalyzeStoryContextUseCaseTest`;
- Story Context callback identity and result validation tests;
- AI Engine Story Context generation and grounding tests;
- Story 0177 offline retrieval evaluation;
- Story 0178 loss-aware composition/projection tests.

The Implementation Plan must add representative scenarios for:

- valid and invalid retrieval plans;
- out-of-scope and mixed-revision requests;
- empty and duplicate retrieval results;
- step, byte/token, call, and elapsed-time exhaustion;
- contradictory evidence;
- insufficient evidence and correct abstention;
- duplicate callbacks and idempotent replay;
- incomplete provider output at an intermediate or final step;
- preservation of one-shot behavior.

## Risks and Open Decisions

### Risks

- A retrieval loop can increase cost and latency without increasing answer
  quality.
- Multiple retrieval responses can accidentally mix revisions.
- The provider can treat retrieved text as proof rather than as cited evidence.
- Plan and step state can create a second opaque orchestration system if action
  types and budgets are not deliberately small.
- Existing follow-up snapshot semantics may not be sufficient for multi-step
  execution without explicit lifecycle and idempotency rules.

### Open decisions for Implementation Planning

1. Which existing Core collectors/search services can provide the first
   deterministic search/read capabilities without introducing a duplicate
   retrieval model?
2. Whether each retrieval step is a separate `AiTask` callback or whether Core
   owns a parent execution with step-level provider calls.
3. Which plan and ledger fields can remain in the existing non-trusted snapshot
   versus requiring a new persistence model.
4. Whether the first bounded loop is synchronous, asynchronous, or implemented
   as a bounded sequence of existing task submissions.
5. Which configuration boundary enables the analyst and how default-off behavior
   is evaluated.

These are implementation-planning questions, not decisions made by this
analysis.

## Architecture Reconciliation

No conflict was found between Story 0184, ADR-006, ADR-063, ADR-067, ADR-069,
ADR-079, or the current repository implementation.

The current repository already contains the required authority boundaries,
revision checks, grounding contracts, budget preflight, one-shot follow-up
authorization, and loss accounting foundations. The missing capability is the
explicit Core-authorized retrieval/action contract and bounded investigation
state above those foundations.

The Story must not be implemented by adding repository access to Python,
loosening projection truncation, bypassing Core callbacks, or treating the
existing one-shot follow-up service as an unrestricted agent loop.

## Recommendation

The Repository Analysis recommends proceeding to an Implementation Plan only
after human approval of this analysis, with the following delivery order:

1. Define and evaluate the minimal Core search/read capability without enabling
   automatic investigation.
2. Add the typed plan, execution budgets, step state, ledger integration, and
   final Core audit behind an explicit feature boundary.
3. Compare the bounded analyst with the existing one-shot path using frozen
   scenarios and Story 0178 loss metrics.
4. Stop for a separate human decision before default enablement, production
   budget changes, or hybrid retrieval.

## Approval Gate

This Repository Analysis is complete and awaits explicit human approval. Human
approval is required before an Implementation Plan may be produced.
