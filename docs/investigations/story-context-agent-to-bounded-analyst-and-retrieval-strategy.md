# Investigation: From Story Context Agent to Bounded Engineering Analyst

## Status

**DESIGN_DIRECTION — AWAITING HUMAN REVIEW**

This document formalizes the maturity path from the current Story Context Agent to a bounded
engineering analyst. It does not authorize implementation, reopen ADR-067 decisions, or change the
V1 non-goals of Story 0112.

## Executive Conclusion

DevLog has a successful **context-provider capability**, not yet a fully autonomous engineering
analyst.

The current Story Context Agent already provides the most important foundation:

```text
request -> Java/Core context construction -> bounded evidence selection
         -> immutable projection and grounding contract
         -> Python structured generation -> Core callback validation
```

The next step is not to give the model unrestricted repository access or to introduce a generic
agent framework. The next step is to add a **bounded investigation loop** above the existing Core
capabilities:

```text
question
  -> bounded investigation plan
  -> authorized targeted retrieval
  -> evidence ledger
  -> hypothesis and contradiction checks
  -> grounded synthesis
  -> deterministic Core validation
```

RAG is a possible optimization inside the retrieval stage. It must remain a candidate-generation
mechanism, never the authority for scope, trust, freshness, evidence identity, or relationships.

## 1. Current State

### 1.1 What is working

The Story Context Agent currently provides:

- Java/Core-owned project and repository context construction.
- Optional `storyId` and optional file filtering at the MCP boundary.
- Bounded context selection with accounting and truncation metadata.
- Versioned projections with context and projection digests.
- Explicit grounding allow-lists.
- Structured findings, uncertainties, missing information, questions, confidence and provenance.
- Python-side output validation and corrective retry.
- Java/Core callback validation and terminal identity checks.
- Immutable, execution-scoped, non-trusted analysis persistence.
- MCP and REST adapters over the same Core capability.

### 1.2 What the recent live tests established

The live `trading-os` runs confirmed:

- A request without `storyId` can execute successfully.
- An omitted `files` field is normalized to an empty list and can execute successfully.
- The Core can select evidence without an explicit file filter.
- A context can contain evidence even when `scope.files=[]`.
- OpenAI generation can complete and callback successfully.
- The result is grounded and structured, but mostly high-level when the context is broad and
  truncated.

### 1.3 What is still missing

The current execution is effectively one-shot:

- The model receives one bounded context projection.
- It produces one structured result.
- It does not formulate and execute follow-up retrieval requests during the same investigation.
- It does not systematically test competing hypotheses.
- It does not perform a deterministic contradiction pass before synthesis.
- It does not expose a complete evidence ledger showing why each conclusion survived validation.
- It can identify a useful implementation question without resolving it.

This explains the difference between a useful context summary and a true engineering investigation.

## 2. Architectural Invariants

The maturity path must preserve the accepted architecture.

### 2.1 Java/Core remains authoritative

Java/Core owns:

- project and story scope;
- authorization;
- context construction and selection;
- trust tier assignment;
- evidence identity and provenance;
- freshness and revision identity;
- grounding contract construction;
- result acceptance and persistence;
- capability budgets and stop conditions.

Python owns:

- probabilistic reasoning;
- plan proposal within the authorized capability set;
- prompt orchestration;
- structured generation;
- uncertainty-aware interpretation;
- bounded iteration requested and authorized by Core.

The analyst must not access the database or repository directly and must not reconstruct a parallel
context model.

### 2.2 AI output remains non-trusted

The analyst may produce factual extractions, interpretations and recommendations. None of these
becomes trusted knowledge automatically. A recommendation is not a proposal, and a confident model
output is not evidence.

### 2.3 Retrieval does not establish relationships

Finding two items through lexical or vector similarity does not establish a relationship between
them. Explicit relationships must continue to come from deterministic Core evidence. AI-generated
relationships remain hypotheses unless the contract classifies them otherwise and supplies evidence.

### 2.4 No autonomous modification

The analyst remains read-only. It cannot modify source files, stories, decisions, trusted knowledge,
branches or repository state.

## 3. Target Capability: Bounded Engineering Analyst

The target is a capability, not a general-purpose autonomous agent.

### 3.1 Input contract

The request should contain:

- project slug;
- optional story identifier;
- intent identifier;
- optional initial file scope;
- investigation question or objective;
- optional constraints and priorities;
- maximum investigation budget.

The question must be subordinate to the catalogued intent. Free-form guidance may refine the
objective but must not bypass Core authorization or broaden the scope implicitly.

### 3.2 Internal execution state

Core should persist or carry an execution-scoped state containing:

- current investigation objective;
- authorized project/story/revision scope;
- current context digest;
- retrieval steps already performed;
- evidence ledger;
- hypotheses and status;
- contradictions discovered;
- remaining token, step and time budgets;
- terminal status and reason.

