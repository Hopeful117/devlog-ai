# Story 0143: Make the v3 ENRICHES Provider Contract Explicit

## Status

`IMPLEMENTED - AWAITING HUMAN ACCEPTANCE`

## Architectural Basis

- ADR-050 - Incremental Knowledge Evolution Semantics
- ADR-051 - Trusted Knowledge Duplicate and Evolution Policy
- ADR-068 - Typed AI-Facing References and Grounding Namespace Isolation
- Story 0128 - Versioned Provider Typed References and Execution-Scoped Grounding Resolution
- Story 0142 - Make v3 Grounding Authorization Explicit and Diagnosable
- Real `architecture-overview-v3` execution for `agent-presence-device`

## Context

Story 0142 removed the previous v3 grounding authorization blocker. The next
real `architecture-overview-v3` execution reached the downstream proposal
relationship contract and failed while processing `ENRICHES` proposals.

The execution was:

```text
project  = agent-presence-device
workflow = architecture-overview-v3
analysis = 91ae4032-641d-45bc-920c-44d2d51410db
aiTask   = ffac766d-774e-4ef5-8b89-4e7bee84f93b
```

The provider was invoked twice.

On attempt 1, the structured response contained an `ENRICHES` proposal with a
present `INSIGHT` / `PROJECT` target reference. Its complete semantic identity
was not a member of the authorized `existingArchitectureKnowledge` targets.
Python rejected it with:

```text
targetInsightRef is not present in existingArchitectureKnowledge
```

The authorized target set was available to the provider and contained five
architecture Insight references.

The corrective retry received only the membership error. On attempt 2 it
returned four `ENRICHES` proposals without `targetInsightRef`, causing:

```text
targetInsightRef is required when deltaType is ENRICHES
```

The provider-facing v3 prompt does not currently state the complete `NEW`
versus `ENRICHES` target contract. The typed schema exposes
`targetInsightRef` as optional and relies on post-schema validation for the
conditional requirement. The v3 relationship retry context also does not use
the typed `context` shape and therefore does not provide its relationship
specific guidance.

## Objective

Define and implement a coherent v3 provider contract for architecture delta
classification and target selection so that:

- `NEW` and `ENRICHES` have explicit provider-facing semantics;
- every `ENRICHES` proposal identifies one exact authorized existing Insight;
- the provider can identify valid targets without treating every visible Insight
  as target-authorized;
- the structured-output contract represents the conditional target relationship
  as strongly as the provider architecture supports;
- corrective retry feedback is actionable without broadening authorization; and
- the existing Python and Java defense-in-depth validation remains intact.

This Story is a provider-contract hardening slice. It does not change the
meaning of typed references, target authorization, trusted knowledge promotion,
or legacy intent behavior.

## Problem

The current v3 implementation has an inconsistent relationship contract:

```text
Intent semantics       -> deltaType is required
Provider prompt        -> does not explicitly define v3 target selection
Provider schema        -> targetInsightRef is optional in the field shape
Python validator       -> rejects missing and non-member ENRICHES targets
Java Core              -> resolves target and validates project ownership
Corrective retry       -> receives only a generic target membership error
```

This permits the provider to generate both of the observed invalid states:

```text
ENRICHES + present but non-member target
ENRICHES + absent targetInsightRef
```

The issue is not a missing-target data condition. Five valid targets were
available in the real execution. The issue is that v3 does not expose the
relationship contract and retry repair surface with enough precision.

## Runtime Evidence

```text
VALID_ENRICHES_TARGETS_AVAILABLE = YES
VALID_TARGET_COUNT               = 5
ATTEMPT_1                        = ENRICHES + present INSIGHT/PROJECT non-member target
ATTEMPT_2                        = four ENRICHES proposals with absent targetInsightRef
FINAL_STATUS                     = INVALID_LLM_OUTPUT
```

The exact provider values were not retained because the execution used normal
trace level. The persisted validation path proves that attempt 1 passed
structured parsing and namespace/scope checks before exact target membership
failed. Attempt 2 failed the Pydantic `ENRICHES` target validator during
structured response parsing.

