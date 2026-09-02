# Story 0108 — Provide Current-State Synthesis for Architecture Overview

## Status

**IMPLEMENTED_AWAITING_FINAL_HUMAN_REVIEW — PRODUCT_GATE_NOT_PASSED AFTER RELATIONSHIP-AWARE RETRY (0/3 STRONG, 3/3 ACCEPTABLE, 0/3 WEAK)**

## Baseline

- Baseline SHA: `2e849641cf74361d7703e4b3f53609b9c5b3e83e`
- Baseline branch: `main`
- Governing benchmark: `docs/investigations/post-0106-0107-canonical-analysis-product-benchmark.md`
- Governing proposal lifecycle: `docs/decisions/ADR-006.md`
- Governing callback contract: `docs/decisions/ADR-020.md`
- Governing Intent versioning: `docs/decisions/ADR-028.md`
- Governing Intent execution: `docs/decisions/ADR-030.md`
- Governing retrieval/trust boundary: `docs/decisions/ADR-063.md`
- Governing context composition: `docs/decisions/ADR-064.md` (`KEEP_PAUSED`)
- Predecessor: Story 0107 — Deterministic Cross-Analysis Fact Ranking

## Product Evidence

The post-0106/0107 canonical benchmark executed `architecture-overview-v1` three times against one stable project state.

```text
ARCHITECTURE_BENCHMARK_USEFUL_RATE = 0/3
ARCHITECTURE_INFORMATION_STABILITY = STABLE
OUTPUT_CONSISTENCY = HIGH BUT CONSISTENTLY NOT USEFUL
PRIMARY_BOTTLENECK = OUTPUT_SYNTHESIS
```

Each run received stable, meaningful architecture information, including approximately:

```text
40 Facts
4 Observations
10 Insights
5 trusted architecture knowledge items
60 repository-evidence items
50 ARCHITECTURE Semantic Section memberships
```

All three executions correctly generated no new/enriching architecture proposals, but the canonical result did not explain the current architecture or explicitly explain the no-delta conclusion.

## Problem

The current product conflates two different outcomes:

```text
CURRENT-STATE SYNTHESIS
What is the architecture now?

KNOWLEDGE DELTA
What materially new or enriching architecture knowledge should be proposed?
```

`architecture-overview-v1` currently generates only `NEW` or `ENRICHES` Insight proposals. When no material delta exists, `proposals = []` is correct under the existing contract but leaves the user without an architecture overview.

A developer unfamiliar with the repository cannot reliably answer from the result:

- what the important components are;
- what each component does;
- how components interact;
- which boundaries or principles matter;
- whether any genuinely new architecture knowledge was discovered.

## Human Story

As a developer reviewing a project,

I want Architecture Overview to give me a coherent, grounded description of the current architecture even when no new architecture knowledge is proposed,

so that I can understand the system without confusing the answer with knowledge awaiting human validation.

## Goal

Provide a canonical, point-in-time current-state Architecture Overview synthesis and preserve architecture knowledge deltas as separate `ValidatableProposal` candidates governed by the existing human-validation lifecycle.

Conceptually:

```text
one selected Analysis context
        ↓
one architecture interpretation
        ↓
current-state synthesis + optional architecture delta proposals
        ↓                         ↓
canonical Analysis result        ValidatableProposal lifecycle
                                  ↓
                                  human validation
```

The diagram describes product semantics, not a mandated DTO design.

## Selected Design Direction

Subject to the required ADR, use one architecture-specific structured generation that returns:

1. one mandatory, grounded current-state architecture synthesis;
2. zero or more optional `NEW` or `ENRICHES` architecture Insight proposals.

Persist the synthesis as an immutable, Analysis-execution-scoped, non-trusted AI result snapshot owned by the AI task or equivalent existing execution boundary. Project it through the canonical endpoint:

```text
GET /api/v1/analyses/{analysisId}/result
```

Continue persisting architecture deltas only as `ValidatableProposal` records.

The synthesis must be explicitly non-authoritative and non-promotable:

```text
SYNTHESIS != TRUSTED KNOWLEDGE
SYNTHESIS != VALIDATABLE PROPOSAL
SYNTHESIS != AUTOMATIC KNOWLEDGE PROMOTION
```

## Required ADR Gate

Implementation is not authorized until a HUMAN-reviewed ADR decides the durable distinction between an Analysis answer and a proposal.

The ADR must resolve at least:

- authority and trust semantics of AI-generated current-state synthesis;
- compatibility with ADR-006's statement that AI-generated outputs are `ValidatableProposal` objects;
- callback and atomic persistence implications under ADR-020;
- explicit reconciliation or supersession of ADR-063 section 29, which states that AI synthesis remains an untrusted `ValidatableProposal`;
- explicit reconciliation or supersession of ADR-064's relation preserving the rule that AI outputs remain proposals, without resuming its context-composition sequence;
- immutable execution-scoped persistence and retention;
- grounding and provenance requirements;
- canonical result projection and future-consumer parity;
- whether synthesis may be reused as future model context without validation;
- partial-output behavior when synthesis and proposals validate differently;
- the Intent-version transition required by ADR-028.

No ADR is created by this Story materialization.

## Architecture Constraints

### Trust boundary

- The Java Core remains authoritative for workflow state, persistence, validation, proposal lifecycle, and trusted knowledge.
- The AI Engine may interpret, synthesize, explain, and generate proposals.
- Current-state synthesis must never create or mutate an `Insight`, `EngineeringDecision`, or other trusted artifact.
- Only an accepted `ValidatableProposal` may enter the existing promotion lifecycle.

### Canonical construction

- One Analysis execution produces one canonical persisted result representation.
- REST, future MCP, future agent, and Workspace consumers must read the same result semantics.
- No frontend-only, REST-only, MCP-only, or regenerated-on-read synthesis pipeline is permitted.

### Context boundary

- Existing deterministic collection, selection, Semantic Sections, and architecture-knowledge selection remain authoritative.
- ADR-064 remains paused.
- Story 0107 Fact ranking remains unchanged.
- No new retrieval or context-composition architecture is introduced.

### Intent boundary

- Current `architecture-overview-v1` semantics are immutable under ADR-028 because the proposed behavior changes prompt semantics and output schema.
- The default implementation direction is a new Architecture Overview Intent version, with the exact canonical transition decided by the prerequisite ADR.
- `describe-project-v1` and `analyze-engineering-decision-v1` must remain behaviorally unchanged.
- A new AI task type is not justified: intent-specific output handling may coexist under `INSIGHT_GENERATION`.

### Scope boundary

- The implementation should remain architecture-specific unless the prerequisite ADR proves a minimal shared answer envelope is required.
- Do not create a generic free-form Analysis answer framework.
- Frontend rendering is a separate Story.

## Current-State Synthesis Semantics

When supported by selected context, the synthesis should communicate:

- major components;
- component responsibilities;
- important relationships;
- architectural boundaries;
- data or control flow;
- runtime or deployment topology;
- persistence responsibilities;
- important architectural principles and relevant trusted decisions;
- explicit uncertainty and unavailable information.

Not every category is mandatory when evidence is absent. Claims must be grounded in the selected point-in-time context.

## No-Delta Semantics

Given sufficient current architecture evidence and no genuinely new or enriching architecture finding:

```text
CURRENT-STATE SYNTHESIS = PRESENT AND USEFUL
DELTA PROPOSALS = []
DELTA CONCLUSION = NO MATERIAL DELTA
```

The result must not collapse to an empty proposal count or force known architecture into a duplicate proposal.

## Delta Semantics

Given sufficient current architecture evidence and a genuinely new or enriching architecture finding:

```text
CURRENT-STATE SYNTHESIS = PRESENT
DELTA PROPOSALS = ONE OR MORE SUPPORTED CANDIDATES
```

Each delta remains independently reviewable and cannot become trusted knowledge without the existing human-validation and promotion transaction.

## Insufficient-Evidence Semantics

When selected context cannot support a useful architecture description, the synthesis must state that supported architecture information is insufficient and identify bounded limitations where possible.

The system must not invent components, relationships, boundaries, principles, or causality to satisfy the output shape.

## Failure Semantics

The selected one-call design creates a deliberate question when synthesis and proposal validity diverge.

Until the prerequisite ADR decides otherwise, the conservative implementation baseline is one atomic governed output:

- validate synthesis and proposals before callback persistence;
- use the existing bounded corrective-generation behavior for invalid output;
- do not persist a partially validated callback;
- fail the task if the combined output remains invalid.

This preserves current callback atomicity but can withhold an otherwise valid synthesis when an optional proposal remains invalid. The ADR must explicitly accept this tradeoff or define a governed partial-success model before implementation. The implementation team must not invent partial-output semantics locally.

## Acceptance Criteria

### AC1 — Current-state synthesis

Given sufficient supported architecture information, when the canonical Architecture Overview Intent completes, then the canonical Analysis result contains a coherent current-state architecture overview explaining supported components, responsibilities, relationships, boundaries, and important principles.

### AC2 — No-delta result remains useful

Given sufficient current architecture knowledge and no new/enriching architecture delta, when Architecture Overview completes, then current-state synthesis is present and useful, delta proposals are empty, and the canonical result explicitly states that no material architecture delta was found.

### AC3 — Delta result remains separated

Given a genuinely new or enriching supported architecture finding, when Architecture Overview completes, then current-state synthesis and one or more separate architecture delta proposals are present.

