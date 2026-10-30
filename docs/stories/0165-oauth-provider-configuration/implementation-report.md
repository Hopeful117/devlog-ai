# Implementation Report - Story 0165

## Repository State

- Branch: `feat/story-0165-oauth-provider-configuration`
- Base HEAD: `a5017fd`
- Worktree: modified only in Story 0165 implementation, tests, documentation,
  and the pre-existing untracked Story 0165 analysis/plan artifacts.
- Commit/push: pending delivery authorization.

## Governance

- Governing constraints: ADR-072, ADR-073, ADR-075.
- Authorized Story: Story 0165, OAuth Provider Configuration Integration.
- DevLog context: unavailable in this session; repository artifacts were used.
- Vault: not consulted; no vault write was performed.

## Files Changed

- `backend/.../aicredentialbinding`: verify-before-persist association lifecycle,
  explicit provider identity mapping, non-secret state/scope projection,
  HMAC-authenticated revocation propagation, and fail-closed binding issuance.
- `backend/.../aiuserconfiguration`: short-lived, single-use principal-bound
  OAuth intents, signed authorization URLs, and authenticated Core completion.
- `ai-engine/app/...`: matching product/internal provider mapping, verification
  response metadata, intent-bound transactions, completion redemption, and
  replay-protected service callbacks.
- `backend/src/main/resources/db/migration/V62__...`: association runtime state.
- `backend/src/main/resources/db/migration/V63__...`: OAuth connection intents.
- `backend/src/main/resources/db/migration/V64__...`: OAuth completion codes.
- Focused Java and Python tests covering mapping and failed verification.
- `ai-engine/README.md`: OAuth configuration and ownership documentation.

## Implementation Summary

Core now verifies an opaque association with the AI Engine before saving or
replacing it. The AI Engine reports only bounded state and granted scopes; Core
projects identity-only credentials as incomplete, missing usage scopes as
`REAUTH_REQUIRED`, usable credentials as `READY`, and refuses task bindings
unless usage authorization is explicit. AI Engine disconnect revokes the Core
association before deleting its local credential. `openai_chatgpt` and internal
`chatgpt` identities are mapped explicitly without fallback.

The browser handoff now starts from an authenticated Core endpoint. Core signs
opaque intent data, expiry, and an HMAC-derived principal binding. The AI Engine
binds that intent to its OAuth transaction but does not finalize Core state from
the provider callback. It returns a one-time completion code; the authenticated
Core principal redeems that code and Core verifies the credential before
consuming the intent.

Core-initiated revocation and replacement invalidate the old AI Engine
credential and all bindings using its handle. Existing associations are
migrated to `REAUTH_REQUIRED`, making the compatibility behavior explicit.
All new Core-to-AI callbacks use the replay-protected service authentication
contract.

## Validation

Commands executed:

```bash
cd ai-engine && .venv/bin/python -m pytest tests/test_chatgpt_oauth.py tests/test_binding_registry.py tests/test_service_auth.py -q
cd ai-engine && python3 -m compileall -q app tests
./backend/mvnw -pl backend -Dtest=OAuthConnectionIntentServiceTest,AiCredentialAssociationServiceImplTest,AiCredentialAssociationControllerTest,AiUserConfigurationServiceTest,AiUserConfigurationControllerTest,AiEngineBindingRequestSignerTest test -B
git diff --check
```

Results:

- Python focused tests: `15 passed`.
- Python compile check: passed.
- Updated Java focused tests: `21 tests, 0 failures, 0 errors`.
- Updated Python handoff/OAuth focused tests: `18 passed`.
- Full AI Engine suite with `SCA_CALLBACK_SECRET=test-secret`: all tests passed.
- Backend targeted controller, service, and PostgreSQL integration tests: `8 tests, 0 failures, 0 errors`.
- Full backend suite: `1,561 tests, 0 failures, 0 errors`.
- Diff check: passed.
- No live credentials or provider/network validation was used.

Broader validation:

- `cd ai-engine && uv run pytest`: passed, `541 tests`.
- `./backend/mvnw -pl backend -am test -B`: passed with `1,556 tests, 0 failures,
  0 errors`; Testcontainers emitted transient connection-pool shutdown warnings
  after successful test completion.

## Documentation Outcome

Updated `ai-engine/README.md` because Story 0165 changes the documented OAuth
configuration/runtime boundary and provider identity mapping. Approved the
Story 0165 workflow artifacts and ADR-072, ADR-073, and ADR-075 per explicit
human authorization.

## Vault Outcome

No vault action. The vault was not consulted because the approved Story,
repository analysis, implementation plan, ADRs, and current source were
sufficient and authoritative for this provider-specific change. No candidate
note or proposal artifact was created.

## Known Limitations

- No live credentials or provider/network validation was used.

## Readiness

READY_FOR_DELIVERY
