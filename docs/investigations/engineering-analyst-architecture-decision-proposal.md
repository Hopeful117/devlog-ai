# Architecture Decision Proposal: DevLog Engineering Analyst

## Status

`CONDITIONAL PROPOSAL - NOT AN ACCEPTED ADR`

This proposal is intentionally conditional on the A/B/C benchmark. It does not
replace ADR-067, authorize production enablement, or create a new ADR.

## Decision Question

Should DevLog continue optimizing only the initial composed context, or add an
authorized bounded investigation capability that can verify concrete evidence?

## Proposed Minimal Architecture If C Wins

```text
REST/MCP request
  -> Java Core authorization and immutable revision scope
  -> initial Story Context projection
  -> Python plan proposal
  -> Java Core capability validation
  -> Core search/read/history execution
  -> canonical evidence ledger and grounding update
  -> Python hypothesis/verification loop
  -> Python structured synthesis
  -> Java Core final audit and non-trusted snapshot
```

Reuse the current Story 0184 v2 contracts and the existing V4 evaluation tool
semantics where their boundaries are compatible. Do not introduce a generic
agent framework, vector store, arbitrary shell access, direct Python repository
access, or persistent conversational memory.

## Authority Matrix

| Responsibility | Java Core | Python AI Engine |
|---|---|---|
| Authentication/authorization | Authoritative | None |
| Project, source, Story, revision scope | Authoritative | Consume only |
| Capability registry and limits | Authoritative | Propose within contract |
| Repository/Git/document access | Execute and project | Request only |
| Evidence identity, trust, provenance, citability | Authoritative | Interpret metadata |
| Plan and hypotheses | Validate envelope/budget | Formulate |
| Investigation orchestration | Own state, idempotency, stop policy | Drive bounded decisions |
| Synthesis | Validate claims/references | Generate |
| Persistence and promotion | Persist non-trusted snapshot; human promotion only | No trusted persistence |

## Incremental Boundary

1. Keep A enabled and unchanged by default.
2. Evaluate the current Story 0184 action path as a non-default treatment.
3. Add only missing evidence families after a benchmark case demonstrates the
   need; do not generalize the capability registry speculatively.
4. Enable C behind an explicit capability/configuration boundary only after a
   human decision on quality, safety, and operations.
5. Re-evaluate hybrid retrieval only if deterministic search/read misses
   answer-critical candidates after strict Core filtering.

## Risks and Controls

| Risk | Control |
|---|---|
| Context anchoring causes C to miss contrary evidence | Blind B/C comparison, contradiction cases, explicit independent search allowance |
| Retrieval loop becomes unbounded | Core-owned step/call/byte/token/time/provider budgets |
| Model treats retrieved text as proof | Canonical references, trust metadata, final Core grounding audit |
| Revision mixing | Every action and result bound to one authorized revision; reject mismatch |
| Agent gains broader evidence than human | ADR-063 Human Context Supremacy; same authorized evidence universe |
| New generic orchestration architecture | Small typed action registry; no arbitrary tools/framework |
| AI output becomes trusted | Preserve ADR-006 and ADR-067 non-trusted snapshot semantics |
| Cost/latency increases without quality gain | Actual usage measurement and conditional decision rules |

## Acceptance Conditions For Future ADR Consideration

- C materially improves the primary quality endpoint over A and B, or resolves
  a clearly identified failure class with no unacceptable safety regression.
- The effect is present across more than one question category.
- Technical failures are not the explanation for the apparent advantage.
- Evidence and uncertainty scores improve, not only prose preference scores.
- The minimal Core/Python boundary remains enforceable and observable.
- A human validates the benchmark oracle, results, and operational trade-offs.

If these conditions fail, retain A and selectively improve composition,
projection, grounding salience, or provider/runtime reliability instead.