This state is execution memory, not persistent conversational memory and not trusted knowledge.

### 3.3 Investigation loop

The bounded loop should follow this sequence:

1. Build the initial canonical context.
2. Ask the model for a structured investigation plan, not a final answer.
3. Validate the plan against allowed capabilities and budgets.
4. Execute authorized retrieval requests through Core.
5. Add returned evidence to the execution ledger.
6. Ask the model to update hypotheses and identify contradictions or gaps.
7. Repeat only while the stop policy allows it.
8. Request the final structured synthesis.
9. Validate every factual and interpretative claim against the final ledger.
10. Persist and return the non-trusted analysis snapshot.

The loop should stop when any of these conditions holds:

- the model declares the question answered with sufficient evidence;
- no authorized retrieval capability can reduce the uncertainty;
- the maximum number of steps is reached;
- the token, time or evidence budget is exhausted;
- the model requests an unauthorized capability;
- evidence is contradictory and the contract requires human review;
- Core detects an identity, grounding or scope mismatch.

## 4. Capability Surface

The first analyst version should expose a small deterministic capability set.

### 4.1 Recommended capabilities

- Search repository paths and symbols within the authorized revision.
- Read a bounded file range.
- Read a bounded document section.
- Search project history by path, symbol, commit or term.
- Compare two authorized revisions or snapshots.
- Retrieve the context for a specific authorized component.
- Retrieve prior non-trusted analysis metadata when explicitly requested.

### 4.2 Capabilities to defer

- Arbitrary shell execution.
- Arbitrary network access.
- Direct database queries from Python.
- Unbounded repository cloning or scanning.
- Automatic code edits.
- Automatic story or decision mutation.
- Generic multi-agent delegation.
- Persistent conversational memory.

Each capability should have an explicit request and response contract, a budget contribution and a
grounding identity. A tool response without a canonical evidence reference must not be usable as
support for a factual conclusion.

## 5. Evidence Ledger and Final Contract

The analyst needs more than a list of findings. It needs a traceable ledger.

### 5.1 Evidence ledger entry

Each entry should contain at least:

```text
evidenceReference
sourceType
project
revision
resource
locator
retrievalStep
relevanceReason
trustTier
```

The ledger is Core-owned. The model may propose why an item is relevant, but Core must bind the
entry to the authoritative evidence identity and revision.

### 5.2 Hypothesis state

Each hypothesis should be explicit:

```text
id
statement
status: OPEN | SUPPORTED | REFUTED | NOT_ESTABLISHED
supportingEvidence[]
contradictingEvidence[]
confidence
```

`SUPPORTED` must not mean proven causality. The relationship semantics from ADR-067 remain in
force. A result may correctly conclude `NOT_ESTABLISHED`.

### 5.3 Final analyst output

The final contract should add bounded investigation metadata to the existing Story Context result:

- question and selected subject;
- investigation steps;
- evidence ledger summary;
- confirmed findings;
- interpretations;
- hypotheses and their status;
- contradictions;
- risks and impacts;
- missing information;
- recommendations;
- stop reason;
- budget accounting;
- complete provenance and revision identity.

Existing semantic sections remain preferable to a generic untyped findings map. Optional additions
must be additive and versioned.

## 6. Role of Retrieval and RAG

### 6.1 Retrieval before RAG

The deterministic Core retrieval system should remain the first retrieval layer. It already provides
bounded selection, trust classification, project scope, revision identity, evidence references and
accounting. The analyst should first learn to use these capabilities iteratively.

### 6.2 Where RAG may help

If evaluation shows that deterministic lexical and structural retrieval misses relevant candidates,
a hybrid index may be introduced:

```text
analyst query
  -> lexical and/or vector candidate search
  -> Core scope and trust filtering
  -> revision and provenance validation
  -> bounded evidence projection
  -> analyst reasoning
```

RAG can improve candidate recall and reduce the cost of scanning large repositories. It can also
help find semantically related documentation, symbols and historical evidence that share few exact
terms.

### 6.3 What RAG must not do

RAG must not:

- define the authoritative context;
- bypass Core authorization;
- turn similarity into a relationship;
- hide the source revision;
- return untraceable chunks;
- promote retrieved text to trusted knowledge;
- replace deterministic selection where a deterministic rule exists.

Every retrieved chunk must be mapped back to a canonical evidence reference, bounded locator,
revision and provenance. If that mapping fails, the chunk can assist exploration internally but must
not support a final factual or interpretative claim.

### 6.4 Decision rule

Do not introduce vector infrastructure merely because the analyst is not yet iterative. First measure
the current retrieval failure modes. Introduce hybrid RAG only when a documented recall gap remains
after targeted deterministic capabilities are implemented and evaluated.

## 7. Delivery Roadmap

### Phase 0 — Context quality baseline

