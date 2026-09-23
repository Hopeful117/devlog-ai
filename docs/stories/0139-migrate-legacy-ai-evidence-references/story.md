# Story 0139: Migrate a Legacy AI Evidence Contract to Typed References

## Status

`DESIGN PREPARED - IMPLEMENTATION NOT AUTHORIZED`

## Architectural Basis

- ADR-068 and its 2026-09-21 AI Reference Boundary clarification
- ADR-063 canonical evidence semantics
- Story 0128 `architecture-overview-v3`
- Story 0129 typed-reference integrity hardening

## Objective

Migrate one remaining model-returned evidence contract from string-based
grounding references to the existing typed `AiReference` architecture.

The first candidate is Story Context Analysis because it still has a separate
string-based `EvidenceRef` contract and can reuse the existing Core mapping and
snapshot-resolution foundation.

## Scope

- Introduce a new versioned Story Context Analysis contract only.
- Project typed Facts, Observations and repository evidence candidates.
- Preserve semantic context separately from grounding candidates.
- Resolve callback references against the originating `AiTask` mapping snapshot.
- Preserve existing Java/Core authoritative validation.
- Preserve legacy Story Context Analysis tasks and persisted results.
- Preserve `EvidenceRef` as the legacy projection for older versions.
- Add namespace, scope, capability and task-isolation diagnostics.

## Explicit Non-Goals

- No mutation of existing SCA contracts.
- No reinterpretation of historical SCA tasks.
- No migration of all AI intents in one Story.
- No new AI reference namespace or scope unless a separate architecture decision
  proves it necessary.
- No changes to evidence selection, ranking or retrieval.
- No MCP or frontend redesign.
- No trust promotion changes.

## Invariants

1. New model-returned references use `{type, ref, scope}` semantics.
2. `AiReference` remains an AI-facing identity, not public project identity.
3. Java/Core creates, maps, resolves and validates references.
4. Provider code never resolves domain identity or repository content.
5. Historical and legacy tasks remain versioned and readable.
6. A valid reference does not imply trust, authorization or proposal acceptance.
7. Corrective retries preserve the same mapping and candidate universe.

## Acceptance Criteria

1. A new SCA intent version has a typed input/output contract.
2. Facts, Observations and repository evidence are structurally distinct
   grounding candidate sets.
3. Wrong namespace, wrong scope, unknown, outside-task and non-grounding
   references fail before persistence.
4. Resolution uses the exact originating task mapping snapshot.
5. Legacy SCA tasks and persisted outputs remain unchanged and readable.
6. Provider payloads do not expose Core-only binding metadata or domain UUIDs as
   the sole grounding identity.
7. Existing Java/Core proposal and analysis trust boundaries remain unchanged.
8. Java, Python and contract tests prove typed round-trip and compatibility.

## Expected Tests

- Typed SCA JSON round-trip tests.
- Candidate namespace isolation tests.
- Task-snapshot resolution tests.
- Legacy task compatibility tests.
- Wrong namespace/scope/outside-context tests.
- Retry candidate-universe preservation tests.
- Core-before-persistence validation tests.

## Implementation Modes

- **LEARN:** trace the SCA callback lifecycle, legacy `EvidenceRef` semantics and
  ADR-068 mapping authority.
- **PAIR:** define the version boundary and Core/Python contract transition.
- **DELEGATE:** add schema fixtures and compatibility matrix tests after the
  version and resolution behavior are agreed.

## Dependencies

- Story 0137 reference-family semantics.
- Story 0138 shared resolution foundation, where the selected SCA evidence
  family requires it.
- Existing Story 0128/0129 typed-reference and fail-closed behavior.

## Compatibility

This Story must use additive versioning. It must not reinterpret historical raw
references or reconstruct missing mapping snapshots. Implementation authorization
is separate and remains required.