## Domain Semantics

`ENRICHES` means that an existing trusted knowledge statement remains valid but
meaningful new information extends or refines it. It creates a new proposal and
later trusted Insight while preserving the existing target and its history.

`NEW` means that the proposed knowledge is genuinely new and does not materially
extend one specific authorized existing architecture Insight.

No material valid delta produces no proposal. The model must not emit an
invalid `ENRICHES` merely because a relationship seems related to existing
knowledge.

## Governing Invariants

1. `ENRICHES` requires one exact authorized existing Insight target.
2. Target identity is the complete tuple `(type, ref, scope)`.
3. For v3, an ENRICHES target has `type = INSIGHT` and `scope = PROJECT`.
4. The target must be an item in `existingArchitectureKnowledge` for the
   originating AI task.
5. `NEW` must omit `targetInsightRef`.
6. A visible contextual Insight is not automatically an authorized target.
7. Titles, content, array position, UUID-only values, text similarity and fuzzy
   matching cannot authorize a target.
8. LLM output is a proposal and never trusted knowledge.
9. Java Core remains authoritative for reference creation, membership,
   resolution, project ownership and acceptance.
10. Python performs defensive structural and semantic validation but does not
    create or resolve domain identity independently.
11. Corrective retry preserves the original candidate universe and one-retry
    policy.
12. No correction may broaden `ENRICHES` authorization to all visible Insights.

## NEW Contract

The provider may choose `NEW` only when the proposed statement is genuinely new
and does not materially extend one specific authorized existing architecture
Insight.

```text
deltaType          = NEW
targetInsightRef   = absent
```

The provider must not use `NEW` as a repair value for an actual enrichment
relationship unless the resulting proposal is independently genuinely new under
the domain semantics.

## ENRICHES Contract

The provider may choose `ENRICHES` only when the proposal materially extends or
refines one specific authorized existing architecture Insight.

```text
deltaType               = ENRICHES
targetInsightRef        = required
targetInsightRef.type  = INSIGHT
targetInsightRef.scope = PROJECT
```

The complete target reference must be copied exactly from the authorized
`existingArchitectureKnowledge` target set. The provider must not construct,
convert, shorten, infer or fabricate the reference.

## Target Authorization

The authoritative target set is:

```text
existingArchitectureKnowledge
```

Each candidate must expose enough information for the provider to associate:

```text
exact typed reference
+
trusted architecture title/content
```

The target set is separate from contextual `selectedInsights`. Contextual
Insights may support understanding but are not valid ENRICHES targets unless the
same exact typed identity is present in `existingArchitectureKnowledge`.

Authorization remains:

```text
(type, ref, scope) in authorized existingArchitectureKnowledge identities
```

When no exact target applies:

- use `NEW` only if the knowledge is genuinely new; or
- emit no proposal when no material valid delta remains.

The implementation must not automatically select a target after generation.

## Provider Prompt Contract

The v3 prompt must communicate, in provider-facing terms, that:

- `NEW` means genuinely new knowledge and must omit `targetInsightRef`;
- `ENRICHES` means a material extension of one supplied trusted architecture
  Insight;
- `ENRICHES` requires `targetInsightRef`;
- the target must be copied exactly from `existingArchitectureKnowledge`;
- only supplied authorized architecture targets may be used;
- contextual `selectedInsights` are not automatically valid targets;
- the target reference must preserve `type`, `ref` and `scope`; and
- no exact target means `NEW` only for genuinely new knowledge, otherwise no
  proposal.

The prompt should present target identity and target content together in a
clearly identifiable comparison surface. It must not expose a second target
registry or imply that the model may resolve domain identity.

## Structured Output Contract

The current typed schema represents `targetInsightRef` as an optional reference
and enforces the conditional relationship in a post-schema validator. The
implementation must evaluate whether a discriminated or conditional structured
schema can represent the following provider contract:

