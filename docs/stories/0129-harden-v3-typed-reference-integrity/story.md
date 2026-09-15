# Story 0129: Harden v3 Typed-Reference Integrity Before Expansion

## Status

`REFINED - IMPLEMENTATION NOT AUTHORIZED`

## Baseline

- Branch: `main`
- HEAD: `d05598202413e81f64bc70a5fed171f603e535ef`
- `origin/main`: `d05598202413e81f64bc70a5fed171f603e535ef`
- Story 0128: merged and present in the baseline
- Related ADRs: ADR-006, ADR-060, ADR-063, ADR-064, ADR-065, ADR-067, ADR-068
- Implementation authorization: `NOT AUTHORIZED`
- Production code changes: `NONE`
- Test changes: `NONE`
- Commit/push/merge: `NONE`

## Goal

Correct the confirmed ADR-068 integrity defect in the existing
`architecture-overview-v3` vertical slice before migrating another provider
contract. An `ENRICHES` proposal must target only an authorized existing
architecture Insight, and typed citable projections must fail closed rather
than silently dropping unresolved identities.

## Context

Story 0128 established the first typed provider contract and execution-scoped
resolver. Its v3 Python validation accepts any project-scoped `INSIGHT` as
`targetInsightRef`; Java resolves it and bypasses the existing architecture
knowledge target allow-list. This permits a syntactically valid but semantically
unauthorized enrichment target.

The same projection also silently removes identity fields when a typed lookup
fails. The corrective boundary must harden this already-deployed v3 slice. It
must not migrate Story Context Analysis or mutate v1/v2 semantics.

## Product Capability

Human reviewers receive architecture delta proposals whose enrichment target is
guaranteed to refer to an existing architecture knowledge item from the same
authorized execution context. Invalid or incomplete typed context fails
deterministically instead of producing ambiguous provider input.

## Scope

- Enforce v3 `ENRICHES` target membership in `existingArchitectureKnowledge`.
- Keep the check authoritative in Java Core and add matching Python defensive
  validation where the typed candidate/context contract permits it.
- Make unresolved identities in v3 citable projections fail closed with a
  deterministic Core classification rather than silently dropping the identity.
- Cover facts, observations, architecture knowledge, semantic-section items and
  relationship endpoints that the v3 projection actually emits.
- Preserve the originating `AiTask` mapping snapshot and current resolver
  authority.
- Add focused regression tests for the defect and projection failure behavior.

## Out Of Scope

- Story Context Analysis typed migration.
- Engineering Event or Engineering Decision migration.
- Any global raw-ID migration.
- Changes to `architecture-overview-v1` or `v2` callback semantics.
- New AI reference types, scopes, version registries or mapping tables.
- Retrieval, RAG, embeddings, vector search, ContextPack or Agent runtime.
- Changes to trust promotion, human validation or proposal lifecycle.
- Public exposure of Core mapping metadata.
- Commit, push, merge or Story acceptance.

## Authority Model

- Java Core owns reference allocation, mapping, context membership, grounding
  capability validation and authoritative target authorization.
- Python performs defensive schema and subset validation only.
- Provider references remain opaque `{type, ref, scope}` values.
- A valid reference identifies an authorized context item; it does not make AI
  output trusted knowledge.
- Resolved output still enters `ValidatableProposal(PROPOSED)` and requires
  human validation before promotion.

## Reference Model

Reuse Story 0128's existing `AiReference`, `AiReferenceRegistry`,
`AiReferenceMappingSnapshot` and `AiReferenceResolver`. Do not add speculative
types or scopes. Architecture targets use the existing `INSIGHT/PROJECT`
reference semantics plus an authoritative membership check against the task's
architecture knowledge projection.

## Versioning Model

Preserve the deployed sequence:

```text
architecture-overview-v1 = legacy
architecture-overview-v2 = legacy with synthesis
architecture-overview-v3 = typed
```

Story 0129 hardens v3 in place because it is correcting the current v3
contract. It must not reinterpret historical callbacks, reconstruct null
snapshots or mutate v1/v2 behavior.

## Execution Flow

```text
SelectedKnowledge
  -> v3 typed projection
  -> persisted AiTask mapping snapshot
  -> provider typed output
  -> callback correlationId
  -> originating AiTask snapshot
  -> Core target-membership and identity validation
  -> proposal persistence as PROPOSED
```

If projection identity cannot be resolved from the registry, Core rejects the
execution preparation or callback with a deterministic mapping failure. It does
not omit the identity and continue with a weakened context.