Goal: make the current provider measurable.

- Record selected and discarded evidence by category.
- Record truncation, freshness and revision state.
- Add evaluation cases for broad and targeted scopes.
- Measure grounding coverage, unsupported claims and useful-question rate.
- Make incomplete context visible in the returned analysis.

Exit condition: the team can distinguish a retrieval failure from a reasoning failure.

### Phase 1 — Targeted retrieval capabilities

Goal: allow one extra authorized context request without a general agent loop.

- Add search and bounded file/document reads as Core capabilities.
- Define typed request/response contracts.
- Bind every response to a revision and evidence reference.
- Add per-capability budgets and authorization checks.
- Keep the initial one-shot Story Context Agent behavior unchanged by default.

Exit condition: a human investigation can resolve a known context gap through explicit targeted
retrieval.

### Phase 2 — Bounded analyst loop

Goal: add planning, retrieval and verification.

- Add a plan contract with allowed action types only.
- Add execution-scoped state and step accounting.
- Add hypothesis and contradiction tracking.
- Add a deterministic final evidence audit.
- Persist the execution snapshot as non-trusted output.

Exit condition: benchmarked investigations show improved evidence coverage without an unacceptable
increase in unsupported claims or cost.

### Phase 3 — Hybrid retrieval evaluation

Goal: determine whether RAG is justified.

- Build a fixed retrieval-recall benchmark.
- Compare deterministic, lexical and vector candidate generation.
- Measure canonical evidence resolution rate.
- Measure false-positive retrieval and grounding cost.
- Introduce RAG only if it improves analyst outcomes under the same trust boundary.

Exit condition: an accepted decision records whether hybrid retrieval is needed and for which evidence
classes.

### Phase 4 — Analyst maturity

Possible later capabilities:

- cross-story comparison;
- prior-analysis comparison;
- significance and risk scoring;
- explicit human follow-up questions;
- proactive analysis triggers.

These require separate product and architectural decisions. They should not be inferred from the
success of the bounded analyst loop.

## 8. Evaluation Plan

The analyst should be evaluated on more than response fluency.

### Retrieval metrics

- relevant evidence recall;
- irrelevant evidence rate;
- revision consistency;
- truncation rate;
- grounding reference resolution rate;
- time and token cost per investigation.

### Reasoning metrics

- supported finding precision;
- unsupported factual claim rate;
- hypothesis calibration;
- contradiction detection;
- correct `NOT_ESTABLISHED` abstention;
- question resolution rate;
- recommendation classification accuracy.

### Runtime metrics

- plan validation failures;
- unauthorized capability requests;
- callback retries;
- terminal callback conflicts;
- execution completion rate;
- failure diagnostics quality;
- idempotent replay behavior.

### Required test scenarios

- broad project question with no `files` filter;
- narrowly scoped file question;
- missing or stale repository revision;
- contradictory evidence;
- insufficient evidence requiring abstention;
- retrieval request outside the authorized project;
- budget exhaustion;
- duplicate callback;
- provider output with fabricated evidence references;
- provider failure during an intermediate step.

## 9. Risks and Mitigations

### Context explosion

More retrieval steps can increase prompt size and cost. Mitigate with step budgets, evidence deduplication,
per-category quotas and explicit stop reasons.

### Retrieval-induced hallucination

The model may treat semantically similar text as proof. Mitigate with canonical references, bounded
locators, typed relationship semantics and final Core validation.

### Hidden revision mixing

Additional retrieval can accidentally combine revisions. Every capability response must carry the
same authorized revision identity or be rejected.

### Agent loop opacity

An analyst that cannot explain why it retrieved evidence is difficult to trust. Persist the plan,
steps, ledger, stop reason and budget accounting in the non-trusted execution snapshot.

### Premature generic-agent complexity

Unbounded planning, arbitrary tools and persistent memory would create a second architecture beside
DevLog Core. Keep the capability registry, action types and budgets explicit and small.

### RAG infrastructure bias

Adding a vector database can obscure basic retrieval defects and create operational cost. Require a
measured recall gap before adding embeddings or vector search.

## 10. Recommended Next Decision

The next implementation objective should be **Phase 1: targeted retrieval capabilities**, not RAG
and not a generic autonomous agent runtime.

The first concrete capability should be a bounded, Core-authorized search/read operation that returns
canonical evidence references and locators. It should be evaluated against the current one-shot agent
before any iterative loop is enabled by default.

This preserves ADR-067 and Story 0112 while creating the missing bridge from:

```text
context provider
```

to:

```text
bounded engineering analyst
```

## References

- `docs/decisions/ADR-067.md`
- `docs/stories/0112-devlog-story-context-agent/story.md`
- `docs/investigations/next-agent-maturity-objective.md`
- `docs/investigations/current-human-driven-engineering-context-capabilities.md`
- `AGENTS.md`
