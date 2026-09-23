# Story 0142: Make v3 Grounding Authorization Explicit and Diagnosable

## Status

`DESIGN PREPARED - IMPLEMENTATION NOT AUTHORIZED`

## Architectural Basis

- ADR-068 - Typed AI-Facing References and Grounding Namespace Isolation
- ADR-063 - Structured context, trust and evidence boundaries
- Story 0128 - Versioned Provider Typed References and Execution-Scoped Grounding Resolution
- Real `architecture-overview-v3` execution for `agent-presence-device`

## Context

The `architecture-overview-v3` vertical slice now passes Core semantic
projection, typed reference mapping, Python request parsing, provider reference
processing, OpenAI invocation and structured output parsing. The real execution
then fails during Python typed grounding validation:

```text
INVALID_LLM_OUTPUT:
groundingRefs contains unauthorized references
```

The failed execution had 40 Fact candidates, no Observation candidates and 60
repository-evidence candidates. The authorized grounding set was sufficient for
the requested architecture synthesis. Context also contained Analysis, Project,
Project Profile and Insight references. Those references are useful to the model
but are not grounding-authorized merely because they are visible.

The exact provider-returned references were not retained because the execution
used normal trace level. The persisted traces prove two failed attempts, but do
not identify the rejected typed tuples. This Story formalizes the contract and
the diagnostics required before another real replay.

## Objective

Make the v3 `groundingRefs` authorization contract explicit to the provider,
deterministic in validation, diagnosable at normal operational trace level and
covered by realistic provider-output and corrective-retry tests.

This Story clarifies the existing v3 authority boundary. It does not broaden
grounding authorization or change Core reference creation and resolution.

## Problem

The current provider model constrains `groundingRefs` to the shape
`(type, ref, scope)`, while runtime authorization is performed by a later
candidate-subset check. The prompt distinguishes some field-specific reference
types but does not state the complete authorized union for `groundingRefs` or
explicitly prohibit every context-only namespace in that field.

The corrective retry receives only the generic message
`groundingRefs contains unauthorized references`. It does not identify the
rejected field, typed reference or rejection reason. Normal traces therefore
cannot explain which visible identity the provider copied into the grounding
field.

## Evidence

The failed task was:

- Analysis: `f8fe2d9a-4daf-4feb-9e50-2fe510c1dfef`
- AI task: `30ac3143-eb7e-4287-91b2-98bce98ed716`
- Intent: `architecture-overview-v3`
- Attempts: one initial generation and one corrective retry
- Final failure: `INVALID_LLM_OUTPUT`

Persisted candidate counts were:

| Candidate set | Count | Authorization |
|---|---:|---|
| Facts | 40 | Authorized for Fact grounding and the v3 grounding union |
| Observations | 0 | No authorized Observation candidates in this execution |
| Repository evidence | 60 | Authorized for evidence grounding and the v3 grounding union |

Context-only namespaces included `ANALYSIS`, `PROJECT`, `PROJECT_PROFILE` and
`INSIGHT`. A raw token can occur in more than one namespace, for example an
`INSIGHT` token in contextual knowledge and an `insight:*` repository-evidence
token. Only the complete typed identity distinguishes them.

## Governing Invariants

1. `LLM output != trusted knowledge`.
2. Visible context does not grant grounding authorization.
3. Grounding validity is exact authorized typed identity.
4. Typed semantic identity is `(type, ref, scope)`.
5. Java/Core remains the authority for reference creation, mapping, resolution
   and authoritative acceptance.
6. Python performs defensive structural and subset validation only.
7. Corrective retry preserves the same candidate universe and cannot broaden
   authorization.
8. Valid reference identity does not imply trust, proposal acceptance or
   promotion to trusted knowledge.

## Grounding Authorization Contract

For `architecture-overview-v3`:

```text
    authorized FACT candidates
  ∪ authorized OBSERVATION candidates
  ∪ authorized REPOSITORY_EVIDENCE candidates
```

An item is authorized only when its exact tuple exists in that union:

```text
(type, ref, scope)
```

The following are context-only by default and must be rejected in
`groundingRefs`:

```text
ANALYSIS
PROJECT
PROJECT_PROFILE
INSIGHT
```

This Story does not authorize any future exception for those namespaces. Such
an exception would require a separate contract decision.

## Field Authorization Contract

| Output field | Authorized references |
|---|---|
| `supportingFactRefs` | Exact references from the Fact candidate set only |
| `supportingObservationRefs` | Exact references from the Observation candidate set only |
| `evidenceRefs` | Exact references from the repository-evidence candidate set only |
| `groundingRefs` | Exact references from the union of Fact, Observation and repository-evidence candidate sets |
| `targetInsightRef` | Existing architecture Insight reference only, for `ENRICHES`; never a grounding reference |

Each field must preserve the complete `type`, `ref` and `scope` tuple. No raw
UUID, partial reference, type substitution, scope substitution,
canonicalization, approximate matching or namespace conversion is permitted.

## Provider Contract

