# Story 0128: Versioned Provider Typed References and Execution-Scoped Grounding Resolution

## Status

`IMPLEMENTED - AWAITING HUMAN ACCEPTANCE`

## Baseline And Governance

- Branch: `main`
- `HEAD`: `d831278cb39c7346f4960b059d3862537b043454`
- `ORIGIN_MAIN`: `d831278cb39c7346f4960b059d3862537b043454`
- Governing ADR: ADR-068, `Accepted`, committed at `HEAD`
- Story 0127 Core implementation: present in the baseline
- Story 0127 documentation: present as worktree/untracked refinement artifacts; its absence from earlier committed history must not be interpreted as absence of implementation
- Implementation authorization: `GRANTED`
- Production code changes in this refinement: `PRESENT`
- Commit/push: `NONE`

Pre-existing worktree changes are preserved: generated files under
`devlog-contracts/target/`, untracked `docs/discoveries/`, and the untracked
Story 0127 documentation directory.

## Goal

Prove ADR-068 end to end for exactly one existing production intent:
`architecture-overview`.

The Core allocates typed references from the authorized projection, sends them
to the typed provider contract, receives the same typed references in output,
and resolves them through the immutable mapping snapshot belonging to the
originating `AiTask` before any proposal-domain operation.

```text
SelectedKnowledge
  -> Core AiReferenceRegistry
  -> typed architecture-overview provider context
  -> Python/Pydantic typed output
  -> callback correlationId
  -> AiTask exact mapping snapshot
  -> Core namespace/scope/grounding validation
  -> domain Fact/Observation/evidence identity
  -> existing PROPOSED ValidatableProposal lifecycle
```

This Story changes the provider boundary for one versioned intent. It does not
make AI output trusted knowledge.

## Repository Reality

Story 0127 implemented `AiReference`, `AiReferenceType`, `AiReferenceScope`,
`AiReferenceRegistry`, `AiReferenceRegistryFactory`, and the nullable JSONB
`AiTask.aiReferenceMappingSnapshot`. The registry is created from the same
authorized `SelectedKnowledge` used by the standard Analysis path and is
currently not serialized into provider input.

The current production path is still legacy:

```text
SelectedKnowledge
  -> SelectedKnowledgePromptProjectionService.toMap()
  -> PromptRequest.selectedKnowledge
  -> InsightPromptBuilder
  -> InsightGenerationOutput
  -> AiProposalResult.supportingFactIds/supportingObservationIds/evidenceReferences
  -> AiTaskResultServiceImpl
  -> repository lookup by raw UUID/current selected snapshot
  -> ValidatableProposal
```

The current exact intent catalog contains:

| Key | Intent id | Version | Prompt template | Output shape |
|---|---|---|---|---|
| `architecture-overview-v1` | `architecture-overview` | `v1` | `architecture-overview-prompt-v1` | legacy Insight proposals |
| `architecture-overview-v2` | `architecture-overview` | `v2` | `architecture-overview-prompt-v2` | legacy Insight proposals plus mandatory synthesis |

`IntentCatalog.resolve(id, version)` resolves these through the existing
`id + "-" + version` key. `Analysis` stores both values, `AiTask` snapshots
both values, Core sends the complete `IntentDefinition` in `PromptRequest`, and
Python validates the prompt template against id, version, supported types and
output schema. The typed migration adds `v3`; it does not reinterpret v1 or v2.

## Versioned Transition

The existing versions remain deployed and readable:

- `v1`: legacy architecture overview contract, unchanged;
- `v2`: legacy raw-reference contract with synthesis, unchanged;
- `v3`: typed-reference architecture overview contract introduced by this Story.

New executions must select `architecture-overview-v3` explicitly. Existing
analyses/tasks retain their stored version and continue through their existing
contract. No database rewrite or historical raw-ID reinterpretation is allowed.

The version selection remains the existing Intent mechanism:

```text
request intent key
  -> AnalysisService / IntentCatalog
  -> Analysis.intentId + Analysis.intentVersion
  -> AiTask intent snapshot + version
  -> PromptRequest.intent
  -> Python template dispatch and response model
  -> callback contract validation using AiTask version
```

The human-facing Angular analysis selector now selects
`architecture-overview-v3` for new architecture reviews. Existing analyses and
historical fixtures retain their stored versions.

