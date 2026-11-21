# Story 0220 - Revision-Pinned Runtime Loss and Provider Outcome Evaluation

## Status

IMPLEMENTED - READY FOR HUMAN REVIEW.

This Story is the third planned Story of the `agent-response-reliability` epic.
It is a new evaluation and observability slice that reuses the bounded loss
ledger delivered by Story 0178 and the explicit scope/freshness contract from
Story 0219. It does not authorize production behavior changes, budget changes,
retrieval changes, commit, push, or merge.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Objective

Measure, on revision-pinned Story Context executions, where information is lost
and how the execution terminates at the provider boundary:

```text
Core selection -> enrichment -> projection -> grounding -> prompt preflight
-> provider execution -> callback terminal state
```

The Story must identify the dominant bottleneck without changing retrieval,
ranking, budgets, grounding authority, or the Java Core/Python boundary.

## Problem

Story 0178 provides bounded Core-side `lossAccounting`, but its provider-visible
outcome remains unknown until an interaction trace or callback outcome is
available. Story 0219 now makes project, source, revision, freshness, and
provenance explicit. The next missing capability is a reproducible evaluation
that correlates those identities with the terminal execution outcome.

Without this correlation, the system can distinguish Core truncation from
provider failure in principle but cannot reliably determine, for a concrete
revision-pinned execution, whether the dominant limitation was selection,
enrichment, projection, grounding, preflight, or provider execution.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Persist a Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0178 - Story Context Loss-Aware Composition and Projection
- Story 0209 - DevLog Agent Response Quality and Evidence Grounding
- Story 0219 - Freshness, Scope and Provenance Explicitness
- `docs/evaluation/answer-quality-benchmark-specification.md`
- `docs/investigations/story-0177-composition-enrichment-projection-investigation.md`
- `docs/investigations/story-context-truncation-and-provider-incomplete-response.md`

## Architectural Constraints

1. Java/Core remains authoritative for scope, revision, context construction,
   selection, grounding, preflight, and callback acceptance.
2. Python remains responsible for probabilistic execution and defensive output
   validation only.
3. Evaluation artifacts remain outside trusted project knowledge and production
   proposal workflows.
4. Existing `lossAccounting`, context digests, projection digests, grounding
   allow-lists, freshness metadata, and provenance identities remain authoritative.
5. Provider-incomplete output remains terminal and is never treated as a
   successful analysis.
6. Runtime captures and diagnostics remain bounded and non-sensitive. They must
   not contain credentials, full prompts, unbounded repository payloads, or raw
   incomplete provider output.
7. `NOT_EVALUATED` is required when technical execution, capture integrity, or
   semantic eligibility is insufficient for a quality conclusion.
8. No RAG, embedding, vector search, autonomous retrieval loop, or budget
   increase is introduced.

## In Scope

- Reuse the Story 0178 loss ledger and Story 0219 execution identity fields.
- Define a bounded, revision-pinned runtime capture for Story Context execution.
- Correlate loss accounting with terminal execution outcomes:
  - `AUTHORIZED`;
  - `PROJECTION_REDUCED`;
  - `REJECTED`;
  - `COMPLETED`;
  - `PROVIDER_INCOMPLETE`;
  - `PROVIDER_TIMEOUT`;
  - `VALIDATION_FAILURE`;
  - `NOT_EVALUATED`.
- Distinguish selection, enrichment, projection, grounding, preflight, and
  provider-terminal losses.
- Measure selected, projected, grounded, provider-visible, and terminal outcome
  dimensions separately.
- Reuse the ADR-066 replay harness for deterministic provider-free scenarios.
- Support optional bounded live captures without making them part of the
  deterministic baseline.
- Evaluate targeted, broad, multi-source, insufficient, and deliberately
  truncated scenarios.
- Produce a decision report identifying the dominant bottleneck and whether a
  later corrective Story is justified.

## Out of Scope

- Changing retrieval, ranking, selection, enrichment, projection, or budgets.
- Adding RAG, embeddings, vector search, or a new semantic candidate producer.
- Changing the Java Core/Python ownership boundary.
- Automatic model-quality judgment or LLM-as-judge scoring.
- Treating provider-visible evidence as known when it cannot be measured.
- Persisting a new domain entity without a separate contract decision.
- Promoting evaluation results, AI output, or runtime captures to trusted
  knowledge.
