# Implementation Plan - Story 0163

## Approved Repository Analysis

`repository-analysis.md` is approved by the human project owner.

## Scope

Implement only the curated, Core-owned Provider and Model Catalog defined by
ADR-074.

The implementation must provide deterministic product metadata for:

- `mock` with no authentication;
- `openai` with API key;
- `openai_chatgpt` with OAuth;
- `gemini` with API key;
- `anthropic` with API key;
- `openrouter` with API key.

It must include the approved vertical-slice metadata for `openai_chatgpt`,
`anthropic` and `mock` and may represent the other providers without claiming
that their complete user-facing configuration flows are implemented.

## Design

### Core ownership

Add a dedicated Core catalog package rather than placing provider metadata in
the existing intent package or in AI Engine process configuration.

The catalog will use immutable, deterministic definitions and a thin read
adapter following the existing `IntentCatalog`/`IntentController` pattern
without reusing intent domain types.

### Catalog model

The internal model will distinguish:

- provider identifier and display name;
- authentication methods;
- provider-scoped model definitions;
- recommended model;
- capability metadata;
- lifecycle status;
- catalog version.

The model will not contain credentials, authorization grants, user preference,
runtime checks or task bindings.

### Model recommendation

The catalog will expose a recommendation as metadata only. It will not expose
fallback behavior. The catalog contract will preserve the distinction between
recommended and explicitly selected models for consumers introduced by later
Stories.

### API boundary

Expose a read-only Core endpoint only if required by existing product API
conventions. The endpoint must return catalog metadata and must not expose
deployment secrets or provider runtime payloads.

The exact endpoint path and DTO names must follow the implementation's existing
controller and response conventions. They are not to be introduced as an
authorization or credential endpoint.

## Files Expected To Change

Expected production locations:

- a new dedicated package under
  `backend/src/main/java/com/hopeful117/devlogai/` for catalog model and
  service;
- a read-only controller and DTOs if the API boundary is included.

Expected test locations:

- unit tests adjacent to the catalog service/model;
- controller tests if a REST adapter is included.

No changes are expected in:

- `ai-engine/app/providers/`;
- `ai-engine/app/core/config.py`;
- OAuth services;
- credential association or binding services;
- frontend sources;
- database migrations.

## Implementation Steps

1. Confirm the final package and naming against nearby Core catalog/controller
   conventions.
2. Add immutable provider, authentication-method and model catalog types.
3. Add the deterministic catalog definition in stable order.
4. Register all six current provider identifiers.
5. Register the required authentication methods and vertical-slice metadata.
6. Register provider-scoped known models and one recommendation per catalog
   entry where the repository can support a stable curated value.
7. Add the read-only Core service/capability.
8. Add the read-only REST adapter only if the existing API boundary requires it.
9. Add unit and adapter tests for the acceptance criteria.
10. Reconcile any affected architecture documentation before review.

## Acceptance Criteria Mapping

| Story criterion | Planned evidence |
|-----------------|------------------|
| All current providers representable | Catalog unit test covering six identifiers |
| OAuth/API key/no-auth metadata | Catalog test for `openai_chatgpt`, `anthropic` and `mock` |
| Provider-scoped model identifiers | Model validation and duplicate-scope tests |
| Deterministic ordering | Repeated catalog projection test |
| Recommendation represented | Provider/model contract test |
| Recommendation does not mutate selection | Contract excludes fallback/mutation behavior |
| No secrets or authorization data | DTO/model assertions and review of fields |
| Read projection if exposed | Controller contract test |

## Testing Commands

Focused validation:

```bash
./backend/mvnw -pl backend test -Dtest=<catalog test classes>
```

Broader relevant validation after focused tests:

```bash
./backend/mvnw -pl backend test -B
```

The exact focused test class names will be reported after implementation.
No provider credentials, network calls or live model execution are required.

## Documentation Reconciliation

The implementation must be checked against ADR-074 and the Epic. If the new
read endpoint or contract changes canonical architecture documentation, update
only the affected canonical document before review. No roadmap change is
expected because `docs/roadmap.md` has no Epic registration convention.

## Risks

- Introducing persistence would exceed Story scope and is prohibited unless
  repository evidence requires it.
- Exposing AI Engine settings directly would couple deployment configuration to
  the product contract and is prohibited.
- Adding runtime discovery would exceed the approved V1 decision.
- Adding credential or authorization fields would violate ADR-074.

## Implementation-Level Decisions

- Use a dedicated catalog package rather than `intent`.
- Prefer an immutable static catalog over database persistence.
- Keep the catalog read-only and non-authoritative for authorization.
- Represent supported providers even when later user-facing flows are not yet
  implemented.

## Gate Status

Implementation Plan complete and ready for human approval.

No production code should be modified until this plan is explicitly approved.