```text
NEW      -> targetInsightRef absent
ENRICHES -> targetInsightRef required
```

The selected representation must preserve compatibility with the Core callback
payload and must not remove defensive validation. If the provider architecture
cannot encode the conditional relationship directly, the prompt and Python
validation must still enforce it fail-closed.

The provider schema must not imply that a structurally valid `INSIGHT/PROJECT`
reference is authorized merely because it has the correct shape.

## Schema Responsibility

The responsibility boundary is:

```text
provider schema
    -> make invalid NEW/ENRICHES relationship shapes difficult or impossible

Python validation
    -> verify exact target membership and relationship semantics defensively

Java Core
    -> resolve the target, verify task and project ownership, and authorize
```

Strengthening the provider schema must not replace exact Python membership
validation or authoritative Java Core validation.

## Corrective Retry Contract

When validation rejects a target, corrective feedback must identify:

- the affected field;
- the rejected complete target identity, without inventing a replacement;
- the rejection reason;
- the `ENRICHES` target invariant; and
- the authorized target source and relevant exact candidates.

For a membership failure, retry context must expose enough of the authorized
target set for the provider to choose an exact target or produce a valid `NEW`
or empty result according to the domain semantics.

Corrective feedback must not:

- authorize a fuzzy replacement;
- silently rewrite the target;
- select a target after generation;
- broaden the candidate universe; or
- add another provider attempt.

## v3 Retry Context

The current relationship retry helper expects legacy top-level fields such as
`selectedFacts` and `existingArchitectureKnowledge`. v3 stores the relevant
typed data under:

```text
selectedKnowledge.context
```

The implementation must adapt relationship retry-context extraction for the v3
typed context shape while preserving v1/v2 behavior. The v3 retry context must
be derived from the same execution-scoped data and target set used for initial
validation.

Required retry choices remain:

```text
ENRICHES + exact authorized target
NEW      + no target, only for genuinely new knowledge
no proposal when no material valid delta remains
```

## Defensive Validation

The implementation must retain:

- Pydantic structural validation;
- Python `ENRICHES` target presence validation;
- Python exact `(type, ref, scope)` membership validation;
- Python rejection of wrong namespace and wrong scope;
- Java Core typed target resolution;
- Java Core target membership and project ownership validation; and
- the existing one-corrective-retry limit.

No provider success may bypass Core validation or promote output directly to
trusted knowledge.

## Functional Requirements

1. Define v3 `NEW` and `ENRICHES` provider semantics explicitly.
2. Require `targetInsightRef` for every v3 `ENRICHES` proposal.
3. Require the exact target to belong to `existingArchitectureKnowledge`.
4. Preserve exact `(type, ref, scope)` target identity.
5. Keep contextual `selectedInsights` distinct from authorized targets.
6. Present valid targets with their typed references and architecture content.
7. Define deterministic behavior when no exact target applies.
8. Represent the conditional relationship in the structured-output contract as
   strongly as the provider architecture supports.
9. Keep Python semantic validation fail-closed.
10. Keep Java Core authoritative for target resolution and ownership.
11. Make corrective target feedback actionable and exact.
12. Extract v3 relationship retry context from the typed `context` shape.
13. Preserve one retry and all v1/v2 behavior.

## Acceptance Criteria

1. v3 explicitly defines `NEW` versus `ENRICHES` provider semantics.
2. Every v3 `ENRICHES` proposal requires `targetInsightRef`.
3. An ENRICHES target must be an exact member of `existingArchitectureKnowledge`.
4. Target authorization uses the complete `(type, ref, scope)` identity.
5. Contextual `selectedInsights` do not become valid targets by visibility alone.
6. `NEW` proposals omit `targetInsightRef`; `NEW` with a target is rejected.
7. The provider-facing contract exposes authorized target references together
   with sufficient architecture content for identification.
8. The v3 prompt explains target selection, exact copying and no-target behavior.
9. The structured-output contract represents the NEW/ENRICHES relationship as
   strongly as supported by the provider architecture.
