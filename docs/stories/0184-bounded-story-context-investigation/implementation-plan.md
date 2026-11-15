# Implementation Plan - Story 0184

## Status

**PLAN COMPLETE - AWAITING HUMAN APPROVAL**

This plan is based on the human-approved Repository Analysis for Story 0184.
It does not authorize implementation until this plan receives explicit human
approval.

## Objective

Add a Core-authorized, bounded investigation protocol above the existing
one-shot Story Context capability. The implementation will first expose typed
search/read actions and then allow the AI Engine to propose bounded follow-up
actions during one investigation. Java/Core validates and executes every action,
maintains the execution ledger, and performs the final grounding audit.

The existing one-shot path remains available and unchanged by default.

## Governing Constraints

- Java/Core remains authoritative for project scope, revision, evidence identity,
  retrieval authorization, trust, grounding, budgets, and callback acceptance.
- Python may propose an investigation plan or action and may request a Core
  capability, but it may not access repositories, databases, filesystems, or
  retrieval indexes directly.
- Every retrieval result is canonical, revision-pinned, bounded, and grounded
  through existing Core references.
- Investigation output remains non-trusted execution data.
- Incomplete or malformed provider output remains terminal and is never parsed
  as successful analysis.
- No initial context, selection, projection, or provider budget is increased by
  this plan.
- No RAG, embeddings, vector database, generic agent framework, persistent
  model memory, or arbitrary tool execution is introduced.
- Existing `contextDigest`, `selectionDigest`, `projectionDigest`, grounding,
  freshness, and callback identity semantics remain authoritative.
- New strict contracts use an additive, versioned protocol; old one-shot
  clients remain valid.

## Design Decisions

### 1. Core-owned execution, provider-proposed actions

The AI Engine may produce a typed plan or action proposal, but Core owns the
state machine:

```text
initial Story Context task
  -> provider proposes plan/action
  -> Core validates action and budget
  -> Core executes authorized retrieval
  -> Core appends bounded result to execution snapshot
  -> provider receives the new authorized projection
  -> final synthesis
  -> Core final audit and callback acceptance
```

The provider never chooses a repository path or revision outside the action
contract and never receives an implicit repository handle.

### 2. Reuse the existing task snapshot; no new database entity initially

The root `AiTask.contextSnapshot` remains the durable execution state. Additive
fields will contain only bounded plan, step, ledger, budget, and stop metadata.
No new database table or migration is planned.

The implementation must use a Core transaction lock around action validation and
snapshot update so duplicate or concurrent action requests cannot consume the
same budget twice. If the existing task lifecycle cannot safely support this
state transition, implementation must stop and report the need for an explicit
entity decision rather than adding an unplanned persistence model.

### 3. Versioned protocol v2

The current `story-context-agent-protocol/v1` remains unchanged for one-shot
requests. Add a versioned v2 envelope for investigation executions containing:

- execution identity and parent task identity;
- authorized project and revision scope;
- current plan/step number;
- action proposal or final synthesis;
- bounded capability result;
- plan, step, ledger, and budget digests where applicable.

The v2 contract must reject unknown action kinds and unknown required fields.

### 4. Minimal capability set

Implement only these initial Core capabilities:

- `SEARCH_EVIDENCE`: bounded lexical/structural search through existing Core
  retrieval seams;
- `READ_EVIDENCE_RANGE`: bounded file/document range read resolved to canonical
  evidence references.

The implementation must reuse existing collectors, repository revision scope,
evidence factories, document references, and content policies where possible.
It must not create a second ranking or trust policy.

### 5. Conservative investigation defaults

The analyst is disabled by default until evaluation is complete. Evaluation
profiles use explicit bounded limits for steps, actions, evidence count, bytes,
tokens, elapsed time, and provider output. Production defaults must not be
increased silently and must be reviewed separately before enablement.

### 6. Bounded ledger and stop semantics

Reuse `ContextLossLedger` conventions and add investigation stages for:

- plan proposed/accepted/rejected;
- action authorized/rejected;
- retrieval returned empty/results/duplicate/unavailable;
- evidence appended/grounded/unresolved;
- final audit passed/failed;
- terminal stop reason.

