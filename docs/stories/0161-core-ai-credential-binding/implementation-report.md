# Story 0161 - Implementation Report

## Status

BLOCKED - partial implementation; the complete Story is not ready for human
acceptance.

## Implemented

- Added Core persistence migration `V58__create_ai_credential_bindings.sql`.
- Added Core `AiCredentialBinding` entity, status model and repository.
- Added Core authorization service with principal membership checks, task scope,
  provider matching, positive TTL, expiration and revocation handling.
- Added `credential_binding_id` to the `AiTask` entity and migration V59 as a
  non-secret lifecycle field.
- Added a Java HMAC signer for the dedicated Core-to-AI service boundary.
- Added Python HMAC signing/verification primitives with canonical request
  signing, timestamp validation, constant-time comparison and nonce replay
  protection.
- Added focused Python security tests for signature tampering, stale requests,
  canonical JSON and replay rejection.
- Added an authenticated Core endpoint for issuing and revoking bindings. It
  resolves the principal only from the authenticated request and returns no
  credential handle.
- Persisted the issued binding ID on the associated AI task.
- Added a dedicated HMAC-protected AI Engine registration endpoint and Core
  client. The exact serialized request body is signed with method, path,
  timestamp, nonce and request ID.
- Added separate `DEVLOG_SERVICE_AUTH_SECRET` configuration on Core and AI
  Engine; it fails closed when absent.
- Replaced the process-local binding registry with a SQLite-backed registry
  persisted on the Docker `devlog-ai-bindings` volume.
- Added an AES-GCM encrypted credential store and a resolver that first checks
  `(bindingId, taskId, provider)` before decrypting a provider credential.
  Encryption requires the deployment-only
  `DEVLOG_CREDENTIAL_ENCRYPTION_KEY`.

## Security Properties Preserved

- OAuth access and refresh tokens are not represented in the Core binding model.
- Bindings are scoped to one principal, task and provider.
- Binding issuance requires project membership and only permits tasks still in
  `CREATED` status.
- Expired and revoked bindings fail closed.
- The HMAC secret is separate from `SCA_CALLBACK_SECRET`.
- The HMAC helper does not log or include request payloads in its errors.

## Not Yet Implemented

- Provider credential resolver integration. The provider still uses the
  process-wide `LLM_API_KEY`; an opaque binding handle is not accepted as an
  API key and no user-authorized provider call is enabled yet.
- End-to-end task contract propagation and provider resolver integration.
- Replay storage shared across AI Engine instances.
- Story 0160 OAuth provider integration.

These items remain necessary before the binding can authorize a real provider
call. No live credential or provider call was enabled.

## Validation

Passed:

```text
uv run pytest tests/test_service_auth.py -q
7 passed

javac -d /tmp/opencode \
  backend/src/main/java/com/hopeful117/devlogai/aicredentialbinding/service/AiEngineBindingRequestSigner.java
```

Blocked or failed:

```text
./backend/mvnw -q -DskipTests compile
```

The backend compilation is blocked by a pre-existing repository error in
`StoryContextResultValidator`: the shared contract does not contain
`StoryContextAnalysisResult.ImplementationPreparation`. This error is outside
the Story 0161 files and prevented full Core test execution.

The broader AI Engine suite was executed but has four pre-existing failures in
comparative evaluation fixtures due to a projection byte-length mismatch under
`/home/ludo/workspace/trading-os`. The new focused HMAC tests pass.

## Documentation Outcome

Documentation update is pending completion of the protocol wiring. The Story
ADR and implementation plan already document the security boundary; runtime
configuration documentation must be updated after the final endpoint and
secret-rotation contract is implemented.

## Readiness

BLOCKED - do not enable live OAuth or claim Story acceptance.
