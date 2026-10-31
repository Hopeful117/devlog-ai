# Implementation Plan - Story 0169

## Status

Implementation plan prepared after human approval of the Story 0169 Repository
Analysis and explicitly approved by the human project owner on 2026-10-31.

## Objective

Make user configuration authoritative for real DevLog task execution. A
configuration in `READY` state must produce one Core-owned, task-scoped binding
that is consumed by the AI Engine for both ChatGPT OAuth and Anthropic API-key
credentials. REST, MCP, and background paths must share the same Core decision.

## Scope

### In scope

- Resolve the active user configuration and runtime state before task submission.
- Enforce `READY` state and selected-model availability.
- Issue a task-scoped `AiCredentialBinding` through the existing Core service.
- Propagate the binding identifier and selected provider/model through the task
  contract without putting secrets in prompts or metadata-controlled authority.
- Connect both ChatGPT OAuth and Anthropic API-key associations to task use.
- Reuse the same authorization decision from REST, MCP, and background adapters.
- Preserve expiry, revocation, principal, project, task, provider, and model
  validation before provider invocation.
- Add end-to-end contract/service tests for both provider vertical slices.

### Out of scope

- New provider authentication mechanisms.
- New AI Engine authorization authority.
- Project-global bindings.
- Provider, credential, or model fallback.
- Grounding, context construction, proposal validation, or trust-promotion
  changes.
- Dynamic model discovery or multi-credential selection.

## Design

### 1. Core task authorization capability

Introduce or extend a single Core service boundary that accepts the authenticated
principal, project, task, provider, and selected model. It will:

1. load the principal's active user configuration;
2. require configuration state `READY`;
3. require the requested provider and model to match the configuration;
4. reject unavailable models;
5. resolve the provider-specific credential association;
6. issue or retrieve an immutable task-scoped `AiCredentialBinding`;
7. return only the opaque binding identifier and non-secret task execution data.

The existing `AiCredentialBindingService` remains the authority for binding
scope, membership, expiry, revocation, and provider validation.

### 2. Task contract propagation

Extend the existing Core-to-AI Engine task submission contract only where needed
to carry the Core-issued binding identifier and the Core-selected provider/model.
The AI Engine must reject missing or mismatched binding data before provider use.
The binding identifier must not be included in prompts, model output, callbacks,
interaction traces, or user-visible MCP results.

### 3. REST and MCP convergence

Route REST and MCP Story Agent submission through the same Core task authorization
capability. MCP remains a thin adapter and must not reproduce configuration,
credential, or binding logic.

### 4. Background execution identity

Use an explicit execution identity already present in the background workflow, or
add a narrowly scoped Core-owned execution principal if repository evidence
supports it. Do not infer a user principal from project, task, metadata, OAuth,
or transport data. If the current background workflow cannot provide an
authorized principal, fail closed and keep the path outside the user-bound
vertical slice until the required identity decision is approved.

### 5. Provider execution

Update AI Engine task processing to resolve ChatGPT and Anthropic credentials
only through the Core-issued binding. Provider/model selection must come from the
authorized task contract, never from arbitrary caller metadata or process-level
fallback credentials when a user binding is required.

## Affected Areas

### Core

- `aicredentialbinding` binding service, repository, and contract mapping;
- `aiuserconfiguration` configuration/runtime-state resolution;
- `ai.task` submission contract and lifecycle;
- `storycontextanalysis` REST and MCP-facing use cases;
- `analysis.workflow` background submission path;
- Core-to-AI Engine client and service-authenticated binding exchange.

### AI Engine

- task processing and task schemas;
- binding registry and credential resolver;
- ChatGPT and Anthropic provider adapters;
- callback/error classification where task authorization fails.

### MCP

- Story Agent tool adapter only, delegating to the shared Core capability.

## Test Plan

### Core unit/service tests

- `READY` ChatGPT configuration issues a binding.
- `READY` Anthropic configuration issues a binding.
- non-`READY` configuration is rejected.
- unavailable selected model is rejected.
- wrong principal, project, task, or provider is rejected.
- expired and revoked bindings are rejected.
- explicit model selection is not replaced by a recommendation.
- no secret material enters the task contract or persisted Core records.

### AI Engine tests

- ChatGPT task resolves only the matching binding.
- Anthropic task resolves only the matching binding.
- missing, foreign, expired, revoked, and mismatched bindings fail before the
  provider call.
- caller metadata cannot change principal, project, task, provider, model, or
  credential selection.
- process-level fallback credentials are not used for a user-bound task.

### Adapter/integration tests

- REST and MCP use the same Core authorization decision.
- background path either uses an explicit authorized execution identity or fails
  closed without bypassing binding authorization.
- OAuth and API-key vertical slices reach a real task submission boundary using
  mocked provider calls.
- revocation before provider use blocks execution.
- Stories 0160 and 0161 non-regression tests remain green.

## Validation Commands

```bash
uv run pytest -q
./backend/mvnw -pl backend -am clean verify -B
cd frontend && npm run lint
cd frontend && npm run format:check
cd frontend && npx ng test --watch=false
cd frontend && npm run build
git diff --check
```

The targeted integration tests will be run before the full relevant suites.

## Risks and Mitigations

- **Background principal ambiguity:** fail closed until an explicit identity is
  available; do not invent a system-to-user mapping.
- **Contract drift between Core and AI Engine:** update shared contracts and
  test both producer and consumer boundaries.
- **Credential leakage through task metadata:** use opaque binding identifiers,
  sanitize traces, and add assertions over serialized requests and prompts.
- **REST/MCP divergence:** keep adapters thin and test both against the same
  Core service mock/contract.
- **Provider fallback regression:** require binding resolution for user-scoped
  tasks and test that process-level credentials are ignored.

## Documentation Reconciliation

Expected documentation changes:

- update the configuration-to-binding-to-task sequence documentation;
- update Core governance documentation for task execution;
- update the Story implementation report with the final documentation outcome.

No vault write is planned. Any transverse-memory outcome remains proposal-only.

## Approval Gate

Implementation must not begin until the human explicitly approves this current
Implementation Plan.