The execution must terminate with one explicit reason such as `COMPLETED`,
`SUFFICIENT_EVIDENCE`, `BUDGET_EXHAUSTED`, `NO_RELEVANT_EVIDENCE`,
`REVISION_INVALID`, `ACTION_REJECTED`, `PROVIDER_INCOMPLETE`, or
`VALIDATION_FAILED`.

## Implementation Sequence

### Phase 1 - Capability contracts and Core action authorization

1. Add immutable Core-side records/enums for investigation scope, action kind,
   search/read request, capability result, plan, step, budget, and stop reason.
2. Add validation for project slug, authorized revision, normalized path/locator,
   result limits, byte/token limits, action purpose, and allowed capability kind.
3. Add a small capability registry mapping action kinds to Core handlers.
4. Reuse deterministic repository/document services to execute search and range
   reads. Resolve every returned item to canonical evidence identity, provenance,
   trust, citability, source, and revision.
5. Reject out-of-scope paths, mixed revisions, unsupported locators, duplicate
   action IDs, exhausted budgets, and unknown capabilities before retrieval.
6. Add bounded aggregate diagnostics without including full repository payloads
   in ordinary traces.

Likely backend surfaces:

- `backend/.../storycontextanalysis/investigation/` for contracts, registry,
  budget, and ledger integration;
- `backend/.../repositorycontext/` for adapter ports over existing search/read
  and evidence resolution services;
- `backend/.../storycontextanalysis/usecase/` for Core orchestration;
- existing authorization, revision, grounding, and snapshot services.

### Phase 2 - Snapshot execution state and action lifecycle

1. Add an investigation section to the existing `AiTask.contextSnapshot` with:
   - protocol version;
   - execution ID;
   - authorized revision;
   - plan digest;
   - current step and action IDs;
   - budget accounting;
   - bounded ledger summary;
   - stop status/reason.
2. Add transactional action acceptance and snapshot update with idempotency.
3. Ensure duplicate action delivery returns the original bounded result and does
   not repeat repository reads or consume budget.
4. Ensure a stale or terminal task cannot execute another action.
5. Preserve the parent context, selection, projection, and grounding digests for
   every step.

No database migration is planned. JSON snapshot size must remain bounded by
configuration and tests.

### Phase 3 - Versioned AI Engine protocol and prompt flow

1. Add Pydantic v2 schemas for investigation plan/action proposals, capability
   results, and final synthesis metadata.
2. Add a Core callback/action endpoint or equivalent existing Core boundary for
   provider action proposals. It must authenticate the task and authorize the
   action through the Phase 1 registry.
3. Update the Story Context prompt builder to distinguish:
   - initial context;
   - authorized retrieval result;
   - prior ledger summary;
   - final synthesis requirements.
4. Keep provider output structured and require action IDs, purpose, and typed
   locators for retrieval proposals.
5. Make the AI Engine stop and report a bounded error when Core rejects an
   action, rather than inventing a result or retrying outside the contract.

Likely AI Engine surfaces:

- `ai-engine/app/schemas/story_context_agent_protocol.py`;
- `ai-engine/app/schemas/story_context_analysis.py`;
- `ai-engine/app/prompts/story_context_analysis.py`;
- `ai-engine/app/services/story_context_analysis_generation_service.py`;
- `ai-engine/app/clients/core_callback_client.py`.

### Phase 4 - Bounded analyst orchestration

1. Add Core orchestration for the bounded sequence of provider decision,
   validated action, retrieval result, and next provider decision.
2. Enforce maximum steps, actions, evidence items, bytes/tokens, elapsed time,
   and provider output before each transition.
3. Deduplicate evidence by canonical reference and preserve the authorized
   revision across all steps.
4. Permit final synthesis only after the plan/action state is terminal or after
   an explicit `SUFFICIENT_EVIDENCE` decision.
5. Run the final Core audit against the complete bounded ledger and grounding
   allow-list before accepting the result.