`architecture-overview-v3` must use a distinct prompt template and output
schema marker, for example `architecture-overview-prompt-v3` and an explicit
`schemaVersion`/contract marker in the Intent output schema. The exact marker
name is implementation-level, but it must be version-specific and persisted in
the existing Intent snapshot. Python must reject a v3 request carrying the v1
or v2 template/schema, and Java must reject a v3 callback using legacy fields.

Rollback is operationally explicit: stop selecting v3 and select v2 for new
executions. v1/v2 remain available until all in-flight v3 tasks are terminal.
An in-flight v3 task is never processed as v2 merely because the provider
failed; its callback is validated against the task's stored v3 version and
snapshot. Historical tasks with a null mapping snapshot are legacy tasks and
must not be resolved using a newly-created mapping.

## Typed Wire Contract

The provider-facing reference is a DTO/value with the same semantics as Core's
`AiReference`, not a serialized Core binding or a domain identity:

```json
{
  "type": "FACT",
  "ref": "F001",
  "scope": "ANALYSIS_CONTEXT"
}
```

The v3 context must expose typed references in the citable collections and in
relationship endpoints. It may retain descriptive fields and display values,
but must not expose persistence UUIDs as the sole citable identity. The v3
grounding boundary is structurally separate from semantic context:

```json
{
  "context": {
    "project": {},
    "analysis": {},
    "projectProfile": {},
    "selectedInsights": [],
    "existingArchitectureKnowledge": [],
    "semanticSections": [],
    "relationships": [],
    "repositoryContext": {}
  },
  "groundingCandidates": {
    "facts": [{"type":"FACT","ref":"F001","scope":"ANALYSIS_CONTEXT"}],
    "observations": [{"type":"OBSERVATION","ref":"O001","scope":"ANALYSIS_CONTEXT"}],
    "evidence": [{"type":"REPOSITORY_EVIDENCE","ref":"git:source:sha","scope":"REPOSITORY"}]
  }
}
```

The exact top-level names may follow existing `selectedKnowledge` conventions,
but there must be one unambiguous separation between contextual projections
and authorized candidate sets. `selectedInsights` and semantic sections remain
visible; their references are not thereby valid Fact or Observation evidence.

The v3 output replaces raw-ID grounding fields with typed references:

```json
{
  "type": "INSIGHT",
  "payload": {"insightType":"ARCHITECTURE_DESCRIPTION", "...":"..."},
  "confidence": 0.8,
  "supportingFactRefs": [
    {"type":"FACT","ref":"F001","scope":"ANALYSIS_CONTEXT"}
  ],
  "supportingObservationRefs": [],
  "evidenceRefs": [
    {"type":"REPOSITORY_EVIDENCE","ref":"git:source:sha","scope":"REPOSITORY"}
  ]
}
```

Synthesis `groundingReferences` must use the same typed reference abstraction,
with a version-consistent field name such as `groundingRefs`. No v3 provider
DTO may expose `supportingFactIds`, `supportingObservationIds`, raw UUID
grounding fields, a Core `canonicalSourceIdentity`, or a domain entity UUID as
the grounding identity. Domain UUIDs may remain in internal mapping and
resolved proposal persistence only.

Core `AiReference` is the semantic source of truth. Java/Python transport DTOs
should mirror its three fields and enum serialization (`FACT`,
`ANALYSIS_CONTEXT`) rather than serialize `AiReferenceBinding`; provider DTOs
are preferable to leaking Core packages or persistence metadata across the
boundary.

## Core Resolution And Validation

For v3, `AiTaskResultServiceImpl` must load the task by callback
`correlationId`, verify the external job id and stored intent version, then
resolve every returned typed reference against the task's persisted mapping
snapshot. Resolution must not query the current selected context as a
substitute for the originating snapshot.

For each reference Core validates:

1. exact type, ref and scope match one binding in the snapshot;
2. binding is authorized for the specific output field;
3. binding maps to exactly one domain identity;
4. mapped identity belongs to the task Analysis/project/source scope;
5. all required domain records still exist before proposal persistence;
6. duplicate or conflicting bindings are rejected deterministically.

The v3 resolver returns domain identities only to the existing proposal mapping
inside the transaction. The provider reference never becomes a domain ID and
the mapping snapshot is not trusted knowledge or a public `AiTaskResponse`.