The provider-facing prompt must present two explicit conceptual sections:

```text
CONTEXT REFERENCES
  visible for understanding only

GROUNDING CANDIDATES
  the only references permitted in grounding fields
```

The prompt must state directly that `groundingRefs` may use only the advertised
Fact, Observation and repository-evidence candidate union. It must state that
contextual Analysis, Project, Project Profile and Insight references may inform
the synthesis but are not valid `groundingRefs`.

The provider must copy each authorized reference exactly, including all three
typed fields. An empty grounding array is valid when no authorized candidate
supports a synthesis claim.

## Structured Output Responsibility

The v3 schema remains responsible for structural validation:

- `groundingRefs` is an optional list of `ProviderAiReference` values;
- each value requires an enum-valid `type`, non-empty `ref` and enum-valid
  `scope`;
- the schema must document that runtime candidate authorization is enforced by
  deterministic subset validation;
- dynamic enumeration of per-task candidate values is not required unless a
  separate architecture decision establishes that capability.

The prompt communicates the candidate contract. The validator remains the
authoritative deterministic check for exact membership in the runtime union.
Schema shape validation must not be treated as authorization.

## Namespace Collision Policy

Authorization must use the complete tuple `(type, ref, scope)`, never `ref`
alone. A token such as `insight:<uuid>` may appear as both:

```text
(INSIGHT, insight:<uuid>, PROJECT)
(REPOSITORY_EVIDENCE, insight:<uuid>, REPOSITORY)
```

Those identities are not interchangeable. Tests must prove that accepting one
does not authorize the other.

## Corrective Retry Contract

The existing single-retry policy remains unchanged. A grounding validation
failure must produce structured correction information containing, at minimum:

- attempt number;
- output field;
- rejected `type`, `ref` and `scope`;
- deterministic rejection reason, such as `NOT_AUTHORIZED_FOR_GROUNDING`,
  `WRONG_TYPE`, `WRONG_SCOPE` or `UNKNOWN_REFERENCE`;
- the authorized category for the field.

Correction feedback must not suggest fuzzy replacement or automatically replace
the rejected value. The provider must return a new complete output using an
exact authorized candidate or an empty field where appropriate.

Retry feedback must not dump the complete Core registry when a smaller
deterministic diagnostic is sufficient, and must not broaden the candidate
universe.

## Diagnostics Contract

Normal operational traces must retain enough structured information to diagnose
typed grounding failures without requiring raw prompts or complete provider
responses. At minimum, retain:

- task and correlation identity;
- intent and version;
- attempt and interaction type;
- failed output field;
- rejected typed reference, including `type`, `ref` and `scope`;
- validation stage;
- rejection classification;
- whether the value was visible in context and whether it was in the authorized
  candidate union.

Raw provider responses and full prompts remain subject to existing trace-level,
privacy and retention controls. Rejected typed references are safe to retain as
structured diagnostics when they contain no provider secret or prompt content.
Diagnostic storage must not become a second authorization registry.

## Functional Requirements

1. Preserve the existing Core-created candidate sets and mapping authority.
2. Make the complete `groundingRefs` union explicit in provider instructions.
3. Keep context-only namespaces visible but unauthorized for grounding.
4. Validate exact `(type, ref, scope)` membership fail-closed.
5. Align schema descriptions, prompt instructions and validator semantics.
6. Produce structured correction feedback for unauthorized typed references.
7. Preserve one corrective retry and the original candidate universe.
8. Persist sufficient normal-level diagnostics for future diagnosis.
9. Keep v1 and v2 contracts unchanged.

## Acceptance Criteria

1. `groundingRefs` authorization is explicitly defined as
   `FACT ∪ OBSERVATION ∪ REPOSITORY_EVIDENCE`.
2. Prompt instructions distinguish visible context from authorized grounding
   candidates.
3. `ANALYSIS`, `PROJECT`, `PROJECT_PROFILE` and `INSIGHT` remain unauthorized in
   `groundingRefs`.
4. Authorization uses exact `(type, ref, scope)` identity.
5. Namespace collisions cannot broaden authorization.
6. Schema documentation and provider instructions agree with validator
   semantics.
7. Corrective feedback identifies the field, typed reference, rejection reason
   and authorized category without fuzzy replacement.
8. Normal diagnostics identify rejected typed references without requiring
   unrestricted raw provider-response persistence.
9. Tests cover valid Fact, Observation and repository-evidence grounding.
10. Tests reject contextual Insight, Analysis, Project and Project Profile
    references in `groundingRefs`.
11. Tests reject wrong type, wrong scope, unknown and namespace-collision cases.
12. Retry tests cover invalid output, structured corrective feedback, valid
    corrected output and a retry that remains invalid.
13. Existing fail-closed validation remains intact.
14. v1 and v2 contracts and tests remain unchanged.
15. Java Core candidate construction and authorization remain unchanged unless
    contradictory implementation evidence is separately documented.
16. Focused AI Engine tests, the full relevant AI Engine suite and repository
    validation pass.