6. Keep the existing one-shot `AnalyzeStoryContextUseCase` path as the default
   when the analyst capability is disabled or unsupported.

### Phase 5 - Evaluation and feature boundary

1. Extend existing Story 0177/0178 fixtures with broad, targeted, truncated,
   contradictory, insufficient, stale-revision, and out-of-scope scenarios.
2. Compare one-shot and bounded-analyst executions for evidence recall,
   grounding coverage, unsupported claims, contradiction handling, abstention,
   useful-question rate, latency, token cost, provider failures, and revision
   leakage.
3. Add a credential-free replay path for action authorization, idempotency,
   budget exhaustion, and final audit behavior.
4. Keep the analyst feature disabled by default until the evaluation report
   provides evidence for a separate enablement decision.

### Phase 6 - Documentation reconciliation

Update only documentation directly affected by the implemented contracts and
runtime behavior:

- Story 0184 implementation report and evaluation report;
- operator/runtime documentation for analyst enablement, budgets, stop reasons,
  and recovery;
- protocol documentation if the versioned v2 contract becomes a repository
  canonical contract.

Do not update generic architecture documentation or introduce a RAG decision
unless the evaluation establishes that a separate decision is required.

## Planned Files and Surfaces

Expected implementation surfaces, subject to verification during implementation:

- new bounded contracts and handlers under
  `backend/.../storycontextanalysis/investigation/`;
- existing `AiTask` snapshot and task repository locking/query support;
- existing Core repository-context services and evidence resolution;
- Story Context authorization, projection, grounding, callback, and budget
  services;
- versioned Story Context protocol records in `devlog-contracts` if the shared
  contract boundary requires them;
- AI Engine protocol schemas, prompt builder, generation service, and Core
  callback client;
- backend, AI Engine, and replay evaluation tests;
- Story 0184 implementation/evaluation/documentation artifacts.

No frontend, MCP-specific duplicate implementation, provider-specific retrieval
implementation, vector infrastructure, or database migration is planned.

## Test Strategy

### Backend unit and contract tests

- action schema validation and unknown action rejection;
- project/path/locator/revision authorization;
- canonical evidence mapping and trust preservation;
- empty, duplicate, unavailable, and mixed-revision results;
- per-capability and total budget enforcement;
- plan and step state transitions;
- duplicate action idempotency;
- terminal task and stale revision rejection;
- bounded snapshot and ledger size;
- final grounding audit and stop reasons;
- one-shot regression behavior.

### AI Engine tests

- v1 compatibility remains valid;
- v2 plan/action/final schemas reject malformed or unknown fields;
- action IDs and locators are preserved;
- Core rejection is terminal and not replaced with invented evidence;
- authorized retrieval results are included in the next prompt;
- provider-incomplete output remains terminal;
- final structured output references only authorized evidence.

### Integration and evaluation tests

- initial context followed by one valid search action;
- initial context followed by bounded range read;
- multiple actions until budget exhaustion;
- contradictory evidence requiring explicit unresolved state;
- no relevant evidence requiring abstention;
- mixed revision rejection;
- duplicate callback/action replay;
- provider failure during an intermediate step;
- broad question comparison against current one-shot path;
- requested-file priority preservation.

## Validation Commands

Focused validation after implementation:

```bash
./backend/mvnw -pl backend test -Dtest='*Investigation*Test,*StoryContext*Test,*FollowUp*Test' -B
cd ai-engine && python -m pytest tests -k 'story_context or protocol or callback' -q
git diff --check
```

Broader validation:

```bash
./backend/mvnw -pl backend -am clean verify -B
./backend/mvnw -pl mcp-server test -B
cd ai-engine && python -m pytest -q
```

The exact focused class selection must be adjusted after implementation once
the final test names are known. A failed or unavailable quality check must be
reported rather than represented as passed.

## Documentation and Delivery Boundary

The implementation must not begin until this plan is explicitly approved. After
implementation, documentation reconciliation and code review remain mandatory.
No commit, push, pull request, merge, or Story acceptance is authorized by this
plan.

## Approval Gate

This Implementation Plan is complete and awaits explicit human approval.
