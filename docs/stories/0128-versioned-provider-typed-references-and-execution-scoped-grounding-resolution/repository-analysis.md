# Story 0128 - Repository Analysis

## Recorded Baseline

| Field | Value |
|---|---|
| `HEAD` | `d831278cb39c7346f4960b059d3862537b043454` |
| `ORIGIN_MAIN` | `d831278cb39c7346f4960b059d3862537b043454` |
| Branch | `main` |
| Worktree status | `main...origin/main`; modified generated `devlog-contracts/target/*`; untracked `docs/discoveries/`; untracked Story 0127 documentation |
| ADR-068 status | `Accepted`, committed at `HEAD` |
| Story 0127 implementation present | `YES`, Core classes, AiTask JSONB field, wiring and tests are in the baseline |
| Story 0127 committed story document | Historical availability is incomplete; current untracked artifacts document the implementation. This is not evidence that implementation is absent. |

No unrelated worktree file was reverted or modified.

## Verified Architecture Gap

Story 0127's actual topology is:

```text
authorized SelectedKnowledge
  -> AiReferenceRegistryFactory
  -> AiReferenceRegistry
  -> AiReferenceMappingSnapshot on AiTask

authorized SelectedKnowledge
  -> SelectedKnowledgePromptProjectionService
  -> selectedKnowledgeSnapshot / PromptRequest
  -> Python provider
```

The two branches meet only in Core persistence. The provider branch still
contains raw `FactSnapshot.id`, `ObservationSnapshot.id`, Insight UUIDs and
repository reference strings. Python `InsightProposalOutput` still emits
`supportingFactIds`, `supportingObservationIds`, and `evidenceReferences`.
Java `AiTaskResultServiceImpl` still validates Fact and Observation UUIDs via
`findAllById` and Analysis ownership, while `AiProposalContractValidator`
builds allow-lists by recursively reading the selected snapshot. Neither path
uses `aiReferenceMappingSnapshot`.

`architecture-overview` is the narrowest real vertical slice because the
catalog already defines `v1` and `v2`, the workflow selects the intent through
the existing version mechanism, and its provider service already owns both
proposal and synthesis output handling. `engineering-story-context-analysis`
must not be migrated as collateral; its callback path is separately handled by
`AnalyzeStoryContextUseCase`.

## Version Finding

The repository has no separate provider-version registry. Version is already a
first-class pair `(intentId, intentVersion)` represented in `IntentDefinition`,
stored on `Analysis` and `AiTask`, copied into `PromptRequest`, checked by the
Python prompt template map, and persisted in interaction traces and task
snapshots. The correct transition is therefore a new catalog entry
`architecture-overview-v3`, not a parallel provider version field.

The existing v2 behavior is materially different from v1 because it requires
`AnalysisSynthesisResult` and has delta validation. Therefore treating v2 as a
generic legacy placeholder and mutating it in place would break rollback and
historical task semantics. v3 is the safe typed contract boundary.

## Contract Finding

Core's authoritative semantic model is already:

```text
AiReference(type, ref, scope)
```

Current enum values include `FACT`, `OBSERVATION`, `INSIGHT`, `ANALYSIS`,
`PROJECT`, `PROJECT_PROFILE`, `HUMAN_CONTEXT`, `ENGINEERING_EVENT`,
`ARCHITECTURE_KNOWLEDGE`, `REPOSITORY_EVIDENCE`, `ENGINEERING_STORY`,
`DECISION`, and `CHALLENGE`; scopes include `ANALYSIS_CONTEXT`, `PROJECT`,
`SOURCE_REVISION`, and `REPOSITORY`.

`AiReferenceMappingSnapshot` additionally contains Core-only
`canonicalSourceIdentity` and `groundingCapabilities`. Those fields are
required for resolution but must not cross the provider boundary. The v3 wire
model should mirror only `type/ref/scope`, using provider DTOs rather than
exposing Java binding or persistence classes.