17. A normal API replay of `architecture-overview-v3` for
    `agent-presence-device` completes typed grounding validation without
    weakening authorization.
18. If an unrelated downstream blocker appears during replay, implementation
    stops and reports it rather than expanding scope.

## Test Strategy

### Contract Tests

- Verify exact typed round-trip for all grounding namespaces.
- Verify the schema remains structural and does not imply dynamic authorization.
- Verify prompt text explicitly states the `groundingRefs` union.

### Authorization Tests

- Accept authorized Fact, Observation and repository-evidence references.
- Reject contextual Insight, Analysis, Project and Project Profile references.
- Reject wrong type and wrong scope.
- Reject unknown and stale references.
- Prove namespace collisions are distinguished by the complete tuple.
- Prove empty grounding arrays remain valid where no authorized support exists.

### Retry Tests

- Initial invalid typed output produces structured correction feedback.
- Corrected typed output succeeds using an exact authorized candidate.
- A second invalid output remains rejected after the single retry.
- Candidate sets, intent version and authorization remain unchanged across the
  retry.

### Runtime Validation

- Replay through the normal Core API path:

  ```text
  project  = agent-presence-device
  workflow = architecture-overview-v3
  ```

- Confirm projection, typed request, provider processing, grounding validation
  and analysis completion.
- Stop on any unrelated downstream blocker.

## Implementation Boundaries

Primary implementation ownership is the Python AI Engine provider contract and
its tests. Java Core remains authoritative for candidate construction,
authorization, mapping and callback acceptance.

The implementation must not alter the meaning of existing typed references or
accept any context-only reference as grounding.

## Expected Implementation Surface

These are expected seams, not authorization to modify them in this Story:

- `ai-engine/app/prompts/insight.py`
- `ai-engine/app/schemas/insight.py`
- `ai-engine/app/services/insight_generation_service.py`
- `ai-engine/app/services/interaction_trace.py`
- `ai-engine/tests/test_typed_architecture_overview.py`
- `ai-engine/tests/test_insight_generation_service.py`
- relevant interaction-trace tests

Java projection, Java candidate authorization, Agent Presence, Organizer and
S141 implementation are outside the expected implementation surface.

## Risks

- Overly broad prompt feedback could expose unnecessary context or provider
  output.
- Diagnostic persistence could increase token or storage volume.
- Reusing raw reference tokens without typed identity could reintroduce namespace
  ambiguity.
- Changing v3 behavior must not alter v1/v2 compatibility.
- A successful provider response must not bypass Core validation or trust gates.

## Non-Goals

- No legacy v1 grounding redesign.
- No legacy v2 grounding redesign.
- No Java semantic projection redesign.
- No candidate ranking, selection or budget changes.
- No AnalysisContext selection changes.
- No authorization broadening.
- No additional retry attempts.
- No RAG, retrieval or provider replacement.
- No LLM model replacement.
- No Agent Presence firmware or `AgentCommunicationPort` changes.
- No `CommunicationDecisionService` changes.
- No Organizer or S141 implementation.

## Definition of Done

- This Story's contract is implemented under separate implementation
  authorization.
- Focused typed grounding and retry tests pass.
- The full relevant AI Engine suite passes.
- `git diff --check` and repository-standard validation pass.
- A real `architecture-overview-v3` replay for `agent-presence-device` passes
  grounding validation.
- No context-only reference is accepted as grounding.
- No namespace, scope or type validation is weakened.
- v1/v2 behavior remains unchanged.
- Any unrelated downstream replay blocker is reported without scope expansion.

## Relationship to ADR-068

This Story operationalizes ADR-068's explicit namespace, scope, Core authority,
grounding-candidate isolation and retry-boundary invariants. It does not change
ADR-068 or introduce a new reference namespace.

## Relationship to Story 0128

This Story is a follow-up hardening slice for Story 0128's implemented
`architecture-overview-v3` contract. It preserves Story 0128's typed wire model,
Core mapping snapshot, one-retry policy, v1/v2 compatibility and authoritative
Core resolution. It formalizes the remaining provider-facing grounding contract
gap and diagnostics requirement.

## Relationship to S141

S141 remains blocked downstream of analysis grounding validation:

```text
Java v3 projection               PASS
ProviderAiReference processing   PASS
OpenAI invocation                PASS
Typed output parsing             PASS
Grounding authorization          BLOCKED
Analysis completion              BLOCKED
CommunicationDecisionService     BLOCKED
SPEAK                            BLOCKED
Physical Agent Presence          BLOCKED
```

This Story must not modify S141. After this Story is implemented and a
legitimate real v3 analysis completes, S141 may resume its final physical E2E
replay.

## Dependencies

- ADR-068 accepted typed-reference and grounding isolation semantics.
- Story 0128 implemented v3 typed projection and callback resolution.
- Existing `ProviderAiReference` semantic-key validation fix.
- Existing one-retry and interaction-trace infrastructure.

## Implementation Authorization

This Story records design and implementation requirements only. Its existence
does not authorize production code changes, story acceptance, commit or push.
