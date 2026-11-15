# Story 0184 - Bounded Story Context Investigation and Targeted Retrieval

## Status

**IMPLEMENTATION COMPLETE - AWAITING HUMAN ACCEPTANCE**

This Story authorizes the next maturity step after loss-aware Story Context
composition. It does not authorize implementation, production rollout, RAG,
generic agent infrastructure, or changes to the accepted trust boundary.

Repository Analysis, Implementation Planning, implementation, acceptance, and
deployment remain subject to the established human approval gates.

## Problem

The Story Context capability currently performs a bounded, one-shot execution:

```text
question
  -> Core candidate collection and selection
  -> Core projection and grounding allow-list
  -> one provider request
  -> structured callback validation
```

This preserves deterministic Core authority, but broad questions can lose
answer-critical evidence across selection, enrichment, projection, prompt
preflight, or provider execution. The caller receives either a high-level
answer or a bounded failure, but the agent has no authorized way to ask Core
for a missing file, symbol, document section, or historical slice during the
same investigation.

Increasing a single token or item limit is not a sufficient solution. It can
increase prompt size, latency, provider failure probability, and noise without
proving that the additional evidence is relevant. The project therefore needs
an explicit, bounded investigation capability that distinguishes retrieval
failure from reasoning failure and makes incompleteness actionable.

## Goal

Evolve Story Context from a one-shot context provider into a bounded engineering
analyst while preserving Java/Core as the sole authority for project scope,
revision, evidence identity, trust, retrieval authorization, grounding, and
final validation.

The evolution has two controlled stages:

1. Add Core-authorized targeted retrieval capabilities with typed contracts and
   revision-pinned evidence references.
2. Add an optional bounded investigation loop that can plan, retrieve, verify,
   and synthesize within explicit execution budgets.

The result must clearly report what was established, what remains uncertain,
what evidence was used, what retrieval was attempted, and why execution stopped.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0111 - Structured Engineering Context
- Story 0112 - DevLog Engineering Story Context Agent
- Story 0173 - Story Context Budget Observability
- Story 0175 - Story Context Complex Response Completion Policy
- Story 0176 - Story Context Grounding and Retry Efficiency
- Story 0178 - Story Context Loss-Aware Composition and Projection
- `docs/investigations/story-context-agent-to-bounded-analyst-and-retrieval-strategy.md`
- `docs/investigations/story-context-truncation-and-provider-incomplete-response.md`

## Dependencies

Before implementation begins:

- Story 0178 must be accepted, or its loss-ledger contract must be explicitly
  superseded by human decision.
- The current context, projection, provider, and callback diagnostics must be
  captured as the baseline for comparison.
- The Repository Analysis must identify whether the first implementation can
  reuse existing Core search/read capabilities or requires a new bounded seam.

## Architectural Constraints

1. Java/Core remains the sole authority for project scope, source and revision
   identity, candidate identity, retrieval authorization, trust classification,
   ranking, selection, enrichment, projection, grounding, budget policy,
   provider-call authorization, and callback acceptance.
2. Python receives only Core-authorized immutable inputs and may not access the
   repository, database, filesystem, retrieval index, or embedding service.
3. Every targeted retrieval response must be revision-pinned and contain
   canonical evidence references, bounded locators, provenance, trust tier,
   citability, and an explicit retrieval outcome.
4. Similarity, ranking, model confidence, diagnostics, or budget pressure must
   never change trust, revision validity, citability, grounding eligibility, or
   relationship semantics.
5. A model-generated plan is a proposal for execution. Core must validate every
   action against an explicit capability registry, project scope, revision, and
   execution budget before it runs.
6. Investigation state, plans, steps, ledgers, hypotheses, and results remain
   non-trusted execution data until a separate human validation flow promotes
   eligible knowledge.
7. All investigation budgets must be explicit and independently observable:
   maximum steps, retrieval calls, evidence items, bytes/tokens, elapsed time,
   and provider output allowance.
8. The loop must fail closed on unknown capability, invalid scope, revision
   mismatch, malformed evidence references, exhausted budget, or incomplete
   provider output.
