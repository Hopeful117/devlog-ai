# Repository Analysis

## Story Understanding

Story 0173 addresses an observability and contract gap in the Story Context
Agent. Core can intentionally reduce the authorized repository context, while
the AI provider can independently stop generation because its output ceiling is
reached. The current execution record does not make those events sufficiently
distinguishable.

The Story requests bounded accounting, failure-phase classification, provider
incomplete diagnostics, budget preflight validation, safe metrics, actionable
caller errors and regression/evaluation coverage. It explicitly preserves
fail-closed behavior and the Java/Core authority boundary.

The Story excludes RAG, embeddings, new retrieval, Python-side repository access,
generic agent loops, automatic budget increases, credential/OAuth/MCP changes,
and treating incomplete provider content as a valid result.

## Repository Summary

The relevant execution path crosses two application modules:

```text
Java Core
  context collection/selection
    -> canonical context and Story Agent projection
    -> AiTask snapshot and authorized provider parameters
    -> AI Engine callback validation/persistence

Python AI Engine
  prompt construction
    -> provider request
    -> structured parsing/defensive validation
    -> interaction trace and callback
```

Java Core is authoritative for scope, context, grounding, trust, task identity
and callback acceptance. The AI Engine owns provider invocation, structured
generation and defensive validation. This boundary is already implemented and
must not be replaced by a shared retrieval or orchestration layer.

## Affected Modules

### Backend repository context

`backend/.../repositorycontext/RepositoryContextEngine.java` owns candidate
collection, ranking, enrichment, selection, repository-context budgets,
selection decisions, warnings, accounting and the `truncated` flag. Its current
defaults are 60 evidence items and 6,000 repository-context tokens. It marks a
context truncated whenever candidates are discarded, including item-budget
discarding.

### Backend Story Context Agent preparation

`backend/.../storycontextanalysis/usecase/StoryContextPreparationService.java`
constructs the canonical context and invokes the Core-owned projection. It
currently increments only broad projection and budget-truncation metrics.

`backend/.../storycontextanalysis/usecase/StoryContextAgentProjectionV1.java`
serializes context, grounding candidates, freshness, request identity and
accounting. It exposes candidate/selection/discard counts, used tokens, budget,
truncation and warnings, but not discarded identities, prompt size or provider
budget information.

`backend/.../storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`
owns projection preparation, task submission, provider authorization and the
execution boundary. It already supports prepared-context reuse and does not
perform a second selection for the submitted prepared context.

`backend/.../storycontextanalysis/usecase/StoryContextSubmissionService.java`
builds the immutable task snapshot, persists canonical context/projection
accounting and forwards Core-authorized `allowedParameters`, including
`maxOutputTokens`, to the AI Engine. It currently does not persist a complete
budget-layer accounting model.

### Backend callback and trace persistence

`backend/.../storycontextanalysis/service/StoryContextAgentCallbackFacade.java`
validates the callback contract, delegates result handling and records broad
metrics for digest, grounding and duplicate-terminal conditions.

`backend/.../ai/engine/dto/AiInteractionTraceRequest.java` and
`backend/.../ai/interactiontrace/entity/AiInteractionTrace.java` already support
failure category, fingerprints, selected knowledge counts, token usage, trace
identifiers and optional diagnostic prompt/model fields.

`backend/.../ai/interactiontrace/service/AiInteractionTracePersistenceService.java`
persists normal trace metadata and retains prompt/model payloads only at the
configured diagnostic level with expiry. This is reusable, but the current
schema has no explicit terminal phase, effective output limit, provider response
status or prompt-size fields.

`backend/.../storycontextanalysis/service/StoryContextAgentMetrics.java`
provides bounded counters and an operation-latency timer. Existing counters are
fixed-name counters without provider/project/request-content labels, which is a
safe base for additional bounded failure counters.

### AI Engine prompt and provider boundary

`ai-engine/app/prompts/story_context_analysis.py` validates the Core-owned
projection, embeds the projection, grounding contract and full structured output
schema into the prompt, and adds an abstention rule when the projection is
truncated. The prompt has a `GenerationPolicy` maximum output size of 5,000, but
provider execution uses authorized generation parameters or the engine default;
there is no cross-layer preflight calculation.

