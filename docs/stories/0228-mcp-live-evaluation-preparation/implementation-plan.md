# Story 0228 - Implementation Plan

## Status

APPROVED - implementation authorized by the human request in the current session.

This plan is derived from the approved Repository Analysis. It authorizes the
implementation corrections in the current worktree, but not provider
execution, budget changes, commits, pushes or merges.

## Implementation Summary

Implement a Core-owned, preparation-only capability for bounded ABC live
evaluations. Persist an immutable preparation plan with durable idempotency,
expose it through a Core HTTP adapter and a thin MCP tool, and add runner-side
plan revalidation before the separately authorized live execution path.

The implementation will not call a provider, create an AI task, dispatch an
OAuth request, create/renew a campaign authorization or write to a local
artifact directory.

## Decisions Made for Implementation

### Dedicated preparation aggregate

Introduce a dedicated Core preparation-plan representation rather than reuse
`AiTask`. `AiTask` represents AI execution, provider binding and callback
lifecycle; using it for preparation would create misleading execution state and
couple readiness to provider task semantics.

The aggregate is append-only/immutable after creation. It stores only bounded,
redacted plan metadata and canonical request/plan digests. It does not store
credentials, raw prompts, provider payloads, full repository content or local
filesystem paths.

### Durable idempotency

Use a unique database scope containing:

```text
capability + project + principal + idempotencyKeyHash + idempotencyScopeHash
```

The canonical request digest is stored separately. The service acquires a
transactional lock before creating a plan.

- Same scope and same request digest: return the existing plan.
- Same scope and different request digest: return a bounded conflict.
- Concurrent equivalent requests: one plan is created.
- Raw idempotency keys and delegation tokens are never persisted or logged.

### Plan persistence policy

- `READY` and `BLOCKED` plans are persisted after the caller's project scope is
  authorized.
- `NOT_AUTHORIZED` is returned with bounded diagnostics and is not persisted as
  an authoritative plan because the caller's project/delegation scope was not
  established.
- A persisted plan has a stable identity, status, request digest, plan digest,
  creation timestamp and bounded expiration/revalidation metadata.

### Artifact destination

The request carries a logical artifact namespace/reference, not a filesystem
path. Core validates its format and binds it into the plan. The Python runner
maps the logical reference to a fresh local output directory only after final
authorization and local preflight. Core does not create or inspect local
directories.

### Core/local revision boundary

The runner computes a bounded runtime attestation from its local checkout,
including runtime revision, worktree reproducibility and harness identity. Core
validates the attestation shape and binds it to the requested/frozen source
revision and policy.

If the configured Core source cannot independently verify a claimed local
worktree state, the plan is `BLOCKED`; the system must not treat an arbitrary
caller-provided boolean as proof of reproducibility. The runner performs the
local filesystem/Git check again immediately before live execution.

### REST surface

This Story adds the Core HTTP endpoint required by `mcp-server` and does not add
a separate public UI-facing REST workflow. The MCP tool remains the first
consumer. The Core application capability remains transport-independent so a
future REST consumer can be added without moving validation into MCP.

## Planned Repository Changes

### Backend Core

Add a focused package, provisionally:

```text
backend/src/main/java/com/hopeful117/devlogai/evaluation/livepreparation/
```

Planned components:

- immutable preparation-plan entity;
- plan status enum: `READY`, `BLOCKED`;
- bounded blocker code/value objects;
- repository with scoped idempotency lookup and conflict support;
- canonical request/plan digest service;
- application service/use case for authorization, validation and plan creation;
- request/response DTOs;
- authenticated Core controller;
- safe plan retrieval endpoint;
- metrics/diagnostic projection without sensitive fields.

The exact package/class names may be adjusted to existing backend conventions,
but the capability must remain separate from `ai.task` execution services.

### Database

Add the next Flyway migration after the current highest migration. It will
create a preparation-plan table with:

- generated plan UUID;
- project/principal scope references or bounded identities;
- capability and benchmark/case identities;
- idempotency key hash and idempotency scope hash;
- request digest and plan digest;
- status;
- source/runtime revisions;
- manifest/oracle/pricing digests;
- bounded policy/limits JSON snapshot;
- bounded blockers JSON snapshot;
- logical artifact namespace;
- created/updated/expiry timestamps.

The migration must add a unique constraint for the idempotency scope and indexes
for authorized plan retrieval. JSON fields must be size-bounded by the service
contract; no raw request payload or secrets are persisted.

### Core HTTP client and controller

Extend the existing `mcp-server` Core client boundary with:

- `POST` preparation request;
- `GET` plan by UUID for authorized revalidation;
- authenticated principal/delegation headers;
- idempotency-key propagation;
- bounded response DTOs.

The backend endpoint must use the existing principal resolver and Core
authorization mechanisms. It must not accept caller-supplied credentials or
trust MCP-provided project authorization claims.

### MCP Server

Add a thin `PrepareLiveEvaluationTool` alongside the existing protocol tools.
The tool will:

- resolve the authenticated MCP principal;
- require the authenticated bearer/delegation context;
- validate only transport-level required arguments;
- call Core;
- serialize the canonical response;
- map errors through the existing Core call policy;
- emit bounded MCP observability metadata.

It will not read manifests, inspect Git, load `.env`, call the Python runner or
construct a provider client.

### Python evaluation runner

Add a prepared-plan contract/validator under
`ai-engine/evaluations/devlog_ai_abc_runner/`.

The live path will:

1. request or receive a Core preparation plan;
2. verify the plan digest and expected manifest/contract identities;
3. verify local runtime revision, worktree and harness identity;
4. verify plan expiration and operational ceilings;
5. obtain/validate the separate campaign authorization;
6. re-read the authoritative plan immediately before execution;
7. refuse execution on any mismatch before provider construction.

The existing `live.py` authorization validator remains the authority for the
campaign authorization contract. The new validator must not replace or weaken
that check.

### Documentation

Update:

- `docs/evaluation/devlog-ai-q1-asymmetric-pilot-runbook-v1.md` with the MCP
  preparation flow and pre-execution revalidation;
- relevant MCP operator documentation with the new tool, statuses and bounded
  diagnostics;
- Story 0228 implementation documentation with the final persistence and
  retention decisions.

Do not document the capability as live execution or as a cost-free provider
path.

## Validation Ownership Matrix

| Concern | Core | MCP | Python runner |
|---|---|---|---|
| Project/principal/delegation authorization | authoritative | forwards identity | consumes result |
| Manifest/oracle identity | authoritative contract binding | forwards request | verifies local copy |
| Source/runtime revision | binds expected values and attestation | forwards metadata | computes local runtime evidence |
| Worktree reproducibility | rejects unverifiable attestation | no decision | computes and rechecks locally |
| Contract/budget identity | authoritative | forwards request | verifies before execution |
| OAuth credential values | never sees values | never sees values | provider boundary only |
| Campaign authorization | not created by preparation | never creates | final execution gate |
| Provider calls | forbidden | forbidden | only separate live path |
| Semantic scoring | forbidden | forbidden | evaluation path after execution |

## Detailed Work Sequence

### Phase 1 - Contracts and canonicalization

1. Define request, response, plan, blocker and readiness DTOs.
2. Define canonical JSON serialization and digest inputs.
3. Define maximum sizes for strings, lists, maps and blocker diagnostics.
4. Define status mapping and safe error codes.
5. Add contract unit tests before persistence wiring.

### Phase 2 - Persistence and idempotent Core service

1. Add the Flyway migration and unique idempotency scope.
2. Add entity and repository queries.
3. Implement transactional plan creation with the existing lock pattern.
4. Implement same-key replay and digest conflict.
5. Implement authorized plan retrieval.
6. Ensure plan fields cannot be changed after creation.
7. Add concurrency and transaction tests.

### Phase 3 - Deterministic preparation validation