9. Incomplete provider output must never be parsed, persisted as successful
   analysis, or used as grounded evidence.
10. Diagnostics must remain bounded and non-sensitive. They must not expose
    credentials, authorization headers, full prompts, raw provider output, or
    unbounded repository payloads.
11. Existing one-shot behavior must remain available as a compatibility path
    until comparative evaluation demonstrates that the bounded analyst is safe
    to enable by default.
12. No production RAG, embeddings, vector database, generic agent framework,
    persistent model memory, or unrestricted tool execution is introduced by
    this Story.

## In Scope

### Phase 1 - Core-authorized targeted retrieval

- Define versioned, typed capability contracts for a minimal retrieval set,
  starting with bounded repository/document search and bounded file or document
  range reads.
- Validate project, source, revision, path, locator, evidence family, trust,
  and citability in Core before returning results.
- Return canonical evidence references and bounded locators rather than opaque
  text chunks.
- Define per-capability limits for result count, bytes, tokens, calls, and
  elapsed time.
- Record retrieval outcomes, rejected requests, budget consumption, and loss
  reasons in the existing execution diagnostics/loss model.
- Expose the capability through the existing Story Context Core boundary rather
  than creating a Python-owned retrieval path.
- Add deterministic tests for valid retrieval, empty results, out-of-scope
  paths, revision mismatch, malformed locators, budget exhaustion, and duplicate
  evidence.

### Phase 2 - Bounded investigation loop

- Define a versioned investigation plan containing only supported action types,
  expected purpose, target scope, and bounded step metadata.
- Add execution-scoped state with a maximum step count, retrieval-call count,
  evidence budget, time budget, and provider budget.
- Allow the analyst to request an additional authorized retrieval only through
  the validated capability contracts.
- Maintain an evidence ledger distinguishing initial evidence, retrieved
  evidence, rejected evidence, duplicate evidence, and evidence unavailable at
  the authorized revision.
- Track explicit findings, interpretations, hypotheses, contradictions,
  unresolved questions, and evidence references.
- Add a deterministic final Core audit verifying evidence-reference resolution,
  revision consistency, grounding eligibility, trust semantics, and scope.
- Add bounded stop reasons such as `COMPLETED`, `SUFFICIENT_EVIDENCE`,
  `BUDGET_EXHAUSTED`, `NO_RELEVANT_EVIDENCE`, `REVISION_INVALID`, and
  `PROVIDER_INCOMPLETE`.
- Persist a non-trusted execution snapshot containing the plan, steps, ledger
  summary, stop reason, budget accounting, and final structured result.

### Evaluation and operations

- Compare the current one-shot path with the bounded analyst on broad,
  targeted, insufficient-evidence, contradictory, stale-revision, and
  deliberately truncated scenarios.
- Measure relevant evidence recall, grounding coverage, unsupported claims,
  contradiction detection, correct abstention, useful-question rate, latency,
  token cost, provider failure rate, and execution completion rate.
- Verify that targeted retrieval reduces answer-critical loss without simply
  increasing the initial context budget.
- Document operator-visible diagnostics and recovery guidance.

## Out of Scope

- Increasing production context, item, enrichment, projection, or provider
  budgets without a separate measured decision.
- Replacing deterministic Core selection with model similarity or confidence.
- Python-side repository, database, filesystem, retrieval, or authorization
  logic.
- Unbounded autonomous planning, arbitrary tool calls, generic agent
  frameworks, persistent memory, or cross-project access.
- Production RAG, embeddings, vector search, or a vector database.
- Promoting analysis output directly to trusted Insights, Decisions, Events, or
  Stories.
- Treating retrieved text, similarity, or model confidence as evidence without
  a canonical Core reference.
- Silently accepting incomplete provider output.
- Broad frontend redesign or unrelated MCP/REST changes.

## Evaluation Model

```text
question and authorized scope
  -> bounded initial Core context
  -> validated investigation plan
  -> Core-authorized targeted retrieval
  -> revision-pinned evidence ledger
  -> hypothesis and contradiction checks
  -> grounded synthesis
  -> deterministic Core audit
```

