# Story 0225 - DevLog to OpenCode End-to-End Audit and Comparative Evaluation

## Status

**IMPLEMENTATION IN PROGRESS**

This Story authorizes repository analysis and protocol design only. It does not
authorize production implementation, evaluation execution against live services,
commit, push, merge, or pull request creation. Human approval is required before
each subsequent implementation or evaluation phase.

## Problem

The project has a working DevLog Story Context capability and an OpenCode MCP
integration, but the complete path from an OpenCode request through MCP, Java
Core, AI Engine, callback validation, persistence and returned result has not
been audited as one reproducible system.

Existing evaluations cover bounded Story Context behavior, loss accounting and
response quality, but they do not yet provide a single Story-0210 baseline that
compares direct context retrieval with the DevLog-assisted Story Agent under
identical questions, repository scope, revision, repetitions and outcome
classification.

The project needs evidence about what information survives each boundary, where
information is lost, how failures are surfaced, whether tracing is complete, and
whether the DevLog-assisted path improves useful grounded answers without
weakening determinism, trust, scope or fail-closed behavior.

## Goal

Produce a revision-pinned, bounded and reproducible audit and comparative
evaluation of the real DevLog to OpenCode workflow.

The result must:

- document the actual runtime contracts and boundary behavior;
- distinguish baseline context retrieval from DevLog-assisted execution;
- expose information loss, partial results, timeout and provider-incomplete
  outcomes without relabeling them as success;
- measure grounded usefulness separately from transport and execution status;
- identify the smallest justified fixes, if any;
- leave production behavior unchanged unless a later approved implementation
  plan explicitly authorizes a targeted fix.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Persist a Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0111 - Structured Engineering Context
- Story 0112 - DevLog Engineering Story Context Agent
- Story 0177 - Evaluate Hybrid Retrieval for Story Context
- Story 0179 - Story Context Runtime Loss Evaluation
- Story 0184 - Bounded Story Context Investigation and Targeted Retrieval
- Story 0209 - DevLog Agent Response Quality and Evidence Grounding
- `docs/investigations/story-context-agent-to-bounded-analyst-and-retrieval-strategy.md`

## Architectural Constraints

1. Java/Core remains the sole authority for project scope, source and revision
   identity, evidence identity, trust tiers, context construction, grounding,
   capability authorization and final callback acceptance.
2. Python remains responsible for probabilistic generation and defensive
   validation only. It must not access the repository, database, retrieval
   index or credentials directly.
3. AI output remains non-trusted execution output. This Story must not promote
   output to Insights, Decisions, Events, Stories or proposals.
4. MCP and REST remain thin transport adapters over the same Core capability.
5. Similarity, model confidence, answer quality or tool success must not grant
   scope, trust, revision validity, citability or grounding authority.
6. Unknown provider-visible reference attribution remains `UNKNOWN` when the
   callback contract does not expose per-reference visibility.
7. Incomplete, timed-out, invalid or preflight-rejected executions remain
   distinct terminal outcomes and are not counted as successful analyses.
8. Captures must be bounded, redacted and free of credentials, authorization
   headers, full prompts, raw provider payloads and unbounded repository data.
9. Evaluation artifacts are technical evidence only and must not become trusted
   project knowledge.
10. No production budget, provider, retrieval, prompt, grounding or MCP contract
    change is authorized by this Story.

## In Scope

### Phase A - Read-only workflow audit

- Trace the real OpenCode -> MCP -> Core -> AI Engine -> callback -> persistence
  -> MCP response path.
- Inventory the actual MCP tools, REST endpoints, request and response fields,
  idempotency behavior, polling behavior, authentication/delegation behavior,
  timeout behavior and terminal statuses.
- Reconcile the v1 Story Context protocol, investigation action protocol and
  legacy/deprecated adapters.
- Identify information loss or ambiguity at every boundary, including context
  selection, projection, grounding, provider visibility, incomplete diagnostics
  and returned MCP results.
- Audit correlation IDs, task IDs, snapshot IDs, context digests, projection
  digests, tool-event observability and Context Inspector availability.
- Inspect existing replay and live evaluation infrastructure for reuse,
  determinism, redaction and outcome-classification gaps.
- Produce a read-only audit report and an implementation-level findings list.

### Phase B - Frozen comparative protocol

- Define a frozen manifest containing question, intent, requested files, project
  scope, target revision, expected evidence, expected abstention and evaluation
  version.
- Define two conditions with identical inputs:
  - `DIRECT_CONTEXT_OAUTH`: direct `get_engineering_context` retrieval through
    the real MCP/Core path, used for context availability and budget metrics;
  - `DEVLOG_ASSISTED_OAUTH`: `devlog_story_agent` through the real MCP/Core and
    ChatGPT OAuth path, used for generated-answer quality metrics.
- Use deterministic assignment IDs and idempotency keys. Do not use process-
  randomized identifiers such as Python's built-in `hash()`.
- Run three repetitions per scenario unless an approved environment limitation
  prevents completion.
- Record successful, failed, timed-out, provider-incomplete, preflight-rejected,
  invalid-capture and not-evaluated outcomes separately.
- Store bounded JSONL captures outside the repository and produce an aggregate
  report with capture and revision consistency checks.

### Phase C - Evaluation scenarios

The frozen corpus must include at least:

1. **Targeted implementation lookup**: identify the owner and configuration of a
   concrete dependency or component using a requested file.
2. **Multi-source architecture question**: reconcile an ADR, Story and current
   implementation while preserving the distinction between intent and current
   repository state.
3. **Historical decision reconstruction**: identify what changed and why, while
   distinguishing historical evidence from current behavior.