- Broad MCP, REST, frontend, Docker, OAuth, or unrelated benchmark changes.

## Acceptance Criteria

- [ ] Every evaluated capture has a verifiable project, source, and immutable
      repository revision, or is marked `NOT_EVALUATED`.
- [ ] Existing `lossAccounting` is correlated with the execution terminal state
      without duplicating or replacing the ledger.
- [ ] Core selection reduction, projection reduction, grounding rejection,
      preflight rejection, provider timeout, provider incomplete output, and
      validation failure remain distinct outcomes.
- [ ] Provider-incomplete output is never counted as a successful response or
      persisted as a successful analysis.
- [ ] Provider-free scenarios are deterministic, revision-pinned, and digest
      stable.
- [ ] Technical failures, invalid captures, and missing semantic eligibility are
      reported as `NOT_EVALUATED` for semantic quality and excluded from quality
      denominators.
- [ ] Optional live captures are bounded, explicitly separated from the
      deterministic baseline, and never presented as baseline evidence unless
      their configuration and identity are frozen.
- [ ] The evaluation report identifies the dominant bottleneck among selection,
      enrichment, projection, grounding, preflight, and provider execution.
- [ ] The report states whether a later corrective Story is justified without
      authorizing that correction.
- [ ] No capture or diagnostic exposes secrets, full prompts, unbounded
      repository content, or raw incomplete provider output.
- [ ] Existing snapshot and interaction-trace surfaces are reused where safe;
      no new persistence or public contract is added without explicit approval.
- [ ] No production trusted knowledge, proposal lifecycle, retrieval policy,
      budget policy, or authority boundary changes.

## Expected Deliverables

- Versioned runtime-capture schema or evaluation artifact contract.
- Deterministic provider-free runtime-loss scenarios and expected outcomes.
- Optional bounded live-capture adapter with explicit `NOT_EVALUATED` handling.
- Correlation and validation logic in the evaluation boundary only, unless
  Repository Analysis proves an additive Core diagnostic is required.
- Structured and human-readable evaluation report.
- Decision report identifying the next measured bottleneck.
- Documentation reconciliation report recording any required updates.

## Validation Plan

### Backend

```bash
./backend/mvnw -pl backend -am test -Dtest=ContextLossLedgerTest,StoryContextCoreIntegrationTest,StoryContextAnalysisQueryServiceTest
```

### AI Engine and Evaluation

```bash
python3 -m pytest ai-engine/tests/test_evaluation_harness.py -q
python3 -m compileall -q ai-engine/evaluations ai-engine/tests
```

### Compatibility and Hygiene

```bash
./backend/mvnw -pl mcp-server -am test -Dtest=StoryContextAnalysisToolTest
git diff --check
```

Every unavailable, blocked, or non-executed check must be reported as such and
must not be counted as passed.

## Human Decisions Required

1. Approve this Story definition and its relationship to Stories 0178, 0209,
   and 0219.
2. Decide whether detailed runtime captures are evaluation-only artifacts or may
   also be retained in existing immutable snapshots.
3. Approve the terminal-state vocabulary and `NOT_EVALUATED` denominator rules.
4. Decide whether optional live captures are required for Story acceptance or
   may remain a documented follow-up.
5. Approve the Repository Analysis before an Implementation Plan is created.

## Gates

This Story requires:

1. approval of the Story definition;
2. Repository Analysis;
3. approval of the Repository Analysis;
4. Implementation Plan;
5. approval of the Implementation Plan before implementation.

The existence of this artifact does not satisfy any later gate.

## References

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)
- [Story 0178](../0178-story-context-loss-aware-composition/story.md)
- [Story 0209](../0209-agent-response-quality-evidence-grounding/story.md)
- [Story 0219](../0219-explicit-freshness-scope-provenance/story.md)
- [ADR-066](../../decisions/ADR-066.md)
- [ADR-079](../../decisions/ADR-079.md)