`ai-engine/app/services/story_context_analysis_generation_service.py` performs
prompt construction, one corrective retry for validation/contract failures,
structured generation, defensive validation and callback construction. Provider
exceptions are classified as `LLM_PROVIDER_ERROR`; they do not enter the
corrective validation retry path.

`ai-engine/app/services/interaction_trace.py` records one trace per generation
attempt and captures token usage only when a provider returns a
`ProviderGenerationResult`. If the provider raises before returning that result,
usage remains absent and the trace contains only the bounded exception message.

`ai-engine/app/providers/chatgpt.py` streams the Responses API and explicitly
raises on `response.incomplete`, retaining only a bounded incomplete reason in
the exception message. It does not currently project response/request IDs,
provider status or incomplete usage into `ProviderGenerationResult` or the
Story Context trace.

`ai-engine/app/providers/openai.py` uses the configured output ceiling directly
with `responses.parse`. It captures usage for completed responses but does not
provide an incomplete-response diagnostic projection for exceptions raised by
the SDK.

### Runtime configuration

`ai-engine/app/core/config.py` defaults `llm_max_output_tokens` to 2,000 and
`docker-compose.yml` exposes the same default through `LLM_MAX_OUTPUT_TOKENS`.
Core can authorize a different `maxOutputTokens` value through provider/model
configuration. The current repository has no model-context-capacity registry or
safe prompt-input/output headroom calculation.

## Existing Implementation

### Existing behavior

- Core deterministically bounds repository evidence and marks reduction with
  `truncated` and accounting metadata.
- The Story Context projection carries context/projection identity, freshness,
  grounding candidates, accounting and warnings.
- Core forwards provider-authorized output parameters to the AI Engine.
- The AI Engine emits structured output, performs defensive validation and uses a
  maximum of one corrective retry for invalid output.
- The ChatGPT adapter rejects incomplete streaming responses and reports a
  bounded reason such as `max_output_tokens`.
- Core callback handling remains authoritative and fail-closed.
- Interaction traces and diagnostic retention already exist, including safe
  handling of full prompts only in diagnostic mode.

### Missing behavior

- No shared accounting model distinguishes selection, projection/prompt input,
  provider output and model capacity.
- No reliable preflight check exists before provider invocation.
- Provider incomplete responses lack a structured diagnostic projection in the
  Story Context trace.
- Trace and snapshot metadata lack an explicit terminal execution phase.
- Metrics do not distinguish provider-incomplete, preflight, parsing and
  validation failures for this capability.
- The caller-facing failure surface does not explain which budget/failure layer
  ended the execution.

### Behavior that must remain unchanged

- Core remains the sole authority for context construction, grounding, trust and
  acceptance.
- `contextDigest` and `projectionDigest` remain Core-issued identities.
- Incomplete provider output is never parsed or persisted as a successful
  analysis.
- Provider credentials, tokens, prompts and repository payloads remain protected
  by existing redaction and diagnostic-retention boundaries.
- REST and MCP remain transport adapters over the same Core capability.

## Relevant Documentation