1. Validate authorized project and principal scope.
2. Validate benchmark/case/manifest/oracle identities and digests.
3. Validate provider/model and non-metered pricing contract.
4. Validate runtime identity, attestation and operational budgets.
5. Validate logical artifact destination.
6. Build bounded blocker list.
7. Persist `READY` or `BLOCKED` only after authorization is established.
8. Return `NOT_AUTHORIZED` without persisting an authoritative plan when scope
   cannot be established.

### Phase 4 - HTTP and MCP adapters

1. Add authenticated Core endpoints.
2. Add typed client methods in `mcp-server`.
3. Add the MCP tool and bounded error mapping.
4. Add MCP observability with no secrets or payloads.
5. Add endpoint and tool tests.

### Phase 5 - Runner revalidation

1. Add plan loader/validator.
2. Add local runtime/worktree attestation verification.
3. Add authoritative plan re-read before provider construction.
4. Preserve the existing campaign authorization validator.
5. Add tests proving all failures stop before provider creation/call.

### Phase 6 - Documentation and quality validation

1. Update the live runbook and MCP documentation.
2. Run focused backend, MCP and AI Engine tests.
3. Run applicable integration tests.
4. Verify `providerCalls=0` and `networkCalls=0` for preparation tests.
5. Inspect redacted artifacts and logs.
6. Run `git diff --check` and review the complete diff.

## Acceptance-Criterion to Test Mapping

| Criterion | Validation |
|---|---|
| Stable authoritative plan | Core service/integration test |
| Three preparation statuses | Contract and controller tests |
| Revision/worktree blocking | Core and runner tests |
| Manifest/oracle/digest blocking | Core validation tests |
| Non-metered OAuth semantics | Pricing contract and preparation tests |
| Operational ceilings | Contract and boundary tests |
| Secret redaction | Core, MCP and runner tests |
| Exact idempotent replay | Repository/service integration test |
| Key conflict | Repository/service integration test |
| Concurrent duplicate prevention | Transaction/concurrency test |
| Zero provider/network calls | Python preparation test |
| Expired/digest-mismatched plan rejection | Runner tests |
| Campaign authorization remains separate | Live boundary tests |
| No production policy changes | Existing regression suites and diff review |
| Runbook completeness | Documentation review |

## Expected Files

The exact paths may be refined after class/package inspection, but the change
should remain limited to:

- backend live-preparation domain/application/controller/repository/test files;
- one backend Flyway migration;
- MCP Core client, tool and tests;
- Python ABC plan contract/validation and tests;
- the ABC live runbook and Story implementation artifacts.

No frontend, provider implementation, production prompt, retrieval policy or
trusted-knowledge module should change.

## Quality Commands

Focused validation should run first:

```bash
./backend/mvnw -pl backend test -Dtest='*LiveEvaluationPreparation*'
./backend/mvnw -pl mcp-server test -Dtest='*LiveEvaluationPreparation*'
uv run --project ai-engine --extra dev pytest \
  ai-engine/tests/test_devlog_ai_abc_runner.py \
  ai-engine/tests/test_devlog_ai_abc_live_preflight.py -q
```

Then run the applicable backend, MCP and AI Engine suites required by the
repository quality workflow. A failed or unavailable required check must remain
explicitly reported.

## Risks and Mitigations

- Core/Python validation drift: bind all shared identities through versioned
  digests and keep authorization Core-owned.
- Worktree attestation overclaim: require local runner evidence and fail closed
  when Core cannot establish a trusted source.
- Accidental provider execution: keep preparation modules free of provider
  imports/constructors and assert zero calls in tests.
- Secret leakage: use allowlisted response projections and redaction tests.
- Duplicate plans: use database uniqueness plus transactional locking.
- Stale plan: require authoritative re-read and final runtime/authorization
  revalidation.
- Scope expansion: reject unspecified observations, budgets or destinations.

## Human Decisions Still Required

1. Explicitly authorize implementation after plan approval.
2. Confirm the retention period for persisted `BLOCKED` plans.
3. Confirm the final non-secret OAuth readiness vocabulary.

## Implementation Gate

No production implementation may begin until this Implementation Plan is
explicitly approved and implementation authorization is separately granted.