Required hard failure classifications are the ADR-068 meanings:
`UNKNOWN_AI_REFERENCE`, `REFERENCE_NAMESPACE_MISMATCH`,
`REFERENCE_SCOPE_MISMATCH`, `REFERENCE_NOT_ALLOWED_FOR_GROUNDING`,
`REFERENCE_OUTSIDE_CONTEXT`, and `REFERENCE_MAPPING_FAILURE`.
Confidence, prompt wording, retry success, or current repository lookup cannot
override a failure.

The existing proposal lifecycle remains unchanged: accepted v3 output becomes
`ValidatableProposal` with status `PROPOSED`; no Insight is promoted by this
Story.

## Python And Provider Changes

Only the `architecture-overview` v3 dispatch is migrated:

- add a v3 Pydantic typed reference model with `extra="forbid"` and enum validation;
- add v3 context and result models with typed grounding collections;
- dispatch v3 by `IntentDefinition.id`, `version`, and prompt template;
- make the v3 prompt copy exact typed values from the corresponding candidate set;
- keep one corrective retry, preserving the same typed candidate universe;
- ensure Mock and OpenAI providers receive the selected v3 response model;
- send the typed callback fields and existing correlation/prompt metadata.

The provider remains an execution component. It must not resolve references,
access Core storage, maintain an identity registry, or invent a second handle
syntax. v1/v2 Python models and prompt behavior remain available for rollback
and historical tasks.

## Traceability

Story 0126's callback `interactionTraces` contract and persistence remain
unchanged. Existing trace metadata already records intent/version, prompt and
context fingerprints, counts, retry attempts, and optional diagnostic payloads.
The typed mapping digest may be added to v3 execution metadata only if this is
done as a backward-compatible optional field and is needed for trace-to-task
correlation; it must not be injected into the provider's grounding authority.
At minimum, Core diagnostics must be able to associate a resolution failure
with correlation id, task version, mapping digest, field and classification
without persisting secrets or broadening the callback schema unnecessarily.

Retry correction must describe the invalid namespace/scope/authorization
problem semantically and must not repeat or broaden invalid raw identifiers.

## Scope

In scope:

- `architecture-overview-v3` Intent catalog definition and version-specific schema;
- Core typed provider projection for the context actually consumed by this intent;
- typed Python/Pydantic input and output models for this version;
- v3 prompt construction, structured provider dispatch and one retry;
- Java callback DTO compatibility for typed proposal and synthesis references;
- exact `AiTask` mapping-snapshot resolution and authoritative validation;
- conversion of valid Fact/Observation/evidence references into the existing proposal lifecycle;
- focused Java/Python contract, mapping, retry and end-to-end integration tests;
- compatibility tests proving v1/v2 and null-snapshot legacy tasks remain unchanged.

Out of scope:

- Story Context Analysis;
- other proposal intents, engineering events, decisions, README or deliverables;
- global migration of every semantic section or repository projection consumer;
- a new versioning mechanism, registry, database table, retrieval layer or ContextPack;
- changing selection, ranking, budgets, relationship discovery or trust promotion;
- global relationship model migration beyond typed endpoints required in the v3 projection;
- frontend, MCP, REST resource, OpenTelemetry or trace-schema redesign;
- reinterpretation or backfill of historical raw-ID callbacks/tasks.

## Acceptance Criteria

1. `architecture-overview-v3` resolves through the existing Intent catalog and has a distinct prompt/output contract.
2. New v3 tasks carry the Core-created mapping snapshot and send a typed context; v1/v2 tasks retain their legacy payloads.
3. Every v3 citable reference has `type`, `ref`, and `scope`; no provider grounding field uses a bare UUID or raw string identity.
4. Fact, Observation, and repository evidence candidate sets are structurally distinct and contain only their authorized typed references.
5. An Insight visible in context, a semantic section, or a relationship endpoint cannot be submitted as Fact or Observation grounding.
6. A valid v3 callback returns typed references that are resolved from the exact originating `AiTask` snapshot, not a current repository/context lookup.
7. Valid Fact, Observation, and evidence references reach the existing `PROPOSED` proposal lifecycle only after Core resolution.
8. Unknown, cross-namespace, wrong-scope, outside-task, and non-grounding references fail hard with deterministic classifications.
9. A mapping snapshot missing/null on a legacy task never gets silently reconstructed or interpreted as v3.
10. Python performs only defensive schema/subset validation; Java remains authoritative and Python has no domain-resolution access.
11. Corrective retry preserves intent version, typed reference schema, candidate sets, and mapping authority; it cannot broaden grounding.
12. Correlation id, external job id, stored intent/version, and existing trace behavior remain enforced.
13. v1/v2 prompt and callback compatibility tests pass, including v2 synthesis rules.
14. Focused Core, AI Engine, and end-to-end tests prove the vertical slice without requiring all other intents to migrate.
15. Application changes are covered by the recorded implementation authorization; this document does not grant Story acceptance.