10. Defensive Python validation remains fail-closed after any schema change.
11. Java Core remains authoritative for target resolution and project ownership.
12. Corrective feedback identifies the field, rejected identity, reason,
    invariant and authorized target source.
13. v3 relationship retry context is extracted from the typed `context` shape.
14. Legacy v1/v2 behavior and retry behavior remain unchanged.
15. Tests accept a valid v3 ENRICHES proposal with an exact authorized target.
16. Tests reject v3 ENRICHES with an absent target.
17. Tests reject a structurally valid but non-member target.
18. Tests reject wrong target namespace and wrong target scope.
19. Tests accept an otherwise valid NEW proposal without a target.
20. Tests reject NEW with a target.
21. Corrective retry tests cover non-member target repair to an exact target.
22. Corrective retry tests cover missing target repair without weakening the
    invariant.
23. Tests cover v3 retry context extraction from nested typed context.
24. Focused AI Engine tests pass.
25. The full relevant AI Engine suite passes.
26. `git diff --check` passes.
27. A real `architecture-overview-v3` replay reaches and passes the ENRICHES
    target relationship stage without authorization broadening.
28. If an unrelated downstream blocker appears during replay, implementation
    stops and reports it without expanding this Story.

## Test Strategy

### Schema And Contract Tests

- Verify the provider-facing representation of NEW and ENRICHES.
- Verify ENRICHES cannot omit its target.
- Verify NEW cannot contain a target.
- Verify serialization remains compatible with the Core callback contract.

### Target Authorization Tests

- Accept an exact `INSIGHT/PROJECT` target from `existingArchitectureKnowledge`.
- Reject an absent target.
- Reject a non-member `INSIGHT/PROJECT` target.
- Reject a wrong namespace with an otherwise familiar ref.
- Reject a wrong scope with an otherwise familiar ref.
- Prove contextual selected Insights do not authorize target selection.
- Prove exact tuple comparison rather than ref-only comparison.

### Prompt Tests

- Verify v3 states NEW versus ENRICHES criteria.
- Verify v3 states that ENRICHES requires an exact target.
- Verify v3 identifies `existingArchitectureKnowledge` as the target source.
- Verify v3 explains no-target behavior.
- Prefer semantic assertions over a single full-prompt snapshot.

### Corrective Retry Tests

- Non-member ENRICHES target receives actionable target feedback and can recover
  with an exact authorized target.
- Missing-target ENRICHES output remains rejected until a valid target is
  supplied.
- Retry can produce NEW without a target when the proposal is genuinely new.
- Retry can produce no proposal when no material valid delta remains.
- Candidate sets, typed identity and retry count remain unchanged.

### Runtime Validation

Replay through the normal Core API path:

```text
project  = agent-presence-device
workflow = architecture-overview-v3
```

Confirm that Core projection, typed mapping, provider processing, target
validation and analysis completion remain intact. Stop on any unrelated
downstream blocker.

## Implementation Boundaries

Primary implementation ownership is the Python AI Engine provider contract and
its tests. Java Core remains authoritative for candidate construction, typed
mapping, target resolution, project ownership and callback acceptance.

The implementation must not alter the meaning of existing typed references,
grounding authorization, or trusted knowledge lifecycle semantics.

## Expected Implementation Surface

These are expected seams, not authorization to modify them in this design-only
Story:

- `ai-engine/app/prompts/insight.py`
- `ai-engine/app/schemas/insight.py`
- `ai-engine/app/services/insight_generation_service.py`
- `ai-engine/app/services/interaction_trace.py` when retry diagnostics require it
- `ai-engine/tests/test_typed_architecture_overview.py`
- `ai-engine/tests/test_insight_generation_service.py`
- relevant prompt and interaction-trace tests

Java Core, Story 0142, Agent Presence, Organizer and S141 are outside the
expected implementation surface.

## Non-Goals

