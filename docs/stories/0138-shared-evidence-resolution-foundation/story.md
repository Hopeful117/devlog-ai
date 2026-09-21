# Story 0138: Shared Evidence Resolution Foundation

## Status

`STORY_0138_IMPLEMENTATION_COMPLETE - STORY_0138_TECHNICALLY_VALIDATED - STORY_0138_ARCHITECTURE_ACCEPTED - FOLLOW_UP_SECURITY_STORY_REQUIRED`

## Architectural Basis

- ADR-063 - Canonical evidence semantics and shared resolution amendment
- ADR-038 - Repository Context Engine
- ADR-068 - Core-owned AI reference resolution

## Objective

Introduce a shared Core-owned evidence-resolution boundary that dispatches
canonical evidence references to specialized evidence-family resolvers.

The boundary must answer what a canonical reference represents. It must not
retrieve candidates, rank evidence, compose context, authorize trust, build
grounding contracts or perform AI behavior.

## Scope

- Define a shared resolution result/error semantic boundary.
- Dispatch references by evidence family/type.
- Integrate specialized resolver families for the smallest useful vertical
  slice, initially Git, revision-pinned documents and persisted domain evidence.
- Preserve canonical identity, source identity, provenance and revision metadata
  in resolution results.
- Distinguish current-state resolution from task-snapshot resolution.
- Reconcile shared failure classifications with existing ADR-068 AI-reference
  classifications without duplicating them.
- Add deterministic resolver tests and no-fallback tests.

## Explicit Non-Goals

- No universal Evidence domain model.
- No resolver-owned retrieval, ranking, relevance or composition.
- No authorization architecture. The boundary may receive an authorization
  context in a later implementation, but this Story must not define principals,
  roles or policies.
- No MCP-specific resolver path.
- No AI provider or prompt changes.
- No RAG, embeddings, vector search or graph traversal.
- No historical data rewrite.

## Resolution Invariants

1. Unknown references fail deterministically.
2. Unsupported evidence families fail explicitly.
3. Ambiguous source identity never selects the first active source.
4. Missing source or revision never silently falls back to another source or
   current HEAD.
5. Missing evidence is distinguishable from evidence that is no longer
   resolvable.
6. A task-scoped AI reference resolves only through its originating mapping
   snapshot and does not bypass the shared boundary with a current-context
   lookup.
7. Resolution preserves canonical identity and evidence-family metadata.
8. Resolution does not imply trust, current authority, relevance or access.
9. Specialized resolvers retain ownership of authoritative domain models.

## Conceptual Failure Classes

The implementation must reconcile existing exception/result models with these
semantic classes:

```text
UNKNOWN_REFERENCE
UNSUPPORTED_REFERENCE_TYPE
AMBIGUOUS_SOURCE
SOURCE_UNAVAILABLE
REVISION_UNAVAILABLE
EVIDENCE_NOT_FOUND
EVIDENCE_NO_LONGER_RESOLVABLE
UNAUTHORIZED
UNSUPPORTED_EXPANSION
```

`UNAUTHORIZED` must remain distinguishable from identity failure even if the
authorization model is implemented later.

## Acceptance Criteria

1. A shared Core resolution boundary dispatches at least Git, document and one
   persisted domain evidence family.
2. Each resolver family retains authority over its own domain or repository
   model.
3. Resolution results preserve reference, source, provenance and applicable
   revision metadata.
4. Ambiguous source, missing revision and unavailable evidence are deterministic
   failures with no silent fallback.
5. Current-state resolution and originating-task snapshot resolution remain
   distinguishable.
6. Existing `AiReferenceResolver` remains the authority for task-scoped AI
   mapping and is not replaced by a public project identity registry.
7. MCP resource handlers can remain thin projections over Core resolution.
8. No resolver service owns retrieval, ranking, composition, grounding policy,
   authorization policy or AI behavior.
9. Focused resolver and integration tests cover all failure classes in scope.

## Expected Tests