The mapping factory currently allocates `F001...`, `O001...`, `A001`, and
`PP001` for analysis-local values, stable typed references for project entities,
and canonical references for repository evidence. Architecture knowledge
aliases the underlying Insight binding. This aliasing and grounding capability
metadata must be preserved when projecting v3 candidates.

## Persistence And Callback Finding

`AiTask.aiReferenceMappingSnapshot` is nullable JSONB and intentionally omitted
from `AiTaskResponse`. This is the correct execution boundary. v3 callback
resolution must use the row locked by
`findByCorrelationIdForUpdate(correlationId)` and the exact snapshot captured
when the task was prepared. It must occur before `toProposals()` and before
`proposalRepository.saveAll()`.

The callback endpoint remains `/api/v1/ai/tasks/{correlationId}/result`.
`AiTaskResultRequest` currently carries generic `AiProposalResult` and optional
`AnalysisSynthesisResult`; v3 requires a version-aware typed result DTO or an
equivalent contract branch that cannot deserialize legacy raw-ID fields as v3.
`externalJobId`, terminal-state handling, interaction trace persistence and
existing callback correlation checks remain applicable.

## Python Finding

`AiTaskProcessingService` dispatches by `task_type`, not directly by intent.
`InsightGenerationService` then selects prompt construction and currently
always uses `InsightGenerationOutput`, with `architecture-overview-v2`
special-cased for synthesis. `InsightPromptBuilder.TEMPLATES` already verifies
intent id/version/types and exact output schema. This is sufficient dispatch
infrastructure for v3; a second task type or provider registry is unnecessary.

`MockLlmProvider` and `OpenAiLlmProvider` already accept a Pydantic response
model, so typed v3 output can use the same provider seam. The provider must not
be changed to resolve Core references. `InteractionTraceCollector` will record
the v3 prompt/output attempt under the existing intent/version fields and must
retain the one-retry boundary.

## Required Migration Boundary

Only these `architecture-overview` dependencies need migration:

- selected facts, observations, insights, architecture knowledge and repository evidence required by the v3 prompt;
- relationship endpoints only where the architecture-overview projection includes them;
- architecture proposal grounding and v2-like synthesis grounding, represented in the v3 typed schema;
- Core callback conversion into existing Insight proposal persistence.

No other prompt builder, proposal type, Story Context Analysis result, event or
decision contract should be migrated merely because a shared DTO exists.

## Risks Identified

- Mutating v2 would make rollback and stored tasks ambiguous.
- Sending `AiReferenceMappingSnapshot` wholesale would leak Core identity and grounding metadata.
- Resolving against current selected knowledge would violate execution reproducibility.
- Reusing a typed Insight reference as Fact grounding would violate capability isolation.
- Keeping generic Java callback DTOs without a strict v3 branch could accept legacy fields accidentally.
- Migrating only prompt text without changing the Pydantic response model would leave the provider contract raw-ID based.
- Updating Python validation without Java snapshot resolution would create false end-to-end confidence.

## Implementation Update

The planned vertical slice has been implemented. The frontend analysis selector
was also updated as a necessary user-facing integration point: new
architecture reviews now request `architecture-overview-v3`; stored v1/v2
analyses remain versioned and readable.

Verification completed after implementation:

- Backend full suite: 1,324 tests passed.
- AI Engine full suite: passed.
- Frontend suite: 260 tests passed across 49 test files.
- Frontend lint, formatting, and repository diff checks passed.

No commit or push was performed. Human Story acceptance remains outstanding.

## Refinement Conclusion

The repository supports a narrow, versioned vertical slice without a new
versioning system or persistence model. `architecture-overview-v3` is
introduced alongside unchanged v1/v2, uses provider DTOs structurally
mirroring Core `AiReference`, and resolves callback evidence exclusively
through the originating `AiTask` mapping snapshot. Implementation is complete;
the remaining gate is separate human Story acceptance.