- No legacy v1 redesign.
- No legacy v2 redesign.
- No `groundingRefs` redesign.
- No Story 0142 changes.
- No Java reference authorization redesign.
- No target authorization broadening.
- No candidate ranking or AnalysisContext selection changes.
- No additional retry attempts.
- No automatic target selection after generation.
- No fuzzy target matching or title-based authorization.
- No RAG, retrieval or provider replacement.
- No model replacement.
- No `CommunicationDecisionService` changes.
- No `AgentCommunicationPort` or Agent Presence firmware changes.
- No Organizer or S141 implementation.

## Risks

- A stronger structured schema may expose compatibility differences in provider
  parsing and Core callback serialization.
- Additional target context may increase prompt size and reduce salience if it
  is duplicated rather than composed as one bounded comparison surface.
- Retry feedback may expose more trusted context than necessary if not limited to
  the relevant authorized target candidates.
- Treating all visible Insights as targets would violate ADR-068 and weaken Core
  authority.
- Using NEW to repair an actual enrichment relationship could reintroduce
  redundant trusted knowledge.
- Any change to shared retry code could regress legacy v1/v2 behavior.

## Definition of Done

- This Story's v3 NEW/ENRICHES provider contract is implemented under separate
  implementation authorization.
- Exact target authorization remains `(type, ref, scope)` membership in
  `existingArchitectureKnowledge`.
- Provider prompt, schema, validator and retry semantics agree.
- Focused target, prompt, schema and retry tests pass.
- The full relevant AI Engine suite passes.
- `git diff --check` passes.
- A real v3 replay passes the ENRICHES target relationship stage legitimately.
- No authorization broadening, automatic target selection or fuzzy matching is
  introduced.
- Java Core remains authoritative for resolution and ownership.
- v1/v2 behavior remains unchanged.
- Any unrelated replay blocker is reported without scope expansion.

## Relationship to ADR-050

This Story operationalizes ADR-050's distinction between genuinely new
knowledge and material extension of an existing trusted knowledge statement. It
does not add new delta types or alter trusted knowledge lifecycle ownership.

## Relationship to ADR-051

This Story preserves the preference for no proposal when there is no material
delta, `ENRICHES` when existing knowledge remains valid but incomplete, and
`NEW` only for genuinely new knowledge.

## Relationship to ADR-068

This Story applies ADR-068's Core-owned typed reference model to proposal target
selection. Target authorization remains exact, namespaced and scoped. Visibility
does not imply authority.

## Relationship to Story 0128

This Story is a follow-up hardening slice for Story 0128's typed provider
references and execution-scoped resolution. It preserves the typed wire model,
Core mapping snapshot, one-retry policy and callback contract.

## Relationship to Story 0142

```text
STORY_0142_SCOPE_COMPLETE = YES
```

Story 0142 addressed the v3 `groundingRefs` authorization contract, the context
versus grounding distinction and grounding retry diagnostics. This Story does
not reopen or expand that work. It addresses the downstream proposal
relationship contract for `ENRICHES` targets.

## Relationship to S141

The current execution chain is:

```text
Java projection                     PASS
ProviderAiReference                 PASS
Story 0142 groundingRefs            PASS
OpenAI structured generation        PASS
ENRICHES target relationship        BLOCKED
Analysis completion                 NOT REACHED
CommunicationDecisionService        NOT REACHED
SPEAK                               NOT REACHED
Agent Presence                      NOT REACHED
```

This is currently the only known blocker before analysis completion on this
execution path. A legitimate real v3 analysis completion after this Story may
make S141 ready for its final physical E2E replay. This Story must not modify
S141.

## Dependencies

- ADR-050 and ADR-051 establish NEW and ENRICHES semantics.
- ADR-068 establishes typed identity and Core authority.
- Story 0128 provides the v3 typed reference vertical slice.
- Story 0142 provides the completed grounding authorization boundary.
- The existing one-retry and interaction-trace infrastructure remains in use.

## Implementation Authorization

This Story records design and implementation requirements only. Its existence
does not authorize production code changes, Story acceptance, commit or push.