The evaluation must preserve the distinction between:

- evidence never retrieved;
- evidence retrieved and rejected by Core;
- evidence selected but unavailable to the projection;
- evidence retrieved through a follow-up capability;
- evidence unavailable at the authorized revision;
- evidence visible to the provider but not used in a claim;
- provider output that was incomplete or invalid.

## Acceptance Criteria

### AC-1 - Capability authority and scope

Every targeted retrieval request is validated and authorized by Java/Core. A
request outside the project, source, revision, path, locator, or capability
policy is rejected before repository access or provider execution.

### AC-2 - Canonical retrieval evidence

Every successful retrieval result contains a canonical evidence reference,
revision identity, bounded locator, evidence family, trust tier, citability, and
provenance sufficient for Core grounding validation.

### AC-3 - Bounded execution

Every investigation has explicit maximum steps, retrieval calls, evidence size,
elapsed time, and provider output allowance. Exhaustion produces a structured
stop reason and cannot silently continue with an unbounded request.

### AC-4 - Validated investigation plan

The analyst can propose a plan, but Core executes only supported action types
whose targets and budgets pass deterministic validation. Invalid or unsupported
actions fail closed with bounded diagnostics.

### AC-5 - Evidence ledger

The execution snapshot records bounded ledger summaries for initial, retrieved,
rejected, duplicate, unavailable, grounded, and provider-visible evidence. The
ledger remains revision-safe and does not introduce provider-generated identity.

### AC-6 - Grounded synthesis and contradiction handling

Final findings and interpretations reference eligible evidence. Contradictory
evidence is represented explicitly, unsupported claims are rejected or marked
unresolved, and insufficient evidence produces a visible abstention or missing
information result.

### AC-7 - Incomplete output safety

Provider-incomplete or malformed intermediate/final output is terminal for the
affected execution. It is never parsed or persisted as a successful analysis,
and the caller receives an actionable bounded diagnostic.

### AC-8 - One-shot compatibility

The existing one-shot Story Context path remains operational and its behavior
is covered by regression tests while the bounded analyst is evaluated.

### AC-9 - Comparative quality evidence

The evaluation demonstrates, using frozen scenarios, whether the bounded
analyst improves relevant evidence coverage and useful-question resolution
without unacceptable increases in unsupported claims, latency, token cost,
provider failures, or revision leakage.

### AC-10 - Human-readable completeness

The returned analysis exposes the investigation stop reason, completeness state,
retrieval summary, unresolved questions, budget accounting, and provenance
without exposing credentials, full prompts, raw provider output, or unbounded
repository content.

### AC-11 - Documentation and decision boundary

The implementation report records whether the bounded analyst is recommended
for default enablement. Enabling it by default, changing production budgets, or
introducing hybrid retrieval requires a separate explicit human decision when
the evaluation does not establish safety.

## Recommended Delivery Order

1. Establish the current one-shot baseline using the loss ledger and frozen
   evaluation scenarios.
2. Implement and evaluate the minimal Core search/read capabilities without
   enabling an automatic loop.
3. Implement the plan, execution state, ledger, and final audit behind an
   explicit feature/configuration boundary.
4. Compare one-shot and bounded analyst outcomes.
5. Stop for human review before default enablement, budget changes, or any
   hybrid retrieval decision.

## Known Risks

- Additional retrieval can increase cost and latency without improving the
  answer. Mitigate with explicit step and evidence budgets.
- Multiple retrievals can mix revisions. Mitigate by binding every response to
  one authorized revision and rejecting mismatches.
- The model can treat retrieved text as proof. Mitigate with canonical
  references, typed grounding, and final Core validation.
- A loop can become opaque. Mitigate by persisting the plan, steps, ledger,
  stop reason, and budget accounting.
- Retrieval gaps may be incorrectly attributed to semantics. Measure
  deterministic recall before considering hybrid retrieval.

## Human Decision Requested

Review and approve or reject this proposed Story scope. Approval of this Story
must not be interpreted as implementation authorization; the Repository
Analysis and Implementation Plan gates remain mandatory.
