# Implementation Plan - Story 0167

## Status

Implementation Plan approved by the human project owner on 2026-10-31.

ADR-074 is accepted and defines the catalog boundary for this implementation.

## Objective

Add a bounded configuration-test capability for `openai_chatgpt` OAuth and
`anthropic` API-key configurations. The test verifies the selected provider,
credential association, and selected model without repository context,
significant generation, fallback, task binding, or permanent authorization.

## Design

### Core Authority

1. Add an authenticated Core endpoint under the existing AI configuration
   boundary, for example `POST /api/v1/ai/configuration/test`.
2. Resolve the principal's persisted configuration and credential association
   in Core.
3. Reject missing, revoked, incomplete, mismatched, or unavailable
   configuration before calling AI Engine.
4. Resolve the selected catalog model and preserve the user's explicit model;
   never substitute the recommendation.
5. Call AI Engine through a service-authenticated client with only the provider,
   selected model, authentication method, and opaque credential handle where
   applicable. Do not send repository context or secret material.
6. Map the bounded AI Engine result to stable product categories and update
   only the derived runtime state if the existing state model requires a
   synchronization point. Do not mutate provider, model, or credential
   preferences.

### AI Engine Capability

1. Add a service-authenticated configuration-test endpoint and a dedicated
   service, separate from task generation and prompt/context pipelines.
2. Resolve the credential only from the supplied opaque handle and provider;
   never accept a raw secret or arbitrary credential metadata.
3. Add provider adapters for the two in-scope paths:
   - Anthropic API-key validation/model availability through a bounded provider
     metadata probe.
    - ChatGPT OAuth validation/model availability through the authenticated
      models metadata endpoint, without repository prompt content or
      billable generation.
4. Require the requested model to be the model tested. A missing or unavailable
   model returns `MODEL_UNAVAILABLE`; no fallback is permitted.
5. Enforce a short timeout, no retry loop for the user test, bounded response
   diagnostics, a 64 KiB provider metadata response limit, and no persistence of
   provider response bodies or test content. A paginated/incomplete model list
   is reported as `PROVIDER_UNAVAILABLE`; it is never treated as complete.
6. Return only a stable result category, provider/model identifiers, bounded
   timing metadata if needed, and redacted diagnostic metadata.

7. Persist the test identity before the provider probe and apply the result only
   if the configuration still owns that identity. Configuration changes and
   concurrent tests therefore cannot receive a stale result.

### Result Categories

Use a shared Core-facing contract with at least:

- `SUCCESS`;
- `CREDENTIAL_INVALID`;
- `PROVIDER_UNAVAILABLE`;
- `MODEL_UNAVAILABLE`;
- `TIMEOUT`;
- `CONFIGURATION_INVALID`.

The contract must not include secrets, provider response bodies, access tokens,
prompts, repository context, or arbitrary exception messages.

## Files and Modules

### Backend

- Add a configuration-test controller, request/response records, and service.
- Extend or add the AI Engine client for the signed configuration-test request.
- Reuse `AiProviderCatalogService`, `AiUserConfigurationService`,
  `AiCredentialAssociationService`, and existing authenticated-principal
  resolution.
- Add bounded result mapping and runtime-state synchronization only if required
  by the current state contract.
- Add unit, controller, and PostgreSQL integration coverage.

### AI Engine

- Add configuration-test API, schemas, service, and stable error mapping.
- Add a provider-test protocol or adapter boundary without changing the
  generation `LlmProvider` contract unnecessarily.
- Reuse encrypted credential resolution and service authentication.
- Add provider fakes for OAuth and Anthropic tests; live provider calls are not
  required for automated tests.

### Documentation

- Update only the canonical configuration/runtime documentation if the new
  endpoint or result categories change documented behavior.
- Record the bounded-test and no-repository-context boundary in the Story
  implementation report.

## Security and Governance Constraints

- Core remains the authorization authority.
- AI Engine receives immutable authorized input only.
- No raw credential crosses into Core or appears in a response/log/trace.
- No repository context, project context, prompt, or user content enters the
  test request.
- A successful test is not an `AiCredentialBinding` and does not authorize a
  future task.
- A failed test cannot rewrite provider/model preferences or claim `READY`.
- Provider/model fallback is forbidden.

## Tests

### Core

- authenticated principal isolation;
- missing, incomplete, revoked, and invalid association;
- selected model preservation and no fallback;
- stable mapping for success, credential, provider, model, and timeout failures;
- no task binding creation or authorization side effect;
- state remains non-ready after a failed test.

### AI Engine

- valid OAuth probe;
- valid Anthropic API-key probe;
- invalid/missing credential;
- provider unavailable;
- model unavailable;
- timeout boundary;
- service-authentication failure;
- no secret, repository context, provider body, or raw exception in output;
- no retry beyond the bounded test policy.

### Regression

- Existing Story 0165 OAuth flows.
- Existing Story 0166 API-key enrollment, association, replacement, and
  revocation.
- Existing task binding and credential resolver behavior.

## Validation Commands

```bash
cd ai-engine && python -m pytest -q
./backend/mvnw -pl backend -am test -B
```

Run focused changed-module tests before the broader suites. Run `git diff
--check` and inspect the complete diff before delivery.

## Resolved Implementation Decision

Anthropic uses its bounded models metadata endpoint. ChatGPT OAuth uses its
configured authenticated models metadata endpoint. Neither path sends a
generation prompt, stores provider response bodies, or falls back to another
model. A provider that cannot expose the selected model through metadata is
reported as `MODEL_UNAVAILABLE` or `PROVIDER_UNAVAILABLE`; no generation
fallback is permitted.
