# Story 0037 — Incremental Architecture Knowledge Evolution

## Status

Draft

## Priority

High

## Objective

Implement the first vertical slice of **ADR-050 (Incremental Knowledge Evolution)** for
`ARCHITECTURE_REVIEW` analyses.

Today, repeated architecture analyses are relatively stable because they are grounded by a
deterministic selection pipeline and a bounded prompt contract. That stability is desirable.

However, the current lifecycle still behaves conceptually like:

Repository Evidence
→ Analysis
→ Proposal
→ Validation
→ Trusted Knowledge

and, on the next scan, repeats the same process again without a first-class notion of
knowledge delta.

As a result, repeated architecture analyses can legitimately produce semantically equivalent
knowledge again, because the system currently has no explicit contract for:

* comparing new evidence with relevant existing trusted architecture knowledge;
* expressing `NEW` vs `ENRICHES` vs no meaningful change;
* avoiding redundant reproposals when nothing materially new was learned.

This story introduces the smallest useful incremental-evolution slice:

* architecture analysis only;
* bounded retrieval of relevant existing trusted architecture knowledge;
* structured injection of that existing knowledge into the AI analysis context;
* a minimal delta contract supporting `NEW`, `ENRICHES`, and a no-meaningful-delta behavior;
* reuse of the existing Proposal / Validation / Promotion lifecycle for knowledge that still
  requires human acceptance.

The immediate goal is not to solve every knowledge-lifecycle problem.

It is to make repeated architecture scans more useful by preferring marginal information value
over redundant semantic replay.

## Motivation

DevLog already demonstrates several good properties:

* deterministic repository context selection;
* bounded prompt construction;
* structured AI output contracts;
* controlled proposal validation and promotion per ADR-006;
* semantic preservation during promotion per ADR-049.

What it does **not** yet provide is incremental architectural understanding.

Current repeated architecture analyses can see prior trusted insights, but they do so only as
general historical context. The system does not explicitly frame those insights as:

* existing trusted architecture knowledge;
* candidates for enrichment;
* or evidence that no significant new architecture knowledge exists.

This creates two practical problems:

1. humans may review repetitive architecture proposals that restate what is already trusted;
2. machines may accumulate redundant knowledge records instead of a cleaner history of
   meaningful evolution.

The first slice should therefore prove that DevLog can evolve trusted architecture knowledge
incrementally **without weakening existing validation guarantees**.

## Scope

### In Scope

1. **ADR-050**
   - Add a new ADR formalizing Incremental Knowledge Evolution.
   - Define the semantics of cumulative knowledge, idempotence, knowledge delta, and lifecycle
     ownership.

2. **Architecture analysis only**
   - Apply the slice only to `ARCHITECTURE_REVIEW` / `architecture-overview-v1`.

3. **Bounded relevant existing knowledge selection**
   - Retrieve only project-scoped trusted knowledge relevant to architecture analysis.
   - Keep selection deterministic and bounded.
   - Avoid sending the entire trusted knowledge corpus to the AI Engine.

4. **Structured existing-knowledge context**
   - Extend the selected analysis input with a dedicated structure representing relevant
     existing trusted architecture knowledge.
   - Distinguish this structure from raw historical `selectedInsights`.

5. **Minimal knowledge delta contract**
   - Support:
     - `NEW`
     - `ENRICHES`
     - a no-significant-delta behavior
   - Keep the result structured so the Java Core does not infer lifecycle semantics from free
     text.

6. **Lifecycle reuse**
   - `NEW` and `ENRICHES` outputs continue through the existing proposal, validation, and
     promotion flow.
   - No-significant-delta results must not create artificial proposals requiring human
     validation.

7. **Validation and tests**
   - Add backend and AI-engine tests proving:
     - identical evidence + relevant existing knowledge => no redundant equivalent proposal;
     - new meaningful evidence => enriching proposal possible;
     - no relevant existing architecture knowledge => new proposal possible;
     - project scoping and architecture-only scoping are preserved;
     - rejected proposals do not mutate trusted knowledge;
     - accepted proposals evolve trusted knowledge through the existing lifecycle.

### Out of Scope

* Knowledge Graph or graph database
* Full contradiction lifecycle
* Full supersession / invalidation lifecycle
* Temporal knowledge engine
* All analysis categories
* Frontend redesign
* Project State redesign
* Artifact generation
* Automatic mutation of trusted knowledge by AI
* Automatic acceptance of contradictions or enrichments
* Kiko / OpenClaw / workflow-agent-specific adaptations

## Constraints

* ADR-006 remains authoritative for lifecycle ownership:
  - AI proposes;
  - Java Core validates, persists, and promotes;
  - trusted knowledge is never mutated directly by the AI Engine.
* Existing idempotence and quality guarantees must not be weakened.
* No thresholds, coverage requirements, lint rules, or validation gates may be reduced.
* Existing trusted knowledge must be treated as bounded context, not as prompt noise.
* The vertical slice must preserve project isolation and architecture-intent scoping.
* `NO_SIGNIFICANT_DELTA` behavior must not fabricate a proposal merely to prove the scan ran.

