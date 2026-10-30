# Repository Analysis - Story 0163

## Repository State

- Branch: `feat/story-0163-provider-model-catalog`
- Base: `main` at `af9d04b`
- Worktree: contains the approved Epic/ADR/Story documentation changes; no
  functional source change has been made for Story 0163.

## Governance

- Epic: Simplifier la configuration des providers et modeles IA de DevLog AI.
- Story: 0163 - Provider and Model Catalog.
- Governing ADRs:
  - ADR-006 - AI proposal and knowledge promotion workflow;
  - ADR-067 - Java/Core and Python AI Engine boundary;
  - ADR-070 - principal and project authorization boundary;
  - ADR-074 - Provider and Model Catalog Boundary.
- Supporting ADRs:
  - ADR-075 - User AI Configuration and Runtime State Ownership;
  - ADR-076 - User-Provided API Key Credential Lifecycle;
  - ADR-072 and ADR-073 for existing OAuth and binding boundaries.

The Epic and ADR documents were explicitly approved by the human project owner.
That approval authorizes the implementation workflow, but it does not remove
the Engineering Story Repository Analysis and Implementation Plan gates.

## Relevant Current Implementation

### AI Engine providers

The AI Engine defines a shared `LlmProvider` protocol in
`ai-engine/app/providers/base.py`. The protocol exposes generation plus the
provider and model identifiers used in execution metadata.

The concrete providers are:

- `mock.py`;
- `openai.py`;
- `chatgpt.py`;
- `gemini.py`;
- `anthropic.py`;
- `openrouter.py`.

Provider construction is process-scoped in
`ai-engine/app/core/dependencies.py`. `Settings` in
`ai-engine/app/core/config.py` reads `LLM_PROVIDER` and `LLM_MODEL` and
validates the supported provider set.

This is execution configuration, not a product catalog. It must not be exposed
as the user configuration model without an explicit adapter.

### Existing catalog pattern

Core already contains a static catalog pattern in
`backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`.
`IntentCatalog` uses deterministic, ordered, immutable definitions and exposes
them through a thin `IntentController`.

This pattern is relevant for the provider/model catalog because the V1 catalog
is curated and controlled by DevLog. It is not evidence that the provider
catalog should be placed inside the existing intent domain or share its
classes.

### Existing execution metadata

`AiTask` persists `provider` and `modelIdentifier` as execution metadata. The
same values appear in interaction traces and provider result contracts. These
fields describe a completed or submitted execution; they are not a catalog,
user preference or authorization decision.

### Existing credential and binding boundaries

Core contains `AiCredentialAssociation` and `AiCredentialBinding` under
`aicredentialbinding`. The binding is task-scoped and validates principal,
task and provider. The AI Engine resolves user credentials through
`ProviderCredentialResolver` after binding validation.

These mechanisms are supporting boundaries for later Stories. Story 0163 must
not add credential selection, binding issuance or provider runtime resolution.

### Frontend

The Angular routes contain project, analysis, story and settings workspace
surfaces, but no provider/model catalog or AI configuration feature. Story 0163
therefore should define a backend/product contract that Story 0168 can consume;
it must not add frontend UI as an opportunistic extension.

## Required Catalog Shape

The catalog must represent product metadata, not secrets or authorization:

```text
ProviderCatalogEntry
  providerId
  displayName
  authenticationMethods
  models
  recommendedModel
  capabilities
  lifecycleStatus
  catalogVersion
```

Each model is scoped to its provider and must support at least:

- stable model identifier;
- display label;
- recommendation status or catalog relation;
- supported/known status;
- relevant DevLog capability metadata.

The exact Java type and API names remain implementation-plan concerns, but the
contract must not reuse `AiTask` execution metadata as a configuration model.

## Required Initial Entries

The catalog must express the approved vertical-slice entries:

- `openai_chatgpt`: OAuth;
- `anthropic`: API key;
- `mock`: no authentication.

It should also represent the other currently supported providers without
claiming that their user-facing configuration flows are implemented by this
Story.

## Boundary and Scope

Story 0163 includes:

- the Core-owned catalog definition;
- stable provider and model identifiers;
- authentication method metadata;
- recommended model metadata;
- capability and lifecycle metadata;
- a read-oriented product projection if required by the existing API style;
- deterministic catalog tests.

Story 0163 excludes:

- user preference persistence;
- runtime `READY` calculation;
- OAuth connection flow;
- API-key intake or storage;
- credential associations;
- task bindings;
- dynamic provider calls;
- frontend components.

## Risks and Constraints

1. Putting catalog metadata in `ai-engine/app/core/config.py` would couple
   product metadata to deployment configuration and make it inaccessible to
   Core-owned user-facing APIs.
2. Reusing `AiTask.provider` or `modelIdentifier` as the catalog model would
   conflate execution history with supported product choices.
3. Adding dynamic provider discovery now would create runtime availability,
   caching and failure semantics outside the approved V1 scope.
4. Adding authorization or credential data to catalog entries would conflict
   with ADR-070, ADR-073 and ADR-074.
5. A new catalog contract must not alter the existing AI Engine provider
   factory or generation contracts.

## Recommended Implementation Direction

Use a dedicated Core catalog package with immutable, deterministic definitions,
following the repository's static `IntentCatalog` pattern without sharing the
intent domain model. Expose only a read-oriented catalog capability. Keep the
catalog source provider-independent and make any future AI Engine capability
probe an explicit, non-authoritative adapter in a later Story.

The first implementation should prefer a fixed curated catalog over database
persistence. No user-specific data is required by this Story, so a migration
or persistence model would add scope without supporting the accepted criteria.

## Validation Plan

Focused validation for this Story should cover:

- catalog completeness for all current provider identifiers;
- OAuth/API-key/no-authentication metadata for the vertical slice;
- provider-scoped model identifiers;
- deterministic ordering;
- recommended model presence and representation;
- absence of credentials and authorization fields;
- read API response shape if an API adapter is included;
- regression of existing intent and AI task metadata contracts.

No live provider call or provider credential is required.

## Documentation Assessment

Story 0163 is itself the first implementation vehicle for ADR-074. The Epic
and ADR references are already present. If the implementation introduces a
new public API contract or changes the canonical architecture documentation,
that documentation must be reconciled before review. No roadmap update is
required because the repository roadmap is phase-level and has no Epic
registration convention.

## DevLog Context

The DevLog Story Agent capability was not available in the current execution
tool context. This analysis therefore uses the current repository, approved
Epic/ADR documents and existing Story artifacts as sources of truth.

## Gate Status

Repository Analysis complete.

The workflow must stop here for explicit human approval of this analysis before
the Implementation Plan is produced. No functional source file should be
modified while this gate is pending.
