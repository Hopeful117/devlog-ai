# Story 0161 - Implementation Plan

## Plan Status

PROPOSED - requires explicit human approval before implementation.

## Governing Decisions

- ADR-006: AI output remains non-trusted.
- ADR-067: Java/Core owns scope, authorization and trusted state.
- ADR-070: principal, transport identity and project authorization remain
  separate.
- ADR-072: OAuth tokens remain inside the AI Engine provider boundary.
- ADR-073: Core issues and validates opaque principal/task/provider bindings.

## Selected Service Authentication

Use a dedicated HMAC service credential for the Core-to-AI binding protocol.
This is distinct from `SCA_CALLBACK_SECRET` and must be configured as:

- blank or missing: binding endpoint fails closed;
- deployment-managed secret, never committed or included in examples;
- rotated by deployment procedure, with an explicit overlap policy if rotation
  requires accepting old and new keys temporarily;
- HTTPS required for production even though HMAC authenticates the message.

The signed canonical request contains:

```text
HTTP method
request path
timestamp
nonce
request id
canonical JSON body
```

The signature is transmitted in a dedicated header. Verification uses constant
time comparison, a bounded clock-skew window, strict hex/base64 validation and
an atomic nonce replay check.

## Core Implementation

### 1. Binding domain model

Add an immutable application/domain representation for:

- `bindingId`;
- `principalId`;
- bound `taskId`;
- bound `projectId` resolved from the task;
- provider identifier;
- non-sensitive AI Engine credential handle or binding fingerprint;
- `ACTIVE`, `REVOKED`, `EXPIRED` status;
- issue and expiry timestamps;
- created/revoked audit timestamps and categorized reason.

Do not store access tokens, refresh tokens, authorization codes, ID tokens,
client secrets or prompt/context payloads.

### 2. Persistence and migration

Add a Flyway migration with:

- UUID primary key;
- foreign key to `ai_tasks` and the task's project through the existing model;
- principal/provider/status fields;
- issued/expiry/revocation timestamps;
- uniqueness preventing more than one active binding for the same task/provider
  unless the service explicitly revokes the previous binding;
- indexes for task, principal and status/expiry lookups;
- constraints for provider and status values.

The migration must not alter existing AI task snapshots or rewrite historical
tasks.

### 3. Core authorization service

Add a Core application capability that:

- requires an `AuthenticatedPrincipal`;
- resolves the task and its project in Core;
- checks project membership using the existing membership repository;
- verifies that the principal is allowed to use the requested provider;
- issues a short-lived binding with a bounded expiry;
- validates binding/task/principal/provider/status/expiry before use;
- revokes bindings idempotently;
- emits only categorized, non-sensitive audit diagnostics.

The service must not accept a project ID or project slug as authority when the
task already determines the project. It must not accept a caller-provided
principal ID in place of the authenticated principal.

### 4. Task contract and lifecycle

Add an explicit non-secret binding field to the Core-to-AI task contract and
the AI Engine submission schema only if it can be excluded from sensitive
snapshots and validated by Core. The field must be:

- optional for existing mock/API-key flows;
- required for the user-authorized provider;
- immutable after task submission;
- validated against task ID and provider in Core;
- rejected when supplied by an untrusted caller without a Core-issued binding.

If the current background analysis workflow has no authenticated principal,
leave it on existing API-key/mock behavior and expose no user-authorized mode
through that path. Do not invent a system principal or implicit development
bypass.

### 5. Signed Core-to-AI binding endpoint/client

Add a dedicated internal endpoint and `RestClient` operation for binding
registration or validation. The endpoint must:

- require the dedicated HMAC signature;
- verify method/path/body, timestamp, nonce and request ID;
- reject replay before domain processing;
- accept only the Core-issued opaque binding data;
- return a bounded non-secret result;
- never return project context, task snapshots or tokens.

The Core client must sign the exact raw/canonical JSON sent on the wire. The AI
Engine endpoint must not trust headers such as principal ID, project ID or
provider without validating the signed body and binding state.

## AI Engine Implementation

### 1. Binding store and resolver

Add an interface for local binding association:

- register an opaque Core binding against a local credential handle;
- resolve only `(bindingId, taskId, provider)`;
- reject missing, foreign, expired or revoked bindings;
- invalidate on Core revocation or credential refresh failure;
- never return token values in a task model or trace object.

Use injected fakes for tests. Production persistence remains governed by
ADR-072's encrypted credential store and must not be a plaintext binding file.

### 2. HMAC verification and replay protection

Implement a reusable verifier with:

- strict canonical request construction;
- constant-time signature comparison;
- UTC timestamp validation and maximum skew;
- nonce/request-ID atomic replay protection with bounded retention;
- generic public errors and categorized internal diagnostics;
- no logging of authorization headers or raw request bodies.

The verifier must be independent from `SCA_CALLBACK_SECRET` and use a distinct
configuration key.

### 3. Task processing integration

Resolve the binding only immediately before provider invocation and only after
task/provider validation. Preserve existing provider interfaces by passing an
internal runtime credential context rather than modifying the serialized
`Prompt` with a token.

Existing process-wide providers remain unchanged for mock and API-key modes.

## Security Test Matrix

### Core tests

- reader/owner can issue only for an authorized task project;
- unauthenticated principal is rejected;
- another project member cannot bind a foreign task;
- task/provider mismatch is rejected;
- duplicate active binding handling is deterministic;
- expiry and revocation fail closed;
- no token-shaped or prompt/context values are persisted;
- historical tasks remain unchanged.

### Protocol tests

- valid HMAC request succeeds;
- blank/missing secret fails closed;
- method/path/body/timestamp/nonce/request-ID tampering fails;
- malformed signature fails without exception leakage;
- stale timestamp fails;
- nonce replay fails atomically;
- different request body with reused request ID fails;
- rotation overlap behavior follows the configured policy;
- headers, signatures and body secrets are absent from logs.

### AI Engine tests

- valid binding resolves only for matching task and provider;
- foreign, unknown, revoked and expired bindings are rejected before OpenAI;
- binding IDs and service signatures do not appear in prompts or traces;
- token material never enters Core callback payloads;
- existing API-key/mock paths remain unchanged.

### Integration tests

- Core issues binding, signs registration and AI Engine registers it;
- Core revocation prevents the next provider resolution;
- a replayed registration is rejected;
- a task with an injected metadata binding cannot bypass Core validation;
- service-authentication failure does not invoke the provider;
- migration starts on a clean database and validates existing data.

## Observability

Allowed event fields:

- correlation/request ID;
- opaque binding ID or one-way fingerprint where needed;
- task ID and provider;
- operation category;
- result category;
- duration and attempt count.

Forbidden fields:

- HMAC secret or signature;
- OAuth access/refresh/ID token;
- authorization code;
- cookies;
- prompt, context, repository content or full request body.

## Documentation Reconciliation

Update only the relevant canonical documentation:

- environment variables for the dedicated Core-AI HMAC secret, skew and replay
  window, without any real value;
- deployment procedure for secret provisioning and rotation;
- AI Engine/Core internal endpoint and security boundary;
- troubleshooting categories without exposing signed payloads or credentials.

## Validation Commands

```bash
./backend/mvnw -pl backend -am test -B
cd ai-engine && python -m pytest -q
git diff --check
```

Focused tests must run before the broader suites. A secret scan of changed
files and a dependency/configuration review are mandatory. Live service-auth
validation is not required and must not use production credentials.

## Delivery Sequence

1. Approve this implementation plan.
2. Implement Core binding model, migration, repository and authorization tests.
3. Implement HMAC signing/verifying and replay protection on both sides.
4. Implement the explicit task binding contract and provider-mode checks.
5. Implement AI Engine resolver/store integration without OAuth tokens in the
   task contract.
6. Run focused and broader validation, including secret scanning.
7. Reconcile documentation and produce the implementation report.
8. Only then resume Story 0160's OAuth/provider implementation.

## Stop Conditions

Stop without weakening the boundary if:

- no authenticated Core principal is available for the requested task;
- the binding would need to contain a token or secret;
- task snapshots cannot exclude the binding safely;
- service-secret rotation/replay protection cannot be configured;
- a caller-controlled metadata value would be used as authorization.

## Readiness

READY_FOR_HUMAN_PLAN_REVIEW