### AC4 — Proposal governance

Architecture delta proposals continue through `ValidatableProposal -> Human Validation -> Trusted Insight`. The synthesis does not enter this lifecycle and cannot promote itself.

### AC5 — Trust boundary

Current-state synthesis is visibly and structurally non-trusted, does not create or modify trusted project knowledge, and is not treated as canonical project state.

### AC6 — Conservative grounding

Every substantive architecture claim is supported by the selected point-in-time project context. Unsupported categories are omitted or represented as uncertainty.

### AC7 — Insufficient evidence

When architecture evidence is insufficient, the canonical result communicates insufficient supported information instead of hallucinating an overview.

### AC8 — Canonical persisted retrieval

The same point-in-time synthesis is retrievable after completion through `GET /api/v1/analyses/{analysisId}/result` without another model call or reconstruction from current mutable project state.

### AC9 — Existing delta semantics

Existing conservative `NEW`/`ENRICHES` validation remains effective. Existing trusted architecture is not rediscovered as a new proposal merely because it appears in synthesis.

### AC10 — One-call target and explicit failure policy

Synthesis and delta detection use one structured model call in the valid path. Combined-output validation and failure behavior follow the prerequisite ADR and are tested explicitly.

### AC11 — Compatibility and intent isolation

Historical results remain readable. `describe-project-v1` and `analyze-engineering-decision-v1` behavior remain unchanged. No new task type is introduced unless contradictory implementation evidence receives separate HUMAN approval.

### AC12 — Product benchmark

Implementation validation includes at least three fresh canonical Architecture Overview executions against one stable project state. `CURRENT_STATE_SYNTHESIS_QUALITY` and `DELTA_CORRECTNESS` are assessed separately. A developer unfamiliar with DevLog can answer the four architecture questions and determine whether any new architecture knowledge was discovered.

## Planned Scope

Subject to ADR approval, expected implementation areas are limited to:

- versioned Architecture Overview Intent/output semantics;
- architecture-specific AI structured output and prompt behavior;
- AI callback representation of the synthesis;
- Core validation and immutable execution-scoped persistence;
- canonical `AnalysisResult` projection;
- focused AI Engine and backend tests;
- three-run canonical product validation.

## Explicit Non-Goals

- frontend implementation;
- Story 0106 prompt tuning or general prompt optimization;
- Story 0107 Fact ranking changes;
- Describe Project grounding-reference reliability;
- model-facing identity aliases or normalization;
- Engineering Decision eligibility or consequence validation;
- documentation overflow or selection policy;
- Semantic Section redesign;
- ADR-064 resumption;
- RAG, embeddings, vectors, or retrieval redesign;
- DevLog Agent or Workspace orchestration;
- MCP launch or MCP-specific result construction;
- automatic trust or knowledge promotion;
- generic Analysis answer framework beyond the minimum ADR-approved transport boundary.

## Dependencies

- HUMAN approval of the prerequisite ADR.
- Current Story 0106 and Story 0107 baseline.
- Existing selected architecture knowledge and Semantic Sections.
- Existing callback idempotency and proposal lifecycle.
- Existing canonical Analysis result endpoint.

## Deferred Product Problems

```text
DESCRIBE_PROJECT_GROUNDING_RELIABILITY = DEFERRED_SEPARATE_PRODUCT_PROBLEM
MODEL_FACING_IDENTITY_NORMALIZATION = PLAUSIBLE_SOLUTION_CANDIDATE / NOT_PROVEN_ROOT_CAUSE / OUT_OF_SCOPE
DECISION_ELIGIBILITY = OUT_OF_SCOPE / MONITOR SEPARATELY
DOCUMENTATION_OVERFLOW = OUT_OF_SCOPE
```

## Validation Strategy

Future implementation must use focused contract, callback, persistence, result-projection, and compatibility tests, followed by at least three canonical runtime executions on a stable project state.

No implementation benchmark is run during Story materialization.

## Human Review Gate

HUMAN design review must decide:

1. whether to approve the synthesis/proposal distinction;
2. whether to authorize an ADR and its required decisions;
3. whether the default new-Intent-version direction satisfies product transition needs;
4. whether callback atomicity or governed partial success is required;
5. whether the proposed persistence owner is correct;
6. whether the architecture-specific scope is sufficiently minimal.

Implementation remains unauthorized.

## Lifecycle State

- Story materialization: completed
- Repository analysis: completed
- Implementation plan: completed
- Prerequisite ADR: required, not created
- Human design review: required
- Implementation: not authorized
- Verification: not started
- Commit: not authorized
- Push: not authorized
- Merge: human-only

Terminal state:

`ARCHITECTURE_OVERVIEW_SYNTHESIS_REQUIRES_ADR_HUMAN_REVIEW`
