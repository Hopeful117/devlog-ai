# Story 0140: Human Evidence Attachment Reproducibility

## Status

`DESIGN PREPARED - IMPLEMENTATION NOT AUTHORIZED`

## Architectural Basis

- ADR-063 Human Context Supremacy amendment
- ADR-063 canonical evidence and shared-resolution amendment
- ADR-006 human validation and trusted-knowledge governance

## Objective

Define and implement the backend persistence boundary for auditable human
evidence attachments using hybrid reproducibility semantics.

An attachment must preserve both the evidence object referenced and enough state
to determine what the human inspected. Full content snapshots remain
category-dependent.

## Scope

- Define a human attachment record associated with an investigation or supported
  human workflow.
- Persist canonical reference, applicable source/revision/state metadata,
  content or representation digest and attachment metadata.
- Resolve and validate the referenced evidence through Core capabilities.
- Distinguish current retrieval from the historical state inspected by the
  human.
- Define category-specific snapshot requirements for Git, repository documents,
  domain knowledge and mutable/external evidence.
- Add audit-oriented failure and stale-state semantics.

## Explicit Non-Goals

- No full immutable snapshot for every category.
- No complete application authorization model.
- No Angular screen redesign.
- No MCP-mediated human workflow.
- No retrieval ranking or search design.
- No automatic trusted-knowledge promotion.
- No universal Evidence entity.

## Invariants

1. Attachments use canonical evidence references, not transport resource URIs as
   their identity.
2. Attachment metadata does not grant authorization or trust.
3. A missing or no-longer-resolvable reference is reported explicitly.
4. Revision/state metadata is preserved whenever the evidence semantics require
   it.
5. Investigation-time state is distinguishable from current retrieval.
6. Category-specific snapshot strength is explicit and testable.
7. Human attachment does not duplicate retrieval or create a second evidence
   identity system.

## Acceptance Criteria

1. A persisted attachment contains canonical reference, applicable state/revision
   metadata, digest and attachment metadata.
2. Git evidence can be reproduced from its immutable identity without requiring
   an unnecessary full content snapshot.
3. Revision-bound repository documents can be reproduced from source/path/revision
   or are explicitly marked unavailable.
4. Mutable or externally changing evidence records sufficient snapshot state to
   detect divergence.
5. Current retrieval and historical attachment inspection are distinguishable.
6. Evidence-not-found, revision-unavailable and no-longer-resolvable states are
   represented without silent fallback.
7. Attachments preserve compatible trust, provenance and temporal semantics.
8. Persistence and service tests prove idempotency, reproducibility and stale
   state behavior.

## Expected Tests

- Attachment persistence and identity tests.
- Git reproducibility tests.
- Revision-pinned document tests.
- Mutable evidence digest/state divergence tests.
- No fallback to current HEAD or another source tests.
- Missing and no-longer-resolvable evidence tests.
- Historical attachment versus current retrieval tests.

## Implementation Modes

- **LEARN:** model persistence, revision provenance, digest semantics and the
  difference between current retrieval and investigation snapshots.
- **PAIR:** define category-specific reproducibility rules and transaction
  boundaries.
- **DELEGATE:** implement repetitive persistence mappings and fixture coverage
  after the attachment semantics are reviewed.

## Dependencies

- Story 0137 canonical reference semantics.
- Story 0138 shared resolution foundation.
- A separately authorized application authorization boundary when attachments
  become user-scoped.

## Compatibility

No existing domain knowledge, AI task snapshot or MCP resource is rewritten.
This Story does not authorize production implementation, migration, commit or
push.
