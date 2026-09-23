# Story 0144: Separate the Production Confidence Provider Contract

## Status

`IMPLEMENTED - AWAITING HUMAN ACCEPTANCE`

## Architectural Basis

- Story 0112 - DevLog Engineering Story Context Agent
- Story 0132 - Evidence-Grounded Causal Interpretation
- Story 0142 - Make v3 Grounding Authorization Explicit and Diagnosable
- Story 0143 - Make the v3 ENRICHES Provider Contract Explicit

## Context

The production Story Context Analysis path passed the rich internal
`StoryContextAnalysisResult` directly to the OpenAI structured-output parser.
Its internal confidence representation is an object containing `level` and
`rationale`, while the established Core callback wire representation is the
scalar level enum `HIGH`, `MEDIUM`, or `LOW`.

Real OpenAI executions returned scalar confidence values despite the strict
object schema. The provider boundary must therefore be explicit rather than
accepting invalid output or weakening validation.

## Scope

Introduce a production provider result model with a bounded scalar confidence
enum and a deterministic adapter to the richer internal result model. Keep the
existing Core callback representation and confidence semantics unchanged.

The provider path must use the provider result model for strict structured
output, then adapt it before internal validation and callback serialization.

## Acceptance Criteria

1. Production provider confidence is explicitly limited to `HIGH`, `MEDIUM`,
   and `LOW`.
2. The OpenAI structured-output schema exposes confidence as a scalar enum,
   not the internal `Confidence` object.
3. A provider result deterministically adapts to internal `Confidence`, with
   the exact level preserved and an explicitly empty rationale.
4. Unsupported confidence values fail deterministically and are not normalized.
5. The canonical Core callback remains the scalar confidence representation.
6. Strict structured output remains enabled.
7. Prompt and corrective-retry schema guidance use the provider representation.
8. The provider/internal boundary does not change grounding, causal, v3
   `ENRICHES`, or target authorization semantics.
9. Production-boundary tests cover schema generation, all valid levels,
   invalid levels, adaptation, callback serialization, and retry behavior.
10. Existing evaluation behavior remains valid.
11. Focused and full AI Engine tests pass, and `git diff --check` passes.
12. A real `architecture-overview-v3` replay passes provider parsing and
   confidence adaptation, or stops at a separately reported blocker.

## Out of Scope

- Story 0142 grounding authorization changes.
- Story 0143 `NEW`/`ENRICHES` or `targetInsightRef` changes.
- Confidence inference, scoring, or semantic promotion.
- Core callback redesign.
- Additional provider retries or malformed JSON repair.
- Agent Presence, S141, firmware, mDNS, Docker discovery, or networking.
- Provider or model replacement.

## Implementation Boundary

Python owns the provider DTO and deterministic adapter. Java Core remains the
authority for callback acceptance and trusted lifecycle semantics.