## Impact

Likely affected components:

* `docs/decisions/ADR-050.md`
* architecture-analysis intent and output contract
* `AnalysisContext` / `SelectedKnowledge` incremental knowledge input
* architecture-focused trusted-knowledge selection
* AI prompt / schema / contract validation
* proposal persistence and validation flow for enrichments
* end-to-end lifecycle tests for repeated architecture analyses

Likely relevant existing components:

* `AnalysisContextServiceImpl`
* `KnowledgeSelectionServiceImpl`
* `SelectedKnowledge`
* `IntentCatalog`
* `AiTaskServiceImpl`
* `AiProposalContractValidator`
* `AiTaskResultServiceImpl`
* AI-engine prompt builder and insight schema
* promotion / validation services governed by ADR-006 and ADR-049

## Acceptance Criteria

* AC-1: ADR-050 is added using the repository ADR template and aligned with ADR-006 and recent
  Knowledge ADRs.
* AC-2: For `ARCHITECTURE_REVIEW`, DevLog retrieves only bounded, project-scoped, relevant
  trusted architecture knowledge as existing-knowledge context.
* AC-3: The selected existing knowledge is serialized in a dedicated structured section distinct
  from generic historical context.
* AC-4: The AI contract for architecture analysis supports at least:
  - `NEW`
  - `ENRICHES`
  - no-significant-delta behavior
* AC-5: The Java Core validates the structured delta contract without parsing natural-language
  lifecycle intent.
* AC-6: A repeated architecture analysis with materially unchanged evidence does not create an
  additional semantically equivalent trusted-knowledge record.
* AC-7: A repeated architecture analysis with meaningful new evidence can produce an enrichment
  proposal that still requires the existing validation lifecycle.
* AC-8: A project with no relevant trusted architecture knowledge can still produce a valid
  `NEW` proposal.
* AC-9: Existing project isolation is preserved; architecture knowledge from another project is
  never injected.
* AC-10: Non-architecture trusted knowledge is not injected into the architecture incremental
  context unless explicitly proven relevant by the selected design.
* AC-11: Rejected incremental proposals do not mutate trusted knowledge.
* AC-12: Accepted incremental proposals evolve trusted knowledge through the existing lifecycle
  without bypassing ADR-006 constraints.
* AC-13: Existing repository validation and quality gates pass unchanged.

## Technical Context

Verified current behavior:

* `AnalysisContextServiceImpl` already includes `projectContext.validatedProposals()` in
  `AnalysisContext`.
* `KnowledgeSelectionServiceImpl` already retrieves trusted `Insight` records through
  `InsightRepository.findByProjectIdOrderByCreatedAtDesc(...)` and injects them as
  `selectedInsights`.
* `architecture-overview-v1` already uses context profiles `architecture-v1` and `history-v1`.
* `DeterministicContextIntelligence` already ranks `VALIDATED_INSIGHT` as a preferred
  repository-context layer for architecture analysis.
* The AI prompt builder already serializes `selectedInsights` into the architecture prompt.
* `AiTaskResultServiceImpl` currently persists every valid structured proposal as a new
  `ValidatableProposal`; it does not perform semantic equivalence checks against trusted
  knowledge.
* Current idempotence is technical rather than semantic:
  - deterministic selection digest;
  - deterministic prompt content digest;
  - callback duplicate acknowledgement;
  - repository-evidence deduplication;
  - but no explicit knowledge-delta evaluation.

## Dependencies

* ADR-006 — AI Proposal and Knowledge Promotion Workflow
* ADR-047 — proposal-type-aware validated engineering events
* ADR-049 — Semantic Preservation During Knowledge Promotion
* Story 0035 — Richer Validated Knowledge

## Risks

1. **Overloading generic `selectedInsights` with delta semantics**
   - Mitigation: introduce a dedicated existing-knowledge structure for architecture evolution
     rather than relying on implicit prompt interpretation.

2. **Leaking irrelevant or cross-project trusted knowledge**
   - Mitigation: project-scoped, architecture-scoped, deterministic bounded selection with test
     coverage.

3. **Breaking current structured output guarantees**
   - Mitigation: evolve the contract explicitly on both AI-engine and Java Core sides, with
     schema validation and contract tests.

4. **Prematurely over-designing contradiction or temporal history**
   - Mitigation: keep the first slice limited to `NEW`, `ENRICHES`, and no-significant-delta
     behavior.

## Decisions for validation

1. Existing trusted architecture knowledge should become a dedicated bounded analysis input,
   not only a generic historical insight list.
2. The first vertical slice should support `NEW`, `ENRICHES`, and no-significant-delta
   behavior only.
3. No-significant-delta should not create an empty or synthetic proposal.
4. ADR-050 should be created as part of this Story because the incremental-evolution semantics
   are broader than a local implementation detail.

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