## Failure Semantics

Use existing ADR-068 classifications where applicable:

- `REFERENCE_MAPPING_FAILURE` for an unresolved citable projection or a mapping
  that cannot provide the required domain identity;
- `UNKNOWN_AI_REFERENCE` for a returned reference absent from the task snapshot;
- `REFERENCE_NAMESPACE_MISMATCH` or `REFERENCE_SCOPE_MISMATCH` for malformed
  target namespace/scope;
- `REFERENCE_OUTSIDE_CONTEXT` when a valid Insight is not an authorized
  `existingArchitectureKnowledge` target for this task.

The failure must occur before proposal persistence. Error diagnostics may retain
the internal identity server-side but must not leak Core-only binding metadata
to the provider.

## Legacy Compatibility

- v1 and v2 retain their raw callback and synthesis contracts.
- Historical tasks retain their stored version and existing behavior.
- A null mapping snapshot is never reconstructed as v3.
- Typed fields remain rejected for legacy task versions.
- No persisted proposal is rewritten.

## Security And Trust Boundary

The target membership check prevents cross-context Insight reuse and avoids
confusing visibility with authorization. It does not promote or validate the
truth of the AI proposal. ADR-006's human validation and atomic promotion rules
remain unchanged.

## Acceptance Criteria

1. A v3 `NEW` proposal with no `targetInsightRef` remains valid.
2. A v3 `ENRICHES` proposal targeting an Insight in
   `existingArchitectureKnowledge` resolves and follows the existing
   `PROPOSED` lifecycle.
3. A v3 `ENRICHES` proposal targeting another project Insight fails with
   `REFERENCE_OUTSIDE_CONTEXT` before proposal persistence.
4. A v3 target with the wrong type or scope fails with the corresponding
   namespace or scope classification.
5. The target check uses the originating task snapshot and never current
   selected knowledge as a fallback.
6. Unresolved v3 Fact, Observation, architecture-knowledge, semantic-section or
   relationship citable identities fail closed with `REFERENCE_MAPPING_FAILURE`.
7. No unresolved identity is silently removed while producing a successful v3
   provider projection.
8. v1/v2 and null-snapshot compatibility behavior remains unchanged.
9. Python defensive validation cannot broaden the target or grounding candidate
   universe.
10. No new reference namespace, scope, registry or retrieval infrastructure is
    introduced.
11. AI output remains a non-trusted proposal requiring human validation.

## Test Intent

- Valid v3 architecture `ENRICHES` target resolution.
- Rejection of a selected non-architecture Insight as an enrichment target.
- Rejection of target namespace and scope mismatches.
- Task isolation: target allowed in another task is rejected locally.
- Deleted/unavailable target identity fails before proposal persistence.
- Fail-closed projection for each citable projection family in scope.
- Relationship endpoint identity reuse and unresolved endpoint handling.
- v1/v2 callback compatibility and null-snapshot behavior.
- Python typed subset validation remains unchanged in authority and cannot
  authorize a target absent from Core's allowed context.

## Definition Of Done

- All acceptance criteria are covered by focused Core and AI Engine tests.
- Relevant existing backend and AI Engine suites pass.
- No production path outside v3 is changed.
- No Story Context, Event or Decision contract is migrated.
- ADR-006 and ADR-068 invariants are documented as preserved.
- `git diff --check` passes.
- Implementation remains uncommitted pending human review and acceptance.

## Implementation Subtasks

1. **LEARN** - Trace the distinction between project-scoped Insight identity
   and membership in the task's architecture knowledge projection; validate the
   failure classification against ADR-068.
2. **PAIR** - Define the Core fail-closed projection and target authorization
   seam without changing the registry or introducing a second identity system.
3. **PAIR** - Implement the Java authoritative checks and ensure they run before
   proposal persistence.
4. **DELEGATE** - Add repetitive positive/negative fixtures and JSON contract
   assertions after the semantics are established.
5. **PAIR** - Review Python defensive validation and verify it cannot broaden
   Core authorization or alter retry candidate sets.
6. **LEARN** - Verify task isolation, deleted-entity behavior and the boundary
   between mapping failure and outside-context failure.

## Decision

```text
NEXT = CORRECTIVE_STORY
NEW_ADR_REQUIRED = NO
STORY_0129_IMPLEMENTATION_AUTHORIZED = NO
```

Typed Story Context Analysis is the recommended follow-up after this corrective
Story, because it is the strongest remaining independent grounding path and can
reuse the repaired snapshot/resolution foundation.