## Required Test Matrix

Core mapping/resolution:

- typed Fact -> exact Fact UUID;
- typed Observation -> exact Observation UUID;
- typed repository evidence -> exact canonical evidence identity;
- unknown ref, wrong type, wrong scope, outside-task ref, and contextual Insight-as-Fact rejection;
- snapshot digest/task isolation and no current-context fallback;
- valid reference deleted or unavailable at resolution classified as mapping failure;
- v1/v2/null-snapshot compatibility.

Contract/provider:

- Java/Python JSON round-trip for enum names and aliases;
- v3 schema rejects legacy `*Ids`, bare strings, extra fields, and malformed scopes;
- prompt contains typed candidates and no internal `canonicalSourceIdentity`;
- v3 dispatch selects the v3 Pydantic response model;
- Mock and OpenAI adapter tests use the same structured response model;
- retry preserves exact typed candidate values and intent version.

Vertical integration:

- Core creates v3 task, projects typed context, submits it, and persists the mapping;
- Python returns a typed callback correlated to that task;
- Java resolves the callback through the persisted snapshot and creates a proposed Insight;
- invalid typed callback is rejected before proposal persistence;
- duplicate terminal callback remains idempotent under existing task rules;
- v2 synthesis remains legacy and is not accepted for v3 unless explicitly represented by the v3 schema.

## Likely Repository Seams

These are implementation seams, not an authorization to modify them:

- `backend/.../intent/service/IntentCatalog.java`
- `backend/.../analysis/workflow/AnalysisWorkflowServiceImpl.java`
- `backend/.../ai/task/service/AiTaskServiceImpl.java`
- `backend/.../knowledge/selection/SelectedKnowledgePromptProjectionService.java`
- `backend/.../ai/reference/*`
- `backend/.../ai/engine/dto/PromptRequest.java`
- `backend/.../ai/engine/dto/AiProposalResult.java`
- `backend/.../ai/engine/dto/AnalysisSynthesisResult.java`
- `backend/.../ai/engine/service/AiProposalContractValidator.java`
- `backend/.../ai/engine/service/AiTaskResultServiceImpl.java`
- `ai-engine/app/schemas/ai_task.py`
- `ai-engine/app/schemas/ai_task_result.py`
- `ai-engine/app/schemas/insight.py`
- `ai-engine/app/prompts/insight.py`
- `ai-engine/app/services/insight_generation_service.py`
- `ai-engine/app/services/task_processing_service.py`
- existing tests named in ADR-068 and Story 0126.

## Implementation Boundary

This document records the implemented slice. It does not constitute Story
acceptance, authorize a commit or push, or change ADR-068. Human acceptance is
still a separate gate.

## Implementation Verification

Implemented across the Core, AI Engine, and human-facing frontend seams:

- `architecture-overview-v3` is registered beside unchanged v1/v2 contracts.
- Core creates and persists the execution-scoped AI reference mapping snapshot.
- v3 projects opaque typed references to Python and resolves callback references
  through the originating `AiTask` snapshot before proposal persistence.
- Python uses strict typed reference schemas, typed candidate subsets, v3 prompt
  dispatch, and the existing single corrective retry boundary.
- The Angular architecture review objective selects `architecture-overview-v3`.
- Legacy v1/v2 callback and synthesis paths remain compatible.

Verification completed:

- Backend focused tests: 29 passed.
- Backend full suite: 1,324 passed.
- AI Engine full suite: passed.
- Frontend tests: 260 passed across 49 test files.
- Frontend lint and formatting checks: passed.
- `git diff --check`: passed.

No commit or push was performed. The Story remains pending human acceptance.