4. **Insufficient evidence**: ask for an incident, relationship or causal fact
   absent from the authorized context and require correct abstention.
5. **Truncated or budget-pressure context**: require bounded uncertainty and no
   unsupported exact claim when the projection is incomplete.

Negative controls must include an out-of-scope file, a wrong revision, a
superseded document and an unknown evidence reference where the relevant
contract supports those checks.

### Phase D - Metrics and decision report

Report objective metrics separately from human qualitative review:

- evidence recall at candidate, selected, projected and grounded levels;
- requested-file and requested-document survival;
- grounding validity and unauthorized-reference rate;
- unsupported-claim rate;
- correct-abstention rate;
- temporal/current-versus-historical distinction accuracy;
- contradiction detection and unresolved-question preservation;
- selected, discarded, enriched and projected item counts;
- token estimates, budget usage and truncation warnings;
- p50/p95 end-to-end and provider latency;
- completion, timeout, provider-incomplete and preflight-rejection rates;
- capture validity, digest consistency and replay reproducibility;
- MCP tool-event and Core task correlation coverage.

Human review must separately assess faithfulness, relevance, actionability,
clarity and context efficiency. A structurally valid response is not sufficient
evidence of semantic correctness.

## Out of Scope

- Production RAG, hybrid retrieval, embeddings or vector infrastructure.
- Increasing production context, item, enrichment, projection or provider
  budgets.
- Provider replacement, fine-tuning or prompt redesign as an unmeasured fix.
- Generic autonomous-agent or multi-agent framework work.
- Python-owned repository access, retrieval, authorization or context assembly.
- Automatic source, Story, Decision, Event or trusted-knowledge mutation.
- Full MCP or REST contract refactoring.
- LLM-as-judge as a required CI dependency.
- Declaring a live provider benchmark complete when the environment blocks it.

## Acceptance Criteria

- [ ] The canonical audit identifies the real MCP, REST, Core and AI Engine
      contracts and their authentication, polling and terminal-status behavior.
- [ ] Every boundary identifies preserved fields, transformed fields, omitted
      fields and bounded diagnostics, including known information-loss points.
- [ ] Context, projection, task, snapshot, correlation and tool-event identities
      are documented and their consistency rules are tested or explicitly marked
      as an evaluation blocker.
- [ ] The audit distinguishes successful analysis, incomplete provider output,
      timeout, failure, preflight rejection and invalid capture.
- [ ] A frozen comparative manifest defines the five required scenario classes,
      expected evidence, abstention behavior, target revision and repetitions.
- [ ] Baseline and DevLog-assisted conditions use identical scenario inputs and
      deterministic assignment/idempotency identities.
- [ ] Captures are revision-pinned, bounded, redacted, digest-correlated and
      free of credentials, full prompts and raw provider payloads.
- [ ] The report includes evidence recall, grounding, unsupported claims,
      abstention, latency, budget, failure and reproducibility metrics.
- [ ] Provider-visible references remain `UNKNOWN` unless the callback contract
      explicitly provides attribution.
- [ ] Existing fail-closed behavior for scope, revision, grounding, trust and
      incomplete output remains unchanged.
- [ ] Any proposed code or harness fix is separately listed with evidence,
      scope, risk and a human approval gate; no fix is implemented implicitly.
- [ ] Results blocked by unavailable services, credentials or runtime state are
      marked `NOT_EVALUATED`, not treated as passed or failed quality results.
- [ ] The final decision report recommends at most one next bounded Story or
      explicitly concludes that no implementation change is justified.

## Validation Plan

### Repository and contract checks

```bash
git status --short
git diff --check
```

Inspect the complete final audit diff and verify that no production source,
credentials or unrelated workspace changes are included.

### Existing deterministic evaluation checks

```bash
python3 -m pytest ai-engine/evaluations/test_story_context_runtime_readiness.py -q
python3 -m pytest ai-engine/evaluations/test_story_context_policy_comparison.py -q
```

The exact command may be adapted to the available environment. Blocked checks
must be reported as blocked rather than passed.

### Live evaluation checks

Only after explicit human approval:

- verify backend, MCP server and AI Engine revisions;
- verify authenticated MCP access and project scope;
- execute the approved frozen manifest with the approved repetition count;
- validate capture redaction, revision identity, digests and terminal outcomes;
- aggregate only valid captures;
- inspect tool-event/task correlation and bounded incomplete diagnostics.

## Human Decisions Required

- Approve the Story-0210 scope and governing scenario corpus.
- Approve the baseline definition and whether the baseline provider path is
  direct context-only measurement or another explicitly named condition.
- Approve the repetition count and retention location for bounded captures.
- Approve the Phase-A repository audit before any implementation planning.
- Approve the implementation plan before changing code or evaluation harnesses.
- Approve any live evaluation execution that uses external provider capacity.

## Known Limitations

- A live provider result is environment- and model-dependent and cannot by itself
  establish general response quality.
- Provider-visible per-reference attribution is unavailable unless the callback
  contract is extended in a separately approved change.
- Existing evaluation fixtures may reference historical revisions and must not be
  presented as current baselines without revision verification.
- Tool-event observability may differ between HTTP and stdio authentication
  modes; this must be measured rather than inferred.
- The repository currently contains unrelated worktree changes and local branch
  divergence; Story-0210 work must not overwrite or clean them.

## Non-Authorization Notice

This Story does not authorize production behavior changes, live execution,
retrieval architecture changes, budget increases, provider changes, commits,
pushes, merges or pull requests. Each requires explicit human authorization at
the applicable project gate.