- Reference-family dispatch tests.
- Git source/revision resolution tests.
- Revision-pinned document resolution tests.
- Domain entity resolution tests.
- Unknown and unsupported reference tests.
- Multi-source ambiguity tests.
- Missing source/revision/evidence tests.
- No fallback to HEAD, active source, current context or another task.
- Preservation of provenance and revision metadata.

## Implementation Modes

- **LEARN:** trace Spring dependency boundaries, resolver ownership and failure
  semantics before introducing the shared boundary.
- **PAIR:** design the result/error seam and prevent god-service growth.
- **DELEGATE:** implement repetitive resolver adapters and negative test cases
  after the shared boundary is reviewed.

## Dependencies

- Story 0137 reference-family and source-identity classification.
- Existing `DocumentReference`, `RepositoryRevisionScope`,
  `AiReferenceResolver` and `RepositoryEvidenceResolverImpl` behavior.

## Resolved Refinement Finding

Repository inspection found and Slice 0 resolved a contradiction with Story
0137's source-identity baseline. SCA, repository structure collection and MCP
source selection now fail deterministically for ambiguous active sources and
honor explicit source/revision scope.

See `engineering-report.md` for the repository reconstruction, reference-family
matrix, failure and temporal analysis, Java options, implementation slices and
unresolved decisions.

## Compatibility

Existing domain services, MCP resources, AI task mappings and historical
snapshots remain supported. Slices 1 through 6 add the Core resolution seam,
repository adapters and thin REST/MCP projections. Legacy migration, historical
rewriting, complete application authorization and any commit or push remain
outside this Story.

## Final Human Acceptance Decision

The implementation and Core architecture are accepted with the following
explicit limitation:

> Resolution does not imply authorization.
>
> The current deployment does not establish a complete application-user
> authentication and authorization boundary. Evidence Resolution therefore
> operates inside the current trusted deployment boundary until a dedicated
> application-security Story defines authentication, caller identity,
> project/analysis ownership and propagation to protected capabilities.

This is not an Evidence Resolution architecture defect. The focused security
investigation classified the outcome as:

```text
FOLLOW_UP_SECURITY_STORY_REQUIRED
```

The repository established no Spring Security application boundary, no caller
identity reaching `EvidenceResolutionFacade`, and no REST/MCP project
authorization for this capability. Equivalent backend capabilities also lack
an established application-wide authentication/authorization boundary, so
Story 0138 did not bypass a previously enforced application security
guarantee. The endpoint is not approved for arbitrary untrusted exposure.

The follow-up security Story must address caller identity, authentication,
project ownership, analysis ownership where applicable, endpoint
authorization, REST/MCP propagation, direct backend exposure and trusted versus
untrusted deployment boundaries. It must not be implemented by changing
canonical identity, the parser, the facade, family resolvers, or
`AiReferenceResolver`.

Story 0139 remains:

```text
USEFUL_BUT_DEFERRABLE
```

## Current Phase Status

```text
SLICE_0_COMPLETE
SOURCE_POLICY_ALIGNED
SLICE_1_COMPLETE
EXPLICIT_FACADE_SEAM_IMPLEMENTED
SLICE_2_COMPLETE
GIT_DOCUMENT_ADAPTERS_IMPLEMENTED
SLICE_3_COMPLETE
FACT_ADAPTER_IMPLEMENTED
SLICE_4_COMPLETE
SNAPSHOT_BOUNDARY_VERIFIED
SLICE_5_COMPLETE
CONSUMER_PROJECTIONS_IMPLEMENTED
SLICE_6_COMPLETE
REPETITIVE_FAMILY_ADAPTERS_IMPLEMENTED
STORY_0138_IMPLEMENTATION_COMPLETE
STORY_0138_TECHNICALLY_VALIDATED
STORY_0138_ARCHITECTURE_ACCEPTED
KNOWN_APPLICATION_SECURITY_LIMITATION_RECORDED
FOLLOW_UP_SECURITY_STORY_REQUIRED
STORY_0139_DEFERRABLE
HUMAN_GIT_ACTION_REQUIRED
```