- `AGENTS.md`
- `docs/stories/0173-story-context-budget-observability/story.md`
- `docs/investigations/story-context-truncation-and-provider-incomplete-response.md`
- `docs/decisions/ADR-006.md`
- `docs/decisions/ADR-063.md`
- `docs/decisions/ADR-067.md`
- `docs/decisions/ADR-069.md`
- `docs/decisions/ADR-079.md`
- `docs/stories/0112-devlog-story-context-agent/story.md`
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/story.md`
- `docs/evaluation/comparative-baseline-v4-provider-observability-accounting-remediation.md`
- `docs/evaluation/comparative-baseline-v4-post-remediation-live-validation.md`
- `TOOLS.md`

DevLog context was requested for this analysis but was unavailable because the
connected service returned `Authentication is required`. No Obsidian vault note
was consulted; repository artifacts and the approved Story provide sufficient
context.

## Constraints

- The accepted Core/Python boundary from ADR-067 and ADR-069 is mandatory.
- ADR-006 prohibits treating AI output as trusted knowledge.
- The Story requires no autonomous retrieval or generic agent runtime.
- Existing callback identity and grounding contracts use strict validation and
  must remain compatible; new fields need additive/versioned treatment where
  `extra="forbid"` applies.
- Diagnostic data must be bounded and secret-free. Diagnostic prompt fields are
  already retention-controlled and must not become normal-log payloads.
- Metrics must use fixed or bounded labels to avoid cardinality growth.
- Core-authorized `maxOutputTokens` is an execution input, not evidence of actual
  provider usage.
- Model capacity and tokenizer behavior cannot be inferred from the configured
  output ceiling.
- Unknown model capacity must fail closed before provider invocation; estimates
  need an explicit certainty status and cannot silently authorize execution.
- New Core/AI Engine diagnostic fields require a versioned or explicitly
  additive contract with a defined mixed-version compatibility matrix.
- The immutable Core execution snapshot is an audit/grounding artifact and may
  contain the authorized context/projection; normal and diagnostic traces have
  separate content and retention rules.
- All supported provider adapters need a normalized diagnostic fallback, with
  unavailable provider-specific values represented as `UNKNOWN`.
- The current worktree contains pre-existing untracked Playwright and OAuth
  artifacts; they are unrelated and must remain untouched.

## Risks

- Adding provider-specific fields to a shared callback contract can affect all AI
  task types, not only Story Context Agent executions.
- Capturing incomplete provider diagnostics at the streaming boundary may require
  provider-specific adapters without leaking response content.
- Prompt-size measurement may not be tokenizer-accurate for every configured
  provider, so an apparently safe preflight could still be an estimate.
- Persisting more accounting in `AiTask.contextSnapshot` or interaction traces
  can increase database volume and diagnostic-retention exposure.
- A budget preflight that rejects too aggressively could reduce successful live
  executions; one that is too permissive will not prevent provider interruption.
- Changing default output limits while adding instrumentation would make root
  cause and regression results harder to interpret; the Story explicitly keeps
  budget tuning separate.
- Existing tests cover provider incomplete reason formatting and trace persistence
  separately, but not the full Core-to-provider failure correlation.

## Open Questions

1. Which configured provider/model capacity source will supply the safe preflight
   bound, while preserving mandatory fail-closed behavior when unavailable?
2. Which measurement method will be used for the first implementation, provided
   that its unit and certainty status are explicit?
3. Which versioned Story Context envelope or additive contract will carry the
   normalized diagnostics without breaking strict shared contracts?
4. Which bounded provider response identifiers are safe and consistently
   available across adapters, with `UNKNOWN` as the fallback?
5. Which caller/API response shape should expose the normalized failure phase and
   whether the context was truncated?

These questions affect the implementation plan but do not indicate an
architectural conflict. They must be resolved without changing the approved
authority and trust boundaries.

## Recommendation

Ready for planning

The repository ownership, current execution path, existing contracts and test
surfaces are sufficiently understood. The open questions are bounded
implementation choices for the next workflow stage, not missing product scope or
blocking architectural decisions.

## Implementation Readiness

No blocking prerequisite was identified. The required Core projection, AI Engine
provider adapters, callback contracts, trace persistence and test harnesses
already exist. The Story can be planned without introducing a new retrieval
engine, persistence subsystem or provider credential boundary.

The implementation remains unauthorized until the Repository Analysis is
approved and a subsequent Implementation Plan is explicitly approved.

## Independent Review Reconciliation

The independent review identified six findings. They are addressed in this
revision:

- unknown model capacity is fail-closed before provider invocation;
- mixed-version contract behavior and rollout compatibility are required;
- execution snapshots are distinguished from normal and diagnostic traces;
- measurement units and certainty statuses are explicit;
- provider coverage and `UNKNOWN` fallbacks are defined;
- deterministic fixtures are mandatory and live credentials are optional.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
